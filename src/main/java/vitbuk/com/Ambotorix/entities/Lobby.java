package vitbuk.com.Ambotorix.entities;

import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.MessageRef;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Lobby {

    private final LocalDateTime created;
    private final Player host;
    private List<CivMap> mapPool;
    private Integer banSize;
    private Integer pickSize;
    private List<Player> players;
    private CivMap selectedMap;
    private String draftStrategyName = "open";
    private Map<String, Leader> pendingPicks = new LinkedHashMap<>();
    private boolean draftInProgress = false;
    private LocalDateTime draftStartedAt;
    // The channel (and thread/topic within it) the lobby lives in, on whichever platform.
    private ChatRef chat;
    // Short opaque handle used in button payloads instead of the channel id — see LobbyService.
    private String token;
    // The single live status message the bot keeps edited with all lobby metadata; null until posted.
    private MessageRef statusMessage;
    // The randomised slot order fixed at draft start, so the status message renders it consistently.
    private List<Player> slotOrder;
    // Herson-draft bookkeeping (ranked picks + resolution state); null until a Herson draft starts.
    private HersonDraftState hersonState;

    public Lobby(Player host) {
        this.created = LocalDateTime.now();
        this.host = host;
        players = new ArrayList<>();
        players.add(host);
        defaultSetup();
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public Player getHost() {
        return host;
    }

    public List<CivMap> getMapPool() {
        return mapPool;
    }

    public void setMapPool(List<CivMap> mapPool) {
        this.mapPool = mapPool;
    }

    public Integer getBanSize() {
        return banSize;
    }

    public void setBanSize(Integer banSize) {
        this.banSize = banSize;
    }

    public Integer getPickSize() {
        return pickSize;
    }

    public void setPickSize(Integer pickSize) {
        this.pickSize = pickSize;
    }

    public List<Player> getPlayers() {
        return players;
    }
    public List<String> getPlayersNames(){
        return players.stream()
                .map(Player::getUserName)
                .collect(Collectors.toList());
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public CivMap getSelectedMap() {
        return selectedMap;
    }

    public void setSelectedMap(CivMap selectedMap) {
        this.selectedMap = selectedMap;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public Player getPlayerByName(String userName) {
        return players.stream()
                .filter(p -> p.getUserName().equals(userName))
                .findFirst()
                .orElse(null);
    }

    public List<Leader> getBannedLeaders() {
        Set<Leader> bannedSet = new HashSet<>();
        for (Player player : players) {
            bannedSet.addAll(player.getBans());
        }

        if (bannedSet.size() == 0) {
            return new ArrayList<Leader>();
        }

        return bannedSet.stream().toList();
    }

    public boolean isBanned(Leader leader) {
        return getBannedLeaders().contains(leader);
    }

    public boolean addMap(CivMap civMap) {
        if (mapPool.contains(civMap)) return false;
        mapPool.add(civMap);
        return true;
    }

    public void clearAllBans() {
        players.forEach(p -> p.getBans().clear());
    }

    public boolean removeMap (CivMap civMap) {
        if (mapPool.contains(civMap)) {
            mapPool.remove(civMap);
            return true;
        }

        return false;
    }
    public String getDraftStrategyName() { return draftStrategyName; }
    public void setDraftStrategyName(String name) { this.draftStrategyName = name; }

    /** All ranked-secret "herson" variants share the same DM submission/resolution machinery. */
    private static final Set<String> HERSON_STRATEGIES = Set.of("herson", "herson-low");
    public boolean isHersonDraft() { return HERSON_STRATEGIES.contains(draftStrategyName); }

    public Map<String, Leader> getPendingPicks() { return pendingPicks; }
    public void addPendingPick(String userName, Leader leader) { pendingPicks.put(userName, leader); }
    public boolean hasPendingPick(String userName) { return pendingPicks.containsKey(userName); }
    public boolean allPicksIn(int expectedCount) { return pendingPicks.size() >= expectedCount; }

    public boolean isDraftInProgress() { return draftInProgress; }
    public void setDraftInProgress(boolean draftInProgress) { this.draftInProgress = draftInProgress; }

    public LocalDateTime getDraftStartedAt() { return draftStartedAt; }
    public void setDraftStartedAt(LocalDateTime draftStartedAt) { this.draftStartedAt = draftStartedAt; }
    public boolean isDraftStarted() { return draftStartedAt != null; }

    public ChatRef getChat() { return chat; }
    public void setChat(ChatRef chat) { this.chat = chat; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public MessageRef getStatusMessage() { return statusMessage; }
    public void setStatusMessage(MessageRef statusMessage) { this.statusMessage = statusMessage; }

    public List<Player> getSlotOrder() { return slotOrder; }
    public void setSlotOrder(List<Player> slotOrder) { this.slotOrder = slotOrder; }

    public HersonDraftState getHersonState() { return hersonState; }
    public void setHersonState(HersonDraftState hersonState) { this.hersonState = hersonState; }

    public void defaultSetup() {
        this.banSize = 1;
        this.pickSize = 6;
        this.mapPool = new ArrayList<>(CivMap.STANDARD_MAPS);
    }
}
