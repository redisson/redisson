/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import io.netty.util.concurrent.Promise;

import java.util.concurrent.Semaphore;

public class RedissonLockEntry {

    private int counter;

    private final Semaphore latch;
    private final Promise<Boolean> promise;
    
    public RedissonLockEntry(RedissonLockEntry source) {
        counter = source.counter;
        latch = source.latch;
        promise = source.promise;
    }
    
    public RedissonLockEntry(Promise<Boolean> promise) {
        super();
        this.latch = new Semaphore(1);
        this.promise = promise;
    }
    
    public boolean isFree() {
        return counter == 0;
    }
    
    public void aquire() {
        counter++;
    }
    
    public void release() {
        counter--;
    }
    
    public Promise<Boolean> getPromise() {
        return promise;
    }
    
    public Semaphore getLatch() {
        return latch;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + counter;
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
        RedissonLockEntry other = (RedissonLockEntry) obj;
        if (counter != other.counter)
            return false;
        return true;
    }
    
}
