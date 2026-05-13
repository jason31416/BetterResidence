package cn.jason31416.betterresidence.claim;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.planetlib.data.Param;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ClaimAreaLookup {
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final long CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final Object cacheLock = new Object();
    private static final Deque<CacheEntry> cache = new ArrayDeque<>();

    private ClaimAreaLookup() {
    }

    @Nullable
    public static Claim findClaimAt(SimpleLocation location) {
        SimpleLocation blockLocation = location.getBlockLocation();
        String world = blockLocation.world().getName();
        int x = (int) blockLocation.x();
        int y = (int) blockLocation.y();
        int z = (int) blockLocation.z();

        Claim cachedClaim = findCachedClaim(world, x, y, z);
        if (cachedClaim != null) {
            return cachedClaim;
        }

        IndexedArea matchingArea = findDeepestAreaAt(world, x, y, z);
        if (matchingArea == null) {
            return null;
        }

        List<IndexedArea> directSubclaimAreas = fetchOverlappingDirectSubclaimAreas(matchingArea);
        addCacheEntry(new CacheEntry(matchingArea, directSubclaimAreas, System.currentTimeMillis()));
        return Claim.fetchClaim(matchingArea.claimUuid());
    }

    public static void clearCache() {
        synchronized (cacheLock) {
            cache.clear();
        }
    }

    @Nullable
    private static Claim findCachedClaim(String world, int x, int y, int z) {
        long now = System.currentTimeMillis();

        synchronized (cacheLock) {
            Iterator<CacheEntry> iterator = cache.iterator();
            while (iterator.hasNext()) {
                CacheEntry entry = iterator.next();
                if (entry.isExpired(now)) {
                    iterator.remove();
                    continue;
                }

                IndexedArea initialArea = entry.initialArea();
                if (!initialArea.world().equals(world) || !initialArea.box().containsBlock(x, y, z)) {
                    continue;
                }

                iterator.remove();
                entry.markAccessed(now);
                cache.addFirst(entry);

                boolean insideSubclaim = entry.directSubclaimAreas().stream()
                        .anyMatch(area -> area.world().equals(world) && area.box().containsBlock(x, y, z));
                if (insideSubclaim) {
                    return null;
                }

                return Claim.fetchClaim(initialArea.claimUuid());
            }
        }

        return null;
    }

    private static void addCacheEntry(CacheEntry entry) {
        long now = System.currentTimeMillis();

        synchronized (cacheLock) {
            cache.removeIf(existing -> existing.isExpired(now)
                    || existing.initialArea().areaId() == entry.initialArea().areaId());
            cache.addFirst(entry);

            while (cache.size() > MAX_CACHE_ENTRIES) {
                cache.removeLast();
            }
        }
    }

    @Nullable
    @SneakyThrows
    private static IndexedArea findDeepestAreaAt(String world, int x, int y, int z) {
        List<IndexedArea> areas = DataHandler.getDatabase().getSqlInstance().executeQuery(
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
                        Param.of(x),
                        Param.of(x),
                        Param.of(y),
                        Param.of(y),
                        Param.of(z),
                        Param.of(z),
                        Param.of(world)
                ),
                ClaimAreaLookup::mapIndexedArea
        );

        return areas.stream()
                .filter(area -> Claim.fetchClaim(area.claimUuid()) != null)
                .max(Comparator
                        .comparingInt((IndexedArea area) -> Objects.requireNonNull(Claim.fetchClaim(area.claimUuid())).getDepth())
                        .thenComparingInt(IndexedArea::areaId))
                .orElse(null);
    }

    @SneakyThrows
    private static List<IndexedArea> fetchOverlappingDirectSubclaimAreas(IndexedArea initialArea) {
        AreaBox box = initialArea.box();

        return DataHandler.getDatabase().getSqlInstance().executeQuery(
                """
                SELECT child.uuid AS claim_uuid,
                       claim_areas.world,
                       area.id AS area_id,
                       area.minX,
                       area.maxX,
                       area.minY,
                       area.maxY,
                       area.minZ,
                       area.maxZ
                FROM claim AS child
                JOIN claim_areas ON claim_areas.claim_uuid = child.uuid
                JOIN area ON area.id = claim_areas.area_id
                WHERE child.parent_uuid = ?
                  AND claim_areas.world = ?
                  AND area.minX <= ?
                  AND area.maxX >= ?
                  AND area.minY <= ?
                  AND area.maxY >= ?
                  AND area.minZ <= ?
                  AND area.maxZ >= ?
                """,
                List.of(
                        Param.of(initialArea.claimUuid()),
                        Param.of(initialArea.world()),
                        Param.of(box.maxX()),
                        Param.of(box.minX()),
                        Param.of(box.maxY()),
                        Param.of(box.minY()),
                        Param.of(box.maxZ()),
                        Param.of(box.minZ())
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

    private record IndexedArea(int areaId, String world, AreaBox box, String claimUuid) {
    }

    private static final class CacheEntry {
        private final IndexedArea initialArea;
        private final List<IndexedArea> directSubclaimAreas;
        private long lastAccessedAtMillis;

        private CacheEntry(IndexedArea initialArea, List<IndexedArea> directSubclaimAreas, long lastAccessedAtMillis) {
            this.initialArea = initialArea;
            this.directSubclaimAreas = List.copyOf(directSubclaimAreas);
            this.lastAccessedAtMillis = lastAccessedAtMillis;
        }

        private IndexedArea initialArea() {
            return initialArea;
        }

        private List<IndexedArea> directSubclaimAreas() {
            return directSubclaimAreas;
        }

        private boolean isExpired(long now) {
            return lastAccessedAtMillis + CACHE_TTL_MILLIS < now;
        }

        private void markAccessed(long now) {
            lastAccessedAtMillis = now;
        }
    }
}
