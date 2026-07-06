package vitbuk.com.Ambotorix.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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
    private final Map<Long, Lobby> lobbies = new ConcurrentHashMap<>();

    public String createLobby(Long chatId, Player host) {
        return createLobby(chatId, null, host);
    }

    /** Creates a lobby anchored to a Telegram forum topic ({@code threadId}; null = General topic). */
    public String createLobby(Long chatId, Integer threadId, Player host) {
        Lobby lobby = new Lobby(host);
        lobby.setMessageThreadId(threadId);
        Lobby existing = lobbies.putIfAbsent(chatId, lobby);
        if (existing != null) {
            return "Lobby already exists. " + existing.getHost().getUserName()
                    + " can terminate it using /terminate";
        }
        return "Lobby created by " + host.getUserName();
    }

    public boolean hasLobby(Long chatId) {
        return lobbies.containsKey(chatId);
    }

    public Lobby getLobby(Long chatId) {
        return lobbies.get(chatId);
    }

    public void removeLobby(Long chatId) {
        lobbies.remove(chatId);
    }

    public Map<Long, Lobby> getAllLobbies() {
        return Collections.unmodifiableMap(lobbies);
    }

    public List<Long> getExpiredLobbyChatIds(int minutesAfterStart) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutesAfterStart);
        return lobbies.entrySet().stream()
                .filter(e -> {
                    LocalDateTime started = e.getValue().getDraftStartedAt();
                    return started != null && started.isBefore(cutoff);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public String registerPlayer(Long chatId, String userName, Long userId) {
        Lobby lobby = lobbies.get(chatId);
        if (lobby == null) return "No active lobby in this chat.";
        if (lobby.getPlayersNames().contains(userName)) {
            return "Player " + userName + " is already registered";
        }
        lobby.addPlayer(new Player(userName, userId));
        return "Player " + userName + " added to lobby";
    }

    public boolean isRegistered(Long chatId, String userName) {
        Lobby lobby = lobbies.get(chatId);
        return lobby != null && lobby.getPlayersNames().contains(userName);
    }

    public boolean isHost(Long chatId, String userName) {
        Lobby lobby = lobbies.get(chatId);
        if (lobby == null) return false;
        String hostName = lobby.getHost().getUserName();
        return hostName != null && hostName.equalsIgnoreCase(userName);
    }

    public boolean isBanned(Long chatId, Leader leader) {
        Lobby lobby = lobbies.get(chatId);
        return lobby != null && lobby.getBannedLeaders().contains(leader);
    }

    public boolean hasAvailableBans(Long chatId, Player player) {
        Lobby lobby = lobbies.get(chatId);
        return lobby != null && lobby.getBanSize() > player.getBans().size();
    }

    public List<Leader> bannedLeaders(Long chatId) {
        Lobby lobby = lobbies.get(chatId);
        return lobby == null ? Collections.emptyList() : lobby.getBannedLeaders();
    }

    public Player findPlayerByName(Long chatId, String userName) {
        Lobby lobby = lobbies.get(chatId);
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
    public Long findHersonChatIdForUser(String userName, Long userId) {
        Long best = null;
        LocalDateTime bestStarted = null;
        for (Map.Entry<Long, Lobby> e : lobbies.entrySet()) {
            Lobby lobby = e.getValue();
            if (!lobby.isHersonDraft()) continue;
            if (!lobby.isDraftInProgress() || lobby.getHersonState() == null) continue;
            Player player = lobby.getPlayers().stream()
                    .filter(p -> (userName != null && userName.equalsIgnoreCase(p.getUserName()))
                            || (userId != null && userId.equals(p.getUserId())))
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

    public List<CivMap> getMappool(Long chatId) {
        Lobby lobby = lobbies.get(chatId);
        return lobby == null ? null : lobby.getMapPool();
    }

    public boolean addMap(Long chatId, CivMap civMap) {
        Lobby lobby = lobbies.get(chatId);
        return lobby != null && lobby.addMap(civMap);
    }

    public void clearAllBans(Long chatId) {
        Lobby lobby = lobbies.get(chatId);
        if (lobby != null) lobby.clearAllBans();
    }

    public boolean removeMap(Long chatId, CivMap civMap) {
        Lobby lobby = lobbies.get(chatId);
        return lobby != null && lobby.removeMap(civMap);
    }

    public List<Player> randomSlotOrder(Long chatId) {
        Lobby lobby = lobbies.get(chatId);
        if (lobby == null) return Collections.emptyList();
        List<Player> shuffled = new ArrayList<>(lobby.getPlayers());
        Collections.shuffle(shuffled);
        return shuffled;
    }

    public CivMap randomMap(Long chatId) {
        Lobby lobby = lobbies.get(chatId);
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
    public CivMap rollMap(Long chatId) {
        Lobby lobby = lobbies.get(chatId);
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
    public void ensureSelectedMap(Long chatId) {
        Lobby lobby = lobbies.get(chatId);
        if (lobby == null || lobby.isDraftStarted()) return;
        CivMap current = lobby.getSelectedMap();
        if (current == null || !lobby.getMapPool().contains(current)) {
            rollMap(chatId);
        }
    }
}
