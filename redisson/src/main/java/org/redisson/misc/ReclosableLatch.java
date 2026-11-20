/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.misc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A gate that threads wait at until it is opened, and that can be closed again.
 * A new latch is closed.
 *
 * @author Nikita Koksharov
 *
 */
public final class ReclosableLatch {

    private final AtomicReference<CountDownLatch> gate = new AtomicReference<>(new CountDownLatch(1));

    /**
     * Opens the latch and releases the threads waiting at it.
     * The latch stays open until {@link #close()} is called:
     * a thread that waits in the meantime returns at once.
     * Does nothing if the latch is open already.
     */
    public void open() {
        gate.get().countDown();
    }

    /**
     * Closes the latch: a thread that waits from now on is held until the next {@link #open()}.
     * Does nothing if the latch is closed already, and nothing to the threads waiting at it.
     * <p>
     * Called straight after {@link #open()}, it may come before the released threads have run.
     * How many of them still get through is not specified.
     */
    public void close() {
        CountDownLatch current = gate.get();
        if (current.getCount() == 0) {
            gate.compareAndSet(current, new CountDownLatch(1));
        }
    }

    /**
     * Waits until the latch is open. Returns at once if it is open already.
     *
     * @throws InterruptedException if the current thread is interrupted while it waits, or was
     *         interrupted before the call; the latter is reported at an open latch too
     */
    public void await() throws InterruptedException {
        gate.get().await();
    }

    /**
     * Waits until the latch is open, but no longer than the given time.
     * Returns at once if the latch is open already.
     *
     * @param time the longest time to wait; with zero or less the latch is only tested
     * @param unit the unit of <code>time</code>
     * @return <code>true</code> if the latch was open or was opened within the time and
     *         <code>false</code> if the time passed first
     * @throws InterruptedException if the current thread is interrupted while it waits, or was
     *         interrupted before the call; the latter is reported at an open latch too
     */
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        return gate.get().await(time, unit);
    }

}
