/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.incubator.channel.uring.IOUringChannelOption;
import io.netty.incubator.channel.uring.IOUringSocketChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.util.HashedWheelTimer;
import io.netty.util.NetUtil;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.redisson.api.RFuture;
import org.redisson.client.handler.RedisChannelInitializer;
import org.redisson.client.handler.RedisChannelInitializer.Type;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.RedisURI;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.UnknownHostException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Low-level Redis client
 * 
 * @author Nikita Koksharov
 *
 */
public final class RedisClient {

    private final AtomicReference<CompletableFuture<InetSocketAddress>> resolvedAddrFuture = new AtomicReference<>();
    private final Bootstrap bootstrap;
    private final Bootstrap pubSubBootstrap;
    private final RedisURI uri;
    private InetSocketAddress resolvedAddr;
    private final ChannelGroup channels;

    private ExecutorService executor;
    private final long commandTimeout;
    private Timer timer;
    private RedisClientConfig config;

    private boolean hasOwnTimer;
    private boolean hasOwnExecutor;
    private boolean hasOwnGroup;
    private boolean hasOwnResolver;
    private volatile boolean shutdown;

    public static RedisClient create(RedisClientConfig config) {
        return new RedisClient(config);
    }
    
    private RedisClient(RedisClientConfig config) {
        RedisClientConfig copy = new RedisClientConfig(config);
        if (copy.getTimer() == null) {
            copy.setTimer(new HashedWheelTimer());
            hasOwnTimer = true;
        }
        if (copy.getGroup() == null) {
            copy.setGroup(new NioEventLoopGroup());
            hasOwnGroup = true;
        }
        if (copy.getExecutor() == null) {
            copy.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2));
            hasOwnExecutor = true;
        }
        if (copy.getResolverGroup() == null) {
            if (config.getSocketChannelClass() == EpollSocketChannel.class) {
                copy.setResolverGroup(new DnsAddressResolverGroup(EpollDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault()));
            } else if (config.getSocketChannelClass() == KQueueSocketChannel.class) {
                copy.setResolverGroup(new DnsAddressResolverGroup(KQueueDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault()));
            } else {
                copy.setResolverGroup(new DnsAddressResolverGroup(NioDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault()));
            }
            hasOwnResolver = true;
        }

        this.config = copy;
        this.executor = copy.getExecutor();
        this.timer = copy.getTimer();
        
        uri = copy.getAddress();
        resolvedAddr = copy.getAddr();
        
        if (resolvedAddr != null) {
            resolvedAddrFuture.set(CompletableFuture.completedFuture(resolvedAddr));
        }
        
        channels = new DefaultChannelGroup(copy.getGroup().next());
        bootstrap = createBootstrap(copy, Type.PLAIN);
        pubSubBootstrap = createBootstrap(copy, Type.PUBSUB);
        
        this.commandTimeout = copy.getCommandTimeout();
    }

    private Bootstrap createBootstrap(RedisClientConfig config, Type type) {
        Bootstrap bootstrap = new Bootstrap()
                        .resolver(config.getResolverGroup())
                        .channel(config.getSocketChannelClass())
                        .group(config.getGroup());

        bootstrap.handler(new RedisChannelInitializer(bootstrap, config, this, channels, type));
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout());
        bootstrap.option(ChannelOption.SO_KEEPALIVE, config.isKeepAlive());
        bootstrap.option(ChannelOption.TCP_NODELAY, config.isTcpNoDelay());

        applyChannelOptions(config, bootstrap);

        config.getNettyHook().afterBoostrapInitialization(bootstrap);
        return bootstrap;
    }

    private void applyChannelOptions(RedisClientConfig config, Bootstrap bootstrap) {
        if (config.getSocketChannelClass() == NioSocketChannel.class) {
            SocketOption<Integer> countOption = null;
            SocketOption<Integer> idleOption = null;
            SocketOption<Integer> intervalOption = null;
            try {
                // fixes Intellij compilation issue with JDK 1.8
                Class<?> options = Class.forName("jdk.net.ExtendedSocketOptions");

                countOption = (SocketOption<Integer>) options.getDeclaredField("TCP_KEEPCOUNT").get(null);
                idleOption = (SocketOption<Integer>) options.getDeclaredField("TCP_KEEPIDLE").get(null);
                intervalOption = (SocketOption<Integer>) options.getDeclaredField("TCP_KEEPINTERVAL").get(null);
            } catch (ReflectiveOperationException e) {
                // skip
            }

            if (config.getTcpKeepAliveCount() > 0 && countOption != null) {
                bootstrap.option(NioChannelOption.of(countOption), config.getTcpKeepAliveCount());
            }
            if (config.getTcpKeepAliveIdle() > 0 && idleOption != null) {
                bootstrap.option(NioChannelOption.of(idleOption), config.getTcpKeepAliveIdle());
            }
            if (config.getTcpKeepAliveInterval() > 0 && intervalOption != null) {
                bootstrap.option(NioChannelOption.of(intervalOption), config.getTcpKeepAliveInterval());
            }
        } else if (config.getSocketChannelClass() == EpollSocketChannel.class) {
            if (config.getTcpKeepAliveCount() > 0) {
                bootstrap.option(EpollChannelOption.TCP_KEEPCNT, config.getTcpKeepAliveCount());
            }
            if (config.getTcpKeepAliveIdle() > 0) {
                bootstrap.option(EpollChannelOption.TCP_KEEPIDLE, config.getTcpKeepAliveIdle());
            }
            if (config.getTcpKeepAliveInterval() > 0) {
                bootstrap.option(EpollChannelOption.TCP_KEEPINTVL, config.getTcpKeepAliveInterval());
            }
            if (config.getTcpUserTimeout() > 0) {
                bootstrap.option(EpollChannelOption.TCP_USER_TIMEOUT, config.getTcpUserTimeout());
            }
        } else if (config.getSocketChannelClass() == IOUringSocketChannel.class) {
            if (config.getTcpKeepAliveCount() > 0) {
                bootstrap.option(IOUringChannelOption.TCP_KEEPCNT, config.getTcpKeepAliveCount());
            }
            if (config.getTcpKeepAliveIdle() > 0) {
                bootstrap.option(IOUringChannelOption.TCP_KEEPIDLE, config.getTcpKeepAliveIdle());
            }
            if (config.getTcpKeepAliveInterval() > 0) {
                bootstrap.option(IOUringChannelOption.TCP_KEEPINTVL, config.getTcpKeepAliveInterval());
            }
            if (config.getTcpUserTimeout() > 0) {
                bootstrap.option(IOUringChannelOption.TCP_USER_TIMEOUT, config.getTcpUserTimeout());
            }
        }
    }

    public InetSocketAddress getAddr() {
        return resolvedAddr;
    }

    public long getCommandTimeout() {
        return commandTimeout;
    }

    public RedisClientConfig getConfig() {
        return config;
    }

    public Timer getTimer() {
        return timer;
    }
    
    public RedisConnection connect() {
        try {
            return connectAsync().toCompletableFuture().join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RedisException) {
                throw (RedisException) e.getCause();
            } else {
                throw new RedisConnectionException("Unable to connect to: " + uri, e);
            }
        }
    }
    
    public CompletableFuture<InetSocketAddress> resolveAddr() {
        if (resolvedAddrFuture.get() != null) {
            return resolvedAddrFuture.get();
        }
        
        CompletableFuture<InetSocketAddress> promise = new CompletableFuture<>();
        if (!resolvedAddrFuture.compareAndSet(null, promise)) {
            return resolvedAddrFuture.get();
        }
        
        byte[] addr = NetUtil.createByteArrayFromIpAddressString(uri.getHost());
        if (addr != null) {
            try {
                resolvedAddr = new InetSocketAddress(InetAddress.getByAddress(uri.getHost(), addr), uri.getPort());
            } catch (UnknownHostException e) {
                // skip
            }
            promise.complete(resolvedAddr);
            return promise;
        }
        
        AddressResolver<InetSocketAddress> resolver = (AddressResolver<InetSocketAddress>) bootstrap.config().resolver().getResolver(bootstrap.config().group().next());
        Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        resolveFuture.addListener((FutureListener<InetSocketAddress>) future -> {
            if (!future.isSuccess()) {
                promise.completeExceptionally(new RedisConnectionException(future.cause()));
                return;
            }

            InetSocketAddress resolved = future.getNow();
            byte[] addr1 = resolved.getAddress().getAddress();
            resolvedAddr = new InetSocketAddress(InetAddress.getByAddress(uri.getHost(), addr1), resolved.getPort());
            promise.complete(resolvedAddr);
        });
        return promise;
    }

    public RFuture<RedisConnection> connectAsync() {
        CompletableFuture<InetSocketAddress> addrFuture = resolveAddr();
        CompletableFuture<RedisConnection> f = addrFuture.thenCompose(res -> {
            CompletableFuture<RedisConnection> r = new CompletableFuture<>();
            ChannelFuture channelFuture = bootstrap.connect(res);
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (isShutdown()) {
                        RedisConnectionException cause = new RedisConnectionException("RedisClient is shutdown");
                        r.completeExceptionally(cause);
                        return;
                    }

                    if (future.isSuccess()) {
                        RedisConnection c = RedisConnection.getFrom(future.channel());
                        c.getConnectionPromise().whenComplete((res, e) -> {
                            bootstrap.config().group().execute(new Runnable() {
                                @Override
                                public void run() {
                                    if (e == null) {
                                        if (!r.complete(c)) {
                                            c.closeAsync();
                                        } else {
                                            executor.execute(() -> {
                                                if (config.getConnectedListener() != null) {
                                                    config.getConnectedListener().accept(getAddr());
                                                }
                                            });
                                        }
                                    } else {
                                        r.completeExceptionally(e);
                                        c.closeAsync();
                                    }
                                }
                            });
                        });
                    } else {
                        bootstrap.config().group().execute(new Runnable() {
                            public void run() {
                                r.completeExceptionally(future.cause());
                            }
                        });
                    }
                }
            });
            return r;
        });
        return new CompletableFutureWrapper<>(f);
    }

    public RedisPubSubConnection connectPubSub() {
        try {
            return connectPubSubAsync().toCompletableFuture().join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RedisException) {
                throw (RedisException) e.getCause();
            } else {
                throw new RedisConnectionException("Unable to connect to: " + uri, e);
            }
        }
    }

    public RFuture<RedisPubSubConnection> connectPubSubAsync() {
        CompletableFuture<InetSocketAddress> nameFuture = resolveAddr();
        CompletableFuture<RedisPubSubConnection> f = nameFuture.thenCompose(res -> {
            CompletableFuture<RedisPubSubConnection> r = new CompletableFuture<>();
            ChannelFuture channelFuture = pubSubBootstrap.connect(res);
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (isShutdown()) {
                        RedisConnectionException cause = new RedisConnectionException("RedisClient is shutdown");
                        r.completeExceptionally(cause);
                        return;
                    }

                    if (future.isSuccess()) {
                        RedisPubSubConnection c = RedisPubSubConnection.getFrom(future.channel());
                        c.getConnectionPromise().whenComplete((res, e) -> {
                            pubSubBootstrap.config().group().execute(new Runnable() {
                                @Override
                                public void run() {
                                    if (e == null) {
                                        if (!r.complete(c)) {
                                            c.closeAsync();
                                        }
                                    } else {
                                        r.completeExceptionally(e);
                                        c.closeAsync();
                                    }
                                }
                            });
                        });
                    } else {
                        pubSubBootstrap.config().group().execute(new Runnable() {
                            public void run() {
                                r.completeExceptionally(future.cause());
                            }
                        });
                    }
                }
            });
            return r;
        });
        return new CompletableFutureWrapper<>(f);
    }

    public void shutdown() {
        shutdownAsync().toCompletableFuture().join();
    }

    public RFuture<Void> shutdownAsync() {
        shutdown = true;
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (channels.isEmpty() || config.getGroup().isShuttingDown()) {
            shutdown(result);
            return new CompletableFutureWrapper<>(result);
        }

        for (Channel channel : channels) {
            RedisConnection connection = RedisConnection.getFrom(channel);
            if (connection != null) {
                connection.closeAsync();
            }
        }

        ChannelGroupFuture channelsFuture = channels.close();
        channelsFuture.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    result.completeExceptionally(future.cause());
                    return;
                }
                
                shutdown(result);
            }
        });

        return new CompletableFutureWrapper<>(result);
    }

    public boolean isShutdown() {
        return shutdown || bootstrap.config().group().isShuttingDown();
    }

    private void shutdown(CompletableFuture<Void> result) {
        if (!hasOwnTimer && !hasOwnExecutor && !hasOwnResolver && !hasOwnGroup) {
            result.complete(null);
        } else {
            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        if (hasOwnTimer) {
                            timer.stop();
                        }
                        
                        if (hasOwnExecutor) {
                            executor.shutdown();
                            executor.awaitTermination(15, TimeUnit.SECONDS);
                        }
                        
                        if (hasOwnResolver) {
                            bootstrap.config().resolver().close();
                        }
                        if (hasOwnGroup) {
                            bootstrap.config().group().shutdownGracefully();
                        }
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                        return;
                    }

                    result.complete(null);
                }
            };
            t.start();
        }
    }

    @Override
    public String toString() {
        return "[addr=" + uri + "," + resolvedAddr + "]";
    }

}
