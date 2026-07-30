# Ambotorix

Chat bot for a local **Civilization 6** community, running on **Telegram and Discord at the same
time** from one process. It runs draft sessions in group chats: players join a *lobby*, ban and pick
leaders, get a random map and slot order, and (in secret mode) submit hidden picks. Pet project; not
commercial.

## Stack

- **Java 21**, **Spring Boot 3.4.3**, built with Maven (`./mvnw`).
- **telegrambots 8.2.0** (`org.telegram`) — long-polling bot, not webhooks.
- **JDA 5.6.1** (`net.dv8tion`) — Discord gateway client. Optional at runtime: no `discord.token`,
  no JDA bean, no Discord adapter.
- **Gson** for JSON, **Jsoup** for scraping leader data, **TwelveMonkeys imageio-webp** for
  decoding scraped `.webp` portraits, `java.awt`/`ImageIO` for composing pick images.
- No database — all lobby state lives in memory (`ConcurrentHashMap`). Restart = state lost.

## Build & run

```bash
./mvnw clean package           # build jar (tests run unless -DskipTests)
./mvnw test                    # unit tests only
docker compose up --build      # run via Docker (needs .env, see below)
```

Required config is supplied as Spring properties / env vars (the real
`src/main/resources/application.properties` is gitignored — it holds secrets):

- `bot.token` — Telegram bot token; **absent means the whole Telegram adapter is not created**
- `bot.username` — bot @username
- `bot.adminId` / `bot.admin.id` — Telegram user id allowed to run admin commands
- `discord.token` — Discord bot token; **absent means the whole Discord adapter is not created**
- `bot.discord-admin-id` — Discord user id allowed to run admin commands (admin rights do not carry
  across platforms)
- `discord.guild-id` (optional) — register slash commands to this one guild, which takes effect
  immediately; unset means global registration, which Discord can take an hour to propagate
- `data.dir` (default `src/main/resources`) — where leader data files are read/written
- `data.update.cron` (default `0 0 3 * * *`), `lobby.auto-terminate.hours` (default 4)

Both adapters are optional and independently conditional, so the bot runs Telegram-only,
Discord-only, or both. With neither configured `ChatGatewayRegistry` fails fast rather than starting
a bot that can't talk to anyone.

In Docker, `docker/entrypoint.sh` seeds a named volume from image defaults on first run so scraped
data (`civ6_leaders.json`, `leader_shortnames.json`, leader images) survives container rebuilds.

## Architecture

Everything lives under `src/main/java/vitbuk/com/Ambotorix/`.

### The chat port — how two platforms share one bot

Everything platform-specific lives in `adapters/`; everything else talks to the `chat/` **port**. A
test (`PlatformIsolationTest`) fails the build if `org.telegram.*` or `net.dv8tion.*` is imported
outside `adapters/`, which is what keeps the separation from rotting.

```
adapters/telegram/            adapters/discord/
  TelegramBot   (inbound)       DiscordBot    (inbound)
  TelegramGateway (outbound)    DiscordGateway (outbound)
  Telegram*Renderer             Discord*Renderer, DiscordSlashCommandRegistrar
          \                       /
           →  chat/ (the port)  ←
              ChatEvent · ChatGateway · OutgoingMessage · Component · ChatRef/UserRef/MessageRef
                                ↓
        BotDispatcher → commands/ → AmbotorixService → draft/ · view/ · entities/
```

Key port types (`chat/`):

- **`ChatRef` / `UserRef` / `MessageRef`** — platform-qualified addresses. Ids are strings; a
  `ChatRef` carries the channel plus any thread/topic.
- **`ChatEvent`** (sealed: `SlashCommand` | `FreeText` | `Interaction`) — normalized inbound. All
  parsing (`@botname`, `/cmd_arg` splitting, JDA option assembly, payload decoding) is the adapter's.
- **`ChatGateway`** — `send` / `editText` / `editComponents`. Synchronous on both platforms (JDA calls
  `.complete()`); `send` returns `Optional.empty()` for an unreachable DM, which is how the
  "couldn't DM you" fallbacks trigger. `ChatGatewayRegistry` routes by platform.
- **`Component`** (sealed: `Chooser` | `Actions`) — interactive UI described by **intent**, not widget.
- **`ActionRef`** — a button payload as `verb + args`, encoded per platform.

### Message text: one dialect, translated at the edge

Message strings are written **once**, in the Telegram HTML subset (`<b> <i> <code> <pre> <a>`), with
`@username` as a **mention placeholder**. Telegram sends that as-is. `DiscordTextRenderer` converts
the markup to Markdown, splits at 2000 chars, and rewrites `@name` → `<@id>` **only** for names in the
message's `MentionTable` (built from the lobby's players) — an unknown `@handle` like `@VitBuk` stays
literal. So there is no rich-text AST; `DiscordTextRendererTest` is what makes that safe.

### Components: the same declaration, different widgets

A view says *"a ranked chooser over these 89 leaders, plus Submit/Reset"*. Each adapter's policy
decides what that becomes:

