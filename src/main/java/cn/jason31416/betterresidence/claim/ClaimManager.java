package cn.jason31416.betterresidence.claim;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.planetlib.data.Param;
import cn.jason31416.planetlib.util.MapTree;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ClaimManager {
    private static final Cache<String, Claim> claimCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    /**
     * Fetch a claim by UUID, from cache or database. The returned object SHOULD NOT be stored persistently, as they are only the cached objects.
     * Store the uuid instead, and fetch it from this function every time you need to use it.
     * @param uuid The claim UUID
     * @return The claim, or null if not found
     */
    @Nullable
    @SneakyThrows
    public static Claim fetchClaim(String uuid) {
        Claim cachedClaim = claimCache.getIfPresent(uuid);
        if (cachedClaim != null) {
            return cachedClaim;
        }

        Optional<MapTree> row = DataHandler.getDatabase().select("claim")
                .keyEquals("uuid", uuid)
                .one();
        if (row.isEmpty()) {
            return null;
        }

        MapTree data = row.get();
        SimplePlayer owner = SimplePlayer.of(UUID.fromString(data.getString("owner_uuid")));
        Claim claim = new Claim(owner, data.getString("name"), uuid, data.getString("parent_uuid", null));
        claimCache.put(uuid, claim);
        return claim;
    }

    @Nullable
    public static Claim fetchClaimByName(String name) {
        Optional<MapTree> row = DataHandler.getDatabase().select("claim")
                .keyEquals("name", name)
                .one();
        return row.map(data -> fetchClaim(data.getString("uuid"))).orElse(null);
    }

    public static List<Claim> fetchClaimsByOwner(UUID ownerUuid) {
        return DataHandler.getDatabase().select("claim")
                .keyEquals("owner_uuid", ownerUuid.toString())
                .list()
                .stream()
                .map(row -> fetchClaim(row.getString("uuid")))
                .filter(claim -> claim != null)
                .sorted(java.util.Comparator.comparing(Claim::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static List<Claim> fetchTopLevelClaimsByOwner(UUID ownerUuid) {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT uuid
                FROM claim
                WHERE owner_uuid = ?
                  AND parent_uuid IS NULL
                ORDER BY name COLLATE NOCASE ASC
                """,
                List.of(Param.of(ownerUuid.toString())),
                rs -> fetchClaim(rs.getString("uuid"))
        ).stream()
                .filter(claim -> claim != null)
                .toList();
    }

    public static boolean claimNameExists(String name) {
        return DataHandler.getDatabase().select("claim")
                .keyEquals("name", name)
                .one()
                .isPresent();
    }

    @Nullable
    public static Claim resolveClaim(String input) {
        Claim claim = fetchClaimByName(input);
        if (claim != null) {
            return claim;
        }
        return fetchClaim(input); // by uuid
    }

    public static void invalidateClaim(String uuid) {
        claimCache.invalidate(uuid);
    }

    public static void invalidateClaims(Collection<String> uuids) {
        claimCache.invalidateAll(uuids);
    }

    public static List<OverlappingClaimAreaInfo> fetchOverlappingClaimAreas(String worldUuid, AreaBox areaBox) {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT claim_areas.area_id,
                       claim_areas.world,
                       area.minX,
                       area.maxX,
                       area.minY,
                       area.maxY,
                       area.minZ,
                       area.maxZ,
                       claim.name AS claim_name,
                       claim.uuid AS claim_uuid
                FROM claim_areas
                JOIN area ON area.id = claim_areas.area_id
                JOIN claim ON claim.uuid = claim_areas.claim_uuid
                WHERE area.minX <= ?
                  AND area.maxX >= ?
                  AND area.minY <= ?
                  AND area.maxY >= ?
                  AND area.minZ <= ?
                  AND area.maxZ >= ?
                  AND claim_areas.world = ?
                ORDER BY claim_areas.area_id ASC
                """,
                List.of(
                        Param.of(areaBox.maxX()),
                        Param.of(areaBox.minX()),
                        Param.of(areaBox.maxY()),
                        Param.of(areaBox.minY()),
                        Param.of(areaBox.maxZ()),
                        Param.of(areaBox.minZ()),
                        Param.of(worldUuid)
                ),
                ClaimManager::mapOverlappingClaimAreaInfo
        );
    }

    public static List<OverlappingClaimAreaInfo> fetchClaimAreasNear(String worldUuid, AreaBox areaBox, int limit) {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT claim_areas.area_id,
                       claim_areas.world,
                       area.minX,
                       area.maxX,
                       area.minY,
                       area.maxY,
                       area.minZ,
                       area.maxZ,
                       claim.name AS claim_name,
                       claim.uuid AS claim_uuid
                FROM claim_areas
                JOIN area ON area.id = claim_areas.area_id
                JOIN claim ON claim.uuid = claim_areas.claim_uuid
                WHERE area.minX <= ?
                  AND area.maxX >= ?
                  AND area.minY <= ?
                  AND area.maxY >= ?
                  AND area.minZ <= ?
                  AND area.maxZ >= ?
                  AND claim_areas.world = ?
                ORDER BY claim_areas.area_id ASC
                LIMIT ?
                """,
                List.of(
                        Param.of(areaBox.maxX()),
                        Param.of(areaBox.minX()),
                        Param.of(areaBox.maxY()),
                        Param.of(areaBox.minY()),
                        Param.of(areaBox.maxZ()),
                        Param.of(areaBox.minZ()),
                        Param.of(worldUuid),
                        Param.of(Math.max(1, limit))
                ),
                ClaimManager::mapOverlappingClaimAreaInfo
        );
    }

    public static List<ClaimAreaInfo> fetchClaimAreas(String claimUuid) {
        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT claim_areas.area_id,
                       claim_areas.world,
                       area.minX,
                       area.maxX,
                       area.minY,
                       area.maxY,
                       area.minZ,
                       area.maxZ
                FROM claim_areas
                JOIN area ON area.id = claim_areas.area_id
                WHERE claim_areas.claim_uuid = ?
                ORDER BY claim_areas.area_id ASC
                """,
                List.of(Param.of(claimUuid)),
                ClaimManager::mapClaimAreaInfo
        );
    }

    public static Optional<ClaimAreaInfo> findClaimAreaAt(String claimUuid, String worldUuid, int x, int y, int z) {
        return fetchClaimAreas(claimUuid).stream()
                .filter(area -> area.worldUuid().equals(worldUuid))
                .filter(area -> area.box().containsBlock(x, y, z))
                .findFirst();
    }

    public static List<String> fetchDescendantClaimUuids(String claimUuid) {
        List<String> descendants = new ArrayList<>();
        Queue<String> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(claimUuid);
        seen.add(claimUuid);

        while (!pending.isEmpty()) {
            String parentUuid = pending.remove();
            List<MapTree> rows = DataHandler.getDatabase().select("claim")
                    .keyEquals("parent_uuid", parentUuid)
                    .list();
            for (MapTree row : rows) {
                String childUuid = row.getString("uuid");
                if (!seen.add(childUuid)) {
                    continue;
                }
                descendants.add(childUuid);
                pending.add(childUuid);
            }
        }

        return descendants;
    }

    public static List<String> fetchAncestorClaimUuids(String claimUuid) {
        List<String> ancestors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Claim current = fetchClaim(claimUuid);

        while (current != null && current.getParentUuid() != null && seen.add(current.getUuid())) {
            ancestors.add(current.getParentUuid());
            current = fetchClaim(current.getParentUuid());
        }

        return ancestors;
    }

    public static Set<String> fetchAncestorOrSelfClaimUuids(String claimUuid) {
        Set<String> ancestors = new HashSet<>();
        ancestors.add(claimUuid);
        ancestors.addAll(fetchAncestorClaimUuids(claimUuid));
        return ancestors;
    }

    public static boolean hasDescendantAreaOverlap(String claimUuid, String worldUuid, AreaBox areaBox) {
        Set<String> descendantUuids = new HashSet<>(fetchDescendantClaimUuids(claimUuid));
        if (descendantUuids.isEmpty()) {
            return false;
        }
        return fetchOverlappingClaimAreas(worldUuid, areaBox).stream()
                .anyMatch(area -> descendantUuids.contains(area.claimUuid()));
    }

    public static boolean isAreaCoveredByClaim(String claimUuid, String worldUuid, AreaBox areaBox) {
        List<AreaBox> remaining = new ArrayList<>(List.of(areaBox));
        List<ClaimAreaInfo> coveringAreas = fetchClaimAreas(claimUuid).stream()
                .filter(area -> area.worldUuid().equals(worldUuid))
                .filter(area -> area.box().overlaps(areaBox))
                .toList();

        for (ClaimAreaInfo coveringArea : coveringAreas) {
            List<AreaBox> nextRemaining = new ArrayList<>();
            for (AreaBox remainingBox : remaining) {
                // Each parent area removes the volume it covers from the still-uncovered pieces.
                // Because AreaBox.subtract returns non-overlapping leftovers, repeatedly applying
                // it computes coverage by the union of all parent areas without needing a voxel loop.
                nextRemaining.addAll(remainingBox.subtract(coveringArea.box()));
            }
            remaining = nextRemaining;
            if (remaining.isEmpty()) {
                return true;
            }
        }

        return remaining.isEmpty();
    }

    public static void copyClaimFlags(String fromClaimUuid, String toClaimUuid) {
        DataHandler.getDatabase().select("claim_flags")
                .keyEquals("claim_uuid", fromClaimUuid)
                .list()
                .forEach(row -> DataHandler.getDatabase().insert("claim_flags")
                        .value("flag", row.getString("flag"))
                        .value("value", row.getString("value"))
                        .value("claim_uuid", toClaimUuid)
                        .executeUpdate());
    }

    public static List<ClaimMemberInfo> fetchClaimMembers(String claimUuid) {
        return DataHandler.getDatabase().select("player_groups")
                .keyEquals("claim_uuid", claimUuid)
                .list()
                .stream()
                .map(row -> new ClaimMemberInfo(
                        SimplePlayer.of(UUID.fromString(row.getString("player_uuid"))),
                        row.getString("group_id")
                ))
                .sorted(java.util.Comparator.comparing(member -> member.player().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @SneakyThrows
    private static ClaimAreaInfo mapClaimAreaInfo(ResultSet rs) {
        return new ClaimAreaInfo(
                rs.getInt("area_id"),
                rs.getString("world"),
                new AreaBox(
                        rs.getInt("minX"),
                        rs.getInt("maxX"),
                        rs.getInt("minY"),
                        rs.getInt("maxY"),
                        rs.getInt("minZ"),
                        rs.getInt("maxZ")
                )
        );
    }

    @SneakyThrows
    private static OverlappingClaimAreaInfo mapOverlappingClaimAreaInfo(ResultSet rs) {
        return new OverlappingClaimAreaInfo(
                rs.getInt("area_id"),
                rs.getString("world"),
                new AreaBox(
                        rs.getInt("minX"),
                        rs.getInt("maxX"),
                        rs.getInt("minY"),
                        rs.getInt("maxY"),
                        rs.getInt("minZ"),
                        rs.getInt("maxZ")
                ),
                rs.getString("claim_uuid"),
                rs.getString("claim_name")
        );
    }


    /**
     * Find the deepest claim containing a location. The initial area lookup uses SQLite R-tree bounds.
     * @param location The location to query.
     * @return The deepest matching claim, or null if no claim contains the location.
     */
    @Nullable
    public static Claim findClaimAt(SimpleLocation location) {
        return ClaimAreaLookup.findClaimAt(location);
    }

    /**
     * Create a claim with its first block-aligned area.
     * @param owner The claim owner.
     * @param name The claim name.
     * @param parentUuid The parent claim UUID, or null for a top-level claim.
     * @param world The world the first area belongs to.
     * @param areaBox Inclusive integer block bounds for the first area.
     * @return The created claim.
     */
    @SneakyThrows
    public static Claim createClaim(SimplePlayer owner, String name, @Nullable String parentUuid, SimpleWorld world, AreaBox areaBox) {
        return createClaim(owner, name, parentUuid, world.getBukkitWorld().getUID().toString(), areaBox);
    }

    /**
     * Create a claim with its first block-aligned area.
     * @param owner The claim owner.
     * @param name The claim name.
     * @param parentUuid The parent claim UUID, or null for a top-level claim.
     * @param worldUuid The world UUID the first area belongs to.
     * @param areaBox Inclusive integer block bounds for the first area.
     * @return The created claim.
     */
    @SneakyThrows
    public static Claim createClaim(SimplePlayer owner, String name, @Nullable String parentUuid, String worldUuid, AreaBox areaBox) {
        synchronized (Claim.areaCreateLock) {
            String claimUuid = UUID.randomUUID().toString();
            int areaId = Claim.allocateNextAreaId();

            DataHandler.getDatabase().executeBatch(List.of(
                    DataHandler.getDatabase().insert("claim")
                            .value("uuid", claimUuid)
                            .value("name", name)
                            .value("owner_uuid", owner.getUUID().toString())
                            .value("parent_uuid", parentUuid),
                    DataHandler.getDatabase().insert("area")
                            .value("id", areaId)
                            .value("minX", areaBox.minX())
                            .value("maxX", areaBox.maxX())
                            .value("minY", areaBox.minY())
                            .value("maxY", areaBox.maxY())
                            .value("minZ", areaBox.minZ())
                            .value("maxZ", areaBox.maxZ()),
                    DataHandler.getDatabase().insert("claim_areas")
                            .value("area_id", areaId)
                            .value("claim_uuid", claimUuid)
                            .value("world", worldUuid)
            ));

            Claim claim = new Claim(owner, name, claimUuid, parentUuid);
            claimCache.put(claimUuid, claim);
            if (parentUuid != null) {
                Claim parent = claimCache.getIfPresent(parentUuid);
                if (parent != null) {
                    parent.subClaims = null;
                }
            }
            ClaimAreaLookup.clearCache();
            return claim;
        }
    }

    public record ClaimAreaInfo(int areaId, String worldUuid, AreaBox box) {
    }

    public record OverlappingClaimAreaInfo(int areaId, String worldUuid, AreaBox box, String claimUuid, String claimName) {
    }

    public record ClaimMemberInfo(SimplePlayer player, String groupId) {
    }

}
