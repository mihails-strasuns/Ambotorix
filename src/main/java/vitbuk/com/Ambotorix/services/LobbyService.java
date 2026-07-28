package vitbuk.com.Ambotorix.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.entities.CivMap;
import vitbuk.com.Ambotorix.entities.HersonDraftState;
import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.entities.Player;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class LobbyService {

    private static final Logger log = LoggerFactory.getLogger(LobbyService.class);

    /**
     * Lobbies keyed by {@link ChatRef#channelKey()} — platform included, so a Telegram group and a
     * Discord channel can never collide even if their numeric ids happen to coincide.
     */
    private final Map<ChatRef, Lobby> lobbies = new ConcurrentHashMap<>();
    /** Short handles used in button payloads, resolved back to the lobby's channel. */
    private final Map<String, ChatRef> byToken = new ConcurrentHashMap<>();

    public String createLobby(ChatRef chat, Player host) {
        ChatRef key = chat.channelKey();
        Lobby lobby = new Lobby(host);
        lobby.setChat(chat);
        Lobby existing = lobbies.putIfAbsent(key, lobby);
        if (existing != null) {
            return "Lobby already exists. " + existing.getHost().getUserName()
                    + " can terminate it using /terminate";
        }
        lobby.setToken(mintToken(key));
        return "Lobby created by " + host.getUserName();
    }

    /**
     * A short, opaque handle for a lobby, carried in button payloads instead of the raw channel id.
     *
     * <p>Both platforms cap interactive payloads — Telegram at 64 bytes of {@code callback_data},
     * Discord at 100 characters of {@code custom_id} — and a 19-digit Discord snowflake plus a verb
     * and a leader shortname eats a lot of that. A 6-character token keeps every payload small and,
     * unlike a bare channel id, resolves back to the lobby's full address including its thread.
     */
    private String mintToken(ChatRef key) {
        String token;
        do {
            token = Long.toString(Math.abs(new Random().nextLong()), 36);
            token = token.substring(0, Math.min(6, token.length()));
        } while (byToken.putIfAbsent(token, key) != null);
        return token;
    }

    /** The lobby a button payload refers to, or null if that lobby is gone (a stale button). */
    public ChatRef resolveToken(String token) {
        return token == null ? null : byToken.get(token);
    }

    public boolean hasLobby(ChatRef chat) {
        return lobbies.containsKey(chat.channelKey());
    }

    public Lobby getLobby(ChatRef chat) {
        return lobbies.get(chat.channelKey());
    }

    public void removeLobby(ChatRef chat) {
        lobbies.remove(chat.channelKey());
    }

    public Map<ChatRef, Lobby> getAllLobbies() {
        return Collections.unmodifiableMap(lobbies);
    }

    public List<ChatRef> getExpiredLobbyChats(int minutesAfterStart) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutesAfterStart);
        return lobbies.entrySet().stream()
                .filter(e -> {
                    LocalDateTime started = e.getValue().getDraftStartedAt();
                    return started != null && started.isBefore(cutoff);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public String registerPlayer(ChatRef chat, String userName, Long userId) {
        Lobby lobby = lobbies.get(chat.channelKey());
        if (lobby == null) return "No active lobby in this chat.";
        if (lobby.getPlayersNames().contains(userName)) {
            return "Player " + userName + " is already registered";
        }
        lobby.addPlayer(new Player(userName, userId));
        return "Player " + userName + " added to lobby";
    }

    public boolean isRegistered(ChatRef chat, String userName) {
        Lobby lobby = lobbies.get(chat.channelKey());
        return lobby != null && lobby.getPlayersNames().contains(userName);
    }

    public boolean isHost(ChatRef chat, String userName) {
        Lobby lobby = lobbies.get(chat.channelKey());
        if (lobby == null) return false;
        String hostName = lobby.getHost().getUserName();
        return hostName != null && hostName.equalsIgnoreCase(userName);
    }

    public boolean isBanned(ChatRef chat, Leader leader) {
        Lobby lobby = lobbies.get(chat.channelKey());
        return lobby != null && lobby.getBannedLeaders().contains(leader);
    }

    public boolean hasAvailableBans(ChatRef chat, Player player) {
        Lobby lobby = lobbies.get(chat.channelKey());
        return lobby != null && lobby.getBanSize() > player.getBans().size();
    }

    public List<Leader> bannedLeaders(ChatRef chat) {
        Lobby lobby = lobbies.get(chat.channelKey());
        return lobby == null ? Collections.emptyList() : lobby.getBannedLeaders();
    }

    public Player findPlayerByName(ChatRef chat, String userName) {
        Lobby lobby = lobbies.get(chat.channelKey());
        if (lobby == null || lobby.getPlayers() == null) return null;
        return lobby.getPlayers().stream()
                .filter(p -> p.getUserName().equalsIgnoreCase(userName))
                .findFirst().orElse(null);
    }

    /**
     * The group chat id of the in-progress Herson draft this user is a participant in (so their DM can
     * be routed — including a "you've already submitted" nudge once they have), or null. If somehow
     * several qualify, the most recently started wins.
     */
    public ChatRef findHersonChatForUser(String userName, String userId) {
        ChatRef best = null;
        LocalDateTime bestStarted = null;
        for (Map.Entry<ChatRef, Lobby> e : lobbies.entrySet()) {
            Lobby lobby = e.getValue();
            if (!lobby.isHersonDraft()) continue;
            if (!lobby.isDraftInProgress() || lobby.getHersonState() == null) continue;
            Player player = lobby.getPlayers().stream()
                    .filter(p -> (userName != null && userName.equalsIgnoreCase(p.getUserName()))
                            || (userId != null && userId.equals(p.getUser().id())))
                    .findFirst().orElse(null);
            if (player == null) continue;
            // Stage is null only for someone who joined after the prompts went out — nothing to route.
            if (lobby.getHersonState().getStage(player.getUserName()) == null) continue;
            LocalDateTime started = lobby.getDraftStartedAt();
            if (best == null || (started != null && (bestStarted == null || started.isAfter(bestStarted)))) {
                best = e.getKey();
                bestStarted = started;
            }
        }
        return best;
    }

    public List<CivMap> getMappool(ChatRef chat) {
        Lobby lobby = lobbies.get(chat.channelKey());
        return lobby == null ? null : lobby.getMapPool();
    }

    public boolean addMap(ChatRef chat, CivMap civMap) {
        Lobby lobby = lobbies.get(chat.channelKey());
        return lobby != null && lobby.addMap(civMap);
    }

    public void clearAllBans(ChatRef chat) {
        Lobby lobby = lobbies.get(chat.channelKey());
        if (lobby != null) lobby.clearAllBans();
    }

    public boolean removeMap(ChatRef chat, CivMap civMap) {
        Lobby lobby = lobbies.get(chat.channelKey());
        return lobby != null && lobby.removeMap(civMap);
    }

    public List<Player> randomSlotOrder(ChatRef chat) {
        Lobby lobby = lobbies.get(chat.channelKey());
        if (lobby == null) return Collections.emptyList();
        List<Player> shuffled = new ArrayList<>(lobby.getPlayers());
        Collections.shuffle(shuffled);
        return shuffled;
    }

    public CivMap randomMap(ChatRef chat) {
        Lobby lobby = lobbies.get(chat.channelKey());
        if (lobby == null) return null;
        List<CivMap> mapPool = lobby.getMapPool();
        if (mapPool.isEmpty()) return null;
        Collections.shuffle(mapPool);
        return mapPool.get(0);
    }

    /**
     * Fix a random map from the current pool as the lobby's selected map (null if the pool is empty).
     * Rolled up front — before bans/picks — so players know the map while they draft.
     */
    public CivMap rollMap(ChatRef chat) {
        Lobby lobby = lobbies.get(chat.channelKey());
        if (lobby == null) return null;
        List<CivMap> pool = lobby.getMapPool();
        // Pick without shuffling the pool in place, so its displayed order stays stable.
        CivMap map = pool.isEmpty() ? null : pool.get(new Random().nextInt(pool.size()));
        lobby.setSelectedMap(map);
        return map;
    }

    /**
     * Keep the selected map consistent with the pool after an edit, before the draft starts:
     * roll a fresh one only if none is set yet or the current pick was removed from the pool.
     * Leaves an already-valid selection untouched so adding/removing other maps doesn't reshuffle it.
     */
    public void ensureSelectedMap(ChatRef chat) {
        Lobby lobby = lobbies.get(chat.channelKey());
        if (lobby == null || lobby.isDraftStarted()) return;
        CivMap current = lobby.getSelectedMap();
        if (current == null || !lobby.getMapPool().contains(current)) {
            rollMap(chat);
        }
    }
}
