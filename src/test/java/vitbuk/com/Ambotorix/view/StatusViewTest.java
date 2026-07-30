package vitbuk.com.Ambotorix.view;

import org.junit.jupiter.api.Test;
import vitbuk.com.Ambotorix.chat.UserRef;
import vitbuk.com.Ambotorix.entities.CivMap;
import vitbuk.com.Ambotorix.entities.HersonDraftState;
import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.entities.Player;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct coverage of the status renderer, previously only reachable through the scenario suite. */
class StatusViewTest {

    /** Status text with markup stripped — assertions read the way a player sees the message. */
    private static String plain(Lobby lobby) {
        return StatusView.render(lobby).replaceAll("<[^>]+>", "");
    }

    private static Leader leader(String full, String shortName) {
        return new Leader(full, shortName, "", "");
    }

    private static Lobby lobbyWith(String hostName, String... others) {
        Lobby lobby = new Lobby(new Player(UserRef.telegram(1L, hostName)));
        long id = 2;
        for (String name : others) {
            lobby.addPlayer(new Player(UserRef.telegram(id++, name)));
        }
        lobby.setMapPool(new java.util.ArrayList<>(List.of(CivMap.PANGEA)));
        lobby.setSelectedMap(CivMap.PANGEA);
        return lobby;
    }

    @Test
    void freshLobbyShowsWaitingSettingsAndMapPool() {
        String out = plain(lobbyWith("alice", "bob"));

        assertTrue(out.contains("Lobby by @alice"), out);
        assertTrue(out.contains("Status: waiting"), out);
        assertTrue(out.contains("Draft: open"), out);
        assertTrue(out.contains("Pick size: 6"), out);
        assertTrue(out.contains("Bans per player: 1"), out);
        assertTrue(out.contains("Players (2):"), out);
        assertTrue(out.contains("Map pool: "), out);
    }

    @Test
    void mapPoolDisappearsOnceDraftStarts() {
        Lobby lobby = lobbyWith("alice");
        lobby.setDraftStartedAt(LocalDateTime.now());
        lobby.setDraftInProgress(true);

        String out = plain(lobby);

        assertTrue(out.contains("Status: drafting"), out);
        assertTrue(out.contains("Map: Pangea"), out);
        assertFalse(out.contains("Map pool:"), out);
    }

    @Test
    void hersonHidesPickAndBanSizeAndShowsHostBans() {
        Lobby lobby = lobbyWith("alice", "bob");
        lobby.setDraftStrategyName("herson");
        lobby.getHost().ban(leader("India Gandhi", "gandhi"));

        String out = plain(lobby);

        assertFalse(out.contains("Pick size"), out);
        assertFalse(out.contains("Bans per player"), out);
        assertTrue(out.contains("Host bans: India Gandhi"), out);
    }

    @Test
    void hersonInProgressCountsSubmissions() {
        Lobby lobby = lobbyWith("alice", "bob");
        lobby.setDraftStrategyName("herson");
        lobby.setDraftStartedAt(LocalDateTime.now());
        lobby.setDraftInProgress(true);
        HersonDraftState state = new HersonDraftState();
        state.init(lobby.getPlayers());
        state.getRankedPicks().put("alice", List.of(leader("India Gandhi", "gandhi")));
        lobby.setHersonState(state);

        assertTrue(plain(lobby).contains("Submissions: 1/2 in"), plain(lobby));
    }

    @Test
    void closedHersonExplainsContestedBansWithPriorities() {
        Lobby lobby = lobbyWith("alice", "bob");
        lobby.setDraftStrategyName("herson");
        lobby.setDraftStartedAt(LocalDateTime.now());
        lobby.setDraftInProgress(false);
        Leader gandhi = leader("India Gandhi", "gandhi");
        HersonDraftState state = new HersonDraftState();
        state.init(lobby.getPlayers());
        state.getRankedPicks().put("alice", List.of(gandhi));
        state.getRankedPicks().put("bob", List.of(leader("Rome Trajan", "trajan"), gandhi));
        state.getBanned().add(gandhi);
        lobby.setHersonState(state);

        String out = plain(lobby);

        assertTrue(out.contains("Status: done"), out);
        assertTrue(out.contains("Contested (banned):"), out);
        assertTrue(out.contains("India Gandhi — @alice (#1), @bob (#2)"), out);
    }

    @Test
    void secretDraftShowsPickProgressThenRevealsPicks() {
        Lobby lobby = lobbyWith("alice", "bob");
        lobby.setDraftStrategyName("secret");
        lobby.setDraftStartedAt(LocalDateTime.now());
        lobby.setDraftInProgress(true);
        lobby.addPendingPick("alice", leader("India Gandhi", "gandhi"));

        assertTrue(plain(lobby).contains("Picks: 1/2 in"), plain(lobby));

        lobby.setDraftInProgress(false);
        String revealed = plain(lobby);
        assertTrue(revealed.contains("@alice → India Gandhi"), revealed);
    }
}
