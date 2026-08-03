package org.redisson.renewal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReadLockEntryTest {

    @Test
    void shouldHaveNoThreadsAfterNonReentrantAcquireAndRelease() {
        ReadLockEntry entry = new ReadLockEntry();
        long threadId = 1L;

        entry.addThreadId(threadId, "lock", "prefix");
        entry.removeThreadId(threadId);

        assertThat(entry.hasNoThreads()).isTrue();
        assertThat(entry.getFirstThreadId()).isNull();
    }

    @Test
    void shouldHaveNoThreadsAfterReentrantAcquireAndRelease() {
        ReadLockEntry entry = new ReadLockEntry();
        long threadId = 1L;

        // read lock acquired reentrantly by the same thread
        entry.addThreadId(threadId, "lock", "prefix");
        entry.addThreadId(threadId, "lock", "prefix");

        // and fully released
        entry.removeThreadId(threadId);
        entry.removeThreadId(threadId);

        // every acquire pushes the thread id onto the queue, so a full release
        // must drop all occurrences - otherwise the renewal entry leaks and the
        // watchdog keeps renewing a lock that is no longer held
        assertThat(entry.hasNoThreads()).isTrue();
        assertThat(entry.getFirstThreadId()).isNull();
    }
}
