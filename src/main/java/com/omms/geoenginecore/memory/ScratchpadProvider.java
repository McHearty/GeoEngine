package com.omms.geoenginecore.memory;

/**
 * Supplies one {@link WorkerScratchpad} per worker thread.
 *
 * <p>A scratchpad is owned by exactly one thread at a time and must
 * never be shared concurrently (TECHSPEC §74). Backing the provider
 * with a {@code ThreadLocal} means each worker's buffer is allocated
 * once and reused for its whole life, with no synchronization inside
 * the worker (TECHSPEC §62, §73).
 */
public final class ScratchpadProvider {
    /** One private scratchpad per worker thread. */
    private static final ThreadLocal<WorkerScratchpad> THREAD_LOCAL_SCRATCHPAD =
        ThreadLocal.withInitial(WorkerScratchpad::new);

    /** Hides the implicit constructor. This is a static utility class. */
    private ScratchpadProvider() {}

    /**
     * @return the scratchpad owned by the current worker thread
     */
    public static WorkerScratchpad get() {
        return THREAD_LOCAL_SCRATCHPAD.get();
    }
}
