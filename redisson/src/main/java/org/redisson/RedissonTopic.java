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

import org.redisson.api.NameMapper;
import org.redisson.api.RFuture;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.StatusListener;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.redisson.pubsub.PublishSubscribeService;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTopic implements RTopic {

    final PublishSubscribeService subscribeService;
    final CommandAsyncExecutor commandExecutor;
    final String name;
    final ChannelName channelName;
    final Codec codec;

    public RedissonTopic(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    public static RedissonTopic createRaw(CommandAsyncExecutor commandExecutor, String name) {
        return new RedissonTopic(commandExecutor.getConnectionManager().getCodec(), commandExecutor, NameMapper.direct(), name);
    }

    public static RedissonTopic createRaw(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        return new RedissonTopic(codec, commandExecutor, NameMapper.direct(), name);
    }

    public RedissonTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this(codec, commandExecutor, commandExecutor.getConnectionManager().getConfig().getNameMapper(), name);
    }

    public RedissonTopic(Codec codec, CommandAsyncExecutor commandExecutor, NameMapper nameMapper, String name) {
        this.commandExecutor = commandExecutor;
        this.name = nameMapper.map(name);
        this.channelName = new ChannelName(this.name);
        this.codec = codec;
        this.subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
    }

    @Override
    public List<String> getChannelNames() {
        return Collections.singletonList(name);
    }

    @Override
    public long publish(Object message) {
        return commandExecutor.get(publishAsync(message));
    }

    protected String getName() {
        return name;
    }

    protected String getName(Object o) {
        return name;
    }

    @Override
    public RFuture<Long> publishAsync(Object message) {
        String name = getName(message);
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, RedisCommands.PUBLISH, name, commandExecutor.encode(codec, message));
    }

    @Override
    public int addListener(StatusListener listener) {
        RFuture<Integer> future = addListenerAsync(listener);
        return commandExecutor.get(future.toCompletableFuture());
    }

    @Override
    public <M> int addListener(Class<M> type, MessageListener<? extends M> listener) {
        RFuture<Integer> future = addListenerAsync(type, (MessageListener<M>) listener);
        return commandExecutor.get(future.toCompletableFuture());
    }

    @Override
    public RFuture<Integer> addListenerAsync(StatusListener listener) {
        PubSubStatusListener pubSubListener = new PubSubStatusListener(listener, name);
        return addListenerAsync(pubSubListener);
    }

    @Override
    public <M> RFuture<Integer> addListenerAsync(Class<M> type, MessageListener<M> listener) {
        PubSubMessageListener<M> pubSubListener = new PubSubMessageListener<>(type, listener, name);
        return addListenerAsync(pubSubListener);
    }

    protected RFuture<Integer> addListenerAsync(RedisPubSubListener<?> pubSubListener) {
        CompletableFuture<PubSubConnectionEntry> future = subscribeService.subscribe(codec, channelName, pubSubListener);
        CompletableFuture<Integer> f = future.thenApply(res -> {
            return System.identityHashCode(pubSubListener);
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void removeAllListeners() {
        commandExecutor.get(removeAllListenersAsync());
    }

    @Override
    public RFuture<Void> removeAllListenersAsync() {
        CompletableFuture<Void> f = subscribeService.removeAllListenersAsync(PubSubType.UNSUBSCRIBE, channelName);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void removeListener(MessageListener<?> listener) {
        RFuture<Void> future = removeListenerAsync(listener);
        commandExecutor.get(future.toCompletableFuture());
    }

    @Override
    public RFuture<Void> removeListenerAsync(MessageListener<?> listener) {
        CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.UNSUBSCRIBE, channelName, listener);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Void> removeListenerAsync(Integer... listenerIds) {
        CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.UNSUBSCRIBE, channelName, listenerIds);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void removeListener(Integer... listenerIds) {
        commandExecutor.get(removeListenerAsync(listenerIds).toCompletableFuture());
    }

    @Override
    public int countListeners() {
        PubSubConnectionEntry entry = subscribeService.getPubSubEntry(channelName);
        if (entry != null) {
            return entry.countListeners(channelName);
        }
        return 0;
    }

    @Override
    public RFuture<Long> countSubscribersAsync() {
        return commandExecutor.writeAsync(name, LongCodec.INSTANCE, RedisCommands.PUBSUB_NUMSUB, name);
    }

    @Override
    public long countSubscribers() {
        return commandExecutor.get(countSubscribersAsync());
    }

}
