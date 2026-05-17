package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.message.MessageList;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimplePlayer;

import java.util.List;

public class ListCommand extends ChildCommand {
    private static final int PAGE_SIZE = 8;

    public ListCommand(IParentCommand parent) {
        super("list", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        SimplePlayer owner;
        int page;
        if (context.args().isEmpty()) {
            if (context.player() == null) {
                return Lang.getMessage("command.list-player-only");
            }
            owner = context.player();
            page = 1;
        } else if (isInteger(context.getArg(0))) {
            if (context.player() == null) {
                return Lang.getMessage("command.list-player-only");
            }
            owner = context.player();
            page = parsePage(context.getArg(0));
        } else {
            owner = SimplePlayer.of(context.getArg(0));
            page = context.args().size() >= 2 ? parsePage(context.getArg(1)) : 1;
        }

        List<Claim> claims = ClaimManager.fetchClaimsByOwner(owner.getUUID());
        if (claims.isEmpty()) {
            return Lang.getMessage("command.no-claims").copy()
                    .add("player", ClaimCommandFormat.escape(owner.getName()));
        }

        int pageSize = PAGE_SIZE;
        int pageCount = Math.max(1, (int) Math.ceil((double) claims.size() / pageSize));
        page = Math.min(Math.max(page, 1), pageCount);
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, claims.size());

        MessageList message = Lang.getMessageList("command.list-message")
                .add("player", ClaimCommandFormat.escape(owner.getName()))
                .add("count", claims.size())
                .add("page", page)
                .add("page-count", pageCount);

        for (Claim claim : claims.subList(from, to)) {
            message.add(createListEntry(claim));
        }
        Lang.getMessageList("command.list-footer")
                .add("player", ClaimCommandFormat.escape(owner.getName()))
                .add("count", claims.size())
                .add("page", page)
                .add("page-count", pageCount)
                .add("prev", ClaimCommandFormat.pageButton("command.format.previous-page", "/res list " + owner.getName() + " " + (page - 1), page > 1))
                .add("next", ClaimCommandFormat.pageButton("command.format.next-page", "/res list " + owner.getName() + " " + (page + 1), page < pageCount))
                .getContent()
                .forEach(message::add);
        return message;
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        return List.of();
    }

    private String createListEntry(Claim claim) {
        List<ClaimManager.ClaimAreaInfo> areas = ClaimManager.fetchClaimAreas(claim.getUuid());
        String hover = ClaimCommandFormat.rawMessage("command.format.list-entry-hover")
                .add("owner", ClaimCommandFormat.escape(claim.getOwner().getName()))
                .add("uuid", claim.getUuid())
                .add("area-summary", ClaimCommandFormat.escape(ClaimCommandFormat.areaSummary(areas)))
                .add("subclaim-count", claim.getSubClaims().size())
                .toFormatted();
        return Lang.getMessage("command.list-entry")
                .add("claim", ClaimCommandFormat.escape(claim.getName()))
                .add("uuid", claim.getUuid())
                .add("short-uuid", ClaimCommandFormat.shortUuid(claim.getUuid()))
                .add("area-summary", ClaimCommandFormat.escape(ClaimCommandFormat.areaSummary(areas)))
                .add("hover", hover)
                .toFormatted();
    }

    private int parsePage(String input) {
        try {
            return Math.max(1, Integer.parseInt(input));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
