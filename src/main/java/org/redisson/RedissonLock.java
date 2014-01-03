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
import java.util.concurrent.locks.Lock;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class RedissonLock implements Lock {

    private final CountDownLatch subscribeLatch = new CountDownLatch(1);
    private final RedisPubSubConnection<Object, Object> pubSubConnection;
    private final RedisConnection<Object, Object> connection;
    private final String lockGroupName = "redisson_lock";
    private final String lockName;

    private static final Integer unlockMessage = 0;

    private final AtomicBoolean subscribeOnce = new AtomicBoolean();

    private final Semaphore msg = new Semaphore(1);

    RedissonLock(RedisPubSubConnection<Object, Object> pubSubConnection, RedisConnection<Object, Object> connection, String lockName) {
        this.pubSubConnection = pubSubConnection;
        this.connection = connection;
        this.lockName = lockName;
    }

    public void subscribe() {
        if (subscribeOnce.compareAndSet(false, true)) {
            RedisPubSubAdapter<Object, Object> listener = new RedisPubSubAdapter<Object, Object>() {

                @Override
                public void subscribed(Object channel, long count) {
                    if (channel.equals(getChannelName())) {
                        subscribeLatch.countDown();
                    }
                }

                @Override
                public void message(Object channel, Object message) {
                    if (message.equals(unlockMessage) && channel.equals(getChannelName())) {
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
        return lockGroupName + lockName;
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
        Boolean res = connection.hsetnx(lockGroupName, lockName, "1");
        return res;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        time = unit.toMillis(time);
        while (!tryLock()) {
            long current = System.currentTimeMillis();
            // waiting for message
            boolean res = msg.tryAcquire(time, TimeUnit.MILLISECONDS);
            if (res) {
                return true;
            }
            long elapsed = System.currentTimeMillis() - current;
            time -= elapsed;
            if (time <= 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void unlock() {
        connection.hdel(lockGroupName, lockName);
        connection.publish(getChannelName(), unlockMessage);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

}
