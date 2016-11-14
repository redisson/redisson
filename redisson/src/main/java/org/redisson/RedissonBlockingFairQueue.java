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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBlockingFairQueue;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.misc.RPromise;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBlockingFairQueue<V> extends RedissonBlockingQueue<V> implements RBlockingFairQueue<V> {

    private final RedissonFairLock fairLock;
    
    protected RedissonBlockingFairQueue(CommandExecutor commandExecutor, String name, UUID id) {
        super(commandExecutor, name);
        String lockName = prefixName("redisson_bfq_lock", name);
        fairLock = new RedissonFairLock(commandExecutor, lockName, id);
    }

    protected RedissonBlockingFairQueue(Codec codec, CommandExecutor commandExecutor, String name, UUID id) {
        super(codec, commandExecutor, name);
        String lockName = prefixName("redisson_bfq_lock", name);
        fairLock = new RedissonFairLock(commandExecutor, lockName, id);
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), fairLock.getName(), fairLock.getThreadsQueueName(), fairLock.getTimeoutSetName());
    }
    
    @Override
    public V take() throws InterruptedException {
        fairLock.lockInterruptibly();
        try {
            return super.take();
        } finally {
            fairLock.unlock();
        }
    }
    
    @Override
    public RFuture<V> takeAsync() {
        final RPromise<V> promise = newPromise();
        final long threadId = Thread.currentThread().getId();
        RFuture<Void> lockFuture = fairLock.lockAsync();
        lockFuture.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }
                
                final RFuture<V> takeFuture = takeAsync();
                takeFuture.addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
                        unlockFuture.addListener(new FutureListener<Void>() {
                            @Override
                            public void operationComplete(Future<Void> future) throws Exception {
                                if (!future.isSuccess()) {
                                    promise.tryFailure(future.cause());
                                    return;
                                }
                                
                                if (!takeFuture.isSuccess()) {
                                    promise.tryFailure(takeFuture.cause());
                                    return;
                                }
                                
                                promise.trySuccess(takeFuture.getNow());
                            }
                        });
                    }
                });
            }
        });

        return promise;
    }
    
    @Override
    public V poll() {
        if (fairLock.tryLock()) {
            try {
                return super.poll();
            } finally {
                fairLock.unlock();
            }
        }
        return null;
    }
    
    @Override
    public RFuture<V> pollAsync() {
        final RPromise<V> promise = newPromise();
        final long threadId = Thread.currentThread().getId();
        RFuture<Boolean> tryLockFuture = fairLock.tryLockAsync();
        tryLockFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollAsync();
                    pollFuture.addListener(new FutureListener<V>() {
                        @Override
                        public void operationComplete(Future<V> future) throws Exception {
                            RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
                            unlockFuture.addListener(new FutureListener<Void>() {
                                @Override
                                public void operationComplete(Future<Void> future) throws Exception {
                                    if (!future.isSuccess()) {
                                        promise.tryFailure(future.cause());
                                        return;
                                    }
                                    
                                    if (!pollFuture.isSuccess()) {
                                        promise.tryFailure(pollFuture.cause());
                                        return;
                                    }
                                    
                                    promise.trySuccess(pollFuture.getNow());
                                }
                            });
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
        if (fairLock.tryLock(timeout, unit)) {
            try {
                long spentTime = System.currentTimeMillis() - startTime;
                long remainTime = unit.toMillis(timeout) - spentTime;
                if (remainTime > 0) {
                    return super.poll(remainTime, TimeUnit.MILLISECONDS);
                }
                return null;
            } finally {
                fairLock.unlock();
            }
        }
        return null;
    }
    
    @Override
    public RFuture<V> pollAsync(final long timeout, final TimeUnit unit) {
        final long startTime = System.currentTimeMillis();
        final RPromise<V> promise = newPromise();
        final long threadId = Thread.currentThread().getId();
        RFuture<Boolean> tryLockFuture = fairLock.tryLockAsync(timeout, unit);
        tryLockFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollAsync(remainTime, TimeUnit.MILLISECONDS);
                        pollFuture.addListener(new FutureListener<V>() {
                            @Override
                            public void operationComplete(Future<V> future) throws Exception {
                                RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
                                unlockFuture.addListener(new FutureListener<Void>() {
                                    @Override
                                    public void operationComplete(Future<Void> future) throws Exception {
                                        if (!future.isSuccess()) {
                                            promise.tryFailure(future.cause());
                                            return;
                                        }
                                        
                                        if (!pollFuture.isSuccess()) {
                                            promise.tryFailure(pollFuture.cause());
                                            return;
                                        }
                                        
                                        promise.trySuccess(pollFuture.getNow());
                                    }
                                });
                            }
                        });
                    } else {
                        RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
                        unlockFuture.addListener(new FutureListener<Void>() {
                            @Override
                            public void operationComplete(Future<Void> future) throws Exception {
                                if (!future.isSuccess()) {
                                    promise.tryFailure(future.cause());
                                    return;
                                }

                                promise.trySuccess(null);
                            }
                        });
                    }
                } else {
                    promise.trySuccess(null);
                }
            }
        });
        
        return promise;
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        if (fairLock.tryLock(timeout, unit)) {
            try {
                long spentTime = System.currentTimeMillis() - startTime;
                long remainTime = unit.toMillis(timeout) - spentTime;
                if (remainTime > 0) {
                    return super.pollLastAndOfferFirstTo(queueName, remainTime, TimeUnit.MILLISECONDS);
                }
                return null;
            } finally {
                fairLock.unlock();
            }
        }
        return null;
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(final String queueName, final long timeout, final TimeUnit unit) {
        final long startTime = System.currentTimeMillis();
        final RPromise<V> promise = newPromise();
        final long threadId = Thread.currentThread().getId();
        RFuture<Boolean> tryLockFuture = fairLock.tryLockAsync(timeout, unit);
        tryLockFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                if (future.getNow()) {
                    long spentTime = System.currentTimeMillis() - startTime;
                    long remainTime = unit.toMillis(timeout) - spentTime;
                    if (remainTime > 0) {
                        final RFuture<V> pollFuture = RedissonBlockingFairQueue.super.pollLastAndOfferFirstToAsync(queueName, remainTime, TimeUnit.MILLISECONDS);
                        pollFuture.addListener(new FutureListener<V>() {
                            @Override
                            public void operationComplete(Future<V> future) throws Exception {
                                RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
                                unlockFuture.addListener(new FutureListener<Void>() {
                                    @Override
                                    public void operationComplete(Future<Void> future) throws Exception {
                                        if (!future.isSuccess()) {
                                            promise.tryFailure(future.cause());
                                            return;
                                        }
                                        
                                        if (!pollFuture.isSuccess()) {
                                            promise.tryFailure(pollFuture.cause());
                                            return;
                                        }
                                        
                                        promise.trySuccess(pollFuture.getNow());
                                    }
                                });
                            }
                        });
                    } else {
                        RFuture<Void> unlockFuture = fairLock.unlockAsync(threadId);
                        unlockFuture.addListener(new FutureListener<Void>() {
                            @Override
                            public void operationComplete(Future<Void> future) throws Exception {
                                if (!future.isSuccess()) {
                                    promise.tryFailure(future.cause());
                                    return;
                                }

                                promise.trySuccess(null);
                            }
                        });
                    }
                } else {
                    promise.trySuccess(null);
                }
            }
        });
        
        return promise;
    }

}
