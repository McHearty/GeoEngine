package com.omms.geoenginecore.field;

import com.omms.geoenginecore.noise.SeedDerivation;

public final class VoronoiFractureField {
    private final long seed;
    private final double cellSize;

    public static final class FractureSample {
        public long cellId;
        public double distanceToEdge;
        public double cellElevation;
    }

    public VoronoiFractureField(long seed, double cellSize) {
        this.seed = seed;
        this.cellSize = cellSize;
    }

    public void sample(double x, double z, FractureSample out) {
        double scaledX = x / cellSize;
        double scaledZ = z / cellSize;

        int iX = fastFloor(scaledX);
        int iZ = fastFloor(scaledZ);

        double d1 = Double.POSITIVE_INFINITY;
        double d2 = Double.POSITIVE_INFINITY;
        long closestId = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cellX = iX + dx;
                int cellZ = iZ + dz;

                long cellHash = SeedDerivation.hashCoords(seed, cellX, cellZ);
                double jitterX = cellX + 0.1 + 0.8 * ((cellHash & 0xFFFF) / 65535.0);
                double jitterZ = cellZ + 0.1 + 0.8 * (((cellHash >>> 16) & 0xFFFF) / 65535.0);

                double distSq = (scaledX - jitterX) * (scaledX - jitterX) 
                              + (scaledZ - jitterZ) * (scaledZ - jitterZ);

                if (distSq < d1) {
                    d2 = d1;
                    d1 = distSq;
                    closestId = cellHash;
                } else if (distSq < d2) {
                    d2 = distSq;
                }
            }
        }

        out.cellId = closestId;
        out.distanceToEdge = Math.max(0.0, Math.sqrt(d2) - Math.sqrt(d1)) * cellSize;
        out.cellElevation = 58.0 + ((int) (Math.abs(closestId) % 5) * 8.0);
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
