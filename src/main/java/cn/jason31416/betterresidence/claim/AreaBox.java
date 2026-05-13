package cn.jason31416.betterresidence.claim;

import cn.jason31416.planetlib.wrapper.SimpleLocation;

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
        int x = (int) blockLocation.x();
        int y = (int) blockLocation.y();
        int z = (int) blockLocation.z();

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
}
