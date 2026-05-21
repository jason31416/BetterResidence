package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;
import org.bukkit.Location;

import java.util.List;

public class SetTpCommand extends ChildCommand {
    public SetTpCommand(IParentCommand parent) {
        super("settp", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        if (!context.args().isEmpty()) {
            return Lang.getMessage("command.settp-usage");
        }

        Location location = context.player().getPlayer().getLocation();
        Claim claim = ClaimManager.findClaimAt(context.player().getLocation());
        if (claim == null) {
            return Lang.getMessage("command.not-in-claim");
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.setflag", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }
        if (!TpCommand.isInsideClaim(location, claim)) {
            return Lang.getMessage("command.settp-outside");
        }

        claim.setFlag(TpCommand.TELEPORT_LOCATION_FLAG, TpCommand.formatTeleportLocation(location));
        return Lang.getMessage("command.settp-success").copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()));
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }
}
