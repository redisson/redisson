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
package org.redisson;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.redisson.misc.RPromise;

public class RedissonLockEntry implements PubSubEntry<RedissonLockEntry> {

    private int counter;

    private final Semaphore latch;
    private final RPromise<RedissonLockEntry> promise;
    private final ConcurrentLinkedQueue<Runnable> listeners = new ConcurrentLinkedQueue<Runnable>();

    public RedissonLockEntry(RPromise<RedissonLockEntry> promise) {
        super();
        this.latch = new Semaphore(0);
        this.promise = promise;
    }

    public void aquire() {
        counter++;
    }

    public int release() {
        return --counter;
    }

    public RPromise<RedissonLockEntry> getPromise() {
        return promise;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public boolean removeListener(Runnable listener) {
        return listeners.remove(listener);
    }

    public ConcurrentLinkedQueue<Runnable> getListeners() {
        return listeners;
    }

    public Semaphore getLatch() {
        return latch;
    }

}
