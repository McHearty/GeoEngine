package com.omms.geoengineforge.structure;

import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.memory.ScratchpadProvider;
import com.omms.geoenginecore.memory.WorkerScratchpad;
import com.omms.geoenginecore.structure.StructureSuitabilityField;
import net.minecraft.core.BlockPos;

public final class GeoStructureValidator {
    private final FieldKernel kernel;
    private final StructureSuitabilityField suitabilityField;
    private final StructureSuitabilityField.FoundationReport report = new StructureSuitabilityField.FoundationReport();

    public GeoStructureValidator(FieldKernel kernel, GeoConfig config) {
        this.kernel = kernel;
        this.suitabilityField = new StructureSuitabilityField(config);
    }

    public boolean canSpawnVillage(BlockPos pos) {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.evaluateFullColumn(pos.getX(), pos.getZ(), sp.sample);

        int subSurfaceY = (int) Math.round(sp.sample.finalSurface) - 6;
        double caveVoid = kernel.getCaveField().evaluateCave(pos.getX(), subSurfaceY, pos.getZ(), sp.sample.finalSurface);

        suitabilityField.evaluateSuitability(sp.sample, caveVoid, report);
        return report.isSuitableForVillage;
    }

    public boolean canSpawnOutpost(BlockPos pos) {
        WorkerScratchpad sp = ScratchpadProvider.get();
        kernel.evaluateFullColumn(pos.getX(), pos.getZ(), sp.sample);

        int subSurfaceY = (int) Math.round(sp.sample.finalSurface) - 6;
        double caveVoid = kernel.getCaveField().evaluateCave(pos.getX(), subSurfaceY, pos.getZ(), sp.sample.finalSurface);

        suitabilityField.evaluateSuitability(sp.sample, caveVoid, report);
        return report.isSuitableForOutpost;
    }
}
