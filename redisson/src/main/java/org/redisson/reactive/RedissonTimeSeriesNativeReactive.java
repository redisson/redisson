/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.reactive;

import org.redisson.RedissonTimeSeriesNative;
import org.redisson.api.tsnative.TSRangeArgs;
import org.redisson.api.tsnative.TSSample;
import reactor.core.publisher.Flux;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesNativeReactive {

    private final CommandReactiveExecutor commandExecutor;
    private final RedissonTimeSeriesNative instance;

    public RedissonTimeSeriesNativeReactive(CommandReactiveExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.instance = new RedissonTimeSeriesNative(commandExecutor, name);
    }

    public Flux<TSSample> iterator() {
        return iterator(10);
    }

    public Flux<TSSample> iterator(int count) {
        return window(0, count);
    }

    private Flux<TSSample> window(long from, int count) {
        return commandExecutor.<java.util.List<TSSample>>reactive(
                    () -> instance.rangeAsync(TSRangeArgs.from(from).count(count)))
                .flatMapMany(samples -> {
                    if (samples.isEmpty()) {
                        return Flux.empty();
                    }
                    Flux<TSSample> emitted = Flux.fromIterable(samples);
                    if (samples.size() < count) {
                        return emitted;
                    }
                    long next = samples.get(samples.size() - 1).getTimestamp() + 1;
                    return emitted.concatWith(Flux.defer(() -> window(next, count)));
                });
    }

}
