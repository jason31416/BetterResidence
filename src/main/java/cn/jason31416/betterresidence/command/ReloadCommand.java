package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;

public class ReloadCommand extends ChildCommand {
    public static final String PERMISSION = "betterresidence.admin";

    public ReloadCommand(IParentCommand parent) {
        super("reload", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (!context.sender().sender().hasPermission(PERMISSION)) {
            return Lang.getMessage("command.no-permission");
        }

        try {
            BetterResidence.getInstance().reloadPluginConfig();
            return Lang.getMessage("command.reload-success");
        } catch (Exception e) {
            return Lang.getMessage("command.reload-failed").copy().add("error", e.getMessage());
        }
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }
}
