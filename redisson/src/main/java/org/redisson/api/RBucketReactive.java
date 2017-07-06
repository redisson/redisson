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

import org.reactivestreams.Publisher;


/**
 * Object holder. Max size of object is 512MB
 *
 * @author Nikita Koksharov
 *
 * @param <V> - the type of object
 */
public interface RBucketReactive<V> extends RExpirableReactive {

    /**
     * Returns size of object in bytes
     * 
     * @return object size
     */
    Publisher<Long> size();
    
    Publisher<Boolean> trySet(V value);

    Publisher<Boolean> trySet(V value, long timeToLive, TimeUnit timeUnit);

    Publisher<Boolean> compareAndSet(V expect, V update);

    Publisher<V> getAndSet(V newValue);

    Publisher<V> get();

    Publisher<Void> set(V value);

    Publisher<Void> set(V value, long timeToLive, TimeUnit timeUnit);

}
