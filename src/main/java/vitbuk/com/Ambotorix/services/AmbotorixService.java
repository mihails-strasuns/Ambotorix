package vitbuk.com.Ambotorix.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.PickImageGenerator;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.Attachment;
import vitbuk.com.Ambotorix.chat.ChatEvent;
import vitbuk.com.Ambotorix.chat.ChatGatewayRegistry;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.MentionTable;
import vitbuk.com.Ambotorix.chat.MessageRef;
import vitbuk.com.Ambotorix.chat.OutgoingMessage;
import vitbuk.com.Ambotorix.chat.UserRef;
import vitbuk.com.Ambotorix.chat.ui.Component;
import vitbuk.com.Ambotorix.commands.*;
import vitbuk.com.Ambotorix.commands.structure.AdminCommand;
import vitbuk.com.Ambotorix.commands.structure.Command;
import vitbuk.com.Ambotorix.commands.structure.GeneralCommand;
import vitbuk.com.Ambotorix.commands.structure.HostCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandFactory;
import vitbuk.com.Ambotorix.draft.DraftStrategy;
import vitbuk.com.Ambotorix.draft.DraftStrategyFactory;
import vitbuk.com.Ambotorix.draft.HersonPickParser;
import vitbuk.com.Ambotorix.draft.HersonResolver;
import vitbuk.com.Ambotorix.matching.LeaderMatcher;
import vitbuk.com.Ambotorix.matching.LeaderMatcher.MatchResult;
import vitbuk.com.Ambotorix.photochallenge.PhotoChallengeService;
import vitbuk.com.Ambotorix.entities.CivMap;
import vitbuk.com.Ambotorix.entities.HersonDraftState;
import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.entities.Player;
import vitbuk.com.Ambotorix.view.BanChooserView;
import vitbuk.com.Ambotorix.view.HersonPickView;
import vitbuk.com.Ambotorix.view.LeaderGridView;
import vitbuk.com.Ambotorix.view.MapPoolView;
import vitbuk.com.Ambotorix.view.PickPoolView;
import vitbuk.com.Ambotorix.view.StatusView;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class    AmbotorixService {
    private static final Logger log = LoggerFactory.getLogger(AmbotorixService.class);

    private final ChatGatewayRegistry chat;
    private final LeaderService leaderService;
    private final LobbyService lobbyService;
    private final CommandFactory commandFactory;
    private final DataUpdateService dataUpdateService;
    private final DraftStrategyFactory draftStrategyFactory;
    private final LeaderMatcher leaderMatcher;
    private final PhotoChallengeService photoChallengeService;
    private final LeaderGridView leaderGridView;
    private final BanChooserView banChooserView;
    private final MapPoolView mapPoolView;
    private final PickPoolView pickPoolView;
    private final HersonPickView hersonPickView;

    @Value("${data.dir:src/main/resources}")
    private String dataDir;

    @Autowired
    public AmbotorixService(ChatGatewayRegistry chat, LeaderService leaderService, LobbyService lobbyService,
                            CommandFactory commandFactory, DataUpdateService dataUpdateService,
                            DraftStrategyFactory draftStrategyFactory, LeaderMatcher leaderMatcher,
                            PhotoChallengeService photoChallengeService, LeaderGridView leaderGridView,
                            BanChooserView banChooserView, MapPoolView mapPoolView, PickPoolView pickPoolView,
                            HersonPickView hersonPickView) {
        this.chat = chat;
        this.leaderService = leaderService;
        this.lobbyService = lobbyService;
        this.commandFactory = commandFactory;
        this.dataUpdateService = dataUpdateService;
        this.draftStrategyFactory = draftStrategyFactory;
        this.leaderMatcher = leaderMatcher;
        this.photoChallengeService = photoChallengeService;
        this.leaderGridView = leaderGridView;
        this.banChooserView = banChooserView;
        this.mapPoolView = mapPoolView;
        this.pickPoolView = pickPoolView;
        this.hersonPickView = hersonPickView;
    }

    //logic for command -> credits
    public void sendCredits(CommandContext ctx) {
        sendPrivateMessage(ctx, "Bot is created by @VitBuk\nhttps://github.com/VitBuk");
    }

    //logic for command -> discord
    public void sendDiscord(CommandContext ctx) {
        sendPrivateMessage(ctx, "Our discord server: \nJoin our Discord: https://discord.gg/2h425TExSt");
    }

    //logic for command -> /photochallenge — posts the leaderboard to the group (a shared challenge)
    public void sendPhotoChallenge(CommandContext ctx) {
        sendMessage(ctx, photoChallengeService.leaderboardMessage());
    }
    //logic for command -> /update
    public void sendUpdate(CommandContext ctx) {
        sendMessage(ctx, "Checking for BBG updates...");
        String result = dataUpdateService.checkAndUpdate();
        sendMessage(ctx, result);
    }

    //logic for command -> help
    public void sendHelp(CommandContext ctx) {
        List<Command> all = commandFactory.getAll();
        Comparator<Command> byPrefix = Comparator.comparing(c -> c.getInfo().prefix(), String.CASE_INSENSITIVE_ORDER);

        // General: /lobby, /lobbyInfo, /help, /leaders, alpha, /credits last
        List<Command> general = all.stream()
                .filter(c -> c instanceof GeneralCommand)
                .sorted((a, b) -> {
                    if (a instanceof LobbyCommand) return -1;
                    if (b instanceof LobbyCommand) return 1;
                    if (a instanceof LobbyInfoCommand) return -1;
                    if (b instanceof LobbyInfoCommand) return 1;
                    if (a instanceof HelpCommand) return -1;
                    if (b instanceof HelpCommand) return 1;
                    if (a instanceof LeadersCommand) return -1;
                    if (b instanceof LeadersCommand) return 1;
                    if (a instanceof CreditsCommand) return 1;
                    if (b instanceof CreditsCommand) return -1;
                    return a.getInfo().prefix().compareToIgnoreCase(b.getInfo().prefix());
                }).toList();

        List<Command> hostCmds = all.stream()
                .filter(c -> c instanceof HostCommand)
                .sorted((a, b) -> {
                    if (a instanceof StartCommand) return -1;
                    if (b instanceof StartCommand) return 1;
                    return a.getInfo().prefix().compareToIgnoreCase(b.getInfo().prefix());
                }).toList();

        List<Command> playerCmds = all.stream()
                .filter(c -> !(c instanceof GeneralCommand))
                .filter(c -> !(c instanceof AdminCommand))
                .filter(c -> !(c instanceof HostCommand))
                .sorted((a, b) -> {
                    if (a instanceof RegisterCommand) return -1;
                    if (b instanceof RegisterCommand) return 1;
                    return a.getInfo().prefix().compareToIgnoreCase(b.getInfo().prefix());
                }).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("<b>Quick start:</b>\n")
          .append(commandFactory.infoOf(LobbyCommand.class).name()).append(" – create lobby\n")
          .append(commandFactory.infoOf(RegisterCommand.class).name()).append(" – register to the lobby\n")
          .append(commandFactory.infoOf(BanButtonsCommand.class).name()).append(" – get buttons to DM, click one to ban a leader\n")
          .append(commandFactory.infoOf(StartCommand.class).name()).append(" – start game (host only)\n");

        sb.append("\n<b>General:</b>\n");
        general.forEach(c -> sb.append(c.getInfo().name()).append(" – ").append(c.getInfo().description()).append('\n'));

        sb.append("\n<b>Host commands:</b>\n");
        hostCmds.forEach(c -> sb.append(c.getInfo().name()).append(" – ").append(c.getInfo().description()).append('\n'));

        sb.append("\n<b>Player commands:</b>\n");
        playerCmds.forEach(c -> sb.append(c.getInfo().name()).append(" – ").append(c.getInfo().description()).append('\n'));

        sendPrivateMessage(ctx, sb.toString());
    }

    // logic for command -> /lobby
    public void sendLobby(CommandContext ctx) {
        sendLobby(ctx, (String) null);
    }

    // logic for command -> /lobby [draftName]. The optional argument pre-selects the draft strategy.
    public void sendLobby(CommandContext ctx, String draftName) {
        ChatRef chatId = ctx.chat();
        Player host = new Player(ctx.user().userName(), ctx.user().asTelegramUserId());

        boolean existed = lobbyService.hasLobby(chatId);
        String message = lobbyService.createLobby(chatId, host);
        if (existed) {
            sendMessage(ctx, message); // "lobby already exists" — a real error, post it
            return;
        }

        if (draftName != null && !draftName.isBlank()) {
            String wanted = draftName.trim().toLowerCase();
            if (draftStrategyFactory.getStrategyNames().contains(wanted)) {
                lobbyService.getLobby(chatId).setDraftStrategyName(wanted);
            } else {
                sendToChat(chatId, "Unknown draft \"" + draftName.trim() + "\". Available: "
                        + String.join(", ", draftStrategyFactory.getStrategyNames()) + ". Keeping default.");
            }
        }

        // Roll the map up front so it's known before anyone bans or picks — it informs the draft.
        lobbyService.rollMap(chatId);

        // Fresh lobby: instead of a throwaway "created" line, post the single live status message
        // that we keep edited for the rest of the session.
        postStatus(chatId);
    }

    // logic for command -> /leaders
    public void sendLeaders(CommandContext ctx) {
        List<Leader> leaders = leaderService.getLeaders();

        StringBuilder sb = new StringBuilder("Leaders: \n");
        sb.append("<i>To get description use /d_[shortName] \n")
                .append("command or buttons below: </i>");

        chat.send(OutgoingMessage.to(ctx.user())
                .text(sb.toString())
                .component(leaderGridView.grid(leaders))
                .build());
    }

    //logic for command -> /mods
    public void sendMods(CommandContext ctx) {
        sendMessage(ctx, allLines(dataDir + "/mods"));
    }

    //logic for command -> /settings
    public void sendSettings(CommandContext ctx) {
        sendMessage(ctx, allLines(dataDir + "/settings"));
    }

    //logic for command -> /d_[shortName]
    public void sendDescription (ChatEvent event, String shortName){
        log.debug("sendDescription triggered for shortName: {}", shortName);

        List<Leader> leaders = leaderService.getLeaders();

        for (Leader l : leaders) {
            if (l.getShortName().equalsIgnoreCase(shortName)) {
                byte[] portrait = readPortrait(l);
                if (portrait != null) {
                    chat.send(OutgoingMessage.to(event.from())
                            .text("<b>" + l.getFullName() + "</b>")
                            .attachment(Attachment.png(l.getShortName() + ".png", portrait))
                            .build());
                }

                String formattedDescription = "<b>ShortName:</b> " + l.getShortName() + "\n\n"
                        + leaderService.formatDescription(l.getDescription());
                sendPrivateMessage(event, formattedDescription);
                return;
            }
        }

        sendPrivateMessage(event, "Unknown leader. Use " + commandFactory.infoOf(LeadersCommand.class).name() + " to see available description command");
    }

    /**
     * A button tap or menu selection. The payload was already decoded into a verb plus arguments by
     * the adapter, so this is a plain dispatch — no per-platform payload string parsing, and no
     * "is this a number" guessing about the lobby id.
     */
    public void handleInteraction(ChatEvent.Interaction event) {
        ActionRef action = event.action();
        String verb = action.verb();

        if (verb.equals(commandFactory.infoOf(DescriptionCommand.class).prefix())) {
            sendDescription(event, action.arg(0));
        } else if (verb.equals(commandFactory.infoOf(MapAddCommand.class).prefix())) {
            withLobbyAndMap(event, action, "/maplist", (lobbyChatId, map) -> sendMapAdd(event, map, lobbyChatId));
        } else if (verb.equals(commandFactory.infoOf(MapRemoveCommand.class).prefix())) {
            withLobbyAndMap(event, action, "/mappool", (lobbyChatId, map) -> sendMapRemove(event, map, lobbyChatId));
        } else if (verb.equals(commandFactory.infoOf(BanCommand.class).prefix())) {
            ChatRef lobbyChatId = lobbyIdOf(event, action, "/banButtons");
            if (lobbyChatId != null) sendBanFromCallback(event, lobbyChatId, action.arg(1));
        } else if (verb.equals(PickPoolView.PICK_VERB)) {
            ChatRef lobbyChatId = lobbyIdOf(event, action, "/start");
            if (lobbyChatId != null) sendPick(event, lobbyChatId, action.arg(1));
        } else if (verb.equals(HersonPickView.CONFIRM_VERB) || verb.equals(HersonPickView.REDO_VERB)) {
            ChatRef lobbyChatId = lobbyIdOf(event, action, null);
            if (lobbyChatId != null) hersonConfirmCallback(event, lobbyChatId, verb.equals(HersonPickView.CONFIRM_VERB));
        } else if (verb.equals(HersonPickView.PICK_VERB)) {
            ChatRef lobbyChatId = lobbyIdOf(event, action, null);
            if (lobbyChatId != null) handleHersonGridPick(event, lobbyChatId, action.arg(1));
        } else if (verb.equals(HersonPickView.SUBMIT_VERB)) {
            ChatRef lobbyChatId = lobbyIdOf(event, action, null);
            if (lobbyChatId != null) handleHersonGridSubmit(event, lobbyChatId);
        } else if (verb.equals(HersonPickView.RESET_VERB)) {
            ChatRef lobbyChatId = lobbyIdOf(event, action, null);
            if (lobbyChatId != null) handleHersonGridReset(event, lobbyChatId);
        }
    }

    /**
     * First argument of every lobby-scoped action: the token of the lobby the button belongs to.
     * Null means the lobby is gone — the button outlived it, which is the common case for a message
     * still sitting in someone's DM history.
     */
    private ChatRef lobbyIdOf(ChatEvent event, ActionRef action, String staleHint) {
        ChatRef lobbyChat = lobbyService.resolveToken(action.arg(0));
        if (lobbyChat == null) {
            sendPrivateMessage(event, staleHint == null
                    ? "This button is outdated."
                    : "This button is outdated. Please use " + staleHint + " again.");
        }
        return lobbyChat;
    }

    private void withLobbyAndMap(ChatEvent event, ActionRef action, String staleHint,
                                 java.util.function.BiConsumer<ChatRef, CivMap> then) {
        ChatRef lobbyChatId = lobbyIdOf(event, action, staleHint);
        if (lobbyChatId == null) return;
        Optional<CivMap> map = action.arg(1) == null
                ? Optional.empty() : CivMap.fromDisplayNameIgnoreCase(action.arg(1));
        if (map.isEmpty()) {
            sendPrivateMessage(event, "Unknown map: " + action.arg(1));
            return;
        }
        then.accept(lobbyChatId, map.get());
    }

    //logic for command -> /clearBans
    public void sendClearBans(CommandContext ctx) {
        ChatRef chatId = ctx.chat();
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null) { sendNoLobby(ctx); return; }
        if (lobby.isDraftInProgress()) {
            sendPrivateMessage(ctx, "Cannot clear bans while draft is in progress.");
            return;
        }
        lobbyService.clearAllBans(chatId);
        refreshStatus(chatId);
    }

    /**
     * Herson has no per-player ban phase — only the host may ban, and only before the draft closes.
     * Returns a rejection message to show the caller, or null if allowed (or this isn't a Herson lobby).
     */
    private String hersonBanRejection(ChatRef chatId, String userName) {
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null || !lobby.isHersonDraft()) return null;
        if (!lobbyService.isHost(chatId, userName)) return "Only the host can ban civs in a Herson draft.";
        if (lobby.isDraftStarted() && !lobby.isDraftInProgress()) return "The draft is closed — bans can no longer be changed.";
        return null;
    }

    //logic for command -> /banButtons
    public void sendBanButtons(CommandContext ctx) {
        ChatRef chatId = ctx.chat();
        String userName = ctx.user().userName();
        Player player = lobbyService.findPlayerByName(chatId, userName);

        if (player == null) { sendPrivateMessage(ctx, "You are not registered in this lobby."); return; }
        String hersonReject = hersonBanRejection(chatId, userName);
        if (hersonReject != null) { sendPrivateMessage(ctx, hersonReject); return; }

        // In Herson the host bans freely (no slots); elsewhere the per-player ban slots apply.
        boolean herson = lobbyService.getLobby(chatId).isHersonDraft();
        if (!herson && !lobbyService.hasAvailableBans(chatId, player)) {
            sendPrivateMessage(ctx, "You have no ban slots remaining.");
            return;
        }

        List<Leader> available = leaderService.getLeaders().stream()
                .filter(l -> !lobbyService.isBanned(chatId, l))
                .toList();

        if (available.isEmpty()) {
            sendPrivateMessage(ctx, "All leaders have already been banned.");
            return;
        }

        chat.send(OutgoingMessage.to(ctx.user())
                .text("Choose a leader to ban:")
                .component(banChooserView.chooser(available, lobbyService.getLobby(chatId).getToken()))
                .build());
    }

    // Called from DM callback — lobbyChatId is the group chat where the lobby lives
    public void sendBanFromCallback(ChatEvent event, ChatRef lobbyChatId, String shortName) {
        String userName = event.from().userName();
        Player player = lobbyService.findPlayerByName(lobbyChatId, userName);

        if (!lobbyService.isRegistered(lobbyChatId, userName)) {
            sendPrivateMessage(event, "You are not registered in this lobby.");
            return;
        }
        String hersonReject = hersonBanRejection(lobbyChatId, userName);
        if (hersonReject != null) { sendPrivateMessage(event, hersonReject); return; }
        Leader leader = leaderService.getLeaderByShortName(shortName);
        if (leader == null) {
            sendPrivateMessage(event, "Unknown leader: " + shortName);
            return;
        }
        applyBan(event, lobbyChatId, userName, player, leader);
    }

    //logic for command -> /ban [query] — smart, forgiving leader matching
    public void sendSmartBan(CommandContext ctx, String query) {
        ChatRef chatId = ctx.chat();
        String userName = ctx.user().userName();
        Player player = lobbyService.findPlayerByName(chatId, userName);

        if (player == null || !lobbyService.isRegistered(chatId, userName)) {
            sendPrivateMessage(ctx, "You are not registered in this lobby.");
            return;
        }
        String hersonReject = hersonBanRejection(chatId, userName);
        if (hersonReject != null) { sendPrivateMessage(ctx, hersonReject); return; }
        if (!lobbyService.hasAvailableBans(chatId, player) && !lobbyService.isHost(chatId, userName)) {
            sendPrivateMessage(ctx, "You have no ban slots remaining.");
            return;
        }

        MatchResult result = leaderMatcher.match(query, leaderService.getLeaders());
        switch (result) {
            case MatchResult.Unique u -> applyBan(ctx.event(), chatId, userName, player, u.leader());
            case MatchResult.Ambiguous a -> sendBanChoices(ctx.event(), chatId, a.leaders(), query);
            case MatchResult.None n -> sendPrivateMessage(ctx, "No leader matches \"" + query.trim()
                    + "\". Use " + commandFactory.infoOf(BanButtonsCommand.class).name() + " to pick from a list.");
        }
    }

    /** Shared ban tail: already-banned + slot checks, then add the ban, refresh status, confirm in DM. */
    private void applyBan(ChatEvent event, ChatRef chatId, String userName, Player player, Leader leader) {
        if (lobbyService.isBanned(chatId, leader)) {
            sendPrivateMessage(event, leader.getFullName() + " is already banned.");
            return;
        }
        if (!lobbyService.hasAvailableBans(chatId, player) && !lobbyService.isHost(chatId, userName)) {
            sendPrivateMessage(event, "You have no ban slots remaining.");
            return;
        }
        player.getBans().add(leader);
        // Bans show up in the live status message instead of a per-ban announcement; the DM confirms
        // which leader was banned (important when a fuzzy/prefix query was interpreted).
        refreshStatus(chatId);
        sendPrivateMessage(event, "✅ Banned " + leader.getFullName() + ".");
    }

    /** Ambiguous query: offer a button per candidate in DM (group fallback if the user isn't DM-reachable). */
    private void sendBanChoices(ChatEvent event, ChatRef chatId, List<Leader> candidates, String query) {
        Component chooser = banChooserView.chooser(candidates, lobbyService.getLobby(chatId).getToken());
        String text = "Multiple leaders match \"" + query.trim() + "\" — pick one to ban:";
        boolean dmDelivered = chat.send(OutgoingMessage.to(event.from())
                .text(text).component(chooser).build()).isPresent();
        if (!dmDelivered) {
            // Not DM-reachable — post the choices in the group so the user isn't stuck.
            chat.send(OutgoingMessage.to(event.chat())
                    .text(text).component(chooser).build());
        }
    }

    // logic for unknown command
    public void sendUnknown(CommandContext ctx) {
        sendMessage(ctx,"Unknown command. Use " + commandFactory.infoOf(HelpCommand.class).name() + " to get list of available commands." );
    }

    //logic for command -> /register
    public void sendRegister(CommandContext ctx) {
        if (!hasLobby(ctx)) {
            sendNoLobby(ctx);
            return;
        }

        ChatRef chatId = ctx.chat();
        Long userId = ctx.user().asTelegramUserId();
        lobbyService.registerPlayer(chatId, ctx.user().userName(), userId);

        // Registration is reflected silently in the live status message — no per-join group line.
        refreshStatus(chatId);

        // Best-effort DM confirmation. If it fails the player simply isn't DM-reachable yet; that's
        // handled when the draft starts (open posts pools publicly, secret notes it in the group).
        sendDm(ctx.user(), "You're registered for the lobby! Your leader picks will arrive here when the draft starts.", List.of());
    }

    //logic for command -> /start
    public void sendStart(CommandContext ctx) {
        ChatRef chatId = ctx.chat();
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null) { sendNoLobby(ctx); return; }
        if (lobby.isDraftInProgress()) {
            sendMessage(ctx, "Draft is already in progress.");
            return;
        }
        lobby.setDraftInProgress(true);
        lobby.setDraftStartedAt(LocalDateTime.now());
        try {
            // Fix the random slot order, fold it into the status message, and announce the
            // milestone with a single mention that backlinks (replies) to the status message.
            // The map was already rolled at lobby creation; only roll now if the pool was empty
            // then and has since been filled.
            lobby.setSlotOrder(lobbyService.randomSlotOrder(chatId));
            if (lobby.getSelectedMap() == null) {
                lobbyService.rollMap(chatId);
            }
            // Slot order and map fold into the status message silently. The single group ping is
            // owned by the strategy: open posts the picks image (a reply to status), secret posts
            // a tag reply — both tag players, so there's no separate "draft started" line.
            refreshStatus(chatId);

            DraftStrategy strategy = draftStrategyFactory.getStrategy(lobby.getDraftStrategyName());
            strategy.execute(lobby, chatId, this);
        } catch (Exception e) {
            log.error("Draft failed for chat {}, resetting draftInProgress", chatId, e);
            lobby.setDraftInProgress(false);
            lobby.setDraftStartedAt(null);
            sendBugReport(ctx);
        }
    }
    public void sendPick(ChatEvent event, ChatRef lobbyChatId, String shortName) {
        Lobby lobby = lobbyService.getLobby(lobbyChatId);
        if (lobby == null) { sendMessage(event, "No active lobby."); return; }
        if (!lobby.isDraftInProgress()) { sendMessage(event, "No draft in progress."); return; }
        if (!"secret".equals(lobby.getDraftStrategyName())) {
            sendMessage(event, "/pick is only available in secret draft mode.");
            return;
        }

        String userName = event.from().userName();

        if (!lobbyService.isRegistered(lobbyChatId, userName)) {
            sendMessage(event, "You are not registered in this lobby.");
            return;
        }
        if (lobby.hasPendingPick(userName)) {
            sendMessage(event, "You already picked a leader.");
            return;
        }
        Leader leader = leaderService.getLeaderByShortName(shortName);
        if (leader == null) {
            sendMessage(event, "Unknown leader: " + shortName + ". Check your DM for available shortnames.");
            return;
        }
        Player player = lobbyService.findPlayerByName(lobbyChatId, userName);
        if (player == null) {
            sendMessage(event, "Player not found in lobby.");
            return;
        }
        if (!player.getPicks().contains(leader)) {
            sendMessage(event, "That leader is not in your pick pool.");
            return;
        }
        lobby.addPendingPick(userName, leader);
        chat.send(OutgoingMessage.to(event.from())
                .text("You picked <b>" + leader.getFullName() + "</b>!").build());

        if (lobby.allPicksIn(lobby.getPlayers().size())) {
            // Reveal: mark the draft done first so the status renders the final picks, then let the
            // strategy edit the status and post the all-picks-in milestone.
            lobby.setDraftInProgress(false);
            draftStrategyFactory.getStrategy(lobby.getDraftStrategyName())
                    .onAllPicksIn(lobby, lobbyChatId, this);
        } else {
            // Pick progress (k/N) is shown in the live status message, silently — no per-pick line.
            refreshStatus(lobbyChatId);
        }
    }

    // ---- Herson draft (ranked secret draft, submitted/resolved over DM) ----

    /** Draft kickoff: create the per-lobby state and DM every player asking for their four ranked picks. */
    public void sendHersonStart(Lobby lobby, ChatRef chatId) {
        HersonDraftState state = new HersonDraftState();
        state.init(lobby.getPlayers());
        lobby.setHersonState(state);

        List<Leader> available = leaderService.getLeaders().stream()
                .filter(l -> !lobby.getBannedLeaders().contains(l))
                .toList();
        for (Player player : lobby.getPlayers()) {
            if (!sendDm(player.getUser(), hersonPrompt(), List.of())) {
                sendToChat(lobby.getChat(),
                        "@" + player.getUserName() + " — couldn't DM you. Message the bot directly first, then send your 4 ranked picks there.");
                continue;
            }
            player.setDmPickMessage(sendDmWithRef(player.getUser(),
                    "🗳 Tap leaders to rank your top 4 picks, then hit SUBMIT:",
                    hersonPickView.grid(available, player.getPriorityPicks(), lobby.getToken())));
        }
        postMilestone(chatId, mentionAll(lobby)
                + " — Herson draft started. Check your DMs and send your 4 ranked picks.");
    }

    private String hersonPrompt() {
        return "🗳️ <b>Herson draft</b> — send your <b>4 ranked picks</b> in one message, most-wanted first, like:\n"
                + "<code>1. Gandhi 2. Lincoln 3. Saladin 4. Trajan</code>\n\n"
                + "Names are matched loosely; I'll ask you to confirm if I had to guess. "
                + "Use /leaders if you need the roster.";
    }

    /** Entry point for any free-text DM (no leading slash) — routed here by the dispatcher. */
    public void handleDirectMessage(ChatEvent.FreeText event) {
        String userName = event.from().userName();
        ChatRef chatId = lobbyService.findHersonChatForUser(userName, event.from().id());
        if (chatId == null) return; // not a participant in any live draft — stay quiet

        Lobby lobby = lobbyService.getLobby(chatId);
        Player player = lobbyService.findPlayerByName(chatId, userName);
        if (lobby == null || player == null || lobby.getHersonState() == null) return;
        String key = player.getUserName();
        HersonDraftState state = lobby.getHersonState();
        String text = event.text().trim();

        switch (state.getStage(key)) {
            case AWAITING_REPICK -> handleHersonRepick(chatId, lobby, player, text);
            case AWAITING_PICKS, AWAITING_CONFIRM -> submitHersonPicks(chatId, lobby, player, text);
            case SUBMITTED -> sendDm(player.getUser(), "You've already submitted. Sit tight — waiting on the rest.", List.of());
            case null -> { /* not a participant in this draft */ }
        }
    }

    private void submitHersonPicks(ChatRef chatId, Lobby lobby, Player player, String text) {
        Long userId = player.getUserId();
        List<String> raw = HersonPickParser.parse(text);
        if (raw.size() != 4) {
            sendDm(player.getUser(), "Please send exactly <b>4</b> ranked picks in one message, e.g.\n"
                    + "<code>1. Gandhi 2. Lincoln 3. Saladin 4. Trajan</code>", List.of());
            return;
        }

        List<Leader> resolved = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        boolean allExact = true;
        for (int i = 0; i < 4; i++) {
            String token = raw.get(i);
            switch (leaderMatcher.match(token, leaderService.getLeaders())) {
                case MatchResult.Unique u -> {
                    resolved.add(u.leader());
                    if (!isExactLeaderMatch(token, u.leader())) allExact = false;
                }
                case MatchResult.Ambiguous a -> {
                    resolved.add(null);
                    problems.add((i + 1) + ". \"" + token + "\" → " + a.leaders().stream()
                            .map(Leader::getFullName).limit(4).collect(Collectors.joining(" / ")));
                }
                case MatchResult.None n -> {
                    resolved.add(null);
                    problems.add((i + 1) + ". \"" + token + "\" → no match");
                }
            }
        }

        if (!problems.isEmpty()) {
            sendDm(player.getUser(), "I couldn't pin down some picks — please re-send all 4:\n" + String.join("\n", problems), List.of());
            return;
        }
        if (new HashSet<>(resolved).size() < 4) {
            sendDm(player.getUser(), "Your 4 picks must be different leaders. Please re-send all 4.", List.of());
            return;
        }
        List<Leader> hostBanned = resolved.stream().filter(lobby.getBannedLeaders()::contains).toList();
        if (!hostBanned.isEmpty()) {
            sendDm(player.getUser(), "These civs were banned by the host: " + hostBanned.stream()
                    .map(Leader::getFullName).collect(Collectors.joining(", "))
                    + ". Please re-send 4 picks without them.", List.of());
            return;
        }

        if (allExact) {
            commitHersonSubmission(chatId, lobby, player, resolved);
        } else {
            lobby.getHersonState().setPendingConfirm(player.getUserName(), resolved);
            lobby.getHersonState().setStage(player.getUserName(), HersonDraftState.Stage.AWAITING_CONFIRM);
            sendDm(player.getUser(), "I read your picks as:\n" + numberedPicks(resolved)
                    + "\n\nPress <b>Confirm</b> if that is what you intended — otherwise just send a new list of 4 picks.",
                    List.of(hersonPickView.confirm(lobby.getToken())));
        }
    }

    private void hersonConfirmCallback(ChatEvent event, ChatRef chatId, boolean confirm) {
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null || lobby.getHersonState() == null) { sendPrivateMessage(event, "That draft is no longer active."); return; }
        String userName = event.from().userName();
        Player player = lobbyService.findPlayerByName(chatId, userName);
        if (player == null) { sendPrivateMessage(event, "You are not in this draft."); return; }
        HersonDraftState state = lobby.getHersonState();
        String key = player.getUserName();

        if (!confirm) {
            state.clearPendingConfirm(key);
            state.setStage(key, HersonDraftState.Stage.AWAITING_PICKS);
            sendDm(player.getUser(), "Okay — send your 4 ranked picks again.", List.of());
            return;
        }
        List<Leader> pending = state.getPendingConfirm(key);
        if (pending == null) { sendDm(player.getUser(), "Nothing to confirm — send your 4 ranked picks.", List.of()); return; }
        commitHersonSubmission(chatId, lobby, player, pending);
    }

    // ---- Herson interactive grid callbacks ----

    private void handleHersonGridPick(ChatEvent event, ChatRef lobbyChatId, String shortName) {
        Lobby lobby = lobbyService.getLobby(lobbyChatId);
        if (lobby == null || lobby.getHersonState() == null) return;
        String userName = event.from().userName();
        Player player = lobbyService.findPlayerByName(lobbyChatId, userName);
        if (player == null) return;
        if (lobby.getHersonState().getStage(userName) == HersonDraftState.Stage.SUBMITTED) return;
        List<String> priority = player.getPriorityPicks();
        if (priority.contains(shortName)) {
            priority.remove(shortName);
        } else {
            priority.add(shortName);
        }
        editHersonGrid(player, lobby, lobbyChatId);
    }

    private void handleHersonGridSubmit(ChatEvent event, ChatRef lobbyChatId) {
        Lobby lobby = lobbyService.getLobby(lobbyChatId);
        if (lobby == null || lobby.getHersonState() == null) { sendPrivateMessage(event, "No active draft."); return; }
        String userName = event.from().userName();
        Player player = lobbyService.findPlayerByName(lobbyChatId, userName);
        if (player == null) { sendPrivateMessage(event, "You are not in this draft."); return; }
        if (lobby.getHersonState().getStage(userName) == HersonDraftState.Stage.SUBMITTED) {
            sendDm(player.getUser(), "You've already submitted. Sit tight — waiting on the rest.", List.of());
            return;
        }
        List<String> priority = player.getPriorityPicks();
        if (priority.size() != 4) {
            sendDm(player.getUser(), "Please rank exactly 4 leaders before submitting (you have " + priority.size() + " selected).", List.of());
            return;
        }
        List<Leader> resolved = priority.stream().map(sn -> leaderService.getLeaderByShortName(sn)).toList();
        if (resolved.stream().anyMatch(Objects::isNull)) {
            sendDm(player.getUser(), "Something went wrong with your selection. Try resetting and picking again.", List.of());
            return;
        }
        List<Leader> hostBanned = resolved.stream().filter(lobby.getBannedLeaders()::contains).toList();
        if (!hostBanned.isEmpty()) {
            sendDm(player.getUser(), "These civs are banned: " + hostBanned.stream()
                    .map(Leader::getFullName).collect(Collectors.joining(", ")) + ". Reset and re-pick.", List.of());
            return;
        }
        commitHersonSubmission(lobbyChatId, lobby, player, resolved);
    }

    private void handleHersonGridReset(ChatEvent event, ChatRef lobbyChatId) {
        Lobby lobby = lobbyService.getLobby(lobbyChatId);
        if (lobby == null || lobby.getHersonState() == null) return;
        String userName = event.from().userName();
        Player player = lobbyService.findPlayerByName(lobbyChatId, userName);
        if (player == null) return;
        if (lobby.getHersonState().getStage(userName) == HersonDraftState.Stage.SUBMITTED) return;
        player.getPriorityPicks().clear();
        editHersonGrid(player, lobby, lobbyChatId);
    }

    private void editHersonGrid(Player player, Lobby lobby, ChatRef groupChatId) {
        if (player.getDmPickMessage() == null) return;
        List<Leader> available = leaderService.getLeaders().stream()
                .filter(l -> !lobby.getBannedLeaders().contains(l))
                .toList();
        chat.editComponents(player.getDmPickMessage(),
                hersonPickView.grid(available, player.getPriorityPicks(), lobby.getToken()));
    }

    /** Strip the grid's buttons once its owner has submitted, so it can't be re-tapped. */
    private void lockHersonGrid(Player player) {
        if (player.getDmPickMessage() == null) return;
        chat.editComponents(player.getDmPickMessage(), List.of());
    }

    private void commitHersonSubmission(ChatRef chatId, Lobby lobby, Player player, List<Leader> picks) {
        HersonDraftState state = lobby.getHersonState();
        String key = player.getUserName();
        state.getRankedPicks().put(key, new ArrayList<>(picks));
        state.clearPendingConfirm(key);
        state.setStage(key, HersonDraftState.Stage.SUBMITTED);
        sendDm(player.getUser(), "✅ Picks recorded:\n" + numberedPicks(picks)
                + "\n\nHidden until the draft resolves.", List.of());
        lockHersonGrid(player);
        refreshStatus(chatId);

        if (state.allRankedSubmitted(lobby.getPlayers().size())) {
            postMilestone(chatId, "📥 All picks are in — resolving the draft…");
            advanceHersonResolution(chatId);
        }
    }

    private void handleHersonRepick(ChatRef chatId, Lobby lobby, Player player, String text) {
        HersonDraftState state = lobby.getHersonState();
        switch (leaderMatcher.match(text, leaderService.getLeaders())) {
            case MatchResult.Unique u -> {
                if (state.getAssigned().containsValue(u.leader())) {
                    sendDm(player.getUser(), u.leader().getFullName() + " is already taken — pick another civ.", List.of());
                    return;
                }
                if (lobby.getBannedLeaders().contains(u.leader())) {
                    sendDm(player.getUser(), u.leader().getFullName() + " is banned by the host — pick another civ.", List.of());
                    return;
                }
                state.getAssigned().put(player.getUserName(), u.leader());
                state.setStage(player.getUserName(), HersonDraftState.Stage.SUBMITTED);
                sendDm(player.getUser(), "✅ You re-picked <b>" + u.leader().getFullName() + "</b>.", List.of());
                refreshStatus(chatId);
                if (!state.anyAwaitingRepick()) advanceHersonResolution(chatId);
            }
            case MatchResult.Ambiguous a -> sendDm(player.getUser(),
                    "Several leaders match — be more specific: " + a.leaders().stream()
                            .map(Leader::getFullName).limit(5).collect(Collectors.joining(" / ")), List.of());
            case MatchResult.None n -> sendDm(player.getUser(),
                    "No leader matches \"" + text + "\". Reply with one civ name from the remaining pool.", List.of());
        }
    }

    /** Run the resolver as far as it can; either finish the draft or pause for coin-flip re-picks. */
    public void advanceHersonResolution(ChatRef chatId) {
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null || lobby.getHersonState() == null) return;
        HersonDraftState state = lobby.getHersonState();
        if (state.anyAwaitingInput()) return; // someone still owes picks or a re-pick

        // The pool excludes both the host's manual bans and the civs the resolver bans as contested.
        // We feed both into the resolver but keep only the contested ones in state (host bans live on
        // the host Player), so the closing summary can attribute each correctly.
        Set<Leader> hostBans = new LinkedHashSet<>(lobby.getBannedLeaders());
        Set<Leader> working = new LinkedHashSet<>(hostBans);
        working.addAll(state.getBanned());
        // "herson-low" bans every civ two or more players ranked (any priority), so survivors are
        // unique and there is never a clash to coin-flip; plain "herson" bans only same-step clashes
        // and breaks last-resort ties with a coin flip.
        boolean low = "herson-low".equals(lobby.getDraftStrategyName());
        HersonResolver.Step step = low
                ? HersonResolver.resolveLow(state.getRankedPicks(), working, state.getAssigned())
                : HersonResolver.resolve(state.getRankedPicks(), working, state.getAssigned());
        working.removeAll(hostBans);
        state.getBanned().clear();
        state.getBanned().addAll(working);

        if (step instanceof HersonResolver.Complete complete) {
            finalizeHerson(chatId, lobby, complete.assignments());
        } else if (step instanceof HersonResolver.CoinFlip coinFlip) {
            runHersonCoinFlip(chatId, lobby, coinFlip.civ(), coinFlip.contestants());
        } else if (step instanceof HersonResolver.Unresolvable unresolvable) {
            // herson-low only: a player's four picks were all banned. Too rare to auto-recover — stop
            // the draft and tell the group so the host can /terminate and re-run.
            lobby.setDraftInProgress(false);
            refreshStatus(chatId);
            String who = unresolvable.players().stream().map(n -> "@" + n).collect(Collectors.joining(", "));
            postMilestone(chatId, "⚠️ Couldn't resolve the draft — " + who
                    + " had all four picks banned by overlaps. The host can /terminate and re-run.");
            log.warn("herson-low unresolvable in chat {}: stranded {}", chatId, unresolvable.players());
        }
    }

    private void runHersonCoinFlip(ChatRef chatId, Lobby lobby, Leader civ, List<String> contestants) {
        HersonDraftState state = lobby.getHersonState();
        List<String> order = new ArrayList<>(contestants);
        Collections.shuffle(order);
        String winner = order.get(0);

        state.getAssigned().put(winner, civ);
        state.setStage(winner, HersonDraftState.Stage.SUBMITTED);
        Player winnerPlayer = lobby.getPlayerByName(winner);
        if (winnerPlayer != null) {
            sendDm(winnerPlayer.getUser(), "🪙 Coin flip — you kept <b>" + civ.getFullName() + "</b>.", List.of());
        }
        for (int i = 1; i < order.size(); i++) {
            String loser = order.get(i);
            state.setStage(loser, HersonDraftState.Stage.AWAITING_REPICK);
            Player loserPlayer = lobby.getPlayerByName(loser);
            if (loserPlayer != null) {
                sendDm(loserPlayer.getUser(), "🪙 Coin flip — <b>" + civ.getFullName()
                        + "</b> went to someone else. Reply with <b>one</b> civ name to pick again from the remaining pool.", List.of());
            }
        }
        refreshStatus(chatId); // suspended until the re-pick(s) arrive
    }

    private void finalizeHerson(ChatRef chatId, Lobby lobby, Map<String, Leader> assignments) {
        lobby.setDraftInProgress(false);
        // The status closes out (done + contested-ban summary) without listing picks — the reveal is a
        // portrait image instead, one row per player with just their assigned leader.
        refreshStatus(chatId);
        postHersonReveal(chatId, lobby, assignments);
    }

    /** Reveal the resolved draft as a combined portrait image — a row per player with their one civ. */
    private void postHersonReveal(ChatRef chatId, Lobby lobby, Map<String, Leader> assignments) {
        List<Player> order = (lobby.getSlotOrder() != null && !lobby.getSlotOrder().isEmpty())
                ? lobby.getSlotOrder() : lobby.getPlayers();
        List<Player> rows = new ArrayList<>();
        for (Player p : order) {
            Leader civ = assignments.get(p.getUserName());
            if (civ == null) continue;
            Player row = new Player(p.getUserName(), p.getUserId());
            row.setPicks(List.of(civ));
            rows.add(row);
        }
        try {
            chat.send(OutgoingMessage.to(lobby.getChat())
                    .text("🎉 Draft resolved! " + mentionAll(lobby))
                    .mentions(mentionsOf(lobby))
                    .attachment(Attachment.png("picks.png", PickImageGenerator.renderPools(rows)))
                    .replyTo(lobby.getStatusMessage())
                    .build());
        } catch (Exception e) {
            log.error("Failed to post Herson reveal image for chat {}", chatId, e);
            // Fall back to a text reveal so players still see the result.
            postMilestone(chatId, "🎉 Draft resolved!\n" + assignments.entrySet().stream()
                    .map(en -> "@" + en.getKey() + " → " + en.getValue().getFullName())
                    .collect(Collectors.joining("\n")));
        }
    }

    /** A leader's portrait as PNG bytes, or null when the file is missing/unreadable. */
    private byte[] readPortrait(Leader leader) {
        try {
            return Files.readAllBytes(Path.of(leader.getPicPath()));
        } catch (IOException e) {
            log.warn("Could not read portrait for {}: {}", leader.getFullName(), e.getMessage());
            return null;
        }
    }

    private String numberedPicks(List<Leader> picks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < picks.size(); i++) {
            sb.append(i + 1).append(". ").append(picks.get(i).getFullName());
            if (i < picks.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /** True when the typed token is the leader's exact shortname or full name (so no confirm is needed). */
    private boolean isExactLeaderMatch(String token, Leader leader) {
        String t = normalizeLoose(token);
        return t.equals(normalizeLoose(leader.getShortName())) || t.equals(normalizeLoose(leader.getFullName()));
    }

    private String normalizeLoose(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    // Builds a draft pick status message listing who has submitted and who is still pending.
    private String pickStatusMessage(Lobby lobby, String justPicked) {
        List<String> submitted = lobby.getPlayers().stream()
                .map(Player::getUserName)
                .filter(lobby::hasPendingPick)
                .toList();
        List<String> waiting = lobby.getPlayers().stream()
                .map(Player::getUserName)
                .filter(name -> !lobby.hasPendingPick(name))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("@").append(justPicked).append(" has made their pick. (")
                .append(submitted.size()).append("/").append(lobby.getPlayers().size()).append(")\n\n");
        sb.append("✅ Submitted: ")
                .append(submitted.isEmpty() ? "—"
                        : submitted.stream().map(n -> "@" + n).collect(Collectors.joining(", ")))
                .append("\n");
        sb.append("⏳ Waiting on: ")
                .append(waiting.isEmpty() ? "—"
                        : waiting.stream().map(n -> "@" + n).collect(Collectors.joining(", ")));
        return sb.toString();
    }

    //logic for command -> /time
    public void sendTime(CommandContext ctx) {
        ZonedDateTime nowInRiga = ZonedDateTime.now(ZoneId.of("Europe/Riga"));
        ZonedDateTime nowInMunich = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss, dd MMM yyyy");

        String message = String.format("\uD83C\uDF07 Current time:\n"
                        + "\uD83C\uDDF1\uD83C\uDDFB Riga: %s\n"
                        + "\uD83C\uDDE9\uD83C\uDDEA Munich: %s",
                nowInRiga.format(formatter),
                nowInMunich.format(formatter)
        );

        sendPrivateMessage(ctx, message);
    }

    //logic for command -> /mappool
    public void sendMappool(CommandContext ctx) {
        if (!hasLobby(ctx)) {
            sendPrivateMessage(ctx, "No active lobby. Use " + commandFactory.infoOf(LobbyCommand.class).name() + " to create one.");
            return;
        }

        ChatRef chatId = ctx.chat();
        List<CivMap> mapPool = lobbyService.getMappool(chatId);

        if (mapPool == null) {
            sendPrivateMessage(ctx, "Something went wrong. Please contact @VitBuk.");
            return;
        }

        if (mapPool.isEmpty()) {
            sendPrivateMessage(ctx, "Map pool is empty. Use " + commandFactory.infoOf(MapAddCommand.class).name() + " to add maps.");
            return;
        }

        chat.send(OutgoingMessage.to(ctx.user())
                .text("Map pool — click to remove:")
                .component(mapPoolView.removable(mapPool, lobbyService.getLobby(chatId).getToken()))
                .build());
    }

    //logic for the command -> maplist
    public void sendMaplist(CommandContext ctx) {
        List<CivMap> allMaps = Arrays.stream(CivMap.values()).toList();
        Lobby lobby = lobbyService.getLobby(ctx.chat());

        StringBuilder sb = new StringBuilder("Maps: \n");
        sb.append("<i>To add map to the pool use ")
                .append(commandFactory.infoOf(MapAddCommand.class).name())
                .append( " command </i> \n");

        chat.send(OutgoingMessage.to(ctx.user())
                .text(sb.toString())
                // Without a lobby there is nothing to add a map to, so no dead buttons.
                .component(lobby == null ? null : mapPoolView.addable(allMaps, lobby.getToken()))
                .build());
    }

    //logic for command -> /mapAdd [name]
    public void sendMapAdd(ChatEvent event, CivMap civMap) {
        if (civMap == null) {
            sendPrivateMessage(event, "There is no such map. To get list of available maps use "
                    + commandFactory.infoOf(MapAddCommand.class).name()
                    + " command.");
            return;
        }

        ChatRef chatId = event.chat();
        if (!lobbyService.addMap(chatId, civMap)) {
            sendPrivateMessage(event, civMap + " is already in the map pool.");
            return;
        }
        lobbyService.ensureSelectedMap(chatId);
        refreshStatus(chatId);
    }

    // Called from DM callback — lobbyChatId is the group chat where the lobby lives
    public void sendMapAdd(ChatEvent event, CivMap civMap, ChatRef lobbyChatId) {
        if (civMap == null) {
            sendMessage(event, "There is no such map. To get list of available maps use "
                    + commandFactory.infoOf(MapAddCommand.class).name()
                    + " command.");
            return;
        }
        if (!lobbyService.addMap(lobbyChatId, civMap)) {
            sendMessage(event, civMap + " is already in the map pool.");
            return;
        }
        lobbyService.ensureSelectedMap(lobbyChatId);
        refreshStatus(lobbyChatId);
        sendMessage(event, "✅ Added " + civMap + " to the map pool.");
    }

    //logic for command /mapRemove [name]
    public void sendMapRemove(ChatEvent event, CivMap civMap) {
        if (civMap == null) {
            sendPrivateMessage(event, "There is no such map. Check map pool with "
                    + commandFactory.infoOf(MappoolCommand.class).name() + ".");
            return;
        }

        ChatRef chatId = event.chat();
        if (lobbyService.removeMap(chatId, civMap)) {
            lobbyService.ensureSelectedMap(chatId);
            refreshStatus(chatId);
            return;
        }

        sendPrivateMessage(event, civMap + " is not in the map pool. Check it with "
                + commandFactory.infoOf(MappoolCommand.class).name() + ".");
    }

    // Called from DM callback — lobbyChatId is the group chat where the lobby lives
    public void sendMapRemove(ChatEvent event, CivMap civMap, ChatRef lobbyChatId) {
        if (!lobbyService.removeMap(lobbyChatId, civMap)) {
            sendPrivateMessage(event, civMap + " is not in the map pool.");
            return;
        }
        lobbyService.ensureSelectedMap(lobbyChatId);
        refreshStatus(lobbyChatId);
        sendPrivateMessage(event, "✅ Removed " + civMap + " from the map pool.");
    }

    public void sendTerminate(CommandContext ctx) {
        if (!hasLobby(ctx)) { sendNoLobby(ctx); return; }
        ChatRef chatId = ctx.chat();
        lobbyService.removeLobby(chatId);
        sendMessage(ctx, "Lobby terminated by @" + ctx.user().userName() + ".");
    }
    public boolean isHost(CommandContext ctx){
        ChatRef chatId = ctx.chat();
        return lobbyService.isHost(chatId, ctx.user().userName());
    }

    public boolean hasLobby(CommandContext ctx) {
        ChatRef chatId = ctx.chat();
        return lobbyService.hasLobby(chatId);
    }

    public void sendNoLobby(CommandContext ctx) {
        sendMessage(ctx, "Lobby is not up. Use command " + commandFactory.infoOf(LobbyCommand.class).name() + " to create new lobby");
    }

    public void sendNotAHost(CommandContext ctx) {
        sendMessage(ctx, "You can`t use host commands");
    }

    public boolean isRegistered(CommandContext ctx) {
        ChatRef chatId = ctx.chat();
        return lobbyService.isRegistered(chatId, ctx.user().userName());
    }

    public void sendNotAPlayer(CommandContext ctx) {
        sendMessage(ctx, "Unregistered players cant use that command. To register use: "  +
                commandFactory.infoOf(RegisterCommand.class).name() + " command");
    }

    public void sendNoSuchMap(CommandContext ctx) {
        sendPrivateMessage(ctx, "No such map. Check " + commandFactory.infoOf(MaplistCommand.class).name()
                + " command for list of available maps");
    }

    // ---- Single live status message: created once, then edited; milestones get a backlinking reply ----

    /** Post the lobby's status message for the first time and remember its id for future edits. */
    public void postStatus(ChatRef chatId) {
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null) return;
        lobby.setStatusMessage(sendStatusMessage(lobby.getChat(), renderStatus(lobby)));
    }

    /** Re-render the status into the existing live message (editing it in place), or post it if absent. */
    public void refreshStatus(ChatRef chatId) {
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null) return;
        if (lobby.getStatusMessage() == null) {
            postStatus(chatId);
            return;
        }
        chat.editText(lobby.getStatusMessage(), renderStatus(lobby), mentionsOf(lobby));
    }

    /** A space-joined {@code @username} mention of every player with a username — used to ping the group on draft start. */
    public String mentionAll(Lobby lobby) {
        return lobby.getPlayers().stream()
                .map(Player::getUserName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> "@" + name)
                .collect(Collectors.joining(" "));
    }

    /** A milestone notification (e.g. draft started / all picks in) that replies to — backlinks — the status message. */
    public void postMilestone(ChatRef chatId, String text) {
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null) return;
        sendReply(lobby.getChat(), lobby.getStatusMessage(), text);
    }

    // The status message is posted silently (no ping) — it is ambient state; the milestone replies do the pinging.
    private MessageRef sendStatusMessage(ChatRef chatRef, String text) {
        return chat.send(OutgoingMessage.to(chatRef)
                .text(text)
                .mentions(mentionsOf(chatRef))
                .silent(true)
                .build()).orElse(null);
    }

    private void sendReply(ChatRef chatRef, MessageRef replyTo, String text) {
        chat.send(OutgoingMessage.to(chatRef)
                .text(text)
                .mentions(mentionsOf(chatRef))
                .replyTo(replyTo)
                .build());
    }

    // logic for command -> /lobbyInfo. The live status message already carries everything, so this
    // just drops an anchor (a reply that backlinks to it) so players can jump to it.
    public void sendLobbyInfo(CommandContext ctx) {
        ChatRef chatId = ctx.chat();
        if (!lobbyService.hasLobby(chatId)) {
            sendMessage(ctx, "No active lobby. Use /lobby to create one.");
            return;
        }
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby.getStatusMessage() == null) {
            // No status message yet (shouldn't normally happen) — post a fresh one.
            postStatus(chatId);
            return;
        }
        sendReply(lobby.getChat(), lobby.getStatusMessage(), "📌 Lobby status ☝️");
    }

    /** Builds the full lobby status text — delegated to the pure {@link StatusView}. */
    public String renderStatus(Lobby lobby) {
        return StatusView.render(lobby);
    }

    public void sendSetBanSize(CommandContext ctx, int n) {
        if (n < 0) {
            sendPrivateMessage(ctx, "Ban size must be 0 or greater.");
            return;
        }
        ChatRef chatId = ctx.chat();
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null) { sendNoLobby(ctx); return; }
        lobby.setBanSize(n);
        refreshStatus(chatId);
    }

    public void sendSetPickSize(CommandContext ctx, int n) {
        if (n < 1) {
            sendPrivateMessage(ctx, "Pick size must be at least 1.");
            return;
        }
        ChatRef chatId = ctx.chat();
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null) { sendNoLobby(ctx); return; }
        lobby.setPickSize(n);
        refreshStatus(chatId);
    }

    public void sendSetDraft(CommandContext ctx, String strategyName) {
        if (!draftStrategyFactory.getStrategyNames().contains(strategyName)) {
            sendPrivateMessage(ctx, "Unknown strategy. Available: " + String.join(", ", draftStrategyFactory.getStrategyNames()));
            return;
        }
        ChatRef chatId = ctx.chat();
        Lobby lobby = lobbyService.getLobby(chatId);
        if (lobby == null) { sendNoLobby(ctx); return; }
        lobby.setDraftStrategyName(strategyName);
        refreshStatus(chatId);
    }

    public void sendAdminLobbies(CommandContext ctx) {
        Map<ChatRef, Lobby> all = lobbyService.getAllLobbies();
        if (all.isEmpty()) {
            sendPrivateMessage(ctx, "No active lobbies.");
            return;
        }
        StringBuilder sb = new StringBuilder("Active lobbies:\n");
        all.forEach((chatId, lobby) -> {
            long ageMinutes = Duration.between(lobby.getCreated(), LocalDateTime.now()).toMinutes();
            sb.append("chat: ").append(chatId).append(" | token: ").append(lobby.getToken())
              .append(" | host: @").append(lobby.getHost().getUserName())
              .append(" | players: ").append(lobby.getPlayers().size())
              .append(" | age: ").append(ageMinutes).append("m\n");
        });
        sendPrivateMessage(ctx, sb.toString());
    }

    /**
     * Terminate a lobby by its short token or its raw channel id. Ids are only unique within a
     * platform, so a bare id is resolved on the admin's own platform.
     */
    public void sendAdminTerminate(CommandContext ctx, String target) {
        ChatRef targetChatId = lobbyService.resolveToken(target);
        if (targetChatId == null) {
            targetChatId = new ChatRef(ctx.chat().platform(), target, null);
        }
        if (!lobbyService.hasLobby(targetChatId)) {
            sendPrivateMessage(ctx, "No lobby found for: " + target);
            return;
        }
        ChatRef lobbyChat = lobbyService.getLobby(targetChatId).getChat();
        lobbyService.removeLobby(targetChatId);
        chat.send(OutgoingMessage.to(lobbyChat).text("Lobby terminated by bot admin.").build());
        sendPrivateMessage(ctx, "Lobby in chat " + targetChatId + " terminated.");
    }

    /** Post into a lobby's own channel (honouring the forum topic it lives in). */
    public void sendToChat(ChatRef target, String text) {
        chat.send(OutgoingMessage.to(target).text(text).mentions(mentionsOf(target)).build());
    }

    // Posts to a lobby's channel, honouring the forum topic it lives in (Telegram message_thread_id);
    // pass null for the General topic / direct messages, which have no topics.


    /** Reply in the channel the event came from. */
    public void sendMessage(ChatEvent event, String text) {
        chat.send(OutgoingMessage.to(event.chat()).text(text).mentions(mentionsOf(event.chat())).build());
    }

    public void sendMessage(CommandContext ctx, String text) {
        sendMessage(ctx.event(), text);
    }

    private void sendBugReport(CommandContext ctx) {
        sendMessage(ctx, "Something wrong happened. Please contact @VitBuk to report bug.");
    }

    /** DM the user behind an event — command feedback that has no business in the group. */
    private void sendPrivateMessage(ChatEvent event, String text) {
        chat.send(OutgoingMessage.to(event.from()).text(text).build());
    }

    private void sendPrivateMessage(CommandContext ctx, String text) {
        sendPrivateMessage(ctx.event(), text);
    }

    /** DM a user, optionally with components; false when they are not reachable. */
    private boolean sendDm(UserRef user, String text, List<Component> components) {
        if (user == null || !user.isAddressable()) return false;
        return chat.send(OutgoingMessage.to(user).text(text).components(components).build()).isPresent();
    }

    /** As {@link #sendDm}, but keeps the handle so the message can be edited later. */
    private MessageRef sendDmWithRef(UserRef user, String text, List<Component> components) {
        if (user == null || !user.isAddressable()) return null;
        return chat.send(OutgoingMessage.to(user).text(text).components(components).build()).orElse(null);
    }

    /**
     * The lobby members a message's {@code @username} placeholders may refer to, so an adapter can
     * turn them into real pings. Empty when the chat has no lobby — unresolved names stay literal.
     */
    private MentionTable mentionsOf(ChatRef chatRef) {
        Lobby lobby = chatRef == null ? null : lobbyService.getLobby(chatRef);
        return lobby == null ? MentionTable.EMPTY : mentionsOf(lobby);
    }

    public MentionTable mentionsOf(Lobby lobby) {
        Map<String, UserRef> byName = new LinkedHashMap<>();
        for (Player player : lobby.getPlayers()) {
            byName.put(player.getUserName(), player.getUser());
        }
        return MentionTable.of(byName);
    }

    private List<String> readLines (String filePath) {
        try {
            return Files.readAllLines(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String allLines (String path) {
        List<String> lines = readLines(path);
        StringBuilder sb = new StringBuilder();
        for (String s : lines) {
            sb.append(s);
            sb.append("\n");
        }

        return sb.toString();
    }
}