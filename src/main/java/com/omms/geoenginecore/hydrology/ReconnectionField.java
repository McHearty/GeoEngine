package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;

/**
 * Bounded multi-plate reconnection (TECHSPEC_AMEND001 A3.12).
 *
 * <p>Handles channel reconnection between adjacent plates to ensure
 * continuous drainage across plate boundaries. Uses bounded search
 * with reconnectRadius and maxReconnectionSamples.
 */
public final class ReconnectionField {
    /** Search radius in blocks. */
    private final double reconnectRadius;
    /** Maximum samples for reconnection search. */
    private final int maxReconnectionSamples;
    /** Sea level extension in blocks. */
    private final int seaLevelExtension;

    /**
     * Constructs the reconnection field.
     *
     * @param reconnectRadius search radius in blocks
     * @param maxReconnectionSamples maximum samples for search
     * @param seaLevelExtension sea level extension in blocks
     */
    public ReconnectionField(double reconnectRadius, int maxReconnectionSamples,
                              int seaLevelExtension) {
        this.reconnectRadius = Math.max(0.0, reconnectRadius);
        this.maxReconnectionSamples = Math.max(1, maxReconnectionSamples);
        this.seaLevelExtension = Math.max(0, seaLevelExtension);
    }

    /**
     * Finds the nearest channel or outlet within the search radius.
     * Uses bounded spiral search with maxReconnectionSamples.
     *
     * @param kernel the H₀ kernel
     * @param x current X coordinate
     * @param z current Z coordinate
     * @param minAccumulation minimum accumulation threshold for channel detection
     * @return true if a reconnection target was found
     */
    public boolean findReconnectionTarget(ScalarFieldKernel kernel,
                                           double x, double z,
                                           double minAccumulation) {
        // Bounded spiral search: expand outward in rings
        int samplesUsed = 0;
        double step = 4.0; // Sample every 4 blocks
        
        for (int ring = 1; samplesUsed < maxReconnectionSamples; ring++) {
            double radius = ring * step;
            if (radius > reconnectRadius) {
                break;
            }
            
            // Sample 8 points on the ring (D8 directions)
            for (int angle = 0; angle < 8; angle++) {
                if (samplesUsed >= maxReconnectionSamples) {
                    break;
                }
                
                double theta = angle * Math.PI / 4.0;
                double sx = x + radius * Math.cos(theta);
                double sz = z + radius * Math.sin(theta);
                
                // Check if this point has sufficient flow accumulation
                // (indicating a channel) or is below sea level (outlet)
                if (kernel != null) {
                    double flowAcc = kernel.evaluateFullFlowAccumulation(sx, sz);
                    if (flowAcc >= minAccumulation) {
                        return true; // Found channel
                    }
                }
                
                samplesUsed++;
            }
        }
        
        return false; // No target found within budget
    }

    /**
     * Finds the nearest outlet within the search radius (simplified check).
     * Legacy API for backward compatibility.
     *
     * @param kernel the H₀ kernel (unused in simplified check)
     * @param x current X coordinate
     * @param z current Z coordinate
     * @param outletX outlet X reference
     * @param outletZ outlet Z reference
     * @return true if within reconnectRadius of outlet
     */
    public boolean findReconnectionTarget(ScalarFieldKernel kernel,
                                           double x, double z,
                                           double outletX, double outletZ) {
        // Simplified: check if we're within reconnectRadius of outlet
        double dx = x - outletX;
        double dz = z - outletZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return dist <= reconnectRadius;
    }

    /**
     * Computes the reconnection distance to the nearest outlet.
     *
     * @param x current X coordinate
     * @param z current Z coordinate
     * @param outletX outlet X reference
     * @param outletZ outlet Z reference
     * @return distance to outlet in blocks
     */
    public double reconnectionDistance(double x, double z,
                                        double outletX, double outletZ) {
        double dx = x - outletX;
        double dz = z - outletZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Checks if reconnection should be attempted based on search budget.
     *
     * @param samplesUsed number of samples used so far
     * @return true if reconnection should continue
     */
    public boolean shouldContinueSearch(int samplesUsed) {
        return samplesUsed <= maxReconnectionSamples;
    }

    /**
     * Computes the sea level extension factor for coastal areas.
     *
     * @param elevation elevation in blocks
     * @param seaLevel sea level in blocks
     * @return extension factor (1.0 = no extension, < 1.0 = extended)
     */
    public double seaLevelExtensionFactor(double elevation, double seaLevel) {
        if (elevation >= seaLevel) {
            return 1.0;
        }
        // Below sea level: extend drainage to sea level
        double depth = seaLevel - elevation;
        return Math.max(0.0, 1.0 - depth / (seaLevelExtension * 10.0));
    }
}
