package com.geoengine.core.math;

import com.geoengine.core.memory.WorkerScratchpad;

/**
 * Defines the terrain-field evaluation stages used by the GeoEngine terrain pipeline.
 *
 * <p>Implementations provide macro-scale surface evaluation, horizontal surface rasterization,
 * and pointwise density evaluation. Implementations may use different evaluation strategies while
 * preserving these method contracts.
 */
public interface FieldKernel {

    /**
     * Evaluates the macro-scale height field for a chunk.
     *
     * <p>The evaluated macro-grid data is written to the supplied scratchpad for use by subsequent
     * terrain-generation stages.
     *
     * @param scratchpad reusable worker-local storage for field evaluation
     * @param chunkWorldX world-space X coordinate identifying the chunk
     * @param chunkWorldZ world-space Z coordinate identifying the chunk
     */
    void evaluateMacroGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ);

    /**
     * Evaluates and rasterizes the terrain surface for a chunk.
     *
     * <p>The resulting surface data is written to the supplied scratchpad for downstream terrain
     * processing.
     *
     * @param scratchpad reusable worker-local storage for field evaluation
     * @param chunkWorldX world-space X coordinate identifying the chunk
     * @param chunkWorldZ world-space Z coordinate identifying the chunk
     */
    void rasterizeSurfaceChunk(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ);

    /**
     * Evaluates the terrain density at a single world-space position.
     *
     * @param scratchpad reusable worker-local storage required by the density evaluation
     * @param worldX world-space X coordinate
     * @param worldY world-space Y coordinate
     * @param worldZ world-space Z coordinate
     * @return density value at the supplied world-space position
     */
    float evaluateDensity(
        WorkerScratchpad scratchpad,
        int worldX,
        int worldY,
        int worldZ
    );
}
