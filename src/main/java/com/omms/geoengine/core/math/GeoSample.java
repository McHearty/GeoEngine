package com.geoengine.core.math;

public final class GeoSample {
    public double worldX;
    public double worldZ;

    // Tectonic & Stress
    public double rawTectonic;
    public double stressWarpX;
    public double stressWarpZ;
    public double warpedX;
    public double warpedZ;
    public double tectonicUplift;

    // Epoch & Climate
    public double age;
    public double temperature;
    public double humidity;
    public double climateMultiplier;

    // Geomorphology & Incision
    public double erosionLowering;
    public double surfaceH0;
    public double riverIncision;
    public double deposition;
    public double finalSurface;

    // Derivatives
    public double gradX;
    public double gradZ;
    public double gradMagnitude;
    public double laplacian;

    // Volumetric
    public double warpY;
    public double caveVoid;
    public double density;

    public int classificationBits;
}
