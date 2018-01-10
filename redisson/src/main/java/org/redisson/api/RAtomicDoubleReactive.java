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

import org.reactivestreams.Publisher;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RAtomicDoubleReactive extends RExpirableReactive {

    Publisher<Boolean> compareAndSet(double expect, double update);

    Publisher<Double> addAndGet(double delta);

    Publisher<Double> decrementAndGet();

    Publisher<Double> get();

    Publisher<Double> getAndAdd(double delta);

    Publisher<Double> getAndSet(double newValue);

    Publisher<Double> incrementAndGet();

    Publisher<Double> getAndIncrement();

    Publisher<Double> getAndDecrement();

    Publisher<Void> set(double newValue);

}
