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
import java.util.function.BiConsumer;

import org.redisson.api.RFuture;
import org.redisson.api.RSemaphore;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class RedissonBaseAdder<T extends Number> extends RedissonExpirable {

    private class ResetListener implements BiConsumer<Long, Throwable> {
        
        private final RPromise<Void> result;

        ResetListener(RPromise<Void> result) {
            this.result = result;
        }

        @Override
        public void accept(Long t, Throwable u) {
            if (u != null) {
                result.tryFailure(u);
                return;
            }
            
            acquireAsync(t.intValue()).onComplete((res, e) -> {
                if (e != null) {
                    result.tryFailure(e);
                    return;
                }

                result.trySuccess(null);
            });
        }

        protected RFuture<Void> acquireAsync(int value) {
            return semaphore.acquireAsync(value);
        }
    }

    private class SumListener implements BiConsumer<Long, Throwable> {
        
        private final RPromise<T> result;

        SumListener(RPromise<T> result) {
            this.result = result;
        }

        @Override
        public void accept(Long t, Throwable u) {
            if (u != null) {
                result.tryFailure(u);
                return;
            }

            acquireAsync(t.intValue()).onComplete((r, e) -> {
                if (e != null) {
                    result.tryFailure(e);
                    return;
                }

                RFuture<T> valueFuture = getAndDeleteAsync();
                valueFuture.onComplete((res, ex) -> {
                    if (ex != null) {
                        result.tryFailure(ex);
                        return;
                    }
                    
                    result.trySuccess(res);
                });
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
                    addAndGetFuture.onComplete((res, e) -> {
                        if (e != null) {
                            log.error("Can't increase sum", e);
                            return;
                        }
                        
                        semaphore.releaseAsync().onComplete((r, ex) -> {
                            if (ex != null) {
                                log.error("Can't release semaphore", ex);
                                return;
                            }
                        });
                    });
                }
                
                if (msg == CLEAR_MSG) {
                    doReset();
                    semaphore.releaseAsync().onComplete((res, e) -> {
                        if (e != null) {
                            log.error("Can't release semaphore", e);
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
        RPromise<T> result = new RedissonPromise<T>();
        
        RFuture<Long> future = topic.publishAsync(SUM_MSG);
        future.onComplete(new SumListener(result));
        
        return result;
    }
    
    public RFuture<T> sumAsync(long timeout, TimeUnit timeUnit) {
        RPromise<T> result = new RedissonPromise<T>();
        
        RFuture<Long> future = topic.publishAsync(SUM_MSG);
        future.onComplete(new SumListener(result) {
            @Override
            protected RFuture<Void> acquireAsync(int value) {
                return tryAcquire(timeout, timeUnit, value);
            }

        });
        
        return result;
    }

    protected RFuture<Void> tryAcquire(long timeout, TimeUnit timeUnit, int value) {
        RPromise<Void> acquireResult = new RedissonPromise<Void>();
        semaphore.tryAcquireAsync(value, timeout, timeUnit).onComplete((res, e) -> {
            if (e != null) {
                acquireResult.tryFailure(e);
                return;
            }
            
            if (res) {
                acquireResult.trySuccess(null);
            } else {
                acquireResult.tryFailure(new TimeoutException());
            }
        });
        return acquireResult;
    }

    public RFuture<Void> resetAsync() {
        RPromise<Void> result = new RedissonPromise<Void>();
        
        RFuture<Long> future = topic.publishAsync(CLEAR_MSG);
        future.onComplete(new ResetListener(result));
        
        return result;
    }
    
    public RFuture<Void> resetAsync(long timeout, TimeUnit timeUnit) {
        RPromise<Void> result = new RedissonPromise<Void>();
        
        RFuture<Long> future = topic.publishAsync(CLEAR_MSG);
        future.onComplete(new ResetListener(result) {
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
