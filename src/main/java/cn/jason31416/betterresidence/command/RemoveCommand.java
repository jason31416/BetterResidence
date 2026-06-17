package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RemoveCommand extends ChildCommand {
    private static final long CONFIRM_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final Map<UUID, PendingRemoval> PENDING_REMOVALS = new ConcurrentHashMap<>();

    public RemoveCommand(IParentCommand parent) {
        super("remove", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.args().size() == 1 && context.getArg(0).equalsIgnoreCase("confirm")) {
            return confirmRemoval(context);
        }

        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        Claim claim = getClaim(context);
        if (claim == null) {
            return Lang.getMessage("command.not-in-claim");
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.removeclaim", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }

        PendingRemoval pendingRemoval = new PendingRemoval(claim.getUuid(), System.currentTimeMillis() + CONFIRM_TIMEOUT_MILLIS);
        PENDING_REMOVALS.put(context.player().getUUID(), pendingRemoval);

        return createConfirmMessage(claim);
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().size() == 1 && "confirm".startsWith(context.getArg(0).toLowerCase(java.util.Locale.ROOT))) {
            return List.of("confirm");
        }
        return List.of();
    }

    private Message confirmRemoval(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }

        PendingRemoval pendingRemoval = PENDING_REMOVALS.get(context.player().getUUID());
        if (pendingRemoval == null) {
            return Lang.getMessage("command.remove-no-pending");
        }
        if (pendingRemoval.expiresAtMillis() < System.currentTimeMillis()) {
            PENDING_REMOVALS.remove(context.player().getUUID());
            return Lang.getMessage("command.remove-expired");
        }

        Claim claim = ClaimManager.fetchClaim(pendingRemoval.claimUuid());
        if (claim == null) {
            PENDING_REMOVALS.remove(context.player().getUUID());
            return Lang.getMessage("command.claim-not-found").copy()
                    .add("claim", ClaimCommandFormat.shortUuid(pendingRemoval.claimUuid()));
        }
        if (!claim.checkPlayerPermission(context.player(), "admin.removeclaim", null)) {
            return Lang.getMessage("command.no-claim-admin");
        }

        String claimName = claim.getName();
        String parentName = getPromotedParentName(claim);
        int promotedCount = claim.getSubClaims().size();
        claim.remove();
        PENDING_REMOVALS.remove(context.player().getUUID());

        return Lang.getMessage("command.remove-success").copy()
                .add("claim", claimName)
                .add("promoted-count", promotedCount)
                .add("parent", parentName);
    }

    private Message createConfirmMessage(Claim claim) {
        List<ClaimManager.ClaimAreaInfo> areas = ClaimManager.fetchClaimAreas(claim.getUuid());
        return Lang.getMessageList("command.remove-confirm-message")
                .copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("uuid", claim.getUuid())
                .add("short-uuid", ClaimCommandFormat.shortUuid(claim.getUuid()))
                .add("area-summary", ClaimCommandFormat.escape(ClaimCommandFormat.areaSummary(areas)))
                .add("subclaim-count", claim.getSubClaims().size())
                .add("parent", getPromotedParentName(claim))
                .add("seconds", TimeUnit.MILLISECONDS.toSeconds(CONFIRM_TIMEOUT_MILLIS))
                .add("confirm", ClaimCommandFormat.rawMessage("command.remove-confirm-button")
                        .add("command", "/res remove confirm")
                        .toFormatted());
    }

    private String getPromotedParentName(Claim claim) {
        if (claim.getParentUuid() == null) {
            return ClaimCommandFormat.raw("command.format.none");
        }
        Claim parent = ClaimManager.fetchClaim(claim.getParentUuid());
        if (parent == null) {
            return ClaimCommandFormat.raw("command.format.none");
        }
        return ClaimCommandFormat.escape(parent.getName());
    }

    private Claim getClaim(ICommandContext context) {
        return ClaimManager.findClaimAt(context.player().getLocation());
    }

    private record PendingRemoval(String claimUuid, long expiresAtMillis) {
    }
}
