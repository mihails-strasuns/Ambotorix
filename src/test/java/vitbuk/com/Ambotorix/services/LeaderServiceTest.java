package vitbuk.com.Ambotorix.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vitbuk.com.Ambotorix.chat.UserRef;
import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.entities.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LeaderServiceTest {

    private LeaderService leaderService;
    private List<Leader> allLeaders;

    @BeforeEach
    void setUp() {
        allLeaders = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            allLeaders.add(new Leader("Leader " + i, "leader" + i, "desc", "path" + i));
        }
        leaderService = new LeaderService(allLeaders);
    }

    @Test
    void setLeadersPool_excludesBannedLeaders() {
        Player host = new Player(UserRef.telegram(1L, "host"));
        Player player2 = new Player(UserRef.telegram(2L, "player2"));
        Leader bannedLeader = allLeaders.get(0);
        host.getBans().add(bannedLeader);

        // Confirm the banned leader is in the full pool (i.e., filtering is what keeps it out)
        assertTrue(leaderService.getLeaders().contains(bannedLeader),
                "Setup: banned leader must exist in the full leader pool");

        Lobby lobby = new Lobby(host);
        lobby.addPlayer(player2);

        Lobby result = leaderService.setLeadersPool(lobby);

        for (Player p : result.getPlayers()) {
            assertFalse(p.getPicks().contains(bannedLeader),
                    "Banned leader should not appear in picks for " + p.getUserName());
        }
    }

    @Test
    void setLeadersPool_assignsUniquePicksAcrossPlayers() {
        // 2 players, pickSize=6, 20 leaders → 20 > 12, picks are assigned from a shuffled
        // non-repeating iterator so all assigned picks across all players are unique
        Player host = new Player(UserRef.telegram(1L, "host"));
        Player player2 = new Player(UserRef.telegram(2L, "player2"));

        Lobby lobby = new Lobby(host);
        lobby.addPlayer(player2);

        Lobby result = leaderService.setLeadersPool(lobby);

        List<Leader> allPicks = new ArrayList<>();
        for (Player p : result.getPlayers()) {
            allPicks.addAll(p.getPicks());
        }

        Set<Leader> uniquePicks = new HashSet<>(allPicks);
        assertEquals(allPicks.size(), uniquePicks.size(),
                "All picks across all players should be unique (no leader assigned twice)");
    }

    @Test
    void getLeaderByShortName_isCaseInsensitive() {
        // allLeaders contains "leader1" as shortName; querying in upper case should still find it
        Leader found = leaderService.getLeaderByShortName("LEADER1");
        assertNotNull(found, "Should find a leader by short name regardless of case");
        assertEquals("Leader 1", found.getFullName());
    }

    @Test
    void getLeaderByShortName_returnsNull_whenNotFound() {
        Leader found = leaderService.getLeaderByShortName("nonexistent");
        assertNull(found, "Should return null when no leader matches the short name");
    }
}
