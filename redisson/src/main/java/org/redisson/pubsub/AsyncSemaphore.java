/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AsyncSemaphore {

    private static class Entry {
        
        private Runnable runnable;
        private int permits;
        
        public Entry(Runnable runnable, int permits) {
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

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((runnable == null) ? 0 : runnable.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Entry other = (Entry) obj;
            if (runnable == null) {
                if (other.runnable != null)
                    return false;
            } else if (!runnable.equals(other.runnable))
                return false;
            return true;
        }
        
        
    }
    
    private int counter;
    private final Set<Entry> listeners = new LinkedHashSet<Entry>();

    public AsyncSemaphore(int permits) {
        counter = permits;
    }
    
    public boolean tryAcquire(long timeoutMillis) {
        final CountDownLatch latch = new CountDownLatch(1);
        final Runnable listener = new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        };
        acquire(listener);
        
        try {
            boolean res = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!res) {
                if (!remove(listener)) {
                    release();
                }
            }
            return res;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!remove(listener)) {
                release();
            }
            return false;
        }
    }

    public int queueSize() {
        synchronized (this) {
            return listeners.size();
        }
    }
    
    public void removeListeners() {
        synchronized (this) {
            listeners.clear();
        }
    }
    
    public void acquire(Runnable listener) {
        acquire(listener, 1);
    }
    
    public void acquire(Runnable listener, int permits) {
        boolean run = false;
        
        synchronized (this) {
            if (counter < permits) {
                listeners.add(new Entry(listener, permits));
                return;
            } else {
                counter -= permits;
                run = true;
            }
        }
        
        if (run) {
            listener.run();
        }
    }
    
    public boolean remove(Runnable listener) {
        synchronized (this) {
            return listeners.remove(new Entry(listener, 0));
        }
    }

    public int getCounter() {
        return counter;
    }
    
    public void release() {
        Entry entryToAcquire = null;
        
        synchronized (this) {
            counter++;
            Iterator<Entry> iter = listeners.iterator();
            if (iter.hasNext()) {
                Entry entry = iter.next();
                if (entry.getPermits() >= counter) {
                    iter.remove();
                    entryToAcquire = entry;
                }
            }
        }
        
        if (entryToAcquire != null) {
            acquire(entryToAcquire.getRunnable(), entryToAcquire.getPermits());
        }
    }

    @Override
    public String toString() {
        return "value:" + counter + ":queue:" + queueSize();
    }
    
    
    
}
