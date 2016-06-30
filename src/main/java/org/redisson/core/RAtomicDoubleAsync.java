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

public interface RAtomicDoubleAsync extends RExpirableAsync {

    Future<Boolean> compareAndSetAsync(double expect, double update);

    Future<Double> addAndGetAsync(double delta);

    Future<Double> decrementAndGetAsync();

    Future<Double> getAsync();

    Future<Double> getAndAddAsync(double delta);

    Future<Double> getAndSetAsync(double newValue);

    Future<Double> incrementAndGetAsync();

    Future<Double> getAndIncrementAsync();

    Future<Double> getAndDecrementAsync();

    Future<Void> setAsync(double newValue);

}
