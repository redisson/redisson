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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;

import org.redisson.core.RLock;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

/**
 * Reentrant distributed lock
 *
 *
 */
public class RedissonLock implements RLock {

    public static class LockValue {

        private UUID id;
        private Long threadId;
        // need for reentrant support
        private int counter;

        public LockValue() {
        }

        public LockValue(UUID id, Long threadId) {
            super();
            this.id = id;
            this.threadId = threadId;
        }

        public void decCounter() {
            counter--;
        }

        public void incCounter() {
            counter++;
        }

        public int getCounter() {
            return counter;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((threadId == null) ? 0 : threadId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LockValue other = (LockValue) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (threadId == null) {
                if (other.threadId != null)
                    return false;
            } else if (!threadId.equals(other.threadId))
                return false;
            return true;
        }

    }

    private final Redisson redisson;
    private final RedisPubSubConnection<Object, Object> pubSubConnection;
    private final RedisConnection<Object, Object> connection;

    private final UUID id = UUID.randomUUID();
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

    private String getKeyName() {
        return groupName + name;
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
        LockValue currentLock = new LockValue(id, Thread.currentThread().getId());
        currentLock.incCounter();

        Boolean res = connection.setnx(getKeyName(), currentLock);
        if (!res) {
            LockValue lock = (LockValue) connection.get(getKeyName());
            if (lock.equals(currentLock)) {
                lock.incCounter();
                connection.set(getKeyName(), lock);
                return true;
            }
        }
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
        LockValue currentLock = new LockValue(id, Thread.currentThread().getId());

        LockValue lock = (LockValue) connection.get(getKeyName());
        if (lock != null && lock.equals(currentLock)) {
            if (lock.getCounter() > 1) {
                lock.decCounter();
                connection.set(getKeyName(), lock);
            } else {
                connection.del(getKeyName());
                connection.publish(getChannelName(), unlockMessage);
            }
        } else {
            throw new IllegalMonitorStateException("Attempt to unlock lock, not locked by current thread id: "
                            + id + " thread-id: " + Thread.currentThread().getId());
        }

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
