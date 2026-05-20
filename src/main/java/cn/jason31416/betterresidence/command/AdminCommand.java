package cn.jason31416.betterresidence.command;

import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.command.ParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;

public class AdminCommand extends ParentCommand {
    public static final String PERMISSION = "betterresidence.admin";

    public AdminCommand(IParentCommand parent) {
        super("admin", parent);
        new FsckCommand(this);
        new ToggleAdminModeCommand(this);
    }

    @Override
    public Message executeRaw(ICommandContext context) {
        if (!context.sender().sender().hasPermission(PERMISSION)) {
            return Lang.getMessage("command.no-permission");
        }
        return Lang.getMessage("command.admin-help");
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (!context.sender().sender().hasPermission(PERMISSION)) {
            return List.of();
        }
        return super.tabComplete(context);
    }
}
