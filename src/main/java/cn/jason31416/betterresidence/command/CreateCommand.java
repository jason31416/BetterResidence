package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.selection.ClaimCreationValidator;
import cn.jason31416.betterresidence.selection.SelectionManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import org.bukkit.entity.Player;

import java.util.List;

public class CreateCommand extends ChildCommand {
    public CreateCommand(IParentCommand parent) {
        super("create", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        if (context.player() == null) {
            return Lang.getMessage("command.player-only");
        }
        if (context.args().isEmpty()) {
            return Lang.getMessage("command.create-usage");
        }

        String claimName = context.getArg(0);
        if (ClaimManager.claimNameExists(claimName)) {
            return Lang.getMessage("command.create-name-exists").copy()
                    .add("claim", claimName);
        }

        Player player = context.player().getPlayer();
        SelectionManager.Selection selection = SelectionManager.getSelection(player);
        ClaimCreationValidator.ValidationResult result = ClaimCreationValidator.validate(player, selection);
        if (!result.valid()) {
            return createValidationError(result);
        }

        SimplePlayer owner = SimplePlayer.of(player);
        if (result.creationType() == ClaimCreationValidator.CreationType.TOP_LEVEL) {
            // Only top-level claims are paid creations. Subclaims are spatial subdivisions inside an
            // already-owned/administered claim, so they skip money checks and player top-level limits.
            if (owner.getBalance() < result.price() || !owner.withdrawBalance(result.price())) {
                return Lang.getMessage("command.create-not-enough-money").copy()
                        .add("price", formatPrice(result.price()))
                        .add("size", result.size());
            }
        }

        Claim parent = result.parentClaim();
        Claim createdClaim = ClaimManager.createClaim(
                owner,
                claimName,
                parent == null ? null : parent.getUuid(),
                SimpleWorld.of(result.world()),
                result.areaBox()
        );
        if (parent != null) {
            // Subclaims do not inherit permissions or members, but flags are copied once at creation
            // so environmental/messages settings start consistent with the parent and can diverge later.
            ClaimManager.copyClaimFlags(parent.getUuid(), createdClaim.getUuid());
        }
        SelectionManager.clearSelection(player);
        if (parent != null) {
            return Lang.getMessage("command.create-subclaim-success").copy()
                    .add("claim", claimName)
                    .add("parent", parent.getName())
                    .add("size", result.size());
        }
        return Lang.getMessage("command.create-success").copy()
                .add("claim", claimName)
                .add("price", formatPrice(result.price()))
                .add("size", result.size());
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }

    private Message createValidationError(ClaimCreationValidator.ValidationResult result) {
        String key = switch (result.reason()) {
            case INCOMPLETE_SELECTION -> "command.create-no-selection";
            case DIFFERENT_WORLDS -> "command.create-different-worlds";
            case OVERLAP -> "command.create-overlap";
            case PARTIAL_OVERLAP -> "command.create-partial-overlap";
            case NO_PARENT_ADMIN -> "command.create-no-parent-admin";
            case MAX_CLAIMS -> "command.create-max-claims";
            case MAX_SUBCLAIMS -> "command.create-max-subclaims";
            case MAX_SUBCLAIM_DEPTH -> "command.create-max-subclaim-depth";
            case NOT_ENOUGH_MONEY -> "command.create-not-enough-money";
            case SUBCLAIM_OVERLAP -> "command.create-subclaim-overlap";
            default -> "command.create-unavailable";
        };
        return Lang.getMessage(key).copy()
                .add("price", formatPrice(result.price()))
                .add("size", result.size())
                .add("max", Config.getInt("claim.max-claims-per-player"))
                .add("max-subclaims", Config.getInt("claim.max-subclaims-per-claim"))
                .add("max-depth", Config.getInt("claim.max-subclaim-depth"))
                .add("parent", result.parentClaim() == null ? "" : result.parentClaim().getName())
                .add("conflict", result.conflict());
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return Long.toString((long) price);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", price);
    }
}
