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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;

import org.redisson.core.RLock;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

// TODO make it reentrant
public class RedissonLock implements RLock {

    private final Redisson redisson;
    private final RedisPubSubConnection<Object, Object> pubSubConnection;
    private final RedisConnection<Object, Object> connection;

    private final String groupName = "redisson_lock";
    private final String name;

    private static final Integer unlockMessage = 0;

    private final CountDownLatch subscribeLatch = new CountDownLatch(1);
    private final AtomicBoolean subscribeOnce = new AtomicBoolean();

    private final Semaphore msg = new Semaphore(1);

    RedissonLock(Redisson redisson, RedisPubSubConnection<Object, Object> pubSubConnection, RedisConnection<Object, Object> connection, String name) {
        this.redisson = redisson;
        this.pubSubConnection = pubSubConnection;
        this.connection = connection;
        this.name = name;
    }

    public void subscribe() {
        if (subscribeOnce.compareAndSet(false, true)) {
            msg.acquireUninterruptibly();

            RedisPubSubAdapter<Object, Object> listener = new RedisPubSubAdapter<Object, Object>() {

                @Override
                public void subscribed(Object channel, long count) {
                    subscribeLatch.countDown();
                }

                @Override
                public void message(Object channel, Object message) {
                    if (message.equals(unlockMessage)) {
                        msg.release();
                    }
                }

            };
            pubSubConnection.addListener(listener);
            pubSubConnection.subscribe(getChannelName());
        }

        try {
            subscribeLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }

    private String getChannelName() {
        return groupName + name;
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        while (!tryLock()) {
            // waiting for message
            msg.acquire();
        }
    }

    @Override
    public boolean tryLock() {
        Boolean res = connection.hsetnx(groupName, name, "1");
        return res;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        time = unit.toMillis(time);
        while (!tryLock()) {
            if (time <= 0) {
                return false;
            }
            long current = System.currentTimeMillis();
            // waiting for message
            msg.tryAcquire(time, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - current;
            time -= elapsed;
        }
        return true;
    }

    @Override
    public void unlock() {
        connection.hdel(groupName, name);
        connection.publish(getChannelName(), unlockMessage);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void destroy() {
        pubSubConnection.unsubscribe(getChannelName());

        connection.close();
        pubSubConnection.close();

        redisson.remove(this);
    }

}
