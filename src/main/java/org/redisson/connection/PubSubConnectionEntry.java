/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.connection;

import java.util.Queue;
import java.util.concurrent.Semaphore;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

public class PubSubConnectionEntry {

    private final Semaphore semaphore;
    private final RedisPubSubConnection conn;
    private final int subscriptionsPerConnection;

    public PubSubConnectionEntry(RedisPubSubConnection conn, int subscriptionsPerConnection) {
        super();
        this.conn = conn;
        this.subscriptionsPerConnection = subscriptionsPerConnection;
        this.semaphore = new Semaphore(subscriptionsPerConnection);
    }

    public void addListener(RedisPubSubListener listener) {
        conn.addListener(listener);
    }

    public Queue<RedisPubSubListener> getListeners() {
        return conn.getListeners();
    }

    public void removeListener(RedisPubSubListener listener) {
        conn.removeListener(listener);
    }

    public boolean subscribe(RedisPubSubAdapter listener, Object channel) {
        if (semaphore.tryAcquire()) {
            conn.addListener(listener);
            conn.subscribe(channel);
            return true;
        }
        return false;
    }

    public void unsubscribe(Object channel) {
        conn.unsubscribe(channel);
        semaphore.release();
    }

    public boolean tryClose() {
        if (semaphore.tryAcquire(subscriptionsPerConnection)) {
            conn.close();
            return true;
        }
        return false;
    }

}
