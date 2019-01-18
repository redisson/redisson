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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.redisson.api.RFuture;
import org.redisson.api.RSemaphore;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.ChannelName;
import org.redisson.client.codec.LongCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class RedissonBaseAdder<T extends Number> extends RedissonExpirable {

    private class ResetListener implements FutureListener<Long> {
        
        private final RPromise<Void> result;

        private ResetListener(RPromise<Void> result) {
            this.result = result;
        }

        @Override
        public void operationComplete(Future<Long> future) throws Exception {
            if (!future.isSuccess()) {
                result.tryFailure(future.cause());
                return;
            }
            
            acquireAsync(future.getNow().intValue()).addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (!future.isSuccess()) {
                        result.tryFailure(future.cause());
                        return;
                    }

                    result.trySuccess(null);
                }
            });
        }

        protected RFuture<Void> acquireAsync(int value) {
            return semaphore.acquireAsync(value);
        }
    }

    private class SumListener implements FutureListener<Long> {
        
        private final RPromise<T> result;

        private SumListener(RPromise<T> result) {
            this.result = result;
        }

        @Override
        public void operationComplete(Future<Long> future) throws Exception {
            if (!future.isSuccess()) {
                result.tryFailure(future.cause());
                return;
            }

            acquireAsync(future.getNow().intValue()).addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (!future.isSuccess()) {
                        result.tryFailure(future.cause());
                        return;
                    }

                    RFuture<T> valueFuture = getAndDeleteAsync();
                    valueFuture.addListener(new FutureListener<T>() {
                        @Override
                        public void operationComplete(Future<T> future) throws Exception {
                            if (!future.isSuccess()) {
                                result.tryFailure(future.cause());
                                return;
                            }
                            
                            result.trySuccess(future.getNow());
                        }
                    });
                }
            });
        }

        protected RFuture<Void> acquireAsync(int value) {
            return semaphore.acquireAsync(value);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RedissonBaseAdder.class);
    
    private static final long CLEAR_MSG = 0;
    private static final long SUM_MSG = 1;

    private final RSemaphore semaphore;
    private final RTopic topic;
    private final int listenerId;
    
    public RedissonBaseAdder(CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(connectionManager, name);
        
        topic = redisson.getTopic(suffixName(getName(), "topic"), LongCodec.INSTANCE);
        semaphore = redisson.getSemaphore(suffixName(getName(), "semaphore"));
        listenerId = topic.addListener(Long.class, new MessageListener<Long>() {
            
            @Override
            public void onMessage(CharSequence channel, Long msg) {
                if (msg == SUM_MSG) {
                    RFuture<T> addAndGetFuture = addAndGetAsync();
                    addAndGetFuture.addListener(new FutureListener<T>() {
                        @Override
                        public void operationComplete(Future<T> future) throws Exception {
                            if (!future.isSuccess()) {
                                log.error("Can't increase sum", future.cause());
                                return;
                            }
                            
                            semaphore.releaseAsync().addListener(new FutureListener<Void>() {
                                @Override
                                public void operationComplete(Future<Void> future) throws Exception {
                                    if (!future.isSuccess()) {
                                        log.error("Can't release semaphore", future.cause());
                                        return;
                                    }
                                }
                            });
                        }
                    });
                }
                
                if (msg == CLEAR_MSG) {
                    doReset();
                    semaphore.releaseAsync().addListener(new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            if (!future.isSuccess()) {
                                log.error("Can't release semaphore", future.cause());
                                return;
                            }
                        }
                    });
                }
            }

        });
        
    }

    protected abstract void doReset();

    public void reset() {
        get(resetAsync());
    }
    
    public void reset(long timeout, TimeUnit timeUnit) {
        get(resetAsync(timeout, timeUnit));
    }
    
    public RFuture<T> sumAsync() {
        final RPromise<T> result = new RedissonPromise<T>();
        
        RFuture<Long> future = topic.publishAsync(SUM_MSG);
        future.addListener(new SumListener(result));
        
        return result;
    }
    
    public RFuture<T> sumAsync(final long timeout, final TimeUnit timeUnit) {
        RPromise<T> result = new RedissonPromise<T>();
        
        RFuture<Long> future = topic.publishAsync(SUM_MSG);
        future.addListener(new SumListener(result) {
            @Override
            protected RFuture<Void> acquireAsync(int value) {
                return tryAcquire(timeout, timeUnit, value);
            }

        });
        
        return result;
    }

    protected RFuture<Void> tryAcquire(long timeout, TimeUnit timeUnit, int value) {
        final RPromise<Void> acquireResult = new RedissonPromise<Void>();
        semaphore.tryAcquireAsync(value, timeout, timeUnit).addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    acquireResult.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow()) {
                    acquireResult.trySuccess(null);
                } else {
                    acquireResult.tryFailure(new TimeoutException());
                }
            }
        });
        return acquireResult;
    }

    public RFuture<Void> resetAsync() {
        final RPromise<Void> result = new RedissonPromise<Void>();
        
        RFuture<Long> future = topic.publishAsync(CLEAR_MSG);
        future.addListener(new ResetListener(result));
        
        return result;
    }
    
    public RFuture<Void> resetAsync(final long timeout, final TimeUnit timeUnit) {
        final RPromise<Void> result = new RedissonPromise<Void>();
        
        RFuture<Long> future = topic.publishAsync(CLEAR_MSG);
        future.addListener(new ResetListener(result) {
            @Override
            protected RFuture<Void> acquireAsync(int value) {
                return tryAcquire(timeout, timeUnit, value);
            }
        });
        
        return result;
    }

    public void destroy() {
        topic.removeListener(listenerId);
    }

    protected abstract RFuture<T> addAndGetAsync();

    protected abstract RFuture<T> getAndDeleteAsync();

}
