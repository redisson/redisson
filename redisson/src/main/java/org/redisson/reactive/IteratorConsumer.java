/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * @author Nikita Koksharov
 */
public abstract class IteratorConsumer<V> implements LongConsumer {

    private final FluxSink<V> emitter;

    private String nextIterPos = "0";
    private RedisClient client;

    private final AtomicLong requested = new AtomicLong();

    public IteratorConsumer(FluxSink<V> emitter) {
        this.emitter = emitter;
    }

    @Override
    public void accept(long value) {
        if (requested.addAndGet(value) == value) {
            nextValues();
        }
    }

    protected void nextValues() {
        scanIterator(client, nextIterPos).whenComplete((res, e) -> {
            if (e != null) {
                emitter.error(e);
                return;
            }

            client = res.getRedisClient();
            nextIterPos = res.getPos();

            for (Object val : res.getValues()) {
                Object v = transformValue(val);
                emitter.next((V) v);
                requested.decrementAndGet();
            }

            if ("0".equals(nextIterPos) && !tryAgain()) {
                emitter.complete();
                return;
            }

            nextValues();
        });
    }

    protected Object transformValue(Object value) {
        return value;
    }

    protected abstract boolean tryAgain();

    protected abstract RFuture<ScanResult<Object>> scanIterator(RedisClient client, String nextIterPos);

}
