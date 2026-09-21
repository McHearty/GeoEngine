package com.omms.geoenginecore.simd;

/**
 * Structure-of-arrays scratch for SIMD column batches
 * (TECHSPEC §66).
 *
 * <p>One batch covers 16 columns of 16 voxels. All arrays are
 * thread-confined and reused across evaluations; none of them may
 * be shared between threads.
 */
public final class SoABuffers {
    /** Voxels per batch (16 columns × 16). */
    public static final int BATCH_SIZE = 256;

    /** World X of each batch slot. */
    public final double[] posX = new double[BATCH_SIZE];
    /** World Z of each batch slot. */
    public final double[] posZ = new double[BATCH_SIZE];
    /** H₀ per batch slot. */
    public final double[] h0 = new double[BATCH_SIZE];
    /** ∂H/∂x per batch slot. */
    public final double[] gradX = new double[BATCH_SIZE];
    /** ∂H/∂z per batch slot. */
    public final double[] gradZ = new double[BATCH_SIZE];
    /** ∇²H per batch slot. */
    public final double[] laplacian = new double[BATCH_SIZE];
    /** Final surface H_f per batch slot. */
    public final double[] finalSurface = new double[BATCH_SIZE];

    /**
     * Fills the batch with the world coordinates of one 16×16
     * chunk.
     *
     * @param chunkOriginX world-coordinate X of the chunk origin
     * @param chunkOriginZ world-coordinate Z of the chunk origin
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
