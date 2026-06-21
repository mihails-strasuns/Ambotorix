# Ambotorix

Telegram bot for a local **Civilization 6** community. It runs draft sessions in Telegram group
chats: players join a *lobby*, ban and pick leaders, get a random map and slot order, and (in
secret mode) submit hidden picks. Pet project; not commercial.

## Stack

- **Java 21**, **Spring Boot 3.4.3**, built with Maven (`./mvnw`).
- **telegrambots 8.2.0** (`org.telegram`) — long-polling bot, not webhooks.
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

- `bot.token` — Telegram bot token
- `bot.username` — bot @username
- `bot.adminId` / `bot.admin.id` — Telegram user id allowed to run admin commands
- `data.dir` (default `src/main/resources`) — where leader data files are read/written
- `data.update.cron` (default `0 0 3 * * *`), `lobby.auto-terminate.hours` (default 4)

In Docker, `docker/entrypoint.sh` seeds a named volume from image defaults on first run so scraped
data (`civ6_leaders.json`, `leader_shortnames.json`, leader images) survives container rebuilds.

## Architecture

Everything lives under `src/main/java/vitbuk/com/Ambotorix/`.

### Request flow

`Ambotorix.java` is the bot entry point (`implements SpringLongPollingBot`). Its `consume(Update)`
is the **central dispatcher**:

1. Callback queries (inline-button taps) → `AmbotorixService.makeCallbackQuery`.
2. Text messages starting with `/` → strip `@botname`, look up the `Command` via `CommandFactory`,
   then run **cross-cutting guards based on which marker interface the command implements** before
   calling `command.execute(update, ambotorixService)`.
3. Free text (no leading `/`) **in a private chat** → `AmbotorixService.handleDirectMessage`. This is
   how the Herson draft collects ranked picks: it finds the in-progress Herson lobby the user owes
   input on (`LobbyService.findHersonAwaitingChatId`) and routes by the player's `HersonDraftState`
   stage. Group chatter without a slash is ignored, as before.

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
registration needed.

### Services (`services/`)

- **`AmbotorixService`** — the central facade / "god service". Commands delegate here; it owns all
  Telegram send logic (`sendMessage`, `sendToChat`, photos, DMs), the `sendStart` draft kickoff, the
  `sendPick` secret-pick flow, and callback routing. This is where most behavior lives.
- **`LobbyService`** — in-memory lobby registry (`Map<Long chatId, Lobby>`); create/find/remove
  lobbies, registration, bans, map pool, random map/slot order, expiry lookup.
- **`LeaderService`** — loads/holds the leader list from `civ6_leaders.json` + `leader_shortnames.json`;
  builds per-player pick pools; resolves leaders by shortname.
- **`MarkupService`** — builds Telegram inline keyboards. Callback data encodes the command prefix +
  args (e.g. `/pick <chatId> <shortName>`, `/d <shortName>`), parsed back in `makeCallbackQuery`.
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

A lobby's strategy is set with `/setDraft [open|secret|herson]` (or up front via `/lobby [draft]`);
`sendStart` looks it up by name.
**To add a strategy:** implement `DraftStrategy` as a `@Component` with a unique `getName()`.

### Entities (`entities/`)

Plain mutable POJOs: `Lobby` (host, players, map pool, ban/pick sizes, draft state, pending picks,
optional `HersonDraftState`), `Player` (username, telegram id, picks, bans; equality by username),
`Leader` (full/short name, description, image path; equality by full name), `CivMap` (enum of all
maps + `STANDARD_MAPS`), `HersonDraftState` (Herson ranked-draft bookkeeping: per-player stage,
ranked picks, resolution bans/assignments).

### Other

- `PickImageGenerator` — composes a player's pick pool into a single PNG via AWT.
- `scheduler/LobbyCleanupScheduler` — `@Scheduled` every 15 min, auto-terminates lobbies older than
  `lobby.auto-terminate.hours` since `/start`.
- `scipts/LeaderScraper` — Jsoup scraper for civ6bbg leader pages (note: package is spelled `scipts`).

## Data files (`src/main/resources/`)

- `civ6_leaders.json` — scraped leader data (name, description, image path). Regenerated by updates.
- `leader_shortnames.json` — maps full leader names → short command aliases (e.g. for `/ban gandhi`).
- `leaderImages/*.png` — leader portraits used in generated pick images.
- `mods`, `settings` — static text blobs served by `/mods` and `/settings`.

## Conventions & gotchas

- Group chat id is the lobby key. Callbacks may originate in a DM, so callback data carries the
  group `chatId` explicitly so the right lobby is found.
- `application.properties` and `application-*.properties` are gitignored (secrets); don't commit them.
- Lobby state is volatile/in-memory — there's no persistence layer; don't assume it survives restarts.
- Tests (`src/test/java/...`) are unit-level and cover only the pure/in-memory logic of
  `LobbyService`, `LeaderService`, the `DataUpdateService` string helpers, and the draft factory.
  The Telegram-facing layer — the `Ambotorix` dispatcher/auth guards, `AmbotorixService`, commands,
  draft `execute()` flows, scraping, and image generation — is currently untested.
