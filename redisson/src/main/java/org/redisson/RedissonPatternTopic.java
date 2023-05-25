/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.api.RFuture;
import org.redisson.api.RPatternTopic;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.api.listener.PatternStatusListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.AsyncSemaphore;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.redisson.pubsub.PublishSubscribeService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
        this(commandExecutor.getServiceManager().getCfg().getCodec(), commandExecutor, name);
    }

    public RedissonPatternTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.channelName = new ChannelName(name);
        this.codec = codec;
        this.subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
    }

    @Override
    public int addListener(PatternStatusListener listener) {
        return addListener(new PubSubPatternStatusListener(listener, name));
    }

    @Override
    public <T> int addListener(Class<T> type, PatternMessageListener<T> listener) {
        PubSubPatternMessageListener<T> pubSubListener = new PubSubPatternMessageListener<T>(type, listener, name);
        return addListener(pubSubListener);
    }

    private int addListener(RedisPubSubListener<?> pubSubListener) {
        CompletableFuture<Collection<PubSubConnectionEntry>> future = subscribeService.psubscribe(channelName, codec, pubSubListener);
        commandExecutor.get(future);
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
        CompletableFuture<Collection<PubSubConnectionEntry>> future = subscribeService.psubscribe(channelName, codec, pubSubListener);
        CompletableFuture<Integer> f = future.thenApply(res -> {
            return System.identityHashCode(pubSubListener);
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    protected void acquire(AsyncSemaphore semaphore) {
        MasterSlaveServersConfig config = commandExecutor.getServiceManager().getConfig();
        int timeout = config.getTimeout() + config.getRetryInterval() * config.getRetryAttempts();
        if (!semaphore.tryAcquire(timeout)) {
            throw new RedisTimeoutException("Remove listeners operation timeout: (" + timeout + "ms) for " + name + " topic");
        }
    }
    
    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.PUNSUBSCRIBE, channelName, listenerId);
        return new CompletableFutureWrapper<>(f);
    }
    
    @Override
    public void removeListener(int listenerId) {
        commandExecutor.get(removeListenerAsync(listenerId).toCompletableFuture());
    }
    
    @Override
    public void removeAllListeners() {
        commandExecutor.get(removeAllListenersAsync());
    }

    @Override
    public RFuture<Void> removeAllListenersAsync() {
        CompletableFuture<Void> f = subscribeService.removeAllListenersAsync(PubSubType.PUNSUBSCRIBE, channelName);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void removeListener(PatternMessageListener<?> listener) {
        CompletableFuture<Void> future = subscribeService.removeListenerAsync(PubSubType.PUNSUBSCRIBE, channelName, listener);
        commandExecutor.get(future);
    }
    
    @Override
    public List<String> getPatternNames() {
        return Collections.singletonList(name);
    }

}
