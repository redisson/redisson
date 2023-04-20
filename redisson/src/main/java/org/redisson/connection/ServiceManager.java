/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.connection;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.util.*;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.*;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import org.redisson.ElementsSubscribeService;
import org.redisson.Version;
import org.redisson.api.NatMapper;
import org.redisson.api.RFuture;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.TransportMode;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.InfinitySemaphoreLatch;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class ServiceManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final Timeout DUMMY_TIMEOUT = new Timeout() {
        @Override
        public Timer timer() {
            return null;
        }

        @Override
        public TimerTask task() {
            return null;
        }

        @Override
        public boolean isExpired() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean cancel() {
            return true;
        }
    };

    private final ConnectionEventsHub connectionEventsHub = new ConnectionEventsHub();

    private final String id = UUID.randomUUID().toString();

    private final EventLoopGroup group;

    private final Class<? extends SocketChannel> socketChannelClass;

    private final AddressResolverGroup<InetSocketAddress> resolverGroup;

    private final ExecutorService executor;

    private final Config cfg;

    private MasterSlaveServersConfig config;

    private HashedWheelTimer timer;

    private IdleConnectionWatcher connectionWatcher;

    private final Promise<Void> shutdownPromise = ImmediateEventExecutor.INSTANCE.newPromise();

    private final InfinitySemaphoreLatch shutdownLatch = new InfinitySemaphoreLatch();

    private final ElementsSubscribeService elementsSubscribeService = new ElementsSubscribeService(this);

    private NatMapper natMapper = NatMapper.direct();

    private static final Map<InetSocketAddress, Set<String>> SCRIPT_SHA_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, String> SHA_CACHE = new LRUCacheMap<>(500, 0, 0);

    public ServiceManager(Config cfg) {
        Version.logVersion();

        if (cfg.getTransportMode() == TransportMode.EPOLL) {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new EpollEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = EpollSocketChannel.class;
            if (PlatformDependent.isAndroid()) {
                this.resolverGroup = DefaultAddressResolverGroup.INSTANCE;
            } else {
                this.resolverGroup = cfg.getAddressResolverGroupFactory().create(EpollDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
            }
        } else if (cfg.getTransportMode() == TransportMode.KQUEUE) {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new KQueueEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = KQueueSocketChannel.class;
            if (PlatformDependent.isAndroid()) {
                this.resolverGroup = DefaultAddressResolverGroup.INSTANCE;
            } else {
                this.resolverGroup = cfg.getAddressResolverGroupFactory().create(KQueueDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
            }
        } else {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new NioEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = NioSocketChannel.class;
            if (PlatformDependent.isAndroid()) {
                this.resolverGroup = DefaultAddressResolverGroup.INSTANCE;
            } else {
                this.resolverGroup = cfg.getAddressResolverGroupFactory().create(NioDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
            }
        }

        if (cfg.getExecutor() == null) {
            int threads = Runtime.getRuntime().availableProcessors() * 2;
            if (cfg.getThreads() != 0) {
                threads = cfg.getThreads();
            }
            executor = Executors.newFixedThreadPool(threads, new DefaultThreadFactory("redisson"));
        } else {
            executor = cfg.getExecutor();
        }

        this.cfg = cfg;

        if (cfg.getConnectionListener() != null) {
            this.connectionEventsHub.addListener(cfg.getConnectionListener());
        }

        this.connectionEventsHub.addListener(new ConnectionListener() {
            @Override
            public void onConnect(InetSocketAddress addr) {
                // empty
            }

            @Override
            public void onDisconnect(InetSocketAddress addr) {
                SCRIPT_SHA_CACHE.remove(addr);
            }
        });
    }

    public void initTimer() {
        int[] timeouts = new int[]{config.getRetryInterval(), config.getTimeout()};
        Arrays.sort(timeouts);
        int minTimeout = timeouts[0];
        if (minTimeout % 100 != 0) {
            minTimeout = (minTimeout % 100) / 2;
        } else if (minTimeout == 100) {
            minTimeout = 50;
        } else {
            minTimeout = 100;
        }

        timer = new HashedWheelTimer(new DefaultThreadFactory("redisson-timer"), minTimeout, TimeUnit.MILLISECONDS, 1024, false);

        connectionWatcher = new IdleConnectionWatcher(group, config);
    }

    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        try {
            return timer.newTimeout(task, delay, unit);
        } catch (IllegalStateException e) {
            if (isShuttingDown()) {
                return DUMMY_TIMEOUT;
            }

            throw e;
        }
    }

    public boolean isShuttingDown() {
        return shutdownLatch.isClosed();
    }

    public boolean isShutdown() {
        return group.isTerminated();
    }

    public ConnectionEventsHub getConnectionEventsHub() {
        return connectionEventsHub;
    }

    public String getId() {
        return id;
    }

    public EventLoopGroup getGroup() {
        return group;
    }

    public AddressResolverGroup<InetSocketAddress> getResolverGroup() {
        return resolverGroup;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public Config getCfg() {
        return cfg;
    }

    public HashedWheelTimer getTimer() {
        return timer;
    }

    public IdleConnectionWatcher getConnectionWatcher() {
        return connectionWatcher;
    }

    public Class<? extends SocketChannel> getSocketChannelClass() {
        return socketChannelClass;
    }

    public Promise<Void> getShutdownPromise() {
        return shutdownPromise;
    }

    public InfinitySemaphoreLatch getShutdownLatch() {
        return shutdownLatch;
    }

    public RedisNodeNotFoundException createNodeNotFoundException(NodeSource source) {
        RedisNodeNotFoundException ex;
        if (source.getSlot() != null && source.getAddr() == null && source.getRedisClient() == null) {
            ex = new RedisNodeNotFoundException("Node for slot: " + source.getSlot() + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
        } else {
            ex = new RedisNodeNotFoundException("Node: " + source + " hasn't been discovered yet. Increase value of retryAttempts and/or retryInterval settings.");
        }
        return ex;
    }

    public MasterSlaveServersConfig getConfig() {
        return config;
    }

    public void setConfig(MasterSlaveServersConfig config) {
        this.config = config;
    }

    public ElementsSubscribeService getElementsSubscribeService() {
        return elementsSubscribeService;
    }

    public CompletableFuture<RedisURI> resolveIP(RedisURI address) {
        return resolveIP(address.getScheme(), address);
    }

    public CompletableFuture<RedisURI> resolveIP(String scheme, RedisURI address) {
        if (address.isIP()) {
            RedisURI addr = toURI(scheme, address.getHost(), "" + address.getPort());
            return CompletableFuture.completedFuture(addr);
        }

        CompletableFuture<RedisURI> result = new CompletableFuture<>();
        AddressResolver<InetSocketAddress> resolver = resolverGroup.getResolver(group.next());
        InetSocketAddress addr = InetSocketAddress.createUnresolved(address.getHost(), address.getPort());
        Future<InetSocketAddress> future = resolver.resolve(addr);
        future.addListener((FutureListener<InetSocketAddress>) f -> {
            if (!f.isSuccess()) {
                log.error("Unable to resolve {}", address, f.cause());
                result.completeExceptionally(f.cause());
                return;
            }

            InetSocketAddress s = f.getNow();
            RedisURI uri = toURI(scheme, s.getAddress().getHostAddress(), "" + address.getPort());
            result.complete(uri);
        });
        return result;
    }

    public RedisURI toURI(String scheme, String host, String port) {
        // convert IPv6 address to unified compressed format
        if (NetUtil.isValidIpV6Address(host)) {
            byte[] addr = NetUtil.createByteArrayFromIpAddressString(host);
            try {
                InetAddress ia = InetAddress.getByAddress(host, addr);
                host = ia.getHostAddress();
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
        RedisURI uri = new RedisURI(scheme + "://" + host + ":" + port);
        return natMapper.map(uri);
    }

    public void setNatMapper(NatMapper natMapper) {
        this.natMapper = natMapper;
    }

    public boolean isCached(InetSocketAddress addr, String script) {
        Set<String> values = SCRIPT_SHA_CACHE.computeIfAbsent(addr, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        String sha = calcSHA(script);
        return values.contains(sha);
    }

    public void cacheScripts(InetSocketAddress addr, Set<String> scripts) {
        Set<String> values = SCRIPT_SHA_CACHE.computeIfAbsent(addr, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        for (String script : scripts) {
            values.add(calcSHA(script));
        }
    }

    public String calcSHA(String script) {
        return SHA_CACHE.computeIfAbsent(script, k -> {
            try {
                MessageDigest mdigest = MessageDigest.getInstance("SHA-1");
                byte[] s = mdigest.digest(script.getBytes());
                return ByteBufUtil.hexDump(s);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public <T> RFuture<T> execute(Supplier<RFuture<T>> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        int retryAttempts = config.getRetryAttempts();
        AtomicInteger attempts = new AtomicInteger(retryAttempts);
        execute(attempts, result, supplier);
        return new CompletableFutureWrapper<>(result);
    }

    private <T> void execute(AtomicInteger attempts, CompletableFuture<T> result, Supplier<RFuture<T>> supplier) {
        RFuture<T> future = supplier.get();
        future.whenComplete((r, e) -> {
            if (e != null) {
                if (e.getCause().getMessage().equals("None of slaves were synced")) {
                    if (attempts.decrementAndGet() < 0) {
                        result.completeExceptionally(e);
                        return;
                    }

                    newTimeout(t -> execute(attempts, result, supplier),
                            config.getRetryInterval(), TimeUnit.MILLISECONDS);
                    return;
                }

                result.completeExceptionally(e);
                return;
            }

            result.complete(r);
        });
    }

    public <T> CompletionStage<T> handleNoSync(CompletionStage<T> stage, Supplier<CompletionStage<?>> supplier) {
        CompletionStage<T> s = stage.handle((r, ex) -> {
            if (ex != null) {
                if (ex.getCause().getMessage().equals("None of slaves were synced")) {
                    return supplier.get().handle((r1, e) -> {
                        if (e != null) {
                            if (e.getCause().getMessage().equals("None of slaves were synced")) {
                                throw new CompletionException(ex.getCause());
                            }
                            e.getCause().addSuppressed(ex.getCause());
                        }
                        throw new CompletionException(ex.getCause());
                    });
                } else {
                    throw new CompletionException(ex.getCause());
                }
            }
            return CompletableFuture.completedFuture(r);
        }).thenCompose(f -> (CompletionStage<T>) f);
        return s;
    }

}
