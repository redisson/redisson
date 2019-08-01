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
package org.redisson.rx;

import java.util.concurrent.atomic.AtomicLong;

import org.redisson.api.RFuture;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;

import io.reactivex.Flowable;
import io.reactivex.functions.Action;
import io.reactivex.functions.LongConsumer;
import io.reactivex.processors.ReplayProcessor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTopicRx {

    private final RTopic topic;
    
    public RedissonTopicRx(RTopic topic) {
        this.topic = topic;
    }

    public <M> Flowable<M> getMessages(Class<M> type) {
        ReplayProcessor<M> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {
            @Override
            public void accept(long n) throws Exception {
                AtomicLong counter = new AtomicLong(n);
                RFuture<Integer> t = topic.addListenerAsync(type, new MessageListener<M>() {
                    @Override
                    public void onMessage(CharSequence channel, M msg) {
                        p.onNext(msg);
                        if (counter.decrementAndGet() == 0) {
                            topic.removeListenerAsync(this);
                            p.onComplete();
                        }
                    }
                });
                t.onComplete((id, e) -> {
                    if (e != null) {
                        p.onError(e);
                        return;
                    }
                    
                    p.doOnCancel(new Action() {
                        @Override
                        public void run() throws Exception {
                            topic.removeListenerAsync(id);
                        }
                    });
                });
            }
        });
    }
    
}
