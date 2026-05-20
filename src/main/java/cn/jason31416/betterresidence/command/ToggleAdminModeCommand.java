package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.AdminModeManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;

public class ToggleAdminModeCommand extends ChildCommand {
    public ToggleAdminModeCommand(IParentCommand parent) {
        super("toggleadminmode", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (!context.sender().sender().hasPermission(AdminCommand.PERMISSION)) {
            return Lang.getMessage("command.no-permission");
        }
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }

        boolean enabled = AdminModeManager.toggleAdminMode(context.player().getPlayer());
        return Lang.getMessage(enabled ? "command.admin-mode-enabled" : "command.admin-mode-disabled");
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }
}
