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
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RFuture;
import org.redisson.api.RSemaphore;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.SemaphorePubSub;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.Semaphore}.
 * <p>
 * Works in non-fair mode. Therefore order of acquiring is unpredictable.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSemaphore extends RedissonExpirable implements RSemaphore {

    private final SemaphorePubSub semaphorePubSub;

    final CommandAsyncExecutor commandExecutor;

    public RedissonSemaphore(CommandAsyncExecutor commandExecutor, String name, SemaphorePubSub semaphorePubSub) {
        super(commandExecutor, name);
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
    public void acquire() throws InterruptedException {
        acquire(1);
    }

    @Override
    public void acquire(int permits) throws InterruptedException {
        if (tryAcquire(permits)) {
            return;
        }

        RFuture<RedissonLockEntry> future = subscribe();
        commandExecutor.syncSubscription(future);
        try {
            while (true) {
                if (tryAcquire(permits)) {
                    return;
                }

                getEntry().getLatch().acquire(permits);
            }
        } finally {
            unsubscribe(future);
        }
//        get(acquireAsync(permits));
    }
    
    @Override
    public RFuture<Void> acquireAsync() {
        return acquireAsync(1);
    }
    
    @Override
    public RFuture<Void> acquireAsync(final int permits) {
        final RPromise<Void> result = new RedissonPromise<Void>();
        RFuture<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    if (!result.trySuccess(null)) {
                        releaseAsync(permits);
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

                        acquireAsync(permits, subscribeFuture, result);
                    }

                });
            }
        });
        return result;
    }
    
    private void tryAcquireAsync(final AtomicLong time, final int permits, final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<Boolean> result) {
        if (result.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }
        
        if (time.get() <= 0) {
            unsubscribe(subscribeFuture);
            result.trySuccess(false);
            return;
        }
        
        final long current = System.currentTimeMillis();
        RFuture<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture);
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow()) {
                    unsubscribe(subscribeFuture);
                    if (!result.trySuccess(true)) {
                        releaseAsync(permits);
                    }
                    return;
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);
                
                if (time.get() <= 0) {
                    unsubscribe(subscribeFuture);
                    result.trySuccess(false);
                    return;
                }

                // waiting for message
                final long current = System.currentTimeMillis();
                final RedissonLockEntry entry = getEntry();
                if (entry.getLatch().tryAcquire()) {
                    tryAcquireAsync(time, permits, subscribeFuture, result);
                } else {
                    final AtomicBoolean executed = new AtomicBoolean();
                    final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                    final Runnable listener = new Runnable() {
                        @Override
                        public void run() {
                            executed.set(true);
                            if (futureRef.get() != null && !futureRef.get().cancel()) {
                                entry.getLatch().release();
                                return;
                            }
                            long elapsed = System.currentTimeMillis() - current;
                            time.addAndGet(-elapsed);

                            tryAcquireAsync(time, permits, subscribeFuture, result);
                        }
                    };
                    entry.addListener(listener);

                    long t = time.get();
                    if (!executed.get()) {
                        Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                            @Override
                            public void run(Timeout timeout) throws Exception {
                                if (entry.removeListener(listener)) {
                                    long elapsed = System.currentTimeMillis() - current;
                                    time.addAndGet(-elapsed);
                                    
                                    tryAcquireAsync(time, permits, subscribeFuture, result);
                                }
                            }
                        }, t, TimeUnit.MILLISECONDS);
                        futureRef.set(scheduledFuture);
                    }
                }
            }
        });
        
    }
    
    private void acquireAsync(final int permits, final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<Void> result) {
        if (result.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }

        RFuture<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture);
                    result.tryFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    unsubscribe(subscribeFuture);
                    if (!result.trySuccess(null)) {
                        releaseAsync(permits);
                    }
                    return;
                }
                
                final RedissonLockEntry entry = getEntry();
                if (entry.getLatch().tryAcquire(permits)) {
                    acquireAsync(permits, subscribeFuture, result);
                } else {
                    Runnable listener = new Runnable() {
                        @Override
                        public void run() {
                            acquireAsync(permits, subscribeFuture, result);
                        }
                    };
                    entry.addListener(listener);
                }
            }
        });
    }

    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryAcquire(int permits) {
        return get(tryAcquireAsync(permits));
    }
    
    @Override
    public RFuture<Boolean> tryAcquireAsync() {
        return tryAcquireAsync(1);
    }
    
    @Override
    public RFuture<Boolean> tryAcquireAsync(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permits amount can't be negative");
        }
        if (permits == 0) {
            return RedissonPromise.newSucceededFuture(true);
        }

        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local value = redis.call('get', KEYS[1]); " +
                  "if (value ~= false and tonumber(value) >= tonumber(ARGV[1])) then " +
                      "local val = redis.call('decrby', KEYS[1], ARGV[1]); " +
                      "return 1; " +
                  "end; " +
                  "return 0;",
                  Collections.<Object>singletonList(getName()), permits);
    }

    @Override
    public RFuture<Boolean> tryAcquireAsync(long waitTime, TimeUnit unit) {
        return tryAcquireAsync(1, waitTime, unit);
    }
    
    @Override
    public boolean tryAcquire(int permits, long waitTime, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        long current = System.currentTimeMillis();

        if (tryAcquire(permits)) {
            return true;
        }

        time -= (System.currentTimeMillis() - current);
        if (time <= 0) {
            return false;
        }

        current = System.currentTimeMillis();
        RFuture<RedissonLockEntry> future = subscribe();
        if (!await(future, time, TimeUnit.MILLISECONDS)) {
            return false;
        }

        try {
            time -= (System.currentTimeMillis() - current);
            if (time <= 0) {
                return false;
            }
            
            while (true) {
                current = System.currentTimeMillis();
                if (tryAcquire(permits)) {
                    return true;
                }

                time -= (System.currentTimeMillis() - current);
                if (time <= 0) {
                    return false;
                }

                // waiting for message
                current = System.currentTimeMillis();

                getEntry().getLatch().tryAcquire(permits, time, TimeUnit.MILLISECONDS);

                time -= (System.currentTimeMillis() - current);
                if (time <= 0) {
                    return false;
                }
            }
        } finally {
            unsubscribe(future);
        }
//        return get(tryAcquireAsync(permits, waitTime, unit));
    }

    @Override
    public RFuture<Boolean> tryAcquireAsync(final int permits, long waitTime, TimeUnit unit) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        final AtomicLong time = new AtomicLong(unit.toMillis(waitTime));
        final long current = System.currentTimeMillis();
        RFuture<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    if (!result.trySuccess(true)) {
                        releaseAsync(permits);
                    }
                    return;
                }
                
                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);
                
                if (time.get() <= 0) {
                    result.trySuccess(false);
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
                        
                        if (time.get() < 0) {
                            unsubscribe(subscribeFuture);
                            result.trySuccess(false);
                            return;
                        }

                        tryAcquireAsync(time, permits, subscribeFuture, result);
                    }
                });
                
                if (!subscribeFuture.isDone()) {
                    Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            if (!subscribeFuture.isDone()) {
                                result.trySuccess(false);
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
    public boolean tryAcquire(long time, TimeUnit unit) throws InterruptedException {
        return tryAcquire(1, time, unit);
    }

    @Override
    public void release() {
        release(1);
    }

    @Override
    public void release(int permits) {
        get(releaseAsync(permits));
    }
    
    @Override
    public RFuture<Void> releaseAsync() {
        return releaseAsync(1);
    }
    
    @Override
    public RFuture<Void> releaseAsync(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permits amount can't be negative");
        }
        if (permits == 0) {
            return RedissonPromise.newSucceededFuture(null);
        }

        return commandExecutor.evalWriteAsync(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
            "local value = redis.call('incrby', KEYS[1], ARGV[1]); " +
            "redis.call('publish', KEYS[2], value); ",
            Arrays.<Object>asList(getName(), getChannelName()), permits);
    }


    @Override
    public int drainPermits() {
        RFuture<Long> future = commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false or value == 0) then " +
                    "return 0; " +
                "end; " +
                "redis.call('set', KEYS[1], 0); " +
                "return value;",
                Collections.<Object>singletonList(getName()));
        Long res = get(future);
        return res.intValue();
    }

    @Override
    public int availablePermits() {
        RFuture<Long> future = commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.GET_LONG, getName());
        Long res = get(future);
        return res.intValue();
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
    public void reducePermits(int permits) {
        get(reducePermitsAsync(permits));
    }
    
    @Override
    public RFuture<Void> reducePermitsAsync(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException();
        }
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false) then "
                  + "value = 0;"
              + "end;"
              + "redis.call('set', KEYS[1], value - ARGV[1]); ",
                Arrays.<Object>asList(getName(), getChannelName()), permits);
    }
    
}
