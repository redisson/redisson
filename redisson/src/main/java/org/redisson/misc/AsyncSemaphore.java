/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AsyncSemaphore {

    private final AtomicInteger counter;
    private final Queue<CompletableFuture<Void>> listeners = new ConcurrentLinkedQueue<>();

    public AsyncSemaphore(int permits) {
        counter = new AtomicInteger(permits);
    }
    
    public boolean tryAcquire(long timeoutMillis) {
        CompletableFuture<Void> f = acquire();
        try {
            f.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        } catch (TimeoutException e) {
            return false;
        }
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
        tryRun();
        return future;
    }

    public void acquire(Runnable listener) {
        acquire().thenAccept(r -> listener.run());
    }

    private void tryRun() {
        while (true) {
            if (counter.decrementAndGet() >= 0) {
                CompletableFuture<Void> future = listeners.poll();
                if (future == null) {
                    counter.incrementAndGet();
                    return;
                }

                if (future.complete(null)) {
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
        tryRun();
    }

    @Override
    public String toString() {
        return "value:" + counter + ":queue:" + queueSize();
    }
    
    
    
}
