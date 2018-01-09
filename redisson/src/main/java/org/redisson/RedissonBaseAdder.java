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

import org.redisson.api.RFuture;
import org.redisson.api.RSemaphore;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.LongAdder;
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

    private static final Logger log = LoggerFactory.getLogger(RedissonBaseAdder.class);
    
    private static final long CLEAR_MSG = 0;
    private static final long SUM_MSG = 1;

    private final RSemaphore semaphore;
    private final RTopic<Long> topic;
    private final int listenerId;
    
    public RedissonBaseAdder(CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(connectionManager, name);
        
        topic = redisson.getTopic(suffixName(getName(), "topic"), LongCodec.INSTANCE);
        semaphore = redisson.getSemaphore(suffixName(getName(), "semaphore"));
        listenerId = topic.addListener(new MessageListener<Long>() {
            
            @Override
            public void onMessage(String channel, Long msg) {
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

    public T sum() {
        return get(sumAsync());
    }
    
    public void reset() {
        get(resetAsync());
    }
    
    public RFuture<T> sumAsync() {
        final RPromise<T> result = new RedissonPromise<T>();
        
        RFuture<Integer> future = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                "redis.call('del', KEYS[1]); "
              + "return redis.call('publish', KEYS[2], ARGV[1]); ",
              Arrays.<Object>asList(getName(), topic.getChannelNames().get(0)), SUM_MSG);
        future.addListener(new FutureListener<Integer>() {

            @Override
            public void operationComplete(Future<Integer> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                semaphore.acquireAsync(future.getNow()).addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        RFuture<T> valueFuture = getAsync();
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
        });
        
        return result;
    }

    public RFuture<Void> resetAsync() {
        final RPromise<Void> result = new RedissonPromise<Void>();
        
        RFuture<Integer> future = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                  "redis.call('del', KEYS[1]); "
                + "return redis.call('publish', KEYS[2], ARGV[1]); ",
                Arrays.<Object>asList(getName(), topic.getChannelNames().get(0)), CLEAR_MSG);
        
        future.addListener(new FutureListener<Integer>() {

            @Override
            public void operationComplete(Future<Integer> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                semaphore.acquireAsync(future.getNow()).addListener(new FutureListener<Void>() {
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
        });
        
        return result;
    }

    public void destroy() {
        topic.removeListener(listenerId);
    }

    protected abstract RFuture<T> addAndGetAsync();

    protected abstract RFuture<T> getAsync();

}
