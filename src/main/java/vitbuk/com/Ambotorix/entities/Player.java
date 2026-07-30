package vitbuk.com.Ambotorix.entities;

import vitbuk.com.Ambotorix.chat.MessageRef;
import vitbuk.com.Ambotorix.chat.UserRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Player {
    private final UserRef user;
    private List<Leader> picks;
    private List<Leader> bans;
    private List<String> priorityPicks = new ArrayList<>();
    // The player's own pick grid, kept so it can be re-rendered on each tap and locked on submit.
    private MessageRef dmPickMessage;

    public Player(UserRef user) {
        this.user = user;
        this.picks = new ArrayList<>();
        this.bans = new ArrayList<>();
    }

    public UserRef getUser() { return user; }
    public String getUserName() { return user.userName(); }
    public List<Leader> getPicks() { return picks; }
    public void setPicks(List<Leader> picks) { this.picks = picks; }
    public List<Leader> getBans() { return bans; }
    public void setBans(List<Leader> bans) { this.bans = bans; }
    public void ban(Leader leader) { bans.add(leader); }

    public List<String> getPriorityPicks() { return priorityPicks; }
    public void setPriorityPicks(List<String> priorityPicks) { this.priorityPicks = priorityPicks; }
    public MessageRef getDmPickMessage() { return dmPickMessage; }
    public void setDmPickMessage(MessageRef dmPickMessage) { this.dmPickMessage = dmPickMessage; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return getUserName().equals(player.getUserName());
    }

    @Override
    public int hashCode() { return Objects.hash(getUserName()); }
}
