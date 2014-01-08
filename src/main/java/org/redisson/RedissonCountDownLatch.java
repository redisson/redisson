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
package org.redisson;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ConnectionManager.PubSubEntry;
import org.redisson.core.RCountDownLatch;
import org.redisson.misc.internal.ReclosableLatch;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class RedissonCountDownLatch implements RCountDownLatch {

    private final CountDownLatch subscribeLatch = new CountDownLatch(1);

    private final String groupName = "redisson_countdownlatch_";
    private final String name;

    private static final Integer zeroCountMessage = 0;
    private static final Integer newCountMessage = 1;

    private final AtomicBoolean subscribeOnce = new AtomicBoolean();

    private final ReclosableLatch msg = new ReclosableLatch();

    private final ConnectionManager connectionManager;
    private PubSubEntry pubSubEntry;

    RedissonCountDownLatch(ConnectionManager connectionManager, String name) {
        this.connectionManager = connectionManager;
        this.name = name;
    }

    public void subscribe() {
        if (subscribeOnce.compareAndSet(false, true)) {
            RedisPubSubAdapter<String, Integer> listener = new RedisPubSubAdapter<String, Integer>() {

                @Override
                public void subscribed(String channel, long count) {
                    if (getChannelName().equals(channel)) {
                        subscribeLatch.countDown();
                    }
                }

                @Override
                public void message(String channel, Integer message) {
                    if (!getChannelName().equals(channel)) {
                        return;
                    }
                    if (message.equals(zeroCountMessage)) {
                        msg.open();
                    }
                    if (message.equals(newCountMessage)) {
                        msg.close();
                    }
                }

            };

            pubSubEntry = connectionManager.subscribe(listener, getChannelName());
        }

        try {
            subscribeLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void await() throws InterruptedException {
        while (getCount() > 0) {
            // waiting for message
            msg.await();
        }
    }


    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        time = unit.toMillis(time);
        while (getCount() > 0) {
            if (time <= 0) {
                return false;
            }
            long current = System.currentTimeMillis();
            // waiting for message
            msg.await(time, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - current;
            time = time - elapsed;
        }

        return true;
    }

    @Override
    public void countDown() {
        if (getCount() <= 0) {
            return;
        }

        RedisConnection<String, Object> connection = connectionManager.acquireConnection();
        try {
            Long val = connection.decr(name);
            if (val == 0) {
                connection.multi();
                connection.del(name);
                connection.publish(getChannelName(), zeroCountMessage);
                if (connection.exec().size() != 2) {
                    throw new IllegalStateException();
                }
            } else if (val < 0) {
                connection.del(name);
            }
        } finally {
            connectionManager.release(connection);
        }
    }

    private String getChannelName() {
        return groupName + name;
    }

    @Override
    public long getCount() {
        RedisConnection<String, Object> connection = connectionManager.acquireConnection();
        try {
            Number val = (Number) connection.get(name);
            if (val == null) {
                return 0;
            }
            return val.longValue();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean trySetCount(long count) {
        RedisConnection<String, Object> connection = connectionManager.acquireConnection();
        try {
            connection.watch(name);
            Long oldValue = (Long) connection.get(name);
            if (oldValue != null) {
                connection.discard();
                return false;
            }
            connection.multi();
            connection.set(name, count);
            connection.publish(getChannelName(), newCountMessage);
            return connection.exec().size() == 2;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void destroy() {
        connectionManager.unsubscribe(pubSubEntry, getChannelName());
//        redisson.remove(this);
    }

}
