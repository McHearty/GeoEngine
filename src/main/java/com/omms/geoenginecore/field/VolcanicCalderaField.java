package com.omms.geoenginecore.field;

import com.omms.geoenginecore.noise.GeoNoise;
import com.omms.geoenginecore.noise.NoiseDomain;
import com.omms.geoenginecore.noise.SeedDerivation;

/**
 * Sparse caldera field (TECHSPEC §109).
 *
 * <p>Calderas are placed sparsely on a 480-block cell lattice; about
 * 15% of cells receive a center, jittered within the cell by a
 * deterministic hash. Each caldera has a noise-warped perimeter, a
 * central depression, a raised rim, and a short outer falloff, so the
 * feature reads as a collapsed volcanic construct.
 */
public final class VolcanicCalderaField {
    /** Domain seed for caldera center placement. */
    private final long seed;
    /** Perimeter warp noise stream. */
    private final GeoNoise shapeNoise;
    /** Placement lattice spacing in blocks (sparse volcanic spacing). */
    private static final double CELL_SIZE = 480.0;

    /**
     * @param worldSeed world seed that roots every seed domain
     * @param version generator version
     */
    public VolcanicCalderaField(long worldSeed, int version) {
        this.seed = SeedDerivation.derive(worldSeed, 1, NoiseDomain.TECTONIC_DETAIL_A.getSalt(), version);
        this.shapeNoise = new GeoNoise(seed);
    }

    /**
     * Evaluates the caldera relief at (x, z).
     *
     * <p>Cells without a center contribute zero (sparse gating).
     * Inside a caldera: the inner 32% is a bowl-shaped depression up
     * to 42 blocks deep, the annulus out to 88% is a rim up to 58
     * blocks high with a sine profile, and the outer 12% fades over
     * 16 blocks.
     *
     * @param x world-space X
     * @param z world-space Z
     * @return caldera relief contribution in blocks
     */
    public double evaluateVolcanicRelief(double x, double z) {
        long cx = (long) Math.floor(x / CELL_SIZE);
        long cz = (long) Math.floor(z / CELL_SIZE);

        long cellHash = SeedDerivation.hashCoords(seed, (int) cx, (int) cz);

        // Sparse gating: Only ~15% of cells contain a volcanic caldera center
        if ((cellHash & 0xFF) > 38) {
            return 0.0;
        }

        // Jitter center within cell
        double centerX = (cx + 0.20 + 0.60 * ((cellHash & 0xFFFF) / 65535.0)) * CELL_SIZE;
        double centerZ = (cz + 0.20 + 0.60 * (((cellHash >>> 16) & 0xFFFF) / 65535.0)) * CELL_SIZE;

        double dx = x - centerX;
        double dz = z - centerZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Noise-warped perimeter
        double angle = Math.atan2(dz, dx);
        double warp = 1.0 + 0.30 * shapeNoise.sample2D(Math.cos(angle) * 1.5, Math.sin(angle) * 1.5);
        double radius = 110.0 * warp;

        if (dist > radius * 1.4) {
            return 0.0;
        }

        double normDist = dist / radius;
        if (normDist < 0.32) {
            double depression = 1.0 - (normDist / 0.32);
            return -42.0 * depression * depression;
        } else if (normDist <= 0.88) {
            double rim = Math.sin((normDist - 0.32) / 0.56 * Math.PI);
            return 58.0 * rim;
        } else {
            double falloff = 1.0 - ((normDist - 0.88) / 0.52);
            return 16.0 * Math.max(0.0, falloff);
        }
    }
}
