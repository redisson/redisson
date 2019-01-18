/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RFuture;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.SemaphorePubSub;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonPermitExpirableSemaphore extends RedissonExpirable implements RPermitExpirableSemaphore {

    private final SemaphorePubSub semaphorePubSub;

    final CommandAsyncExecutor commandExecutor;

    private final String timeoutName;
    
    private final long nonExpirableTimeout = 922337203685477L;

    public RedissonPermitExpirableSemaphore(CommandAsyncExecutor commandExecutor, String name, SemaphorePubSub semaphorePubSub) {
        super(commandExecutor, name);
        this.timeoutName = suffixName(name, "timeout");
        this.commandExecutor = commandExecutor;
        this.semaphorePubSub = semaphorePubSub;
    }

    String getChannelName() {
        return getChannelName(getName());
    }
    
    public static String getChannelName(String name) {
        if (name.contains("{")) {
            return "redisson_sc:" + name;
        }
        return "redisson_sc:{" + name + "}";
    }

    @Override
    public String acquire() throws InterruptedException {
        return acquire(1, -1, TimeUnit.MILLISECONDS);
    }

    @Override
    public String acquire(long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        return acquire(1, leaseTime, timeUnit);
    }
    
    @Override
    public RFuture<String> acquireAsync(long leaseTime, TimeUnit timeUnit) {
        return acquireAsync(1, leaseTime, timeUnit);
    }

    
    private String acquire(int permits, long ttl, TimeUnit timeUnit) throws InterruptedException {
        String permitId = tryAcquire(permits, ttl, timeUnit);
        if (permitId != null && !permitId.startsWith(":")) {
            return permitId;
        }

        RFuture<RedissonLockEntry> future = subscribe();
        commandExecutor.syncSubscription(future);
        try {
            while (true) {
                final Long nearestTimeout;
                permitId = tryAcquire(permits, ttl, timeUnit);
                if (permitId != null) {
                    if (!permitId.startsWith(":")) {
                        return permitId;
                    } else {
                        nearestTimeout = Long.valueOf(permitId.substring(1)) - System.currentTimeMillis();
                    }
                } else {
                    nearestTimeout = null;
                }
                
                if (nearestTimeout != null) {
                    getEntry().getLatch().tryAcquire(permits, nearestTimeout, TimeUnit.MILLISECONDS);
                } else {
                    getEntry().getLatch().acquire(permits);
                }
            }
        } finally {
            unsubscribe(future);
        }
//        return get(acquireAsync(permits, ttl, timeUnit));
    }
    
    public RFuture<String> acquireAsync() {
        return acquireAsync(1, -1, TimeUnit.MILLISECONDS);
    }
    
    private RFuture<String> acquireAsync(final int permits, final long ttl, final TimeUnit timeUnit) {
        final RPromise<String> result = new RedissonPromise<String>();
        long timeoutDate = calcTimeout(ttl, timeUnit);
        RFuture<String> tryAcquireFuture = tryAcquireAsync(permits, timeoutDate);
        tryAcquireFuture.addListener(new FutureListener<String>() {
            @Override
            public void operationComplete(Future<String> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                String permitId = future.getNow();
                if (permitId != null && !permitId.startsWith(":")) {
                    if (!result.trySuccess(permitId)) {
                        releaseAsync(permitId);
                    }
                    return;
                }

                final RFuture<RedissonLockEntry> subscribeFuture = subscribe();
                subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                    @Override
                    public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        acquireAsync(permits, subscribeFuture, result, ttl, timeUnit);
                    }

                });
            }
        });
        return result;
    }
    
    private void tryAcquireAsync(final AtomicLong time, final int permits, final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<String> result, final long ttl, final TimeUnit timeUnit) {
        if (result.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }
        
        if (time.get() <= 0) {
            unsubscribe(subscribeFuture);
            result.trySuccess(null);
            return;
        }
        
        long timeoutDate = calcTimeout(ttl, timeUnit);
        final long current = System.currentTimeMillis();
        RFuture<String> tryAcquireFuture = tryAcquireAsync(permits, timeoutDate);
        tryAcquireFuture.addListener(new FutureListener<String>() {
            @Override
            public void operationComplete(Future<String> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture);
                    result.tryFailure(future.cause());
                    return;
                }

                final Long nearestTimeout;
                String permitId = future.getNow();
                if (permitId != null) {
                    if (!permitId.startsWith(":")) {
                        unsubscribe(subscribeFuture);
                        if (!result.trySuccess(permitId)) {
                            releaseAsync(permitId);
                        }
                        return;
                    } else {
                        nearestTimeout = Long.valueOf(permitId.substring(1)) - System.currentTimeMillis();
                    }
                } else {
                    nearestTimeout = null;
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);
                
                if (time.get() <= 0) {
                    unsubscribe(subscribeFuture);
                    result.trySuccess(null);
                    return;
                }

                // waiting for message
                final long current = System.currentTimeMillis();
                final RedissonLockEntry entry = getEntry();
                if (entry.getLatch().tryAcquire()) {
                    tryAcquireAsync(time, permits, subscribeFuture, result, ttl, timeUnit);
                } else {
                    final AtomicReference<Timeout> waitTimeoutFutureRef = new AtomicReference<Timeout>();

                    final Timeout scheduledFuture;
                    if (nearestTimeout != null) {
                        scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                            @Override
                            public void run(Timeout timeout) throws Exception {
                                if (waitTimeoutFutureRef.get() != null && !waitTimeoutFutureRef.get().cancel()) {
                                    return;
                                }
                                
                                long elapsed = System.currentTimeMillis() - current;
                                time.addAndGet(-elapsed);

                                tryAcquireAsync(time, permits, subscribeFuture, result, ttl, timeUnit);
                            }
                        }, nearestTimeout, TimeUnit.MILLISECONDS);
                    } else {
                        scheduledFuture = null;
                    }

                    final Runnable listener = new Runnable() {
                        @Override
                        public void run() {
                            if (waitTimeoutFutureRef.get() != null && !waitTimeoutFutureRef.get().cancel()) {
                                entry.getLatch().release();
                                return;
                            }
                            if (scheduledFuture != null && !scheduledFuture.cancel()) {
                                entry.getLatch().release();
                                return;
                            }
                            
                            long elapsed = System.currentTimeMillis() - current;
                            time.addAndGet(-elapsed);

                            tryAcquireAsync(time, permits, subscribeFuture, result, ttl, timeUnit);
                        }
                    };
                    entry.addListener(listener);

                    long t = time.get();
                    Timeout waitTimeoutFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            if (scheduledFuture != null && !scheduledFuture.cancel()) {
                                return;
                            }

                            if (entry.removeListener(listener)) {
                                long elapsed = System.currentTimeMillis() - current;
                                time.addAndGet(-elapsed);
                                
                                tryAcquireAsync(time, permits, subscribeFuture, result, ttl, timeUnit);
                            }
                        }
                    }, t, TimeUnit.MILLISECONDS);
                    waitTimeoutFutureRef.set(waitTimeoutFuture);
                }
            }
        });
        
    }
    
    private void acquireAsync(final int permits, final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<String> result, final long ttl, final TimeUnit timeUnit) {
        if (result.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }
        
        long timeoutDate = calcTimeout(ttl, timeUnit);
        RFuture<String> tryAcquireFuture = tryAcquireAsync(permits, timeoutDate);
        tryAcquireFuture.addListener(new FutureListener<String>() {
            @Override
            public void operationComplete(Future<String> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture);
                    result.tryFailure(future.cause());
                    return;
                }

                final Long nearestTimeout;
                String permitId = future.getNow();
                if (permitId != null) {
                    if (!permitId.startsWith(":")) {
                        unsubscribe(subscribeFuture);
                        if (!result.trySuccess(permitId)) {
                            releaseAsync(permitId);
                        }
                        return;
                    } else {
                        nearestTimeout = Long.valueOf(permitId.substring(1)) - System.currentTimeMillis();
                    }
                } else {
                    nearestTimeout = null;
                }

                final RedissonLockEntry entry = getEntry();
                if (entry.getLatch().tryAcquire(permits)) {
                    acquireAsync(permits, subscribeFuture, result, ttl, timeUnit);
                } else {
                    final Timeout scheduledFuture;
                    if (nearestTimeout != null) {
                        scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                            @Override
                            public void run(Timeout timeout) throws Exception {
                                acquireAsync(permits, subscribeFuture, result, ttl, timeUnit);
                            }
                        }, nearestTimeout, TimeUnit.MILLISECONDS);
                    } else {
                        scheduledFuture = null;
                    }
                    
                    Runnable listener = new Runnable() {
                        @Override
                        public void run() {
                            if (scheduledFuture != null && !scheduledFuture.cancel()) {
                                entry.getLatch().release();
                                return;
                            }
                            acquireAsync(permits, subscribeFuture, result, ttl, timeUnit);
                        }
                    };
                    entry.addListener(listener);
                }
            }
        });
    }

    @Override
    public String tryAcquire() {
        String res = tryAcquire(1, -1, TimeUnit.MILLISECONDS);
        if (res != null && res.startsWith(":")) {
            return null;
        }
        return res;
    }

    private String tryAcquire(int permits, long ttl, TimeUnit timeUnit) {
        long timeoutDate = calcTimeout(ttl, timeUnit);
        return get(tryAcquireAsync(permits, timeoutDate));
    }

    private long calcTimeout(long ttl, TimeUnit timeUnit) {
        if (ttl != -1) {
            return System.currentTimeMillis() + timeUnit.toMillis(ttl);
        }
        return nonExpirableTimeout;
    }
    
    public RFuture<String> tryAcquireAsync() {
        final RPromise<String> result = new RedissonPromise<String>();
        RFuture<String> res = tryAcquireAsync(1, nonExpirableTimeout);
        res.addListener(new FutureListener<String>() {
            @Override
            public void operationComplete(Future<String> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                String permitId = future.getNow();
                if (permitId != null && !permitId.startsWith(":")) {
                    if (!result.trySuccess(permitId)) {
                        releaseAsync(permitId);
                    }
                } else {
                    result.trySuccess(null);
                }
            }
        });
        return result;
    }

    protected String generateId() {
        byte[] id = new byte[16];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        PlatformDependent.threadLocalRandom().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }
    
    public RFuture<String> tryAcquireAsync(int permits, long timeoutDate) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permits amount can't be negative");
        }

        String id = generateId();
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_STRING_DATA, 
                  "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[4], 'limit', 0, ARGV[1]); " +
                  "if #expiredIds > 0 then " +
                      "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                      "local value = redis.call('incrby', KEYS[1], #expiredIds); " + 
                      "if tonumber(value) > 0 then " +
                          "redis.call('publish', KEYS[3], value); " +
                      "end;" +
                  "end; " +
                  "local value = redis.call('get', KEYS[1]); " +
                  "if (value ~= false and tonumber(value) >= tonumber(ARGV[1])) then " +
                      "redis.call('decrby', KEYS[1], ARGV[1]); " +
                      "redis.call('zadd', KEYS[2], ARGV[2], ARGV[3]); " +
                      "return ARGV[3]; " +
                  "end; " +
                  "local v = redis.call('zrange', KEYS[2], 0, 0, 'WITHSCORES'); " + 
                  "if v[1] ~= nil and v[2] ~= ARGV[5] then " + 
                      "return ':' .. tostring(v[2]); " + 
                  "end " +
                  "return nil;",
                  Arrays.<Object>asList(getName(), timeoutName, getChannelName()), permits, timeoutDate, id, System.currentTimeMillis(), nonExpirableTimeout);
    }

    public RFuture<String> tryAcquireAsync(long waitTime, TimeUnit unit) {
        return tryAcquireAsync(1, waitTime, -1, unit);
    }
    
    @Override
    public String tryAcquire(long waitTime, long ttl, TimeUnit unit) throws InterruptedException {
        return tryAcquire(1, waitTime, ttl, unit);
    }
    
    @Override
    public RFuture<String> tryAcquireAsync(long waitTime, long ttl, TimeUnit unit) {
        return tryAcquireAsync(1, waitTime, ttl, unit);
    }

    private String tryAcquire(int permits, long waitTime, long ttl, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        long current = System.currentTimeMillis();

        String permitId = tryAcquire(permits, ttl, unit);
        if (permitId != null && !permitId.startsWith(":")) {
            return permitId;
        }

        time -= (System.currentTimeMillis() - current);
        if (time <= 0) {
            return null;
        }
        
        current = System.currentTimeMillis();
        RFuture<RedissonLockEntry> future = subscribe();
        if (!await(future, time, TimeUnit.MILLISECONDS)) {
            return null;
        }

        try {
            time -= (System.currentTimeMillis() - current);
            if (time <= 0) {
                return null;
            }
            
            while (true) {
                current = System.currentTimeMillis();
                final Long nearestTimeout;
                permitId = tryAcquire(permits, ttl, unit);
                if (permitId != null) {
                    if (!permitId.startsWith(":")) {
                        return permitId;
                    } else {
                        nearestTimeout = Long.valueOf(permitId.substring(1)) - System.currentTimeMillis();
                    }
                } else {
                    nearestTimeout = null;
                }
                
                time -= (System.currentTimeMillis() - current);
                if (time <= 0) {
                    return null;
                }

                // waiting for message
                current = System.currentTimeMillis();

                if (nearestTimeout != null) {
                    getEntry().getLatch().tryAcquire(permits, Math.min(time, nearestTimeout), TimeUnit.MILLISECONDS);
                } else {
                    getEntry().getLatch().tryAcquire(permits, time, TimeUnit.MILLISECONDS);
                }
                
                long elapsed = System.currentTimeMillis() - current;
                time -= elapsed;
                if (time <= 0) {
                    return null;
                }
            }
        } finally {
            unsubscribe(future);
        }
//        return get(tryAcquireAsync(permits, waitTime, ttl, unit));
    }

    private RFuture<String> tryAcquireAsync(final int permits, long waitTime, final long ttl, final TimeUnit timeUnit) {
        final RPromise<String> result = new RedissonPromise<String>();
        final AtomicLong time = new AtomicLong(timeUnit.toMillis(waitTime));
        final long current = System.currentTimeMillis();
        long timeoutDate = calcTimeout(ttl, timeUnit);
        RFuture<String> tryAcquireFuture = tryAcquireAsync(permits, timeoutDate);
        tryAcquireFuture.addListener(new FutureListener<String>() {
            @Override
            public void operationComplete(Future<String> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                String permitId = future.getNow();
                if (permitId != null && !permitId.startsWith(":")) {
                    if (!result.trySuccess(permitId)) {
                        releaseAsync(permitId);
                    }
                    return;
                }
                
                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);
                
                if (time.get() <= 0) {
                    result.trySuccess(null);
                    return;
                }
                
                final long current = System.currentTimeMillis();
                final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                final RFuture<RedissonLockEntry> subscribeFuture = subscribe();
                subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                    @Override
                    public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        if (futureRef.get() != null) {
                            futureRef.get().cancel();
                        }

                        long elapsed = System.currentTimeMillis() - current;
                        time.addAndGet(-elapsed);
                        
                        tryAcquireAsync(time, permits, subscribeFuture, result, ttl, timeUnit);
                    }
                });
                
                if (!subscribeFuture.isDone()) {
                    Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            if (!subscribeFuture.isDone()) {
                                result.trySuccess(null);
                            }
                        }
                    }, time.get(), TimeUnit.MILLISECONDS);
                    futureRef.set(scheduledFuture);
                }
            }
        });
        
        return result;
    }

    
    private RedissonLockEntry getEntry() {
        return semaphorePubSub.getEntry(getName());
    }

    private RFuture<RedissonLockEntry> subscribe() {
        return semaphorePubSub.subscribe(getName(), getChannelName(), commandExecutor.getConnectionManager().getSubscribeService());
    }

    private void unsubscribe(RFuture<RedissonLockEntry> future) {
        semaphorePubSub.unsubscribe(future.getNow(), getName(), getChannelName(), commandExecutor.getConnectionManager().getSubscribeService());
    }

    @Override
    public String tryAcquire(long waitTime, TimeUnit unit) throws InterruptedException {
        String res = tryAcquire(1, waitTime, -1, unit);
        if (res != null && res.startsWith(":")) {
            return null;
        }
        return res;
    }

    @Override
    public void release(String permitId) {
        get(releaseAsync(permitId));
    }

    @Override
    public boolean tryRelease(String permitId) {
        return get(tryReleaseAsync(permitId));
    }
    
    @Override
    public RFuture<Boolean> tryReleaseAsync(String permitId) {
        if (permitId == null) {
            throw new IllegalArgumentException("permitId can't be null");
        }

        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local removed = redis.call('zrem', KEYS[3], ARGV[1]);" + 
                "if tonumber(removed) ~= 1 then " + 
                    "return 0;" + 
                "end;" +
                "local value = redis.call('incrby', KEYS[1], ARGV[2]); " +
                "redis.call('publish', KEYS[2], value); " + 
                "return 1;",
                Arrays.<Object>asList(getName(), getChannelName(), timeoutName), permitId, 1);
    }
    
    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getName(), timeoutName);
        return super.sizeInMemoryAsync(keys);
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), timeoutName);
    }
    
    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                        "return redis.call('pexpire', KEYS[2], ARGV[1]); ",
                Arrays.<Object>asList(getName(), timeoutName),
                timeUnit.toMillis(timeToLive));
    }

    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('pexpireat', KEYS[1], ARGV[1]); " +
                        "return redis.call('pexpireat', KEYS[2], ARGV[1]); ",
                Arrays.<Object>asList(getName(), timeoutName),
                timestamp);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('persist', KEYS[1]); " +
                        "return redis.call('persist', KEYS[2]); ",
                Arrays.<Object>asList(getName(), timeoutName));
    }
    
    @Override
    public RFuture<Void> releaseAsync(final String permitId) {
        final RPromise<Void> result = new RedissonPromise<Void>();
        tryReleaseAsync(permitId).addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow()) {
                    result.trySuccess(null);
                } else {
                    result.tryFailure(new IllegalArgumentException("Permit with id " + permitId + " has already been released or doesn't exist"));
                }
            }
        });
        return result;
    }

    @Override
    public int availablePermits() {
        return get(availablePermitsAsync());
    }
    
    @Override
    public RFuture<Integer> availablePermitsAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER, 
                "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, -1); " +
                "if #expiredIds > 0 then " +
                    "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                    "local value = redis.call('incrby', KEYS[1], #expiredIds); " + 
                    "if tonumber(value) > 0 then " +
                        "redis.call('publish', KEYS[3], value); " +
                    "end;" + 
                    "return value; " +
                "end; " +
                "local ret = redis.call('get', KEYS[1]); " + 
                "return ret == false and 0 or ret;",
                Arrays.<Object>asList(getName(), timeoutName, getChannelName()), System.currentTimeMillis());
    }

    @Override
    public boolean trySetPermits(int permits) {
        return get(trySetPermitsAsync(permits));
    }

    @Override
    public RFuture<Boolean> trySetPermitsAsync(int permits) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false or value == 0) then "
                    + "redis.call('set', KEYS[1], ARGV[1]); "
                    + "redis.call('publish', KEYS[2], ARGV[1]); "
                    + "return 1;"
                + "end;"
                + "return 0;",
                Arrays.<Object>asList(getName(), getChannelName()), permits);
    }

    @Override
    public void addPermits(int permits) {
        get(addPermitsAsync(permits));
    }
    
    @Override
    public RFuture<Void> addPermitsAsync(int permits) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false) then "
                  + "value = 0;"
              + "end;"
              + "redis.call('set', KEYS[1], tonumber(value) + tonumber(ARGV[1])); "
              + "if tonumber(ARGV[1]) > 0 then "
                  + "redis.call('publish', KEYS[2], ARGV[1]); "
              + "end;",
                Arrays.<Object>asList(getName(), getChannelName()), permits);
    }

    @Override
    public RFuture<Boolean> updateLeaseTimeAsync(String permitId, long leaseTime, TimeUnit unit) {
        long timeoutDate = calcTimeout(leaseTime, unit);
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[3], 'limit', 0, -1); " +
                "if #expiredIds > 0 then " +
                    "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                    "local value = redis.call('incrby', KEYS[1], #expiredIds); " + 
                    "if tonumber(value) > 0 then " +
                        "redis.call('publish', KEYS[3], value); " +
                    "end;" + 
                "end; " +

                  "local value = redis.call('zscore', KEYS[2], ARGV[1]); " +
                  "if (value ~= false) then "
                    + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[1]); "
                    + "return 1;"
                + "end;"
                + "return 0;",
                Arrays.<Object>asList(getName(), timeoutName, getChannelName()),
                permitId, timeoutDate, System.currentTimeMillis());
    }

    @Override
    public boolean updateLeaseTime(String permitId, long leaseTime, TimeUnit unit) {
        return get(updateLeaseTimeAsync(permitId, leaseTime, unit));
    }

}
