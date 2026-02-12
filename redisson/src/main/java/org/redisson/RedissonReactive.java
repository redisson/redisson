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

import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.MapCacheOptions;
import org.redisson.api.MapOptions;
import org.redisson.api.*;
import org.redisson.api.options.*;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonCodec;
import org.redisson.config.Config;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.reactive.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonReactive implements RedissonReactiveClient {

    private final WriteBehindService writeBehindService;
    private final EvictionScheduler evictionScheduler;
    private final CommandReactiveExecutor commandExecutor;
    private final ConnectionManager connectionManager;

    RedissonReactive(ConnectionManager connectionManager, EvictionScheduler evictionScheduler,
                     WriteBehindService writeBehindService) {
        this.connectionManager = connectionManager;
        RedissonObjectBuilder objectBuilder = null;
        if (connectionManager.getServiceManager().getCfg().isReferenceEnabled()) {
            objectBuilder = new RedissonObjectBuilder(this);
        }
        commandExecutor = CommandReactiveExecutor.create(connectionManager, objectBuilder);
        this.evictionScheduler = evictionScheduler;
        this.writeBehindService = writeBehindService;
    }

    public CommandReactiveExecutor getCommandExecutor() {
        return commandExecutor;
    }
    
    @Override
    public <K, V> RStreamReactive<K, V> getStream(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(commandExecutor, name), RStreamReactive.class);
    }

    @Override
    public <K, V> RStreamReactive<K, V> getStream(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(codec, commandExecutor, name), RStreamReactive.class);
    }

    @Override
    public <K, V> RStreamReactive<K, V> getStream(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonStream<K, V>(params.getCodec(), ca, params.getName()), RStreamReactive.class);
    }

    @Override
    public RSearchReactive getSearch() {
        return getSearch((Codec) null);
    }

    @Override
    public RSearchReactive getSearch(Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSearch(codec, commandExecutor), RSearchReactive.class);
    }

    @Override
    public RSearchReactive getSearch(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSearch(params.getCodec(), ca), RSearchReactive.class);
    }

    @Override
    public <V> RGeoReactive<V> getGeo(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonGeo<V>(commandExecutor, name, null), 
                new RedissonScoredSortedSetReactive<V>(commandExecutor, name), RGeoReactive.class);
    }
    
    @Override
    public <V> RGeoReactive<V> getGeo(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonGeo<V>(codec, commandExecutor, name, null), 
                new RedissonScoredSortedSetReactive<V>(codec, commandExecutor, name), RGeoReactive.class);
    }

    @Override
    public <V> RGeoReactive<V> getGeo(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonGeo<V>(params.getCodec(), ca, params.getName(), null),
                new RedissonScoredSortedSetReactive<V>(params.getCodec(), ca, params.getName()), RGeoReactive.class);
    }

    @Override
    public RLockReactive getFairLock(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFairLock(commandExecutor, name), RLockReactive.class);
    }

    @Override
    public RLockReactive getFairLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFairLock(ca, params.getName()), RLockReactive.class);
    }

    @Override
    public RRateLimiterReactive getRateLimiter(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRateLimiter(commandExecutor, name), RRateLimiterReactive.class);
    }

    @Override
    public RRateLimiterReactive getRateLimiter(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRateLimiter(ca, params.getName()), RRateLimiterReactive.class);
    }

    @Override
    public RBinaryStreamReactive getBinaryStream(String name) {
        RedissonBinaryStream stream = new RedissonBinaryStream(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, stream,
                new RedissonBinaryStreamReactive(commandExecutor, stream), RBinaryStreamReactive.class);
    }

    @Override
    public RBinaryStreamReactive getBinaryStream(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonBinaryStream stream = new RedissonBinaryStream(ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, stream,
                new RedissonBinaryStreamReactive(ca, stream), RBinaryStreamReactive.class);
    }

    @Override
    public RSemaphoreReactive getSemaphore(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSemaphore(commandExecutor, name), RSemaphoreReactive.class);
    }

    @Override
    public RSemaphoreReactive getSemaphore(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSemaphore(ca, params.getName()), RSemaphoreReactive.class);
    }

    @Override
    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonPermitExpirableSemaphore(commandExecutor, name), RPermitExpirableSemaphoreReactive.class);
    }

    @Override
    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonPermitExpirableSemaphore(ca, params.getName()), RPermitExpirableSemaphoreReactive.class);
    }

    @Override
    public RReadWriteLockReactive getReadWriteLock(String name) {
        return new RedissonReadWriteLockReactive(commandExecutor, name);
    }

    @Override
    public RReadWriteLockReactive getReadWriteLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return new RedissonReadWriteLockReactive(ca, params.getName());
    }

    @Override
    public RLockReactive getLock(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonLock(commandExecutor, name), RLockReactive.class);
    }

    @Override
    public RLockReactive getLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonLock(ca, params.getName()), RLockReactive.class);
    }

    @Override
    public RLockReactive getSpinLock(String name) {
        return getSpinLock(name, LockOptions.defaults());
    }

    @Override
    public RLockReactive getSpinLock(String name, LockOptions.BackOff backOff) {
        RedissonSpinLock spinLock = new RedissonSpinLock(commandExecutor, name, backOff);
        return ReactiveProxyBuilder.create(commandExecutor, spinLock, RLockReactive.class);
    }

    @Override
    public RFencedLockReactive getFencedLock(String name) {
        RedissonFencedLock lock = new RedissonFencedLock(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, lock, RFencedLockReactive.class);
    }

    @Override
    public RFencedLockReactive getFencedLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonFencedLock lock = new RedissonFencedLock(ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, lock, RFencedLockReactive.class);
    }

    @Override
    public RLockReactive getMultiLock(RLockReactive... locks) {
        RLock[] ls = Arrays.stream(locks)
                            .map(l -> new RedissonLock(commandExecutor, l.getName()))
                            .toArray(RLock[]::new);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonMultiLock(ls), RLockReactive.class);
    }

    @Override
    public RLockReactive getMultiLock(String group, Collection<Object> values) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFasterMultiLock(commandExecutor, group, values), RLockReactive.class);
    }

    @Override
    public RLockReactive getMultiLock(RLock... locks) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonMultiLock(locks), RLockReactive.class);
    }
    
    @Override
    public RLockReactive getRedLock(RLock... locks) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRedLock(locks), RLockReactive.class);
    }

    @Override
    public RCountDownLatchReactive getCountDownLatch(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonCountDownLatch(commandExecutor, name), RCountDownLatchReactive.class);
    }

    @Override
    public RCountDownLatchReactive getCountDownLatch(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonCountDownLatch(ca, params.getName()), RCountDownLatchReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, commandExecutor), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, commandExecutor), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(org.redisson.api.options.MapCacheOptions<K, V> options) {
        MapCacheParams<K, V> params = (MapCacheParams) options;
        MapCacheOptions<K, V> ops = createOptions(params);

        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RMapCache<K, V> map = new RedissonMapCache<>(params.getCodec(), evictionScheduler, ca,
                params.getName(), null, ops, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, ca), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapCacheNativeReactive<K, V> getMapCacheNative(String name) {
        RMapCacheNative<K, V> map = new RedissonMapCacheNative<>(commandExecutor, name, null, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, commandExecutor), RMapCacheNativeReactive.class);
    }

    @Override
    public <K, V> RMapCacheNativeReactive<K, V> getMapCacheNative(String name, Codec codec) {
        RMapCacheNative<K, V> map = new RedissonMapCacheNative<>(codec, commandExecutor, name, null, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, commandExecutor), RMapCacheNativeReactive.class);
    }

    @Override
    public <K, V> RMapCacheNativeReactive<K, V> getMapCacheNative(org.redisson.api.options.MapOptions<K, V> options) {
        MapParams<K, V> params = (MapParams<K, V>) options;
        MapOptions<K, V> ops = createOptions(params);

        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RMapCacheNative<K, V> map = new RedissonMapCacheNative<>(params.getCodec(), ca,
                params.getName(), null, ops, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, ca), RMapCacheNativeReactive.class);
    }

    private static <K, V> MapOptions<K, V> createOptions(MapParams<K, V> params) {
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
        return ops;
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
    public <V> RBucketReactive<V> getBucket(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBucket<V>(commandExecutor, name), RBucketReactive.class);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBucket<V>(codec, commandExecutor, name), RBucketReactive.class);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonBucket<V>(params.getCodec(), ca, params.getName()), RBucketReactive.class);
    }

    @Override
    public RBucketsReactive getBuckets() {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBuckets(commandExecutor), RBucketsReactive.class);
    }

    @Override
    public RBucketsReactive getBuckets(Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBuckets(codec, commandExecutor), RBucketsReactive.class);
    }

    @Override
    public RBucketsReactive getBuckets(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBuckets(params.getCodec(), ca), RBucketsReactive.class);
    }

    @Override
    public <V> List<RBucketReactive<V>> findBuckets(String pattern) {
        RKeys redissonKeys = new RedissonKeys(commandExecutor);
        Iterable<String> keys = redissonKeys.getKeysByPattern(pattern);

        List<RBucketReactive<V>> buckets = new ArrayList<RBucketReactive<V>>();
        for (Object key : keys) {
            if (key != null) {
                buckets.add(this.<V>getBucket(key.toString()));
            }
        }
        return buckets;
    }

    @Override
    public <V> RJsonBucketReactive<V> getJsonBucket(String name, JsonCodec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonJsonBucket<V>(codec, commandExecutor, name), RJsonBucketReactive.class);
    }

    @Override
    public <V> RJsonBucketReactive<V> getJsonBucket(JsonBucketOptions<V> options) {
        JsonBucketParams<V> params = (JsonBucketParams<V>) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonJsonBucket<V>(params.getCodec(), ca, params.getName()), RJsonBucketReactive.class);
    }
    
    @Override
    public RJsonBucketsReactive getJsonBuckets(JsonCodec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonJsonBuckets(codec, commandExecutor), RJsonBucketsReactive.class);
    }
    
    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(commandExecutor, name), RHyperLogLogReactive.class);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(codec, commandExecutor, name), RHyperLogLogReactive.class);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonHyperLogLog<V>(params.getCodec(), ca, params.getName()), RHyperLogLogReactive.class);
    }

    @Override
    public RIdGeneratorReactive getIdGenerator(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonIdGenerator(commandExecutor, name), RIdGeneratorReactive.class);
    }

    @Override
    public RIdGeneratorReactive getIdGenerator(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonIdGenerator(ca, params.getName()), RIdGeneratorReactive.class);
    }

    @Override
    public <V> RListReactive<V> getList(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonList<V>(commandExecutor, name, null), 
                new RedissonListReactive<V>(commandExecutor, name), RListReactive.class);
    }

    @Override
    public <V> RListReactive<V> getList(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonList<V>(codec, commandExecutor, name, null), 
                new RedissonListReactive<V>(codec, commandExecutor, name), RListReactive.class);
    }

    @Override
    public <V> RListReactive<V> getList(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonList<V>(params.getCodec(), ca, params.getName(), null),
                new RedissonListReactive<V>(params.getCodec(), ca, params.getName()), RListReactive.class);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(commandExecutor, name), 
                new RedissonListMultimapReactive<K, V>(commandExecutor, name), RListMultimapReactive.class);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(codec, commandExecutor, name), 
                new RedissonListMultimapReactive<K, V>(codec, commandExecutor, name), RListMultimapReactive.class);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(params.getCodec(), ca, params.getName()),
                new RedissonListMultimapReactive<K, V>(params.getCodec(), ca, params.getName()), RListMultimapReactive.class);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(commandExecutor, name), 
                new RedissonSetMultimapReactive<K, V>(commandExecutor, name, this), RSetMultimapReactive.class);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(codec, commandExecutor, name), 
                new RedissonSetMultimapReactive<K, V>(codec, commandExecutor, name, this), RSetMultimapReactive.class);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(params.getCodec(), ca, params.getName()),
                new RedissonSetMultimapReactive<K, V>(params.getCodec(), ca, params.getName(), this), RSetMultimapReactive.class);
    }

    @Override
    public <K, V> RListMultimapCacheReactive<K, V> getListMultimapCache(String name) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheReactive<K, V>(listMultimap, commandExecutor), RListMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RListMultimapCacheReactive<K, V> getListMultimapCache(String name, Codec codec) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheReactive<>(listMultimap, commandExecutor), RListMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RListMultimapCacheReactive<K, V> getListMultimapCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, params.getCodec(), ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheReactive<>(listMultimap, ca), RListMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RListMultimapCacheNativeReactive<K, V> getListMultimapCacheNative(String name) {
        RedissonListMultimapCacheNative<K, V> listMultimap = new RedissonListMultimapCacheNative<>(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheReactive<>(listMultimap, commandExecutor), RListMultimapCacheNativeReactive.class);
    }

    @Override
    public <K, V> RListMultimapCacheNativeReactive<K, V> getListMultimapCacheNative(String name, Codec codec) {
        RedissonListMultimapCacheNative<K, V> listMultimap = new RedissonListMultimapCacheNative<>(codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheReactive<>(listMultimap, commandExecutor), RListMultimapCacheNativeReactive.class);
    }

    @Override
    public <K, V> RListMultimapCacheNativeReactive<K, V> getListMultimapCacheNative(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonListMultimapCacheNative<K, V> listMultimap = new RedissonListMultimapCacheNative<>(params.getCodec(), ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheReactive<>(listMultimap, ca), RListMultimapCacheNativeReactive.class);
    }

    @Override
    public <K, V> RSetMultimapCacheReactive<K, V> getSetMultimapCache(String name) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheReactive<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RSetMultimapCacheReactive<K, V> getSetMultimapCache(String name, Codec codec) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheReactive<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RSetMultimapCacheReactive<K, V> getSetMultimapCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, params.getCodec(), ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheReactive<K, V>(setMultimap, ca, this), RSetMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RSetMultimapCacheNativeReactive<K, V> getSetMultimapCacheNative(String name) {
        RedissonSetMultimapCacheNative<K, V> setMultimap = new RedissonSetMultimapCacheNative<>(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheReactive<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheNativeReactive.class);
    }

    @Override
    public <K, V> RSetMultimapCacheNativeReactive<K, V> getSetMultimapCacheNative(String name, Codec codec) {
        RedissonSetMultimapCacheNative<K, V> setMultimap = new RedissonSetMultimapCacheNative<>(codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheReactive<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheNativeReactive.class);
    }

    @Override
    public <K, V> RSetMultimapCacheNativeReactive<K, V> getSetMultimapCacheNative(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonSetMultimapCacheNative<K, V> setMultimap = new RedissonSetMultimapCacheNative<>(params.getCodec(), ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheReactive<K, V>(setMultimap, ca, this), RSetMultimapCacheNativeReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, commandExecutor), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, commandExecutor), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(org.redisson.api.options.MapOptions<K, V> options) {
        MapParams<K, V> params = (MapParams<K, V>) options;
        MapOptions<K, V> ops = createOptions(params);

        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonMap<K, V> map = new RedissonMap<>(params.getCodec(), ca, params.getName(), null, ops, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapReactive<K, V>(map, ca), RMapReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        RedissonSet<V> set = new RedissonSet<V>(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetReactive<V>(set, this), RSetReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        RedissonSet<V> set = new RedissonSet<V>(codec, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetReactive<V>(set, this), RSetReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonSet<V> set = new RedissonSet<V>(params.getCodec(), ca, params.getName(), null);
        return ReactiveProxyBuilder.create(commandExecutor, set,
                new RedissonSetReactive<V>(set, this), RSetReactive.class);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScoredSortedSet<V>(commandExecutor, name, null), 
                new RedissonScoredSortedSetReactive<V>(commandExecutor, name), RScoredSortedSetReactive.class);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null), 
                new RedissonScoredSortedSetReactive<V>(codec, commandExecutor, name), RScoredSortedSetReactive.class);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScoredSortedSet<V>(params.getCodec(), ca, params.getName(), null),
                new RedissonScoredSortedSetReactive<V>(params.getCodec(), ca, params.getName()), RScoredSortedSetReactive.class);
    }

    @Override
    public RLexSortedSetReactive getLexSortedSet(String name) {
        RedissonLexSortedSet set = new RedissonLexSortedSet(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonLexSortedSetReactive(set), 
                RLexSortedSetReactive.class);
    }

    @Override
    public RLexSortedSetReactive getLexSortedSet(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonLexSortedSet set = new RedissonLexSortedSet(ca, params.getName(), null);
        return ReactiveProxyBuilder.create(commandExecutor, set,
                new RedissonLexSortedSetReactive(set),
                RLexSortedSetReactive.class);
    }

    @Override
    public RShardedTopicReactive getShardedTopic(String name) {
        RedissonShardedTopic topic = new RedissonShardedTopic(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonTopicReactive(topic), RShardedTopicReactive.class);
    }

    @Override
    public RShardedTopicReactive getShardedTopic(String name, Codec codec) {
        RedissonShardedTopic topic = new RedissonShardedTopic(codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonTopicReactive(topic), RShardedTopicReactive.class);
    }

    @Override
    public RShardedTopicReactive getShardedTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonShardedTopic topic = new RedissonShardedTopic(params.getCodec(), ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonTopicReactive(topic), RShardedTopicReactive.class);
    }

    @Override
    public RTopicReactive getTopic(String name) {
        RedissonTopic topic = new RedissonTopic(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonTopicReactive(topic), RTopicReactive.class);
    }

    @Override
    public RTopicReactive getTopic(String name, Codec codec) {
        RedissonTopic topic = new RedissonTopic(codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic, 
                new RedissonTopicReactive(topic), RTopicReactive.class);
    }

    @Override
    public RTopicReactive getTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonTopic topic = new RedissonTopic(params.getCodec(), ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonTopicReactive(topic), RTopicReactive.class);
    }

    @Override
    public RReliableTopicReactive getReliableTopic(String name) {
        RedissonReliableTopic topic = new RedissonReliableTopic(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonReliableTopicReactive(topic), RReliableTopicReactive.class);
    }

    @Override
    public RReliableTopicReactive getReliableTopic(String name, Codec codec) {
        RedissonReliableTopic topic = new RedissonReliableTopic(codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonReliableTopicReactive(topic), RReliableTopicReactive.class);
    }

    @Override
    public RReliableTopicReactive getReliableTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonReliableTopic topic = new RedissonReliableTopic(params.getCodec(), ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonReliableTopicReactive(topic), RReliableTopicReactive.class);
    }

    @Override
    public RPatternTopicReactive getPatternTopic(String pattern) {
         return ReactiveProxyBuilder.create(commandExecutor, new RedissonPatternTopic(commandExecutor, pattern), RPatternTopicReactive.class);
    }

    @Override
    public RPatternTopicReactive getPatternTopic(String pattern, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonPatternTopic(codec, commandExecutor, pattern), RPatternTopicReactive.class);
    }

    @Override
    public RPatternTopicReactive getPatternTopic(PatternTopicOptions options) {
        PatternTopicParams params = (PatternTopicParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonPatternTopic(params.getCodec(), ca, params.getPattern()), RPatternTopicReactive.class);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonQueue<V>(commandExecutor, name, null), 
                new RedissonListReactive<V>(commandExecutor, name), RQueueReactive.class);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonQueue<V>(codec, commandExecutor, name, null), 
                new RedissonListReactive<V>(codec, commandExecutor, name), RQueueReactive.class);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonQueue<V>(params.getCodec(), ca, params.getName(), null),
                new RedissonListReactive<V>(params.getCodec(), ca, params.getName()), RQueueReactive.class);
    }

    @Override
    public <V> RReliableQueueReactive<V> getReliableQueue(String name) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Please refer to https://redisson.pro/feature-comparison.html");
    }

    @Override
    public <V> RReliableQueueReactive<V> getReliableQueue(String name, Codec codec) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Please refer to https://redisson.pro/feature-comparison.html");
    }

    @Override
    public <V> RReliableQueueReactive<V> getReliableQueue(PlainOptions options) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Please refer to https://redisson.pro/feature-comparison.html");
    }

    @Override
    public <V> RRingBufferReactive<V> getRingBuffer(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRingBuffer<V>(commandExecutor, name, null), RRingBufferReactive.class);
    }

    @Override
    public <V> RRingBufferReactive<V> getRingBuffer(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRingBuffer<V>(codec, commandExecutor, name, null), RRingBufferReactive.class);
    }

    @Override
    public <V> RRingBufferReactive<V> getRingBuffer(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonRingBuffer<V>(params.getCodec(), ca, params.getName(), null), RRingBufferReactive.class);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, queue, 
                new RedissonBlockingQueueReactive<V>(queue), RBlockingQueueReactive.class);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(codec, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, queue, 
                new RedissonBlockingQueueReactive<V>(queue), RBlockingQueueReactive.class);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(params.getCodec(), ca, params.getName(), null);
        return ReactiveProxyBuilder.create(commandExecutor, queue,
                new RedissonBlockingQueueReactive<V>(queue), RBlockingQueueReactive.class);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonDeque<V>(commandExecutor, name, null), 
                new RedissonListReactive<V>(commandExecutor, name), RDequeReactive.class);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonDeque<V>(codec, commandExecutor, name, null), 
                new RedissonListReactive<V>(codec, commandExecutor, name), RDequeReactive.class);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonDeque<V>(params.getCodec(), ca, params.getName(), null),
                new RedissonListReactive<V>(params.getCodec(), ca, params.getName()), RDequeReactive.class);
    }

    @Override
    public <V, L> RTimeSeriesReactive<V, L> getTimeSeries(String name) {
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<V, L>(evictionScheduler, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesReactive<V, L>(timeSeries, this), RTimeSeriesReactive.class);
    }

    @Override
    public <V, L> RTimeSeriesReactive<V, L> getTimeSeries(String name, Codec codec) {
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<V, L>(codec, evictionScheduler, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesReactive<V, L>(timeSeries, this), RTimeSeriesReactive.class);
    }

    @Override
    public <V, L> RTimeSeriesReactive<V, L> getTimeSeries(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<>(params.getCodec(), evictionScheduler, ca, params.getName());
        return ReactiveProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesReactive<V, L>(timeSeries, this), RTimeSeriesReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name) {
        RSetCache<V> set = new RedissonSetCache<V>(evictionScheduler, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetCacheReactive<V>(set, this), RSetCacheReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name, Codec codec) {
        RSetCache<V> set = new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetCacheReactive<V>(set, this), RSetCacheReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RSetCache<V> set = new RedissonSetCache<V>(params.getCodec(), evictionScheduler, ca, params.getName(), null);
        return ReactiveProxyBuilder.create(commandExecutor, set,
                new RedissonSetCacheReactive<V>(set, this), RSetCacheReactive.class);
    }

    @Override
    public RAtomicLongReactive getAtomicLong(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonAtomicLong(commandExecutor, name), RAtomicLongReactive.class);
    }

    @Override
    public RAtomicLongReactive getAtomicLong(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonAtomicLong(ca, params.getName()), RAtomicLongReactive.class);
    }

    @Override
    public RAtomicDoubleReactive getAtomicDouble(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonAtomicDouble(commandExecutor, name), RAtomicDoubleReactive.class);
    }

    @Override
    public RAtomicDoubleReactive getAtomicDouble(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonAtomicDouble(ca, params.getName()), RAtomicDoubleReactive.class);
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
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        String executorId = connectionManager.getServiceManager().getId();
        if (params.getCodec() != null && params.getCodec() != connectionManager.getServiceManager().getCfg().getCodec()) {
            executorId = executorId + ":" + params.getName();
        }
        return new RedissonRemoteService(params.getCodec(), params.getName(), ca, executorId);
    }

    @Override
    public RBitSetReactive getBitSet(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBitSet(commandExecutor, name), RBitSetReactive.class);
    }

    @Override
    public RBitSetReactive getBitSet(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBitSet(ca, params.getName()), RBitSetReactive.class);
    }

    @Override
    public <V> RBloomFilterReactive<V> getBloomFilter(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBloomFilter<>(commandExecutor, name), RBloomFilterReactive.class);
    }

    @Override
    public <V> RBloomFilterReactive<V> getBloomFilter(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBloomFilter<>(codec, commandExecutor, name), RBloomFilterReactive.class);
    }

    @Override
    public <V> RBloomFilterReactive<V> getBloomFilter(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonBloomFilter<V>(params.getCodec(), ca, params.getName()), RBloomFilterReactive.class);
    }

    @Override
    public <V> RBloomFilterNativeReactive<V> getBloomFilterNative(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBloomFilterNative<>(commandExecutor, name), RBloomFilterNativeReactive.class);
    }

    @Override
    public <V> RBloomFilterNativeReactive<V> getBloomFilterNative(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBloomFilterNative<>(codec, commandExecutor, name), RBloomFilterNativeReactive.class);
    }

    @Override
    public <V> RBloomFilterNativeReactive<V> getBloomFilterNative(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonBloomFilterNative<V>(params.getCodec(), ca, params.getName()), RBloomFilterNativeReactive.class);
    }

    @Override
    public <V> RCuckooFilterReactive<V> getCuckooFilter(String name) {
        return getCuckooFilter(name, null);
    }

    @Override
    public <V> RCuckooFilterReactive<V> getCuckooFilter(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonCuckooFilter<V>(codec, commandExecutor, name),
                RCuckooFilterReactive.class);
    }

    @Override
    public <V> RCuckooFilterReactive<V> getCuckooFilter(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor,
                new RedissonCuckooFilter<V>(params.getCodec(), ca, params.getName()),
                RCuckooFilterReactive.class);
    }

    @Override
    public RFunctionReactive getFunction() {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFuction(commandExecutor), RFunctionReactive.class);
    }

    @Override
    public RFunctionReactive getFunction(Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFuction(commandExecutor, codec), RFunctionReactive.class);
    }

    @Override
    public RFunctionReactive getFunction(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFuction(ca, params.getCodec()), RFunctionReactive.class);
    }

    @Override
    public RScriptReactive getScript() {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor), RScriptReactive.class);
    }
    
    @Override
    public RScriptReactive getScript(Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor, codec), RScriptReactive.class);
    }

    @Override
    public RScriptReactive getScript(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScript(ca, params.getCodec()), RScriptReactive.class);
    }

    @Override
    public RVectorSetReactive getVectorSet(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonVectorSet(commandExecutor, name), RVectorSetReactive.class);
    }

    @Override
    public RVectorSetReactive getVectorSet(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonVectorSet(ca, params.getName()), RVectorSetReactive.class);
    }

    @Override
    public RBatchReactive createBatch(BatchOptions options) {
        return new RedissonBatchReactive(evictionScheduler, connectionManager, commandExecutor, options);
    }

    @Override
    public RBatchReactive createBatch() {
        return createBatch(BatchOptions.defaults());
    }

    @Override
    public RKeysReactive getKeys() {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonKeys(commandExecutor), new RedissonKeysReactive(commandExecutor), RKeysReactive.class);
    }

    @Override
    public RKeysReactive getKeys(KeysOptions options) {
        KeysParams params = (KeysParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonKeys(ca), new RedissonKeysReactive(ca), RKeysReactive.class);
    }

    @Override
    public Config getConfig() {
        return connectionManager.getServiceManager().getCfg();
    }

    @Override
    public void shutdown() {
        writeBehindService.stop();
        connectionManager.shutdown();
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
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec, MapCacheOptions<K, V> options) {
        RMapCache<K, V> map = new RedissonMapCache<>(codec, evictionScheduler, commandExecutor, name, null, options, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, commandExecutor), RMapCacheReactive.class);
    }


    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, MapCacheOptions<K, V> options) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, options, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, commandExecutor), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, options, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, commandExecutor), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMap<>(codec, commandExecutor, name, null, options, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapReactive<>(map, commandExecutor), RMapReactive.class);
    }

    @Override
    public <K, V> RLocalCachedMapReactive<K, V> getLocalCachedMap(String name, LocalCachedMapOptions<K, V> options) {
        return getLocalCachedMap(name, null, options);
    }

    @Override
    public <K, V> RLocalCachedMapReactive<K, V> getLocalCachedMap(String name, Codec codec, LocalCachedMapOptions<K, V> options) {
        RMap<K, V> map = new RedissonLocalCachedMap<>(codec, commandExecutor, name,
                                                        options, evictionScheduler, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapReactive<>(map, commandExecutor), RLocalCachedMapReactive.class);
    }

    @Override
    public <K, V> RLocalCachedMapReactive<K, V> getLocalCachedMap(org.redisson.api.options.LocalCachedMapOptions<K, V> options) {
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

        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RMap<K, V> map = new RedissonLocalCachedMap<>(params.getCodec(), ca, params.getName(),
                ops, evictionScheduler, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapReactive<>(map, ca), RLocalCachedMapReactive.class);
    }

    @Override
    public <K, V> RLocalCachedMapCacheReactive<K, V> getLocalCachedMapCache(String name, LocalCachedMapCacheOptions<K, V> options) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Visit https://redisson.pro");
    }

    @Override
    public <K, V> RLocalCachedMapCacheReactive<K, V> getLocalCachedMapCache(String name, Codec codec, LocalCachedMapCacheOptions<K, V> options) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Visit https://redisson.pro");
    }

    @Override
    public RTransactionReactive createTransaction(TransactionOptions options) {
        return new RedissonTransactionReactive(commandExecutor, options);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, deque, 
                new RedissonBlockingDequeReactive<V>(deque), RBlockingDequeReactive.class);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name, Codec codec) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(codec, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, deque, 
                new RedissonBlockingDequeReactive<V>(deque), RBlockingDequeReactive.class);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(params.getCodec(), ca, params.getName(), null);
        return ReactiveProxyBuilder.create(commandExecutor, deque,
                new RedissonBlockingDequeReactive<V>(deque), RBlockingDequeReactive.class);
    }

    @Override
    public <V> RTransferQueueReactive<V> getTransferQueue(String name) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(commandExecutor, name, service);
        return ReactiveProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueReactive<V>(queue), RTransferQueueReactive.class);
    }

    @Override
    public <V> RTransferQueueReactive<V> getTransferQueue(String name, Codec codec) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(codec, commandExecutor, name, service);
        return ReactiveProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueReactive<V>(queue), RTransferQueueReactive.class);
    }

    @Override
    public <V> RTransferQueueReactive<V> getTransferQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandReactiveExecutor ca = commandExecutor.copy(params);
        String remoteName = RedissonObject.suffixName(params.getName(), "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(params.getCodec(), ca, params.getName(), service);
        return ReactiveProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueReactive<V>(queue), RTransferQueueReactive.class);
    }

    @Override
    public String getId() {
        return commandExecutor.getServiceManager().getId();
    }

}
