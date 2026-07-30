package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.GeneralCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class PhotoChallengeCommand implements GeneralCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/photochallenge",
            "/photochallenge",
            "Show the photo-challenge leaderboard");

    @Override
    public CommandInfo getInfo() {
        return INFO;
    }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendPhotoChallenge(ctx);
    }
}
