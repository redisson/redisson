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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.redisson.async.ResultOperation;
import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RLock;

import com.lambdaworks.redis.RedisAsyncConnection;
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

    private static final ConcurrentMap<String, RedissonLockEntry> ENTRIES = new ConcurrentHashMap<String, RedissonLockEntry>();

    RedissonLock(ConnectionManager connectionManager, String name, UUID id) {
        super(connectionManager, name);
        this.id = id;
    }

    private void release() {
        while (true) {
            RedissonLockEntry entry = ENTRIES.get(getEntryName());
            if (entry == null) {
                return;
            }
            RedissonLockEntry newEntry = new RedissonLockEntry(entry);
            newEntry.release();
            if (ENTRIES.replace(getEntryName(), entry, newEntry)) {
                if (newEntry.isFree() 
                        && ENTRIES.remove(getEntryName(), newEntry)) {
                    Future future = connectionManager.unsubscribe(getChannelName());
                    future.awaitUninterruptibly();
                }
                return;
            }
        }
    }

    private String getEntryName() {
        return id + ":" + getName();
    }
    
    private Promise<Boolean> aquire() {
        while (true) {
            RedissonLockEntry entry = ENTRIES.get(getEntryName());
            if (entry != null) {
                RedissonLockEntry newEntry = new RedissonLockEntry(entry);
                newEntry.aquire();
                if (ENTRIES.replace(getEntryName(), entry, newEntry)) {
                    return newEntry.getPromise();
                }
            } else {
                return null;
            }
        }
    }
    
    private Future<Boolean> subscribe() {
        Promise<Boolean> promise = aquire();
        if (promise != null) {
            return promise;
        }

        Promise<Boolean> newPromise = newPromise();
        final RedissonLockEntry value = new RedissonLockEntry(newPromise);
        value.aquire();
        RedissonLockEntry oldValue = ENTRIES.putIfAbsent(getEntryName(), value);
        if (oldValue != null) {
            Promise<Boolean> oldPromise = aquire();
            if (oldPromise == null) {
                return subscribe();
            }
            return oldPromise;
        }

        RedisPubSubAdapter<Object> listener = new RedisPubSubAdapter<Object>() {

            @Override
            public void subscribed(String channel, long count) {
                if (getChannelName().equals(channel)) {
                    value.getPromise().setSuccess(true);
                }
            }

            @Override
            public void message(String channel, Object message) {
                if (message.equals(unlockMessage) && getChannelName().equals(channel)) {
                    value.getLatch().release();
                }
            }

        };

        connectionManager.subscribe(listener, getChannelName());
        return newPromise;
    }

    private String getChannelName() {
        return "redisson__lock__channel__" + getName();
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

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }

    
    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }
    
    @Override
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        Future<Boolean> promise = subscribe();
        try {
            promise.awaitUninterruptibly();
            
            while (true) {
                Long ttl;
                if (leaseTime != -1) {
                    ttl = tryLockInner(leaseTime, unit);
                } else {
                    ttl = tryLockInner();
                }
                if (ttl == null) {
                    break;
                }
                // waiting for message
                RedissonLockEntry entry = ENTRIES.get(getEntryName());
                if (ttl >= 0) {
                    entry.getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    entry.getLatch().acquire();
                }
            }
        } finally {
            release();
        }
    }

    @Override
    public boolean tryLock() {
        return tryLockInner() == null;
    }
    
    private Long tryLockInner() {
        final LockValue currentLock = new LockValue(id, Thread.currentThread().getId());
        currentLock.incCounter();
        
        return connectionManager.write(new SyncOperation<LockValue, Long>() {

            @Override
            public Long execute(RedisConnection<Object, LockValue> connection) {
                Boolean res = connection.setnx(getName(), currentLock);
                if (!res) {
                    LockValue lock = (LockValue) connection.get(getName());
                    if (lock != null && lock.equals(currentLock)) {
                        lock.incCounter();
                        connection.set(getName(), lock);
                        return null;
                    }
                    
                    Long ttl = connection.pttl(getName());
                    return ttl;
                }
                return null;
            }
        });
    }

    private Long tryLockInner(final long leaseTime, final TimeUnit unit) {
        final LockValue currentLock = new LockValue(id, Thread.currentThread().getId());
        currentLock.incCounter();
        
        return connectionManager.write(new SyncOperation<Object, Long>() {
            @Override
            public Long execute(RedisConnection<Object, Object> connection) {
                long time = unit.toMillis(leaseTime);
                String res = connection.setexnx(getName(), currentLock, time);
                if ("OK".equals(res)) {
                    return null;
                } else {
                    LockValue lock = (LockValue) connection.get(getName());
                    if (lock != null && lock.equals(currentLock)) {
                        lock.incCounter();
                        connection.psetex(getName(), time, lock);
                        return null;
                    }
                    
                    Long ttl = connection.pttl(getName());
                    return ttl;
                }
            }
        });
    }
    
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        Future<Boolean> promise = subscribe();
        try {
            if (!promise.awaitUninterruptibly(waitTime, unit)) {
                return false;
            }
            
            long time = unit.toMillis(waitTime);
            
            while (true) {
                Long ttl;
                if (leaseTime != -1) {
                    ttl = tryLockInner(leaseTime, unit);
                } else {
                    ttl = tryLockInner();
                }
                if (ttl == null) {
                    break;
                }
                
                if (time <= 0) {
                    return false;
                }
                long current = System.currentTimeMillis();
                // waiting for message
                RedissonLockEntry entry = ENTRIES.get(getEntryName());
                
                if (ttl >= 0 && ttl < time) {
                    entry.getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    entry.getLatch().tryAcquire(time, TimeUnit.MILLISECONDS);
                }
                
                long elapsed = System.currentTimeMillis() - current;
                time -= elapsed;
            }
            return true;
        } finally {
            release();
        }
    }
    
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock(time, -1, unit);
    }

    @Override
    public void unlock() {
        connectionManager.write(new SyncOperation<Object, Void>() {
            @Override
            public Void execute(RedisConnection<Object, Object> connection) {
                LockValue lock = (LockValue) connection.get(getName());
                if (lock != null) {
                    LockValue currentLock = new LockValue(id, Thread.currentThread().getId());
                    if (lock.equals(currentLock)) {
                        if (lock.getCounter() > 1) {
                            lock.decCounter();
                            connection.set(getName(), lock);
                        } else {
                            unlock(connection);
                        }
                    } else {
                        throw new IllegalMonitorStateException("Attempt to unlock lock, not locked by current id: "
                                + id + " thread-id: " + Thread.currentThread().getId());
                    }
                } else {
                    // could be deleted
                }
                return null;
            }
        });
    }

    private void unlock(RedisConnection<Object, Object> connection) {
        int counter = 0;
        while (counter < 5) {
            connection.multi();
            connection.del(getName());
            connection.publish(getChannelName(), unlockMessage);
            List<Object> res = connection.exec();
            if (res.size() == 2) {
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
    public void forceUnlock() {
        connectionManager.write(new SyncOperation<Object, Void>() {
            @Override
            public Void execute(RedisConnection<Object, Object> connection) {
                unlock(connection);
                return null;
            }
        });
    }

    @Override
    public boolean isLocked() {
        return getCurrentLock() != null;
    }

    private LockValue getCurrentLock() {
        LockValue lock = connectionManager.read(new ResultOperation<LockValue, LockValue>() {
            @Override
            protected Future<LockValue> execute(RedisAsyncConnection<Object, LockValue> async) {
                return async.get(getName());
            }
        });
        return lock;
    }

    @Override
    public boolean isHeldByCurrentThread() {
        LockValue lock = getCurrentLock();
        LockValue currentLock = new LockValue(id, Thread.currentThread().getId());
        return lock != null && lock.equals(currentLock);
    }

    @Override
    public int getHoldCount() {
        LockValue lock = getCurrentLock();
        LockValue currentLock = new LockValue(id, Thread.currentThread().getId());
        if (lock != null && lock.equals(currentLock)) {
            return lock.getCounter();
        }
        return 0;
    }

    @Override
    public void delete() {
        forceUnlock();
    }

}
