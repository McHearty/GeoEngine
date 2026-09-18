package com.omms.geoenginecore.simd;

public final class SoABuffers {
    public static final int BATCH_SIZE = 256;

    public final double[] posX = new double[BATCH_SIZE];
    public final double[] posZ = new double[BATCH_SIZE];
    public final double[] h0 = new double[BATCH_SIZE];
    public final double[] gradX = new double[BATCH_SIZE];
    public final double[] gradZ = new double[BATCH_SIZE];
    public final double[] laplacian = new double[BATCH_SIZE];
    public final double[] finalSurface = new double[BATCH_SIZE];

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
