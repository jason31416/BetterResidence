package cn.jason31416.betterresidence.command;

import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.message.MessageList;
import cn.jason31416.planetlib.util.Lang;

import java.util.ArrayList;
import java.util.List;

public class HelpCommand extends ChildCommand {
    private static final int PAGE_SIZE = 8;
    private final List<String> COMMAND_KEYS;

    public HelpCommand(IParentCommand parent) {
        super("help", parent);
        COMMAND_KEYS = new ArrayList<>(parent.getSubCommands().keySet());
        COMMAND_KEYS.remove("");
        COMMAND_KEYS.remove(null);
    }

    @Override
    public Message execute(ICommandContext context) {
        int page = context.args().isEmpty() ? 1 : parsePage(context.getArg(0));
        int pageCount = Math.max(1, (int) Math.ceil((double) COMMAND_KEYS.size() / PAGE_SIZE));
        page = Math.min(Math.max(page, 1), pageCount);

        MessageList message = Lang.getMessageList("command.help-message")
                .copy()
                .add("page", page)
                .add("page-count", pageCount);

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, COMMAND_KEYS.size());
        for (String commandKey : COMMAND_KEYS.subList(from, to)) {
            message.add(ClaimCommandFormat.rawMessage("command.help-entry")
                    .add("usage", ClaimCommandFormat.raw("command.help.commands." + commandKey + ".usage"))
                    .add("description", ClaimCommandFormat.raw("command.help.commands." + commandKey + ".description"))
                    .toFormatted());
        }

        Lang.getMessageList("command.help-footer")
                .copy()
                .add("page", page)
                .add("page-count", pageCount)
                .add("prev", ClaimCommandFormat.pageButton("command.format.previous-page", "/res help " + (page - 1), page > 1))
                .add("next", ClaimCommandFormat.pageButton("command.format.next-page", "/res help " + (page + 1), page < pageCount))
                .getContent()
                .forEach(message::add);
        return message;
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().size() == 1) {
            int pageCount = Math.max(1, (int) Math.ceil((double) COMMAND_KEYS.size() / PAGE_SIZE));
            return java.util.stream.IntStream.rangeClosed(1, pageCount)
                    .mapToObj(Integer::toString)
                    .filter(page -> page.startsWith(context.getArg(0)))
                    .toList();
        }
        return List.of();
    }

    private int parsePage(String input) {
        try {
            return Math.max(1, Integer.parseInt(input));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
