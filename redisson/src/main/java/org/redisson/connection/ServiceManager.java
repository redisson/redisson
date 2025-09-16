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
package org.redisson.connection;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.incubator.channel.uring.IOUringDatagramChannel;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;
import io.netty.incubator.channel.uring.IOUringSocketChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.*;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.PlatformDependent;
import org.redisson.ElementsSubscribeService;
import org.redisson.QueueTransferService;
import org.redisson.RedissonShutdownException;
import org.redisson.api.NatMapper;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.NoSyncedSlavesException;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.Protocol;
import org.redisson.config.TransportMode;
import org.redisson.liveobject.resolver.MapResolver;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.FastRemovalQueue;
import org.redisson.misc.RandomXoshiro256PlusPlus;
import org.redisson.misc.RedisURI;
import org.redisson.remote.ResponseEntry;
import org.redisson.renewal.LockRenewalScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class ServiceManager {

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

    private Class<? extends DuplexChannel> socketChannelClass;

    private final AddressResolverGroup<InetSocketAddress> resolverGroup;

    private final ExecutorService executor;

    private final Config cfg;

    private MasterSlaveServersConfig config;

    private HashedWheelTimer timer;

    private IdleConnectionWatcher connectionWatcher;

    private final AtomicBoolean shutdownLatch = new AtomicBoolean();

    private final ElementsSubscribeService elementsSubscribeService = new ElementsSubscribeService(this);

    private NatMapper natMapper = NatMapper.direct();

    private static final Map<InetSocketAddress, Set<String>> SCRIPT_SHA_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, String> SHA_CACHE = new LRUCacheMap<>(500, 0, 0);

    private final Map<String, ResponseEntry> responses = new ConcurrentHashMap<>();

    private final QueueTransferService queueTransferService = new QueueTransferService();

    private LockRenewalScheduler renewalScheduler;

    public ServiceManager(MasterSlaveServersConfig config, Config cfg) {
        RedisURI u = null;
        if (config.getMasterAddress() != null) {
            u = new RedisURI(config.getMasterAddress());
            if (u.isUDS()) {
                if (!cfg.isSingleConfig()) {
                    throw new IllegalStateException("UDS is supported only in a single server mode");
                }
                if (cfg.getTransportMode() != TransportMode.EPOLL
                        && cfg.getTransportMode() != TransportMode.KQUEUE) {
                    throw new IllegalStateException("UDS is supported only if transportMode = EPOLL or KQUEUE");
                }
            }
        }

        if (cfg.getTransportMode() == TransportMode.EPOLL) {
            if (cfg.getEventLoopGroup() == null) {
                if (cfg.getNettyExecutor() != null) {
                    this.group = new EpollEventLoopGroup(cfg.getNettyThreads(), cfg.getNettyExecutor());
                } else {
                    this.group = new EpollEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
                }
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = EpollSocketChannel.class;

            if (u != null && u.isUDS()) {
                this.socketChannelClass = EpollDomainSocketChannel.class;
            }

            if (PlatformDependent.isAndroid()) {
                this.resolverGroup = DefaultAddressResolverGroup.INSTANCE;
            } else {
                this.resolverGroup = cfg.getAddressResolverGroupFactory().create(EpollDatagramChannel.class, EpollSocketChannel.class, DnsServerAddressStreamProviders.platformDefault());
            }
        } else if (cfg.getTransportMode() == TransportMode.KQUEUE) {
            if (cfg.getEventLoopGroup() == null) {
                if (cfg.getNettyExecutor() != null) {
                    this.group = new KQueueEventLoopGroup(cfg.getNettyThreads(), cfg.getNettyExecutor());
                } else {
                    this.group = new KQueueEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
                }
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = KQueueSocketChannel.class;

            if (u != null && u.isUDS()) {
                this.socketChannelClass = KQueueDomainSocketChannel.class;
            }

            this.resolverGroup = cfg.getAddressResolverGroupFactory().create(KQueueDatagramChannel.class, KQueueSocketChannel.class, DnsServerAddressStreamProviders.platformDefault());
        } else if (cfg.getTransportMode() == TransportMode.IO_URING) {
            if (cfg.getEventLoopGroup() == null) {
                this.group = createIOUringGroup(cfg);
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = IOUringSocketChannel.class;
            this.resolverGroup = cfg.getAddressResolverGroupFactory().create(IOUringDatagramChannel.class, IOUringSocketChannel.class, DnsServerAddressStreamProviders.platformDefault());
        } else {
            if (cfg.getEventLoopGroup() == null) {
                if (cfg.getNettyExecutor() != null) {
                    this.group = new NioEventLoopGroup(cfg.getNettyThreads(), cfg.getNettyExecutor());
                } else {
                    this.group = new NioEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
                }
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = NioSocketChannel.class;
            if (PlatformDependent.isAndroid()) {
                this.resolverGroup = DefaultAddressResolverGroup.INSTANCE;
            } else {
                this.resolverGroup = cfg.getAddressResolverGroupFactory().create(NioDatagramChannel.class, NioSocketChannel.class, DnsServerAddressStreamProviders.platformDefault());
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
        this.config = config;

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

        initTimer();
    }

    // for Quarkus substitution
    private static EventLoopGroup createIOUringGroup(Config cfg) {
        return new IOUringEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
    }

    private void initTimer() {
        Duration testdelay = config.getRetryDelay().calcDelay(0);
        int minTimeout = Math.min((int) testdelay.toMillis(), config.getTimeout());
        if (minTimeout <= 0) {
            minTimeout = 10;
        } else if (minTimeout % 100 != 0) {
            minTimeout = Math.max(10, (minTimeout % 100) / 2);
        } else if (minTimeout == 100) {
            minTimeout = 50;
        } else {
            minTimeout = 100;
        }

        timer = new HashedWheelTimer(new DefaultThreadFactory("redisson-timer"),
                minTimeout, TimeUnit.MILLISECONDS, 1024, false);

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
        return shutdownLatch.get();
    }

    public boolean isShuttingDown(Throwable e) {
        return e instanceof RedissonShutdownException
                    || e.getCause() instanceof RedissonShutdownException;
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

    public CompletableFuture<List<RedisURI>> resolveAll(RedisURI uri) {
        if (uri.isIP()) {
            RedisURI mappedUri = toURI(uri.getScheme(), uri.getHost(), "" + uri.getPort());
            return CompletableFuture.completedFuture(Collections.singletonList(mappedUri));
        }

        AddressResolver<InetSocketAddress> resolver = resolverGroup.getResolver(group.next());
        Future<List<InetSocketAddress>> future = resolver.resolveAll(InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        CompletableFuture<List<RedisURI>> result = new CompletableFuture<>();
        future.addListener((GenericFutureListener<Future<List<InetSocketAddress>>>) f -> {
            if (!f.isSuccess()) {
                log.error("Unable to resolve {}", uri, f.cause());
                result.completeExceptionally(f.cause());
                return;
            }

            List<RedisURI> nodes = future.getNow().stream().map(addr -> {
                return toURI(uri.getScheme(), addr.getAddress().getHostAddress(), "" + addr.getPort());
            }).collect(Collectors.toList());

            long loopbackCount = future.getNow().stream()
                    .filter(addr -> addr.getAddress().isLoopbackAddress())
                    .count();
            if (loopbackCount > 1) {
                nodes.sort(Comparator.comparing(RedisURI::getHost));
                nodes = nodes.subList(0, 1);
            }

            result.complete(nodes);
        });
        return result;
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

    public Class<? extends DuplexChannel> getSocketChannelClass() {
        return socketChannelClass;
    }

    private final FastRemovalQueue<CompletableFuture<?>> lastFutures = new FastRemovalQueue<>();

    public void addFuture(CompletableFuture<?> future) {
        lastFutures.add(future);
        future.whenComplete((r, e) -> {
            lastFutures.remove(future);
        });

        if (lastFutures.size() > 100) {
            lastFutures.poll();
        }
    }

    public void shutdownFutures(long timeout, TimeUnit unit) {
        Stream<CompletableFuture<?>> stream = StreamSupport.stream(lastFutures.spliterator(), false);
        CompletableFuture<Void> future = CompletableFuture.allOf(stream.toArray(CompletableFuture[]::new));
        try {
            future.get(timeout, unit);
        } catch (Exception e) {
            // skip
        }
        lastFutures.forEach(f -> f.completeExceptionally(new RedissonShutdownException("Redisson is shutdown")));
        lastFutures.clear();
    }

    public void close() {
        shutdownLatch.set(true);
    }

    private volatile String lastClusterNodes;

    public void setLastClusterNodes(String lastClusterNodes) {
        this.lastClusterNodes = lastClusterNodes;
    }

    public <T> CompletableFuture<T> createNodeNotFoundFuture(String channelName, int slot) {
        RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for name: " + channelName + " slot: " + slot
                + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. " +
                "Try to increase 'retryDelay' and/or 'retryAttempts' settings. Last cluster nodes topology: " + lastClusterNodes);
        CompletableFuture<T> promise = new CompletableFuture<>();
        promise.completeExceptionally(ex);
        return promise;
    }

    public RedisNodeNotFoundException createNodeNotFoundException(NodeSource source) {
        RedisNodeNotFoundException ex;
        if (cfg.isClusterConfig()
                && source.getSlot() != null
                    && source.getAddr() == null
                        && source.getRedisClient() == null) {
            ex = new RedisNodeNotFoundException("Node for slot: " + source.getSlot() + " hasn't been discovered yet. Increase 'retryAttempts' setting. Last cluster nodes topology: " + lastClusterNodes);
        } else {
            ex = new RedisNodeNotFoundException("Node: " + source + " hasn't been discovered yet. Increase 'retryAttempts' setting. Last cluster nodes topology: " + lastClusterNodes);
        }
        return ex;
    }

    public MasterSlaveServersConfig getConfig() {
        return config;
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

    public CompletableFuture<InetSocketAddress> resolve(RedisURI address) {
        if (address.isIP()) {
            try {
                InetAddress ip = InetAddress.getByName(address.getHost());
                InetSocketAddress addr = new InetSocketAddress(InetAddress.getByAddress(address.getHost(), ip.getAddress()), address.getPort());
                return CompletableFuture.completedFuture(addr);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(e);
            }
        }

        CompletableFuture<InetSocketAddress> result = new CompletableFuture<>();
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
            // apply natMapper
            RedisURI uri = toURI(address.getScheme(), s.getAddress().getHostAddress(), "" + address.getPort());
            if (!uri.getHost().equals(s.getAddress().getHostAddress())) {
                InetAddress ip = InetAddress.getByName(uri.getHost());
                InetSocketAddress mappedAddr = new InetSocketAddress(InetAddress.getByAddress(s.getAddress().getHostAddress(), ip.getAddress()), uri.getPort());
                result.complete(mappedAddr);
                return;
            }
            result.complete(s);
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
        try {
            return natMapper.map(uri);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return uri;
        }
    }

    public void setNatMapper(NatMapper natMapper) {
        this.natMapper = natMapper;
    }

    public NatMapper getNatMapper() {
        return natMapper;
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
                byte[] s = mdigest.digest(script.getBytes(StandardCharsets.UTF_8));
                return ByteBufUtil.hexDump(s);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public <T> RFuture<T> execute(Supplier<CompletionStage<T>> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        execute(attempts, result, supplier);
        return new CompletableFutureWrapper<>(result);
    }

    private <T> void execute(AtomicInteger attempts, CompletableFuture<T> result, Supplier<CompletionStage<T>> supplier) {
        CompletionStage<T> future = supplier.get();
        future.whenComplete((r, e) -> {
            if (e != null) {
                if (e.getCause() instanceof NoSyncedSlavesException) {
                    if (attempts.get() >= config.getRetryAttempts()) {
                        result.completeExceptionally(e);
                        return;
                    }

                    attempts.incrementAndGet();

                    Duration timeout = config.getRetryDelay().calcDelay(attempts.get());
                    newTimeout(t -> execute(attempts, result, supplier),
                                                timeout.toMillis(), TimeUnit.MILLISECONDS);
                    return;
                }

                result.completeExceptionally(e);
                return;
            }

            result.complete(r);
        });
    }

    public <V> void transfer(CompletionStage<V> source, CompletableFuture<V> dest) {
        source.whenComplete((res, e) -> {
            if (e != null) {
                dest.completeExceptionally(e);
                return;
            }

            dest.complete(res);
        });
    }

    private final Random random = RandomXoshiro256PlusPlus.create();

    public Random getRandom() {
        return random;
    }

    public Long generateValue() {
        return random.nextLong();
    }

    public String generateId() {
        return ByteBufUtil.hexDump(generateIdArray());
    }

    public byte[] generateIdArray() {
        return generateIdArray(16);
    }
    public byte[] generateIdArray(int size) {
        byte[] id = new byte[size];

        random.nextBytes(id);
        return id;
    }

    private final AtomicBoolean liveObjectLatch = new AtomicBoolean();

    public AtomicBoolean getLiveObjectLatch() {
        return liveObjectLatch;
    }

    public boolean isResp3() {
        return cfg.getProtocol() == Protocol.RESP3;
    }

    private static final Map<RedisCommand<?>, RedisCommand<?>> RESP3MAPPING = new HashMap<>();

    static {
        RESP3MAPPING.put(RedisCommands.XREADGROUP_BLOCKING, RedisCommands.XREADGROUP_BLOCKING_V2);
        RESP3MAPPING.put(RedisCommands.XREADGROUP, RedisCommands.XREADGROUP_V2);
        RESP3MAPPING.put(RedisCommands.XREADGROUP_BLOCKING_SINGLE, RedisCommands.XREADGROUP_BLOCKING_SINGLE_V2);
        RESP3MAPPING.put(RedisCommands.XREADGROUP_SINGLE, RedisCommands.XREADGROUP_SINGLE_V2);
        RESP3MAPPING.put(RedisCommands.XREAD_BLOCKING_SINGLE, RedisCommands.XREAD_BLOCKING_SINGLE_V2);
        RESP3MAPPING.put(RedisCommands.XREAD_SINGLE, RedisCommands.XREAD_SINGLE_V2);
        RESP3MAPPING.put(RedisCommands.XREAD_BLOCKING, RedisCommands.XREAD_BLOCKING_V2);
        RESP3MAPPING.put(RedisCommands.XREAD, RedisCommands.XREAD_V2);
        RESP3MAPPING.put(RedisCommands.HRANDFIELD, RedisCommands.HRANDFIELD_V2);

        RESP3MAPPING.put(RedisCommands.ZRANGE_SINGLE_ENTRY, RedisCommands.ZRANGE_SINGLE_ENTRY_V2);
        RESP3MAPPING.put(RedisCommands.ZRANGE_ENTRY, RedisCommands.ZRANGE_ENTRY_V2);
        RESP3MAPPING.put(RedisCommands.ZREVRANGE_ENTRY, RedisCommands.ZREVRANGE_ENTRY_V2);
        RESP3MAPPING.put(RedisCommands.ZRANGEBYSCORE_ENTRY, RedisCommands.ZRANGEBYSCORE_ENTRY_V2);
        RESP3MAPPING.put(RedisCommands.ZREVRANGEBYSCORE_ENTRY, RedisCommands.ZREVRANGEBYSCORE_ENTRY_V2);
        RESP3MAPPING.put(RedisCommands.ZINITER_ENTRY, RedisCommands.ZINITER_ENTRY_V2);
        RESP3MAPPING.put(RedisCommands.ZUNION_ENTRY, RedisCommands.ZUNION_ENTRY_V2);
        RESP3MAPPING.put(RedisCommands.ZDIFF_ENTRY, RedisCommands.ZDIFF_ENTRY_V2);
    }

    public <R> RedisCommand<R> resp3(RedisCommand<R> command) {
        if (isResp3()) {
            return (RedisCommand<R>) RESP3MAPPING.getOrDefault(command, command);
        }
        return command;
    }

    public Map<String, ResponseEntry> getResponses() {
        return responses;
    }

    public QueueTransferService getQueueTransferService() {
        return queueTransferService;
    }

    public Codec getCodec(Codec codec) {
        if (codec == null) {
            return cfg.getCodec();
        }
        return codec;
    }

    private final Map<String, AdderEntry> addersUsage = new ConcurrentHashMap<>();

    public Map<String, AdderEntry> getAddersUsage() {
        return addersUsage;
    }

    private final Map<String, AtomicInteger> addersCounter = new ConcurrentHashMap<>();

    public Map<String, AtomicInteger> getAddersCounter() {
        return addersCounter;
    }

    private final MapResolver mapResolver = new MapResolver(this);

    public MapResolver getLiveObjectMapResolver() {
        return mapResolver;
    }

    public static final RLock DUMMY_LOCK = (RLock) Proxy.newProxyInstance(ServiceManager.class.getClassLoader(), new Class[] {RLock.class}, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().endsWith("lockAsync")) {
                return new CompletableFutureWrapper<>((Void) null);
            }
            return null;
        }
    });

    public void register(LockRenewalScheduler renewalScheduler) {
        this.renewalScheduler = renewalScheduler;
    }

    public LockRenewalScheduler getRenewalScheduler() {
        return renewalScheduler;
    }
}
