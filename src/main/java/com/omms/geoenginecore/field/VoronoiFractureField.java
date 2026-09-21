package com.omms.geoenginecore.field;

import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Deterministic Voronoi fracture field (TECHSPEC §90-§91).
 *
 * <p>Partitions the plane into coherent cells whose centers are
 * jittered from a deterministic per-cell hash. The field provides
 * region identity, boundary location (distance to the fracture line),
 * and a per-cell vertical offset, so terrain pieces share consistent
 * fracture boundaries and no floating slabs or disconnected terrain
 * can form.
 */
public final class VoronoiFractureField {
    /** Domain seed used to hash cell centers. */
    private final long seed;
    /** Fracture cell size in blocks. */
    private final double cellSize;

    /**
     * Reusable output of one fracture query.
     *
     * <p>Mutable scratch struct reused across evaluations
     * (TECHSPEC §66); do not share between threads.
     */
    public static final class FractureSample {
        /** Deterministic identity of the owning cell. */
        public long cellId;
        /** Blocks from the column to the nearest fracture line. */
        public double distanceToEdge;
        /** Discrete vertical offset assigned to the owning cell. */
        public double cellElevation;
    }

    /**
     * @param seed domain seed used to hash cell centers
     * @param cellSize fracture cell size in blocks
     */
    public VoronoiFractureField(long seed, double cellSize) {
        this.seed = seed;
        this.cellSize = cellSize;
    }

    /**
     * Fills {@code out} for one column.
     *
     * <p>Tracks the two nearest jittered centers over the 3×3
     * neighborhood of the containing cell; the distance to the
     * fracture line is the gap between the two center distances.
     * The owning cell receives one of five discrete vertical offsets
     * so regions form coherent, block-quantized terraces.
     *
     * @param x world-space X of the column
     * @param z world-space Z of the column
     * @param out scratch struct to fill
     */
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

    /**
     * Fast floor for values whose integer part fits an
     * {@code int}, including negative fractional inputs.
     *
     * @param v value to floor
     * @return largest integer ≤ v
     */
    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
