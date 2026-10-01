package com.omms.geoenginecore.hydrology;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug sampler for river network visualization.
 * Walks the drainage graph and samples centerline points for particle rendering.
 * Core-pure: no Minecraft types.
 */
public final class RiverDebugSampler {

    public record CenterlineSample(
        double x, double y, double z,
        int order,
        long basinId,
        boolean confluence,
        boolean sink
    ) {}

    /** Points to emit per lattice edge (densification factor). */
    private static final int DENSIFY = 2;
    /** Hover height above bed (blocks). */
    private static final double HOVER = 3.0;
    /** Maximum total samples. */
    private static final int MAX_SAMPLES = 1500;

    private RiverDebugSampler() {}

    /**
     * Sample river centerlines in a world AABB.
     *
     * @param graph drainage graph for the region
     * @param hydrologyField for basin/confluence queries
     * @param kernel for bed elevation queries
     * @param minX, minZ, maxX, maxZ world-space AABB
     * @return sampled centerline points (never null)
     */
    public static List<CenterlineSample> sampleRiverCenterlines(
        DrainageGraph graph,
        HydrologyField hydrologyField,
        com.omms.geoenginecore.math.ScalarFieldKernel kernel,
        double minX, double minZ, double maxX, double maxZ) {

        List<CenterlineSample> samples = new ArrayList<>();

        // Walk all edges on channels (order >= 1)
        for (int i = 0; i < graph.receiverIndex.length; i++) {
            int receiver = graph.receiverIndex[i];
            if (receiver < 0 || receiver == i) {
                continue;
            }

            // Check if this edge is on a channel (order >= 1)
            double af = graph.flowAccumulation[i];
            if (af < 2.5) { // ChannelField.CHANNEL_INITIATION_FLOW
                continue;
            }
            int order;
            if (af < 5.0) {
                order = 1;
            } else if (af < 25.0) {
                order = 2;
            } else if (af < 80.0) {
                order = 3;
            } else {
                order = 4;
            }

            // Sample points along this edge
            sampleEdge(graph, hydrologyField, kernel, i, receiver, af, order,
                minX, minZ, maxX, maxZ, samples);

            if (samples.size() >= MAX_SAMPLES) {
                break;
            }
        }

        return samples;
    }

    private static void sampleEdge(
        DrainageGraph graph,
        HydrologyField hydrologyField,
        com.omms.geoenginecore.math.ScalarFieldKernel kernel,
        int cur, int receiver, double flowAcc, int order,
        double minX, double minZ, double maxX, double maxZ,
        List<CenterlineSample> samples) {

        double x1 = graph.latticeX(cur);
        double z1 = graph.latticeZ(cur);
        double x2 = graph.latticeX(receiver);
        double z2 = graph.latticeZ(receiver);

        // Query bed elevation at segment endpoints
        double h1 = queryBed(kernel, x1, z1);
        double h2 = queryBed(kernel, x2, z2);

        // Query basin and confluence
        long basinId = hydrologyField.basinIdForCell(graph, cur);
        boolean confluence = hydrologyField.confluenceIdForCell(graph, cur) != HydrologyField.NO_ID;
        boolean sink = receiver < 0;

        // Emit DENSIFY points along this edge
        for (int i = 0; i < DENSIFY; i++) {
            double t = (i + 1) / (double) (DENSIFY + 1);
            double x = x1 + t * (x2 - x1);
            double z = z1 + t * (z2 - z1);
            double y = (h1 + t * (h2 - h1)) + HOVER;

            // AABB filter
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                samples.add(new CenterlineSample(x, y, z, order, basinId, confluence, sink));
            }
        }
    }

    private static double queryBed(com.omms.geoenginecore.math.ScalarFieldKernel kernel,
                                   double x, double z) {
        // Query bed elevation at point
        try {
            com.omms.geoenginecore.math.GeoSample sample = new com.omms.geoenginecore.math.GeoSample();
            sample.worldX = (int) x;
            sample.worldZ = (int) z;
            kernel.evaluateFullColumn((int) x, (int) z, sample);
            return sample.hPre;
        } catch (Exception e) {
            return 64.0; // Default to sea level if query fails
        }
    }
}
