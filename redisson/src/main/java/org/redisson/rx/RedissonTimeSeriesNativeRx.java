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
package org.redisson.rx;

import io.reactivex.rxjava3.core.Flowable;
import org.redisson.RedissonTimeSeriesNative;
import org.redisson.api.tsnative.TSRangeArgs;
import org.redisson.api.tsnative.TSSample;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeriesNativeRx {

    private final CommandRxExecutor commandExecutor;
    private final RedissonTimeSeriesNative instance;

    public RedissonTimeSeriesNativeRx(CommandRxExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.instance = new RedissonTimeSeriesNative(commandExecutor, name);
    }

    public Flowable<TSSample> iterator() {
        return iterator(10);
    }

    public Flowable<TSSample> iterator(int count) {
        return window(0, count);
    }

    private Flowable<TSSample> window(long from, int count) {
        return commandExecutor.<java.util.List<TSSample>>flowable(
                    () -> instance.rangeAsync(TSRangeArgs.from(from).count(count)))
                .concatMap(samples -> {
                    if (samples.isEmpty()) {
                        return Flowable.empty();
                    }
                    Flowable<TSSample> emitted = Flowable.fromIterable(samples);
                    if (samples.size() < count) {
                        return emitted;
                    }
                    long next = samples.get(samples.size() - 1).getTimestamp() + 1;
                    return emitted.concatWith(Flowable.defer(() -> window(next, count)));
                });
    }

}
