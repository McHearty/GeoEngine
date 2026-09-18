package com.omms.geoenginecore.derivative;

public final class Gradient {
    public double gx;
    public double gz;
    public double magnitude;

    public void calculate(double hNorth, double hSouth, double hWest, double hEast, double step) {
        this.gx = (hSouth - hNorth) / step;
        this.gz = (hEast - hWest) / step;
        this.magnitude = Math.sqrt(gx * gx + gz * gz);
    }
}
