package com.geoengine.core.simd;

import com.geoengine.core.math.FieldKernel;
import com.geoengine.core.math.GeoConfig;
import com.geoengine.core.math.ScalarFieldKernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Selects the available terrain-field kernel implementation at runtime.
 *
 * <p>The vectorized implementation is loaded reflectively so that GeoEngine can initialize
 * without requiring the Java Vector API to be active. If vector-kernel discovery or construction
 * fails, the scalar implementation is used instead.
 */
public final class KernelProvider {
    /** Logger used for kernel capability detection and fallback diagnostics. */
    private static final Logger LOGGER = Logger.getLogger("GeoEngine-Core");

    /** Whether the Java Vector API and vector-kernel constructor were successfully discovered. */
    private static final boolean VECTOR_API_AVAILABLE;

    /** Constructor handle for {@link VectorFieldKernel}, when the vector implementation is available. */
    private static final MethodHandle VECTOR_KERNEL_CONSTRUCTOR;

    static {
        boolean available = false;
        MethodHandle constructor = null;

        try {
            Class.forName(
                "jdk.incubator.vector.DoubleVector",
                false,
                KernelProvider.class.getClassLoader()
            );

            Class<?> vectorKernelClass = Class.forName(
                "com.geoengine.core.simd.VectorFieldKernel",
                true,
                KernelProvider.class.getClassLoader()
            );

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            constructor = lookup.findConstructor(
                vectorKernelClass, MethodType.methodType(void.class, long.class, GeoConfig.class)
            );
            available = true;
            LOGGER.info("[GeoEngine] Java 21 Vector API detected: SIMD acceleration enabled.");
        } catch (Throwable t) {
            LOGGER.log(
                Level.INFO,
                "[GeoEngine] Incubator Vector API not active. Running on Scalar reference authority."
            );
            available = false;
            constructor = null;
        }

        VECTOR_API_AVAILABLE = available;
        VECTOR_KERNEL_CONSTRUCTOR = constructor;
    }

    private KernelProvider() {}

    /**
     * Returns whether the vectorized kernel was successfully discovered during class initialization.
     *
     * @return {@code true} when the Vector API and vector-kernel constructor are available
     */
    public static boolean isVectorApiAvailable() {
        return VECTOR_API_AVAILABLE;
    }

    /**
     * Creates the preferred field-kernel implementation for the current runtime.
     *
     * <p>The vectorized implementation is attempted when capability detection succeeded. Failure
     * during vector-kernel construction does not prevent kernel creation; the scalar implementation
     * is returned instead.
     *
     * @param worldSeed world seed used to initialize the kernel
     * @param config terrain-generation configuration
     * @return a vectorized kernel when available and successfully constructed; otherwise a scalar
     *     kernel
     */
    public static FieldKernel createKernel(long worldSeed, GeoConfig config) {
        if (VECTOR_API_AVAILABLE && VECTOR_KERNEL_CONSTRUCTOR != null) {
            try {
                return (FieldKernel) VECTOR_KERNEL_CONSTRUCTOR.invoke(worldSeed, config);
            } catch (Throwable t) {
                LOGGER.log(
                    Level.WARNING,
                    "[GeoEngine] SIMD instantiation failed; falling back to Scalar.",
                    t
                );
            }
        }
        return new ScalarFieldKernel(worldSeed, config);
    }
}
