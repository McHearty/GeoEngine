package com.geoengine.core.simd;

/**
 * Structure-of-arrays storage for batched terrain-field evaluation.
 *
 * <p>Each array stores one field for up to {@link #BATCH_SIZE} samples. For chunk-sized batches,
 * indices use the mapping {@code (z << 4) | x}, corresponding to a 16x16 horizontal chunk.
 */
public final class SoABuffers {
    /** Number of samples allocated for a single batch. */
    public static final int BATCH_SIZE = 256;

    /** World-space X coordinate for each sample. */
    public final double[] posX = new double[BATCH_SIZE];

    /** World-space Z coordinate for each sample. */
    public final double[] posZ = new double[BATCH_SIZE];

    /** Surface height before downstream surface modifications. */
    public final double[] h0 = new double[BATCH_SIZE];

    /** X component of the surface gradient for each sample. */
    public final double[] gradX = new double[BATCH_SIZE];

    /** Z component of the surface gradient for each sample. */
    public final double[] gradZ = new double[BATCH_SIZE];

    /** Discrete Laplacian of the surface field for each sample. */
    public final double[] laplacian = new double[BATCH_SIZE];

    /** Final surface height for each sample. */
    public final double[] finalSurface = new double[BATCH_SIZE];

    /**
     * Initializes the world-space coordinates for a 16x16 chunk.
     *
     * <p>Each local coordinate pair is mapped to a linear index using
     * {@code (localZ << 4) | localX}.
     *
     * @param chunkOriginX world-space X coordinate of the chunk origin
     * @param chunkOriginZ world-space Z coordinate of the chunk origin
     */
    public void initCoordinates(int chunkOriginX, int chunkOriginZ) {
        for (int lz = 0; lz < 16; lz++) {
            double wz = chunkOriginZ + lz;
            int rowOffset = lz << 4;
            for (int lx = 0; lx < 16; lx++) {
                int idx = rowOffset | lx;
                posX[idx] = chunkOriginX + lx;
                posZ[idx] = wz;
            }
        }
    }
}
