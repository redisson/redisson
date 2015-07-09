package org.redisson.client;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.redisson.client.handler.RedisData;
import org.redisson.client.protocol.Codec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.PubSubStatusMessage;
import org.redisson.client.protocol.PubSubStatusDecoder;
import org.redisson.client.protocol.PubSubMessage;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.StringCodec;
import org.redisson.client.protocol.PubSubMessageDecoder;
import org.redisson.client.protocol.PubSubPatternMessage;
import org.redisson.client.protocol.PubSubPatternMessageDecoder;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class RedisPubSubConnection {

    public static final AttributeKey<RedisPubSubConnection> CONNECTION = AttributeKey.valueOf("connection");

    final ConcurrentLinkedQueue<RedisPubSubListener<Object>> listeners = new ConcurrentLinkedQueue<RedisPubSubListener<Object>>();

    final Channel channel;
    final RedisClient redisClient;

    public RedisPubSubConnection(RedisClient redisClient, Channel channel) {
        this.redisClient = redisClient;
        this.channel = channel;

        channel.attr(CONNECTION).set(this);
    }

    public void addListener(RedisPubSubListener listener) {
        listeners.add(listener);
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

    public Future<PubSubStatusMessage> subscribe(String ... channel) {
        return async(new PubSubStatusDecoder(), new PubSubMessageDecoder(), RedisCommands.SUBSCRIBE, channel);
    }

    public Future<PubSubStatusMessage> psubscribe(String ... channel) {
        return async(new PubSubStatusDecoder(), new PubSubPatternMessageDecoder(), RedisCommands.PSUBSCRIBE, channel);
    }

    public Future<PubSubStatusMessage> unsubscribe(String ... channel) {
        return async(new PubSubStatusDecoder(), RedisCommands.SUBSCRIBE, channel);
    }

    public Future<Long> publish(String channel, String msg) {
        return async(new StringCodec(), RedisCommands.PUBLISH, channel, msg);
    }

    public <T, R> Future<R> async(Codec encoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = redisClient.getBootstrap().group().next().<R>newPromise();
        channel.writeAndFlush(new RedisData<T, R>(promise, encoder, command, params));
        return promise;
    }

    public <T, R> Future<R> async(Codec encoder, Decoder<Object> nextDecoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = redisClient.getBootstrap().group().next().<R>newPromise();
        channel.writeAndFlush(new RedisData<T, R>(promise, nextDecoder, encoder, command, params));
        return promise;
    }


    public ChannelFuture closeAsync() {
        return channel.close();
    }

}
