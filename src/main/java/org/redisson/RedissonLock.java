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

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;

import org.redisson.connection.ConnectionManager;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.core.RLock;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonLock extends RedissonObject implements RLock {

    public static class LockValue implements Serializable {

        private static final long serialVersionUID = -8895632286065689476L;

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

    private final UUID id;

    private static final Integer unlockMessage = 0;

    private final AtomicReference<Promise<Boolean>> promise = new AtomicReference<Promise<Boolean>>();

    private final Semaphore msg = new Semaphore(1);

    private PubSubConnectionEntry pubSubEntry;

    RedissonLock(ConnectionManager connectionManager, String name, UUID id) {
        super(connectionManager, name);
        this.id = id;
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

        msg.acquireUninterruptibly();

        RedisPubSubAdapter<String, Integer> listener = new RedisPubSubAdapter<String, Integer>() {

            @Override
            public void subscribed(String channel, long count) {
                if (getChannelName().equals(channel)) {
                    newPromise.setSuccess(true);
                }
            }

            @Override
            public void message(String channel, Integer message) {
                if (message.equals(unlockMessage) && getChannelName().equals(channel)) {
                    msg.release();
                }
            }

        };

        pubSubEntry = connectionManager.subscribe(listener, getChannelName());
        return newPromise;
    }

    @Override
    public void lock() {
        subscribe().awaitUninterruptibly();

        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }

    private String getKeyName() {
        return "redisson__lock__" + getName();
    }

    private String getChannelName() {
        return "redisson__lock__channel__" + getName();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        subscribe().awaitUninterruptibly();

        while (!tryLock()) {
            // waiting for message
            msg.acquire();
        }
    }

    @Override
    public boolean tryLock() {
        subscribe().awaitUninterruptibly();

        LockValue currentLock = new LockValue(id, Thread.currentThread().getId());
        currentLock.incCounter();

        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            Boolean res = connection.setnx(getKeyName(), currentLock);
            if (!res) {
                LockValue lock = (LockValue) connection.get(getKeyName());
                if (lock != null && lock.equals(currentLock)) {
                    lock.incCounter();
                    connection.set(getKeyName(), lock);
                    return true;
                }
            }
            return res;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        if (!subscribe().awaitUninterruptibly(time, unit)) {
            return false;
        }

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
        subscribe().awaitUninterruptibly();

        LockValue currentLock = new LockValue(id, Thread.currentThread().getId());

        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            LockValue lock = (LockValue) connection.get(getKeyName());
            if (lock != null && lock.equals(currentLock)) {
                if (lock.getCounter() > 1) {
                    lock.decCounter();
                    connection.set(getKeyName(), lock);
                } else {
                    unlock(connection);
                }
            } else {
                throw new IllegalMonitorStateException("Attempt to unlock lock, not locked by current id: "
                        + id + " thread-id: " + Thread.currentThread().getId());
            }
        } finally {
            connectionManager.release(connection);
        }
    }

    private void unlock(RedisConnection<Object, Object> connection) {
        int counter = 0;
        while (counter < 5) {
            connection.multi();
            connection.del(getKeyName());
            connection.publish(getChannelName(), unlockMessage);
            if (connection.exec().size() == 2) {
                return;
            }
            counter++;
        }
        throw new IllegalStateException("Can't unlock lock after 5 attempts. Current id: "
                + id + " thread-id: " + Thread.currentThread().getId());
    }

    @Override
    public Condition newCondition() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        connectionManager.unsubscribe(pubSubEntry, getChannelName());
    }

    @Override
    public void forceUnlock() {
        subscribe().awaitUninterruptibly();

        RedisConnection<Object, Object> connection = connectionManager.connectionWriteOp();
        try {
            while (true) {
                LockValue lock = (LockValue) connection.get(getKeyName());
                if (lock != null) {
                    unlock(connection);
                }
            }
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean isLocked() {
        subscribe().awaitUninterruptibly();

        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            LockValue lock = (LockValue) connection.get(getKeyName());
            return lock != null;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean isHeldByCurrentThread() {
        subscribe().awaitUninterruptibly();

        LockValue currentLock = new LockValue(id, Thread.currentThread().getId());

        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            LockValue lock = (LockValue) connection.get(getKeyName());
            return lock != null && lock.equals(currentLock);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public int getHoldCount() {
        subscribe().awaitUninterruptibly();

        LockValue currentLock = new LockValue(id, Thread.currentThread().getId());

        RedisConnection<Object, Object> connection = connectionManager.connectionReadOp();
        try {
            LockValue lock = (LockValue) connection.get(getKeyName());
            if (lock != null && lock.equals(currentLock)) {
                return lock.getCounter();
            }
            return 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public void delete() {
        super.delete(getKeyName());
    }

}
