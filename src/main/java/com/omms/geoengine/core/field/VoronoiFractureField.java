package com.geoengine.core.field;

import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates a deterministic jittered Voronoi field for fracture-like terrain structures.
 *
 * <p>The input coordinates are mapped into a regular cell grid whose sites are deterministically
 * jittered within each cell. The sampler examines the surrounding 3x3 cell neighborhood to find
 * the two nearest sites and derives the distance to the nearest cell boundary from their distance
 * difference.
 *
 * <p>Each selected cell also receives a deterministic elevation step derived from its hashed cell
 * identifier.
 */
public final class VoronoiFractureField {
    /** Seed used to deterministically hash Voronoi cell coordinates. */
    private final long seed;

    /** Horizontal size of each Voronoi cell in world-space units. */
    private final double cellSize;

    /**
     * Mutable output containing the sampled Voronoi cell information.
     *
     * <p>Instances are populated by {@link #sample(double, double, FractureSample)} and can be
     * reused across evaluations to avoid creating a new result object for each sample.
     */
    public static final class FractureSample {
        /** Deterministic hash identifying the nearest Voronoi cell. */
        public long cellId;

        /**
         * World-space distance proxy to the nearest Voronoi cell edge.
         *
         * <p>The value is derived from the difference between the distances to the two nearest
         * sites and is therefore a boundary-proximity measure rather than an exact point-to-edge
         * distance.
         */
        public double distanceToEdge;

        /** Deterministic elevation assigned to the nearest Voronoi cell. */
        public double cellElevation;
    }

    /**
     * Creates a deterministic Voronoi fracture field.
     *
     * @param seed seed used for deterministic cell hashing
     * @param cellSize horizontal size of the Voronoi cells
     */
    public VoronoiFractureField(long seed, double cellSize) {
        this.seed = seed;
        this.cellSize = cellSize;
    }

    /**
     * Samples the Voronoi field at a horizontal world-space position.
     *
     * <p>The sampler identifies the nearest and second-nearest jittered cell sites in the
     * surrounding 3x3 neighborhood. The nearest site's hash is written to {@code out}, together
     * with a boundary-proximity measure and the elevation assigned to that cell.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @param out mutable result object populated by this method
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
                double jitterZ =
                    cellZ + 0.1 + 0.8 * (((cellHash >>> 16) & 0xFFFF) / 65535.0);

                double distSq =
                    (scaledX - jitterX) * (scaledX - jitterX)
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
        out.distanceToEdge =
            Math.max(0.0, Math.sqrt(d2) - Math.sqrt(d1)) * cellSize;

        int step = (int) (Math.abs(closestId) % 5);
        out.cellElevation = 58.0 + (step * 8.0);
    }

    /**
     * Computes mathematical floor for a value represented as an integer cell coordinate.
     *
     * <p>Java's primitive cast truncates toward zero, so negative fractional values require an
     * adjustment to obtain floor semantics.
     *
     * @param v value to floor
     * @return greatest integer less than or equal to {@code v}
     */
    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
