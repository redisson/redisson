/**
 * Copyright 2016 Nikita Koksharov
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.core.RSemaphore;
import org.redisson.pubsub.SemaphorePubSub;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.Semaphore}.
 * <p/>
 * Works in non-fair mode. Therefore order of acquiring is unpredictable.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSemaphore extends RedissonExpirable implements RSemaphore {

    final UUID id;

    private static final SemaphorePubSub PUBSUB = new SemaphorePubSub();

    final CommandExecutor commandExecutor;

    protected RedissonSemaphore(CommandExecutor commandExecutor, String name, UUID id) {
        super(commandExecutor, name);
        this.commandExecutor = commandExecutor;
        this.id = id;
    }

    private String getEntryName() {
        return id + ":" + getName();
    }

    String getChannelName() {
        return "redisson_semaphore__channel__{" + getName() + "}";
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

        Future<RedissonLockEntry> future = subscribe();
        get(future);
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
    public Future<Void> acquireAsync() {
        return acquireAsync(1);
    }
    
    @Override
    public Future<Void> acquireAsync(final int permits) {
        final Promise<Void> result = newPromise();
        Future<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.setFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    result.setSuccess(null);
                    return;
                }
                
                final Future<RedissonLockEntry> subscribeFuture = subscribe();
                subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                    @Override
                    public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.setFailure(future.cause());
                            return;
                        }

                        acquireAsync(permits, subscribeFuture, result);
                    }

                });
            }
        });
        return result;
    }
    
    private void tryAcquireAsync(final AtomicLong time, final int permits, final Future<RedissonLockEntry> subscribeFuture, final Promise<Boolean> result) {
        Future<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
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
                    result.trySuccess(true);
                    return;
                }
                
                if (time.get() < 0) {
                    unsubscribe(subscribeFuture);
                    result.trySuccess(false);
                    return;
                }

                // waiting for message
                final long current = System.currentTimeMillis();
                final RedissonLockEntry entry = getEntry();
                synchronized (entry) {
                    if (entry.getLatch().tryAcquire()) {
                        tryAcquireAsync(time, permits, subscribeFuture, result);
                    } else {
                        final AtomicBoolean executed = new AtomicBoolean();
                        final AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<ScheduledFuture<?>>();
                        final Runnable listener = new Runnable() {
                            @Override
                            public void run() {
                                executed.set(true);
                                if (futureRef.get() != null) {
                                    futureRef.get().cancel(false);
                                }
                                long elapsed = System.currentTimeMillis() - current;
                                time.addAndGet(-elapsed);

                                tryAcquireAsync(time, permits, subscribeFuture, result);
                            }
                        };
                        entry.addListener(listener);

                        long t = time.get();
                        if (!executed.get()) {
                            ScheduledFuture<?> scheduledFuture = commandExecutor.getConnectionManager().getGroup().schedule(new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (entry) {
                                        if (entry.removeListener(listener)) {
                                            long elapsed = System.currentTimeMillis() - current;
                                            time.addAndGet(-elapsed);

                                            tryAcquireAsync(time, permits, subscribeFuture, result);
                                        }
                                    }
                                }
                            }, t, TimeUnit.MILLISECONDS);
                            futureRef.set(scheduledFuture);
                        }
                    }
                }
            }
        });
        
    }
    
    private void acquireAsync(final int permits, final Future<RedissonLockEntry> subscribeFuture, final Promise<Void> result) {
        Future<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture);
                    result.setFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    unsubscribe(subscribeFuture);
                    result.setSuccess(null);
                    return;
                }
                
                final RedissonLockEntry entry = getEntry();
                synchronized (entry) {
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
    public Future<Boolean> tryAcquireAsync() {
        return tryAcquireAsync(1);
    }
    
    @Override
    public Future<Boolean> tryAcquireAsync(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permits amount can't be negative");
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
    public Future<Boolean> tryAcquireAsync(long waitTime, TimeUnit unit) {
        return tryAcquireAsync(1, waitTime, unit);
    }
    
    @Override
    public boolean tryAcquire(int permits, long waitTime, TimeUnit unit) throws InterruptedException {
        if (tryAcquire(permits)) {
            return true;
        }

        long time = unit.toMillis(waitTime);
        Future<RedissonLockEntry> future = subscribe();
        if (!await(future, time, TimeUnit.MILLISECONDS)) {
            return false;
        }

        try {
            while (true) {
                if (tryAcquire(permits)) {
                    return true;
                }

                if (time <= 0) {
                    return false;
                }

                // waiting for message
                long current = System.currentTimeMillis();

                getEntry().getLatch().tryAcquire(permits, time, TimeUnit.MILLISECONDS);

                long elapsed = System.currentTimeMillis() - current;
                time -= elapsed;
            }
        } finally {
            unsubscribe(future);
        }
//        return get(tryAcquireAsync(permits, waitTime, unit));
    }

    @Override
    public Future<Boolean> tryAcquireAsync(final int permits, long waitTime, TimeUnit unit) {
        final Promise<Boolean> result = newPromise();
        final AtomicLong time = new AtomicLong(unit.toMillis(waitTime));
        Future<Boolean> tryAcquireFuture = tryAcquireAsync(permits);
        tryAcquireFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.setFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    result.setSuccess(true);
                    return;
                }
                
                final long current = System.currentTimeMillis();
                final AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<ScheduledFuture<?>>();
                final Future<RedissonLockEntry> subscribeFuture = subscribe();
                subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                    @Override
                    public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.setFailure(future.cause());
                            return;
                        }
                        
                        if (futureRef.get() != null) {
                            futureRef.get().cancel(false);
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
                    ScheduledFuture<?> scheduledFuture = commandExecutor.getConnectionManager().getGroup().schedule(new Runnable() {
                        @Override
                        public void run() {
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
        return PUBSUB.getEntry(getEntryName());
    }

    private Future<RedissonLockEntry> subscribe() {
        return PUBSUB.subscribe(getEntryName(), getChannelName(), commandExecutor.getConnectionManager());
    }

    private void unsubscribe(Future<RedissonLockEntry> future) {
        PUBSUB.unsubscribe(future.getNow(), getEntryName(), getChannelName(), commandExecutor.getConnectionManager());
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
    public Future<Void> releaseAsync() {
        return releaseAsync(1);
    }
    
    @Override
    public Future<Void> releaseAsync(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permits amount can't be negative");
        }

        return commandExecutor.evalWriteAsync(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
            "local value = redis.call('incrby', KEYS[1], ARGV[1]); " +
            "redis.call('publish', KEYS[2], value); ",
            Arrays.<Object>asList(getName(), getChannelName()), permits);
    }


    @Override
    public int drainPermits() {
        Long res = commandExecutor.evalWrite(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false or value == 0) then " +
                    "return 0; " +
                "end; " +
                "redis.call('set', KEYS[1], 0); " +
                "return value;",
                Collections.<Object>singletonList(getName()));
        return res.intValue();
    }

    @Override
    public int availablePermits() {
        Long res = commandExecutor.write(getName(), LongCodec.INSTANCE, RedisCommands.GET_LONG, getName());
        return res.intValue();
    }

    @Override
    public void setPermits(int permits) {
        get(setPermitsAsync(permits));
    }
    
    @Override
    public Future<Void> setPermitsAsync(int permits) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false or value == 0) then "
                    + "redis.call('set', KEYS[1], ARGV[1]); "
                    + "redis.call('publish', KEYS[2], ARGV[1]); "
                + "end;",
                Arrays.<Object>asList(getName(), getChannelName()), permits);
    }

}
