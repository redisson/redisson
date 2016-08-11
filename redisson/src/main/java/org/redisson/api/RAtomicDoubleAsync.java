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

public interface RAtomicDoubleAsync extends RExpirableAsync {

    RFuture<Boolean> compareAndSetAsync(double expect, double update);

    RFuture<Double> addAndGetAsync(double delta);

    RFuture<Double> decrementAndGetAsync();

    RFuture<Double> getAsync();

    RFuture<Double> getAndAddAsync(double delta);

    RFuture<Double> getAndSetAsync(double newValue);

    RFuture<Double> incrementAndGetAsync();

    RFuture<Double> getAndIncrementAsync();

    RFuture<Double> getAndDecrementAsync();

    RFuture<Void> setAsync(double newValue);

}
