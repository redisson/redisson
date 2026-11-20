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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class AsyncSemaphore {

    /**
     * A waiter that a thread completes at this moment. If a callback of this waiter calls
     * release() or acquire() on the same semaphore and thread, the call does not start the
     * next hand-off inside the callback. The call is counted here, and tryRun() starts that
     * hand-off after the callback returns. So the stack does not grow with the number of
     * waiters.
     */
    private static final class HandOff {

        private final AsyncSemaphore semaphore;
        private final HandOff outer;
        private int runs;

        HandOff(AsyncSemaphore semaphore, HandOff outer) {
            this.semaphore = semaphore;
            this.outer = outer;
        }
    }

    private static final ThreadLocal<HandOff> HAND_OFF = new ThreadLocal<>();

    private final ExecutorService executorService;
    private final AtomicInteger tasksLatch = new AtomicInteger(1);
    private final AtomicInteger stackSize = new AtomicInteger();

    private final AtomicInteger counter;
    private final FastRemovalQueue<CompletableFuture<Void>> listeners = new FastRemovalQueue<>();

    public AsyncSemaphore(int permits) {
        this(permits, null);
    }

    public AsyncSemaphore(int permits, ExecutorService executorService) {
        counter = new AtomicInteger(permits);
        this.executorService = executorService;
    }

    public int queueSize() {
        return listeners.size();
    }
    
    public void removeListeners() {
        CompletableFuture<Void> future;
        while ((future = listeners.poll()) != null) {
            future.completeExceptionally(new CancellationException("AsyncSemaphore listeners have been removed"));
        }
    }

    public CompletableFuture<Void> acquire() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        HandOff handOff = handOffOfCurrentThread();
        boolean behindOthers = handOff != null && !listeners.isEmpty();
        listeners.add(future);
        future.whenComplete((r, e) -> {
            if (e != null) {
                listeners.remove(future);
            }
        });
        if (behindOthers) {
            handOff.runs++;
            return future;
        }
        tryForkAndRun();
        return future;
    }

    private HandOff handOffOfCurrentThread() {
        for (HandOff handOff = HAND_OFF.get(); handOff != null; handOff = handOff.outer) {
            if (handOff.semaphore == this) {
                return handOff;
            }
        }
        return null;
    }

    private void tryForkAndRun() {
        if (!tryFork(0)) {
            tryRun();
        }
    }

    /**
     * Moves one tryRun() call to the executor, if hand-offs are deep enough.
     *
     * @param chain hand-offs that the caller made one after another before this one.
     *              Nested hand-offs were in stackSize. A chain is a loop now, so its length
     *              comes from the loop, and a chain goes to the executor at the same length.
     * @return true if the executor took the call
     */
    private boolean tryFork(int chain) {
        if (executorService != null) {
            int val = tasksLatch.get();
            if (stackSize.get() + chain > 25 * val
                    && tasksLatch.compareAndSet(val, val+1)) {
                try {
                    executorService.submit(() -> {
                        tasksLatch.decrementAndGet();
                        tryRun();
                    });
                    return true;
                } catch (RejectedExecutionException e) {
                    tasksLatch.decrementAndGet();
                }
            }
        }
        return false;
    }

    private void tryRun() {
        int chain = 0;
        int runs = 0;
        while (true) {
            if (counter.decrementAndGet() >= 0) {
                CompletableFuture<Void> future = listeners.poll();
                if (future == null) {
                    counter.incrementAndGet();
                    if (listeners.isEmpty()) {
                        return;
                    }
                    continue;
                }

                HandOff handOff = new HandOff(this, HAND_OFF.get());
                HAND_OFF.set(handOff);
                boolean complete;
                try {
                    if (executorService != null) {
                        stackSize.incrementAndGet();
                        complete = future.complete(null);
                        stackSize.decrementAndGet();
                    } else {
                        complete = future.complete(null);
                    }
                } finally {
                    HAND_OFF.set(handOff.outer);
                }
                runs += handOff.runs;

                if (!complete) {
                    counter.incrementAndGet();
                    // dead waiter already gave the permit back above; continue instead of
                    // falling through to the trailing increment, which would double-count it
                    continue;
                }

                chain++;
                do {
                    if (runs == 0) {
                        return;
                    }
                    runs--;
                } while (tryFork(chain));
                continue;
            }

            if (counter.incrementAndGet() <= 0) {
                return;
            }
        }
    }

    public int getCounter() {
        return Math.max(0, counter.get());
    }

    public void release() {
        counter.incrementAndGet();
        HandOff handOff = handOffOfCurrentThread();
        if (handOff == null) {
            tryForkAndRun();
            return;
        }

        if (!listeners.isEmpty()) {
            handOff.runs++;
        }
    }

    @Override
    public String toString() {
        return "value:" + counter + ":queue:" + queueSize();
    }

}