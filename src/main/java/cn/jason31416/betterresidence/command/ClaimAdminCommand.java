package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import javax.annotation.Nullable;

/**
 * The parent class for commands requiring in-claim permission of `admin`.
 */
public abstract class ClaimAdminCommand extends ChildCommand {
    protected ClaimAdminCommand(String name, IParentCommand parent) {
        super(name, parent);
    }

    @Nullable
    protected Message validateClaimAdmin(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }

        Claim claim = getClaim(context);
        if (claim == null) {
            return Lang.getMessage("command.not-in-claim");
        }

        if (!claim.checkPlayerPermission(context.player(), "admin", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }

        return null;
    }

    @Nullable
    protected Claim getClaim(ICommandContext context) {
        return ClaimManager.findClaimAt(context.player().getLocation());
    }
}
