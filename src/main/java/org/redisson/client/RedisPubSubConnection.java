/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.util.Queue;
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

import io.netty.channel.Channel;

public class RedisPubSubConnection extends RedisConnection {

    final Queue<RedisPubSubListener<Object>> listeners = new ConcurrentLinkedQueue<RedisPubSubListener<Object>>();

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
    }

    public void psubscribe(Codec codec, String ... channel) {
        async(new PubSubPatternMessageDecoder(codec.getValueDecoder()), RedisCommands.PSUBSCRIBE, channel);
    }

    public void unsubscribe(String ... channel) {
        async((MultiDecoder)null, RedisCommands.UNSUBSCRIBE, channel);
    }

    public void punsubscribe(String ... channel) {
        async((MultiDecoder)null, RedisCommands.PUNSUBSCRIBE, channel);
    }

    private <T, R> void async(MultiDecoder<Object> messageDecoder, RedisCommand<T> command, Object ... params) {
        channel.writeAndFlush(new CommandData<T, R>(null, messageDecoder, null, command, params));
    }

}
