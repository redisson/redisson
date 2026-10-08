package org.redisson.renewal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counter semantics used when watchdog is registered before SET and cancelled
 * on failed acquire (see #7272 pre-schedule fix).
 */
public class LockEntryTest {

    @Test
    public void speculativeRegisterThenCancelLeavesNoThreads() {
        LockEntry entry = new LockEntry();
        long threadId = 42L;

        // pre-schedule before SET
        entry.addThreadId(threadId, "lock:42");
        assertThat(entry.hasNoThreads()).isFalse();

        // acquire failed → undo
        entry.removeThreadId(threadId);
        assertThat(entry.hasNoThreads()).isTrue();
    }

    @Test
    public void reentrantAcquireAndFullReleaseClearsEntry() {
        LockEntry entry = new LockEntry();
        long threadId = 7L;

        entry.addThreadId(threadId, "lock:7");
        entry.addThreadId(threadId, "lock:7");
        entry.removeThreadId(threadId);
        assertThat(entry.hasNoThreads()).isFalse();
        entry.removeThreadId(threadId);
        assertThat(entry.hasNoThreads()).isTrue();
    }

}
