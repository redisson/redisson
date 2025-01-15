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
package org.redisson;

import org.redisson.api.ExecutorOptions;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.MapCacheOptions;
import org.redisson.api.MapOptions;
import org.redisson.api.*;
import org.redisson.api.options.*;
import org.redisson.api.redisnode.*;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.config.Config;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ServiceManager;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.redisnode.RedissonClusterNodes;
import org.redisson.redisnode.RedissonMasterSlaveNodes;
import org.redisson.redisnode.RedissonSentinelMasterSlaveNodes;
import org.redisson.redisnode.RedissonSingleNode;
import org.redisson.renewal.LockRenewalScheduler;
import org.redisson.transaction.RedissonTransaction;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public final class Redisson implements RedissonClient {

    private final EvictionScheduler evictionScheduler;
    private final WriteBehindService writeBehindService;
    private final ConnectionManager connectionManager;
    private final CommandAsyncExecutor commandExecutor;

    private final ConcurrentMap<Class<?>, Class<?>> liveObjectClassCache = new ConcurrentHashMap<>();
    private final Config config;

    Redisson(Config config) {
        this.config = config;
        Config configCopy = new Config(config);

        connectionManager = ConnectionManager.create(configCopy);
        RedissonObjectBuilder objectBuilder = null;
        if (config.isReferenceEnabled()) {
            objectBuilder = new RedissonObjectBuilder(this);
        }
        commandExecutor = connectionManager.createCommandExecutor(objectBuilder, RedissonObjectBuilder.ReferenceType.DEFAULT);
        evictionScheduler = new EvictionScheduler(commandExecutor);
        writeBehindService = new WriteBehindService(commandExecutor);

        connectionManager.getServiceManager().register(new LockRenewalScheduler(commandExecutor));
    }

    public EvictionScheduler getEvictionScheduler() {
        return evictionScheduler;
    }

    public CommandAsyncExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public ServiceManager getServiceManager() {
        return connectionManager.getServiceManager();
    }

    /**
     * Create sync/async Redisson instance with default config
     *
     * @return Redisson instance
     */
    public static RedissonClient create() {
        Config config = new Config();
        config.useSingleServer()
        .setAddress("redis://127.0.0.1:6379");
        return create(config);
    }

    /**
     * Create sync/async Redisson instance with provided config
     *
     * @param config for Redisson
     * @return Redisson instance
     */
    public static RedissonClient create(Config config) {
        return new Redisson(config);
    }

    /*
     * Use Redisson.create().rxJava() method instead
     */
    @Deprecated
    public static RedissonRxClient createRx() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        return createRx(config);
    }

    /*
     * Use Redisson.create(config).rxJava() method instead
     */
    @Deprecated
    public static RedissonRxClient createRx(Config config) {
        RedissonClient redisson = create(config);
        return redisson.rxJava();
    }

    @Override
    public RedissonRxClient rxJava() {
        return new RedissonRx(connectionManager, evictionScheduler, writeBehindService);
    }

    /*
     * Use Redisson.create().reactive() method instead
     */
    @Deprecated
    public static RedissonReactiveClient createReactive() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        return createReactive(config);
    }

    /*
     * Use Redisson.create(config).reactive() method instead
     */
    @Deprecated
    public static RedissonReactiveClient createReactive(Config config) {
        RedissonClient redisson = create(config);
        return redisson.reactive();
    }

    @Override
    public RedissonReactiveClient reactive() {
        return new RedissonReactive(connectionManager, evictionScheduler, writeBehindService);
    }

    @Override
    public <V, L> RTimeSeries<V, L> getTimeSeries(String name) {
        return new RedissonTimeSeries<>(evictionScheduler, commandExecutor, name);
    }

    @Override
    public <V, L> RTimeSeries<V, L> getTimeSeries(String name, Codec codec) {
        return new RedissonTimeSeries<>(codec, evictionScheduler, commandExecutor, name);
    }

    @Override
    public <V, L> RTimeSeries<V, L> getTimeSeries(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonTimeSeries<>(params.getCodec(), evictionScheduler,
                commandExecutor.copy(params),
                params.getName());
    }

    @Override
    public <K, V> RStream<K, V> getStream(String name) {
        return new RedissonStream<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RStream<K, V> getStream(String name, Codec codec) {
        return new RedissonStream<>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RStream<K, V> getStream(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonStream<>(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public RSearch getSearch() {
        return new RedissonSearch(null, commandExecutor);
    }

    @Override
    public RSearch getSearch(Codec codec) {
        return new RedissonSearch(codec, commandExecutor);
    }

    @Override
    public RSearch getSearch(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        return new RedissonSearch(params.getCodec(), commandExecutor.copy(params));
    }

    @Override
    public RBinaryStream getBinaryStream(String name) {
        return new RedissonBinaryStream(commandExecutor, name);
    }

    @Override
    public RBinaryStream getBinaryStream(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonBinaryStream(commandExecutor.copy(params), params.getName());
    }

    @Override
    public <V> RGeo<V> getGeo(String name) {
        return new RedissonGeo<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RGeo<V> getGeo(String name, Codec codec) {
        return new RedissonGeo<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RGeo<V> getGeo(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonGeo<>(params.getCodec(), commandExecutor.copy(params),
                params.getName(), this);
    }

    @Override
    public <V> RBucket<V> getBucket(String name) {
        return new RedissonBucket<V>(commandExecutor, name);
    }

    @Override
    public RRateLimiter getRateLimiter(String name) {
        return new RedissonRateLimiter(commandExecutor, name);
    }

    @Override
    public RRateLimiter getRateLimiter(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonRateLimiter(commandExecutor.copy(params), params.getName());
    }

    @Override
    public <V> RBucket<V> getBucket(String name, Codec codec) {
        return new RedissonBucket<>(codec, commandExecutor, name);
    }

    @Override
    public <V> RBucket<V> getBucket(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonBucket<>(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public RBuckets getBuckets() {
        return new RedissonBuckets(commandExecutor);
    }

    @Override
    public RBuckets getBuckets(Codec codec) {
        return new RedissonBuckets(codec, commandExecutor);
    }

    @Override
    public RBuckets getBuckets(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        return new RedissonBuckets(params.getCodec(), commandExecutor.copy(params));
    }

    @Override
    public <V> RJsonBucket<V> getJsonBucket(String name, JsonCodec codec) {
        return new RedissonJsonBucket<>(codec, commandExecutor, name);
    }

    @Override
    public <V> RJsonBucket<V> getJsonBucket(JsonBucketOptions<V> options) {
        JsonBucketParams<V> params = (JsonBucketParams) options;
        return new RedissonJsonBucket<>(params.getCodec(), commandExecutor.copy(params), params.getName());
    }
    
    @Override
    public RJsonBuckets getJsonBuckets(JsonCodec codec) {
        return new RedissonJsonBuckets(codec, commandExecutor);
    }
    
    @Override
    public <V> RHyperLogLog<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLog<V>(commandExecutor, name);
    }

    @Override
    public <V> RHyperLogLog<V> getHyperLogLog(String name, Codec codec) {
        return new RedissonHyperLogLog<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RHyperLogLog<V> getHyperLogLog(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonHyperLogLog<V>(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public <V> RList<V> getList(String name) {
        return new RedissonList<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RList<V> getList(String name, Codec codec) {
        return new RedissonList<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RList<V> getList(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonList<V>(params.getCodec(), commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <K, V> RListMultimap<K, V> getListMultimap(String name) {
        return new RedissonListMultimap<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimap<K, V> getListMultimap(String name, Codec codec) {
        return new RedissonListMultimap<K, V>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimap<K, V> getListMultimap(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonListMultimap<>(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public <K, V> RLocalCachedMapCache<K, V> getLocalCachedMapCache(String name, LocalCachedMapCacheOptions<K, V> options) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Visit https://redisson.pro");
    }

    @Override
    public <K, V> RLocalCachedMapCache<K, V> getLocalCachedMapCache(String name, Codec codec, LocalCachedMapCacheOptions<K, V> options) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Visit https://redisson.pro");
    }

    @Override
    public <K, V> RLocalCachedMap<K, V> getLocalCachedMap(String name, LocalCachedMapOptions<K, V> options) {
        return getLocalCachedMap(name, null, options);
    }

    @Override
    public <K, V> RLocalCachedMap<K, V> getLocalCachedMap(String name, Codec codec, LocalCachedMapOptions<K, V> options) {
        return new RedissonLocalCachedMap<K, V>(codec, commandExecutor, name,
                options, evictionScheduler, this, writeBehindService);
    }

    @Override
    public <K, V> RLocalCachedMap<K, V> getLocalCachedMap(org.redisson.api.options.LocalCachedMapOptions<K, V> options) {
        LocalCachedMapParams<K, V> params = (LocalCachedMapParams) options;

        LocalCachedMapOptions<K, V> ops = LocalCachedMapOptions.<K, V>defaults()
                .cacheProvider(LocalCachedMapOptions.CacheProvider.valueOf(params.getCacheProvider().toString()))
                .cacheSize(params.getCacheSize())
                .storeMode(LocalCachedMapOptions.StoreMode.valueOf(params.getStoreMode().toString()))
                .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.valueOf(params.getEvictionPolicy().toString()))
                .maxIdle(params.getMaxIdleInMillis())
                .loader(params.getLoader())
                .loaderAsync(params.getLoaderAsync())
                .reconnectionStrategy(LocalCachedMapOptions.ReconnectionStrategy.valueOf(params.getReconnectionStrategy().toString()))
                .storeCacheMiss(params.isStoreCacheMiss())
                .timeToLive(params.getTimeToLiveInMillis())
                .syncStrategy(LocalCachedMapOptions.SyncStrategy.valueOf(params.getSyncStrategy().toString()))
                .useObjectAsCacheKey(params.isUseObjectAsCacheKey())
                .useTopicPattern(params.isUseTopicPattern())
                .expirationEventPolicy(LocalCachedMapOptions.ExpirationEventPolicy.valueOf(params.getExpirationEventPolicy().toString()))
                .writer(params.getWriter())
                .writerAsync(params.getWriterAsync())
                .writeBehindDelay(params.getWriteBehindDelay())
                .writeBehindBatchSize(params.getWriteBehindBatchSize())
                .writerRetryInterval(Duration.ofMillis(params.getWriteRetryInterval()));

        if (params.getWriteMode() != null) {
            ops.writeMode(MapOptions.WriteMode.valueOf(params.getWriteMode().toString()));
        }
        if (params.getWriteRetryAttempts() > 0) {
            ops.writerRetryAttempts(params.getWriteRetryAttempts());
        }

        return new RedissonLocalCachedMap<>(params.getCodec(), commandExecutor.copy(params), params.getName(),
                ops, evictionScheduler, this, writeBehindService);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name) {
        return new RedissonMap<K, V>(commandExecutor, name, this, null, null);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name, MapOptions<K, V> options) {
        return new RedissonMap<K, V>(commandExecutor, name, this, options, writeBehindService);
    }

    @Override
    public <K, V> RMap<K, V> getMap(org.redisson.api.options.MapOptions<K, V> options) {
        MapParams<K, V> params = (MapParams<K, V>) options;
        MapOptions<K, V> ops = MapOptions.<K, V>defaults()
                .loader(params.getLoader())
                .loaderAsync(params.getLoaderAsync())
                .writer(params.getWriter())
                .writerAsync(params.getWriterAsync())
                .writeBehindDelay(params.getWriteBehindDelay())
                .writeBehindBatchSize(params.getWriteBehindBatchSize())
                .writerRetryInterval(Duration.ofMillis(params.getWriteRetryInterval()));

        if (params.getWriteMode() != null) {
            ops.writeMode(MapOptions.WriteMode.valueOf(params.getWriteMode().toString()));
        }
        if (params.getWriteRetryAttempts() > 0) {
            ops.writerRetryAttempts(params.getWriteRetryAttempts());
        }

        return new RedissonMap<>(params.getCodec(), commandExecutor.copy(params), params.getName(),
                this, ops, writeBehindService);
    }

    @Override
    public <K, V> RMapCacheNative<K, V> getMapCacheNative(String name) {
        return new RedissonMapCacheNative<>(commandExecutor, name, this, null, null);
    }

    @Override
    public <K, V> RMapCacheNative<K, V> getMapCacheNative(String name, Codec codec) {
        return new RedissonMapCacheNative<>(codec, commandExecutor, name, this, null, null);
    }

    @Override
    public <K, V> RMapCacheNative<K, V> getMapCacheNative(org.redisson.api.options.MapOptions<K, V> options) {
        MapParams<K, V> params = (MapParams<K, V>) options;
        MapOptions<K, V> ops = MapOptions.<K, V>defaults()
                .loader(params.getLoader())
                .loaderAsync(params.getLoaderAsync())
                .writer(params.getWriter())
                .writerAsync(params.getWriterAsync())
                .writeBehindDelay(params.getWriteBehindDelay())
                .writeBehindBatchSize(params.getWriteBehindBatchSize())
                .writerRetryInterval(Duration.ofMillis(params.getWriteRetryInterval()));

        if (params.getWriteMode() != null) {
            ops.writeMode(MapOptions.WriteMode.valueOf(params.getWriteMode().toString()));
        }
        if (params.getWriteRetryAttempts() > 0) {
            ops.writerRetryAttempts(params.getWriteRetryAttempts());
        }

        return new RedissonMapCacheNative<>(params.getCodec(), commandExecutor.copy(params), params.getName(),
                this, ops, writeBehindService);
    }

    @Override
    public <K, V> RSetMultimap<K, V> getSetMultimap(String name) {
        return new RedissonSetMultimap<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimapCache<K, V> getSetMultimapCache(String name) {
        return new RedissonSetMultimapCache<K, V>(evictionScheduler, commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimapCache<K, V> getSetMultimapCache(String name, Codec codec) {
        return new RedissonSetMultimapCache<K, V>(evictionScheduler, codec, commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimapCache<K, V> getSetMultimapCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonSetMultimapCache<K, V>(evictionScheduler, params.getCodec(),
                commandExecutor.copy(params), params.getName());
    }

    @Override
    public <K, V> RSetMultimapCacheNative<K, V> getSetMultimapCacheNative(String name) {
        return new RedissonSetMultimapCacheNative<>(commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimapCacheNative<K, V> getSetMultimapCacheNative(String name, Codec codec) {
        return new RedissonSetMultimapCacheNative<>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimapCacheNative<K, V> getSetMultimapCacheNative(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonSetMultimapCacheNative<>(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public <K, V> RListMultimapCache<K, V> getListMultimapCache(String name) {
        return new RedissonListMultimapCache<K, V>(evictionScheduler, commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimapCache<K, V> getListMultimapCache(String name, Codec codec) {
        return new RedissonListMultimapCache<K, V>(evictionScheduler, codec, commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimapCache<K, V> getListMultimapCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonListMultimapCache<K, V>(evictionScheduler, params.getCodec(),
                commandExecutor.copy(params), params.getName());
    }

    @Override
    public <K, V> RListMultimapCacheNative<K, V> getListMultimapCacheNative(String name) {
        return new RedissonListMultimapCacheNative<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimapCacheNative<K, V> getListMultimapCacheNative(String name, Codec codec) {
        return new RedissonListMultimapCacheNative<>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimapCacheNative<K, V> getListMultimapCacheNative(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonListMultimapCacheNative<>(params.getCodec(),
                commandExecutor.copy(params), params.getName());
    }

    @Override
    public <K, V> RSetMultimap<K, V> getSetMultimap(String name, Codec codec) {
        return new RedissonSetMultimap<K, V>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimap<K, V> getSetMultimap(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonSetMultimap<>(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public <V> RSetCache<V> getSetCache(String name) {
        return new RedissonSetCache<V>(evictionScheduler, commandExecutor, name, this);
    }

    @Override
    public <V> RSetCache<V> getSetCache(String name, Codec codec) {
        return new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name, this);
    }

    @Override
    public <V> RSetCache<V> getSetCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonSetCache<V>(params.getCodec(), evictionScheduler,
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name) {
        return new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, this, null, null);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name, MapCacheOptions<K, V> options) {
        return new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, this, options, writeBehindService);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name, Codec codec) {
        return new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, this, null, null);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name, Codec codec, MapCacheOptions<K, V> options) {
        return new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, this, options, writeBehindService);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(org.redisson.api.options.MapCacheOptions<K, V> options) {
        MapCacheParams<K, V> params = (MapCacheParams<K, V>) options;
        MapCacheOptions<K, V> ops = createOptions(params);
        return new RedissonMapCache<>(params.getCodec(), evictionScheduler,
                commandExecutor.copy(params), params.getName(), this, ops, writeBehindService);
    }

    private static <K, V> MapCacheOptions<K, V> createOptions(MapCacheParams<K, V> params) {
        MapCacheOptions<K, V> ops = MapCacheOptions.<K, V>defaults()
                .loader(params.getLoader())
                .loaderAsync(params.getLoaderAsync())
                .writer(params.getWriter())
                .writerAsync(params.getWriterAsync())
                .writeBehindDelay(params.getWriteBehindDelay())
                .writeBehindBatchSize(params.getWriteBehindBatchSize())
                .writerRetryInterval(Duration.ofMillis(params.getWriteRetryInterval()));

        if (params.getWriteMode() != null) {
            ops.writeMode(MapOptions.WriteMode.valueOf(params.getWriteMode().toString()));
        }
        if (params.getWriteRetryAttempts() > 0) {
            ops.writerRetryAttempts(params.getWriteRetryAttempts());
        }

        if (params.isRemoveEmptyEvictionTask()) {
            ops.removeEmptyEvictionTask();
        }
        return ops;
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return new RedissonMap<K, V>(codec, commandExecutor, name, this, null, null);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        return new RedissonMap<K, V>(codec, commandExecutor, name, this, options, writeBehindService);
    }

    @Override
    public RLock getLock(String name) {
        return new RedissonLock(commandExecutor, name);
    }

    @Override
    public RLock getLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonLock(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RLock getSpinLock(String name) {
        return getSpinLock(name, LockOptions.defaults());
    }

    @Override
    public RLock getSpinLock(String name, LockOptions.BackOff backOff) {
        return new RedissonSpinLock(commandExecutor, name, backOff);
    }

    @Override
    public RFencedLock getFencedLock(String name) {
        return new RedissonFencedLock(commandExecutor, name);
    }

    @Override
    public RFencedLock getFencedLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonFencedLock(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RLock getMultiLock(RLock... locks) {
        return new RedissonMultiLock(locks);
    }

    @Override
    public RLock getMultiLock(String group, Collection<Object> values) {
        return new RedissonFasterMultiLock(commandExecutor, group, values);
    }

    @Override
    public RLock getRedLock(RLock... locks) {
        return new RedissonRedLock(locks);
    }

    @Override
    public RLock getFairLock(String name) {
        return new RedissonFairLock(commandExecutor, name);
    }

    @Override
    public RLock getFairLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonFairLock(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RReadWriteLock getReadWriteLock(String name) {
        return new RedissonReadWriteLock(commandExecutor, name);
    }

    @Override
    public RReadWriteLock getReadWriteLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonReadWriteLock(commandExecutor.copy(params), params.getName());
    }

    @Override
    public <V> RSet<V> getSet(String name) {
        return new RedissonSet<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RSet<V> getSet(String name, Codec codec) {
        return new RedissonSet<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RSet<V> getSet(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonSet<V>(params.getCodec(), commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public RFunction getFunction() {
        return new RedissonFuction(commandExecutor);
    }

    @Override
    public RFunction getFunction(Codec codec) {
        return new RedissonFuction(commandExecutor, codec);
    }

    @Override
    public RFunction getFunction(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        return new RedissonFuction(commandExecutor.copy(params), params.getCodec());
    }

    @Override
    public RScript getScript() {
        return new RedissonScript(commandExecutor);
    }
    
    @Override
    public RScript getScript(Codec codec) {
        return new RedissonScript(commandExecutor, codec);
    }

    @Override
    public RScript getScript(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        return new RedissonScript(commandExecutor.copy(params), params.getCodec());
    }

    @Override
    public RScheduledExecutorService getExecutorService(String name) {
        return getExecutorService(name, connectionManager.getServiceManager().getCfg().getCodec());
    }

    @Override
    public RScheduledExecutorService getExecutorService(String name, ExecutorOptions options) {
        return getExecutorService(name, connectionManager.getServiceManager().getCfg().getCodec(), options);
    }

    @Override
    public RScheduledExecutorService getExecutorService(String name, Codec codec) {
        return getExecutorService(name, codec, ExecutorOptions.defaults());
    }

    @Override
    public RScheduledExecutorService getExecutorService(String name, Codec codec, ExecutorOptions options) {
        return new RedissonExecutorService(codec, commandExecutor, this, name, options);
    }

    @Override
    public RScheduledExecutorService getExecutorService(org.redisson.api.options.ExecutorOptions options) {
        ExecutorParams params = (ExecutorParams) options;
        ExecutorOptions ops = ExecutorOptions.defaults()
                                            .idGenerator(params.getIdGenerator())
                                            .taskRetryInterval(params.getTaskRetryInterval(), TimeUnit.MILLISECONDS);
        return new RedissonExecutorService(params.getCodec(),
                commandExecutor.copy(params), this, params.getName(), ops);
    }

    @Override
    public RRemoteService getRemoteService() {
        return getRemoteService("redisson_rs", connectionManager.getServiceManager().getCfg().getCodec());
    }

    @Override
    public RRemoteService getRemoteService(String name) {
        return getRemoteService(name, connectionManager.getServiceManager().getCfg().getCodec());
    }

    @Override
    public RRemoteService getRemoteService(Codec codec) {
        return getRemoteService("redisson_rs", codec);
    }

    @Override
    public RRemoteService getRemoteService(String name, Codec codec) {
        String executorId = connectionManager.getServiceManager().getId();
        if (codec != connectionManager.getServiceManager().getCfg().getCodec()) {
            executorId = executorId + ":" + name;
        }
        return new RedissonRemoteService(codec, name, commandExecutor, executorId);
    }

    @Override
    public RRemoteService getRemoteService(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        String executorId = connectionManager.getServiceManager().getId();
        if (params.getCodec() != null
                && params.getCodec() != connectionManager.getServiceManager().getCfg().getCodec()) {
            executorId = executorId + ":" + params.getName();
        }
        return new RedissonRemoteService(params.getCodec(), params.getName(), commandExecutor.copy(params), executorId);
    }

    @Override
    public <V> RSortedSet<V> getSortedSet(String name) {
        return new RedissonSortedSet<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RSortedSet<V> getSortedSet(String name, Codec codec) {
        return new RedissonSortedSet<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RSortedSet<V> getSortedSet(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonSortedSet<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RScoredSortedSet<V> getScoredSortedSet(String name) {
        return new RedissonScoredSortedSet<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RScoredSortedSet<V> getScoredSortedSet(String name, Codec codec) {
        return new RedissonScoredSortedSet<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RScoredSortedSet<V> getScoredSortedSet(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonScoredSortedSet<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public RLexSortedSet getLexSortedSet(String name) {
        return new RedissonLexSortedSet(commandExecutor, name, this);
    }

    @Override
    public RLexSortedSet getLexSortedSet(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonLexSortedSet(commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public RShardedTopic getShardedTopic(String name) {
        return new RedissonShardedTopic(commandExecutor, name);
    }

    @Override
    public RShardedTopic getShardedTopic(String name, Codec codec) {
        return new RedissonShardedTopic(codec, commandExecutor, name);
    }

    @Override
    public RShardedTopic getShardedTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonShardedTopic(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public RTopic getTopic(String name) {
        return new RedissonTopic(commandExecutor, name);
    }

    @Override
    public RTopic getTopic(String name, Codec codec) {
        return new RedissonTopic(codec, commandExecutor, name);
    }

    @Override
    public RTopic getTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonTopic(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public RReliableTopic getReliableTopic(String name) {
        return new RedissonReliableTopic(commandExecutor, name, null);
    }

    @Override
    public RReliableTopic getReliableTopic(String name, Codec codec) {
        return new RedissonReliableTopic(codec, commandExecutor, name, null);
    }

    @Override
    public RReliableTopic getReliableTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonReliableTopic(params.getCodec(),
                commandExecutor.copy(params), params.getName(), null);
    }

    @Override
    public RPatternTopic getPatternTopic(String pattern) {
        return new RedissonPatternTopic(commandExecutor, pattern);
    }

    @Override
    public RPatternTopic getPatternTopic(String pattern, Codec codec) {
        return new RedissonPatternTopic(codec, commandExecutor, pattern);
    }

    @Override
    public RPatternTopic getPatternTopic(PatternTopicOptions options) {
        PatternTopicParams params = (PatternTopicParams) options;
        return new RedissonPatternTopic(params.getCodec(), commandExecutor.copy(params), params.getPattern());
    }

    @Override
    public <V> RDelayedQueue<V> getDelayedQueue(RQueue<V> destinationQueue) {
        if (destinationQueue == null) {
            throw new NullPointerException();
        }
        return new RedissonDelayedQueue<V>(destinationQueue.getCodec(), commandExecutor, destinationQueue.getName());
    }

    @Override
    public <V> RQueue<V> getQueue(String name) {
        return new RedissonQueue<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RQueue<V> getQueue(String name, Codec codec) {
        return new RedissonQueue<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RQueue<V> getQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonQueue<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RTransferQueue<V> getTransferQueue(String name) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        return new RedissonTransferQueue<V>(commandExecutor, name, service);
    }

    @Override
    public <V> RTransferQueue<V> getTransferQueue(String name, Codec codec) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        return new RedissonTransferQueue<V>(codec, commandExecutor, name, service);
    }

    @Override
    public <V> RTransferQueue<V> getTransferQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        String remoteName = RedissonObject.suffixName(params.getName(), "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        return new RedissonTransferQueue<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), service);
    }

    @Override
    public <V> RRingBuffer<V> getRingBuffer(String name) {
        return new RedissonRingBuffer<V>(commandExecutor, name, this);
    }
    
    @Override
    public <V> RRingBuffer<V> getRingBuffer(String name, Codec codec) {
        return new RedissonRingBuffer<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RRingBuffer<V> getRingBuffer(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonRingBuffer<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueue<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueue<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonBlockingQueue<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RBoundedBlockingQueue<V> getBoundedBlockingQueue(String name) {
        return new RedissonBoundedBlockingQueue<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RBoundedBlockingQueue<V> getBoundedBlockingQueue(String name, Codec codec) {
        return new RedissonBoundedBlockingQueue<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RBoundedBlockingQueue<V> getBoundedBlockingQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonBoundedBlockingQueue<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RDeque<V> getDeque(String name) {
        return new RedissonDeque<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RDeque<V> getDeque(String name, Codec codec) {
        return new RedissonDeque<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RDeque<V> getDeque(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonDeque<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RBlockingDeque<V> getBlockingDeque(String name) {
        return new RedissonBlockingDeque<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RBlockingDeque<V> getBlockingDeque(String name, Codec codec) {
        return new RedissonBlockingDeque<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RBlockingDeque<V> getBlockingDeque(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonBlockingDeque<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public RAtomicLong getAtomicLong(String name) {
        return new RedissonAtomicLong(commandExecutor, name);
    }

    @Override
    public RAtomicLong getAtomicLong(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonAtomicLong(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RLongAdder getLongAdder(String name) {
        return new RedissonLongAdder(commandExecutor, name, this);
    }

    @Override
    public RLongAdder getLongAdder(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonLongAdder(commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public RDoubleAdder getDoubleAdder(String name) {
        return new RedissonDoubleAdder(commandExecutor, name, this);
    }

    @Override
    public RDoubleAdder getDoubleAdder(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonDoubleAdder(commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public RAtomicDouble getAtomicDouble(String name) {
        return new RedissonAtomicDouble(commandExecutor, name);
    }

    @Override
    public RAtomicDouble getAtomicDouble(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonAtomicDouble(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RCountDownLatch getCountDownLatch(String name) {
        return new RedissonCountDownLatch(commandExecutor, name);
    }

    @Override
    public RCountDownLatch getCountDownLatch(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonCountDownLatch(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RBitSet getBitSet(String name) {
        return new RedissonBitSet(commandExecutor, name);
    }

    @Override
    public RBitSet getBitSet(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonBitSet(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RSemaphore getSemaphore(String name) {
        return new RedissonSemaphore(commandExecutor, name);
    }

    @Override
    public RSemaphore getSemaphore(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonSemaphore(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(String name) {
        return new RedissonPermitExpirableSemaphore(commandExecutor, name);
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonPermitExpirableSemaphore(commandExecutor.copy(params), params.getName());
    }

    @Override
    public <V> RBloomFilter<V> getBloomFilter(String name) {
        return new RedissonBloomFilter<V>(commandExecutor, name);
    }

    @Override
    public <V> RBloomFilter<V> getBloomFilter(String name, Codec codec) {
        return new RedissonBloomFilter<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RBloomFilter<V> getBloomFilter(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonBloomFilter<V>(params.getCodec(), commandExecutor.copy(params), params.getName());
    }

    @Override
    public RIdGenerator getIdGenerator(String name) {
        return new RedissonIdGenerator(commandExecutor, name);
    }

    @Override
    public RIdGenerator getIdGenerator(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return new RedissonIdGenerator(commandExecutor.copy(params), params.getName());
    }

    @Override
    public RKeys getKeys() {
        return new RedissonKeys(commandExecutor);
    }

    @Override
    public RKeys getKeys(KeysOptions options) {
        KeysParams params = (KeysParams) options;
        return new RedissonKeys(commandExecutor.copy(params));
    }

    @Override
    public RTransaction createTransaction(TransactionOptions options) {
        return new RedissonTransaction(commandExecutor, options);
    }

    @Override
    public RBatch createBatch(BatchOptions options) {
        return new RedissonBatch(evictionScheduler, commandExecutor, options);
    }

    @Override
    public RBatch createBatch() {
        return createBatch(BatchOptions.defaults());
    }

    @Override
    public RLiveObjectService getLiveObjectService() {
        return new RedissonLiveObjectService(liveObjectClassCache, commandExecutor);
    }

    @Override
    public RLiveObjectService getLiveObjectService(LiveObjectOptions options) {
        LiveObjectParams params = (LiveObjectParams) options;
        return new RedissonLiveObjectService(liveObjectClassCache, commandExecutor.copy(params));
    }

    @Override
    public RClientSideCaching getClientSideCaching(ClientSideCachingOptions options) {
        if (!getServiceManager().isResp3()) {
            throw new IllegalStateException("'protocol' config setting should be set to RESP3 value. "
                    + System.lineSeparator() + System.lineSeparator() +
                    "NOTE: client side caching feature invalidates whole Map per entry change which is ineffective. " +
                    "Use local cached https://redisson.org/docs/data-and-services/collections/#eviction-local-cache-and-data-partitioning or https://redisson.org/docs/data-and-services/collections/#local-cache instead.");
        }
        return new RedissonClientSideCaching(commandExecutor, options);
    }

    @Override
    public void shutdown() {
        writeBehindService.stop();
        connectionManager.shutdown();
    }


    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        writeBehindService.stop();
        connectionManager.shutdown(quietPeriod, timeout, unit);
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public <T extends BaseRedisNodes> T getRedisNodes(org.redisson.api.redisnode.RedisNodes<T> nodes) {
        if (nodes.getClazz() == RedisSingle.class) {
            if (config.isSentinelConfig() || config.isClusterConfig()) {
                throw new IllegalArgumentException("Can't be used in non Redis single configuration");
            }
            return (T) new RedissonSingleNode(connectionManager, commandExecutor);
        }
        if (nodes.getClazz() == RedisCluster.class) {
            if (!config.isClusterConfig()) {
                throw new IllegalArgumentException("Can't be used in non Redis Cluster configuration");
            }
            return (T) new RedissonClusterNodes(connectionManager, commandExecutor);
        }
        if (nodes.getClazz() == RedisSentinelMasterSlave.class) {
            if (!config.isSentinelConfig()) {
                throw new IllegalArgumentException("Can't be used in non Redis Sentinel configuration");
            }
            return (T) new RedissonSentinelMasterSlaveNodes(connectionManager, commandExecutor);
        }
        if (nodes.getClazz() == RedisMasterSlave.class) {
            if (config.isSentinelConfig() || config.isClusterConfig()) {
                throw new IllegalArgumentException("Can't be used in non Redis Master Slave configuration");
            }
            return (T) new RedissonMasterSlaveNodes(connectionManager, commandExecutor);
        }
        throw new IllegalArgumentException();
    }

    @Override
    public NodesGroup<Node> getNodesGroup() {
        return new RedisNodes<Node>(connectionManager, connectionManager.getServiceManager(), commandExecutor);
    }

    @Override
    public ClusterNodesGroup getClusterNodesGroup() {
        if (!config.isClusterConfig()) {
            throw new IllegalStateException("Redisson is not in cluster mode!");
        }
        return new RedisClusterNodes(connectionManager, connectionManager.getServiceManager(), commandExecutor);
    }

    @Override
    public boolean isShutdown() {
        return connectionManager.getServiceManager().isShutdown();
    }

    @Override
    public boolean isShuttingDown() {
        return connectionManager.getServiceManager().isShuttingDown();
    }

    @Override
    public <V> RPriorityQueue<V> getPriorityQueue(String name) {
        return new RedissonPriorityQueue<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RPriorityQueue<V> getPriorityQueue(String name, Codec codec) {
        return new RedissonPriorityQueue<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RPriorityQueue<V> getPriorityQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonPriorityQueue<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RPriorityBlockingQueue<V> getPriorityBlockingQueue(String name) {
        return new RedissonPriorityBlockingQueue<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RPriorityBlockingQueue<V> getPriorityBlockingQueue(String name, Codec codec) {
        return new RedissonPriorityBlockingQueue<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RPriorityBlockingQueue<V> getPriorityBlockingQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonPriorityBlockingQueue<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RPriorityBlockingDeque<V> getPriorityBlockingDeque(String name) {
        return new RedissonPriorityBlockingDeque<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RPriorityBlockingDeque<V> getPriorityBlockingDeque(String name, Codec codec) {
        return new RedissonPriorityBlockingDeque<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RPriorityBlockingDeque<V> getPriorityBlockingDeque(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonPriorityBlockingDeque<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public <V> RPriorityDeque<V> getPriorityDeque(String name) {
        return new RedissonPriorityDeque<V>(commandExecutor, name, this);
    }

    @Override
    public <V> RPriorityDeque<V> getPriorityDeque(String name, Codec codec) {
        return new RedissonPriorityDeque<V>(codec, commandExecutor, name, this);
    }

    @Override
    public <V> RPriorityDeque<V> getPriorityDeque(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        return new RedissonPriorityDeque<V>(params.getCodec(),
                commandExecutor.copy(params), params.getName(), this);
    }

    @Override
    public String getId() {
        return connectionManager.getServiceManager().getId();
    }

}
