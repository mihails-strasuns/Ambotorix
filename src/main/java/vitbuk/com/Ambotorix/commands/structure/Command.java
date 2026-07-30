package vitbuk.com.Ambotorix.commands.structure;

import vitbuk.com.Ambotorix.services.AmbotorixService;

public interface Command {
    CommandInfo getInfo();
    void execute(CommandContext ctx, AmbotorixService ambotorixService);
}
