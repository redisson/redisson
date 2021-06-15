/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import java.util.function.Consumer;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class SetReactiveIterator<V> implements Consumer<FluxSink<V>> {

    @Override
    public void accept(FluxSink<V> emitter) {
        emitter.onRequest(new IteratorConsumer<V>(emitter) {
            @Override
            protected boolean tryAgain() {
                return SetReactiveIterator.this.tryAgain();
            }

            @Override
            protected RFuture<ScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return SetReactiveIterator.this.scanIterator(client, nextIterPos);
            }
        });
    }
    
    protected boolean tryAgain() {
        return false;
    }
    
    protected abstract RFuture<ScanResult<Object>> scanIterator(RedisClient client, long nextIterPos);

}
