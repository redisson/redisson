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

import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisPubSubConnection extends RedisConnection {

    final Queue<RedisPubSubListener<Object>> listeners = new ConcurrentLinkedQueue<>();
    final Map<ChannelName, Codec> channels = new ConcurrentHashMap<>();
    final Map<ChannelName, Codec> shardedChannels = new ConcurrentHashMap<>();
    final Map<ChannelName, Codec> patternChannels = new ConcurrentHashMap<>();
    final Map<ChannelName, PubSubType> unsubscribedChannels = new ConcurrentHashMap<>();

    public RedisPubSubConnection(RedisClient redisClient, Channel channel, CompletableFuture<RedisPubSubConnection> connectionPromise) {
        super(redisClient, channel, connectionPromise);
    }

    public void addListener(RedisPubSubListener<?> listener) {
        listeners.add((RedisPubSubListener<Object>) listener);
    }

    public void removeListener(RedisPubSubListener<?> listener) {
        listeners.remove(listener);
    }

    public void onMessage(PubSubStatusMessage message) {
        for (RedisPubSubListener<Object> redisPubSubListener : listeners) {
            redisPubSubListener.onStatus(message.getType(), message.getChannel());
        }
    }

    public void onMessage(PubSubMessage message) {
        for (RedisPubSubListener<Object> redisPubSubListener : listeners) {
            redisPubSubListener.onMessage(message.getChannel(), message.getValue());
        }
    }

    public void onMessage(PubSubPatternMessage message) {
        for (RedisPubSubListener<Object> redisPubSubListener : listeners) {
            redisPubSubListener.onPatternMessage(message.getPattern(), message.getChannel(), message.getValue());
        }
    }

    public ChannelFuture subscribe(CompletableFuture<Void> promise, Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            this.channels.put(ch, codec);
        }
        return async(promise, new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SUBSCRIBE, channels);
    }

    public ChannelFuture ssubscribe(CompletableFuture<Void> promise, Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            this.shardedChannels.put(ch, codec);
        }
        return async(promise, new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SSUBSCRIBE, channels);
    }

    public ChannelFuture psubscribe(CompletableFuture<Void> promise, Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            patternChannels.put(ch, codec);
        }
        return async(promise, new PubSubPatternMessageDecoder(codec.getValueDecoder()), RedisCommands.PSUBSCRIBE, channels);
    }

    public ChannelFuture subscribe(Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            this.channels.put(ch, codec);
        }
        return async(new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SUBSCRIBE, channels);
    }

    public ChannelFuture ssubscribe(Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            this.shardedChannels.put(ch, codec);
        }
        return async(new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SSUBSCRIBE, channels);
    }

    public ChannelFuture psubscribe(Codec codec, ChannelName... channels) {
        for (ChannelName ch : channels) {
            patternChannels.put(ch, codec);
        }
        return async(new PubSubPatternMessageDecoder(codec.getValueDecoder()), RedisCommands.PSUBSCRIBE, channels);
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
