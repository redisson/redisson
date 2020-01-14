/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
package org.redisson;

import org.redisson.api.RFuture;
import org.redisson.connection.ConnectionManager;
import org.redisson.misc.RedissonPromise;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Nikita Koksharov
 */
public class ElementsSubscribeService {
    
    private final Map<Integer, RFuture<?>> subscribeListeners = new HashMap<>();
    private final ConnectionManager connectionManager;

    public ElementsSubscribeService(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public <V> int subscribeOnElements(Supplier<RFuture<V>> func, Consumer<V> consumer) {
        int id = System.identityHashCode(consumer);
        synchronized (subscribeListeners) {
            RFuture<?> currentFuture = subscribeListeners.putIfAbsent(id, RedissonPromise.newSucceededFuture(null));
            if (currentFuture != null) {
                throw new IllegalArgumentException("Consumer object with listener id " + id + " already registered");
            }
        }
        resubscribe(func, consumer);
        return id;
    }

    public void unsubscribe(int listenerId) {
        RFuture<?> f;
        synchronized (subscribeListeners) {
            f = subscribeListeners.remove(listenerId);
        }
        if (f != null) {
            f.cancel(false);
        }
    }

    private <V> void resubscribe(Supplier<RFuture<V>> func, Consumer<V> consumer) {
        int listenerId = System.identityHashCode(consumer);
        if (!subscribeListeners.containsKey(listenerId)) {
            return;
        }

        RFuture<V> f;
        synchronized (subscribeListeners) {
            if (!subscribeListeners.containsKey(listenerId)) {
                return;
            }

            f = func.get();
            subscribeListeners.put(listenerId, f);
        }

        f.onComplete((r, e) -> {
            if (e != null) {
                connectionManager.newTimeout(t -> {
                    resubscribe(func, consumer);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            consumer.accept(r);
            resubscribe(func, consumer);
        });
    }

    
}
