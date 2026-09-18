package com.omms.geoenginecore.geomorphology;

import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.GeoSample;
import com.omms.geoenginecore.math.ScalarFieldKernel;

public final class LandformClassifier {
    private static final double EPSILON_CURVATURE = 0.008;
    private static final double FLAT_SLOPE_THRESHOLD = 0.06;

    private final GeoConfig config;
    private final HessianSolver.CurvatureResult curvatureScratch = new HessianSolver.CurvatureResult();
    private final MultiScaleRelief.ReliefReport reliefScratch = new MultiScaleRelief.ReliefReport();

    public LandformClassifier(GeoConfig config) {
        this.config = config;
    }

    public int classify(
        ScalarFieldKernel kernel, GeoSample sample,
        double hC, double hN, double hS, double hW, double hE,
        double hNW, double hNE, double hSW, double hSE,
        double delta
    ) {
        HessianSolver.solve(hC, hN, hS, hW, hE, hNW, hNE, hSW, hSE, delta, curvatureScratch);
        double l1 = curvatureScratch.lambda1;
        double l2 = curvatureScratch.lambda2;

        MultiScaleRelief.evaluate(kernel, sample.worldX, sample.worldZ, hC, reliefScratch);

        double slope = sample.gradMagnitude;
        double altitude = sample.finalSurface;
        double seaLevel = config.seaLevel();

        int bits = 0;

        // Process Flags (§97) - Fixed: Glacial processes active in polar temperatures regardless of elevation
        if (sample.riverIncision > 1.8) bits |= LandformBits.PROCESS_FLUVIAL;
        if (sample.temperature < 0.25 || (altitude > seaLevel + 220.0 && sample.temperature < 0.35)) {
            bits |= LandformBits.PROCESS_GLACIAL;
        }
        if (sample.rawTectonic > 240.0) bits |= LandformBits.PROCESS_TECTONIC;
        if (sample.temperature > 0.65 && sample.humidity < 0.22 && slope < 0.18) bits |= LandformBits.PROCESS_AEOLIAN;
        if (sample.erosionLowering > 18.0) bits |= LandformBits.PROCESS_EROSIONAL;
        if (altitude < seaLevel + 6.0 && altitude > seaLevel - 8.0) bits |= LandformBits.PROCESS_COASTAL;

        if (altitude < seaLevel) {
            bits |= LandformBits.ENV_SUBMARINE;
        } else if (altitude <= seaLevel + 6.0) {
            bits |= LandformBits.ENV_COASTAL;
        } else if (altitude < seaLevel + 120.0) {
            bits |= LandformBits.ENV_LOWLAND;
        } else if (altitude < seaLevel + 260.0) {
            bits |= LandformBits.ENV_HIGHLAND;
        } else {
            bits |= LandformBits.ENV_ALPINE;
        }

        LandformType resolvedType = resolveLandform(sample, l1, l2, slope, altitude, seaLevel, reliefScratch, bits);
        bits = LandformBits.setType(bits, resolvedType);

        if (LandformBits.hasProcess(bits, LandformBits.PROCESS_FLUVIAL) && altitude >= seaLevel - 2.0) {
            bits |= LandformBits.FEAT_RIVER_CHANNEL;
            if (resolvedType == LandformType.CANYON && slope > 0.75) {
                bits |= LandformBits.FEAT_WATERFALL;
            }
        }
        if (resolvedType == LandformType.DUNE_FIELD) bits |= LandformBits.FEAT_DUNE_FIELD;
        if (resolvedType == LandformType.CALDERA || resolvedType == LandformType.VOLCANIC_CONE) {
            bits |= LandformBits.FEAT_VOLCANO_VENT;
        }

        sample.classificationBits = bits;
        return bits;
    }

    private LandformType resolveLandform(
        GeoSample sample, double l1, double l2, double slope, double altitude, double seaLevel,
        MultiScaleRelief.ReliefReport relief, int processBits
    ) {
        // Priority Tier 1: Calderas & Cones (Restricted strictly to active volcanic processes)
        if (LandformBits.hasProcess(processBits, LandformBits.PROCESS_VOLCANIC)) {
            if (l1 > EPSILON_CURVATURE && l2 > EPSILON_CURVATURE && relief.mesoProminence < -24.0) {
                return LandformType.CALDERA;
            }
            if (l1 < -EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE && slope > 0.45 && relief.mesoProminence > 35.0) {
                return LandformType.VOLCANIC_CONE;
            }
            if (relief.regionalProminence > 60.0 && slope < 0.25) {
                return LandformType.SHIELD_VOLCANO;
            }
        }

        // Priority Tier 2: Fjords, Canyons & Gorges
        if (sample.riverIncision > 6.0 || (l1 > EPSILON_CURVATURE && l2 <= EPSILON_CURVATURE)) {
            if (LandformBits.hasProcess(processBits, LandformBits.PROCESS_GLACIAL) 
                && LandformBits.hasProcess(processBits, LandformBits.PROCESS_COASTAL)) {
                return LandformType.FJORD;
            }
            if (sample.riverIncision > 10.0 && slope > 0.50) {
                return LandformType.CANYON;
            }
            if (slope > 0.35 && sample.riverIncision > 4.0) {
                return LandformType.GORGE;
            }
            if (l1 > EPSILON_CURVATURE && l2 <= EPSILON_CURVATURE) {
                return LandformType.VALLEY;
            }
        }

        // Priority Tier 3: Tablelands (Scale-Discriminated)
        if (slope < FLAT_SLOPE_THRESHOLD && Math.abs(l1) < EPSILON_CURVATURE && Math.abs(l2) < EPSILON_CURVATURE) {
            if (altitude > seaLevel + 140.0) {
                if (relief.mesoProminence > 25.0 && relief.microProminence > 10.0) {
                    return LandformType.BUTTE;
                }
                if (relief.mesoProminence > 20.0) {
                    return LandformType.MESA;
                }
                return LandformType.PLATEAU;
            }
            return LandformType.PLAINS;
        }

        // Priority Tier 4: Positive Mountains & Ridges
        if (relief.mesoProminence > 18.0 || relief.regionalProminence > 35.0 || altitude > seaLevel + 180.0) {
            if (relief.continentalRelief > 120.0 && relief.regionalProminence > 80.0) {
                return LandformType.MASSIF;
            }
            if (slope > 0.35 || l1 < -EPSILON_CURVATURE) {
                return LandformType.MOUNTAIN;
            }
            if (l1 >= -EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE) {
                return LandformType.RIDGE;
            }
            if (relief.mesoProminence > 12.0 && slope < 0.35) {
                return LandformType.HILL;
            }
        }

        if (LandformBits.hasProcess(processBits, LandformBits.PROCESS_AEOLIAN) && slope < 0.15) {
            return LandformType.DUNE_FIELD;
        }

        if (l1 > EPSILON_CURVATURE && l2 > EPSILON_CURVATURE && relief.regionalProminence < -25.0) {
            return LandformType.BASIN;
        }

        if (l1 > EPSILON_CURVATURE && l2 < -EPSILON_CURVATURE) {
            return LandformType.SADDLE;
        }

        return (slope < FLAT_SLOPE_THRESHOLD) ? LandformType.PLAINS : LandformType.UNKNOWN;
    }
}
