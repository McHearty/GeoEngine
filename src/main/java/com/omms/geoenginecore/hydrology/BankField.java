package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.GeoMath;

/**
 * Bank geometry from continuous channel fields (TECHSPEC_AMEND001 A3.7).
 *
 * <p>Computes bank displacement as a continuous function of distance from
 * the channel centerline. Bank geometry operates on the final post-meander,
 * post-smoothing channel centerline.
 */
public final class BankField {
    /** Bank width in blocks. */
    private final double bankWidth;
    /** Bank slope steepness. */
    private final double bankSlope;
    /** Bank noise amplitude. */
    private final double bankNoise;
    /** Bank steepness factor. */
    private final double bankSteepFactor;
    /** Containment berm height. */
    private final double containmentBerm;

    /**
     * Constructs the bank field.
     *
     * @param bankWidth bank width in blocks
     * @param bankSlope bank slope steepness
     * @param bankNoise bank noise amplitude
     * @param bankSteepFactor bank steepness factor
     * @param containmentBerm containment berm height
     */
    public BankField(double bankWidth, double bankSlope, double bankNoise,
                     double bankSteepFactor, double containmentBerm) {
        this.bankWidth = Math.max(0.5, bankWidth);
        this.bankSlope = Math.max(0.1, bankSlope);
        this.bankNoise = Math.max(0.0, bankNoise);
        this.bankSteepFactor = Math.max(0.1, bankSteepFactor);
        this.containmentBerm = Math.max(0.0, containmentBerm);
    }

    /**
     * Computes the bank displacement at a given distance from the
     * channel centerline.
     *
     * @param distance distance from centerline in blocks
     * @return bank displacement in blocks
     */
    public double displacement(double distance) {
        double halfBankWidth = bankWidth * 0.5;

        // Within channel: flat floor
        if (distance <= halfBankWidth) {
            return 0.0;
        }

        // On bank: linear slope
        double bankDistance = distance - halfBankWidth;
        if (bankDistance <= halfBankWidth) {
            return bankDistance * bankSlope;
        }

        // Beyond bank: flat with containment berm
        return halfBankWidth * bankSlope + containmentBerm;
    }

    /**
     * Computes the bank displacement with noise.
     *
     * @param distance distance from centerline in blocks
     * @param seed deterministic seed for noise
     * @return bank displacement in blocks
     */
    public double displacementNoisy(double distance, long seed) {
        double base = displacement(distance);

        // Add deterministic noise within channel
        double halfBankWidth = bankWidth * 0.5;
        if (distance <= halfBankWidth && bankNoise > 0.0) {
            // Simple deterministic noise based on distance and seed
            long mixed = seed ^ (long) (distance * 100.0);
            mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
            mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
            mixed = mixed ^ (mixed >>> 31);
            double noise = ((mixed >>> 11) & 0x7FFFFFFFL) / (double) 0x80000000L;
            base += (noise - 0.5) * bankNoise;
        }

        return base;
    }

    /**
     * Determines if a location is within the bank region.
     *
     * @param distance distance from centerline in blocks
     * @return true if within bank region
     */
    public boolean isWithinBank(double distance) {
        double halfBankWidth = bankWidth * 0.5;
        return distance > halfBankWidth && distance <= bankWidth;
    }

    /**
     * Determines if a location is on the channel floor.
     *
     * @param distance distance from centerline in blocks
     * @return true if on channel floor
     */
    public boolean isOnFloor(double distance) {
        return distance <= bankWidth * 0.5;
    }
}
