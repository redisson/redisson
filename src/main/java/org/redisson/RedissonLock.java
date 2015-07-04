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
import java.util.concurrent.locks.Condition;

import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RLock;
import org.redisson.core.RScript;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.<br>
 * Lock will be removed automatically if client disconnects.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonLock extends RedissonExpirable implements RLock {

    public static final long LOCK_EXPIRATION_INTERVAL_SECONDS = 30;
    private static final ConcurrentMap<String, Timeout> refreshTaskMap = new ConcurrentHashMap<String, Timeout>();
    protected long internalLockLeaseTime = TimeUnit.SECONDS.toMillis(LOCK_EXPIRATION_INTERVAL_SECONDS);

    private final UUID id;

    private static final Integer unlockMessage = 0;

    private static final ConcurrentMap<String, RedissonLockEntry> ENTRIES = new ConcurrentHashMap<String, RedissonLockEntry>();

    protected RedissonLock(ConnectionManager connectionManager, String name, UUID id) {
        super(connectionManager, name);
        this.id = id;
    }

    private void unsubscribe() {
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

    private String getEntryName() {
        return id + ":" + getName();
    }

    private Promise<Boolean> aquire() {
        while (true) {
            RedissonLockEntry entry = ENTRIES.get(getEntryName());
            if (entry == null) {
                return null;
            }

            RedissonLockEntry newEntry = new RedissonLockEntry(entry);
            newEntry.aquire();
            if (ENTRIES.replace(getEntryName(), entry, newEntry)) {
                return newEntry.getPromise();
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
                if (getChannelName().equals(channel)
                        && !value.getPromise().isSuccess()) {
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

        synchronized (ENTRIES) {
            connectionManager.subscribe(listener, getChannelName());
        }
        return newPromise;
    }

    private String getChannelName() {
        return "redisson__lock__channel__{" + getName() + "}";
    }

    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    @Override
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        Long ttl;
        if (leaseTime != -1) {
            ttl = tryLockInner(leaseTime, unit);
        } else {
            ttl = tryLockInner();
        }
        // lock acquired
        if (ttl == null) {
            return;
        }

        subscribe().awaitUninterruptibly();

        try {
            while (true) {
                if (leaseTime != -1) {
                    ttl = tryLockInner(leaseTime, unit);
                } else {
                    ttl = tryLockInner();
                }
                // lock acquired
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
            unsubscribe();
        }
    }

    @Override
    public boolean tryLock() {
        return tryLockInner() == null;
    }

    private Long tryLockInner() {
        Long ttlRemaining = tryLockInner(LOCK_EXPIRATION_INTERVAL_SECONDS, TimeUnit.SECONDS);
        // lock acquired
        if (ttlRemaining == null) {
            newRefreshTask();
        }
        return ttlRemaining;
    }

    private void newRefreshTask() {
        if (refreshTaskMap.containsKey(getName())) {
            return;
        }

        Timeout task = connectionManager.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                expire(internalLockLeaseTime, TimeUnit.MILLISECONDS);
                refreshTaskMap.remove(getName());
                newRefreshTask(); // reschedule itself
            }
        }, internalLockLeaseTime / 3, TimeUnit.MILLISECONDS);

        if (refreshTaskMap.putIfAbsent(getName(), task) != null) {
            task.cancel();
        }
    }

    /**
     * Stop refresh timer
     * @return true if timer was stopped successfully
     */
    private void stopRefreshTask() {
        Timeout task = refreshTaskMap.remove(getName());
        if (task != null) {
            task.cancel();
        }
    }


    private Long tryLockInner(final long leaseTime, final TimeUnit unit) {
        internalLockLeaseTime = unit.toMillis(leaseTime);

        return new RedissonScript(connectionManager)
                .evalR("local v = redis.call('get', KEYS[1]); " +
                                "if (v == false) then " +
                                "  redis.call('set', KEYS[1], cjson.encode({['o'] = ARGV[1], ['c'] = 1}), 'px', ARGV[2]); " +
                                "  return nil; " +
                                "else " +
                                "  local o = cjson.decode(v); " +
                                "  if (o['o'] == ARGV[1]) then " +
                                "    o['c'] = o['c'] + 1; redis.call('set', KEYS[1], cjson.encode(o), 'px', ARGV[2]); " +
                                "    return nil; " +
                                "  end;" +
                                "  return redis.call('pttl', KEYS[1]); " +
                                "end",
                        RScript.ReturnType.INTEGER,
                        Collections.<Object>singletonList(getName()), Collections.singletonList(id.toString() + "-" + Thread.currentThread().getId()), Collections.singletonList(internalLockLeaseTime));
    }

    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        Long ttl;
        if (leaseTime != -1) {
            ttl = tryLockInner(leaseTime, unit);
        } else {
            ttl = tryLockInner();
        }
        // lock acquired
        if (ttl == null) {
            return true;
        }

        if (!subscribe().awaitUninterruptibly(time, TimeUnit.MILLISECONDS)) {
            return false;
        }

        try {
            while (true) {
                if (leaseTime != -1) {
                    ttl = tryLockInner(leaseTime, unit);
                } else {
                    ttl = tryLockInner();
                }
                // lock acquired
                if (ttl == null) {
                    break;
                }

                if (time <= 0) {
                    return false;
                }

                // waiting for message
                long current = System.currentTimeMillis();
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
            unsubscribe();
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock(time, -1, unit);
    }

    @Override
    public void unlock() {
        String opStatus = new RedissonScript(connectionManager)
                .evalR("local v = redis.call('get', KEYS[1]); " +
                                "if (v == false) then " +
                                "  redis.call('publish', ARGV[4], ARGV[2]); " +
                                "  return 'OK'; " +
                                "else " +
                                "  local o = cjson.decode(v); " +
                                "  if (o['o'] == ARGV[1]) then " +
                                "    o['c'] = o['c'] - 1; " +
                                "    if (o['c'] > 0) then " +
                                "      redis.call('set', KEYS[1], cjson.encode(o), 'px', ARGV[3]); " +
                                "      return 'FALSE';"+
                                "    else " +
                                "      redis.call('del', KEYS[1]);" +
                                "      redis.call('publish', ARGV[4], ARGV[2]); " +
                                "      return 'OK';"+
                                "    end" +
                                "  end;" +
                                "  return nil; " +
                                "end",
                        RScript.ReturnType.STATUS,
                        Collections.<Object>singletonList(getName()), Arrays.asList(id.toString() + "-" + Thread.currentThread().getId(), unlockMessage), Arrays.asList(internalLockLeaseTime, getChannelName()));
        if ("OK".equals(opStatus)) {
            stopRefreshTask();
        } else if ("FALSE".equals(opStatus)) {
            //do nothing
        } else {
            throw new IllegalStateException("Can't unlock lock Current id: "
                    + id + " thread-id: " + Thread.currentThread().getId());
        }

    }

    @Override
    public Condition newCondition() {
        // TODO implement
        throw new UnsupportedOperationException();
    }

    @Override
    public void forceUnlock() {
        connectionManager.get(forceUnlockAsync());
    }

    private Future<Boolean> forceUnlockAsync() {
        stopRefreshTask();
        return new RedissonScript(connectionManager)
                .evalAsyncR("redis.call('del', KEYS[1]); redis.call('publish', ARGV[2], ARGV[1]); return true",
                        RScript.ReturnType.BOOLEAN,
                        Collections.<Object>singletonList(getName()), Collections.singletonList(unlockMessage), Collections.singletonList(getChannelName()));
    }

    @Override
    public boolean isLocked() {
        return connectionManager.read(new SyncOperation<Boolean, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, Boolean> conn) {
                return conn.exists(getName());
            }
        });
    }

    @Override
    public boolean isHeldByCurrentThread() {
        String opStatus = new RedissonScript(connectionManager)
                .eval("local v = redis.call('get', KEYS[1]); " +
                                "if (v == false) then " +
                                "  return nil; " +
                                "else " +
                                "  local o = cjson.decode(v); " +
                                "  if (o['o'] == ARGV[1]) then " +
                                "    return 'OK'; " +
                                "  else" +
                                "    return nil; " +
                                "  end;" +
                                "end",
                        RScript.ReturnType.STATUS,
                        Collections.<Object>singletonList(getName()), id.toString() + "-" + Thread.currentThread().getId());
        return "OK".equals(opStatus);
    }

    @Override
    public int getHoldCount() {
        Long opStatus = new RedissonScript(connectionManager)
                .eval("local v = redis.call('get', KEYS[1]); " +
                                "if (v == false) then " +
                                "  return 0; " +
                                "else " +
                                "  local o = cjson.decode(v); " +
                                "  return o['c']; " +
                                "end",
                        RScript.ReturnType.INTEGER,
                        Collections.<Object>singletonList(getName()), id.toString() + "-" + Thread.currentThread().getId());
        return opStatus.intValue();
    }

    @Override
    public boolean delete() {
        forceUnlock();
        return true;
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return forceUnlockAsync();
    }

}
