# Plan: Telegram + Discord side by side

Status: **implemented** (2026-07-28), except §9.2 — the scenario suite still runs only through the
Telegram adapter. Deviations from the design as written are recorded in §13. Open decisions: §12.

## 1. Goal

Run the *same* bot on Telegram and Discord **simultaneously, in one process**, with one copy of the
lobby/draft logic. Concretely, after this work:

- `LobbyService`, `LeaderService`, `DraftStrategy`, `HersonResolver`, every `Command`, and every
  message the bot composes contain **zero** platform types.
- Each platform contributes an **adapter**: it translates inbound platform events into a normalized
  `ChatEvent`, and renders outbound *semantic* messages (text + `Component`s) into that
  platform's wire format.
- The `.chat` scenario suite runs **unchanged**, and is parameterized over platforms.

Non-goals (explicit): a single lobby spanning both platforms; persistence; changing draft rules.

## 2. Why the current code can't just grow a second frontend

| Coupling | Where | Count |
|---|---|---|
| `Update` threaded through every command signature | `Command.execute(Update, AmbotorixService)`, all 28 commands | 28 files |
| `TelegramClient` injected and `execute()`d directly | `AmbotorixService`, `OpenDraftStrategy`, `SecretDraftStrategy`, `NotificationService` | 4 files |
| `InlineKeyboardMarkup` is the return type of the whole UI layer | `MarkupService` (all 8 methods) | 1 file |
| `SendPhoto` baked into the image renderer | `PickImageGenerator.createCombinedPickMessage` | 1 file |
| HTML markup hard-coded in message strings + `parseMode("HTML")` | `AmbotorixService` (~60 sites) | 1 file |
| `@username` mentions built by string concat | `mentionAll`, status render, ~20 sites | 1 file |
| `Long chatId` as the lobby key and as the DM target | `LobbyService`, `Lobby`, callback payloads, scheduler, admin commands | 6 files |

One of these is *semantic*, not mechanical, and drives the design:

**The leader grid.** There are **89 leaders**. Telegram happily renders an 89-button inline
keyboard. Discord caps a message at **5 action rows × 5 buttons = 25 buttons**; 89 options must
become **4 string-select menus** (25 options each). The core must therefore describe *"a chooser over
these 89 options, ranked, showing rank badges"* — not a button matrix.

The other two are cheaper than they look, and the plan deliberately takes the cheap route:

- **Mentions** (`"@" + userName` vs `<@userId>`) are handled as a **placeholder rewrite in the
  adapter**, not a structured text model — see §4.4/D6.
- **Markup** (`<b>` vs `**`) is handled by an **HTML→Markdown renderer** in the Discord adapter, so
  the ~60 existing message strings stay as they are.

## 3. Target package layout

```
vitbuk/com/Ambotorix/
  chat/                    ← THE PORT. No platform imports allowed here or below it.
    ChatRef, UserRef, MessageRef, Platform, Audience
    ChatEvent (sealed: Command | FreeText | Action)
    ActionRef, ActionCodec
    OutgoingMessage, Attachment, MentionTable
    ui/       Component (sealed: Chooser | Actions), Option, ActionButton, Capabilities
    ChatGateway, ChatGatewayRegistry
  core/
    BotDispatcher            ← ex-Ambotorix.consume: guards + routing, platform-free
    commands/                ← Command.execute(CommandContext)
    services/                ← LobbyService, LeaderService, DataUpdate…, flow services (§8)
    draft/, entities/, matching/, photochallenge/
    view/                    ← StatusView, LeaderGridView, BanChooserView, HersonPickView,
                               HelpView, MapPoolView, PickPoolImage   (domain → text/Component)
  adapters/
    telegram/  TelegramBot (SpringLongPollingBot), TelegramEventMapper, TelegramGateway,
               TelegramTextRenderer, TelegramComponentRenderer
    discord/   DiscordBot (JDA listeners), DiscordEventMapper, DiscordGateway,
               DiscordTextRenderer, DiscordComponentRenderer, SlashCommandRegistrar
```

