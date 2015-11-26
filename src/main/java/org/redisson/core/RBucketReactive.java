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
package org.redisson.core;

import java.util.concurrent.TimeUnit;

import rx.Single;

/**
 *  object functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> - the type of object
 */
public interface RBucketReactive<V> extends RExpirableReactive {

    Single<V> get();

    Single<Void> set(V value);

    Single<Void> set(V value, long timeToLive, TimeUnit timeUnit);

    Single<Boolean> exists();

}
