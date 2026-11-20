package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(10)
public class ReclosableLatchTest {

    private static final int WAITERS = 16;

    private interface Wait {

        void at(ReclosableLatch latch) throws InterruptedException;

    }

    @Test
    public void testStartsClosed() throws InterruptedException {
        ReclosableLatch latch = new ReclosableLatch();

        long start = System.nanoTime();
        assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isFalse();
        // false alone proves little: a wait that gives up at once returns false as well
        assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(100));
        assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isFalse();
        // hours, so that a latch that drops the sign waits until the class timeout instead of passing late
        assertThat(latch.await(-1, TimeUnit.HOURS)).isFalse();
    }

    @Test
    public void testOpenReleasesEveryWaiter() throws InterruptedException {
        ReclosableLatch latch = new ReclosableLatch();
        AtomicInteger released = new AtomicInteger();
        List<Thread> waiters = park(latch, released);

        latch.open();

        join(waiters);
        assertThat(released).hasValue(WAITERS);
    }

    @Test
    public void testOpenStaysOpen() throws InterruptedException {
        ReclosableLatch latch = new ReclosableLatch();

        latch.open();
        latch.open();

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.await(0, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(latch.await(-1, TimeUnit.HOURS)).isTrue();
        latch.await();
    }

    @Test
    public void testCloseShutsAnOpenLatch() throws InterruptedException {
        ReclosableLatch latch = new ReclosableLatch();
        latch.open();

        latch.close();
        latch.close();

        assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isFalse();
        latch.open();
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testCloseOnAClosedLatchKeepsItsWaiters() throws InterruptedException {
        ReclosableLatch latch = new ReclosableLatch();
        AtomicInteger released = new AtomicInteger();
        List<Thread> waiters = park(latch, released);

        // closing a latch that is closed already must change nothing for the threads waiting at it
        latch.close();
        latch.open();

        join(waiters);
        assertThat(released).hasValue(WAITERS);
    }

    @Test
    public void testOpenThenCloseStrandsNoWaiter() throws InterruptedException {
        ReclosableLatch latch = new ReclosableLatch();
        AtomicInteger released = new AtomicInteger();
        List<Thread> waiters = park(latch, released);

        // No pause in between. How many of the waiters get through before the latch closes again
        // is left open on purpose: an implementation may let through none of them or all of them.
        latch.open();
        latch.close();
        assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isFalse();

        // whichever of them still wait must not have been lost
        latch.open();

        join(waiters);
        assertThat(released).hasValue(WAITERS);
    }

    @Test
    public void testOpenEndsATimedWait() throws InterruptedException {
        ReclosableLatch latch = new ReclosableLatch();
        AtomicBoolean opened = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try {
                opened.set(latch.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        awaitState(Thread.State.TIMED_WAITING, waiter);

        latch.open();

        waiter.join(10_000);
        assertThat(opened).isTrue();
    }

    @Test
    public void testAwaitIsInterruptible() throws InterruptedException {
        assertInterrupted(latch -> latch.await(), Thread.State.WAITING);
    }

    @Test
    public void testTimedAwaitIsInterruptible() throws InterruptedException {
        assertInterrupted(latch -> latch.await(30, TimeUnit.SECONDS), Thread.State.TIMED_WAITING);
    }

    @Test
    public void testInterruptBeforeTheCallIsReported() throws InterruptedException {
        ReclosableLatch closed = new ReclosableLatch();
        ReclosableLatch open = new ReclosableLatch();
        open.open();

        assertInterruptBeforeTheCallIsReported(closed, latch -> latch.await());
        assertInterruptBeforeTheCallIsReported(closed, latch -> latch.await(30, TimeUnit.SECONDS));
        // an open latch has nothing to wait for, and still must not swallow the interrupt
        assertInterruptBeforeTheCallIsReported(open, latch -> latch.await());
        assertInterruptBeforeTheCallIsReported(open, latch -> latch.await(30, TimeUnit.SECONDS));
    }

    // On a thread of its own: a latch that does not report the interrupt would block the test thread
    // at the closed latch, and one that also ignores later interrupts could not be stopped by the timeout.
    private static void assertInterruptBeforeTheCallIsReported(ReclosableLatch latch, Wait wait)
            throws InterruptedException {
        AtomicBoolean reported = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                wait.at(latch);
            } catch (InterruptedException e) {
                reported.set(true);
            }
        });
        caller.setDaemon(true);
        caller.start();

        caller.join(10_000);
        assertThat(reported).isTrue();
    }

    private static void assertInterrupted(Wait wait, Thread.State parked) throws InterruptedException {
        ReclosableLatch latch = new ReclosableLatch();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try {
                wait.at(latch);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        waiter.setDaemon(true);
        waiter.start();
        awaitState(parked, waiter);

        waiter.interrupt();

        waiter.join(10_000);
        assertThat(interrupted).isTrue();
    }

    // Returns once every thread is parked at the latch. A parked thread has read the latch's
    // state already, so what the test does to the latch next is what that thread has to cope with.
    private static List<Thread> park(ReclosableLatch latch, AtomicInteger released) {
        List<Thread> waiters = new ArrayList<>();
        for (int i = 0; i < WAITERS; i++) {
            Thread waiter = new Thread(() -> {
                try {
                    latch.await();
                    released.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            waiter.setDaemon(true);
            waiter.start();
            waiters.add(waiter);
        }
        awaitState(Thread.State.WAITING, waiters.toArray(new Thread[0]));
        return waiters;
    }

    private static void awaitState(Thread.State state, Thread... threads) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(1)).until(() -> {
            for (Thread thread : threads) {
                if (thread.getState() != state) {
                    return false;
                }
            }
            return true;
        });
    }

    private static void join(List<Thread> waiters) throws InterruptedException {
        for (Thread waiter : waiters) {
            waiter.join(10_000);
            assertThat(waiter.isAlive()).isFalse();
        }
    }

}
