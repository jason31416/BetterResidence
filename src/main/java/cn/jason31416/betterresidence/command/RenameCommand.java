package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.ClaimNameValidator;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;

public class RenameCommand extends ChildCommand {
    public RenameCommand(IParentCommand parent) {
        super("rename", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        if (context.args().size() != 1) {
            return Lang.getMessage("command.rename-usage");
        }

        Claim claim = ClaimManager.findClaimAt(context.player().getLocation());
        if (claim == null) {
            return Lang.getMessage("command.not-in-claim");
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.rename", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }

        String newName = context.getArg(0);
        if (!ClaimNameValidator.isValid(newName)) {
            return Lang.getMessage("command.rename-invalid-name").copy()
                    .add("claim", newName)
                    .add("regex", ClaimNameValidator.getRegex());
        }
        if (claim.getName().equals(newName)) {
            return Lang.getMessage("command.rename-same-name").copy()
                    .add("claim", ClaimCommandFormat.escape(claim.getName()));
        }
        if (ClaimManager.claimNameExists(newName)) {
            return Lang.getMessage("command.rename-name-exists").copy()
                    .add("claim", ClaimCommandFormat.escape(newName));
        }

        String oldName = claim.getName();
        ClaimManager.renameClaim(claim.getUuid(), newName);
        return Lang.getMessage("command.rename-success").copy()
                .add("old-claim", ClaimCommandFormat.escape(oldName))
                .add("claim", ClaimCommandFormat.escape(newName));
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }
}
