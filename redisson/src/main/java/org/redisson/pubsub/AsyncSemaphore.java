/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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

    private static class Entry {
        
        private final Runnable runnable;
        private final int permits;
        
        Entry(Runnable runnable, int permits) {
            super();
            this.runnable = runnable;
            this.permits = permits;
        }
        
        public int getPermits() {
            return permits;
        }
        
        public Runnable getRunnable() {
            return runnable;
        }

    }

    private final AtomicInteger counter;
    private final Queue<Entry> listeners = new ConcurrentLinkedQueue<>();
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
        acquire(listener, 1);
    }

    public void acquire(Runnable listener, int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits should be non-zero");
        }
        listeners.add(new Entry(listener, permits));
        tryRun(1);
    }

    private void tryRun(int permits) {
        if (counter.get() == 0
                || listeners.peek() == null) {
            return;
        }

        if (counter.addAndGet(-permits) >= 0) {
            Entry e = listeners.peek();
            if (e == null) {
                counter.addAndGet(permits);
                return;
            }
            if (e.getPermits() != permits) {
                counter.addAndGet(permits);
                tryRun(e.getPermits());
                return;
            }
            Entry entry = listeners.poll();
            if (entry == null) {
                counter.addAndGet(permits);
                return;
            }

            if (removedListeners.remove(entry.getRunnable())) {
                counter.addAndGet(entry.getPermits());
                tryRun(1);
            } else {
                entry.runnable.run();
            }
        } else {
            counter.addAndGet(permits);
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
        tryRun(1);
    }

    @Override
    public String toString() {
        return "value:" + counter + ":queue:" + queueSize();
    }
    
    
    
}
