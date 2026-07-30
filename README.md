# Ambotorix

Telegram bot for a local Civilization VI community. It runs leader-draft lobbies in a
group chat: players join a lobby, ban leaders, and the bot hands out pick pools (publicly
for open drafts, or privately via DM for secret drafts), then reveals the results. It also
serves reference info — leaders, maps, recommended mods and settings — and keeps the BBG
leader data up to date by scraping it on a schedule.

This is a pet project.

## Ambotorix Beta 0.5

What's new in this release:

- **Herson results revealed as a portrait image** (by Mihails). Instead of listing the resolved
  picks as text, the draft now posts a combined portrait image — one row per player with their
  assigned leader — mirroring the open/secret draft reveals. The status message closes out with just
  the contested-ban summary; it falls back to a text reveal if image generation fails.
- **`herson-low` draft variant** (by Mihails) — selectable via `/lobby herson-low` or
  `/setDraft herson-low`. A stricter Herson: it bans **any** civ that two or more players ranked,
  regardless of priority, so every surviving civ belongs to exactly one player — no clashes and no
  coin flips, each player simply keeps their highest surviving pick. The only failure mode is a
  player whose four picks were all banned; the draft then stops with a message for the host to
  `/terminate` and re-run.
- **Draft DMs now show the selected map.** The map rolled for the lobby is persisted and displayed
  in the open- and secret-draft DMs, so players see what they're drafting for.
- **Clearer pick status.** During secret/Herson drafts the status message now lists who has already
  submitted and who is still being waited on, instead of just a count.
- **`/leaders` compact grid.** The leader list is rendered as a 3-column grid using short display
  names (e.g. `Lincoln`, `Kublai (China)`), so it scans far more easily.
- **`/mods` and `/settings` now post in the group chat** instead of DM, so everyone sees the agreed
  mod list and settings at once.
- **Refreshed `/mods` list** to match the current multiplayer setup, each as a tappable Steam
  Workshop link: Better Balanced Game 7.5 Beta, BBG Expanded 2.0, Multiplayer Helper, and Better
  Balanced Maps.
- **Smarter `/ban` matching.** An exact short-name now always wins, even when that word also appears
  in another leader's name — e.g. `/ban nzinga` bans Mvemba a Nzinga directly instead of asking you
  to disambiguate it against Nzinga Mbande. Matching priority is now: exact short name → exact
  name-word → prefix → fuzzy.
- **Short-name fixes:** `khan_china` → `kublai_china`, and the Eleanor of Aquitaine variants
  `aquitaine_*` → `eleanor_*`.

## Ambotorix Beta 0.4

What's new in this release:

- **Herson ranked secret draft** (new draft strategy, by Mihails). A ranked secret draft over
  the full roster: players DM four ranked picks as free text (`1. X 2. Y 3. Z 4. W`); names are
  fuzzy-matched and confirmed via buttons when auto-corrected. Once everyone submits, a pure
  resolver collapses the lists into a unique assignment — a civ wanted by 2+ players is banned and
  contestants fall through to their next pick; a clash surviving all four picks is broken by a coin
  flip. Picks stay hidden until resolved, then the status reveals each player's civ plus a
  contested-ban summary. Herson has no per-player ban phase (only the host may ban, freely, before
  the draft closes).
- **`/lobby <strategy>`** — `/lobby` now takes an optional draft-strategy name (e.g. `/lobby secret`)
  so the host can pick the draft up front instead of a separate `/setDraft`; an unknown name falls
  back to the default with a note.
- **`/photochallenge`** — posts the community photo-challenge leaderboard to the group. The bot
  reads the standings from the shared Google Sheet (the "Main" tab) and reprints them as a
  monospace table (Player / Total / Leaders / City-States / Wonders). The sheet stays the source
  of truth; the sheet CSV URL is configurable via `photochallenge.sheet.csv-url`.
- **`/mods`** now shows each mod as a tappable Steam Workshop link (no version numbers, since
  those drift over time).

## Ambotorix Beta 0.3

What's new in this release:

- **Less group-chat spam on draft start.** Removed the standalone "🎮 Draft started" message.
  The draft now pings the group exactly once:
  - **Open draft** posts the combined picks image as a reply to the status message, captioned
    with `@`-mentions of every player (no more pool-listing text — the image shows the pools).
  - **Secret draft** posts a single tag reply to the status message telling players to check
    their DMs to pick.
- Both pings reply to (backlink) the status message, so players can tap to jump to slot order
  and picks.
