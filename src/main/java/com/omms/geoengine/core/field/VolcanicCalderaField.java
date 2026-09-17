package com.geoengine.core.field;

import com.geoengine.core.noise.GeoNoise;
import com.geoengine.core.noise.NoiseDomain;
import com.geoengine.core.noise.SeedDerivation;

/**
 * Evaluates a deterministic volcanic-caldera relief profile.
 *
 * <p>Caldera centers are arranged on a regular horizontal cell grid. Relief is determined from
 * the distance to the selected cell center and consists of a central depression, an elevated
 * surrounding rim, and an outer falloff region.
 *
 * <p>The class also initializes a deterministic tectonic-detail noise source, but the current
 * relief evaluation does not sample that noise field.
 */
public final class VolcanicCalderaField {
    /**
     * Deterministic noise source associated with the volcanic field.
     *
     * <p>Currently initialized for the field but not consumed by {@link #evaluateVolcanicRelief}.
     */
    private final GeoNoise ventNoise;

    /** Horizontal spacing between candidate caldera centers. */
    private static final double CELL_SIZE = 280.0;

    /**
     * Creates a volcanic-caldera field using a deterministic tectonic-detail seed.
     *
     * @param worldSeed world seed used for deterministic field generation
     * @param version generator version used during seed derivation
     */
    public VolcanicCalderaField(long worldSeed, int version) {
        long seed = SeedDerivation.derive(
            worldSeed,
            1,
            NoiseDomain.TECTONIC_DETAIL_A.getSalt(),
            version);

        this.ventNoise = new GeoNoise(seed);
    }

    /**
     * Evaluates volcanic relief at a horizontal world-space position.
     *
     * <p>The nearest cell center is selected using {@link Math#round(double)} on each horizontal
     * coordinate. Positions outside one and a half caldera radii from that center have no volcanic
     * relief. Within the active radius, the profile transitions from a quadratic central
     * depression to a sinusoidal rim and then to a linear outer falloff.
     *
     * @param x world-space X coordinate
     * @param z world-space Z coordinate
     * @return volcanic elevation contribution, with negative values representing the central
     *     depression
     */
    public double evaluateVolcanicRelief(double x, double z) {
        long cellX = Math.round(x / CELL_SIZE);
        long cellZ = Math.round(z / CELL_SIZE);

        double centerX = cellX * CELL_SIZE;
        double centerZ = cellZ * CELL_SIZE;

        double dx = x - centerX;
        double dz = z - centerZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double radius = 96.0;

        if (dist > radius * 1.5) {
            return 0.0;
        }

        double normDist = dist / radius;
        if (normDist < 0.28) {
            double depression = 1.0 - (normDist / 0.28);
            return -38.0 * depression * depression;
        } else if (normDist <= 0.85) {
            double rim = Math.sin((normDist - 0.28) / 0.57 * Math.PI);
            return 52.0 * rim;
        } else {
            double falloff = 1.0 - ((normDist - 0.85) / 0.65);
            return 14.0 * Math.max(0.0, falloff);
        }
    }
}
