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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RBlockingFairQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.misc.RPromise;
import org.redisson.pubsub.SemaphorePubSub;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBlockingFairQueue<V> extends RedissonBlockingQueue<V> implements RBlockingFairQueue<V> {

    public static final long TIMEOUT_SECONDS = 30;
    
    private final UUID id;
    private final AtomicInteger instances = new AtomicInteger();
    private final SemaphorePubSub semaphorePubSub;
    
    protected RedissonBlockingFairQueue(CommandExecutor commandExecutor, String name, SemaphorePubSub semaphorePubSub, UUID id, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
        this.semaphorePubSub = semaphorePubSub;
        this.id = id;
        instances.incrementAndGet();
    }

    protected RedissonBlockingFairQueue(Codec codec, CommandExecutor commandExecutor, String name, SemaphorePubSub semaphorePubSub, UUID id, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
        this.semaphorePubSub = semaphorePubSub;
        this.id = id;
        instances.incrementAndGet();
    }
    
    private String getIdsListName() {
        return suffixName(getName(), "list");
    }
    
    private String getTimeoutName() {
        return suffixName(getName(), "timeout");
    }
    
    private String getChannelName() {
        return suffixName(getName(), getCurrentId() + ":channel");
    }
    
    private RedissonLockEntry getEntry() {
        return semaphorePubSub.getEntry(getName());
    }

    private RFuture<RedissonLockEntry> subscribe() {
        return semaphorePubSub.subscribe(getName(), getChannelName(), commandExecutor.getConnectionManager());
    }

    private void unsubscribe(RFuture<RedissonLockEntry> future) {
        semaphorePubSub.unsubscribe(future.getNow(), getName(), getChannelName(), commandExecutor.getConnectionManager());
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), getIdsListName(), getTimeoutName());
    }
    
    private Long tryAcquire() {
        return get(tryAcquireAsync());
    }
    
    private RFuture<Long> tryAcquireAsync() {
        long timeout = System.currentTimeMillis() + TIMEOUT_SECONDS*1000; 
        
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG,

                  "local timeout = redis.call('get', KEYS[3]);"
                + "if timeout ~= false and tonumber(timeout) <= tonumber(ARGV[3]) then "
                    + "redis.call('lpop', KEYS[2]); "
                    + "local nextValue = redis.call('lindex', KEYS[2], 0); "
                    + "if nextValue ~= false and nextValue ~= ARGV[1] then "
                        + "redis.call('set', KEYS[3], ARGV[2]);"
                        + "redis.call('publish', '{' .. KEYS[1] .. '}:' .. nextValue .. ':channel', 1);"
                    + "end; "
                + "end; "

                + "local items = redis.call('lrange', KEYS[2], 0, -1) " 
                + "local found = false; " 
                + "for i=1,#items do " 
                   + "if items[i] == ARGV[1] then " 
                      + "found = true; "  
                      + "break;" 
                   + "end; " 
                + "end; "
                
                + "if found == false then "
                    + "redis.call('lpush', KEYS[2], ARGV[1]); "
                + "end; "
                    
                + "local value = redis.call('lindex', KEYS[2], 0); "
                + "if value == ARGV[1] then "
                    + "redis.call('set', KEYS[3], ARGV[2]);"
                    + "local size = redis.call('llen', KEYS[2]); "
                    + "if size > 1 then "
                        + "redis.call('lpop', KEYS[2]);"
                        + "redis.call('rpush', KEYS[2], value);"
                        + "local nextValue = redis.call('lindex', KEYS[2], 0); "
                        + "redis.call('publish', '{' .. KEYS[1] .. '}:' .. nextValue .. ':channel', 1);"
                    + "end; "
                    + "return nil;"
                + "end;"
                + "return tonumber(timeout) - tonumber(ARGV[3]);",
                  Arrays.<Object>asList(getName(), getIdsListName(), getTimeoutName()), getCurrentId(), timeout, System.currentTimeMillis());
    }

    private String getCurrentId() {
        return id.toString();
    }

    
    @Override
    public V take() throws InterruptedException {
        Long currentTimeout = tryAcquire();
        if (currentTimeout == null) {
            return super.take();
        }

        RFuture<RedissonLockEntry> future = subscribe();
        commandExecutor.syncSubscription(future);
        try {
            while (true) {
                currentTimeout = tryAcquire();
                if (currentTimeout == null) {
                    return super.take();
                }

                getEntry().getLatch().tryAcquire(currentTimeout, TimeUnit.MILLISECONDS);
            }
        } finally {
            unsubscribe(future);
        }
    }
    
    @Override
    public void destroy() {
        if (instances.decrementAndGet() == 0) {
            get(commandExecutor.evalWriteAsync(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID_WITH_VALUES,
                    "for i = 1, #ARGV, 1 do "
                        + "redis.call('lrem', KEYS[1], 0, ARGV[i]);"
                    +"end; ",
            Collections.<Object>singletonList(getIdsListName()), getCurrentId()));
        }
    }
    
    @Override
    public RFuture<V> takeAsync() {
        final RPromise<V> promise = newPromise();

        RFuture<Long> tryAcquireFuture = tryAcquireAsync();
        tryAcquireFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                final Long currentTimeout = future.getNow();
                if (currentTimeout == null) {
                    final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.takeAsync();
                    pollFuture.addListener(new FutureListener<V>() {
                        @Override
                        public void operationComplete(Future<V> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.tryFailure(future.cause());
                                return;
                            }
                            
                            promise.trySuccess(future.getNow());
                        }
                    });
                } else {
                    final RFuture<RedissonLockEntry> subscribeFuture = subscribe();
                    final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                    subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                        @Override
                        public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.tryFailure(future.cause());
                                return;
                            }

                            if (futureRef.get() != null) {
                                futureRef.get().cancel();
                            }

                            tryTakeAsync(subscribeFuture, promise);
                        }
                    });
                }
            }
        });
        
        return promise;
    }
    
    @Override
    public V poll() {
        Long currentTimeout = tryAcquire();
        if (currentTimeout == null) {
            return super.poll();
        }

        return null;
    }
    
    @Override
    public RFuture<V> pollAsync() {
        final RPromise<V> promise = newPromise();

        RFuture<Long> tryAcquireFuture = tryAcquireAsync();
        tryAcquireFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                final Long currentTimeout = future.getNow();
                if (currentTimeout == null) {
                    final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollAsync();
                    pollFuture.addListener(new FutureListener<V>() {
                        @Override
                        public void operationComplete(Future<V> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.tryFailure(future.cause());
                                return;
                            }
                            
                            promise.trySuccess(future.getNow());
                        }
                    });
                } else {
                    promise.trySuccess(null);
                }
            }
        });
        
        return promise;
    }
    
    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        Long currentTimeout = tryAcquire();
        if (currentTimeout == null) {
            long spentTime = System.currentTimeMillis() - startTime;
            long remainTime = unit.toMillis(timeout) - spentTime;
            if (remainTime > 0) {
                return super.poll(remainTime, TimeUnit.MILLISECONDS);
            }
            return null;
        }

        RFuture<RedissonLockEntry> future = subscribe();
        long spentTime = System.currentTimeMillis() - startTime;
        long remainTime = unit.toMillis(timeout) - spentTime;
        if (!future.awaitUninterruptibly(remainTime, TimeUnit.MILLISECONDS)) {
            return null;
        }

        try {
            while (true) {
                currentTimeout = tryAcquire();
                if (currentTimeout == null) {
                    spentTime = System.currentTimeMillis() - startTime;
                    remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        return super.poll(remainTime, TimeUnit.MILLISECONDS);
                    }
                    return null;
                }

                spentTime = System.currentTimeMillis() - startTime;
                remainTime = unit.toMillis(timeout) - spentTime;
                remainTime = Math.min(remainTime, currentTimeout);
                if (remainTime <= 0 || !getEntry().getLatch().tryAcquire(remainTime, TimeUnit.MILLISECONDS)) {
                    return null;
                }
            }
        } finally {
            unsubscribe(future);
        }
    }
    
    @Override
    public RFuture<V> pollAsync(final long timeout, final TimeUnit unit) {
        final long startTime = System.currentTimeMillis();
        final RPromise<V> promise = newPromise();

        RFuture<Long> tryAcquireFuture = tryAcquireAsync();
        tryAcquireFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                Long currentTimeout = future.getNow();
                if (currentTimeout == null) {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollAsync(remainTime, TimeUnit.MILLISECONDS);
                        pollFuture.addListener(new FutureListener<V>() {
                            @Override
                            public void operationComplete(Future<V> future) throws Exception {
                                if (!future.isSuccess()) {
                                    promise.tryFailure(future.cause());
                                    return;
                                }
                                
                                promise.trySuccess(future.getNow());
                            }
                        });
                    } else {
                        promise.trySuccess(null);
                    }
                } else {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    remainTime = Math.min(remainTime, currentTimeout);
                    if (remainTime <= 0) {
                        promise.trySuccess(null);
                        return;
                    }
                    
                    final RFuture<RedissonLockEntry> subscribeFuture = subscribe();
                    final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                    subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                        @Override
                        public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.tryFailure(future.cause());
                                return;
                            }

                            if (futureRef.get() != null) {
                                futureRef.get().cancel();
                            }

                            tryPollAsync(startTime, timeout, unit, subscribeFuture, promise);
                        }
                    });
                    if (!subscribeFuture.isDone()) {
                        Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                            @Override
                            public void run(Timeout timeout) throws Exception {
                                if (!subscribeFuture.isDone()) {
                                    subscribeFuture.cancel(false);
                                    promise.trySuccess(null);
                                }
                            }
                        }, remainTime, TimeUnit.MILLISECONDS);
                        futureRef.set(scheduledFuture);
                    }
                }
            }
        });
        
        return promise;
    }
    
    private void tryTakeAsync(final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<V> promise) {
        if (promise.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }
        
        RFuture<Long> tryAcquireFuture = tryAcquireAsync();
        tryAcquireFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture);
                    promise.tryFailure(future.cause());
                    return;
                }

                Long currentTimeout = future.getNow();
                if (currentTimeout == null) {
                    final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.takeAsync();
                    pollFuture.addListener(new FutureListener<V>() {
                        @Override
                        public void operationComplete(Future<V> future) throws Exception {
                            unsubscribe(subscribeFuture);
                            if (!future.isSuccess()) {
                                promise.tryFailure(future.cause());
                                return;
                            }
                            
                            promise.trySuccess(future.getNow());
                        }
                    });
                } else {
                    final RedissonLockEntry entry = getEntry();
                    synchronized (entry) {
                        if (entry.getLatch().tryAcquire()) {
                            tryTakeAsync(subscribeFuture, promise);
                        } else {
                            final AtomicBoolean executed = new AtomicBoolean();
                            final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                            final Runnable listener = new Runnable() {
                                @Override
                                public void run() {
                                    executed.set(true);
                                    if (futureRef.get() != null) {
                                        futureRef.get().cancel();
                                    }

                                    tryTakeAsync(subscribeFuture, promise);
                                }
                            };
                            entry.addListener(listener);

                            if (!executed.get()) {
                                Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                                    @Override
                                    public void run(Timeout t) throws Exception {
                                        synchronized (entry) {
                                            if (entry.removeListener(listener)) {
                                                tryTakeAsync(subscribeFuture, promise);
                                            }
                                        }
                                    }
                                }, currentTimeout, TimeUnit.MILLISECONDS);
                                futureRef.set(scheduledFuture);
                            }
                        }
                    }
                }
            };
        });
    }

    private void tryPollAsync(final long startTime, final long timeout, final TimeUnit unit,
            final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<V> promise) {
        if (promise.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }
        
        long spentTime = System.currentTimeMillis() - startTime;
        long remainTime = unit.toMillis(timeout) - spentTime;
        if (remainTime <= 0) {
            unsubscribe(subscribeFuture);
            promise.trySuccess(null);
            return;
        }
        
        RFuture<Long> tryAcquireFuture = tryAcquireAsync();
        tryAcquireFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture);
                    promise.tryFailure(future.cause());
                    return;
                }

                Long currentTimeout = future.getNow();
                if (currentTimeout == null) {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollAsync(remainTime, TimeUnit.MILLISECONDS);
                        pollFuture.addListener(new FutureListener<V>() {
                            @Override
                            public void operationComplete(Future<V> future) throws Exception {
                                unsubscribe(subscribeFuture);
                                if (!future.isSuccess()) {
                                    promise.tryFailure(future.cause());
                                    return;
                                }
                                
                                promise.trySuccess(future.getNow());
                            }
                        });
                    } else {
                        unsubscribe(subscribeFuture);
                        promise.trySuccess(null);
                    }
                } else {
                    final RedissonLockEntry entry = getEntry();
                    synchronized (entry) {
                        if (entry.getLatch().tryAcquire()) {
                            tryPollAsync(startTime, timeout, unit, subscribeFuture, promise);
                        } else {
                            final AtomicBoolean executed = new AtomicBoolean();
                            final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                            final Runnable listener = new Runnable() {
                                @Override
                                public void run() {
                                    executed.set(true);
                                    if (futureRef.get() != null) {
                                        futureRef.get().cancel();
                                    }

                                    tryPollAsync(startTime, timeout, unit, subscribeFuture, promise);
                                }
                            };
                            entry.addListener(listener);

                            if (!executed.get()) {
                                long spentTime = System.currentTimeMillis() - startTime;
                                long remainTime = unit.toMillis(timeout) - spentTime;
                                Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                                    @Override
                                    public void run(Timeout t) throws Exception {
                                        synchronized (entry) {
                                            if (entry.removeListener(listener)) {
                                                tryPollAsync(startTime, timeout, unit, subscribeFuture, promise);
                                            }
                                        }
                                    }
                                }, remainTime, TimeUnit.MILLISECONDS);
                                futureRef.set(scheduledFuture);
                            }
                        }
                    }
                }
            };
        });
    }
    
    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        Long currentTimeout = tryAcquire();
        if (currentTimeout == null) {
            long spentTime = System.currentTimeMillis() - startTime;
            long remainTime = unit.toMillis(timeout) - spentTime;
            if (remainTime > 0) {
                return super.pollLastAndOfferFirstTo(queueName, remainTime, TimeUnit.MILLISECONDS);
            }
            return null;
        }

        RFuture<RedissonLockEntry> future = subscribe();
        long spentTime = System.currentTimeMillis() - startTime;
        long remainTime = unit.toMillis(timeout) - spentTime;
        if (!future.awaitUninterruptibly(remainTime, TimeUnit.MILLISECONDS)) {
            return null;
        }

        try {
            while (true) {
                currentTimeout = tryAcquire();
                if (currentTimeout == null) {
                    spentTime = System.currentTimeMillis() - startTime;
                    remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        return super.pollLastAndOfferFirstTo(queueName, remainTime, TimeUnit.MILLISECONDS);
                    }
                    return null;
                }

                spentTime = System.currentTimeMillis() - startTime;
                remainTime = unit.toMillis(timeout) - spentTime;
                remainTime = Math.min(remainTime, currentTimeout);
                if (remainTime <= 0 || !getEntry().getLatch().tryAcquire(remainTime, TimeUnit.MILLISECONDS)) {
                    return null;
                }
            }
        } finally {
            unsubscribe(future);
        }
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(final String queueName, final long timeout, final TimeUnit unit) {
        final long startTime = System.currentTimeMillis();
        final RPromise<V> promise = newPromise();

        RFuture<Long> tryAcquireFuture = tryAcquireAsync();
        tryAcquireFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                Long currentTimeout = future.getNow();
                if (currentTimeout == null) {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollLastAndOfferFirstToAsync(queueName, remainTime, TimeUnit.MILLISECONDS);
                        pollFuture.addListener(new FutureListener<V>() {
                            @Override
                            public void operationComplete(Future<V> future) throws Exception {
                                if (!future.isSuccess()) {
                                    promise.tryFailure(future.cause());
                                    return;
                                }
                                
                                promise.trySuccess(future.getNow());
                            }
                        });
                    } else {
                        promise.trySuccess(null);
                    }
                } else {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    remainTime = Math.min(remainTime, currentTimeout);
                    if (remainTime <= 0) {
                        promise.trySuccess(null);
                        return;
                    }
                    
                    final RFuture<RedissonLockEntry> subscribeFuture = subscribe();
                    final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                    subscribeFuture.addListener(new FutureListener<RedissonLockEntry>() {
                        @Override
                        public void operationComplete(Future<RedissonLockEntry> future) throws Exception {
                            if (!future.isSuccess()) {
                                promise.tryFailure(future.cause());
                                return;
                            }

                            if (futureRef.get() != null) {
                                futureRef.get().cancel();
                            }

                            tryPollLastAndOfferFirstToAsync(startTime, timeout, unit, subscribeFuture, promise, queueName);
                        }
                    });
                    if (!subscribeFuture.isDone()) {
                        Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                            @Override
                            public void run(Timeout timeout) throws Exception {
                                if (!subscribeFuture.isDone()) {
                                    subscribeFuture.cancel(false);
                                    promise.trySuccess(null);
                                }
                            }
                        }, remainTime, TimeUnit.MILLISECONDS);
                        futureRef.set(scheduledFuture);
                    }
                }
            }
        });
        
        return promise;
    }

    private void tryPollLastAndOfferFirstToAsync(final long startTime, final long timeout, final TimeUnit unit,
            final RFuture<RedissonLockEntry> subscribeFuture, final RPromise<V> promise, final String queueName) {
        if (promise.isDone()) {
            unsubscribe(subscribeFuture);
            return;
        }
        
        long spentTime = System.currentTimeMillis() - startTime;
        long remainTime = unit.toMillis(timeout) - spentTime;
        if (remainTime <= 0) {
            unsubscribe(subscribeFuture);
            promise.trySuccess(null);
            return;
        }
        
        RFuture<Long> tryAcquireFuture = tryAcquireAsync();
        tryAcquireFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    unsubscribe(subscribeFuture);
                    promise.tryFailure(future.cause());
                    return;
                }

                Long currentTimeout = future.getNow();
                if (currentTimeout == null) {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollLastAndOfferFirstToAsync(queueName, remainTime, TimeUnit.MILLISECONDS);
                        pollFuture.addListener(new FutureListener<V>() {
                            @Override
                            public void operationComplete(Future<V> future) throws Exception {
                                unsubscribe(subscribeFuture);
                                if (!future.isSuccess()) {
                                    promise.tryFailure(future.cause());
                                    return;
                                }
                                
                                promise.trySuccess(future.getNow());
                            }
                        });
                    } else {
                        unsubscribe(subscribeFuture);
                        promise.trySuccess(null);
                    }
                } else {
                    final RedissonLockEntry entry = getEntry();
                    synchronized (entry) {
                        if (entry.getLatch().tryAcquire()) {
                            tryPollAsync(startTime, timeout, unit, subscribeFuture, promise);
                        } else {
                            final AtomicBoolean executed = new AtomicBoolean();
                            final AtomicReference<Timeout> futureRef = new AtomicReference<Timeout>();
                            final Runnable listener = new Runnable() {
                                @Override
                                public void run() {
                                    executed.set(true);
                                    if (futureRef.get() != null) {
                                        futureRef.get().cancel();
                                    }

                                    tryPollLastAndOfferFirstToAsync(startTime, timeout, unit, subscribeFuture, promise, queueName);
                                }
                            };
                            entry.addListener(listener);

                            if (!executed.get()) {
                                long spentTime = System.currentTimeMillis() - startTime;
                                long remainTime = unit.toMillis(timeout) - spentTime;
                                Timeout scheduledFuture = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                                    @Override
                                    public void run(Timeout t) throws Exception {
                                        synchronized (entry) {
                                            if (entry.removeListener(listener)) {
                                                tryPollLastAndOfferFirstToAsync(startTime, timeout, unit, subscribeFuture, promise, queueName);
                                            }
                                        }
                                    }
                                }, remainTime, TimeUnit.MILLISECONDS);
                                futureRef.set(scheduledFuture);
                            }
                        }
                    }
                }
            };
        });
    }

    
}
