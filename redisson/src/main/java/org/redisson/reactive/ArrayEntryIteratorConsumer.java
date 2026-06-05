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

import org.redisson.api.RArray;
import org.redisson.api.array.ArrayEntry;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * Backpressure-aware page consumer for {@link RArray} iteration on the Reactor facade.
 * <p>
 * The array stores values by sparse non-negative index, so iteration is performed via
 * keyset pagination over {@code ARSCAN}: each batch is fetched from the index following
 * the last entry of the previous batch, with {@code count} used as the page size hint.
 * A single in-flight chain is kept regardless of how many times {@code accept(long)} is
 * invoked by upstream request replenishment.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class ArrayEntryIteratorConsumer<V> implements LongConsumer {

    private final FluxSink<ArrayEntry<V>> emitter;
    private final RArray<V> array;
    private final int count;

    private long nextStart;
    private long endBound;
    private boolean endResolved;
    private boolean finished;

    private final AtomicLong requested = new AtomicLong();

    public ArrayEntryIteratorConsumer(FluxSink<ArrayEntry<V>> emitter, RArray<V> array, int count) {
        this.emitter = emitter;
        this.array = array;
        this.count = count;
    }

    @Override
    public void accept(long value) {
        // Single-chain guard: addAndGet(value) == value iff prior counter was 0,
        // i.e. no chain is currently running.
        if (requested.addAndGet(value) == value) {
            nextValues();
        }
    }

    private void nextValues() {
        if (finished) {
            emitter.complete();
            return;
        }
        if (!endResolved) {
            array.lengthAsync().whenComplete((len, e) -> {
                if (e != null) {
                    emitter.error(e);
                    return;
                }
                endBound = len - 1;
                endResolved = true;
                nextValues();
            });
            return;
        }
        if (nextStart > endBound) {
            finished = true;
            emitter.complete();
            return;
        }
        array.scanAsync(nextStart, endBound, count).whenComplete((page, e) -> {
            if (e != null) {
                emitter.error(e);
                return;
            }
            if (page.isEmpty()) {
                finished = true;
                emitter.complete();
                return;
            }
            for (ArrayEntry<V> entry : page) {
                emitter.next(entry);
                requested.decrementAndGet();
            }
            nextStart = lastIndex(page) + 1;
            nextValues();
        });
    }

    private long lastIndex(List<ArrayEntry<V>> page) {
        return page.get(page.size() - 1).getIndex();
    }

}
