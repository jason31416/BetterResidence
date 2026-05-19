package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.handler.DataIntegrityHandler;
import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.nio.file.Path;
import java.util.List;

public class FsckCommand extends ChildCommand {
    public FsckCommand(IParentCommand parent) {
        super("fsck", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (!context.sender().sender().hasPermission(AdminCommand.PERMISSION)) {
            return Lang.getMessage("command.no-permission");
        }

        context.sender().sendMessage(Lang.getMessage("command.fsck-started"));
        PlanetLib.getScheduler().runAsync(task -> {
            int failedChecks = DataIntegrityHandler.checkDataIntegrity(progress -> Lang.getMessage("command.fsck-check-completed")
                    .copy()
                    .add("check", progress.name())
                    .add("status", Lang.getMessage(progress.failed() ? "command.fsck-check-status-failed" : "command.fsck-check-status-passed"))
                    .add("rows", progress.corruptedRows())
                    .send(context.sender().sender()));
            Path reportFile = DataIntegrityHandler.getLastReportFile();
            if (failedChecks == 0) {
                context.sender().sendMessage(Lang.getMessage("command.fsck-success"));
                return;
            }
            String reportText = reportFile == null
                    ? Lang.getMessage("command.fsck-report-unavailable").toFormatted()
                    : reportFile.toString();
            context.sender().sendMessage(Lang.getMessage("command.fsck-failed")
                    .copy()
                    .add("failed", failedChecks)
                    .add("report", reportText));
        });

        return null;
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }
}