| | Telegram | Discord |
|---|---|---|
| ≤25 options | inline keyboard | buttons, 5 per row |
| >25 options / ranked | inline keyboard, badge appended to the label | grouped `StringSelectMenu`s of 25, rank in the option description |

**Discord allows only 5 action rows**, so the roster (89 leaders) fills exactly 4 select menus plus
the Submit/Reset row. `DiscordComponentRendererTest.liveRosterStillFitsDiscordsFiveRowCeiling` fails
the build if the roster ever passes 100 — at which point the renderer needs letter-filter paging.

### Request flow

`BotDispatcher.handle(ChatEvent)` is the **central, platform-free dispatcher**:

1. `Interaction` (button/menu tap) → `AmbotorixService.handleInteraction`, which dispatches on the
   decoded `ActionRef` verb.
2. `SlashCommand` → look up the `Command` via `CommandFactory`, run the cross-cutting guards below,
   then `command.execute(ctx, ambotorixService)`.
3. `FreeText` **in a DM** → `AmbotorixService.handleDirectMessage`. This is how the Herson draft
   collects ranked picks. Group chatter is ignored, as before.

Adapters acknowledge interactions themselves (Telegram `answerCallbackQuery`, Discord `deferEdit`)
before the dispatcher sees the event, so no core code has to remember to.

### Commands (`commands/`)

Each command is a `@Component` implementing `Command` (`getInfo()` + `execute()`), with a
`CommandInfo(prefix, name, description)` record. Spring autowires all of them into `CommandFactory`,
which indexes them by prefix (e.g. `/ban`) and by class.

Authorization & validation is **declarative via marker interfaces** in `commands/structure/` —
the dispatcher checks `instanceof` and rejects early:

- `GeneralCommand` — anyone, anywhere (e.g. `/help`, `/leaders`, `/time`).
- `HostCommand` — requires a lobby in this chat *and* the caller is the host (e.g. `/start`,
  `/setDraft`, `/mapAdd`).
- `PlayerCommand` — requires a lobby *and* the caller is registered (e.g. `/ban`, `/pick`).
- `AdminCommand` — caller's id must equal `bot.adminId` (e.g. `/adminLobbies`).
- `DynamicCommand` — command takes an argument (e.g. `/ban [shortName]`); dispatcher enforces that
  an argument is present. Combined with the above, e.g. `PickCommand implements PlayerCommand, DynamicCommand`.

**To add a command:** create a `@Component` in `commands/`, implement `Command` + the appropriate
marker interface(s), and add the real logic as a `sendXxx` method on `AmbotorixService`. No manual
registration needed — and it appears on Discord too, since `DiscordSlashCommandRegistrar` publishes
`CommandInfo` as a slash command on startup (a `DynamicCommand` gets one required string option).

Commands receive a `CommandContext`: `chat()`, `user()` and `args()` — the argument is already split
off by the adapter, so commands never slice raw message text.

### Services (`services/`)

- **`AmbotorixService`** — the central facade / "god service". Commands delegate here; it owns all
  send logic (`sendMessage`, `sendToChat`, DMs, attachments — all via `ChatGatewayRegistry`), the
  `sendStart` draft kickoff, the `sendPick` secret-pick flow, and interaction routing. This is where
  most behavior lives. Still ~1500 lines; splitting it per flow is the obvious next refactor.
- **`LobbyService`** — in-memory lobby registry (`Map<ChatRef, Lobby>`, keyed by
  `ChatRef.channelKey()` so platforms can never collide); create/find/remove lobbies, registration,
  bans, map pool, random map/slot order, expiry lookup. Also mints each lobby a **6-char token**:
  button payloads carry that instead of a channel id, which keeps them inside Telegram's 64-byte
  `callback_data` and Discord's 100-char `custom_id`, and resolves back to the lobby's full address.
- **`LeaderService`** — loads/holds the leader list from `civ6_leaders.json` + `leader_shortnames.json`;
  builds per-player pick pools; resolves leaders by shortname.
- **`view/`** — pure renderers from domain objects to text and `Component`s: `StatusView` (the live
  lobby status), `LeaderGridView`, `BanChooserView`, `MapPoolView`, `PickPoolView`, `HersonPickView`.
  No platform types; unit-tested directly.
- **`DataUpdateService`** — checks civ6bbg.github.io for new BBG (Better Balanced Game) versions,
  re-scrapes leaders, writes data files, reloads `LeaderService`. Runs on startup and on
  `data.update.cron`; `/update` triggers it manually.
- **`NotificationService`** — DMs the admin on errors / newly-detected leaders.

### Draft strategies (`draft/`)

Strategy pattern. `DraftStrategy` implementations are `@Component`s collected by
`DraftStrategyFactory`, keyed by `getName()`:

- **`open`** (`OpenDraftStrategy`) — pick pools are posted publicly in the group as composed images.
- **`secret`** (`SecretDraftStrategy`) — pools are DM'd with pick buttons; players submit hidden
  picks via `/pick` or button; `onAllPicksIn` reveals everyone's choice once all are in.
