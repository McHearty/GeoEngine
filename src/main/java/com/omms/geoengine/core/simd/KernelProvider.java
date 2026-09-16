package com.geoengine.core.simd;

import com.geoengine.core.math.FieldKernel;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.ScalarFieldKernel;

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
                "com.geoengine.core.simd.VectorFieldKernel", true, KernelProvider.class.getClassLoader()
            );

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            constructor = lookup.findConstructor(
                vectorKernelClass, MethodType.methodType(void.class, long.class, GeoConfig.class)
            );
            available = true;
            LOGGER.info("[GeoEngine] Java 21 Vector API detected: SIMD acceleration enabled.");
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
        if (VECTOR_API_AVAILABLE && VECTOR_KERNEL_CONSTRUCTOR != null) {
            try {
                return (FieldKernel) VECTOR_KERNEL_CONSTRUCTOR.invoke(worldSeed, config);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[GeoEngine] SIMD instantiation failed; falling back to Scalar.", t);
            }
        }
        return new ScalarFieldKernel(worldSeed, config);
    }
}
