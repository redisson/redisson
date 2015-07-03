// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandHandler;
import com.lambdaworks.redis.protocol.ConnectionWatchdog;
import com.lambdaworks.redis.pubsub.PubSubCommandHandler;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

/**
 * A scalable thread-safe <a href="http://redis.io/">Redis</a> client. Multiple threads
 * may share one connection provided they avoid blocking and transactional operations
 * such as BLPOP and MULTI/EXEC.
 *
 * @author Will Glozer
 */
public class RedisClient {

    private Bootstrap bootstrap;
    private ChannelGroup channels;
    private long timeout;
    private TimeUnit unit;
    private InetSocketAddress addr;

    /**
     * Create a new client that connects to the supplied host on the default port.
     *
     * @param host    Server hostname.
     */
    public RedisClient(EventLoopGroup group, String host) {
        this(group, host, 6379, 60000);
    }

    /**
     * Create a new client that connects to the supplied host and port. Connection
     * attempts and non-blocking commands will {@link #setDefaultTimeout timeout}
     * after 60 seconds.
     *
     * @param host    Server hostname.
     * @param port    Server port.
     */
    public RedisClient(EventLoopGroup group, String host, int port, int timeout) {
        this(group, NioSocketChannel.class, host, port, timeout);
    }

    public RedisClient(EventLoopGroup group, Class<? extends SocketChannel> socketChannelClass, String host,
            int port, int timeout) {
        addr = new InetSocketAddress(host, port);

        bootstrap = new Bootstrap().channel(socketChannelClass).group(group).remoteAddress(addr);

        setDefaultTimeout(timeout, TimeUnit.MILLISECONDS);

        channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    }

    /**
     * Set the default timeout for {@link RedisConnection connections} created by
     * this client. The timeout applies to connection attempts and non-blocking
     * commands.
     *
     * @param timeout   Default connection timeout.
     * @param unit      Unit of time for the timeout.
     */
    public void setDefaultTimeout(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit    = unit;
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) unit.toMillis(timeout));
    }

    /**
     * Open a new synchronous connection to the redis server that treats
     * keys and values as UTF-8 strings.
     *
     * @return A new connection.
     */
    public RedisConnection<String, String> connect() {
        return connect(new Utf8StringCodec());
    }

    /**
     * Open a new asynchronous connection to the redis server that treats
     * keys and values as UTF-8 strings.
     *
     * @return A new connection.
     */
    public RedisAsyncConnection<String, String> connectAsync() {
        return connectAsync(new Utf8StringCodec());
    }

    /**
     * Open a new pub/sub connection to the redis server that treats
     * keys and values as UTF-8 strings.
     *
     * @return A new connection.
     */
    public RedisPubSubConnection<String, String> connectPubSub() {
        return connectPubSub(new Utf8StringCodec());
    }

    /**
     * Open a new synchronous connection to the redis server. Use the supplied
     * {@link RedisCodec codec} to encode/decode keys and values.
     *
     * @param codec Use this codec to encode/decode keys and values.
     *
     * @return A new connection.
     */
    public <K, V> RedisConnection<K, V> connect(RedisCodec<K, V> codec) {
        return new RedisConnection<K, V>(connectAsync(codec));
    }

    /**
     * Open a new asynchronous connection to the redis server. Use the supplied
     * {@link RedisCodec codec} to encode/decode keys and values.
     *
     * @param codec Use this codec to encode/decode keys and values.
     *
     * @return A new connection.
     */
    public <K, V> RedisAsyncConnection<K, V> connectAsync(RedisCodec<K, V> codec) {
        BlockingQueue<Command<K, V, ?>> queue = new LinkedBlockingQueue<Command<K, V, ?>>();

        CommandHandler<K, V> handler = new CommandHandler<K, V>(queue);
        RedisAsyncConnection<K, V> connection = new RedisAsyncConnection<K, V>(this, queue, codec, timeout, unit, bootstrap.group());

        return connect(handler, connection);
    }

    /**
     * Open a new pub/sub connection to the redis server. Use the supplied
     * {@link RedisCodec codec} to encode/decode keys and values.
     *
     * @param codec Use this codec to encode/decode keys and values.
     *
     * @return A new pub/sub connection.
     */
    public <K, V> RedisPubSubConnection<K, V> connectPubSub(RedisCodec<K, V> codec) {
        BlockingQueue<Command<K, V, ?>> queue = new LinkedBlockingQueue<Command<K, V, ?>>();

        PubSubCommandHandler<K, V> handler = new PubSubCommandHandler<K, V>(queue, codec);
        RedisPubSubConnection<K, V> connection = new RedisPubSubConnection<K, V>(this, queue, codec, timeout, unit, bootstrap.group());

        return connect(handler, connection);
    }

    private <K, V, T extends RedisAsyncConnection<K, V>> T connect(final CommandHandler<K, V> handler, final T connection) {
        try {
            final ConnectionWatchdog watchdog = new ConnectionWatchdog(bootstrap, channels);

            ChannelFuture connect = null;
            // TODO use better concurrent workaround
            synchronized (bootstrap) {
                connect = bootstrap.handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(watchdog, handler, connection);
                    }
                }).connect();
            }
            connect.sync();

            connection.setReconnect(true);

            return connection;
        } catch (Throwable e) {
            throw new RedisConnectionException("Unable to connect " + addr, e);
        }
    }

    /**
     * Shutdown this client and close all open connections. The client should be
     * discarded after calling shutdown.
     */
    public void shutdown() {
        ChannelGroupFuture future = shutdownAsync();
        future.awaitUninterruptibly();
    }

    public ChannelGroupFuture shutdownAsync() {
        bootstrap.attr(ConnectionWatchdog.SHUTDOWN_KEY, true);

        for (Channel c : channels) {
            ChannelPipeline pipeline = c.pipeline();
            RedisAsyncConnection<?, ?> connection = pipeline.get(RedisAsyncConnection.class);
            connection.close();
        }
        return channels.close();
    }

    public InetSocketAddress getAddr() {
        return addr;
    }

    @Override
    public String toString() {
        return "RedisClient [addr=" + addr + "]";
    }

}

