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

import org.redisson.api.NameMapper;
import org.redisson.api.RFuture;
import org.redisson.api.RShardedTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.pubsub.PubSubConnectionEntry;

import java.util.concurrent.CompletableFuture;

/**
 * Sharded Topic for Redis Cluster. Messages are delivered to message listeners connected to the same Topic.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonShardedTopic extends RedissonTopic implements RShardedTopic {

    public RedissonShardedTopic(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonShardedTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    public RedissonShardedTopic(Codec codec, CommandAsyncExecutor commandExecutor, NameMapper nameMapper, String name) {
        super(codec, commandExecutor, nameMapper, name);
    }

    public static RedissonTopic createRaw(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        return new RedissonShardedTopic(codec, commandExecutor, NameMapper.direct(), name);
    }

    @Override
    protected RFuture<Integer> addListenerAsync(RedisPubSubListener<?> pubSubListener) {
        CompletableFuture<PubSubConnectionEntry> future = subscribeService.ssubscribe(codec, channelName, pubSubListener);
        CompletableFuture<Integer> f = future.thenApply(res -> {
            return System.identityHashCode(pubSubListener);
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Long> publishAsync(Object message) {
        String name = getName(message);
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, RedisCommands.SPUBLISH, name, commandExecutor.encode(codec, message));
    }

    @Override
    public RFuture<Void> removeListenerAsync(MessageListener<?> listener) {
        CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.SUNSUBSCRIBE, channelName, listener);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Void> removeListenerAsync(Integer... listenerIds) {
        CompletableFuture<Void> f = subscribeService.removeListenerAsync(PubSubType.SUNSUBSCRIBE, channelName, listenerIds);
        return new CompletableFutureWrapper<>(f);
    }

    public RFuture<Void> removeAllListenersAsync() {
        CompletableFuture<Void> f = subscribeService.removeAllListenersAsync(PubSubType.SUNSUBSCRIBE, channelName);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Long> countSubscribersAsync() {
        return commandExecutor.writeAsync(name, LongCodec.INSTANCE, RedisCommands.PUBSUB_SHARDNUMSUB, channelName);
    }
}
