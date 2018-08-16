/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.reactive;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.PubSubPatternMessageListener;
import org.redisson.PubSubPatternStatusListener;
import org.redisson.api.RFuture;
import org.redisson.api.RPatternTopicReactive;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.api.listener.PatternStatusListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.AsyncSemaphore;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.redisson.pubsub.PublishSubscribeService;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> message
 */
public class RedissonPatternTopicReactive<M> implements RPatternTopicReactive<M> {

    final PublishSubscribeService subscribeService;
    final CommandReactiveExecutor commandExecutor;
    private final String name;
    private final ChannelName channelName;
    private final Codec codec;

    public RedissonPatternTopicReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    public RedissonPatternTopicReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.channelName = new ChannelName(name);
        this.codec = codec;
        this.subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
    }

    @Override
    public Publisher<Integer> addListener(final PatternStatusListener listener) {
        return commandExecutor.reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                RPromise<Integer> promise = new RedissonPromise<Integer>();
                addListener(new PubSubPatternStatusListener(listener, name), promise);
                return promise;
            }
        });
    };

    @Override
    public Publisher<Integer> addListener(final PatternMessageListener<M> listener) {
        return commandExecutor.reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                RPromise<Integer> promise = new RedissonPromise<Integer>();
                PubSubPatternMessageListener<M> pubSubListener = new PubSubPatternMessageListener<M>(listener, name);
                addListener(pubSubListener, promise);
                return promise;
            }
        });
    }

    private void addListener(final RedisPubSubListener<M> pubSubListener, final RPromise<Integer> promise) {
        RFuture<PubSubConnectionEntry> future = subscribeService.psubscribe(channelName, codec, pubSubListener);
        future.addListener(new FutureListener<PubSubConnectionEntry>() {
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }

                promise.trySuccess(pubSubListener.hashCode());
            }
        });
    }
    
    protected void acquire(AsyncSemaphore semaphore) {
        MasterSlaveServersConfig config = commandExecutor.getConnectionManager().getConfig();
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        if (!semaphore.tryAcquire(timeout)) {
            throw new RedisTimeoutException("Remove listeners operation timeout: (" + timeout + "ms) for " + name + " topic");
        }
    }

    @Override
    public void removeListener(int listenerId) {
        AsyncSemaphore semaphore = subscribeService.getSemaphore(channelName);
        acquire(semaphore);

        PubSubConnectionEntry entry = subscribeService.getPubSubEntry(channelName);
        if (entry == null) {
            semaphore.release();
            return;
        }
        
        entry.removeListener(channelName, listenerId);
        if (!entry.hasListeners(channelName)) {
            subscribeService.punsubscribe(channelName, semaphore);
        } else {
            semaphore.release();
        }
    }

    @Override
    public List<String> getPatternNames() {
        return Collections.singletonList(name);
    }

}