**D1 — The dependency rule.** `core` and `view` may depend on `chat`; `chat` depends on nothing;
`adapters` depend on both. Enforce it with a single ArchUnit-style test (or a `grep`-based test if
we don't want the dependency): *no `org.telegram.*` / `net.dv8tion.*` import outside `adapters/`.*
That test is what keeps the separation from rotting.

## 4. The port

### 4.1 Identity and addressing

```java
enum Platform { TELEGRAM, DISCORD }

/** A destination: a channel on a platform, optionally a thread/topic inside it. */
record ChatRef(Platform platform, String channelId, String threadId) {}

/** An actor. userName is the stable handle used as the in-lobby key; displayName is cosmetic. */
record UserRef(Platform platform, String id, String userName, String displayName) {}

/** A handle on something the bot sent, so it can be edited or replied to later. */
record MessageRef(ChatRef chat, String messageId) {}

/** Who a message is for — resolved to a concrete ChatRef by the gateway. */
sealed interface Audience {
    record Channel(ChatRef chat) implements Audience {}  // the group/lobby channel
    record Direct(UserRef user)  implements Audience {}  // a DM — same meaning on both platforms
}
```

**D6a — DMs on both platforms; no ephemeral messages.** Discord *could* answer a slash command with
an ephemeral reply, but ephemerals are bound to a 15-minute interaction token and cannot be fetched
or edited by message id afterwards — which would break the two flows that depend on editing a DM in
place (the Herson ranked grid, and re-locking it on submit). Using DMs uniformly keeps one code path,
keeps the existing "couldn't DM you, message the bot first" fallbacks meaningful on both platforms,
and keeps the scenario suite free of a platform-specific `<ephemeral>` predicate. Ephemeral replies
stay available as a later, purely cosmetic polish for one-shot error text.

Ids become `String` throughout. Discord snowflakes fit a `long`, but a uniform string avoids
sign/parse traps and makes `ChatRef` printable in logs and admin output.

### 4.2 Inbound events

```java
sealed interface ChatEvent {
    UserRef from(); ChatRef chat(); boolean direct();   // direct == private chat / DM

    record Command (UserRef from, ChatRef chat, boolean direct,
                    String prefix, String args, String raw)          implements ChatEvent {}
    record FreeText(UserRef from, ChatRef chat, boolean direct, String text) implements ChatEvent {}
    record Action  (UserRef from, ChatRef chat, boolean direct,
                    ActionRef action, List<String> values)           implements ChatEvent {}
}
```

The adapter owns *all* platform parsing: stripping `@botname`, splitting on `[\s_]+`, assembling JDA
slash-command options back into an `args` string, decoding a `custom_id`. `BotDispatcher` sees only
the record above — which is exactly the shape today's `Ambotorix.consume` already reduces `Update` to.

### 4.3 Actions (button/menu payloads)

```java
record ActionRef(String verb, List<String> args) {}   // e.g. ("hpick", ["7f3a2b", "lincoln"])
```

`ActionCodec` encodes to `verb|arg|arg` (and back). Two hard limits meet here: Telegram
`callback_data` ≤ **64 bytes**, Discord `custom_id` ≤ **100 chars**.

**D2 — Lobby tokens instead of raw chat ids in payloads.** Today's payloads embed the group chat id
(`/hpick -1001234567890 lincoln` — 14 chars of id; a Discord snowflake is 19). Replace it with a
**6-char base36 lobby token** minted by `LobbyService` and resolvable back to a `ChatRef`. This keeps
every payload comfortably inside 64 bytes on both platforms and removes the current implicit
"payload must be parseable as Long" assumption (several `NumberFormatException` catch blocks in
`makeCallbackQuery` disappear). A codec unit test pins the byte budget.

### 4.4 Text — **D6: keep the strings, rewrite at the edge**

**The canonical internal format stays what it already is:** a plain `String` in the Telegram HTML
subset the codebase uses today (`<b> <i> <code> <pre> <a>`), with `@username` as the **mention
placeholder**. No structured-text AST, no migration of ~60 message strings, no churn in `StatusView` or
any flow service. The core is "platform-free" not because its text is abstract, but because both
things a platform cares about — markup and mentions — are *derivable at render time*.

Two renderer responsibilities, both in the adapter:

| | Telegram | Discord |
|---|---|---|
| Markup | pass through, `parseMode("HTML")` | `HtmlToMarkdown`: `<b>`→`**`, `<i>`→`*`, `<code>`→`` ` ``, `<pre>`→```` ``` ````, `<a href>`→`[label](url)`, unescape entities, escape stray `* _ ~ \|` |
| Mentions | pass through (`@alice` is already correct) | `@alice` → `<@123456789>` |
| Length | ≤4096 (captions ≤1024) | split on line boundaries into ≤2000-char chunks (embed description ≤4096) |

Chunking lives in the gateway, so no caller worries about it.

**Mention resolution.** `OutgoingMessage` carries a `MentionTable` — a `Map<String userName, UserRef>`
that the sending facade populates from the target lobby's players (plus a per-platform directory
cache built from inbound events, for messages sent outside a lobby context). The Discord renderer
rewrites `@name` **only when `name` is in the table**; anything else — `@VitBuk` in `/credits`, an
`@` inside a leader description — is left as literal text. That "resolve or leave alone" rule is what
makes a bare `@username` placeholder safe without a sigil.

