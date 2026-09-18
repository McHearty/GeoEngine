package com.omms.geoenginecore.simd;

import com.omms.geoenginecore.dimension.DimensionProfile;
import com.omms.geoenginecore.math.FieldKernel;
import com.omms.geoenginecore.math.GeoConfig;
import com.omms.geoenginecore.math.ScalarFieldKernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class KernelProvider {
    private static final Logger LOGGER = Logger.getLogger("GeoEngine-Core");
    private static final boolean VECTOR_API_AVAILABLE;
    private static final MethodHandle VECTOR_KERNEL_CONSTRUCTOR;

    static {
        boolean available = false;
        MethodHandle constructor = null;

        try {
            Class.forName("jdk.incubator.vector.DoubleVector", false, KernelProvider.class.getClassLoader());

            Class<?> vectorKernelClass = Class.forName(
                "com.omms.geoenginecore.simd.VectorFieldKernel", true, KernelProvider.class.getClassLoader()
            );

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            constructor = lookup.findConstructor(
                vectorKernelClass, MethodType.methodType(void.class, long.class, DimensionProfile.class)
            );
            available = true;
            LOGGER.info("[GeoEngine] Java 21 Vector API detected: Hardware SIMD active.");
        } catch (Throwable t) {
            LOGGER.log(Level.INFO, "[GeoEngine] Incubator Vector API not active. Running on Scalar reference authority.");
            available = false;
            constructor = null;
        }

        VECTOR_API_AVAILABLE = available;
        VECTOR_KERNEL_CONSTRUCTOR = constructor;
    }

    private KernelProvider() {}

    public static boolean isVectorApiAvailable() {
        return VECTOR_API_AVAILABLE;
    }

    public static FieldKernel createKernel(long worldSeed, GeoConfig config) {
        return createKernel(worldSeed, new com.omms.geoenginecore.dimension.OverworldProfile(config));
    }

    public static FieldKernel createKernel(long worldSeed, DimensionProfile profile) {
        if (VECTOR_API_AVAILABLE && VECTOR_KERNEL_CONSTRUCTOR != null) {
            try {
                return (FieldKernel) VECTOR_KERNEL_CONSTRUCTOR.invoke(worldSeed, profile);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[GeoEngine] SIMD instantiation failed; falling back to Scalar.", t);
            }
        }
        return new ScalarFieldKernel(worldSeed, profile);
    }
}
