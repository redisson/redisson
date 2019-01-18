/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.concurrent.atomic.AtomicLong;

import org.redisson.api.RFuture;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;

import reactor.core.publisher.Flux;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTopicReactive {

    private final RTopic topic;
    
    public RedissonTopicReactive(RTopic topic) {
        this.topic = topic;
    }

    public <M> Flux<M> getMessages(Class<M> type) {
        return Flux.<M>create(emitter -> {
            emitter.onRequest(n -> {
                AtomicLong counter = new AtomicLong(n);
                RFuture<Integer> t = topic.addListenerAsync(type, new MessageListener<M>() {
                    @Override
                    public void onMessage(CharSequence channel, M msg) {
                        emitter.next(msg);
                        if (counter.decrementAndGet() == 0) {
                            topic.removeListenerAsync(this);
                            emitter.complete();
                        }
                    }
                });
                t.whenComplete((id, e) -> {
                    if (e != null) {
                        emitter.error(e);
                        return;
                    }
                    
                    emitter.onDispose(() -> {
                        topic.removeListenerAsync(id);
                    });
                });
            });
        });
    }
    
}
