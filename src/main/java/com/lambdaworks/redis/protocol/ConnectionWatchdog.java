// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.protocol;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisAsyncConnection;

/**
 * A netty {@link ChannelHandler} responsible for monitoring the channel and
 * reconnecting when the connection is lost.
 *
 * @author Will Glozer
 */
@ChannelHandler.Sharable
public class ConnectionWatchdog extends ChannelInboundHandlerAdapter{

    public static final AttributeKey<Boolean> SHUTDOWN_KEY = AttributeKey.valueOf("shutdown");

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Bootstrap bootstrap;
    private Channel channel;
    private ChannelGroup channels;
    private static final int BACKOFF_CAP = 12;

    /**
     * Create a new watchdog that adds to new connections to the supplied {@link ChannelGroup}
     * and establishes a new {@link Channel} when disconnected, while reconnect is true.
     *
     * @param bootstrap Configuration for new channels.
     */
    public ConnectionWatchdog(Bootstrap bootstrap, ChannelGroup channels) {
        this.bootstrap = bootstrap;
        this.channels  = channels;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        channels.add(channel);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ChannelPipeline pipeLine = channel.pipeline();
        CommandHandler<?, ?> handler = pipeLine.get(CommandHandler.class);
        RedisAsyncConnection<?, ?> connection = pipeLine.get(RedisAsyncConnection.class);
        if (connection.isReconnect()) {
            EventLoop loop = ctx.channel().eventLoop();
            reconnect(loop, handler, connection);
        }
        ctx.fireChannelInactive();
    }

    /**
     * Reconnect to the remote address that the closed channel was connected to.
     * This creates a new {@link ChannelPipeline} with the same handler instances
     * contained in the old channel's pipeline.
     *
     * @param loop EventLoop
     * @param handler Redis Command handle.
     * @param connection RedisAsyncConnection
     *
     * @throws Exception when reconnection fails.
     */
    private void reconnect(final EventLoop loop, final CommandHandler<?, ?> handler, final RedisAsyncConnection<?, ?> connection){
        loop.schedule(new Runnable() {
            @Override
            public void run() {
                doReConnect(loop, handler, connection, 1);
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    private void doReConnect(final EventLoop loop, final CommandHandler<?, ?> handler, final RedisAsyncConnection<?, ?> connection, final int attempts) {
        if (!connection.isReconnect()) {
            return;
        }

        log.debug("trying to reconnect {}", connection.getRedisClient().getAddr());

        ChannelFuture connect;
        synchronized (bootstrap) {
            connect = bootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(ConnectionWatchdog.this, handler, connection);
                }
            }).connect();
        }

        connect.addListener(new GenericFutureListener<ChannelFuture>() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.channel().attr(SHUTDOWN_KEY).get() != null) {
                    future.channel().pipeline().remove(ConnectionWatchdog.this);
                    return;
                }

                if (!future.isSuccess()) {
                    if (!connection.isReconnect()) {
                        return;
                    }

                    int timeout = 2 << attempts;
                    loop.schedule(new Runnable() {
                        @Override
                        public void run() {
                            doReConnect(loop, handler, connection, Math.min(BACKOFF_CAP, attempts + 1));
                        }
                    }, timeout, TimeUnit.MILLISECONDS);
                }
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    @Override
    public String toString() {
        return super.toString() + " - bootstrap: " + bootstrap;
    }

}
