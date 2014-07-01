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

import org.redisson.misc.ReclosableLatch;

public class RedissonCountDownLatchEntry {

    private int counter;

    private final ReclosableLatch latch;
    private final Promise<Boolean> promise;
    
    public RedissonCountDownLatchEntry(RedissonCountDownLatchEntry source) {
        counter = source.counter;
        latch = source.latch;
        promise = source.promise;
    }
    
    public RedissonCountDownLatchEntry(Promise<Boolean> promise) {
        super();
        this.latch = new ReclosableLatch();
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
    
    public ReclosableLatch getLatch() {
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
        RedissonCountDownLatchEntry other = (RedissonCountDownLatchEntry) obj;
        if (counter != other.counter)
            return false;
        return true;
    }
    
}
