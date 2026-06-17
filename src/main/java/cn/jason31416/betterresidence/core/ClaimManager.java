package cn.jason31416.betterresidence.core;

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

    public static ClaimListPage fetchClaimsPage(ClaimListQuery query) {
        QueryParts queryParts = buildClaimListQuery(query, false);
        int totalCount = DataHandler.getDatabase().getSqlInstance().executeQueryOne(
                queryParts.sql(),
                queryParts.params(),
                rs -> rs.getInt("claim_count")
        ).orElse(0);

        if (totalCount == 0) {
            return new ClaimListPage(List.of(), 0, 1);
        }

        int page = Math.min(Math.max(query.page(), 1), Math.max(1, (int) Math.ceil((double) totalCount / query.pageSize())));
        int offset = (page - 1) * query.pageSize();

        queryParts = buildClaimListQuery(query.withPage(page), true);
        List<Param> params = new ArrayList<>(queryParts.params());
        params.add(Param.of(query.pageSize()));
        params.add(Param.of(offset));

        List<String> uuids = DataHandler.getDatabase().getSqlInstance().executeQuery(
                queryParts.sql(),
                params,
                rs -> rs.getString("uuid")
        );
        List<Claim> claims = uuids.stream()
                .map(ClaimManager::fetchClaim)
                .filter(claim -> claim != null)
                .toList();

        return new ClaimListPage(claims, totalCount, page);
    }

    private static QueryParts buildClaimListQuery(ClaimListQuery query, boolean pageQuery) {
        List<Param> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        boolean areaJoinRequired = query.worldUuid() != null || query.areaSearch() != null;
        if (pageQuery) {
            sql.append(areaJoinRequired ? "SELECT DISTINCT claim.uuid, claim.name FROM claim" : "SELECT claim.uuid, claim.name FROM claim");
        } else {
            sql.append(areaJoinRequired ? "SELECT COUNT(DISTINCT claim.uuid) AS claim_count FROM claim" : "SELECT COUNT(*) AS claim_count FROM claim");
        }

        if (areaJoinRequired) {
            sql.append(" JOIN claim_areas ON claim_areas.claim_uuid = claim.uuid");
        }
        if (query.areaSearch() != null) {
            sql.append(" JOIN area ON area.id = claim_areas.area_id");
        }

        List<String> conditions = new ArrayList<>();
        if (query.ownerUuid() != null) {
            conditions.add("claim.owner_uuid = ?");
            params.add(Param.of(query.ownerUuid().toString()));
        }
        if (query.namePrefix() != null && !query.namePrefix().isBlank()) {
            conditions.add("claim.name COLLATE NOCASE LIKE ? ESCAPE '\\'");
            params.add(Param.of(escapeLike(query.namePrefix()) + "%"));
        }
        if (query.parentNull()) {
            conditions.add("claim.parent_uuid IS NULL");
        } else if (query.parentUuid() != null) {
            conditions.add("claim.parent_uuid = ?");
            params.add(Param.of(query.parentUuid()));
        }
        if (query.worldUuid() != null) {
            conditions.add("claim_areas.world = ?");
            params.add(Param.of(query.worldUuid()));
        }
        if (query.areaSearch() != null) {
            AreaSearch areaSearch = query.areaSearch();
            AreaBox box = areaSearch.box();
            conditions.add("claim_areas.world = ?");
            params.add(Param.of(areaSearch.worldUuid()));
            conditions.add("area.minX <= ?");
            params.add(Param.of(box.maxX()));
            conditions.add("area.maxX >= ?");
            params.add(Param.of(box.minX()));
            conditions.add("area.minY <= ?");
            params.add(Param.of(box.maxY()));
            conditions.add("area.maxY >= ?");
            params.add(Param.of(box.minY()));
            conditions.add("area.minZ <= ?");
            params.add(Param.of(box.maxZ()));
            conditions.add("area.maxZ >= ?");
            params.add(Param.of(box.minZ()));
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (pageQuery) {
            sql.append(" ORDER BY claim.name COLLATE NOCASE ASC, claim.uuid ASC LIMIT ? OFFSET ?");
        }
        return new QueryParts(sql.toString(), params);
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public static boolean claimNameExists(String name) {
        return DataHandler.getDatabase().select("claim")
                .keyEquals("name", name)
                .one()
                .isPresent();
    }

    public static void renameClaim(String claimUuid, String newName) {
        DataHandler.getDatabase().update("claim")
                .value("name", newName)
                .keyEquals("uuid", claimUuid)
                .executeUpdate();
        invalidateClaim(claimUuid);
        ClaimAreaLookup.clearCache();
    }

    public static void setClaimOwner(String claimUuid, SimplePlayer newOwner) {
        DataHandler.getDatabase().update("claim")
                .value("owner_uuid", newOwner.getUUID().toString())
                .keyEquals("uuid", claimUuid)
                .executeUpdate();
        invalidateClaim(claimUuid);
        ClaimAreaLookup.clearCache();
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

    public static void clearCache() {
        claimCache.invalidateAll();
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
        List<cn.jason31416.planetlib.data.statement.SQLStatement> statements = new ArrayList<>();
        DataHandler.getDatabase().select("claim_flags")
                .keyEquals("claim_uuid", fromClaimUuid)
                .list()
                .forEach(row -> statements.add(DataHandler.getDatabase().insert("claim_flags")
                        .value("flag", row.getString("flag"))
                        .value("value", row.getString("value"))
                        .value("claim_uuid", toClaimUuid)));
        if (!statements.isEmpty()) {
            DataHandler.getDatabase().executeBatch(statements);
        }
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
                .filter(member -> member.player().getName()!=null)
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

    private record QueryParts(String sql, List<Param> params) {
    }

    public record AreaSearch(String worldUuid, AreaBox box) {
    }

    public record ClaimListQuery(@Nullable UUID ownerUuid, @Nullable String worldUuid, @Nullable String namePrefix,
                                 @Nullable String parentUuid, boolean parentNull, @Nullable AreaSearch areaSearch,
                                 int page, int pageSize) {
        public ClaimListQuery withPage(int page) {
            return new ClaimListQuery(ownerUuid, worldUuid, namePrefix, parentUuid, parentNull, areaSearch, page, pageSize);
        }
    }

    public record ClaimListPage(List<Claim> claims, int totalCount, int page) {
    }

}
