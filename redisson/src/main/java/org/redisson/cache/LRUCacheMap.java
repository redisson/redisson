/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.cache;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * LRU (least recently used) cache.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class LRUCacheMap<K, V> extends AbstractCacheMap<K, V> {

    private final Queue<CachedValue> queue = new ConcurrentLinkedQueue<CachedValue>();
    
    public LRUCacheMap(int size, long timeToLiveInMillis, long maxIdleInMillis) {
        super(size, timeToLiveInMillis, maxIdleInMillis);
    }

    @Override
    protected void onValueCreate(CachedValue value) {
        queue.add(value);
    }
    
    @Override
    protected void onValueRemove(CachedValue value) {
        queue.remove(value);
    }
    
    @Override
    protected void onValueRead(CachedValue value) {
        // move value to tail of queue 
        if (queue.remove(value)) {
            queue.add(value);
        }
    }

    @Override
    protected void onMapFull() {
        CachedValue value = queue.poll();
        if (value != null) {
            map.remove(value.getKey(), value);
        }
    }
    
    @Override
    public void clear() {
        queue.clear();
        super.clear();
    }

}
