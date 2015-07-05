package org.redisson.client;

import org.redisson.client.handler.RedisData;
import org.redisson.client.protocol.Codec;
import org.redisson.client.protocol.RedisCommand;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class RedisConnection {

    final Channel channel;
    final RedisClient redisClient;

    public RedisConnection(RedisClient redisClient, Channel channel) {
        super();
        this.redisClient = redisClient;
        this.channel = channel;
    }

    public <R> R await(Future<R> cmd) {
        if (!cmd.awaitUninterruptibly(redisClient.getTimeout(), redisClient.getTimeoutUnit())) {
            Promise<R> promise = (Promise<R>)cmd;
            RedisTimeoutException ex = new RedisTimeoutException();
            promise.setFailure(ex);
            throw ex;
        }
        if (!cmd.isSuccess()) {
            if (cmd.cause() instanceof RedisException) {
                throw (RedisException) cmd.cause();
            }
            throw new RedisException("Unexpected exception while processing command", cmd.cause());
        }
        return cmd.getNow();
    }

    public <V> V get(Future<V> future) {
        future.awaitUninterruptibly();
        if (future.isSuccess()) {
            return future.getNow();
        }

        if (future.cause() instanceof RedisException) {
            throw (RedisException) future.cause();
        }
        throw new RedisException("Unexpected exception while processing command", future.cause());
    }

    public <T, R> R sync(Codec encoder, RedisCommand<T> command, Object ... params) {
        Future<R> r = async(encoder, command, params);
        return await(r);
    }

    public <T, R> Future<R> async(Codec encoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = redisClient.getBootstrap().group().next().<R>newPromise();
        channel.writeAndFlush(new RedisData<T, R>(promise, encoder, command, params));
        return promise;
    }

//  public <R> Future<R> execute(Codec encoder, RedisCommand<R> command, Object ... params) {
//  Promise<R> promise = bootstrap.group().next().<R>newPromise();
//  channel.writeAndFlush(new RedisData<R, R>(promise, encoder, command, params));
//  return promise;
//}



}
