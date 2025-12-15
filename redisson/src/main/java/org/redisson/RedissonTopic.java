/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.config.NameMapper;
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

import java.util.*;
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
    final List<String> names = new ArrayList<>();
    final List<ChannelName> channelNames = new ArrayList<>();
    final Codec codec;

    public RedissonTopic(CommandAsyncExecutor commandExecutor, String... names) {
        this(commandExecutor.getServiceManager().getCfg().getCodec(), commandExecutor, names);
    }

    public static RedissonTopic createRaw(CommandAsyncExecutor commandExecutor, String... names) {
        return new RedissonTopic(commandExecutor.getServiceManager().getCfg().getCodec(), commandExecutor, NameMapper.direct(), names);
    }

    public static RedissonTopic createRaw(Codec codec, CommandAsyncExecutor commandExecutor, String... names) {
        return new RedissonTopic(codec, commandExecutor, NameMapper.direct(), names);
    }

    public RedissonTopic(Codec codec, CommandAsyncExecutor commandExecutor, String... names) {
        this(codec, commandExecutor, commandExecutor.getServiceManager().getNameMapper(), names);
    }

    public RedissonTopic(Codec codec, CommandAsyncExecutor commandExecutor, NameMapper nameMapper, String... names) {
        this.commandExecutor = commandExecutor;
        for (String name : names) {
            name = nameMapper.map(name);
            this.names.add(name);
            ChannelName channelName = new ChannelName(name);
            this.channelNames.add(channelName);
        }
        this.codec = commandExecutor.getServiceManager().getCodec(codec);
        this.subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
    }

    @Override
    public List<String> getChannelNames() {
        return names;
    }

    @Override
    public long publish(Object message) {
        return commandExecutor.get(publishAsync(message));
    }

    protected String getName() {
        return names.get(0);
    }

    @Override
    public RFuture<Long> publishAsync(Object message) {
        String name = getName();
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, RedisCommands.PUBLISH, name, commandExecutor.encode(codec, message));
    }

    @Override
    public int addListener(StatusListener listener) {
        RFuture<Integer> future = addListenerAsync(listener);
        return commandExecutor.get(future.toCompletableFuture());
    }

    @Override
    public <M> int addListener(Class<M> type, MessageListener<? extends M> listener) {
        RFuture<Integer> future = addListenerAsync(type, listener);
        return commandExecutor.get(future.toCompletableFuture());
    }

    @Override
    public RFuture<Integer> addListenerAsync(StatusListener listener) {
        PubSubStatusListener pubSubListener = new PubSubStatusListener(listener, names.toArray(new String[0]));
        return addListenerAsync(pubSubListener);
    }

    @Override
    public <M> RFuture<Integer> addListenerAsync(Class<M> type, MessageListener<? extends M> listener) {
        PubSubMessageListener<M> pubSubListener = new PubSubMessageListener<>(type, (MessageListener<M>) listener, new HashSet<>(names));
        return addListenerAsync(pubSubListener);
    }

    protected RFuture<Integer> addListenerAsync(RedisPubSubListener<?> pubSubListener) {
        CompletableFuture<List<PubSubConnectionEntry>> future = subscribeService.subscribe(codec, channelNames, pubSubListener);
        CompletableFuture<Integer> f = future.thenApply(res -> {
            if (pubSubListener instanceof PubSubStatusListener
                    && subscribeService.isMultiEntity(channelNames.get(0))) {
                // replaced in subscribe() method
                Optional<RedisPubSubListener<?>> l = res.stream()
                        .flatMap(r -> r.getListeners(channelNames.get(0)).stream())
                        .filter(r -> r instanceof PubSubStatusListener
                                && ((PubSubStatusListener) pubSubListener).getListener() == ((PubSubStatusListener) r).getListener())
                        .findAny();
                if (l.isPresent()) {
                    return System.identityHashCode(l.get());
                }
            }
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
        CompletableFuture<Void> f = subscribeService.removeAllListenersAsync(PubSubType.UNSUBSCRIBE, channelNames.toArray(new ChannelName[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void removeListener(MessageListener<?> listener) {
        RFuture<Void> future = removeListenerAsync(listener);
        commandExecutor.get(future.toCompletableFuture());
    }

    @Override
    public RFuture<Void> removeListenerAsync(MessageListener<?> listener) {
        CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.UNSUBSCRIBE, channelNames, listener);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Void> removeListenerAsync(Integer... listenerIds) {
        CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.UNSUBSCRIBE, channelNames, listenerIds);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void removeListener(Integer... listenerIds) {
        commandExecutor.get(removeListenerAsync(listenerIds).toCompletableFuture());
    }

    @Override
    public int countListeners() {
        return subscribeService.countListeners(channelNames);
    }

    @Override
    public RFuture<Long> countSubscribersAsync() {
        return commandExecutor.writeAsync(names.get(0), LongCodec.INSTANCE, RedisCommands.PUBSUB_NUMSUB, names.toArray());
    }

    @Override
    public long countSubscribers() {
        return commandExecutor.get(countSubscribersAsync());
    }

}
