/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.pubsub;

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
    private final Queue<Runnable> listeners = new ConcurrentLinkedQueue<>();
    private final Set<Runnable> removedListeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public AsyncSemaphore(int permits) {
        counter = new AtomicInteger(permits);
    }
    
    public boolean tryAcquire(long timeoutMillis) {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable runnable = () -> latch.countDown();
        acquire(runnable);
        
        try {
            boolean r = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!r) {
                remove(runnable);
            }
            return r;
        } catch (InterruptedException e) {
            remove(runnable);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public int queueSize() {
        return listeners.size() - removedListeners.size();
    }
    
    public void removeListeners() {
        listeners.clear();
        removedListeners.clear();
    }
    
    public void acquire(Runnable listener) {
        listeners.add(listener);
        tryRun();
    }

    private void tryRun() {
        if (counter.get() == 0
                || listeners.peek() == null) {
            return;
        }

        if (counter.decrementAndGet() >= 0) {
            Runnable listener = listeners.poll();
            if (listener == null) {
                counter.incrementAndGet();
                return;
            }

            if (removedListeners.remove(listener)) {
                counter.incrementAndGet();
                tryRun();
            } else {
                listener.run();
            }
        } else {
            counter.incrementAndGet();
        }
    }

    public void remove(Runnable listener) {
        removedListeners.add(listener);
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
