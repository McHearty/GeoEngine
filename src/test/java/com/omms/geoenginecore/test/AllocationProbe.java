package com.omms.geoenginecore.test;

import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;

public final class AllocationProbe {
    private static final ThreadMXBean THREAD_BEAN = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    private long startBytes;

    public void start() {
        if (!THREAD_BEAN.isThreadAllocatedMemorySupported()) {
            throw new UnsupportedOperationException("Thread allocated memory profiling not supported by JVM");
        }
        startBytes = THREAD_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    public long stop() {
        long endBytes = THREAD_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
        return Math.max(0, endBytes - startBytes);
    }
}
