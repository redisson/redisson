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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.redisson.async.ResultOperation;
import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RCountDownLatch;

import com.lambdaworks.redis.RedisAsyncConnection;
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

    private static final ConcurrentMap<String, RedissonCountDownLatchEntry> ENTRIES = new ConcurrentHashMap<String, RedissonCountDownLatchEntry>();
    
    private final UUID id;

    RedissonCountDownLatch(ConnectionManager connectionManager, String name, UUID id) {
        super(connectionManager, name);
        this.id = id;
    }

    private Future<Boolean> subscribe() {
        Promise<Boolean> promise = aquire();
        if (promise != null) {
            return promise;
        }

        Promise<Boolean> newPromise = newPromise();
        final RedissonCountDownLatchEntry value = new RedissonCountDownLatchEntry(newPromise);
        value.aquire();
        RedissonCountDownLatchEntry oldValue = ENTRIES.putIfAbsent(getEntryName(), value);
        if (oldValue != null) {
            Promise<Boolean> oldPromise = aquire();
            if (oldPromise == null) {
                return subscribe();
            }
            return oldPromise;
        }
        
        RedisPubSubAdapter<Integer> listener = new RedisPubSubAdapter<Integer>() {

            @Override
            public void subscribed(String channel, long count) {
                if (getChannelName().equals(channel)) {
                    value.getPromise().setSuccess(true);
                }
            }

            @Override
            public void message(String channel, Integer message) {
                if (!getChannelName().equals(channel)) {
                    return;
                }
                if (message.equals(zeroCountMessage)) {
                    value.getLatch().open();
                }
                if (message.equals(newCountMessage)) {
                    value.getLatch().close();
                }
            }

        };

        connectionManager.subscribe(listener, getChannelName());
        return newPromise;
    }

    private void release() {
        while (true) {
            RedissonCountDownLatchEntry entry = ENTRIES.get(getEntryName());
            if (entry == null) {
                return;
            }
            RedissonCountDownLatchEntry newEntry = new RedissonCountDownLatchEntry(entry);
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
    
    private Promise<Boolean> aquire() {
        while (true) {
            RedissonCountDownLatchEntry entry = ENTRIES.get(getEntryName());
            if (entry != null) {
                RedissonCountDownLatchEntry newEntry = new RedissonCountDownLatchEntry(entry);
                newEntry.aquire();
                if (ENTRIES.replace(getEntryName(), entry, newEntry)) {
                    return newEntry.getPromise();
                }
            } else {
                return null;
            }
        }
    }

    public void await() throws InterruptedException {
        Future<Boolean> promise = subscribe();
        try {
            promise.await();
            
            while (getCountInner() > 0) {
                // waiting for open state
                RedissonCountDownLatchEntry entry = ENTRIES.get(getEntryName());
                if (entry != null) {
                    entry.getLatch().await();
                }
            }
        } finally {
            release();
        }
    }


    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        Future<Boolean> promise = subscribe();
        try {
            if (!promise.await(time, unit)) {
                return false;
            }
            
            time = unit.toMillis(time);
            while (getCountInner() > 0) {
                if (time <= 0) {
                    return false;
                }
                long current = System.currentTimeMillis();
                // waiting for open state
                RedissonCountDownLatchEntry entry = ENTRIES.get(getEntryName());
                if (entry != null) {
                    entry.getLatch().await(time, TimeUnit.MILLISECONDS);
                }

                long elapsed = System.currentTimeMillis() - current;
                time = time - elapsed;
            }
            
            return true;
        } finally {
            release();
        }
    }

    @Override
    public void countDown() {
        if (getCount() <= 0) {
            return;
        }

        connectionManager.write(new SyncOperation<Object, Void>() {
            @Override
            public Void execute(RedisConnection<Object, Object> conn) {
                Long val = conn.decr(getName());
                if (val == 0) {
                    conn.multi();
                    conn.del(getName());
                    conn.publish(getChannelName(), zeroCountMessage);
                    if (conn.exec().size() != 2) {
                        throw new IllegalStateException();
                    }
                } else if (val < 0) {
                    conn.del(getName());
                }
                return null;
            }
        });
    }

    private String getEntryName() {
        return id + getName();
    }
    
    private String getChannelName() {
        return groupName + getName();
    }

    @Override
    public long getCount() {
        return getCountInner();
    }

    private long getCountInner() {
        Number val = connectionManager.read(new ResultOperation<Number, Number>() {
            @Override
            protected Future<Number> execute(RedisAsyncConnection<Object, Number> async) {
                return async.get(getName());
            }
        });
        
        if (val == null) {
            return 0;
        }
        return val.longValue();
    }

    @Override
    public boolean trySetCount(final long count) {
        return connectionManager.write(new SyncOperation<Object, Boolean>() {

            @Override
            public Boolean execute(RedisConnection<Object, Object> conn) {
                conn.watch(getName());
                Long oldValue = (Long) conn.get(getName());
                if (oldValue != null) {
                    conn.unwatch();
                    return false;
                }
                conn.multi();
                conn.set(getName(), count);
                conn.publish(getChannelName(), newCountMessage);
                return conn.exec().size() == 2;
            }
        });
    }
    
    @Override
    public void delete() {
        connectionManager.write(new SyncOperation<Object, Void>() {
            @Override
            public Void execute(RedisConnection<Object, Object> conn) {
                conn.multi();
                conn.del(getName());
                conn.publish(getChannelName(), zeroCountMessage);
                if (conn.exec().size() != 2) {
                    throw new IllegalStateException();
                }
                return null;
            }
        });
    }

}
