package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.claim.ClaimManager;
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
        // Re-checking immediately before withdrawal keeps command execution safe even if preview state is stale.
        if (owner.getBalance() < result.price() || !owner.withdrawBalance(result.price())) {
            return Lang.getMessage("command.create-not-enough-money").copy()
                    .add("price", formatPrice(result.price()))
                    .add("size", result.size());
        }

        ClaimManager.createClaim(owner, claimName, null, SimpleWorld.of(result.world()), result.areaBox());
        SelectionManager.clearSelection(player);
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
            case MAX_CLAIMS -> "command.create-max-claims";
            case NOT_ENOUGH_MONEY -> "command.create-not-enough-money";
            default -> "command.create-unavailable";
        };
        return Lang.getMessage(key).copy()
                .add("price", formatPrice(result.price()))
                .add("size", result.size())
                .add("max", Config.getInt("claim.max-claims-per-player"))
                .add("conflict", result.conflict());
    }

    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return Long.toString((long) price);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", price);
    }
}
