package com.geoengine.core.memory;

/**
 * Provides one reusable {@link WorkerScratchpad} per executing thread.
 *
 * <p>The thread-local storage prevents scratchpad instances from being shared between concurrent
 * workers while allowing repeated terrain evaluations on the same thread to reuse their temporary
 * buffers.
 */
public final class ScratchpadProvider {

    /** Thread-local scratchpad storage used by terrain-generation workers. */
    private static final ThreadLocal<WorkerScratchpad> THREAD_LOCAL_SCRATCHPAD =
        ThreadLocal.withInitial(WorkerScratchpad::new);

    private ScratchpadProvider() {}

    /**
     * Returns the scratchpad associated with the current thread.
     *
     * <p>The scratchpad is created lazily on the first call from a thread and reused by subsequent
     * calls from that thread.
     *
     * @return the current thread's reusable worker scratchpad
     */
    public static WorkerScratchpad get() {
        return THREAD_LOCAL_SCRATCHPAD.get();
    }
}
