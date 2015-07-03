// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.pubsub;

import static com.lambdaworks.redis.protocol.CommandType.PSUBSCRIBE;
import static com.lambdaworks.redis.protocol.CommandType.PUNSUBSCRIBE;
import static com.lambdaworks.redis.protocol.CommandType.SUBSCRIBE;
import static com.lambdaworks.redis.protocol.CommandType.UNSUBSCRIBE;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandArgs;

/**
 * An asynchronous thread-safe pub/sub connection to a redis server. After one or
 * more channels are subscribed to only pub/sub related commands or {@link #quit}
 * may be called.
 *
 * Incoming messages and results of the {@link #subscribe}/{@link #unsubscribe}
 * calls will be passed to all registered {@link RedisPubSubListener}s.
 *
 * A {@link com.lambdaworks.redis.protocol.ConnectionWatchdog} monitors each
 * connection and reconnects automatically until {@link #close} is called. Channel
 * and pattern subscriptions are renewed after reconnecting.
 *
 * @author Will Glozer
 */
public class RedisPubSubConnection<K, V> extends RedisAsyncConnection<K, V> {
    private final Queue<RedisPubSubListener<V>> listeners = new ConcurrentLinkedQueue<RedisPubSubListener<V>>();
    private Set<String> channels;
    private Set<String> patterns;

    /**
     * Initialize a new connection.
     *
     * @param queue     Command queue.
     * @param codec     Codec used to encode/decode keys and values.
     * @param timeout   Maximum time to wait for a responses.
     * @param unit      Unit of time for the timeout.
     * @param eventLoopGroup
     */
    public RedisPubSubConnection(RedisClient client, BlockingQueue<Command<K, V, ?>> queue, RedisCodec<K, V> codec, long timeout, TimeUnit unit, EventLoopGroup eventLoopGroup) {
        super(client, queue, codec, timeout, unit, eventLoopGroup);
        channels  = new HashSet<String>();
        patterns  = new HashSet<String>();
    }

    /**
     * Add a new listener.
     *
     * @param listener Listener.
     */
    public void addListener(RedisPubSubListener<V> listener) {
        listeners.add(listener);
    }

    /**
     * Remove an existing listener.
     *
     * @param listener Listener.
     */
    public void removeListener(RedisPubSubListener<V> listener) {
        listeners.remove(listener);
    }

    public void psubscribe(String... patterns) {
        dispatch(PSUBSCRIBE, new PubSubOutput<K, V>(codec), args(patterns));
    }

    public void subscribe(String... channels) {
        dispatch(SUBSCRIBE, new PubSubOutput<K, V>(codec), args(channels));
    }

    public Future<V> unsubscribe(String... channels) {
        return dispatch(UNSUBSCRIBE, new PubSubOutput<K, V>(codec), args(channels));
    }

    public Future<V> punsubscribe(String... patterns) {
      return dispatch(PUNSUBSCRIBE, new PubSubOutput<K, V>(codec), args(patterns));
    }


  @Override
    public synchronized void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        if (channels.size() > 0) {
            subscribe(channels.toArray(new String[channels.size()]));
            channels.clear();
        }

        if (patterns.size() > 0) {
            psubscribe(toArray(patterns));
            patterns.clear();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        PubSubOutput<K, V> output = (PubSubOutput<K, V>) msg;
        
        // Update internal state
        switch (output.type()) {
	        case psubscribe:
	            patterns.add(output.pattern());
	            break;
	        case punsubscribe:
	            patterns.remove(output.pattern());
	            break;
	        case subscribe:
	            channels.add(output.channel());
	            break;
	        case unsubscribe:
	            channels.remove(output.channel());
	            break;
            default:
                break;
        }
        
        // notify watchers, if any
        for (RedisPubSubListener<V> listener : listeners) {
            switch (output.type()) {
                case message:
                    listener.message(output.channel(), output.get());
                    break;
                case pmessage:
                    listener.message(output.pattern(), output.channel(), output.get());
                    break;
                case psubscribe:
                    listener.psubscribed(output.pattern(), output.count());
                    break;
                case punsubscribe:
                    listener.punsubscribed(output.pattern(), output.count());
                    break;
                case subscribe:
                    listener.subscribed(output.channel(), output.count());
                    break;
                case unsubscribe:
                    listener.unsubscribed(output.channel(), output.count());
                    break;
            }
        }
    }

    private CommandArgs<K, V> args(String... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        for (String key : keys) {
            args.add(key.toString());
        }
        return args;
    }

    @SuppressWarnings("unchecked")
    private <T> T[] toArray(Collection<T> c) {
        Class<T> cls = (Class<T>) c.iterator().next().getClass();
        T[] array = (T[]) Array.newInstance(cls, c.size());
        return c.toArray(array);
    }
}
