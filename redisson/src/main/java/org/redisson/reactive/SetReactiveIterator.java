/**
 * Copyright 2018 Nikita Koksharov
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

import org.reactivestreams.Subscriber;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class SetReactiveIterator<V> extends Stream<V> {

    @Override
    public void subscribe(final Subscriber<? super V> t) {
        t.onSubscribe(new ReactiveSubscription<V>(this, t) {

            private long nextIterPos;
            private RedisClient client;

            private boolean finished;

            @Override
            protected void onRequest(long n) {
                nextValues();
            }

            protected void nextValues() {
                final ReactiveSubscription<V> m = this;
                scanIterator(client, nextIterPos).addListener(new FutureListener<ListScanResult<Object>>() {
                    @Override
                    public void operationComplete(Future<ListScanResult<Object>> future) throws Exception {
                        if (!future.isSuccess()) {
                            m.onError(future.cause());
                            return;
                        }
                        
                        if (finished) {
                            client = null;
                            nextIterPos = 0;
                            return;
                        }

                        ListScanResult<Object> res = future.getNow();
                        client = res.getRedisClient();
                        nextIterPos = res.getPos();
                        
                        for (Object val : res.getValues()) {
                            m.onNext((V)val);
                        }

                        if (res.getPos() == 0) {
                            finished = true;
                            m.onComplete();
                        }
                        
                        if (finished) {
                            return;
                        }
                        nextValues();
                    }
                });
            }
        });
    }
    
    protected abstract RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos);

}
