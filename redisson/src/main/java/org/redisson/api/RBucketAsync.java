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
package org.redisson.api;

import java.util.concurrent.TimeUnit;

/**
 * Async object functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> - the type of object
 */
public interface RBucketAsync<V> extends RExpirableAsync {

    /**
     * Returns size of object in bytes
     * 
     * @return object size
     */
    RFuture<Long> sizeAsync();
    
    RFuture<V> getAsync();

    RFuture<Boolean> trySetAsync(V value);

    RFuture<Boolean> trySetAsync(V value, long timeToLive, TimeUnit timeUnit);

    RFuture<Boolean> compareAndSetAsync(V expect, V update);

    RFuture<V> getAndSetAsync(V newValue);

    RFuture<Void> setAsync(V value);

    RFuture<Void> setAsync(V value, long timeToLive, TimeUnit timeUnit);

}
