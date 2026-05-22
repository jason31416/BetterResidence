package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.core.PermissionTargetType;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.command.ParameterType;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;
import java.util.Optional;

public class SetCommand extends ChildCommand {
    public SetCommand(IParentCommand parent) {
        super("set", parent);
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
        if (!claim.checkPlayerPermission(context.player(), "admin.setpermission", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }
        if (!context.checkArgs(ParameterType.STRING, ParameterType.STRING)) {
            return null;
        }

        String permission = context.getArg(0);
        Optional<PermissionTargetType> targetType = PermissionCommandFormat.getTargetType(permission);
        if (targetType.isEmpty()) {
            return Lang.getMessage("command.permission-not-found").copy()
                    .add("permission", context.getArg(0));
        }
        if (permission.contains(":") && targetType.get().equals(PermissionTargetType.NONE)) {
            return Lang.getMessage("command.permission-target-unsupported").copy()
                    .add("permission", PermissionCommandFormat.getBasePermission(permission));
        }

        String groupName = context.getArg(1);
        Optional<Integer> groupWeight = claim.getGroupWeight(groupName);
        if (groupWeight.isEmpty()) {
            return Lang.getMessage("command.group-not-found").copy()
                    .add("group", groupName);
        }

        claim.setPermission(permission, groupWeight.get());
        return Lang.getMessage("command.set-permission-success").copy()
                .add("permission", permission)
                .add("group", groupName)
                .add("claim", claim.getName());
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().size() == 1) {
            return PermissionCommandFormat.tabCompletePermission(context.getArg(0));
        }
        if (context.args().size() == 2) {
            Claim claim = getClaim(context);
            if (claim == null) {
                return List.of();
            }
            List<TrustCommand.GroupCompletion> groups = new java.util.ArrayList<>();
            // /res set accepts non-real threshold aliases in addition to real claim groups.
            // Initially planned to have everyone, but we removed it realizing that it is a bit confusing.
//            groups.add(new TrustCommand.GroupCompletion(DefaultClaimGroupRegistry.getEveryoneName(), DefaultClaimGroupRegistry.EVERYONE_WEIGHT));
            groups.add(new TrustCommand.GroupCompletion(DefaultClaimGroupRegistry.getVisitorName(), DefaultClaimGroupRegistry.VISITOR_WEIGHT));
            groups.addAll(claim.getClaimGroups().stream().map(TrustCommand.GroupCompletion::from).toList());
            return TrustCommand.completeGroups(groups);
        }
        return List.of();
    }

    private Claim getClaim(ICommandContext context) {
        return ClaimManager.findClaimAt(context.player().getLocation());
    }
}
