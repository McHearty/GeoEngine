package com.omms.geoenginecore.math;

import com.omms.geoenginecore.field.CaveField;
import com.omms.geoenginecore.field.WarpField;
import com.omms.geoenginecore.geomorphology.LandformClassifier;
import com.omms.geoenginecore.memory.WorkerScratchpad;

/**
 * Contract shared by every GeoEngine terrain kernel.
 *
 * <p>A kernel evaluates the deterministic geomorphic field stack. All
 * results are pure functions of (world seed, dimension, generator
 * version, config, coordinate), so chunk generation order, worker
 * thread, cache state, and server restart can never change a value
 * produced here (TECHSPEC §8).
 *
 * <p>The interface separates the 2-D surface pipeline (macro grid,
 * surface rasterization, full column) from 3-D volumetric density
 * evaluation (TECHSPEC §67).
 */
public interface FieldKernel {
    /**
     * Samples the expensive crustal fields onto the worker's 6×6
     * world-space macro node grid (4-block spacing plus one halo ring).
     *
     * <p>The macro grid is a coarse stand-in for the full 16×16 chunk.
     * It is a cache-friendly representation of stable fields, not the
     * final surface (TECHSPEC §38-§40).
     *
     * @param scratchpad worker-owned scratchpad that receives the macro node values
     * @param chunkWorldX world-coordinate X of the chunk origin
     * @param chunkWorldZ world-coordinate Z of the chunk origin
     */
    void evaluateMacroGrid(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ);

    /**
     * Rasterizes the 16×16 chunk surface.
     *
     * <p>Evaluates the macro grid, bilinearly interpolates the macro
     * nodes onto the per-column chunk lattice, then runs the remaining
     * surface processing passes (pre-fluvial modifiers, derivatives,
     * hydrology, landform classification).
     *
     * @param scratchpad worker-owned scratchpad that receives all chunk grids
     * @param chunkWorldX world-coordinate X of the chunk origin
     * @param chunkWorldZ world-coordinate Z of the chunk origin
     */
    void rasterizeSurfaceChunk(WorkerScratchpad scratchpad, int chunkWorldX, int chunkWorldZ);

    /**
     * Evaluates the canonical terrain density D(x, y, z) for one voxel
     * (TECHSPEC §49).
     *
     * @param scratchpad worker-owned scratchpad holding the chunk surface grid
     * @param worldX absolute world X of the voxel
     * @param worldY absolute world Y of the voxel
     * @param worldZ absolute world Z of the voxel
     * @return positive value when the voxel is solid, non-positive when it is air
     */
    float evaluateDensity(WorkerScratchpad scratchpad, int worldX, int worldY, int worldZ);

    /**
     * Runs the complete 2-D column pipeline for one world-space column
     * and stores every intermediate quantity in {@code sample}
     * (TECHSPEC §24: T → E → H₀ → derivatives → H_pre → drainage →
     * H* = H_pre − R → S → H_f → classification).
     *
     * <p>This is the scalar reference path. Optimized kernels must
     * reproduce these values within the established tolerance
     * (TECHSPEC §68, §71).
     *
     * @param wx world-space X of the column
     * @param wz world-space Z of the column
     * @param sample reusable sample struct overwritten in full by this pass
     */
    void evaluateFullColumn(double wx, double wz, GeoSample sample);

    /**
     * @return the volumetric cave field owned by this kernel
     */
    CaveField getCaveField();

    /**
     * @return the volumetric rock warp owned by this kernel
     */
    WarpField getWarpField();

    /**
     * @return the landform classifier owned by this kernel
     */
    LandformClassifier getLandformClassifier();
}