- Kept the "🎉 All picks are in" reveal and the secret-draft DM-failure fallback notices.
- **Lobbies auto-terminate 30 minutes after start** (was 4 hours). Config key is now
  `lobby.auto-terminate.minutes`; the cleanup check runs every 5 minutes.
- **Smart bans.** `/ban` now accepts loose input: `/ban alex` bans Alexander, `/ban teresa`
  bans Theresa despite the typo. Matching is tiered (exact → prefix → fuzzy), and when a query
  matches several leaders (e.g. `roosevelt`, `eleanor`, `qin`) the bot replies with buttons to
  pick the right one rather than guessing.

## Ambotorix Beta 0.2

What's new in this release:

- **Single live status message.** The lobby keeps one message (settings, players, bans,
  slot order, picks) that is edited in place as things change, instead of posting a new line
  for every join, ban, config tweak and pick. Only milestones ("draft started", "all picks
  in") ping the group.
- **One combined pick-pools image** for open drafts — a row per player — instead of one post
  per player.
- **Quieter group chat.** Info and host-tuning commands (`/help`, `/leaders`, `/maplist`,
  `/mappool`, `/mods`, `/settings`, map/config commands, etc.) now reply in DM instead of
  spamming the group.
- **`/clearBans`** (host) — clears all bans so players can redo them.
- **`/banButtons`** — every registered player gets DM buttons for each not-yet-banned leader;
  tapping one bans that leader and updates the group status message.
- **`/mappool` remove buttons** — tap to remove a map from the pool, sent to DM.
- **Quick start** section at the top of `/help`.
- **Bug fix:** `/mapRemove_<Map>` now works, and maps can no longer be added twice.

See [TODO.md](TODO.md) for what's planned next.

## Running it

The bot is containerised. With Docker and a populated `.env` (bot token, username, admin id):

```bash
docker compose up --build -d
```

### Running on Discord too

The bot speaks Telegram and Discord from one process, with one copy of the lobby/draft logic. Each
adapter is independently optional — set `bot.token` for Telegram, `discord.token` for Discord, or both.
An unconfigured platform's adapter simply isn't created; with neither set the bot refuses to start
rather than running mute.

In Docker these go in `.env` alongside the Telegram ones (Spring maps the env var names onto the
property names automatically):

```bash
DISCORD_TOKEN=...            # enables the Discord adapter
BOT_DISCORD_ADMIN_ID=...     # admin rights are per platform; a Telegram admin id means nothing on Discord
DISCORD_GUILD_ID=...         # optional: register commands to one server, instantly (see below)
```

Running without Docker, the same three as properties in `src/main/resources/application.properties`:

```properties
discord.token=...
bot.discord-admin-id=...
discord.guild-id=...
```

After editing `.env`, use `docker compose up -d` — `docker compose restart` reuses the old
environment and your change will appear to have no effect.

**Commands not showing up?** Global slash-command registration can take Discord up to an hour to
propagate, and until it does, typing `/lobby` just sends a plain message and the bot looks dead. Set
`discord.guild-id` to your server's id (Developer Mode on → right-click the server → Copy Server ID)
and commands register to that server immediately. Leave it unset for a real deployment.

Set up the application at <https://discord.com/developers/applications>, then invite the bot with the
`bot` and `applications.commands` scopes and these permissions: **Send Messages**, **Embed Links**,
**Attach Files**, **Read Message History**. No privileged intents are required — commands arrive as
slash-command interactions, and Discord always delivers message content in DMs (which is how Herson
ranked picks are submitted).

Commands register themselves as slash commands on startup, so `/lobby`, `/register`, `/ban …` behave
the same on both platforms. Two things look different because the platforms differ, not the logic:
the lobby status is a Discord embed, and the 89-leader pick grid becomes grouped dropdown menus
(Discord caps a message at 25 buttons).

Lobbies never span platforms — a Telegram group and a Discord channel each host their own.

### Updating data files (`mods`, `settings`, etc.) on an existing deployment

`src/main/resources` is a named Docker volume that the entrypoint **only seeds on first run**
(when `civ6_leaders.json` is absent). So a plain `git pull` + `docker compose up --build` does
**not** refresh files like `mods` or `settings` that already exist in the volume — the old copies
persist. To push an updated data file to a running deployment, copy it into the volume and restart:

```bash
docker compose cp src/main/resources/mods ambotorix:/app/src/main/resources/mods
docker compose restart
```

(Recreating the volume also works but re-triggers the full leader scrape on next start.)
