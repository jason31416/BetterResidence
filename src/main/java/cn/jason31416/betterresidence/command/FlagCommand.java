package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.FlagRegistry;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;
import java.util.Optional;

public class FlagCommand extends ChildCommand {
    public FlagCommand(IParentCommand parent) {
        super("flag", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        Claim claim = ClaimManager.findClaimAt(context.player().getLocation());
        if (claim == null) {
            return Lang.getMessage("command.not-in-claim");
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.setflag", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }
        if (context.args().isEmpty()) {
            return Lang.getMessage("command.flag-usage");
        }

        String flagId = context.getArg(0);
        Optional<FlagRegistry.RegisteredFlag> flag = FlagRegistry.getFlag(flagId);
        if (flag.isEmpty()) {
            return Lang.getMessage("command.flag-not-found").copy()
                    .add("flag", ClaimCommandFormat.escape(flagId));
        }

        if (context.args().size() == 1) {
            return Lang.getMessage("command.flag-get-success").copy()
                    .add("claim", ClaimCommandFormat.escape(claim.getName()))
                    .add("flag", ClaimCommandFormat.escape(flagId))
                    .add("value", ClaimCommandFormat.escape(formatValue(claim.getFlag(flag.get()))));
        }

        String value = String.join(" ", context.args().subList(1, context.args().size()));
        if (!claim.setFlag(flagId, value)) {
            return Lang.getMessage("command.flag-invalid-value").copy()
                    .add("flag", ClaimCommandFormat.escape(flagId))
                    .add("value", ClaimCommandFormat.escape(value));
        }

        return Lang.getMessage("command.flag-set-success").copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("flag", ClaimCommandFormat.escape(flagId))
                .add("value", ClaimCommandFormat.escape(formatValue(value)));
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().size() == 1) {
            String input = context.getArg(0);
            return FlagRegistry.getFlagIds().stream()
                    .filter(flag -> flag.startsWith(input))
                    .toList();
        }
        if (context.args().size() == 2) {
            return FlagRegistry.getFlag(context.getArg(0))
                    .map(flag -> flag.tabCompleteValue(context.getArg(1)))
                    .orElse(List.of());
        }
        return List.of();
    }

    private String formatValue(String value) {
        return value.isEmpty() ? ClaimCommandFormat.raw("command.format.empty-value") : value;
    }
}