Cost of this route, stated honestly: the core carries a Telegram-flavoured markup dialect, and
`HtmlToMarkdown` is a small parser that needs its own unit tests (nesting, `<pre>` blocks — the
`/photochallenge` monospace table is the demanding case). In exchange, phase P4 shrinks from
"migrate every message string" to "write two classes", and every existing message keeps rendering
byte-identically on Telegram. If the dialect ever becomes painful, a structured text AST can be introduced
later *behind the same gateway API* — this decision is reversible, which is why it's the right one to
start with.

### 4.5 Components — semantic, not literal

```java
sealed interface Component {
    /** Choose from many options. The adapter picks the widget. */
    record Chooser(String id, String placeholder, List<Option> options,
                   int preferredColumns, Selection selection) implements Component {}
    /** A small fixed set of actions: Confirm / Re-enter / Submit / Reset. */
    record Actions(List<ActionButton> buttons) implements Component {}
}
record Option(String label, String description, ActionRef action, String badge) {}
record ActionButton(String label, Style style, ActionRef action) {}
enum Selection { NONE, SINGLE, RANKED }   // RANKED: badge carries the rank, taps toggle
```

**D7 — Widget choice is an adapter policy, never a core concern.** Each adapter owns a
`ChooserPolicy` mapping `(options.size(), selection)` → widget. The core states intent once;
which widget serves it is a decision we can revise later without touching a single line of draft code.

`TelegramChooserPolicy` — one rule: inline keyboard, `preferredColumns` wide, badge appended to the
label (`"Lincoln (1)"`), re-rendered on each tap via `editMessageReplyMarkup`. 89 buttons is fine.

`DiscordChooserPolicy`:

| Case | Widget |
|---|---|
| ≤25 options, `SINGLE` | button grid, 5 per row (map pool, ban disambiguation, confirm) |
| >25 options, `SINGLE` | **grouped `StringSelectMenu`s** of ≤25, bucketed alphabetically (`A–F`, `G–L`, …) as the `placeholder` |
| `RANKED` (the 89-leader Herson grid) | same grouped selects, `max_values = 1`; each selection **appends** to the rank list, and every menu re-renders with `description: "#1"` on ranked options + `defaultValues` set. `Actions` (Submit/Reset) occupies the 5th row |
| `Actions` | one button action row |

**The 5-row ceiling is a real constraint.** 89 leaders → 4 select menus (100 option slots) + 1 action
row = exactly Discord's 5-action-row maximum, with 11 slots of headroom. If a future BBG release
pushes the roster past **100 leaders**, the policy must gain a first-step letter filter or ◀▶ paging.
An adapter conformance test asserts the roster still fits and fails loudly when it stops fitting
(§9.3) — better a red test than a runtime `IllegalArgumentException` mid-draft.

Separately, and for free from the slash-command surface: `DynamicCommand`s whose argument is a leader
name (`/ban`, `/d`, `/pick`) get **Discord autocomplete** — the type-ahead interaction returns up to
25 matches from the existing `LeaderMatcher`. That is the most Discord-native "pick one of 89", and
it needs no `Chooser` at all.

This section is the whole point of the exercise: **`HersonPickView` says "a ranked chooser over the 89
unbanned leaders, plus Submit/Reset"; neither the view nor the draft strategy knows a select menu
exists.**

