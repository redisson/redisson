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

import static org.redisson.client.protocol.RedisCommands.LREM_SINGLE;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBlockingFairQueue;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.pubsub.SemaphorePubSub;

import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBlockingFairQueue<V> extends RedissonBlockingQueue<V> implements RBlockingFairQueue<V> {

    private final Set<String> usedIds = Collections.newSetFromMap(PlatformDependent.<String, Boolean>newConcurrentHashMap());
    private final UUID id;
    private final SemaphorePubSub semaphorePubSub;
    
    protected RedissonBlockingFairQueue(CommandExecutor commandExecutor, String name, SemaphorePubSub semaphorePubSub, UUID id) {
        super(commandExecutor, name);
        this.semaphorePubSub = semaphorePubSub;
        this.id = id;
    }

    protected RedissonBlockingFairQueue(Codec codec, CommandExecutor commandExecutor, String name, SemaphorePubSub semaphorePubSub, UUID id) {
        super(codec, commandExecutor, name);
        this.semaphorePubSub = semaphorePubSub;
        this.id = id;
    }
    
    private String getIdsListName() {
        return "{" + getName() + "}:list";
    }
    
    private String getChannelName() {
        return "{" + getName() + "}:" + getCurrentId() + ":channel";
    }
    
    private RedissonLockEntry getEntry() {
        return semaphorePubSub.getEntry(getName() + ":" + getCurrentId());
    }

    private RFuture<RedissonLockEntry> subscribe() {
        return semaphorePubSub.subscribe(getName() + ":" + getCurrentId(), getChannelName(), commandExecutor.getConnectionManager());
    }

    private void unsubscribe(RFuture<RedissonLockEntry> future) {
        semaphorePubSub.unsubscribe(future.getNow(), getName() + ":" + getCurrentId(), getChannelName(), commandExecutor.getConnectionManager());
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), getIdsListName());
    }
    
    private boolean tryAcquire() {
        return get(tryAcquireAsync());
    }
    
    private RFuture<Boolean> tryAcquireAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local items = redis.call('lrange', KEYS[2], 0, -1) " +
                "local found = false; " +
                "for i=1,#items do " +
                    "if items[i] == ARGV[1] then " +
                        "found = true; " + 
                        "break;" +
                    "end; " +
                "end; "
                + "if found == false then "
                    + "redis.call('rpush', KEYS[2], ARGV[1]); "
                + "end; "
                + "local value = redis.call('lindex', KEYS[2], 0); "
                + "local size = redis.call('llen', KEYS[2]); "
                + "if value ~= false and value == ARGV[1] then "
                    + "if size > 1 then "
                        + "redis.call('lpop', KEYS[2]);"
                        + "redis.call('rpush', KEYS[2], value);"
                        + "local nextValue = redis.call('lindex', KEYS[2], 0); "
                        + "redis.call('publish', '{' .. KEYS[1] .. '}:' .. nextValue .. ':channel', 1);"
                    + "end; "
                    + "return 1;"
                + "end;" +
                  "return 0;",
                  Arrays.<Object>asList(getName(), getIdsListName()), getCurrentId());
    }

    private String getCurrentId() {
        String currentId = id + "-" + Thread.currentThread().getId();
        usedIds.add(currentId);
        return currentId;
    }

    
    @Override
    public V take() throws InterruptedException {
        if (tryAcquire()) {
            return super.take();
        }

        RFuture<RedissonLockEntry> future = subscribe();
        commandExecutor.syncSubscription(future);
        try {
            while (true) {
                if (tryAcquire()) {
                    return super.take();
                }

                getEntry().getLatch().acquire(1);
            }
        } finally {
            unsubscribe(future);
        }
    }
    
    @Override
    public void destroy() {
        commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_VOID_WITH_VALUES,
                 "for i = 1, #ARGV, 1 do "
                    + "redis.call('lrem', KEYS[1], 0, ARGV[i]);"
                +"end; ",
        Collections.<Object>singletonList(getIdsListName()), usedIds.toArray());
    }
    
