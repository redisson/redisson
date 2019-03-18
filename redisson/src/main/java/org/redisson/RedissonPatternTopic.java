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

import java.util.Collections;
import java.util.List;

import org.redisson.api.RFuture;
import org.redisson.api.RPatternTopic;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.api.listener.PatternStatusListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.AsyncSemaphore;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.redisson.pubsub.PublishSubscribeService;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonPatternTopic implements RPatternTopic {

    final PublishSubscribeService subscribeService;
    final CommandAsyncExecutor commandExecutor;
    private final String name;
    private final ChannelName channelName;
    private final Codec codec;

    protected RedissonPatternTopic(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    protected RedissonPatternTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.channelName = new ChannelName(name);
        this.codec = codec;
        this.subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
    }

    @Override
    public int addListener(PatternStatusListener listener) {
        return addListener(new PubSubPatternStatusListener(listener, name));
    };

    @Override
    public <T> int addListener(Class<T> type, PatternMessageListener<T> listener) {
        PubSubPatternMessageListener<T> pubSubListener = new PubSubPatternMessageListener<T>(type, listener, name);
        return addListener(pubSubListener);
    }

    private int addListener(RedisPubSubListener<?> pubSubListener) {
        RFuture<PubSubConnectionEntry> future = subscribeService.psubscribe(channelName, codec, pubSubListener);
        commandExecutor.syncSubscription(future);
        return System.identityHashCode(pubSubListener);
    }
    
    @Override
    public RFuture<Integer> addListenerAsync(PatternStatusListener listener) {
        PubSubPatternStatusListener pubSubListener = new PubSubPatternStatusListener(listener, name);
        return addListenerAsync(pubSubListener);
    }
    
    @Override
    public <T> RFuture<Integer> addListenerAsync(Class<T> type, PatternMessageListener<T> listener) {
        PubSubPatternMessageListener<T> pubSubListener = new PubSubPatternMessageListener<T>(type, listener, name);
        return addListenerAsync(pubSubListener);
    }
    
    private RFuture<Integer> addListenerAsync(RedisPubSubListener<?> pubSubListener) {
        RFuture<PubSubConnectionEntry> future = subscribeService.subscribe(codec, channelName, pubSubListener);
        RPromise<Integer> result = new RedissonPromise<Integer>();
        future.onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }
            
            result.trySuccess(System.identityHashCode(pubSubListener));
        });
        return result;
    }
    
    protected void acquire(AsyncSemaphore semaphore) {
        MasterSlaveServersConfig config = commandExecutor.getConnectionManager().getConfig();
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        if (!semaphore.tryAcquire(timeout)) {
            throw new RedisTimeoutException("Remove listeners operation timeout: (" + timeout + "ms) for " + name + " topic");
        }
    }
    
    
    public RFuture<Void> removeListenerAsync(int listenerId) {
        RPromise<Void> result = new RedissonPromise<>();
        AsyncSemaphore semaphore = subscribeService.getSemaphore(channelName);
        semaphore.acquire(() -> {
            PubSubConnectionEntry entry = subscribeService.getPubSubEntry(channelName);
            if (entry == null) {
                semaphore.release();
                result.trySuccess(null);
                return;
            }
            
            entry.removeListener(channelName, listenerId);
            if (!entry.hasListeners(channelName)) {
                subscribeService.punsubscribe(channelName, semaphore);
            } else {
                semaphore.release();
                result.trySuccess(null);
            }
        });
        return result;
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
    public void removeAllListeners() {
        AsyncSemaphore semaphore = subscribeService.getSemaphore(channelName);
        acquire(semaphore);
        
        PubSubConnectionEntry entry = subscribeService.getPubSubEntry(channelName);
        if (entry == null) {
            semaphore.release();
            return;
        }

        if (entry.removeAllListeners(channelName)) {
            subscribeService.punsubscribe(channelName, semaphore);
        } else {
            semaphore.release();
        }
    }

    @Override
    public void removeListener(PatternMessageListener<?> listener) {
        AsyncSemaphore semaphore = subscribeService.getSemaphore(channelName);
        acquire(semaphore);
        
        PubSubConnectionEntry entry = subscribeService.getPubSubEntry(channelName);
        if (entry == null) {
            semaphore.release();
            return;
        }

        entry.removeListener(channelName, listener);
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
