package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.command.ParameterType;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimplePlayer;

import java.util.List;

public class TrustCommand extends ClaimAdminCommand {
    public TrustCommand(IParentCommand parent) {
        super("trust", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        Message validationError = validateClaimAdmin(context);
        if (validationError != null) {
            return validationError;
        }
        if (!context.checkArgs(ParameterType.PLAYER)) {
            return null;
        }

        Claim claim = getClaim(context);
        SimplePlayer target = context.getPlayerArg(0);
        String groupName = context.args().size() >= 2
                ? context.getArg(1)
                : Lang.messageLoader.getRawMessage("claim.group.trusted", "Trusted");

        if (!claim.setPlayerGroup(target, groupName)) {
            return Lang.getMessage("command.group-not-found").copy()
                    .add("group", groupName);
        }

        String noneGroupName = Lang.messageLoader.getRawMessage("claim.group.none", "None");
        if (groupName.equals(noneGroupName)) {
            return Lang.getMessage("command.untrust-success").copy()
                    .add("player", target.getName())
                    .add("claim", claim.getName());
        }

        return Lang.getMessage("command.trust-success").copy()
                .add("player", target.getName())
                .add("group", groupName)
                .add("claim", claim.getName());
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }
}
