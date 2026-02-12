/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.FutureListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.pubsub.*;
import org.redisson.misc.FastRemovalQueue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedisPubSubConnection extends RedisConnection {

    private static final FastRemovalQueue<RedisPubSubListener<Object>> EMPTY_QUEUE = new FastRemovalQueue<>();

    final Map<ChannelName, FastRemovalQueue<RedisPubSubListener<Object>>> listeners = new ConcurrentHashMap<>();
    final Map<ChannelName, Codec> channels = new ConcurrentHashMap<>();
    final Map<ChannelName, Codec> shardedChannels = new ConcurrentHashMap<>();
    final Map<ChannelName, Codec> patternChannels = new ConcurrentHashMap<>();
    final Map<ChannelName, PubSubType> unsubscribedChannels = new ConcurrentHashMap<>();

    public RedisPubSubConnection(RedisClient redisClient, Channel channel, CompletableFuture<RedisPubSubConnection> connectionPromise) {
        super(redisClient, channel, connectionPromise);
    }

    public void addListener(ChannelName channelName, RedisPubSubListener<?> listener) {
        FastRemovalQueue<RedisPubSubListener<Object>> queue = listeners.computeIfAbsent(channelName, c -> new FastRemovalQueue<>());
        queue.add((RedisPubSubListener<Object>) listener);
    }

    public void removeListener(ChannelName channelName, RedisPubSubListener<?> listener) {
        listeners.compute(channelName, (k, queue) -> {
            if (queue == null) {
                return null;
            }

            queue.remove((RedisPubSubListener<Object>) listener);
            if (queue.isEmpty()) {
                return null;
            }
            return queue;
        });
    }

    public void onMessage(PubSubStatusMessage message) {
        FastRemovalQueue<RedisPubSubListener<Object>> queue = listeners.getOrDefault(message.getChannel(), EMPTY_QUEUE);
        for (RedisPubSubListener<Object> redisPubSubListener : queue) {
            redisPubSubListener.onStatus(message.getType(), message.getChannel());
        }
    }

    public void onMessage(PubSubMessage message) {
        FastRemovalQueue<RedisPubSubListener<Object>> queue = listeners.getOrDefault(message.getChannel(), EMPTY_QUEUE);
        for (RedisPubSubListener<Object> redisPubSubListener : queue) {
            redisPubSubListener.onMessage(message.getChannel(), message.getValue());
        }
    }

    public void onMessage(PubSubPatternMessage message) {
        FastRemovalQueue<RedisPubSubListener<Object>> queue = listeners.getOrDefault(message.getPattern(), EMPTY_QUEUE);
        for (RedisPubSubListener<Object> redisPubSubListener : queue) {
            redisPubSubListener.onPatternMessage(message.getPattern(), message.getChannel(), message.getValue());
        }
    }

    public ChannelFuture subscribe(CompletableFuture<Void> promise, Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            this.channels.put(ch, codec);
        }
        return async(promise, new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SUBSCRIBE, (Object[]) channels);
    }

    public ChannelFuture ssubscribe(CompletableFuture<Void> promise, Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            this.shardedChannels.put(ch, codec);
        }
        return async(promise, new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SSUBSCRIBE, (Object[]) channels);
    }

    public ChannelFuture psubscribe(CompletableFuture<Void> promise, Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            patternChannels.put(ch, codec);
        }
        return async(promise, new PubSubPatternMessageDecoder(codec.getValueDecoder()), RedisCommands.PSUBSCRIBE, (Object[]) channels);
    }

    public ChannelFuture subscribe(Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            this.channels.put(ch, codec);
        }
        return async(new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SUBSCRIBE, (Object[]) channels);
    }

    public ChannelFuture ssubscribe(Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            this.shardedChannels.put(ch, codec);
        }
        return async(new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SSUBSCRIBE, (Object[]) channels);
    }

    public ChannelFuture psubscribe(Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            patternChannels.put(ch, codec);
        }
        return async(new PubSubPatternMessageDecoder(codec.getValueDecoder()), RedisCommands.PSUBSCRIBE, (Object[]) channels);
    }

    public ChannelFuture unsubscribe(PubSubType type, ChannelName... channels) {
        RedisCommand<Object> command;
        if (type == PubSubType.UNSUBSCRIBE) {
            command = RedisCommands.UNSUBSCRIBE;
            for (ChannelName ch : channels) {
                this.channels.remove(ch);
                unsubscribedChannels.put(ch, type);
            }
        } else if (type == PubSubType.SUNSUBSCRIBE) {
            command = RedisCommands.SUNSUBSCRIBE;
            for (ChannelName ch : channels) {
                this.shardedChannels.remove(ch);
                unsubscribedChannels.put(ch, type);
            }
        } else {
            command = RedisCommands.PUNSUBSCRIBE;
            for (ChannelName ch : channels) {
                patternChannels.remove(ch);
                unsubscribedChannels.put(ch, type);
            }
        }

        ChannelFuture future = async((MultiDecoder) null, command, channels);
        future.addListener((FutureListener<Void>) f -> {
            if (!f.isSuccess()) {
                for (ChannelName channel : channels) {
                    removeDisconnectListener(channel);
                    onMessage(new PubSubStatusMessage(type, channel));
                }
            }
        });
        return future;
    }

    public void removeDisconnectListener(ChannelName channel) {
        unsubscribedChannels.remove(channel);
    }

    @Override
    public void fireDisconnected() {
        super.fireDisconnected();

        unsubscribedChannels.forEach((key, value) -> onMessage(new PubSubStatusMessage(value, key)));
    }

    private <T, R> ChannelFuture async(MultiDecoder<Object> messageDecoder, RedisCommand<T> command, Object... params) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        return channel.writeAndFlush(new CommandData<>(promise, messageDecoder, null, command, params));
    }

    private <T, R> ChannelFuture async(CompletableFuture<Void> promise, MultiDecoder<Object> messageDecoder, RedisCommand<T> command, Object... params) {
        return channel.writeAndFlush(new CommandData<>(promise, messageDecoder, null, command, params));
    }

    public Map<ChannelName, Codec> getShardedChannels() {
        return Collections.unmodifiableMap(shardedChannels);
    }

    public Map<ChannelName, Codec> getChannels() {
        return Collections.unmodifiableMap(channels);
    }

    public Map<ChannelName, Codec> getPatternChannels() {
        return Collections.unmodifiableMap(patternChannels);
    }

}
