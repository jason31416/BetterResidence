package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.core.PermissionRegistry;
import cn.jason31416.betterresidence.core.PermissionTargetType;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.command.ParameterType;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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

        String permission = normalizePermission(context.getArg(0));
        Optional<PermissionTargetType> targetType = getTargetType(permission);
        if (targetType.isEmpty()) {
            return Lang.getMessage("command.permission-not-found").copy()
                    .add("permission", context.getArg(0));
        }
        if (permission.contains(":") && targetType.get().equals(PermissionTargetType.NONE)) {
            return Lang.getMessage("command.permission-target-unsupported").copy()
                    .add("permission", getBasePermission(permission));
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
            return tabCompletePermission(context.getArg(0));
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

    private String normalizePermission(String permission) {
        int targetSeparator = permission.indexOf(':');
        String basePermission = targetSeparator >= 0 ? permission.substring(0, targetSeparator) : permission;
        String target = targetSeparator >= 0 ? permission.substring(targetSeparator) : "";
        if (!PermissionRegistry.isRegistered(basePermission) && isPermissionNamespace(basePermission)) {
            return basePermission + ".*" + target;
        }
        return permission;
    }

    private List<String> tabCompletePermission(String input) {
        int materialSeparator = input.indexOf(':');
        if (materialSeparator >= 0) {
            String permission = input.substring(0, materialSeparator);
            String materialPrefix = input.substring(materialSeparator + 1);
            return tabCompleteMaterial(permission, materialPrefix);
        }

        return Stream.concat(PermissionRegistry.getPermissionIds().stream(), PermissionRegistry.getNamespaces().stream())
                .distinct()
                .filter(permission -> permission.startsWith(input))
                .filter(permission -> shouldShowPermissionCompletion(input, permission))
                .toList();
    }

    private boolean isPermissionNamespace(String permission) {
        return !permission.contains(".")
                && !permission.contains(":")
                && PermissionRegistry.getNamespaces().contains(permission);
    }

    private boolean shouldShowPermissionCompletion(String input, String permission) {
        return input.contains(".")
                || !permission.contains(".")
                || permission.substring(0, permission.indexOf('.')).equals(input);
    }

    private List<String> tabCompleteMaterial(String permission, String materialPrefix) {
        if (materialPrefix.isEmpty()) {
            return List.of();
        }

        Optional<PermissionTargetType> targetType = getTargetType(normalizePermission(permission));
        if (targetType.isEmpty()) {
            return List.of();
        }

        return targetType.get().tabComplete(materialPrefix).stream()
                .map(material -> permission + ":" + material)
                .toList();
    }

    private Optional<PermissionTargetType> getTargetType(String permission) {
        String basePermission = getBasePermission(permission);
        if (basePermission.endsWith(".*")) {
            return PermissionRegistry.getNamespaceTargetType(basePermission.substring(0, basePermission.length() - 2));
        }
        return PermissionRegistry.getPermission(basePermission).map(PermissionRegistry.RegisteredPermission::targetType);
    }

    private String getBasePermission(String permission) {
        int targetSeparator = permission.indexOf(':');
        if (targetSeparator >= 0) {
            return permission.substring(0, targetSeparator);
        }
        return permission;
    }
}
