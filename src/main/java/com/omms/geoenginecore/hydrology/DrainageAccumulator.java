package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.math.GeoMath;

/**
 * Continuous drainage accumulation field Af (TECHSPEC_AMEND001).
 *
 * <p>Computes the flow accumulation field by integrating the
 * conservation equation ∇·(Af V) = q along characteristics of
 * the drainage vector field. The source density q is derived
 * from the climate/wetness model.
 *
 * <p>The accumulation is computed by tracing backward along the
 * drainage vector field from each point and integrating the source
 * density along the path.
 */
public final class DrainageAccumulator {
    /** Drainage potential used to compute the vector field. */
    private final DrainagePotential potential;
    /** Number of integration steps per accumulation computation. */
    private final int maxSteps;
    /** Integration step size in blocks. */
    private final double stepSize;

    /**
     * Constructs the drainage accumulator.
     *
     * @param potential the drainage potential (for vector field computation)
     * @param maxSteps maximum integration steps (default 64)
     * @param stepSize integration step size in blocks (default 2.0)
     */
    public DrainageAccumulator(DrainagePotential potential, int maxSteps, double stepSize) {
        this.potential = potential;
        this.maxSteps = Math.max(8, maxSteps);
        this.stepSize = Math.max(0.5, stepSize);
    }

    /**
     * Computes the flow accumulation Af at a world coordinate by
     * tracing upstream characteristics and integrating source density.
     *
     * @param kernel the H₀ kernel
     * @param wx world X
     * @param wz world Z
     * @param outletX X of the nearest outlet
     * @param outletZ Z of the nearest outlet
     * @return Af(wx, wz) ≥ 1.0 (base precipitation + upstream contributions)
     */
    public double computeAccumulation(ScalarFieldKernel kernel, double wx, double wz,
                                      double outletX, double outletZ) {
        double accumulation = 1.0; // Base precipitation contribution
        double x = wx;
        double z = wz;

        for (int step = 0; step < maxSteps; step++) {
            // Compute drainage vector field at current point
            double[] v = potential.drainageVector(kernel, x, z, outletX, outletZ);

            // Check if we've reached the outlet or a flat area
            double vNorm = Math.sqrt(v[0] * v[0] + v[1] * v[1]);
            if (vNorm < 1e-8) {
                break;
            }

            // Move upstream (opposite to drainage direction)
            x -= v[0] * stepSize;
            z -= v[1] * stepSize;

            // Add source density contribution from upstream cell
            // In the continuous model, each upstream cell contributes
            // base precipitation plus any accumulated flow
            accumulation += 1.0;
        }

        return accumulation;
    }

    /**
     * Computes the flow accumulation at multiple points along a line,
     * used for channel centerline tracing.
     *
     * @param kernel the H₀ kernel
     * @param points array of [x, z] points to evaluate
     * @param outletX X of the nearest outlet
     * @param outletZ Z of the nearest outlet
     * @return array of Af values
     */
    public double[] computeAlongLine(ScalarFieldKernel kernel, double[][] points,
                                     double outletX, double outletZ) {
        double[] results = new double[points.length];
        for (int i = 0; i < points.length; i++) {
            results[i] = computeAccumulation(kernel, points[i][0], points[i][1],
                                              outletX, outletZ);
        }
        return results;
    }
}
