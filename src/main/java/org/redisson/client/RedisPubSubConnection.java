package org.redisson.client;

import org.redisson.client.handler.RedisData;
import org.redisson.client.protocol.Codec;
import org.redisson.client.protocol.PubSubMessage;
import org.redisson.client.protocol.PubSubMessageDecoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.StringCodec;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class RedisPubSubConnection {

    final Channel channel;
    final RedisClient redisClient;

    public RedisPubSubConnection(RedisClient redisClient, Channel channel) {
        this.redisClient = redisClient;
        this.channel = channel;
    }

    public Future<PubSubMessage> subscribe(String ... channel) {
        return async(new PubSubMessageDecoder(), RedisCommands.SUBSCRIBE, channel);
    }

    public Future<Long> publish(String channel, String msg) {
        return async(new StringCodec(), RedisCommands.PUBLISH, channel, msg);
    }

    public <T, R> Future<R> async(Codec encoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = redisClient.getBootstrap().group().next().<R>newPromise();
        channel.writeAndFlush(new RedisData<T, R>(promise, encoder, command, params));
        return promise;
    }

    public ChannelFuture closeAsync() {
        return channel.close();
    }

}
