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
package org.redisson.jcache.bean;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.cache.management.CacheStatisticsMXBean;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class JCacheStatisticsMXBean implements CacheStatisticsMXBean {

    private final AtomicLong removals = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    
    private final AtomicLong removeTime = new AtomicLong();
    private final AtomicLong getTime = new AtomicLong();
    private final AtomicLong putTime = new AtomicLong();
    
    
    @Override
    public void clear() {
        removals.set(0);
        hits.set(0);
        puts.set(0);
        misses.set(0);
        evictions.set(0);
        
        removeTime.set(0);
        getTime.set(0);
        putTime.set(0);
    }

    public void addHits(long value) {
        hits.addAndGet(value);
    }
    
    @Override
    public long getCacheHits() {
        return hits.get();
    }

    @Override
    public float getCacheHitPercentage() {
        long gets = getCacheGets();
        if (gets == 0) {
            return 0;
        }
        return (getCacheHits() * 100) / (float) gets;
    }

    public void addMisses(long value) {
        misses.addAndGet(value);
    }
    
    @Override
    public long getCacheMisses() {
        return misses.get();
    }

    @Override
    public float getCacheMissPercentage() {
        long gets = getCacheGets();
        if (gets == 0) {
            return 0;
        }
        return (getCacheMisses() * 100) / (float) gets;
    }

    @Override
    public long getCacheGets() {
        return hits.get() + misses.get();
    }

    public void addPuts(long value) {
        puts.addAndGet(value);
    }
    
    @Override
    public long getCachePuts() {
        return puts.get();
    }
    
    public void addRemovals(long value) {
        removals.addAndGet(value);
    }

    @Override
    public long getCacheRemovals() {
        return removals.get();
    }

    public void addEvictions(long value) {
        evictions.addAndGet(value);
    }
    
    @Override
    public long getCacheEvictions() {
        return evictions.get();
    }

    private float get(long value, long timeInNanos) {
        if (value == 0 || timeInNanos == 0) {
            return 0;
        }
        long timeInMicrosec = TimeUnit.NANOSECONDS.toMicros(timeInNanos);
        return timeInMicrosec / value;
    }
    
    public void addGetTime(long value) {
        getTime.addAndGet(value);
    }
    
    @Override
    public float getAverageGetTime() {
        return get(getCacheGets(), getTime.get());
    }

    public void addPutTime(long value) {
        putTime.addAndGet(value);
    }
    
    @Override
    public float getAveragePutTime() {
        return get(getCachePuts(), putTime.get());
    }
    
    public void addRemoveTime(long value) {
        removeTime.addAndGet(value);
    }

    @Override
    public float getAverageRemoveTime() {
        return get(getCachePuts(), removeTime.get());
    }

}
