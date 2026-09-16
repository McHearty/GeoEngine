package com.geoengine.core.memory;

public final class ScratchpadProvider {
    private static final ThreadLocal<WorkerScratchpad> THREAD_LOCAL_SCRATCHPAD =
        ThreadLocal.withInitial(WorkerScratchpad::new);

    private ScratchpadProvider() {}

    public static WorkerScratchpad get() {
        return THREAD_LOCAL_SCRATCHPAD.get();
    }
}
