package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimGroup;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.command.ParameterType;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;

public class TrustCommand extends ChildCommand {
    public TrustCommand(IParentCommand parent) {
        super("trust", parent);
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
        String groupName = context.args().size() >= 2
                ? context.getArg(1)
                : DefaultClaimGroupRegistry.getTrustedGroup().name();

        if (!claim.setPlayerGroup(target, groupName)) {
            return Lang.getMessage("command.group-not-found").copy()
                    .add("group", groupName);
        }

        if (groupName.equals(DefaultClaimGroupRegistry.getVisitorName())) {
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
        if (context.args().size() == 2 && context.player() != null) {
            Claim claim = getClaim(context);
            if (claim == null) {
                return List.of();
            }
            List<GroupCompletion> groups = new java.util.ArrayList<>();
            // Visitor is not a real group, but accepting it here removes the player's group.
            groups.add(new GroupCompletion(DefaultClaimGroupRegistry.getVisitorName(), DefaultClaimGroupRegistry.VISITOR_WEIGHT));
            groups.addAll(claim.getClaimGroups().stream()
                    .filter(group -> !group.id().equals(DefaultClaimGroupRegistry.OWNER_ID))
                    .map(GroupCompletion::from)
                    .toList());
            return completeGroups(groups);
        }
        return List.of();
    }

    private Claim getClaim(ICommandContext context) {
        return ClaimManager.findClaimAt(context.player().getLocation());
    }

    /**
     * Command completion item that keeps the display name together with its permission weight.
     */
    public record GroupCompletion(String name, int weight) {
        public static GroupCompletion from(ClaimGroup group) {
            return new GroupCompletion(group.name(), group.weight());
        }
    }

    /**
     * Supposed to sort group completions from highest permission weight to lowest, but tab completion probably don't support ordered.
     */
    protected static List<String> completeGroups(List<GroupCompletion> groups) {
        return groups.stream()
//                .sorted(Comparator.comparingInt(GroupCompletion::weight).reversed())
                .map(GroupCompletion::name)
                .toList();
    }
}
