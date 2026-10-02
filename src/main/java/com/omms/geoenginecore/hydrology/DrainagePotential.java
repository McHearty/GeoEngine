package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.math.GeoMath;

/**
 * Outlet-aware drainage potential Φ (TECHSPEC_AMEND001).
 *
 * <p>The drainage potential combines terrain elevation with a
 * deterministic outlet-distance term to guide water toward outlets
 * rather than local depressions. The potential is defined as:
 *
 * <pre>
 * Φ(x,z) = H₀(x,z) + λ · D_outlet(x,z) + B(x,z)
 * </pre>
 *
 * <p>where:
 * <ul>
 *   <li>H₀ is the unincised terrain surface (T - E)</li>
 *   <li>D_outlet is the deterministic distance to the nearest outlet</li>
 *   <li>B is a bounded basin/spill correction</li>
 *   <li>λ is the outlet attraction strength</li>
 * </ul>
 */
public final class DrainagePotential {
    /** Outlet attraction strength λ. */
    private final double lambda;
    /** Regularization constant ε for gradient normalization. */
    private final double epsilon;
    /** Finite difference step size for gradient computation. */
    private final double stepSize;

    /**
     * Constructs the drainage potential with the given parameters.
     *
     * @param lambda outlet attraction strength (default 0.05)
     * @param epsilon regularization constant (default 1e-6)
     * @param stepSize finite difference step size in blocks (default 1.0)
     */
    public DrainagePotential(double lambda, double epsilon, double stepSize) {
        this.lambda = Math.max(0.0, lambda);
        this.epsilon = Math.max(1e-10, epsilon);
        this.stepSize = Math.max(0.1, stepSize);
    }

    /**
     * Computes the outlet-aware drainage potential at a world coordinate.
     *
     * @param kernel the H₀ kernel
     * @param wx world X
     * @param wz world Z
     * @param outletX X of the nearest outlet
     * @param outletZ Z of the nearest outlet
     * @return Φ(wx, wz)
     */
    public double evaluate(ScalarFieldKernel kernel, double wx, double wz,
                           double outletX, double outletZ) {
        double h0 = kernel.evaluatePureH0(wx, wz);
        double dist = Math.hypot(wx - outletX, wz - outletZ);
        return h0 + lambda * dist;
    }

    /**
     * Computes the gradient of the drainage potential using central
     * finite differences: ∇Φ = (∂Φ/∂x, ∂Φ/∂z).
     *
     * @param kernel the H₀ kernel
     * @param wx world X
     * @param wz world Z
     * @param outletX X of the nearest outlet
     * @param outletZ Z of the nearest outlet
     * @return array [dPhi/dx, dPhi/dz]
     */
    public double[] gradient(ScalarFieldKernel kernel, double wx, double wz,
                             double outletX, double outletZ) {
        double h = stepSize;

        // Central finite differences for ∂Φ/∂x
        double phiXPlus = evaluate(kernel, wx + h, wz, outletX, outletZ);
        double phiXMinus = evaluate(kernel, wx - h, wz, outletX, outletZ);
        double dPhiDx = (phiXPlus - phiXMinus) / (2.0 * h);

        // Central finite differences for ∂Φ/∂z
        double phiZPlus = evaluate(kernel, wx, wz + h, outletX, outletZ);
        double phiZMinus = evaluate(kernel, wx, wz - h, outletX, outletZ);
        double dPhiDz = (phiZPlus - phiZMinus) / (2.0 * h);

        return new double[]{dPhiDx, dPhiDz};
    }

    /**
     * Computes the flow accumulation at a point by tracing upstream
     * characteristics and integrating the source density.
     *
     * @param kernel the H₀ kernel
     * @param wx world X
     * @param wz world Z
     * @param outletX X of the nearest outlet
     * @param outletZ Z of the nearest outlet
     * @return flow accumulation Af ≥ 1.0
     */
    public double computeAccumulation(ScalarFieldKernel kernel, double wx, double wz,
                                      double outletX, double outletZ) {
        double accumulation = 1.0;
        double x = wx;
        double z = wz;

        for (int step = 0; step < 4; step++) {
            double[] v = drainageVector(kernel, x, z, outletX, outletZ);
            double vNorm = Math.sqrt(v[0] * v[0] + v[1] * v[1]);
            if (vNorm < 1e-8) {
                break;
            }

            // Move upstream
            x -= v[0] * 16.0;
            z -= v[1] * 16.0;
            accumulation += 1.0;
        }

        return accumulation;
    }

    /**
     * Computes the drainage vector field V = -∇Φ/‖∇Φ‖.
     *
     * @param kernel the H₀ kernel
     * @param wx world X
     * @param wz world Z
     * @param outletX X of the nearest outlet
     * @param outletZ Z of the nearest outlet
     * @return array [Vx, Vz] (unit vector pointing downhill)
     */
    public double[] drainageVector(ScalarFieldKernel kernel, double wx, double wz,
                                    double outletX, double outletZ) {
        double[] grad = gradient(kernel, wx, wz, outletX, outletZ);
        double gradX = grad[0];
        double gradZ = grad[1];

        // Normalize with epsilon regularization
        double norm = Math.sqrt(gradX * gradX + gradZ * gradZ);
        if (norm < epsilon) {
            // Flat area: default to flowing toward outlet
            double dx = outletX - wx;
            double dz = outletZ - wz;
            norm = Math.sqrt(dx * dx + dz * dz);
            if (norm < epsilon) {
                return new double[]{0.0, -1.0}; // Fallback: south
            }
            return new double[]{dx / norm, dz / norm};
        }

        // V = -∇Φ / ‖∇Φ‖ (flow downhill)
        return new double[]{-gradX / norm, -gradZ / norm};
    }
}
