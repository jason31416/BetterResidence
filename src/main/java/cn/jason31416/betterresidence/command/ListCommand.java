package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.planetlib.command.ChildCommand;
import cn.jason31416.planetlib.command.ICommandContext;
import cn.jason31416.planetlib.command.IParentCommand;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.message.MessageList;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ListCommand extends ChildCommand {
    private static final int PAGE_SIZE = 8;

    public ListCommand(IParentCommand parent) {
        super("list", parent);
    }

    @Override
    public Message execute(ICommandContext context) {
        ParseResult parseResult = parseFilters(context);
        if (parseResult.error() != null) {
            return parseResult.error();
        }

        ListFilters filters = parseResult.filters();
        ClaimManager.ClaimListPage claimPage = ClaimManager.fetchClaimsPage(filters.toQuery());
        int pageCount = Math.max(1, (int) Math.ceil((double) claimPage.totalCount() / PAGE_SIZE));

        MessageList message = Lang.getMessageList("command.list-message")
                .copy()
                .add("filters", ClaimCommandFormat.escape(filters.display()))
                .add("count", claimPage.totalCount())
                .add("page", claimPage.page())
                .add("page-count", pageCount);

        if (claimPage.claims().isEmpty()) {
            message.add(Lang.getMessage("command.list-empty").toFormatted());
        } else {
            for (Claim claim : claimPage.claims()) {
                message.add(createListEntry(claim));
            }
        }

        Lang.getMessageList("command.list-footer")
                .copy()
                .add("count", claimPage.totalCount())
                .add("page", claimPage.page())
                .add("page-count", pageCount)
                .add("prev", ClaimCommandFormat.pageButton("command.format.previous-page", filters.commandForPage(claimPage.page() - 1), claimPage.page() > 1))
                .add("next", ClaimCommandFormat.pageButton("command.format.next-page", filters.commandForPage(claimPage.page() + 1), claimPage.page() < pageCount))
                .getContent()
                .forEach(message::add);
        return message;
    }

    @Override
    public List<String> tabComplete(ICommandContext context) {
        if (context.args().isEmpty()) {
            return List.of();
        }
        String prefix = context.getArg(context.args().size() - 1).toLowerCase(Locale.ROOT);
        return List.of("user:", "radius:", "world:", "name:", "parent:", "parent:null", "page:").stream()
                .filter(filter -> filter.startsWith(prefix))
                .toList();
    }

    private ParseResult parseFilters(ICommandContext context) {
        ListFilters filters = new ListFilters();
        for (String arg : context.args()) {
            if (isInteger(arg)) {
                filters.page = parsePositiveInt(arg);
                if (filters.page == null) {
                    return ParseResult.error(Lang.getMessage("command.list-invalid-page"));
                }
                continue;
            }

            int separator = arg.indexOf(':');
            if (separator < 0) {
                filters.owner = SimplePlayer.of(arg);
                filters.replaceFilterPart("user:", "user:" + arg);
                continue;
            }

            String key = arg.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = arg.substring(separator + 1);
            if (value.isBlank()) {
                return ParseResult.error(Lang.getMessage("command.list-invalid-filter").copy()
                        .add("filter", ClaimCommandFormat.escape(arg)));
            }

            switch (key) {
                case "page" -> {
                    filters.page = parsePositiveInt(value);
                    if (filters.page == null) {
                        return ParseResult.error(Lang.getMessage("command.list-invalid-page"));
                    }
                }
                case "user" -> {
                    filters.owner = SimplePlayer.of(value);
                    filters.replaceFilterPart("user:", "user:" + value);
                }
                case "radius" -> {
                    if (context.player() == null) {
                        return ParseResult.error(Lang.getMessage("command.list-radius-player-only"));
                    }
                    Integer radius = parsePositiveInt(value);
                    if (radius == null) {
                        return ParseResult.error(Lang.getMessage("command.list-invalid-radius"));
                    }
                    SimpleLocation blockLocation = context.player().getLocation().getBlockLocation();
                    String worldUuid = context.player().getLocation().getWorld().getBukkitWorld().getUID().toString();
                    filters.areaSearch = new ClaimManager.AreaSearch(worldUuid, new AreaBox(
                            (int) blockLocation.x() - radius,
                            (int) blockLocation.x() + radius,
                            (int) blockLocation.y() - radius,
                            (int) blockLocation.y() + radius,
                            (int) blockLocation.z() - radius,
                            (int) blockLocation.z() + radius
                    ));
                    filters.replaceFilterPart("radius:", "radius:" + radius);
                }
                case "world" -> {
                    String worldUuid = resolveWorldUuid(value);
                    if (worldUuid == null) {
                        return ParseResult.error(Lang.getMessage("command.list-invalid-world").copy()
                                .add("world", ClaimCommandFormat.escape(value)));
                    }
                    filters.worldUuid = worldUuid;
                    filters.replaceFilterPart("world:", "world:" + value);
                }
                case "name" -> {
                    filters.namePrefix = value;
                    filters.replaceFilterPart("name:", "name:" + value);
                }
                case "parent" -> {
                    if (value.equalsIgnoreCase("null")) {
                        filters.parentNull = true;
                        filters.parentUuid = null;
                        filters.replaceFilterPart("parent:", "parent:null");
                    } else {
                        Claim parent = ClaimManager.resolveClaim(value);
                        filters.parentNull = false;
                        filters.parentUuid = parent == null ? ListFilters.NO_MATCH_PARENT_UUID : parent.getUuid();
                        filters.replaceFilterPart("parent:", "parent:" + value);
                    }
                }
                default -> {
                    return ParseResult.error(Lang.getMessage("command.list-invalid-filter").copy()
                            .add("filter", ClaimCommandFormat.escape(key)));
                }
            }
        }
        if (filters.page == null) {
            filters.page = 1;
        }
        return ParseResult.success(filters);
    }

    @Nullable
    private String resolveWorldUuid(String input) {
        try {
            UUID uuid = UUID.fromString(input);
            return uuid.toString();
        } catch (IllegalArgumentException ignored) {
        }
        World world = Bukkit.getWorld(input);
        return world == null ? null : world.getUID().toString();
    }

    @Nullable
    private Integer parsePositiveInt(String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
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
                .add("owner", ClaimCommandFormat.escape(claim.getOwner().getName()))
                .add("uuid", claim.getUuid())
                .add("short-uuid", ClaimCommandFormat.shortUuid(claim.getUuid()))
                .add("area-summary", ClaimCommandFormat.escape(ClaimCommandFormat.areaSummary(areas)))
                .add("hover", hover)
                .toFormatted();
    }

    private boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private record ParseResult(@Nullable ListFilters filters, @Nullable Message error) {
        private static ParseResult success(ListFilters filters) {
            return new ParseResult(filters, null);
        }

        private static ParseResult error(Message error) {
            return new ParseResult(null, error);
        }
    }

    private static final class ListFilters {
        private static final String NO_MATCH_PARENT_UUID = "00000000-0000-0000-0000-000000000000";

        private final List<String> filterParts = new ArrayList<>();
        private SimplePlayer owner;
        private String worldUuid;
        private String namePrefix;
        private String parentUuid;
        private boolean parentNull;
        private ClaimManager.AreaSearch areaSearch;
        private Integer page;

        private ClaimManager.ClaimListQuery toQuery() {
            return new ClaimManager.ClaimListQuery(
                    owner == null ? null : owner.getUUID(),
                    worldUuid,
                    namePrefix,
                    parentUuid,
                    parentNull,
                    areaSearch,
                    page,
                    PAGE_SIZE
            );
        }

        private String display() {
            return filterParts.isEmpty() ? ClaimCommandFormat.raw("command.format.all") : String.join(" ", filterParts);
        }

        private String commandForPage(int page) {
            List<String> parts = new ArrayList<>(filterParts);
            parts.add("page:" + page);
            return "/res list " + String.join(" ", parts);
        }

        private void replaceFilterPart(String prefix, String value) {
            filterParts.removeIf(part -> part.startsWith(prefix));
            filterParts.add(value);
        }
    }
}
