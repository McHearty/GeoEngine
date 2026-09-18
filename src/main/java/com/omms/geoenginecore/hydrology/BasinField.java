package com.omms.geoenginecore.hydrology;

public final class BasinField {
    private BasinField() {}

    public static boolean isBasin(double laplacian, double regionalRelief) {
        return laplacian > 0.02 && regionalRelief < 0.10;
    }

    public static double getAccommodationSpace(double basinDepth, double distanceToRim) {
        return Math.max(0.0, basinDepth * (1.0 - Math.exp(-distanceToRim * 0.02)));
    }
}
