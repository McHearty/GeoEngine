package com.geoengine.core.math;

/**
 * Mutable intermediate values produced while evaluating a terrain sample.
 *
 * <p>Instances are intended to be reused by the terrain-generation pipeline to avoid allocating
 * temporary objects during field evaluation.
 */
public final class GeoSample {
    /** World-space X coordinate of the sample. */
    public double worldX;

    /** World-space Z coordinate of the sample. */
    public double worldZ;

    // Tectonic & Stress

    /** Raw tectonic field value before geomorphological modification. */
    public double rawTectonic;

    /** X component of the stress warp applied to the sample position. */
    public double stressWarpX;

    /** Z component of the stress warp applied to the sample position. */
    public double stressWarpZ;

    /** Warped world-space X coordinate used by downstream field evaluations. */
    public double warpedX;

    /** Warped world-space Z coordinate used by downstream field evaluations. */
    public double warpedZ;

    /** Tectonic uplift contribution, when produced by the terrain pipeline. */
    public double tectonicUplift;

    // Epoch & Climate

    /** Geological age value evaluated at the warped position. */
    public double age;

    /** Temperature value evaluated at the warped position. */
    public double temperature;

    /** Humidity value evaluated at the warped position. */
    public double humidity;

    /** Climate-dependent multiplier applied to geomorphological processes. */
    public double climateMultiplier;

    // Geomorphology & Incision

    /** Amount of surface lowering produced by the erosion stage. */
    public double erosionLowering;

    /** Surface height before hydrological incision and deposition. */
    public double surfaceH0;

    /** Surface lowering produced by river incision. */
    public double riverIncision;

    /** Surface height added by the deposition stage. */
    public double deposition;

    /** Final surface height after all geomorphological modifications. */
    public double finalSurface;

    // Derivatives

    /** X component of the surface gradient. */
    public double gradX;

    /** Z component of the surface gradient. */
    public double gradZ;

    /** Magnitude of the surface gradient. */
    public double gradMagnitude;

    /** Discrete Laplacian of the surface field. */
    public double laplacian;

    // Volumetric

    /** Vertical warp applied to the terrain density calculation. */
    public double warpY;

    /** Cave-field contribution to the terrain density calculation. */
    public double caveVoid;

    /** Signed terrain density at the sample position. */
    public double density;

    /** Bit flags describing classifications assigned to the sample. */
    public int classificationBits;
}
