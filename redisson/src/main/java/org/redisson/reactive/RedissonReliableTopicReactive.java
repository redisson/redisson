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

import org.redisson.api.RFuture;
import org.redisson.api.RReliableTopic;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReliableTopicReactive {

    private final RReliableTopic topic;

    public RedissonReliableTopicReactive(RReliableTopic topic) {
        this.topic = topic;
    }

    public <M> Flux<M> getMessages(Class<M> type) {
        return Flux.create(emitter -> {
            emitter.onRequest(n -> {
                AtomicLong counter = new AtomicLong(n);
                AtomicReference<String> idRef = new AtomicReference<>();
                RFuture<String> t = topic.addListenerAsync(type, (channel, msg) -> {
                    emitter.next(msg);
                    if (counter.decrementAndGet() == 0) {
                        topic.removeListenerAsync(idRef.get());
                        emitter.complete();
                    }
                });
                t.whenComplete((id, e) -> {
                    if (e != null) {
                        emitter.error(e);
                        return;
                    }

                    idRef.set(id);
                    emitter.onDispose(() -> {
                        topic.removeListenerAsync(id);
                    });
                });
            });
        });
    }
    
}
