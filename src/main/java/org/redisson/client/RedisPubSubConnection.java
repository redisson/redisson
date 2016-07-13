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
package org.redisson.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.pubsub.PubSubMessage;
import org.redisson.client.protocol.pubsub.PubSubMessageDecoder;
import org.redisson.client.protocol.pubsub.PubSubPatternMessage;
import org.redisson.client.protocol.pubsub.PubSubPatternMessageDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.redisson.client.protocol.pubsub.PubSubType;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

public class RedisPubSubConnection extends RedisConnection {

    final Queue<RedisPubSubListener<Object>> listeners = new ConcurrentLinkedQueue<RedisPubSubListener<Object>>();
    final Map<String, Codec> channels = PlatformDependent.newConcurrentHashMap();
    final Map<String, Codec> patternChannels = PlatformDependent.newConcurrentHashMap();
    final Set<String> unsubscibedChannels = new HashSet<String>();
    final Set<String> punsubscibedChannels = new HashSet<String>();

    public RedisPubSubConnection(RedisClient redisClient, Channel channel) {
        super(redisClient, channel);
    }

    public void addListener(RedisPubSubListener listener) {
        listeners.add(listener);
    }

    public void addOneShotListener(RedisPubSubListener listener) {
        listeners.add(new OneShotPubSubListener<Object>(this, listener));
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

    public void subscribe(Codec codec, String ... channel) {
        async(new PubSubMessageDecoder(codec.getValueDecoder()), RedisCommands.SUBSCRIBE, channel);
        for (String ch : channel) {
            channels.put(ch, codec);
        }
    }

    public void psubscribe(Codec codec, String ... channel) {
        async(new PubSubPatternMessageDecoder(codec.getValueDecoder()), RedisCommands.PSUBSCRIBE, channel);
        for (String ch : channel) {
            patternChannels.put(ch, codec);
        }
    }

    public void unsubscribe(final String ... channels) {
        synchronized (this) {
            for (String ch : channels) {
                this.channels.remove(ch);
                unsubscibedChannels.add(ch);
            }
        }
        ChannelFuture future = async((MultiDecoder)null, RedisCommands.UNSUBSCRIBE, channels);
        future.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    for (String channel : channels) {
                        removeDisconnectListener(channel);
                        onMessage(new PubSubStatusMessage(PubSubType.UNSUBSCRIBE, channel));
                    }
                }
            }
        });
    }
    
    public void removeDisconnectListener(String channel) {
        synchronized (this) {
            unsubscibedChannels.remove(channel);
            punsubscibedChannels.remove(channel);
        }
    }
    
    @Override
    public void onDisconnect() {
        Set<String> channels = new HashSet<String>();
        Set<String> pchannels = new HashSet<String>();
        synchronized (this) {
            channels.addAll(unsubscibedChannels);
            pchannels.addAll(punsubscibedChannels);
        }
        for (String channel : channels) {
            onMessage(new PubSubStatusMessage(PubSubType.UNSUBSCRIBE, channel));
        }
        for (String channel : pchannels) {
            onMessage(new PubSubStatusMessage(PubSubType.PUNSUBSCRIBE, channel));
        }
    }

    public void punsubscribe(final String ... channels) {
        synchronized (this) {
            for (String ch : channels) {
                patternChannels.remove(ch);
                punsubscibedChannels.add(ch);
            }
        }
        ChannelFuture future = async((MultiDecoder)null, RedisCommands.PUNSUBSCRIBE, channels);
        future.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    for (String channel : channels) {
                        removeDisconnectListener(channel);
                        onMessage(new PubSubStatusMessage(PubSubType.PUNSUBSCRIBE, channel));
                    }
                }
            }
        });
    }

    private <T, R> ChannelFuture async(MultiDecoder<Object> messageDecoder, RedisCommand<T> command, Object ... params) {
        return channel.writeAndFlush(new CommandData<T, R>(null, messageDecoder, null, command, params));
    }

    public Map<String, Codec> getChannels() {
        return Collections.unmodifiableMap(channels);
    }

    public Map<String, Codec> getPatternChannels() {
        return Collections.unmodifiableMap(patternChannels);
    }

}
