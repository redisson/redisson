/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public final class AsyncSemaphore {

    private final ExecutorService executorService;
    private final AtomicInteger tasksLatch = new AtomicInteger(1);
    private final AtomicInteger stackSize = new AtomicInteger();

    private final AtomicInteger counter;
    private final Queue<CompletableFuture<Void>> listeners = new ConcurrentLinkedQueue<>();

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
        listeners.clear();
    }

    public CompletableFuture<Void> acquire() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        listeners.add(future);
        tryForkAndRun();
        return future;
    }

    private void tryForkAndRun() {
        if (executorService != null) {
            int val = tasksLatch.get();
            if (stackSize.get() > 100 * val
                    && tasksLatch.compareAndSet(val, val+1)) {
                executorService.submit(() -> {
                    tasksLatch.decrementAndGet();
                    tryRun();
                });
                return;
            }
        }

        tryRun();
    }

    private void tryRun() {
        while (true) {
            if (counter.decrementAndGet() >= 0) {
                CompletableFuture<Void> future = listeners.poll();
                if (future == null) {
                    counter.incrementAndGet();
                    return;
                }

                boolean complete;
                if (executorService != null) {
                    stackSize.incrementAndGet();
                    complete = future.complete(null);
                    stackSize.decrementAndGet();
                } else {
                    complete = future.complete(null);
                }
                if (complete) {
                    return;
                }
            }

            if (counter.incrementAndGet() <= 0) {
                return;
            }
        }
    }

    public int getCounter() {
        return counter.get();
    }

    public void release() {
        counter.incrementAndGet();
        tryForkAndRun();
    }

    @Override
    public String toString() {
        return "value:" + counter + ":queue:" + queueSize();
    }
    
    
    
}
