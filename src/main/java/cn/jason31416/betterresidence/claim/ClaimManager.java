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
import java.util.List;
import java.util.Optional;
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
        return claimCache.get(uuid, () -> {
            Optional<MapTree> row = DataHandler.getDatabase().select("claim")
                    .keyEquals("uuid", uuid)
                    .one();
            if (row.isEmpty()) return null;
            MapTree data = row.get();
            SimplePlayer owner = SimplePlayer.of(UUID.fromString(data.getString("owner_uuid")));
            return new Claim(owner, data.getString("name"), uuid, data.getString("parent_uuid", null));
        });
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

    public record ClaimMemberInfo(SimplePlayer player, String groupId) {
    }

}