### 4.6 The gateway

```java
interface ChatGateway {
    Platform platform();
    Optional<MessageRef> send(OutgoingMessage msg);        // empty == not deliverable (DM closed)
    boolean edit(MessageRef ref, OutgoingMessage msg);
    void acknowledge(ActionRef action);                    // AnswerCallbackQuery / deferEdit
    Capabilities capabilities();                           // maxText, maxButtons, maxOptions…
}

record OutgoingMessage(Audience to, String text, MentionTable mentions, List<Component> components,
                       Attachment attachment, MessageRef replyTo, boolean silent) {}
```

`ChatGatewayRegistry.of(ChatRef)` routes by `platform()`. Core code holds the registry, never a
concrete gateway.

**D3 — Stay synchronous.** Today every send completes inline before `consume` returns, and the
scenario harness depends on that. JDA is async; the adapter calls `.complete()` (JDA blocks on rate
limits internally). Cost: a slow Discord channel blocks that channel's handler thread. Acceptable at
this scale, and it keeps the harness — the main safety net for the whole refactor — working
unchanged. Revisit only if it bites.

## 5. What each domain concept becomes

| Today | After |
|---|---|
| `AmbotorixService.renderStatus(Lobby) : String` (HTML) | `StatusView.render(Lobby) : String` — same HTML dialect, but pure and unit-testable |
| `MarkupService.hersonPickMarkup(...) : InlineKeyboardMarkup` | `HersonPickView.chooser(...) : Component.Chooser` |
| `MarkupService.leadersGridMarkup / banButtonsMarkup / mapRemoveMarkup / maplistMarkup / pickMarkup / hersonConfirmMarkup` | `LeaderGridView`, `BanChooserView`, `MapPoolView`, `PickPoolView`, `ConfirmView` |
| `PickImageGenerator.…() : LeaderPickPhoto` (wraps `SendPhoto`) | `PickImageGenerator.render(List<Player>) : byte[]` → `Attachment` |
| `Command.execute(Update, AmbotorixService)` | `Command.execute(CommandContext ctx)` |
| `Lobby.messageThreadId : Integer` | `Lobby.chat : ChatRef` (carries the thread) |
| `Lobby.statusMessageId : Integer` | `Lobby.statusMessage : MessageRef` |
| `Player.userId : Long` | `Player.user : UserRef` |
| `Player.dmPickMessageId : Long` | `Player.dmPickMessage : MessageRef` |
| `LobbyService: Map<Long, Lobby>` | `Map<ChatRef, Lobby>` + `Map<String token, ChatRef>` |
| `bot.adminId : Long` | `bot.admin.telegram-id`, `bot.admin.discord-id` |

**Preserved behavior worth stating:** the lobby key stays *channel-level*, not thread-level, on
Telegram (one lobby per group, thread remembered on the `Lobby` — exactly as today). Discord threads
*are* channels, so Discord gets per-thread lobbies for free without a rule change.

## 6. `CommandContext`

```java
record CommandContext(ChatEvent.Command event, ChatRef chat, UserRef user, Chat out) {}
```

where `Chat` is a thin per-request facade over the registry:
`out.toChannel(text, components…)`, `out.toDm(user, text)`,
`out.status().post/refresh()`, `out.milestone(text)`. Commands go from

```java
ambotorixService.sendSmartBan(update, query);          // before
banFlow.ban(ctx, query);                               // after
```

The four marker interfaces (`GeneralCommand`, `HostCommand`, `PlayerCommand`, `AdminCommand`,
`DynamicCommand`) and their dispatcher guards move over **verbatim** — that design already works and
is platform-free. `AdminCommand`'s check becomes "the caller's `UserRef` matches the configured admin
*for that platform*".

## 7. Discord adapter

**Library: JDA 5.x** (`net.dv8tion:JDA`, latest 5.x). Mature, non-reactive, first-class buttons /
select menus / threads / file uploads / DMs, and a plain `@Bean JDA` fits Spring without a
starter. Discord4J is the alternative (reactive, would fight D3).

Bean is `@ConditionalOnProperty("discord.token")`, so the app still boots Telegram-only — the
deployment change is opt-in.

### 7.1 Command surface — **D4: slash commands, with a text-prefix fallback**