//    @Override
//    public RFuture<V> takeAsync() {
//        final RPromise<V> promise = newPromise();
//        final long threadId = Thread.currentThread().getId();
//        RFuture<Void> lockFuture = fairLock.lockAsync();
//        lockFuture.addListener(new FutureListener<Void>() {
//            @Override
//            public void operationComplete(Future<Void> future) throws Exception {
//                if (!future.isSuccess()) {
//                    promise.tryFailure(future.cause());
//                    return;
//                }
//                
//                final RFuture<V> takeFuture = takeAsync();
//                takeFuture.addListener(new FutureListener<V>() {
//                    @Override
//                    public void operationComplete(Future<V> future) throws Exception {
//                        RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
//                        unlockFuture.addListener(new FutureListener<Void>() {
//                            @Override
//                            public void operationComplete(Future<Void> future) throws Exception {
//                                if (!future.isSuccess()) {
//                                    promise.tryFailure(future.cause());
//                                    return;
//                                }
//                                
//                                if (!takeFuture.isSuccess()) {
//                                    promise.tryFailure(takeFuture.cause());
//                                    return;
//                                }
//                                
//                                promise.trySuccess(takeFuture.getNow());
//                            }
//                        });
//                    }
//                });
//            }
//        });
//
//        return promise;
//        return null;
//    }
    
    @Override
    public V poll() {
        if (tryAcquire()) {
            return super.poll();
        }

        RFuture<RedissonLockEntry> future = subscribe();
        commandExecutor.syncSubscription(future);
        try {
            while (true) {
                if (tryAcquire()) {
                    return super.poll();
                }

                getEntry().getLatch().acquireUninterruptibly(1);
            }
        } finally {
            unsubscribe(future);
        }
    }
    
//    @Override
//    public RFuture<V> pollAsync() {
//        final RPromise<V> promise = newPromise();
//        final long threadId = Thread.currentThread().getId();
//        RFuture<Boolean> tryLockFuture = fairLock.tryLockAsync();
//        tryLockFuture.addListener(new FutureListener<Boolean>() {
//            @Override
//            public void operationComplete(Future<Boolean> future) throws Exception {
//                if (!future.isSuccess()) {
//                    promise.tryFailure(future.cause());
//                    return;
//                }
//
//                if (future.getNow()) {
//                    final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollAsync();
//                    pollFuture.addListener(new FutureListener<V>() {
//                        @Override
//                        public void operationComplete(Future<V> future) throws Exception {
//                            RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
//                            unlockFuture.addListener(new FutureListener<Void>() {
//                                @Override
//                                public void operationComplete(Future<Void> future) throws Exception {
//                                    if (!future.isSuccess()) {
//                                        promise.tryFailure(future.cause());
//                                        return;
//                                    }
//                                    
//                                    if (!pollFuture.isSuccess()) {
//                                        promise.tryFailure(pollFuture.cause());
//                                        return;
//                                    }
//                                    
//                                    promise.trySuccess(pollFuture.getNow());
//                                }
//                            });
//                        }
//                    });
//                } else {
//                    promise.trySuccess(null);
//                }
//            }
//        });
//        
//        return promise;
//        return null;
//    }

    
    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        if (tryAcquire()) {
            long spentTime = System.currentTimeMillis() - startTime;
            long remainTime = unit.toMillis(timeout) - spentTime;
            if (remainTime > 0) {
                return super.poll(remainTime, TimeUnit.MILLISECONDS);
            }
            return null;
        }

        RFuture<RedissonLockEntry> future = subscribe();
        commandExecutor.syncSubscription(future);
        try {
            while (true) {
                if (tryAcquire()) {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        return super.poll(remainTime, TimeUnit.MILLISECONDS);
                    }
                    return null;
                }

                getEntry().getLatch().acquire(1);
            }
        } finally {
            unsubscribe(future);
        }
    }
    
