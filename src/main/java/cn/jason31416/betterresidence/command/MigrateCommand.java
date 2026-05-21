package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.handler.migration.ResidenceMigration;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class MigrateCommand extends ChildCommand {
    public MigrateCommand(IParentCommand parent) {
        super("migrate", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (!context.sender().sender().hasPermission(AdminCommand.PERMISSION)) {
            return Lang.getMessage("command.no-permission");
        }

        boolean dryRun = context.args().stream().anyMatch(arg -> arg.equalsIgnoreCase("--dry-run"));
        Plugin residence = Bukkit.getPluginManager().getPlugin("Residence");
        if (residence == null || !residence.isEnabled()) {
            context.sender().sender().sendMessage("[BetterResidence] No supported migration source is installed. Currently supported: Residence.");
            return null;
        }

        context.sender().sender().sendMessage("[BetterResidence] Starting Residence migration" + (dryRun ? " dry run" : "") + "...");
        ResidenceMigration.MigrationResult result = ResidenceMigration.migrate(context.sender().sender(), dryRun);
        context.sender().sender().sendMessage(result.formatSummary());
        for (String warning : result.warnings()) {
            context.sender().sender().sendMessage("[BetterResidence] " + warning);
        }
        return null;
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().size() == 1) {
            return List.of("--dry-run").stream()
                    .filter(option -> option.startsWith(context.getArg(0)))
                    .toList();
        }
        return List.of();
    }
}
