package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.command.ParameterType;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimplePlayer;

import java.util.List;

public class SetOwnerCommand extends ChildCommand {
    public SetOwnerCommand(IParentCommand parent) {
        super("setowner", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        if (context.args().size() != 1) {
            return Lang.getMessage("command.setowner-usage");
        }

        Claim claim = ClaimManager.findClaimAt(context.player().getLocation());
        if (claim == null) {
            return Lang.getMessage("command.not-in-claim");
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.removeclaim", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }
        if (!context.checkArgs(ParameterType.PLAYER)) {
            return null;
        }

        SimplePlayer newOwner = context.getPlayerArg(0);
        if (newOwner.equals(claim.getOwner())) {
            return Lang.getMessage("command.setowner-same-owner").copy()
                    .add("player", ClaimCommandFormat.escape(newOwner.getName()))
                    .add("claim", ClaimCommandFormat.escape(claim.getName()));
        }

        claim.setPlayerGroup(newOwner, null);
        ClaimManager.setClaimOwner(claim.getUuid(), newOwner);
        return Lang.getMessage("command.setowner-success").copy()
                .add("player", ClaimCommandFormat.escape(newOwner.getName()))
                .add("claim", ClaimCommandFormat.escape(claim.getName()));
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }
}
