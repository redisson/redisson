package org.redisson.client.handler;

import java.util.concurrent.TimeUnit;

import org.redisson.client.RedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.concurrent.GenericFutureListener;

public class ConnectionWatchdog extends ChannelInboundHandlerAdapter {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Bootstrap bootstrap;
    private ChannelGroup channels;
    private static final int BACKOFF_CAP = 12;

    public ConnectionWatchdog(Bootstrap bootstrap, ChannelGroup channels) {
        this.bootstrap = bootstrap;
        this.channels  = channels;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channels.add(ctx.channel());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RedisConnection connection = ctx.channel().attr(RedisConnection.CONNECTION).get();
        if (!connection.isClosed()) {
            EventLoopGroup group = ctx.channel().eventLoop().parent();
            reconnect(group, connection);
        }
        ctx.fireChannelInactive();
    }

    private void reconnect(final EventLoopGroup group, final RedisConnection connection){
        group.schedule(new Runnable() {
            @Override
            public void run() {
                doReConnect(group, connection, 1);
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    private void doReConnect(final EventLoopGroup group, final RedisConnection connection, final int attempts) {
        if (connection.isClosed()) {
            return;
        }

        log.debug("reconnecting connection {} to {} ", connection, connection.getRedisClient().getAddr(), connection);

        bootstrap.connect().addListener(new GenericFutureListener<ChannelFuture>() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (connection.isClosed()) {
                    return;
                }

                if (future.isSuccess()) {
                    log.debug("connection {} connected to {}", connection, connection.getRedisClient().getAddr());
                    connection.updateChannel(future.channel());
                    return;
                }

                int timeout = 2 << attempts;
                group.schedule(new Runnable() {
                    @Override
                    public void run() {
                        doReConnect(group, connection, Math.min(BACKOFF_CAP, attempts + 1));
                    }
                }, timeout, TimeUnit.MILLISECONDS);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

}
