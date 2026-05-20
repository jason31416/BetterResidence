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

public class UntrustCommand extends ChildCommand {
    public UntrustCommand(IParentCommand parent) {
        super("untrust", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        Claim claim = getClaim(context);
        if (claim == null) {
            return Lang.getMessage("command.not-in-claim");
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.trust", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }
        if (!context.checkArgs(ParameterType.PLAYER)) {
            return null;
        }

        SimplePlayer target = context.getPlayerArg(0);
        claim.setPlayerGroup(target, null);

        return Lang.getMessage("command.untrust-success").copy()
                .add("player", target.getName())
                .add("claim", claim.getName());
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }

    private Claim getClaim(ICommandContext context) {
        return ClaimManager.findClaimAt(context.player().getLocation());
    }
}
