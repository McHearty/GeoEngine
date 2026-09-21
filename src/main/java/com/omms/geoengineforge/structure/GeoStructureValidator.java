package com.omms.geoengineforge.structure;

import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.structure.StructureSuitabilityField;
import net.minecraft.core.BlockPos;

/**
 * Structure spawn validation against the pipeline
 * (TECHSPEC §209).
 *
 * <p>Villages require solid ground with a 6-block subsurface depth
 * and no cave void beneath the building; outposts additionally need
 * snow cover and a cold, wet climate.
 */
public final class GeoStructureValidator {
    /** Field kernel. */
    private final FieldKernel kernel;
    /** Suitability scorer. */
    private final StructureSuitabilityField suitabilityField;
    /** Reused foundation report (allocation-free scoring). */
    private final StructureSuitabilityField.FoundationReport report = new StructureSuitabilityField.FoundationReport();

    /**
     * @param kernel field kernel
     * @param config validated configuration
     */
    public GeoStructureValidator(FieldKernel kernel, GeoConfig config) {
        this.kernel = kernel;
        this.suitabilityField = new StructureSuitabilityField(config);
    }

    /**
     * Village suitability: solid ground, 6-block subsurface depth,
     * and no cave void beneath the building (TECHSPEC §209).
     *
     * @param pos candidate building position
     * @return true if a village may spawn here
     */
    public boolean canSpawnVillage(BlockPos pos) {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.evaluateFullColumn(pos.getX(), pos.getZ(), sp.sample);

        int subSurfaceY = (int) Math.round(sp.sample.finalSurface) - 6;
        double caveVoid = kernel.getCaveField().evaluateCave(pos.getX(), subSurfaceY, pos.getZ(), sp.sample.finalSurface);

        suitabilityField.evaluateSuitability(sp.sample, caveVoid, report);
        return report.isSuitableForVillage;
    }

    /**
     * Outpost suitability: village rules plus snow cover and a
     * cold, wet climate (TECHSPEC §209).
     *
     * @param pos candidate building position
     * @return true if an outpost may spawn here
     */
    public boolean canSpawnOutpost(BlockPos pos) {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.evaluateFullColumn(pos.getX(), pos.getZ(), sp.sample);

        int subSurfaceY = (int) Math.round(sp.sample.finalSurface) - 6;
        double caveVoid = kernel.getCaveField().evaluateCave(pos.getX(), subSurfaceY, pos.getZ(), sp.sample.finalSurface);

        suitabilityField.evaluateSuitability(sp.sample, caveVoid, report);
        return report.isSuitableForOutpost;
    }
}
