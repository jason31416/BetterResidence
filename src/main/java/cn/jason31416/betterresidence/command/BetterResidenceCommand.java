package cn.jason31416.betterresidence.command;

import cn.jason31416.planetlib.command.RootCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.message.Message;

public class BetterResidenceCommand extends RootCommand {

    public BetterResidenceCommand() {
        super("betterresidence");

        new AdminCommand(this);
        new ReloadCommand(this);
        new TrustCommand(this);
        new UntrustCommand(this);
        new SetCommand(this);
        new FlagCommand(this);
        new FlagListCommand(this);
        new InfoCommand(this);
        new ListCommand(this);
        new TpCommand(this);
        new SetTpCommand(this);
        new CreateCommand(this);
        new RemoveCommand(this);
        new AreaCommand(this);
        new ResizeCommand(this, "expand", true);
        new ResizeCommand(this, "contract", false);
    }

    @Override
    public Message execute(ICommandContext context) {
        return null;
    }

    // We don't need to manually handle tab complete for rootcommand.
}
