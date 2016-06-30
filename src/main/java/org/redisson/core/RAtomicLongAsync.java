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
package org.redisson.core;

import io.netty.util.concurrent.Future;

public interface RAtomicLongAsync extends RExpirableAsync {

    Future<Boolean> compareAndSetAsync(long expect, long update);

    Future<Long> addAndGetAsync(long delta);

    Future<Long> decrementAndGetAsync();

    Future<Long> getAsync();

    Future<Long> getAndAddAsync(long delta);

    Future<Long> getAndSetAsync(long newValue);

    Future<Long> incrementAndGetAsync();

    Future<Long> getAndIncrementAsync();

    Future<Long> getAndDecrementAsync();

    Future<Void> setAsync(long newValue);

}
