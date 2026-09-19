package com.omms.geoenginecore.math;

public final class GeoSample {
    public double worldX;
    public double worldZ;

    public double rawTectonic;
    public double stressWarpX;
    public double stressWarpZ;
    public double warpedX;
    public double warpedZ;
    public double tectonicUplift;

    public double age;
    public double temperature;
    public double humidity;
    public double climateMultiplier;

    public double erosionLowering;
    public double surfaceH0;
    public double flowAccumulation;
    public double riverIncision;
    public double deposition;
    public double finalSurface;
    public int waterSurfaceLevel; // Local river water level (§149)

    public double gradX;
    public double gradZ;
    public double gradMagnitude;
    public double laplacian;

    public double warpY;
    public double caveVoid;
    public double density;

    public int classificationBits;
}
