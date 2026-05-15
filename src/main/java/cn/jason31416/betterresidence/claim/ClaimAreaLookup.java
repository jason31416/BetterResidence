package cn.jason31416.betterresidence.claim;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.planetlib.data.Param;
import cn.jason31416.planetlib.util.PluginLogger;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ClaimAreaLookup {
    private static final int CHUNK_SIZE = 16;
    private static final int MAX_CACHE_ENTRIES = 1024;
    private static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final Cache<ChunkKey, List<CachedArea>> cache = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_ENTRIES)
            .expireAfterAccess(CACHE_TTL_MILLIS, TimeUnit.MILLISECONDS)
            .build();

    private ClaimAreaLookup() {
    }

    /**
     * The main API to look up the claim effective at certain location.
     * @param location Any location in any world.
     * @return The claim at the location, or null if not found.
     */
    @Nullable
    public static Claim findClaimAt(SimpleLocation location) {
//        long startNanos = System.nanoTime();

        SimpleLocation blockLocation = location.getBlockLocation();
        String worldUuid = location.getWorld().getBukkitWorld().getUID().toString();
        int x = (int) blockLocation.x();
        int y = (int) blockLocation.y();
        int z = (int) blockLocation.z();

        ChunkKey key = chunkKey(worldUuid, x, z);
//        long nano1 = System.nanoTime();

        CachedLookup cachedLookup = getCachedOrFetchAreas(key);
//        long nano2 = System.nanoTime();
        Claim claim = findDeepestClaimAt(cachedLookup.areas(), x, y, z);
//        long nano3 = System.nanoTime();
//        PluginLogger.info("findClaimAt cacheHit="+cachedLookup.cacheHit+" initTime="+(nano1-startNanos)+" fetchTime="+(nano2-nano1)+" depthTime="+(nano3-nano2)+" foundAreas="+cachedLookup.areas().size());


        return claim;
    }

    public static void clearCache() {
        cache.invalidateAll();
    }

    private static ChunkKey chunkKey(String worldUuid, int x, int z) {
        return new ChunkKey(
                worldUuid,
                Math.floorDiv(x, CHUNK_SIZE),
                Math.floorDiv(z, CHUNK_SIZE)
        );
    }

    private static AreaBox chunkBox(ChunkKey key) {
        int minX = key.chunkX() * CHUNK_SIZE;
        int minZ = key.chunkZ() * CHUNK_SIZE;

        return new AreaBox(
                minX,
                minX + CHUNK_SIZE - 1,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                minZ,
                minZ + CHUNK_SIZE - 1
        );
    }

    private static CachedLookup getCachedOrFetchAreas(ChunkKey key) {
        List<CachedArea> cachedAreas = cache.getIfPresent(key);
        if (cachedAreas != null) {
            return new CachedLookup(cachedAreas, true);
        }

        List<CachedArea> areas = resolveAreas(fetchOverlappingAreas(key));
        cache.put(key, areas);
        return new CachedLookup(areas, false);
    }

    @Nullable
    private static Claim findDeepestClaimAt(List<CachedArea> areas, int x, int y, int z) {
        if(areas.isEmpty()) return null;
        return areas.stream()
                .filter(area -> area.box().containsBlock(x, y, z))
                .max(Comparator
                        .comparingInt(CachedArea::claimDepth)
                        .thenComparingInt(CachedArea::areaId))
                .map(CachedArea::claim)
                .orElse(null);
    }

    private static List<CachedArea> resolveAreas(List<IndexedArea> areas) {
        return areas.stream()
                .map(area -> {
                    Claim claim = ClaimManager.fetchClaim(area.claimUuid());
                    if (claim == null) {
                        return null;
                    }

                    return new CachedArea(
                            area.areaId(),
                            area.box(),
                            area.claimUuid(),
                            claim,
                            claim.getDepth()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @SneakyThrows
    private static List<IndexedArea> fetchOverlappingAreas(ChunkKey key) {
        AreaBox box = chunkBox(key);

        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT claim_areas.claim_uuid,
                       claim_areas.world,
                       area.id AS area_id,
                       area.minX,
                       area.maxX,
                       area.minY,
                       area.maxY,
                       area.minZ,
                       area.maxZ
                FROM area
                JOIN claim_areas ON claim_areas.area_id = area.id
                WHERE area.minX <= ?
                  AND area.maxX >= ?
                  AND area.minY <= ?
                  AND area.maxY >= ?
                  AND area.minZ <= ?
                  AND area.maxZ >= ?
                  AND claim_areas.world = ?
                """,
                List.of(
                        Param.of(box.maxX()),
                        Param.of(box.minX()),
                        Param.of(box.maxY()),
                        Param.of(box.minY()),
                        Param.of(box.maxZ()),
                        Param.of(box.minZ()),
                        Param.of(key.worldUuid())
                ),
                ClaimAreaLookup::mapIndexedArea
        );
    }

    @SneakyThrows
    private static IndexedArea mapIndexedArea(java.sql.ResultSet rs) {
        return new IndexedArea(
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
                rs.getString("claim_uuid")
        );
    }

    private record ChunkKey(String worldUuid, int chunkX, int chunkZ) {
    }

    private record IndexedArea(int areaId, String world, AreaBox box, String claimUuid) {
    }

    private record CachedArea(int areaId, AreaBox box, String claimUuid, Claim claim, int claimDepth) {
    }

    private record CachedLookup(List<CachedArea> areas, boolean cacheHit) {
    }
}