`CommandInfo(prefix, name, description)` is already exactly what Discord's application-command
registration needs. `SlashCommandRegistrar` walks `CommandFactory.getAll()` on startup and registers
`/lobby`, `/register`, `/ban <query>` … (one `STRING` option named `args` for `DynamicCommand`s,
`required = true` — which makes Discord enforce the "argument present" guard client-side for free).

Slash commands also carry **autocomplete**, which is how `/ban`, `/d` and `/pick` get a good
"one of 89 leaders" experience without any component at all (§4.5).

Why slash and not message-prefix commands:
- Typing `/lobby` on Discord opens the slash picker; sending it as literal text is awkward UX.
- Reading message text in a guild needs the **privileged `MESSAGE_CONTENT` intent**. Slash commands
  don't.

Text fallback: keep a `discord.command-prefix` (default `!`) message handler for parity/debugging.
It needs `MESSAGE_CONTENT`; leave it off by default.

**Important:** the Herson free-text DM flow works **without** `MESSAGE_CONTENT` — Discord always
delivers content for DMs to the bot and for messages that mention it. So `DIRECT_MESSAGES` +
`GUILD_MESSAGES` intents suffice for the full draft.

### 7.2 Interaction plumbing

- Every button/select tap must be acknowledged within **3 s** → `event.deferEdit()` in the mapper,
  mapped to `ChatGateway.acknowledge()` (Telegram's `AnswerCallbackQuery` sits behind the same call).
- `Selection.RANKED` re-render is `hook.editOriginalComponents(...)` — the analogue of today's
  `EditMessageReplyMarkup`. Because the grid lives in a **DM** (D6a) and not an ephemeral reply, it
  can also be edited later by message id, which is what `lockHersonGrid` needs on submit.

### 7.3 Status message

**D5 — Status is a Discord embed, edited in place.** Renders the status text into `MessageEmbed.description`
(4096 chars, vs 2000 for content), posted with `MessageFlags.SUPPRESS_NOTIFICATIONS` (Discord's
`disable_notification`), and edited via `editMessageEmbedsById`. Milestone "backlinks" become real
Discord replies (`MessageCreateAction.setMessageReference`). The harness asserts on the embed
description, so `<status …>` scenario lines keep working.

### 7.4 Deliverability

`Optional.empty()` from `send` for a DM the bot cannot open (user has DMs off / no shared guild) —
same shape as today's Telegram `TelegramApiException` path, so the existing "couldn't DM you"
fallbacks in `sendHersonStart` / `SecretDraftStrategy` are reused untouched.

## 8. `AmbotorixService` (1676 lines) — split during the port

The port touches nearly every line of this class anyway, so splitting is close to free at that point.
Along seams already visible in the file:

| New service | Moves | ~lines |
|---|---|---|
| `LobbyFlowService` | `/lobby`, `/register`, `/terminate`, settings, map add/remove, status post/refresh/milestone | 350 |
| `BanFlowService` | `/ban`, `/banButtons`, `/clearBans`, ambiguity choices, `hersonBanRejection` | 180 |
| `SecretPickService` | `sendPick`, pick-status | 120 |
| `HersonFlowService` | the whole DM submit / confirm / grid / coin-flip / resolve block | 450 |
| `InfoService` | `/help`, `/leaders`, `/d`, `/mods`, `/settings`, `/time`, `/credits`, `/photochallenge` | 250 |

`DraftStrategy.execute(Lobby, ChatRef, DraftContext)` takes a narrow `DraftContext` instead of the
god service — which also drops `TelegramClient` out of `OpenDraftStrategy` / `SecretDraftStrategy`.

This phase (P3.5) is **optional and severable**: if the appetite runs out, a facade `AmbotorixService`
delegating to the port still works. It just stays a 1600-line class.

## 9. Testing

The `.chat` suite is the safety net for every phase, so it moves *early*, not last.

1. **P6 moves the harness to the port.** `TestChatGateway implements ChatGateway` and synthesizes
   `ChatEvent`s straight into `BotDispatcher` — strictly *less* fakery than today's
   `TestTelegramClient` (no `Update` tree to fabricate). The `.chat` files, `Scenario`, `LineMatcher`
   and the cursor model are unchanged; `HtmlNormalizer` becomes `MarkupNormalizer` with a per-platform
   strip.
2. **Scenarios parameterize over platform.** Each `.chat` file yields one `DynamicTest` per platform;
   the runner installs that platform's real text + component renderers behind the fake transport, so
   wire differences (mention syntax, 25-button chunking, 2000-char splitting) are exercised by every
   existing scenario. A `--- ONLY: telegram ---` header tag handles the rare platform-specific file.
3. **Adapter conformance tests** (new, small, per platform): `callback_data` ≤64 bytes;
   `custom_id` ≤100 chars; ≤5 action rows and ≤25 buttons per Discord message; ≤2000-char chunks;
   `ActionCodec` round-trip; **and the live roster still fits `DiscordChooserPolicy`** (§4.5 — this
   is the test that fails when BBG pushes past 100 leaders). Pure renderer tests: no network, no JDA.
4. **Renderer unit tests**: `HtmlToMarkdown` (nesting, entities, `<pre>` tables) and mention
   rewriting (in-table name → `<@id>`, unknown `@handle` left literal). These carry the weight that
   a structured text AST would otherwise have carried at compile time, so they are not optional.
5. **View unit tests**: `StatusView.render(lobby)` against a built `Lobby`, replacing today's
   only-through-scenarios coverage of a 100-line string builder.
6. The existing `TestTelegramClient` is retained for one thin Telegram-wire regression test until the
   Telegram adapter's conformance test supersedes it.

## 10. Phasing

Every phase ends with `./mvnw test` green and is independently mergeable.

Actual sequence used (P1 and P3 were merged, and P5's identity work landed with P1 because the
gateway signature needed it):

| # | Phase | Content | Risk |
|---|---|---|---|
| **P0** | Prep | `renderStatus` → `StatusView` (still returning HTML `String`); `PickImageGenerator` returns `byte[]`; add scenarios for the thin spots (`/banButtons`, map pool, admin) | none |
| **P1** | Gateway | Introduce `chat/` port types; `TelegramGateway` implements them; **all** `telegramClient.execute` calls move behind it (strategies + `NotificationService` included). Core still passes `Update` around | low |
| **P2** | Inbound | `ChatEvent` + `BotDispatcher`; `Command.execute(CommandContext)`; delete `org.telegram` from `commands/` and `core/` | medium — 28 mechanical file edits |
| **P3** | Components | `MarkupService` → `view/` producing `Component`s; `TelegramComponentRenderer` | low |
| **P3.5** | Split (optional) | Break up `AmbotorixService` per §8 | medium |
| **P4** | Text | `MentionTable` threaded onto `OutgoingMessage`; `parseMode` moves into the Telegram renderer. Message strings **unchanged** (D6) | low — two classes, no string churn |
| **P5** | Identity | `ChatRef`/`UserRef`/`MessageRef`, lobby tokens, re-keyed `LobbyService`, per-platform admin config | medium — touches persistence-free state everywhere |
| **P6** | Harness | Move to the port; parameterize scenarios (Discord renderer stubbed) | low |
| **P7** | Discord | JDA bean, slash registration + autocomplete, event mapper, gateway, renderers (`HtmlToMarkdown`, mention rewrite, select-menu chunking), embeds | the real work |
| **P8** | Dual-run | Config + Docker env, per-platform admin notifications, `/adminLobbies` across platforms, README + CLAUDE.md | low |

P0–P6 ship **no user-visible change** — that's deliberate: the whole refactor is verifiable by the
existing scenarios before a single Discord line is written.

## 11. Configuration after P8

```properties
bot.telegram.token=…
bot.telegram.username=@…
bot.telegram.admin-id=123456789

discord.token=…                    # absent → Discord adapter not created
discord.application-id=…
discord.admin-id=987654321
discord.command-prefix=!           # text fallback; needs MESSAGE_CONTENT intent
```

Discord setup steps for the README: create the application, invite with scopes `bot`
+ `applications.commands`, permissions *Send Messages / Embed Links / Attach Files / Read Message
History / Use Slash Commands*. `MESSAGE_CONTENT` only if the text-prefix fallback is enabled.

## 12. Open decisions

**Settled (previously open):**

- ~~O2 — how far to take a structured-text migration~~ → **D6**: no AST. Keep the HTML-subset strings and
  `@username` placeholders; rewrite both at the adapter edge. Reversible later.
- ~~O4 — Discord widget for 89 leaders~~ → **D7**: grouped select menus, chosen by an adapter-owned
  `ChooserPolicy`, plus slash autocomplete for the command paths. The core sees one `Chooser`.
- ~~O5 — ephemeral vs DM on Discord~~ → **D6a**: DMs on both platforms; ephemerals can't be edited
  after their interaction token expires, which the Herson grid needs.

**Still open:**

- **O1 — Player identity key.** `Player.equals` is by `userName`, and `HersonDraftState` maps are
  keyed by username. Discord usernames are unique but changeable, and a Telegram user may have none.
  Recommend switching the in-lobby key to `platform:userId` with the username kept for display.
  Fixes a real (if rare) bug class today; costs a pass over `HersonDraftState`. **Do it in P5?**
  Note this is now *also* what `MentionTable` keys off — resolving mentions by a stable id rather
  than a mutable handle makes D6 sturdier.
- **O3 — Do we run P3.5 (splitting `AmbotorixService`)?** Cheap *during* the port, expensive later.
- **O6 — Do Telegram and Discord players ever share a lobby?** Assumed **no**. If that's ever wanted,
  say so now: `ChatRef`-keyed lobbies would need to become a lobby with a *set* of chat bindings, and
  that is a materially different design.
- **O7 — The 100-leader ceiling** (§4.5). Accept the conformance test as the tripwire, or build
  letter-filter paging up front? Recommend the tripwire: 89 today, and BBG roster growth is slow.


## 13. What was built, and where it deviated

All phases are in `main` except the second half of §9 (scenarios are not yet parameterized over
platforms). The build is green at 91 tests: the 14 `.chat` scenarios plus unit tests for the views,
both Discord renderers, the lobby registry and the isolation rule.

**Merged phases.** P1 and P3 shipped together: defining `OutgoingMessage` before `Component` existed
would have forced it to carry an `InlineKeyboardMarkup` and be rewritten immediately. P5's identity
types (`ChatRef`/`UserRef`/`MessageRef`) landed with P1 for the same reason — the gateway signature
needs them. Only P5's *keying* (re-keying `LobbyService`, lobby tokens) was a separate step.

**Deviations worth knowing:**

- **`ChatGateway.acknowledge` was dropped.** Telegram's `answerCallbackQuery` needs the callback
  query id and Discord's `deferEdit` needs the interaction object — neither survives normalization
  into a `ChatEvent`. Both adapters now ack while mapping the event, before the dispatcher sees it,
  which is simpler and removes an obligation core code could forget. The port has no ack at all.
- **`Audience.Invoker` was dropped** (it only existed to model ephemeral replies, which D6a rejected).
  `Audience` is just `Channel | Direct`.
- **Lobby tokens turned out to be load-bearing for a second reason.** D2 justified them on payload
  length. The stronger reason emerged during P5: a payload carrying a bare channel id cannot
  reconstruct a lobby's *thread*, so a lobby in a Telegram forum topic would lose it on every button
  tap. A token resolves to the full `ChatRef`.
- **Packages were not moved into `core/`.** The rename is churn without benefit; `PlatformIsolationTest`
  enforces the dependency rule directly, which was the actual goal of D1. `BotDispatcher` sits in the
  root package where `Ambotorix.java` used to.
- **`/d_roosevelt_bull_moose` started working.** The old `DescriptionCommand` stripped *every*
  underscore before slicing, so multi-word shortnames never resolved when typed (buttons were fine).
  Splitting once, in the adapter, fixes it incidentally.
- **`/maplist` with no lobby now omits its buttons** instead of showing buttons that reported
  "already in the map pool" when tapped.
- **P3.5 (splitting `AmbotorixService`) was not done** — O3 stayed open. It is ~1500 lines and now
  has a clean seam under it, so it can be split whenever without touching the adapters.

**Not done: §9.2, scenarios across both platforms.** The `.chat` suite still drives
`TestTelegramClient`. Doing it properly means a `TestChatGateway` at the port seam plus a Discord
render pass, which is the natural next piece of work — it would exercise select-menu chunking and
mention rewriting through every existing scenario rather than only through unit tests.
