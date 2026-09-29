package com.omms.geoenginecore.test.fixtures;

import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;

/**
 * Per-thread allocation probe (TECHSPEC §164).
 *
 * <p>Uses {@code ThreadMXBean.getThreadAllocatedBytes} to measure
 * bytes allocated by the current thread between {@link #start()} and
 * {@link #stop()}, giving a per-worker allocation signal for the
 * zero-allocation contracts without adding hot-path overhead.
 */
public final class AllocationProbe {
    /** Thread MXBean with allocation profiling. */
    private static final ThreadMXBean THREAD_BEAN = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    /** Allocation watermark captured at start(), in bytes. */
    private long startBytes;

    /**
     * Captures the allocation watermark.
     *
     * @throws UnsupportedOperationException if the JVM lacks thread
     *     allocation profiling
     */
    public void start() {
        if (!THREAD_BEAN.isThreadAllocatedMemorySupported()) {
            throw new UnsupportedOperationException("Thread allocated memory profiling not supported by JVM");
        }
        startBytes = THREAD_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    /**
     * @return bytes allocated since start()
     */
    public long stop() {
        long endBytes = THREAD_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
        return Math.max(0, endBytes - startBytes);
    }
}
