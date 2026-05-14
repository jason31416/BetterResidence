package cn.jason31416.betterresidence.command;

import cn.jason31416.planetlib.command.RootCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.message.Message;

import java.util.List;

public class BetterResidenceCommand extends RootCommand {

    public BetterResidenceCommand() {
        super("betterresidence");

        new ReloadCommand(this);
    }

    @Override
    public Message execute(ICommandContext context) {
        return null;
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of("reload");
    }
}
