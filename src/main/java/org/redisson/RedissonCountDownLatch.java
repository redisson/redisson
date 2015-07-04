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

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RCountDownLatch;
import org.redisson.core.RScript;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

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

    private static final Integer zeroCountMessage = 0;
    private static final Integer newCountMessage = 1;

    private static final ConcurrentMap<String, RedissonCountDownLatchEntry> ENTRIES = new ConcurrentHashMap<String, RedissonCountDownLatchEntry>();

    private final UUID id;

    protected RedissonCountDownLatch(ConnectionManager connectionManager, String name, UUID id) {
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
                if (getChannelName().equals(channel)
                        && !value.getPromise().isSuccess()) {
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

        synchronized (ENTRIES) {
            connectionManager.subscribe(listener, getChannelName());
        }
        return newPromise;
    }

    private void unsubscribe() {
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
                    synchronized (ENTRIES) {
                        // maybe added during subscription
                        if (!ENTRIES.containsKey(getEntryName())) {
                            connectionManager.unsubscribe(getChannelName());
                        }
                    }
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
            unsubscribe();
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
            unsubscribe();
        }
    }

    @Override
    public void countDown() {
        if (getCount() <= 0) {
            return;
        }

        new RedissonScript(connectionManager).evalR(
                "local v = redis.call('decr', KEYS[1]);" +
                        "if v <= 0 then redis.call('del', KEYS[1]) end;" +
                        "if v == 0 then redis.call('publish', ARGV[2], ARGV[1]) end;" +
                        "return 'OK'",
                RScript.ReturnType.STATUS,
                Collections.<Object>singletonList(getName()), Collections.singletonList(zeroCountMessage), Collections.singletonList(getChannelName()));
    }

    private String getEntryName() {
        return id + getName();
    }

    private String getChannelName() {
        return "redisson_countdownlatch_{" + getName() + "}";
    }

    @Override
    public long getCount() {
        return getCountInner();
    }

    private long getCountInner() {

        Long val = new RedissonScript(connectionManager).eval(
                "return redis.call('get', KEYS[1])",
                RScript.ReturnType.INTEGER,
                Collections.<Object>singletonList(getName()));

        if (val == null) {
            return 0;
        }
        return val;
    }

    @Override
    public boolean trySetCount(long count) {
        return new RedissonScript(connectionManager).evalR(
                "if redis.call('exists', KEYS[1]) == 0 then redis.call('set', KEYS[1], ARGV[2]); redis.call('publish', ARGV[3], ARGV[1]); return true else return false end",
                RScript.ReturnType.BOOLEAN,
                Collections.<Object>singletonList(getName()), Collections.singletonList(newCountMessage), Arrays.asList(count, getChannelName()));
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return new RedissonScript(connectionManager).evalAsyncR(
                "if redis.call('del', KEYS[1]) == 1 then redis.call('publish', ARGV[2], ARGV[1]); return true else return false end",
                RScript.ReturnType.BOOLEAN,
                Collections.<Object>singletonList(getName()), Collections.singletonList(newCountMessage), Collections.singletonList(getChannelName()));
    }

}