- **`herson`** (`HersonDraftStrategy`) — ranked secret draft over the **full roster**, no per-player
  ban phase (only the **host** may ban, freely, any time before the draft closes — those civs are
  excluded from the pool). Players DM **four ranked picks** as free text (`1. X 2. Y 3. Z 4. W`);
  names are fuzzy-matched (`LeaderMatcher`) and the player confirms via buttons if anything was
  auto-corrected (or just re-sends a corrected list). Once everyone has submitted, the pure
  `HersonResolver` collapses the ranked lists into a unique assignment: a civ wanted by 2+ players is
  banned and the contestants fall through to their next pick; a clash that survives all four picks is
  broken by a **coin flip** (winner keeps it, the loser is DM'd to re-pick from the remaining pool).
  Picks stay hidden until fully resolved, then the result is revealed as a **portrait image** (via
  `PickImageGenerator`, one row per player with just their assigned leader) and the status closes out
  with a **contested-ban summary** (which civs were banned and who had ranked them, at what priority).
  The
  strategy itself is just the kickoff hook — the DM submission flow, confirm/re-pick handling and
  resolution orchestration live on `AmbotorixService`, and all per-lobby Herson state (ranked picks,
  per-player stage, resolver bans, assignments) lives in `HersonDraftState` on the `Lobby`.
  `HersonResolver`/`HersonPickParser` are pure and unit-tested. Inapplicable commands are rejected
  with a comment (e.g. `/pick` → "only in secret draft", a non-host `/ban` in Herson → "only the host
  can ban").
- **`herson-low`** (`HersonLowDraftStrategy`) — same DM submission flow as `herson`, stricter
  resolution: **any** civ ranked by 2+ players is banned outright, *regardless of priority*
  (`HersonResolver.resolveLow`), then each player keeps their highest surviving pick. Because every
  surviving civ is unique to one player there is never a clash — and so never a coin flip. The only
  failure mode is a player whose four picks were all banned; that's reported as `Unresolvable` and the
  draft stops with a message for the host to `/terminate` and re-run (rare enough not to auto-recover).
  The shared machinery treats both variants as Herson via `Lobby.isHersonDraft()`;
  `advanceHersonResolution` picks the resolver by strategy name.

A lobby's strategy is set with `/setDraft [open|secret|herson|herson-low]` (or up front via `/lobby [draft]`);
`sendStart` looks it up by name.
**To add a strategy:** implement `DraftStrategy` as a `@Component` with a unique `getName()`.

### Entities (`entities/`)

Plain mutable POJOs: `Lobby` (host, players, map pool, ban/pick sizes, draft state, pending picks,
optional `HersonDraftState`), `Player` (username, telegram id, picks, bans; equality by username),
`Leader` (full/short name, description, image path; equality by full name), `CivMap` (enum of all
maps + `STANDARD_MAPS`), `HersonDraftState` (Herson ranked-draft bookkeeping: per-player stage,
ranked picks, resolution bans/assignments).

### Other

- `PickImageGenerator` — composes a player's pick pool into PNG **bytes** via AWT; the adapter wraps
  them in a Telegram `SendPhoto` or a Discord `FileUpload`.
- `scheduler/LobbyCleanupScheduler` — `@Scheduled` every 15 min, auto-terminates lobbies older than
  `lobby.auto-terminate.hours` since `/start`.
- `scipts/LeaderScraper` — Jsoup scraper for civ6bbg leader pages (note: package is spelled `scipts`).

## Data files (`src/main/resources/`)

- `civ6_leaders.json` — scraped leader data (name, description, image path). Regenerated by updates.
- `leader_shortnames.json` — maps full leader names → short command aliases (e.g. for `/ban gandhi`).
- `leaderImages/*.png` — leader portraits used in generated pick images.
- `mods`, `settings` — static text blobs served by `/mods` and `/settings`.

## Conventions & gotchas

- The lobby key is the **channel** (`ChatRef.channelKey()`), not the thread: one lobby per Telegram
  group, which remembers the forum topic it posts into. Discord threads are channels in their own
  right, so they get per-thread lobbies for free.
- Interactions may originate in a DM, so every lobby-scoped payload carries the **lobby token**.
- `application.properties` and `application-*.properties` are gitignored (secrets); don't commit them.
- Lobby state is volatile/in-memory — there's no persistence layer; don't assume it survives restarts.
- Tests: the `.chat` **scenario suite** (`src/test/resources/scenarios/`) drives the real Spring
  wiring through `TestTelegramClient` and covers the draft flows end to end — it is the safety net
  for refactors. Alongside it: unit tests for `LobbyService`/`LeaderService`/draft resolvers, the
  pure views, both Discord renderers, and `PlatformIsolationTest`. **Still untested:** the Discord
  adapter's inbound mapping (no JDA test double yet), scraping, and image generation. The scenarios
  run only through the Telegram adapter — parameterizing them over both platforms is the next test
  investment (see MULTIPLATFORM_PLAN.md §9).
