package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;

public class InfoCommand extends ChildCommand {
    public InfoCommand(IParentCommand parent) {
        super("info", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        Claim claim;
        if (context.args().isEmpty()) {
            if (context.player() == null) {
                return Lang.getMessage("command.info-player-only");
            }
            claim = ClaimManager.findClaimAt(context.player().getLocation());
            if (claim == null) {
                return Lang.getMessage("command.not-in-claim");
            }
        } else {
            String claimInput = context.getArg(0);
            claim = resolveClaim(claimInput);
            if (claim == null) {
                return Lang.getMessage("command.claim-not-found").copy()
                        .add("claim", ClaimCommandFormat.escape(claimInput));
            }
        }

        return createInfoMessage(context, claim);
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }

    private Claim resolveClaim(String input) {
        Claim claim = ClaimManager.fetchClaim(input);
        if (claim != null) {
            return claim;
        }
        return ClaimManager.fetchClaimByName(input);
    }

    private Message createInfoMessage(ICommandContext context, Claim claim) {
        List<ClaimManager.ClaimMemberInfo> members = ClaimManager.fetchClaimMembers(claim.getUuid());
        Claim parent = claim.getParentUuid() == null ? null : ClaimManager.fetchClaim(claim.getParentUuid());

        String currentGroup = context.player() == null
                ? ClaimCommandFormat.raw("command.format.console")
                : claim.getPlayerGroup(context.player()).first();
        String parentText = parent == null
                ? ClaimCommandFormat.raw("command.format.none")
                : ClaimCommandFormat.rawMessage("command.format.parent-link")
                .add("uuid", parent.getUuid())
                .add("claim", ClaimCommandFormat.escape(parent.getName()))
                .toString();

        return Lang.getMessageList("command.info-message")
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("owner", ClaimCommandFormat.escape(claim.getOwner().getName()))
                .add("parent", parentText)
                .add("depth", claim.getDepth())
                .add("subclaim-count", claim.getSubClaims().size())
                .add("subclaims", ClaimCommandFormat.subClaimHover(claim))
                .add("group", ClaimCommandFormat.escape(currentGroup))
                .add("group-count", claim.getClaimGroups().size())
                .add("groups", ClaimCommandFormat.groupHover(claim))
                .add("member-count", members.size())
                .add("members", ClaimCommandFormat.memberHover(claim, members));
    }
}
