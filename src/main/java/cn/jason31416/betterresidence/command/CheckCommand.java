package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimGroup;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.core.PermissionNode;
import cn.jason31416.betterresidence.core.PermissionTargetType;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;
import it.unimi.dsi.fastutil.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CheckCommand extends ChildCommand {
    public CheckCommand(IParentCommand parent) {
        super("check", parent);
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
        if (context.args().size() != 1) {
            return Lang.getMessage("command.check-usage");
        }

        String permission = context.getArg(0);
        Optional<PermissionTargetType> targetType = PermissionCommandFormat.getTargetType(permission);
        if (targetType.isEmpty()) {
            return Lang.getMessage("command.permission-not-found").copy()
                    .add("permission", ClaimCommandFormat.escape(permission));
        }
        if (permission.contains(":") && targetType.get().equals(PermissionTargetType.NONE)) {
            return Lang.getMessage("command.permission-target-unsupported").copy()
                    .add("permission", PermissionCommandFormat.getBasePermission(permission));
        }

        String basePermission = PermissionCommandFormat.getBasePermission(permission);
        String target = PermissionCommandFormat.getTarget(permission);
        Optional<PermissionNode> permissionNode = claim.findPermissionNode(basePermission, target);
        int requiredWeight = permissionNode
                .map(PermissionNode::getWeight)
                .orElse(DefaultClaimGroupRegistry.MAX_WEIGHT);
        ClaimGroup requiredGroup = findMinimumRequiredGroup(claim, requiredWeight);
        Pair<String, Integer> playerGroup = claim.getPlayerGroup(context.player());
        boolean allowed = claim.checkPlayerPermission(context.player(), basePermission, target);

        return Lang.getMessageList("command.check-message")
                .copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("status", Lang.getMessage(allowed ? "command.check-allowed" : "command.check-denied")
                        .add("group", ClaimCommandFormat.escape(playerGroup.first()))
                        .add("permission", PermissionCommandFormat.displayPermission(permission, targetType.get()))
                        .toFormatted())
                .add("required-group", ClaimCommandFormat.escape(requiredGroup.name()))
                .add("required-weight", requiredWeight);
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().size() == 1) {
            return PermissionCommandFormat.tabCompletePermission(context.getArg(0));
        }
        return List.of();
    }

    private ClaimGroup findMinimumRequiredGroup(Claim claim, int requiredWeight) {
        List<ClaimGroup> groups = new ArrayList<>();
        groups.add(new ClaimGroup("everyone", DefaultClaimGroupRegistry.getEveryoneName(), DefaultClaimGroupRegistry.EVERYONE_WEIGHT));
        groups.add(new ClaimGroup("visitor", DefaultClaimGroupRegistry.getVisitorName(), DefaultClaimGroupRegistry.VISITOR_WEIGHT));
        groups.addAll(claim.getClaimGroups());

        return groups.stream()
                .filter(group -> group.weight() >= requiredWeight)
                .min(Comparator.comparingInt(ClaimGroup::weight))
                .orElse(DefaultClaimGroupRegistry.getOwnerGroup());
    }
}
