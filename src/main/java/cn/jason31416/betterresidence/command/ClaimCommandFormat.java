package cn.jason31416.betterresidence.command;

import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.betterresidence.claim.ClaimGroup;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.planetlib.util.Lang;

import java.util.Comparator;
import java.util.List;

final class ClaimCommandFormat {
    private static final String EMPTY_FALLBACK = "";

    private ClaimCommandFormat() {
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("<", "\\<")
                .replace(">", "\\>");
    }

    static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

    static String areaSummary(List<ClaimManager.ClaimAreaInfo> areas) {
        if (areas.isEmpty()) {
            return raw("command.format.no-areas");
        }
        ClaimManager.ClaimAreaInfo firstArea = areas.getFirst();
        return rawMessage("command.format.area-summary")
                .add("count", areas.size())
                .add("area", formatAreaBox(firstArea.box()))
                .toString();
    }

    static String groupHover(Claim claim) {
        List<String> groups = claim.getClaimGroups().stream()
                .sorted(Comparator.comparingInt(ClaimGroup::weight).reversed())
                .map(group -> rawMessage("command.format.group-entry")
                        .add("group", group.name())
                        .add("weight", group.weight())
                        .toString())
                .toList();
        return joinLimited(groups, Config.getInt("claim.info-group-hover-limit"));
    }

    static String areaHover(List<ClaimManager.ClaimAreaInfo> areas) {
        if (areas.isEmpty()) {
            return raw("command.format.no-areas");
        }
        return joinLimited(areas.stream()
                .map(area -> rawMessage("command.format.area-entry")
                        .add("area-id", area.areaId())
                        .add("area", formatAreaBox(area.box()))
                        .toString())
                .toList(), Config.getInt("claim.info-area-hover-limit"));
    }

    static String memberHover(Claim claim, List<ClaimManager.ClaimMemberInfo> members) {
        if (members.isEmpty()) {
            return raw("command.format.no-members");
        }
        return joinLimited(members.stream()
                .map(member -> rawMessage("command.format.member-entry")
                        .add("player", member.player().getName())
                        .add("group", claim.getClaimGroupById(member.groupId())
                                .map(ClaimGroup::name)
                                .orElse(member.groupId()))
                        .toString())
                .toList(), Config.getInt("claim.info-member-hover-limit"));
    }

    static String subClaimHover(Claim claim) {
        List<String> subClaims = claim.getSubClaims().stream()
                .map(ClaimManager::fetchClaim)
                .filter(subClaim -> subClaim != null)
                .map(subClaim -> rawMessage("command.format.subclaim-entry")
                        .add("claim", subClaim.getName())
                        .add("short-uuid", shortUuid(subClaim.getUuid()))
                        .toString())
                .toList();
        return subClaims.isEmpty() ? raw("command.format.no-subclaims") : joinLimited(subClaims, Config.getInt("claim.info-subclaim-hover-limit"));
    }

    static String flagHover(List<ClaimManager.ClaimFlagInfo> flags) {
        if (flags.isEmpty()) {
            return raw("command.format.no-flags");
        }
        return joinLimited(flags.stream()
                .map(flag -> rawMessage("command.format.flag-entry")
                        .add("flag", escape(flag.flag()))
                        .add("value", escape(flag.value()))
                        .toString())
                .toList(), Config.getInt("claim.info-flag-hover-limit"));
    }

    static String pageButton(String labelKey, String command, boolean enabled) {
        String label = raw(labelKey);
        if (!enabled) {
            return rawMessage("command.format.page-disabled")
                    .add("label", label)
                    .toFormatted();
        }
        return rawMessage("command.format.page-enabled")
                .add("label", label)
                .add("command", command)
                .toFormatted();
    }

    static String raw(String key) {
        return Lang.messageLoader.getRawMessage(key, EMPTY_FALLBACK);
    }

    static Message rawMessage(String key) {
        return Message.of(raw(key));
    }

    private static String formatAreaBox(cn.jason31416.betterresidence.claim.AreaBox box) {
        return rawMessage("command.format.area-box")
                .add("min-x", box.minX())
                .add("min-y", box.minY())
                .add("min-z", box.minZ())
                .add("max-x", box.maxX())
                .add("max-y", box.maxY())
                .add("max-z", box.maxZ())
                .toString();
    }

    private static String joinLimited(List<String> lines, int limit) {
        if (lines.isEmpty()) {
            return "";
        }
        List<String> visibleLines = lines.size() > limit ? lines.subList(0, limit) : lines;
        String result = String.join("<newline>", visibleLines.stream().map(ClaimCommandFormat::escape).toList());
        if (lines.size() > limit) {
            result += "<newline>" + rawMessage("command.format.more-items")
                    .add("count", lines.size() - limit)
                    .toString();
        }
        return result;
    }
}
