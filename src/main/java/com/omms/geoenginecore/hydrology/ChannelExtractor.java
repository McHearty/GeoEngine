package com.omms.geoenginecore.hydrology;

import com.omms.geoenginecore.math.ScalarFieldKernel;
import com.omms.geoenginecore.math.GeoMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Channel extraction from continuous drainage fields (TECHSPEC_AMEND001).
 *
 * <p>Extracts channel paths by tracing characteristics of the
 * continuous drainage vector field. Starting from high-accumulation
 * points, channels are traced upstream following the vector field
 * to identify channel networks.
 */
public final class ChannelExtractor {
    /** Drainage potential used for vector field computation. */
    private final DrainagePotential potential;
    /** Minimum accumulation threshold for channel identification. */
    private final double minAccumulation;
    /** Integration step size in blocks. */
    private final double stepSize;
    /** Maximum channel length in blocks. */
    private final double maxLength;

    /**
     * Constructs the channel extractor.
     *
     * @param potential the drainage potential
     * @param minAccumulation minimum flow accumulation for channel ID
     * @param stepSize integration step size in blocks
     * @param maxLength maximum channel length in blocks
     */
    public ChannelExtractor(DrainagePotential potential, double minAccumulation,
                            double stepSize, double maxLength) {
        this.potential = potential;
        this.minAccumulation = Math.max(0.0, minAccumulation);
        this.stepSize = Math.max(0.5, stepSize);
        this.maxLength = Math.max(10.0, maxLength);
    }

    /**
     * Represents a point on a channel centerline.
     */
    public record ChannelPoint(double x, double z, double elevation, double accumulation) {
    }

    /**
     * Represents an extracted channel path.
     */
    public record ChannelPath(
        /** Source point (upstream). */
        ChannelPoint source,
        /** Mouth point (downstream). */
        ChannelPoint mouth,
        /** Length of the channel in blocks. */
        double length,
        /** Centerline points. */
        List<ChannelPoint> points) {
    }

    /**
     * Traces a channel upstream from a given point following the
     * drainage vector field until the channel terminates or the
     * maximum length is reached.
     *
     * @param kernel the H₀ kernel
     * @param startX starting X coordinate
     * @param startZ starting Z coordinate
     * @param outletX outlet X reference
     * @param outletZ outlet Z reference
     * @return the traced channel path
     */
    public ChannelPath traceChannel(ScalarFieldKernel kernel, double startX, double startZ,
                                     double outletX, double outletZ) {
        List<ChannelPoint> points = new ArrayList<>();
        double x = startX;
        double z = startZ;
        double length = 0.0;
        double elevation = kernel.evaluatePureH0(x, z);
        double accumulation = potential.computeAccumulation(kernel, x, z, outletX, outletZ);

        // Add mouth point
        points.add(new ChannelPoint(x, z, elevation, accumulation));

        // Trace upstream
        while (length < maxLength) {
            // Compute drainage vector at current point
            double[] v = potential.drainageVector(kernel, x, z, outletX, outletZ);

            // Check if we've reached a flat area or the channel terminates
            double vNorm = Math.sqrt(v[0] * v[0] + v[1] * v[1]);
            if (vNorm < 1e-8) {
                break;
            }

            // Move upstream (opposite to drainage direction)
            x -= v[0] * stepSize;
            z -= v[1] * stepSize;
            length += stepSize;

            elevation = kernel.evaluatePureH0(x, z);
            accumulation = potential.computeAccumulation(kernel, x, z, outletX, outletZ);

            // Check if accumulation drops below threshold
            if (accumulation < minAccumulation) {
                break;
            }

            points.add(new ChannelPoint(x, z, elevation, accumulation));
        }

        // First point is mouth, last point is source
        ChannelPoint mouth = points.get(0);
        ChannelPoint source = points.get(points.size() - 1);

        return new ChannelPath(source, mouth, length, points);
    }

    /**
     * Traces a channel downstream from a given point.
     *
     * @param kernel the H₀ kernel
     * @param startX starting X coordinate
     * @param startZ starting Z coordinate
     * @param outletX outlet X reference
     * @param outletZ outlet Z reference
     * @return the traced channel path
     */
    public ChannelPath traceDownstream(ScalarFieldKernel kernel, double startX, double startZ,
                                        double outletX, double outletZ) {
        List<ChannelPoint> points = new ArrayList<>();
        double x = startX;
        double z = startZ;
        double length = 0.0;

        while (length < maxLength) {
            double elevation = kernel.evaluatePureH0(x, z);
            double accumulation = potential.computeAccumulation(kernel, x, z, outletX, outletZ);
            points.add(new ChannelPoint(x, z, elevation, accumulation));

            // Compute drainage vector at current point
            double[] v = potential.drainageVector(kernel, x, z, outletX, outletZ);

            double vNorm = Math.sqrt(v[0] * v[0] + v[1] * v[1]);
            if (vNorm < 1e-8) {
                break;
            }

            // Move downstream (with drainage direction)
            x += v[0] * stepSize;
            z += v[1] * stepSize;
            length += stepSize;
        }

        ChannelPoint source = points.get(0);
        ChannelPoint mouth = points.get(points.size() - 1);

        return new ChannelPath(source, mouth, length, points);
    }
}
