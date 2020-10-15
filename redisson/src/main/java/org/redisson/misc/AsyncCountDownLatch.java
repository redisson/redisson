package org.redisson.misc;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class AsyncCountDownLatch {

    private final AtomicInteger commandsSent = new AtomicInteger();
    private volatile Runnable callback;

    public void countDown() {
        if (commandsSent.incrementAndGet() == 0) {
            callback.run();
        }
    }

    public void latch(Runnable callback, int count) {
        if (this.callback != null) {
            throw new IllegalStateException("Latch can't be called twice");
        }
        this.callback = callback;
        if (commandsSent.addAndGet(-count) == 0) {
            callback.run();
        }
    }
}
