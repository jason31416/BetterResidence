package cn.jason31416.betterresidence.claim;

/**
 * A real assignable claim permission group.
 * <p>
 * Visitor and everyone are not represented by this record: visitor means no player group record,
 * and everyone is only a special /res set threshold alias.
 *
 * @param id Stable internal id stored in player_groups.
 * @param name Display name used by commands.
 * @param weight Permission weight used by threshold checks.
 */
public record ClaimGroup(String id, String name, int weight) {
}
