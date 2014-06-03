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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.connection.ConnectionManager;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.RCountDownLatch;
import org.redisson.misc.ReclosableLatch;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;

/**
 * Distributed alternative to the {@link java.util.concurrent.CountDownLatch}
 *
 * It has a advantage over {@link java.util.concurrent.CountDownLatch} --
 * count can be reset via {@link #trySetCount}.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCountDownLatch extends RedissonObject implements RCountDownLatch {

    private final String groupName = "redisson_countdownlatch_";

    private static final Integer zeroCountMessage = 0;
    private static final Integer newCountMessage = 1;

    private final AtomicReference<Promise<Boolean>> promise = new AtomicReference<Promise<Boolean>>();

    private final ReclosableLatch msg = new ReclosableLatch();

    private PubSubConnectionEntry pubSubEntry;

    RedissonCountDownLatch(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    private Future<Boolean> subscribe() {
        Promise<Boolean> p = promise.get();
        if (p != null) {
            return p;
        }

        final Promise<Boolean> newPromise = newPromise();
        if (!promise.compareAndSet(null, newPromise)) {
            return promise.get();
        }

        RedisPubSubAdapter<String, Integer> listener = new RedisPubSubAdapter<String, Integer>() {

            @Override
            public void subscribed(String channel, long count) {
                if (getChannelName().equals(channel)) {
                    newPromise.setSuccess(true);
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
        return newPromise;
    }

    public void await() throws InterruptedException {
        subscribe().await();

        while (getCount() > 0) {
            // waiting for open state
            msg.await();
        }
    }


    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        if (!subscribe().await(time, unit)) {
            return false;
        }

        time = unit.toMillis(time);
        while (getCount() > 0) {
            if (time <= 0) {
                return false;
            }
            long current = System.currentTimeMillis();
            // waiting for open state
            msg.await(time, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - current;
            time = time - elapsed;
        }

        return true;
    }

    @Override
    public void countDown() {
        subscribe().awaitUninterruptibly();

        if (getCount() <= 0) {
            return;
        }

        RedisConnection<String, Object> connection = connectionManager.connectionWriteOp();
        try {
            Long val = connection.decr(getName());
            if (val == 0) {
                connection.multi();
                connection.del(getName());
                connection.publish(getChannelName(), zeroCountMessage);
                if (connection.exec().size() != 2) {
                    throw new IllegalStateException();
                }
            } else if (val < 0) {
                connection.del(getName());
            }
        } finally {
            connectionManager.release(connection);
        }
    }

    private String getChannelName() {
        return groupName + getName();
    }

    @Override
    public long getCount() {
        subscribe().awaitUninterruptibly();

        RedisConnection<String, Object> connection = connectionManager.connectionReadOp();
        try {
            Number val = (Number) connection.get(getName());
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
        subscribe().awaitUninterruptibly();

        RedisConnection<String, Object> connection = connectionManager.connectionWriteOp();
        try {
            connection.watch(getName());
            Long oldValue = (Long) connection.get(getName());
            if (oldValue != null) {
                connection.unwatch();
                return false;
            }
            connection.multi();
            connection.set(getName(), count);
            connection.publish(getChannelName(), newCountMessage);
            return connection.exec().size() == 2;
        } finally {
            connectionManager.release(connection);
        }
    }

    public void close() {
        connectionManager.unsubscribe(pubSubEntry, getChannelName());
    }

}
