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
package org.redisson;

import org.redisson.misc.ReclosableLatch;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RedissonCountDownLatchEntry implements PubSubEntry<RedissonCountDownLatchEntry> {

    private int counter;

    private final ReclosableLatch latch;
    private final CompletableFuture<RedissonCountDownLatchEntry> promise;
    private final ConcurrentLinkedQueue<Runnable> listeners = new ConcurrentLinkedQueue<>();

    public RedissonCountDownLatchEntry(CompletableFuture<RedissonCountDownLatchEntry> promise) {
        super();
        this.latch = new ReclosableLatch();
        this.promise = promise;
    }

    public void acquire() {
        counter++;
    }
    public void acquire(int permits) {
        counter+=permits;
    }

    public int release() {
        return --counter;
    }

    public CompletableFuture<RedissonCountDownLatchEntry> getPromise() {
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

    public ReclosableLatch getLatch() {
        return latch;
    }

}
