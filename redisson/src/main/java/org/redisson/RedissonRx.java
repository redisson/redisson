/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.api.*;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.MapCacheOptions;
import org.redisson.api.MapOptions;
import org.redisson.api.options.*;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonCodec;
import org.redisson.config.Config;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.rx.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonRx implements RedissonRxClient {

    private final WriteBehindService writeBehindService;
    private final EvictionScheduler evictionScheduler;
    private final CommandRxExecutor commandExecutor;
    private final ConnectionManager connectionManager;

    RedissonRx(ConnectionManager connectionManager, EvictionScheduler evictionScheduler, WriteBehindService writeBehindService) {
        this.connectionManager = connectionManager;
        RedissonObjectBuilder objectBuilder = null;
        if (connectionManager.getServiceManager().getCfg().isReferenceEnabled()) {
            objectBuilder = new RedissonObjectBuilder(this);
        }
        commandExecutor = CommandRxExecutor.create(connectionManager, objectBuilder);
        this.evictionScheduler = evictionScheduler;
        this.writeBehindService = writeBehindService;
    }

    public CommandRxExecutor getCommandExecutor() {
        return commandExecutor;
    }

    @Override
    public <K, V> RStreamRx<K, V> getStream(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(commandExecutor, name), RStreamRx.class);
    }

    @Override
    public <K, V> RStreamRx<K, V> getStream(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(codec, commandExecutor, name), RStreamRx.class);
    }

    @Override
    public <K, V> RStreamRx<K, V> getStream(PlainOptions options) {
        PlainParams params = (PlainParams) options;

        return RxProxyBuilder.create(commandExecutor,
                new RedissonStream<K, V>(params.getCodec(), commandExecutor.copy(params), params.getName()), RStreamRx.class);
    }

    @Override
    public RSearchRx getSearch() {
        return getSearch((Codec) null);
    }

    @Override
    public RSearchRx getSearch(Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonSearch(codec, commandExecutor), RSearchRx.class);
    }

    @Override
    public RSearchRx getSearch(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        return RxProxyBuilder.create(commandExecutor,
                new RedissonSearch(params.getCodec(), commandExecutor.copy(params)), RSearchRx.class);
    }

    @Override
    public <V> RGeoRx<V> getGeo(String name) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, new RedissonGeo<V>(commandExecutor, name, null), 
                new RedissonScoredSortedSetRx<V>(set), RGeoRx.class);
    }
    
    @Override
    public <V> RGeoRx<V> getGeo(String name, Codec codec) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, new RedissonGeo<V>(codec, commandExecutor, name, null), 
                new RedissonScoredSortedSetRx<V>(set), RGeoRx.class);
    }

    @Override
    public <V> RGeoRx<V> getGeo(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(params.getCodec(), ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, new RedissonGeo<V>(params.getCodec(), ce, params.getName(), null),
                new RedissonScoredSortedSetRx<V>(set), RGeoRx.class);
    }

    @Override
    public RLockRx getFairLock(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonFairLock(commandExecutor, name), RLockRx.class);
    }

    @Override
    public RLockRx getFairLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return RxProxyBuilder.create(commandExecutor,
                            new RedissonFairLock(commandExecutor.copy(params), params.getName()), RLockRx.class);
    }

    @Override
    public RRateLimiterRx getRateLimiter(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonRateLimiter(commandExecutor, name), RRateLimiterRx.class);
    }

    @Override
    public RRateLimiterRx getRateLimiter(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        return RxProxyBuilder.create(commandExecutor,
                new RedissonRateLimiter(commandExecutor.copy(params), params.getName()), RRateLimiterRx.class);
    }

    @Override
    public RBinaryStreamRx getBinaryStream(String name) {
        RedissonBinaryStream stream = new RedissonBinaryStream(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, stream,
                new RedissonBinaryStreamRx(commandExecutor, stream), RBinaryStreamRx.class);
    }

    @Override
    public RBinaryStreamRx getBinaryStream(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonBinaryStream stream = new RedissonBinaryStream(ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, stream,
                new RedissonBinaryStreamRx(ce, stream), RBinaryStreamRx.class);
    }

    @Override
    public RSemaphoreRx getSemaphore(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonSemaphore(commandExecutor, name), RSemaphoreRx.class);
    }

    @Override
    public RSemaphoreRx getSemaphore(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonSemaphore(ce, params.getName()), RSemaphoreRx.class);
    }

    @Override
    public RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonPermitExpirableSemaphore(commandExecutor, name), RPermitExpirableSemaphoreRx.class);
    }

    @Override
    public RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor,
                new RedissonPermitExpirableSemaphore(ce, params.getName()), RPermitExpirableSemaphoreRx.class);
    }

    @Override
    public RReadWriteLockRx getReadWriteLock(String name) {
        return new RedissonReadWriteLockRx(commandExecutor, name);
    }

    @Override
    public RReadWriteLockRx getReadWriteLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return new RedissonReadWriteLockRx(ce, params.getName());
    }

    @Override
    public RLockRx getLock(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonLock(commandExecutor, name), RLockRx.class);
    }

    @Override
    public RLockRx getLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonLock(ce, params.getName()), RLockRx.class);
    }

    @Override
    public RLockRx getSpinLock(String name) {
        return getSpinLock(name, LockOptions.defaults());
    }

    @Override
    public RLockRx getSpinLock(String name, LockOptions.BackOff backOff) {
        RedissonSpinLock spinLock = new RedissonSpinLock(commandExecutor, name, backOff);
        return RxProxyBuilder.create(commandExecutor, spinLock, RLockRx.class);
    }

    @Override
    public RFencedLockRx getFencedLock(String name) {
        RedissonFencedLock lock = new RedissonFencedLock(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, lock, RFencedLockRx.class);
    }

    @Override
    public RFencedLockRx getFencedLock(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonFencedLock lock = new RedissonFencedLock(ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, lock, RFencedLockRx.class);
    }

    @Override
    public RLockRx getMultiLock(RLockRx... locks) {
        RLock[] ls = Arrays.stream(locks)
                            .map(l -> new RedissonLock(commandExecutor, l.getName()))
                            .toArray(RLock[]::new);
        return RxProxyBuilder.create(commandExecutor, new RedissonMultiLock(ls), RLockRx.class);
    }

    @Override
    public RLockRx getMultiLock(String group, Collection<Object> values) {
        return RxProxyBuilder.create(commandExecutor, new RedissonFasterMultiLock(commandExecutor, group, values), RLockRx.class);
    }

    @Override
    public RLockRx getMultiLock(RLock... locks) {
        return RxProxyBuilder.create(commandExecutor, new RedissonMultiLock(locks), RLockRx.class);
    }
    
    @Override
    public RLockRx getRedLock(RLock... locks) {
        return RxProxyBuilder.create(commandExecutor, new RedissonRedLock(locks), RLockRx.class);
    }

    @Override
    public RCountDownLatchRx getCountDownLatch(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonCountDownLatch(commandExecutor, name), RCountDownLatchRx.class);
    }

    @Override
    public RCountDownLatchRx getCountDownLatch(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonCountDownLatch(ce, params.getName()), RCountDownLatchRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec) {
        RMap<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name) {
        RedissonMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(org.redisson.api.options.MapCacheOptions<K, V> options) {
        MapCacheParams<K, V> params = (MapCacheParams<K, V>) options;
        MapCacheOptions<K, V> ops = MapCacheOptions.<K, V>defaults()
                .loader(params.getLoader())
                .loaderAsync(params.getLoaderAsync())
                .writer(params.getWriter())
                .writerAsync(params.getWriterAsync())
                .writeBehindDelay(params.getWriteBehindDelay())
                .writeBehindBatchSize(params.getWriteBehindBatchSize())
                .writerRetryInterval(Duration.ofMillis(params.getWriteRetryInterval()));

        if (params.isRemoveEmptyEvictionTask()) {
            ops.removeEmptyEvictionTask();
        }

        if (params.getWriteMode() != null) {
            ops.writeMode(MapOptions.WriteMode.valueOf(params.getWriteMode().toString()));
        }
        if (params.getWriteRetryAttempts() > 0) {
            ops.writerRetryAttempts(params.getWriteRetryAttempts());
        }

        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonMapCache<K, V> map = new RedissonMapCache<>(params.getCodec(), evictionScheduler,
                ce, params.getName(), null, ops, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheRx<K, V>(map, ce), RMapCacheRx.class);
    }

    @Override
    public <V> RBucketRx<V> getBucket(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBucket<V>(commandExecutor, name), RBucketRx.class);
    }

    @Override
    public <V> RBucketRx<V> getBucket(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBucket<V>(codec, commandExecutor, name), RBucketRx.class);
    }

    @Override
    public <V> RBucketRx<V> getBucket(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor,
                new RedissonBucket<V>(params.getCodec(), ce, params.getName()), RBucketRx.class);
    }

    @Override
    public RBucketsRx getBuckets() {
        return RxProxyBuilder.create(commandExecutor, new RedissonBuckets(commandExecutor), RBucketsRx.class);
    }

    @Override
    public RBucketsRx getBuckets(Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBuckets(codec, commandExecutor), RBucketsRx.class);
    }

    @Override
    public RBucketsRx getBuckets(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonBuckets(params.getCodec(), ce), RBucketsRx.class);
    }

    @Override
    public <V> RJsonBucketRx<V> getJsonBucket(String name, JsonCodec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonJsonBucket<>(codec, commandExecutor, name), RJsonBucketRx.class);
    }

    @Override
    public <V> RJsonBucketRx<V> getJsonBucket(JsonBucketOptions<V> options) {
        JsonBucketParams<V> params = (JsonBucketParams<V>) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonJsonBucket<>(params.getCodec(), ce, params.getName()), RJsonBucketRx.class);
    }
    
    @Override
    public RJsonBucketsRx getJsonBuckets(JsonCodec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonJsonBuckets(codec, commandExecutor), RJsonBucketsRx.class);
    }
    
    @Override
    public <V> RHyperLogLogRx<V> getHyperLogLog(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(commandExecutor, name), RHyperLogLogRx.class);
    }

    @Override
    public <V> RHyperLogLogRx<V> getHyperLogLog(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(codec, commandExecutor, name), RHyperLogLogRx.class);
    }

    @Override
    public <V> RHyperLogLogRx<V> getHyperLogLog(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(params.getCodec(), ce, params.getName()), RHyperLogLogRx.class);
    }

    @Override
    public RIdGeneratorRx getIdGenerator(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonIdGenerator(commandExecutor, name), RIdGeneratorRx.class);
    }

    @Override
    public RIdGeneratorRx getIdGenerator(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonIdGenerator(ce, params.getName()), RIdGeneratorRx.class);
    }

    @Override
    public <V> RListRx<V> getList(String name) {
        RedissonList<V> list = new RedissonList<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, list, 
                new RedissonListRx<V>(list), RListRx.class);
    }

    @Override
    public <V> RListRx<V> getList(String name, Codec codec) {
        RedissonList<V> list = new RedissonList<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, list, 
                new RedissonListRx<V>(list), RListRx.class);
    }

    @Override
    public <V> RListRx<V> getList(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonList<V> list = new RedissonList<V>(params.getCodec(), ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, list,
                new RedissonListRx<V>(list), RListRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name) {
        RedissonListMultimap<K, V> listMultimap = new RedissonListMultimap<>(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name, Codec codec) {
        RedissonListMultimap<K, V> listMultimap = new RedissonListMultimap<>(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonListMultimap<K, V> listMultimap = new RedissonListMultimap<>(params.getCodec(), ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(String name) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapCacheRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(String name, Codec codec) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapCacheRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, params.getCodec(), ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, ce), RListMultimapCacheRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheNativeRx<K, V> getListMultimapCacheNative(String name) {
        RedissonListMultimapCacheNative<K, V> listMultimap = new RedissonListMultimapCacheNative<>(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapCacheNativeRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheNativeRx<K, V> getListMultimapCacheNative(String name, Codec codec) {
        RedissonListMultimapCacheNative<K, V> listMultimap = new RedissonListMultimapCacheNative<>(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapCacheNativeRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheNativeRx<K, V> getListMultimapCacheNative(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonListMultimapCacheNative<K, V> listMultimap = new RedissonListMultimapCacheNative<>(params.getCodec(), ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, ce), RListMultimapCacheNativeRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name) {
        RedissonSetMultimap<K, V> setMultimap = new RedissonSetMultimap<>(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<K, V>(setMultimap, commandExecutor, this), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name, Codec codec) {
        RedissonSetMultimap<K, V> setMultimap = new RedissonSetMultimap<>(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<K, V>(setMultimap, commandExecutor, this), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonSetMultimap<K, V> setMultimap = new RedissonSetMultimap<>(params.getCodec(), ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<>(setMultimap, ce, this), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(String name) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(String name, Codec codec) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, params.getCodec(), ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<>(setMultimap, ce, this), RSetMultimapCacheRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheNativeRx<K, V> getSetMultimapCacheNative(String name) {
        RedissonSetMultimapCacheNative<K, V> setMultimap = new RedissonSetMultimapCacheNative<>(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<>(setMultimap, commandExecutor, this), RSetMultimapCacheNativeRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheNativeRx<K, V> getSetMultimapCacheNative(String name, Codec codec) {
        RedissonSetMultimapCacheNative<K, V> setMultimap = new RedissonSetMultimapCacheNative<>(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<>(setMultimap, commandExecutor, this), RSetMultimapCacheNativeRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheNativeRx<K, V> getSetMultimapCacheNative(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonSetMultimapCacheNative<K, V> setMultimap = new RedissonSetMultimapCacheNative<>(params.getCodec(), ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<>(setMultimap, ce, this), RSetMultimapCacheNativeRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, commandExecutor), RMapRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, commandExecutor), RMapRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(org.redisson.api.options.MapOptions<K, V> options) {
        MapParams<K, V> params = (MapParams<K, V>) options;
        MapOptions<K, V> ops = createOptions(params);

        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonMap<K, V> map = new RedissonMap<>(params.getCodec(), ce, params.getName(), null, ops, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapRx<>(map, ce), RMapRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name) {
        RedissonSet<V> set = new RedissonSet<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetRx<V>(set, this), RSetRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name, Codec codec) {
        RedissonSet<V> set = new RedissonSet<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetRx<V>(set, this), RSetRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonSet<V> set = new RedissonSet<V>(params.getCodec(), ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, set,
                new RedissonSetRx<V>(set, this), RSetRx.class);
    }

    @Override
    public <V> RScoredSortedSetRx<V> getScoredSortedSet(String name) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonScoredSortedSetRx<V>(set), RScoredSortedSetRx.class);
    }

    @Override
    public <V> RScoredSortedSetRx<V> getScoredSortedSet(String name, Codec codec) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonScoredSortedSetRx<V>(set), RScoredSortedSetRx.class);
    }

    @Override
    public <V> RScoredSortedSetRx<V> getScoredSortedSet(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(params.getCodec(), ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, set,
                new RedissonScoredSortedSetRx<>(set), RScoredSortedSetRx.class);
    }

    @Override
    public RLexSortedSetRx getLexSortedSet(String name) {
        RedissonLexSortedSet set = new RedissonLexSortedSet(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonLexSortedSetRx(set), RLexSortedSetRx.class);
    }

    @Override
    public RLexSortedSetRx getLexSortedSet(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonLexSortedSet set = new RedissonLexSortedSet(ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, set,
                new RedissonLexSortedSetRx(set), RLexSortedSetRx.class);
    }

    @Override
    public RShardedTopicRx getShardedTopic(String name) {
        RShardedTopic topic = new RedissonShardedTopic(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RShardedTopicRx.class);
    }

    @Override
    public RShardedTopicRx getShardedTopic(String name, Codec codec) {
        RShardedTopic topic = new RedissonShardedTopic(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RShardedTopicRx.class);
    }

    @Override
    public RShardedTopicRx getShardedTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RShardedTopic topic = new RedissonShardedTopic(params.getCodec(), ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RShardedTopicRx.class);
    }

    @Override
    public RTopicRx getTopic(String name) {
        RTopic topic = new RedissonTopic(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RTopicRx.class);
    }

    @Override
    public RTopicRx getTopic(String name, Codec codec) {
        RTopic topic = new RedissonTopic(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RTopicRx.class);
    }

    @Override
    public RTopicRx getTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RTopic topic = new RedissonTopic(params.getCodec(), ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RTopicRx.class);
    }

    @Override
    public RReliableTopicRx getReliableTopic(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonReliableTopic(commandExecutor, name), RReliableTopicRx.class);
    }

    @Override
    public RReliableTopicRx getReliableTopic(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonReliableTopic(codec, commandExecutor, name), RReliableTopicRx.class);
    }

    @Override
    public RReliableTopicRx getReliableTopic(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor,
                new RedissonReliableTopic(params.getCodec(), ce, params.getName()), RReliableTopicRx.class);
    }

    @Override
    public RPatternTopicRx getPatternTopic(String pattern) {
        return RxProxyBuilder.create(commandExecutor, new RedissonPatternTopic(commandExecutor, pattern), RPatternTopicRx.class);
    }

    @Override
    public RPatternTopicRx getPatternTopic(String pattern, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonPatternTopic(codec, commandExecutor, pattern), RPatternTopicRx.class);
    }

    @Override
    public RPatternTopicRx getPatternTopic(PatternTopicOptions options) {
        PatternTopicParams params = (PatternTopicParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonPatternTopic(params.getCodec(), ce, params.getPattern()), RPatternTopicRx.class);
    }

    @Override
    public <V> RQueueRx<V> getQueue(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonQueue<V>(commandExecutor, name, null), 
                new RedissonListRx<V>(new RedissonList<V>(commandExecutor, name, null)), RQueueRx.class);
    }

    @Override
    public <V> RQueueRx<V> getQueue(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonQueue<V>(codec, commandExecutor, name, null), 
                new RedissonListRx<V>(new RedissonList<V>(codec, commandExecutor, name, null)), RQueueRx.class);
    }

    @Override
    public <V> RQueueRx<V> getQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonQueue<V>(params.getCodec(), ce, params.getName(), null),
                new RedissonListRx<V>(new RedissonList<V>(params.getCodec(), ce, params.getName(), null)), RQueueRx.class);
    }

    @Override
    public <V> RReliableQueueRx<V> getReliableQueue(String name) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Please refer to https://redisson.pro/feature-comparison.html");
    }

    @Override
    public <V> RReliableQueueRx<V> getReliableQueue(String name, Codec codec) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Please refer to https://redisson.pro/feature-comparison.html");
    }

    @Override
    public <V> RReliableQueueRx<V> getReliableQueue(PlainOptions options) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Please refer to https://redisson.pro/feature-comparison.html");
    }

    @Override
    public <V> RRingBufferRx<V> getRingBuffer(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonRingBuffer<V>(commandExecutor, name, null), RRingBufferRx.class);
    }

    @Override
    public <V> RRingBufferRx<V> getRingBuffer(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonRingBuffer<V>(codec, commandExecutor, name, null), RRingBufferRx.class);
    }

    @Override
    public <V> RRingBufferRx<V> getRingBuffer(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor,
                new RedissonRingBuffer<V>(params.getCodec(), ce, params.getName(), null), RRingBufferRx.class);
    }

    @Override
    public <V> RBlockingQueueRx<V> getBlockingQueue(String name) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, queue, 
                new RedissonBlockingQueueRx<V>(queue), RBlockingQueueRx.class);
    }

    @Override
    public <V> RBlockingQueueRx<V> getBlockingQueue(String name, Codec codec) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, queue, 
                new RedissonBlockingQueueRx<V>(queue), RBlockingQueueRx.class);
    }

    @Override
    public <V> RBlockingQueueRx<V> getBlockingQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(params.getCodec(), ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, queue,
                new RedissonBlockingQueueRx<V>(queue), RBlockingQueueRx.class);
    }

    @Override
    public <V> RDequeRx<V> getDeque(String name) {
        RedissonDeque<V> queue = new RedissonDeque<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, queue, 
                new RedissonListRx<V>(queue), RDequeRx.class);
    }

    @Override
    public <V> RDequeRx<V> getDeque(String name, Codec codec) {
        RedissonDeque<V> queue = new RedissonDeque<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, queue, 
                new RedissonListRx<V>(queue), RDequeRx.class);
    }

    @Override
    public <V> RDequeRx<V> getDeque(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonDeque<V> queue = new RedissonDeque<V>(params.getCodec(), ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, queue,
                new RedissonListRx<V>(queue), RDequeRx.class);
    }

    @Override
    public <V, L> RTimeSeriesRx<V, L> getTimeSeries(String name) {
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<V, L>(evictionScheduler, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesRx<V, L>(timeSeries, this), RTimeSeriesRx.class);
    }

    @Override
    public <V, L> RTimeSeriesRx<V, L> getTimeSeries(String name, Codec codec) {
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<V, L>(codec, evictionScheduler, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesRx<V, L>(timeSeries, this), RTimeSeriesRx.class);
    }

    @Override
    public <V, L> RTimeSeriesRx<V, L> getTimeSeries(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<>(params.getCodec(), evictionScheduler, ce, params.getName());
        return RxProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesRx<>(timeSeries, this), RTimeSeriesRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(String name) {
        RSetCache<V> set = new RedissonSetCache<V>(evictionScheduler, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetCacheRx<V>(set, this), RSetCacheRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(String name, Codec codec) {
        RSetCache<V> set = new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetCacheRx<V>(set, this), RSetCacheRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RSetCache<V> set = new RedissonSetCache<V>(params.getCodec(), evictionScheduler, ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, set,
                new RedissonSetCacheRx<V>(set, this), RSetCacheRx.class);
    }

    @Override
    public RAtomicLongRx getAtomicLong(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonAtomicLong(commandExecutor, name), RAtomicLongRx.class);
    }

    @Override
    public RAtomicLongRx getAtomicLong(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonAtomicLong(ce, params.getName()), RAtomicLongRx.class);
    }

    @Override
    public RAtomicDoubleRx getAtomicDouble(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonAtomicDouble(commandExecutor, name), RAtomicDoubleRx.class);
    }

    @Override
    public RAtomicDoubleRx getAtomicDouble(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonAtomicDouble(ce, params.getName()), RAtomicDoubleRx.class);
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
        return new RedissonRemoteService(params.getCodec(), params.getName(), commandExecutor, executorId);
    }

    @Override
    public RBitSetRx getBitSet(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBitSet(commandExecutor, name), RBitSetRx.class);
    }

    @Override
    public RBitSetRx getBitSet(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonBitSet(ce, params.getName()), RBitSetRx.class);
    }

    @Override
    public <V> RBloomFilterRx<V> getBloomFilter(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBloomFilter<>(commandExecutor, name), RBloomFilterRx.class);
    }

    @Override
    public <V> RBloomFilterRx<V> getBloomFilter(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBloomFilter<>(codec, commandExecutor, name), RBloomFilterRx.class);
    }

    @Override
    public <V> RBloomFilterRx<V> getBloomFilter(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor,
                new RedissonBloomFilter<V>(params.getCodec(), ce, params.getName()), RBloomFilterRx.class);
    }

    @Override
    public <V> RBloomFilterNativeRx<V> getBloomFilterNative(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBloomFilterNative<>(commandExecutor, name), RBloomFilterNativeRx.class);
    }

    @Override
    public <V> RBloomFilterNativeRx<V> getBloomFilterNative(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBloomFilterNative<>(codec, commandExecutor, name), RBloomFilterNativeRx.class);
    }

    @Override
    public <V> RBloomFilterNativeRx<V> getBloomFilterNative(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor,
                new RedissonBloomFilterNative<V>(params.getCodec(), ce, params.getName()), RBloomFilterNativeRx.class);
    }

    @Override
    public <V> RCuckooFilterRx<V> getCuckooFilter(String name) {
        return getCuckooFilter(name, null);
    }

    @Override
    public <V> RCuckooFilterRx<V> getCuckooFilter(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor,
                new RedissonCuckooFilter<V>(codec, commandExecutor, name),
                RCuckooFilterRx.class);
    }

    @Override
    public <V> RCuckooFilterRx<V> getCuckooFilter(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ca = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor,
                new RedissonCuckooFilter<V>(params.getCodec(), ca, params.getName()),
                RCuckooFilterRx.class);
    }

    @Override
    public RFunctionRx getFunction() {
        return RxProxyBuilder.create(commandExecutor, new RedissonFuction(commandExecutor), RFunctionRx.class);
    }

    @Override
    public RFunctionRx getFunction(Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonFuction(commandExecutor, codec), RFunctionRx.class);
    }

    @Override
    public RFunctionRx getFunction(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonFuction(ce, params.getCodec()), RFunctionRx.class);
    }

    @Override
    public RScriptRx getScript() {
        return RxProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor), RScriptRx.class);
    }
    
    @Override
    public RScriptRx getScript(Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor, codec), RScriptRx.class);
    }

    @Override
    public RScriptRx getScript(OptionalOptions options) {
        OptionalParams params = (OptionalParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonScript(ce, params.getCodec()), RScriptRx.class);
    }

    @Override
    public RVectorSetRx getVectorSet(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonVectorSet(commandExecutor, name), RVectorSetRx.class);
    }

    @Override
    public RVectorSetRx getVectorSet(CommonOptions options) {
        CommonParams params = (CommonParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonVectorSet(ce, params.getName()), RVectorSetRx.class);
    }

    @Override
    public RBatchRx createBatch() {
        return createBatch(BatchOptions.defaults());
    }
    
    @Override
    public RBatchRx createBatch(BatchOptions options) {
        return new RedissonBatchRx(evictionScheduler, connectionManager, commandExecutor, options);
    }

    @Override
    public RKeysRx getKeys() {
        return RxProxyBuilder.create(commandExecutor, new RedissonKeys(commandExecutor), new RedissonKeysRx(commandExecutor), RKeysRx.class);
    }

    @Override
    public RKeysRx getKeys(KeysOptions options) {
        KeysParams params = (KeysParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        return RxProxyBuilder.create(commandExecutor, new RedissonKeys(ce), new RedissonKeysRx(ce), RKeysRx.class);
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
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec, MapCacheOptions<K, V> options) {
        RedissonMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, options, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheRx.class);
    }


    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, MapCacheOptions<K, V> options) {
        RMap<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, options, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapCacheNativeRx<K, V> getMapCacheNative(String name) {
        RMap<K, V> map = new RedissonMapCacheNative<>(commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheNativeRx.class);
    }

    @Override
    public <K, V> RMapCacheNativeRx<K, V> getMapCacheNative(String name, Codec codec) {
        RMap<K, V> map = new RedissonMapCacheNative<>(codec, commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheNativeRx.class);
    }

    @Override
    public <K, V> RMapCacheNativeRx<K, V> getMapCacheNative(org.redisson.api.options.MapOptions<K, V> options) {
        MapParams<K, V> params = (MapParams<K, V>) options;
        MapOptions<K, V> ops = createOptions(params);

        CommandRxExecutor ce = commandExecutor.copy(params);
        RMap<K, V> map = new RedissonMapCacheNative<>(params.getCodec(), ce, params.getName(), null, ops, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheRx<>(map, ce), RMapCacheNativeRx.class);
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

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, options, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, commandExecutor), RMapRx.class);
    }


    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, options, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, commandExecutor), RMapRx.class);
    }

    @Override
    public <K, V> RLocalCachedMapRx<K, V> getLocalCachedMap(String name, LocalCachedMapOptions<K, V> options) {
        return getLocalCachedMap(name, null, options);
    }

    @Override
    public <K, V> RLocalCachedMapRx<K, V> getLocalCachedMap(String name, Codec codec, LocalCachedMapOptions<K, V> options) {
        RMap<K, V> map = new RedissonLocalCachedMap<>(codec, commandExecutor, name, options, evictionScheduler, null, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapRx<>(map, commandExecutor), RLocalCachedMapRx.class);
    }

    @Override
    public <K, V> RLocalCachedMapRx<K, V> getLocalCachedMap(org.redisson.api.options.LocalCachedMapOptions<K, V> options) {
        LocalCachedMapParams<K, V> params = (LocalCachedMapParams) options;

        LocalCachedMapOptions<K, V> ops = LocalCachedMapOptions.<K, V>defaults()
                .cacheProvider(LocalCachedMapOptions.CacheProvider.valueOf(params.getCacheProvider().toString()))
                .cacheSize(params.getCacheSize())
                .storeMode(LocalCachedMapOptions.StoreMode.valueOf(params.getStoreMode().toString()))
                .readMode(LocalCachedMapOptions.ReadMode.valueOf(params.getReadMode().toString()))
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

        CommandRxExecutor ce = commandExecutor.copy(params);
        RMap<K, V> map = new RedissonLocalCachedMap<>(params.getCodec(), ce, params.getName(),
                                ops, evictionScheduler, null, writeBehindService);

        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapRx<>(map, ce), RLocalCachedMapRx.class);
    }

    @Override
    public <K, V> RLocalCachedMapCacheRx<K, V> getLocalCachedMapCache(String name, LocalCachedMapCacheOptions<K, V> options) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Visit https://redisson.pro");
    }

    @Override
    public <K, V> RLocalCachedMapCacheRx<K, V> getLocalCachedMapCache(String name, Codec codec, LocalCachedMapCacheOptions<K, V> options) {
        throw new UnsupportedOperationException("This feature is implemented in the Redisson PRO version. Visit https://redisson.pro");
    }

    @Override
    public RTransactionRx createTransaction(TransactionOptions options) {
        return new RedissonTransactionRx(commandExecutor, options);
    }

    @Override
    public <V> RBlockingDequeRx<V> getBlockingDeque(String name) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, deque, 
                new RedissonBlockingDequeRx<V>(deque), RBlockingDequeRx.class);
    }

    @Override
    public <V> RBlockingDequeRx<V> getBlockingDeque(String name, Codec codec) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, deque, 
                new RedissonBlockingDequeRx<V>(deque), RBlockingDequeRx.class);
    }

    @Override
    public <V> RBlockingDequeRx<V> getBlockingDeque(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(params.getCodec(), ce, params.getName(), null);
        return RxProxyBuilder.create(commandExecutor, deque,
                new RedissonBlockingDequeRx<V>(deque), RBlockingDequeRx.class);
    }

    @Override
    public <V> RTransferQueueRx<V> getTransferQueue(String name) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(commandExecutor, name, service);
        return RxProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueRx<V>(queue), RTransferQueueRx.class);
    }

    @Override
    public <V> RTransferQueueRx<V> getTransferQueue(String name, Codec codec) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(codec, commandExecutor, name, service);
        return RxProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueRx<V>(queue), RTransferQueueRx.class);
    }

    @Override
    public <V> RTransferQueueRx<V> getTransferQueue(PlainOptions options) {
        PlainParams params = (PlainParams) options;
        String remoteName = RedissonObject.suffixName(params.getName(), "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        CommandRxExecutor ce = commandExecutor.copy(params);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(params.getCodec(), ce, params.getName(), service);
        return RxProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueRx<V>(queue), RTransferQueueRx.class);
    }

    @Override
    public String getId() {
        return commandExecutor.getServiceManager().getId();
    }
    
}
