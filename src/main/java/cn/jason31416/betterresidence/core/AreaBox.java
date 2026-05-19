package cn.jason31416.betterresidence.core;

import cn.jason31416.planetlib.wrapper.SimpleLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public record AreaBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    public AreaBox {
        if (minX > maxX) {
            throw new IllegalArgumentException("minX cannot be greater than maxX");
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("minY cannot be greater than maxY");
        }
        if (minZ > maxZ) {
            throw new IllegalArgumentException("minZ cannot be greater than maxZ");
        }
    }

    public boolean contains(SimpleLocation location) {
        SimpleLocation blockLocation = location.getBlockLocation();
        return containsBlock((int) blockLocation.x(), (int) blockLocation.y(), (int) blockLocation.z());
    }

    public boolean containsBlock(int x, int y, int z) {
        return minX <= x && maxX >= x
                && minY <= y && maxY >= y
                && minZ <= z && maxZ >= z;
    }

    public boolean overlaps(AreaBox other) {
        return minX <= other.maxX
                && maxX >= other.minX
                && minY <= other.maxY
                && maxY >= other.minY
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
    }

    public boolean contains(AreaBox other) {
        return minX <= other.minX
                && maxX >= other.maxX
                && minY <= other.minY
                && maxY >= other.maxY
                && minZ <= other.minZ
                && maxZ >= other.maxZ;
    }

    @Nullable
    public AreaBox intersection(AreaBox other) {
        int intersectionMinX = Math.max(minX, other.minX);
        int intersectionMaxX = Math.min(maxX, other.maxX);
        int intersectionMinY = Math.max(minY, other.minY);
        int intersectionMaxY = Math.min(maxY, other.maxY);
        int intersectionMinZ = Math.max(minZ, other.minZ);
        int intersectionMaxZ = Math.min(maxZ, other.maxZ);
        if (intersectionMinX > intersectionMaxX || intersectionMinY > intersectionMaxY || intersectionMinZ > intersectionMaxZ) {
            return null;
        }
        return new AreaBox(intersectionMinX, intersectionMaxX, intersectionMinY, intersectionMaxY, intersectionMinZ, intersectionMaxZ);
    }

    public List<AreaBox> subtract(AreaBox covered) {
        AreaBox intersection = intersection(covered);
        if (intersection == null) {
            return List.of(this);
        }
        if (intersection.contains(this)) {
            return List.of();
        }

        List<AreaBox> remaining = new ArrayList<>();
        // Split off the X slabs first. They cover every Y/Z block outside the covered X range,
        // so later Y/Z splits only need to operate inside the intersecting X interval.
        if (minX < intersection.minX) {
            remaining.add(new AreaBox(minX, intersection.minX - 1, minY, maxY, minZ, maxZ));
        }
        if (intersection.maxX < maxX) {
            remaining.add(new AreaBox(intersection.maxX + 1, maxX, minY, maxY, minZ, maxZ));
        }

        int middleMinX = intersection.minX;
        int middleMaxX = intersection.maxX;
        // After removing X slabs, split Y slabs within the middle X column. This avoids
        // overlapping leftovers while still preserving the original inclusive bounds exactly.
        if (minY < intersection.minY) {
            remaining.add(new AreaBox(middleMinX, middleMaxX, minY, intersection.minY - 1, minZ, maxZ));
        }
        if (intersection.maxY < maxY) {
            remaining.add(new AreaBox(middleMinX, middleMaxX, intersection.maxY + 1, maxY, minZ, maxZ));
        }

        int middleMinY = intersection.minY;
        int middleMaxY = intersection.maxY;
        // Finally split Z slabs only inside the X/Y intersection. The intersection itself is
        // intentionally omitted, which is the actual covered volume being subtracted.
        if (minZ < intersection.minZ) {
            remaining.add(new AreaBox(middleMinX, middleMaxX, middleMinY, middleMaxY, minZ, intersection.minZ - 1));
        }
        if (intersection.maxZ < maxZ) {
            remaining.add(new AreaBox(middleMinX, middleMaxX, middleMinY, middleMaxY, intersection.maxZ + 1, maxZ));
        }

        return remaining;
    }

    public long volume() {
        return ((long) maxX - minX + 1) * ((long) maxY - minY + 1) * ((long) maxZ - minZ + 1);
    }
}
