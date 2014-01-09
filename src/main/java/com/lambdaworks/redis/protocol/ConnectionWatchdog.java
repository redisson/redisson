// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.protocol;

import com.lambdaworks.redis.RedisAsyncConnection;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * A netty {@link ChannelHandler} responsible for monitoring the channel and
 * reconnecting when the connection is lost.
 *
 * @author Will Glozer
 */
@ChannelHandler.Sharable
public class ConnectionWatchdog extends ChannelInboundHandlerAdapter implements TimerTask {
    private Bootstrap bootstrap;
    private Channel channel;
    private ChannelGroup channels;
    private Timer timer;
    private boolean reconnect;
    private int attempts;

    /**
     * Create a new watchdog that adds to new connections to the supplied {@link ChannelGroup}
     * and establishes a new {@link Channel} when disconnected, while reconnect is true.
     *
     * @param bootstrap Configuration for new channels.
     * @param timer     Timer used for delayed reconnect.
     */
    public ConnectionWatchdog(Bootstrap bootstrap, ChannelGroup channels, Timer timer) {
        this.bootstrap = bootstrap;
        this.channels  = channels;
        this.timer     = timer;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
        channels.add(channel);
        attempts = 0;
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (reconnect) {
            if (attempts < 8) attempts++;
            int timeout = 2 << attempts;
            timer.newTimeout(this, timeout, TimeUnit.MILLISECONDS);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    /**
     * Reconnect to the remote address that the closed channel was connected to.
     * This creates a new {@link ChannelPipeline} with the same handler instances
     * contained in the old channel's pipeline.
     *
     * @param timeout Timer task handle.
     *
     * @throws Exception when reconnection fails.
     */
    @Override
    public void run(Timeout timeout) throws Exception {
        ChannelPipeline old = channel.pipeline();
        final CommandHandler<?, ?> handler = old.get(CommandHandler.class);
        final RedisAsyncConnection<?, ?> connection = old.get(RedisAsyncConnection.class);

        ChannelFuture connect = null;
        // TODO use better concurrent workaround
        synchronized (bootstrap) {
            connect = bootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(this, handler, connection);
                }
            }).connect();
        }
        connect.sync();
    }
}