//    @Override
//    public RFuture<V> pollAsync(final long timeout, final TimeUnit unit) {
//        final long startTime = System.currentTimeMillis();
//        final RPromise<V> promise = newPromise();
//        final long threadId = Thread.currentThread().getId();
//        RFuture<Boolean> tryLockFuture = fairLock.tryLockAsync(timeout, unit);
//        tryLockFuture.addListener(new FutureListener<Boolean>() {
//            @Override
//            public void operationComplete(Future<Boolean> future) throws Exception {
//                if (!future.isSuccess()) {
//                    promise.tryFailure(future.cause());
//                    return;
//                }
//
//                if (future.getNow()) {
//                    long spentTime = System.currentTimeMillis() - startTime;
//                    long remainTime = unit.toMillis(timeout) - spentTime;
//                    if (remainTime > 0) {
//                        final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollAsync(remainTime, TimeUnit.MILLISECONDS);
//                        pollFuture.addListener(new FutureListener<V>() {
//                            @Override
//                            public void operationComplete(Future<V> future) throws Exception {
//                                RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
//                                unlockFuture.addListener(new FutureListener<Void>() {
//                                    @Override
//                                    public void operationComplete(Future<Void> future) throws Exception {
//                                        if (!future.isSuccess()) {
//                                            promise.tryFailure(future.cause());
//                                            return;
//                                        }
//                                        
//                                        if (!pollFuture.isSuccess()) {
//                                            promise.tryFailure(pollFuture.cause());
//                                            return;
//                                        }
//                                        
//                                        promise.trySuccess(pollFuture.getNow());
//                                    }
//                                });
//                            }
//                        });
//                    } else {
//                        RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
//                        unlockFuture.addListener(new FutureListener<Void>() {
//                            @Override
//                            public void operationComplete(Future<Void> future) throws Exception {
//                                if (!future.isSuccess()) {
//                                    promise.tryFailure(future.cause());
//                                    return;
//                                }
//
//                                promise.trySuccess(null);
//                            }
//                        });
//                    }
//                } else {
//                    promise.trySuccess(null);
//                }
//            }
//        });
//        
//        return promise;
//        return null;
//    }

//    @Override
//    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
//        long startTime = System.currentTimeMillis();
//        if (fairLock.tryLock(timeout, unit)) {
//            try {
//                long spentTime = System.currentTimeMillis() - startTime;
//                long remainTime = unit.toMillis(timeout) - spentTime;
//                if (remainTime > 0) {
//                    return super.pollLastAndOfferFirstTo(queueName, remainTime, TimeUnit.MILLISECONDS);
//                }
//                return null;
//            } finally {
//                fairLock.unlock();
//            }
//        }
//        return null;
//    }

//    @Override
//    public RFuture<V> pollLastAndOfferFirstToAsync(final String queueName, final long timeout, final TimeUnit unit) {
//        final long startTime = System.currentTimeMillis();
//        final RPromise<V> promise = newPromise();
//        final long threadId = Thread.currentThread().getId();
//        RFuture<Boolean> tryLockFuture = fairLock.tryLockAsync(timeout, unit);
//        tryLockFuture.addListener(new FutureListener<Boolean>() {
//            @Override
//            public void operationComplete(Future<Boolean> future) throws Exception {
//                if (!future.isSuccess()) {
//                    promise.tryFailure(future.cause());
//                    return;
//                }
//
//                if (future.getNow()) {
//                    long spentTime = System.currentTimeMillis() - startTime;
//                    long remainTime = unit.toMillis(timeout) - spentTime;
//                    if (remainTime > 0) {
//                        final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollLastAndOfferFirstToAsync(queueName, remainTime, TimeUnit.MILLISECONDS);
//                        pollFuture.addListener(new FutureListener<V>() {
//                            @Override
//                            public void operationComplete(Future<V> future) throws Exception {
//                                RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
//                                unlockFuture.addListener(new FutureListener<Void>() {
//                                    @Override
//                                    public void operationComplete(Future<Void> future) throws Exception {
//                                        if (!future.isSuccess()) {
//                                            promise.tryFailure(future.cause());
//                                            return;
//                                        }
//                                        
//                                        if (!pollFuture.isSuccess()) {
//                                            promise.tryFailure(pollFuture.cause());
//                                            return;
//                                        }
//                                        
//                                        promise.trySuccess(pollFuture.getNow());
//                                    }
//                                });
//                            }
//                        });
//                    } else {
//                        RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
//                        unlockFuture.addListener(new FutureListener<Void>() {
//                            @Override
//                            public void operationComplete(Future<Void> future) throws Exception {
//                                if (!future.isSuccess()) {
//                                    promise.tryFailure(future.cause());
//                                    return;
//                                }
//
//                                promise.trySuccess(null);
//                            }
//                        });
//                    }
//                } else {
//                    promise.trySuccess(null);
//                }
//            }
//        });
//        
//        return promise;
//        return null;
//    }

}
