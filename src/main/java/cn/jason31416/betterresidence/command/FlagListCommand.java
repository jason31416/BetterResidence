package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.message.MessageList;
import cn.jason31416.planetlib.util.Lang;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class FlagListCommand extends ChildCommand {
    public FlagListCommand(IParentCommand parent) {
        super("flaglist", parent);
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

        Map<String, String> flags = claim.getStoredFlags();
        MessageList message = Lang.getMessageList("command.flaglist-message")
                .copy()
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("count", flags.size());
        if (flags.isEmpty()) {
            message.add(Lang.getMessage("command.flaglist-empty").toFormatted());
            return message;
        }

        flags.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> Lang.getMessage("command.flaglist-entry")
                        .add("flag", ClaimCommandFormat.escape(entry.getKey()))
                        .add("value", ClaimCommandFormat.escape(formatValue(entry.getValue())))
                        .toFormatted())
                .forEach(message::add);
        return message;
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }

    private String formatValue(String value) {
        return value.isEmpty() ? ClaimCommandFormat.raw("command.format.empty-value") : value;
    }
}
