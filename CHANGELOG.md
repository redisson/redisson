Redisson Releases History
================================

Upgrade to __[Redisson PRO](https://redisson.pro)__ with **advanced features**.

### 23-Dec-2024 - 3.41.0 released

Feature - `RPermitExpirableSemaphore.getLeaseTime()` method added (thanks to @seakider)  
Feature - `sslVerificationMode` setting added  

Fixed - `RPermitExpirableSemaphore.release(java.util.List)` shouldn't release permits if one of them doesn't exist (thanks to @seakider)  
Fixed - `RTopic` listeners leak if they are defined as a lambda  
Fixed - `RPriorityBlockingQueue.draintTo()` method shouldn't resend command on response timeout  
Fixed - `RandomXoshiro256PlusPlus` might block Redisson initialization  

### 12-Dec-2024 - 3.40.2 released

Improvement - optimization LRUCacheMap speed by up to 200x  

Fixed - Quarkus config parsing with sentinel nodes (thanks to @blacksails)  
Fixed - starvation of pub/sub connections may cause a memory leak  

### 06-Dec-2024 - 3.40.1 released

Improvement - Netty pending tasks amount report in timeout exceptions  

Fixed - Redis or Valkey hostname parsing  
Fixed - `NoClassDefFoundError` is thrown during run with Spring Boot 3.4.0 in GraalVM native mode  
Fixed - `RTopic` and `RShardedTopic` fail to resubscribe after node's DNS record change (regression since 3.27.0)  

### 03-Dec-2024 - 3.40.0 released

Feature - metrics added to `RClusteredTopic`, `RReliableTopic`, `RClusteredReliableTopic`, `RShardedTopic`, `RJsonStore` and `RLocalCachedJsonStore` objects  
Feature - Spring Data Redis 3.4.x module added  
Feature - Spring Boot upgraded to 3.4.0  
Feature - `setIfLess()` and `setIfGreater()` methods added to `RAtomicDouble` and `RAtomicLong` objects (thanks to @seakider)  
Feature - `RMultimap.fastReplaceValues()` method added (thanks to @davidreis97)  

Fixed - IPV6 uris with braces are parsed incorrectly
Fixed - minCleanUpDelay setting isn't applied during the first run of the EvictionTask  
Fixed - master node shouldn't be changed on new ip addition for AWS Serverless endpoint  
Fixed - Quarkus native build requires Snappy library  
Fixed - Quarkus 3.14+ compatibility  
Fixed - `RBitSet.length()` method returns unexpected value and caused RedisException in some case (thanks to @seakider)  
Fixed - `TypedJsonJacksonCodec` doesn't catch Exception (thanks to @lyrric)  

### 15-Nov-2024 - 3.39.0 released

Feature - [partitioning](https://redisson.org/docs/data-and-services/publish-subscribe/#partitioning) implementation for `RTopic` object  
Feature - [partitioning](https://redisson.org/docs/data-and-services/publish-subscribe/#partitioning_1) implementation for `RShardedTopic` object  
Feature - [partitioning](https://redisson.org/docs/data-and-services/publish-subscribe/#partitioning_2) implementation for `RReliableTopic` object  
Feature - [ZStandard](https://github.com/facebook/zstd) compression codec added (ZStdCodec)  
Feature - ability to register [listeners](https://redisson.org/docs/data-and-services/collections/#listeners_1) for `RListMultimap` and `RSetMultimap` objects  
Feature - ability to register [listeners](https://redisson.org/docs/data-and-services/collections/#listeners_10) for `RTimeSeries` object  

Fixed - possible race-condition in `RMapCache.computeIfAbsent()` method  
Fixed - possible race-condition in `computeIfAbsent()`, `computeIfPresent()` methods of RMap object  
Fixed - `RMapCache.computeIfAbsentAsync()` method isn't implemented  
Fixed - use CursorId in ScanIteration to avoid long overflow in Spring Data 3.3 implementation (thanks to @vlastikcz)  
Fixed - unable to cancel a task created by `RExecutorService.scheduleAtFixedRate()` method (thanks to @zcxsythenew)  
Fixed - potential LocalCache memory leak if useObjectAsCacheKey = true (thanks to @lehuuthanh5)  
Fixed - EntryListener is not working on Turkish language Windows 10  
Fixed - Redisson shutdown exception is thrown during background process of expired Tomcat sessions  
Fixed - some methods of Reactive and RxJava API don't work in GraalVM native image  
Fixed - `RTransactionRx` and `RTransactionReactive` don't work in GraalVM native image  
Fixed - `JsonJacksonCodec` doesn't work in GraalVM native image  
Fixed - NPE is thrown if `RExecutorService` task submitted in GraalVM native image  
Fixed - `RObject.rename()` method does not replace an existing structure in cluster mode  

### 31-Oct-2024 - 3.38.1 released

Fixed - Kryo codec upgraded to 5.6.2 for JDK 8 compatibility (thanks to @Wujiaxuan007)  
Fixed - pollAsync() and removeAsync() methods of RPriorityQueue and RPriorityDeque objects aren't guarded properly with lock  
Fixed - Spring Cache `Cacheable(sync)` annotation loads value multiple times for reactive types or completableFuture

### 30-Oct-2024 - 3.38.0 released

Feature - [Local cache for Live Object Service](https://redisson.org/docs/data-and-services/services/#local-cache) support  
Feature - [RClientSideCaching](https://redisson.org/docs/client-side-caching/) object added. Client side caching through RESP3 protocol  
Feature - Tomcat 11 support  
Feature - `RBatch.getSearch()` method added (thanks to @pfyod)  
Feature - `RedissonClient.getMultiLock()` method added for locking on objects (thanks to @lyrric)  
Feature - `RPatternTopic.removeListener()` accepts multiple ids  
Feature - `LocalCachedMapOptions.useTopicPattern()` setting added  
Feature - InetAddress and SocketAddress serialization added to Kryo5Codec

__Breaking change - RLongAdder and RDoubleAdder topic channel name has been renamed__

Improvement - timeToLive parameter renamed to keepAliveTime for RRateLimiter.`trySetRate()` and `setRate()` methods (thanks to @lyrric)  
Improvement - Add check, The parameter timeToLive should be greater than or equal to rateInterval (thanks to @lyrric)  

Fixed - `lazyInitialization=true` doesn't work in cluster mode (regression since 3.27.0)  
Fixed - Spring Cache @Cacheable(sync) doesn't work with reactive types or completableFuture  
Fixed - Pub/Sub connections randomly disconnecting (regression since 3.26.0) (thanks to @Wujiaxuan007)  
Fixed - `RLiveObjectService.persist()` and `merge()` methods, when called with multiple arguments, return detached objects  
Fixed - `RJsonBucketReactive` and `RJsonBucketRx` use Reactive Types as arguments  
Fixed - `ClassNotFoundException` is thrown when a LiveObject expires  
Fixed - Micronaut native image build  
Fixed - Quarkus native image build  
Fixed - `RSearch.info()` method throws an exception on infinity values (thanks to @iamtakingiteasy)  

### 02-Oct-2024 - 3.37.0 released

Feature - `findCommon()` and `findCommonLength()` methods added to `RBucket` object  
Feature - `RMapCache.computeIfAbsent()` method with TTL parameter added (thanks to @lyrric)  
Feature - Apache Tomcat `RedissonSessionManager.setConfig()` method added (thanks to @jglapa)  
Feature - `LocalCachedMapOptions.useObjectAsCacheKey()` setting added (thanks to @lehuuthanh5)  
Feature - `trySetRate()` and `setRate()` methods with TTL parameter added to `RRateLimiter` object  
Feature - `RKeys.getKeys()` method with type parameter added  

Improvement - `RRemoteService` method calls optimization  

Fixed - Spring Data Redis method `RedisSetCommands.isMember()` doesn't work  
Fixed - Spring Data Redis `xcaim()` and `xClaimJustId()` methods don't use getMinIdleTime() parameter (thanks to @jinia91)  
Fixed - `retainAll()` and `containsAll()` methods of `RSet` object throw "too many results to unpack" error  
Fixed - `ServiceManager.execute()` method may hang in case of exception  
Fixed - `RedissonNode.shutdown()` method doesn't stop executors  
Fixed - listeners reattach process should be stopped on Redisson shutdown  
Fixed - `BiHashMap` usage removed  
Fixed - 100% CPU usage by CommandsQueue in rare cases  
Fixed - `ProtobufCodec` doesn't work with `CompositeCodec`  

### 09-Sep-2024 - 3.36.0 released

Feature - `Kryo5Codec` `useReferences` setting added  
Feature - `RListMultimapCacheNative` and `RSetMultimapCacheNative` objects added. Require Redis 7.4+  
Feature - `AggregationOptions.sortBy()` method with `withCount` parameter added (thanks to @pfyod)  
Feature - `allowedClasses` setting added to `FuryCodec`  
Feature - `addIfAbsent(Map)` method added to `RSetCache` object (thanks to @lyrric)  

Improvement - 'hmget' should be instead of 'hget' in `RMapCache.getAllWithTTLOnly()` method  

Fixed - RedisExecutor throws "Failed to submit a listener notification task" error during shutdown  
Fixed - Keep the jmockit version in the plugin consistent with that in the dependencies (thanks to @lyrric)  
Fixed - hostname after comma in Redis Cluster topology isn't parsed  
Fixed - `drainToAsync()` method returns an incorrect value (thanks to @seakider)  
Fixed - numeric cast in `CommandDecoder`  
Fixed - `RLiveObject` value shouldn't be deleted during index update  
Fixed - `RSetCache.addAllIfAbsent()` method doesn't work  
Fixed - missed `getSetMultimapCache()` and `getListMultimapCache()` methods in `RBatchReactive` object  
Fixed - missed `getMapCacheNative()` method in `RBatch` object  
Fixed - `MapValueDecoder` throws NPE  
Fixed - `EnumMap` type handling by `Kryo5Codec`  
Fixed - `Kryo5Codec` `registrationRequired` setting replaced with `allowedClasses`  
Fixed - JCache eviction task isn't removed on `close()` method invocation  
Fixed - missed `destroy()` method for `RListMultimapCache` and `RSetMultimapCache` objects  

### 22-Aug-2024 - 3.35.0 released

Feature - `INDEXEMPTY` option added to tag and text fields in `RSearch.createIndex()` method  
Feature - `INDEXMISSING` option added to all fields in `RSearch.createIndex()` method  
Feature - `StreamMessageId.LAST` option added  
Feature - `copy()` and `copyAndReplace()` methods added to `RObject` interface  
Feature - [Apache Fury](https://github.com/apache/fury) codec support  
Feature - `RSetCache` object supports `TrackingListener`, `SetAddListener` and `SetRemoveListener` listeners  
Feature - `RClusteredMapCacheNative` object implemented. Requires Redis 7.4+  
Feature - `RLocalCachedMapCacheNative` object implemented. Requires Redis 7.4+  
Feature - `localcache_native` and `clustered_native` implementations added to Quarkus module. Requires Redis 7.4+  
Feature - `RedissonClusteredCacheNative` and `RedissonLocalCachedCacheNative` implementations added to MyBatis module. Requires Redis 7.4+
Feature - `RedissonClusteredSpringCacheNativeManager` and `RedissonSpringLocalCachedCacheNativeManager` implementations added to Spring Cache module. Requires Redis 7.4+  
Feature - `RedissonClusteredNativeRegionFactory` and `RedissonLocalCachedNativeRegionFactory` added to Hibernate module. Requires Redis 7.4+  
Feature - `local-caches-native` and `clustered-caches-native` implementations added to Micronaut module. Requires Redis 7.4+  

Improvement - `ProtobufCodec` memory allocation optimization  
Improvement - [Apache Fury](https://github.com/apache/fury) codec optimization (thanks to @chaokunyang)  
Improvement - quarkus should make an attempt to read config file using Thread's ContextClassLoader  
Improvement - quarkus should make an attempt to read config file using Thread's ContextClassLoader (thanks to @seakider)  
Improvement - don't take lock for `RMap.computeIfAbsent()` if only get is needed (thanks to @shreyas-sprinklr)  

Fixed - writer, writeMode, writerAsync, writeBehindDelay, writeBehindBatchSize, loader, loaderAsync settings aren't applied to caches-native in Micronaut module  
Fixed - missed `caches-native` implementation for Micronaut 3.x and Micronaut 2.x  
Fixed - a new retry attempt to the same node isn't made for INFO_REPLICATION, SENTINEL_GET_MASTER_ADDR_BY_NAME, SENTINEL_SENTINELS, SENTINEL_SLAVES and CLUSTER_NODES commands  
Fixed - `RType.JSON` and `RType.STREAM` can't be resolved by `RKey.getType()` method  
Fixed - `RKeys.removeListener()` method doesn't remove `NewObjectListener` and `SetObjectListener`  
Fixed - `copy()` method doesn't works with db (thanks to @seakider)  
Fixed - `maven.compiler.release` setting isn't defined  
Fixed - `RSearch.info()` method throws `NumberFormatException` (thanks to @iamtakingiteasy)  
Fixed - timeout parameters defined per object aren't applied to `RJsonBuckets` and `RJsonBucket` objects  
Fixed - RedisException is thrown by `.removeAll()` and `.indexOf()` methods of `RedissonSubList` object (thanks to @seakider)  
Fixed - wrong event keyspace name for `MapCacheNative` object (thanks to @larryTheCoder)  
Fixed - missed `rename()` and `renamenx()` methods implementation for `RIdGenerator`, `RMapCache` and `RTimeSeries` objects  
Fixed - `Kryo5Codec` doesn't handle `UnmodifiableCollection`, `SynchronizedCollection` and `CheckedCollection`  
Fixed - `RRateLimiter` incorrect rate count in the event of an attempt to exceed the limit  
Fixed - `credentials-resolver`, `failed-slave-node-detector`, `command-mapper`, `name-mapper`, `nat-mapper` settings aren't recognized by Helidon and Quarkus  
Fixed - `RMultimapCacheReactive.expireKey()` method returns Single instead of Reactor Mono  
Fixed - `@RObjectField` annotation with codec option has no effect  
Fixed - an exception is thrown if the `@RObjectField` annotation is defined on a field  
Fixed - `RDestroyable.destroy()` method doesn't remove listeners  
Fixed - FailedSlaveNodeDetector's parameters by can't be defined in YAML config  


### 31-Jul-2024 - 3.34.1 released

Fixed - RObject.rename() method doesn't work in cluster

### 30-Jul-2024 - 3.34.0 released

Feature - RJsonBuckets object added (thanks to @seakider)  
Feature - `remainTimeToLive(Set keys)` and `clearExpire(Set keys)` methods added to `RMapCacheNative` object  
Feature - `eval()` and `evalSha()` methods added to `RScript` object for execution on all nodes in Redis Cluster  

Improvement - performance optimization for Apache Tomcat Session management  
Improvement - default value of quietPeriod set to 0 in `RedissonClient.shutdown()` method  
Improvement - upgrade protobuf from 3.16.3 to 4.27.2 (thanks to @zzhlhc)  

Fixed - Apache Tomcat Session Manager throws CROSSSLOT Keys in request don't hash to the same slot (regression since 3.32.0)  
Fixed - empty partitions shouldn't be skipped in cluster topology scan  
Fixed - `ClusterConnectionManager.checkSlotsMigration()` method throws NPE  
Fixed - RSearch IndexInfoDecoder may throw NPE  
Fixed - local cached Map implementations don't emit cache clear event on `clear()` or `delete()` methods call  
Fixed - `RObject.rename()` method doesn't work in cluster  
Fixed - a new attempt to send a command should be made right after channel reconnection  
Fixed - 'race condition' while calling RLock.tryLock() method (thanks to @pad-master82)  
Fixed - Can't connect to servers error doesn't show exception reported during connection  
Fixed - `SeekableByteChannel.truncate()` method (thanks to @seakider)  
Fixed - `RMap.computeAsync()` method doesn't handle empty keys properly (thanks to @phrone)  

### 17-Jul-2024 - 3.33.0 released

Feature - [RJsonStore](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#724-json-store) object added  
Feature - [RLocalCachedJsonStore](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#7241-json-store-local-cache) object added  
Feature - `RSearch.getIndexes()` method added  
Feature - `RedissonRegionNativeFactory` added for Hibernate module. Requires Redis 7.4+  
Feature - `RedissonSpringCacheNativeManager` implemented. Requires Redis 7.4+  
Feature - `RedissonCacheNative` implementation added for Mybatis cache. Requires Redis 7.4+  
Feature - `caches-native` cache type added to Micronaut module. Requires Redis 7.4+    

Improvement - Spring Cache `RedissonCache.put()` method optimization  

__Breaking change - JsonCodec interface refactoring__

Fixed - re-added master node isn't updated in Cluster topology  
Fixed - expiration time isn't set for Apacht Tomcat Manager `redisson:tomcat_notified_nodes` keys (thanks to @ehardy)  
Fixed - RSearch `AggregationOptions.loadAll()` setting isn't applied  
Fixed - containsAll() method of RList, RQueue, RMultimap objects returns incorrect result (thanks to @seakider)  
Fixed - too many requests with small threads amount may cause OOM  
Fixed - connection leak during high load with few connections  
Fixed - RingBuffer#setCapacity, trim list incorrect (thanks to @seakider)  
Fixed - password shouldn't be printed in logs  
Fixed - encoded user/password in URL aren't accepted  

### 24-Jun-2024 - 3.32.0 released

Feature - Quarkus Cache implementation added  
Feature - `RMapCacheNative` implementation which uses Redis 7.4+ commands  
Feature - unlinkByPattern() method added to RKeys interface (thanks to @seakider)  

Fixed - Apache Tomcat request.changeSessionId() method does not change the session id in Redis (thanks to @bclay2116)  
Fixed - RSearch parse params error (thanks to @seakider)  
Fixed - RSearch.info() throws `NumberFormatException` (thanks to @seakider)  
Fixed - cluster failover handling  
Fixed - last master node shouldn't be removed in cluster topology  
Fixed - duplicated master/slave added log output in sentinel mode  
Fixed - connection leak if master change operation failed during failover  
Fixed - `RxJava3` request can't be canceled  
Fixed - `RFairLock` doesn't calculate remaining ttl properly before next acquisition attempt  
Fixed - `scanInterval` setting check  
Fixed - `ElementsSubscribeService.resubscribe()` method throws `RedissonShutdownException`  
Fixed - `RMap.getAll()` method throws an exception if result of `RMap.keySet()` method passed as an argument  
Fixed - Redis node random selection doesn't work properly  
Fixed - concurrency problem during RLock expiration renewal (thanks to @seakider)  
Fixed - `RListMultimap` throws too many results to unpack error  
Fixed - entry shutdown during cluster slots migration check  

### 31-May-2024 - 3.31.0 released

Feature - Spring Boot 3.3.0 integration  
Feature - Spring Data Redis 3.3.0 integration  
Feature - allow retry of `NOREPLICAS` error (thanks to @ghollies)  

Improvement - `SequentialDnsAddressResolverFactory` default `concurrencyLevel` set to 2  
Improvement - `ThreadLocalRandom` replaced with `xoshiro256++` RNG to avoid collisions  

Fixed - cluster failover handling  
Fixed - cluster topology scan shouldn't be stopped by any exception  
Fixed - `RSetMultiMap` throws `too many results to unpack` error  
Fixed - append commands error when using batch mode (thanks to @seakider)  
Fixed - `ERR unknown command EVALSHA_RO` error shouldn't be logged  
Fixed - `TransactionalBucket#set(V, Duration)` `PSETEX` command is called before `MULTI` command (thanks to @seakider)  
Fixed - `CommandMapper` isn't applied to Lua scripts  
Fixed - incorrect connection release if `BatchOptions.executionMode` = `REDIS_WRITE_ATOMIC` or `REDIS_READ_ATOMIC` (thanks to @seakider)  
Fixed - `RFairLock` methods throw 'attempt to compare nil with number' error  
Fixed - Spring Data Redis `RedissonConnectionFactory.getSentinelConnection()` method throws error on the first offline sentinel  
Fixed - read mode = SLAVE isn't applied for `RSet.random()` methods  
Fixed - Keyspace notifications should be listened only on master nodes  
Fixed - `RListMultimap.removeAll()` method always deletes link to list  
Fixed - `RLockReactive` methods don't work in native image  
Fixed - Correctly update shutdown timeout after each step in connection manager shutdown (thanks to @MartinEkInsurely)  
Fixed - broken tck `JCache` tests  
Fixed - not all sentinels defined in the configuration are registered  

### 10-May-2024 - 3.30.0 released
Feature - `sslKeystoreType` setting added  
Feature - `RPatternTopic.getActiveTopic()` method added (thanks to @MasterShi)  
Feature - `RJsonBucket.merge()` method added  
Feature - Async, Rx, Reactive interfaces implemented for `RBloomFilter` object  
Feature - fallback mode for JCache  
Feature - [passwords encryption](https://github.com/redisson/redisson/wiki/2.-Configuration#223-passwords-encryption) support  
Feature - [Spring Cloud Stream](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#149-spring-cloud-stream) integration  

Improvement - configuration variable defined as Java system property overrides environment variable  

Fixed - io.projectreactor package should be defined as optional in OSGi Manifest  
Fixed - Spring Data Redis `StreamPollTask.deserializeAndEmitRecords()` method throws NPE after failover  
Fixed - Spring Data Redis blocking poll commands can't be reattached after failover  
Fixed - Unable to find session error reported by `RedissonSessionManager`  
Fixed - Sentinels discovery is applied at Redisson startup if `sentinelsDiscovery = true`  
Fixed - master node is used for read operations after slave node addition if `readMode = SLAVE` in replicated mode  
Fixed - failover handling of blocking methods calls with defined timeout. RStream, RBlockingQueue objects  
Fixed - multiple `RLocalCachedMap` objects don't work in the same RTransaction (thanks to @vlad-ogol @RajaJaisankar)  
Fixed - codec setting isn't applied in `redisson.getMap(MapOptions)` method  
Fixed - Live Object field can't set to null value  
Fixed - `SentinelConnectionManager` stops scheduling topology change change / dns check after host resolution error  
Fixed - `RMapCache.fastPutIfExistsOperation()` method uses incorrect pubsub channel

### 22-Apr-2024 - 3.29.0 released
Feature - `NewObjectListener` added to track created objects  
Feature - `NewObjectListener` and `SetObjectListener` can be registered with `RKeys.addListener()` method  
Feature - `subscribeOnElements()`, `subscribeOnLastElements()` and `subscribeOnFirstElements()` methods wait for `CompletionStage` to complete before polling the next element  
Feature - `shardedSubscriptionMode` setting added in Cluster configuration  
Feature - `RSemaphore.trySetPermits()` method with ttl parameter added  
Feature - `getDeletedIds()` method added to `RStream` `AutoClaimResult` object  

Improvement - responses map lock replaced with fine-grained entry locking in `RRemoteService` and `RScheduledExecutorService`  

Fixed - `RStream.autoClaim()` method throws ClassCastException  
Fixed - `RSearch` aggregate expression applied incorrectly  
Fixed - `LocalCachedMapDisabledKey` event is parsed incorrectly if local cache used with `RTransaction`  
Fixed - Slave node in cluster mode isn't shutdown properly if `readMode = MASTER` and `subscribeMode = MASTER` (regression since 3.27.2)  
Fixed - race condition during cluster topology update causes slave added/removed events  
Fixed - OSGi MANIFEST should define optional dependencies  
Fixed - `TimeoutException` is thrown if `connectionMinimumIdleSize = 0`  
Fixed - `ClassCastException` is thrown for Reactive/Rx RemoteService invocation if Redisson instance isn't Reactive/Rx  
Fixed - semaphore object is not deleted after `RLocalCachedMap.clearLocalCache()` method invocation  
Fixed - `AggregationOptions.groupBy()` setting with reducers used in `RSearch.aggregate()` method causes an exception  
Fixed - `AggregationOptions.groupBy()` setting usage with `RSearch.aggregate()` method causes an exception if reducers aren't defined  
Fixed - `AggregationOptions.sortBy()` setting usage with `RSearch.aggregate()` method causes an exception  
Fixed - resource leak error when executing multiple contains operation of `RSet` in transaction (thanks to @wynn5a)  
Fixed - jmockit upgraded to 1.52.0 inside maven-surefire-plugin (thanks to @roharon)  

### 10-Apr-2024 - 3.28.0 released

Feature - [Multi Sentinel mode](https://github.com/redisson/redisson/wiki/2.-Configuration/#211-multi-sentinel-mode) implementation  
Feature - `RLocalCachedMapCacheV2` object implemented with effecient partitioning and advanced entry eviction  
Feature - graceful shutdown in quarkus (thanks to @naah69)  

Improvement - `RLongAdder` and `RDoubleAddder` should use sharded topic if possible  
Improvement - reduced CPU and Memory consumption by `ClusterConnectionManager.getLastPartitonsByURI()` method  
Improvement - `RedisURI.hashCode()` caching to reduce CPU consumption  
Improvement - shutdown check added in `RTopic.removeListener()` method  

Fixed - incorrect detection of sharded pubsub support  
Fixed - `RBatch` does not work with RKeys.randomKeyAsync() method (thanks to @wynn5a)  
Fixed - unresolved Redis node hostname in cluster mode affects cluster topology scan  
Fixed - `MASTER` nodes aren't used if `readMode = MASTER_SLAVE`  
Fixed - `RLock`, `RFencedLock`, `RReadWriteLock` miss unlock messages and wait a defined timeout before a next attempt or hang  
Fixed - `RSemaphore`, `RPermitExpirableSemaphore` miss release messages and wait a defined timeout before a next attempt or hang  
Fixed - incorrect value of `RLongAdder.sum()` and `RDoubleAddder.sum()` methods if multiple Adder instances for the same Redisson object are used  
Fixed - `CountDownLatch.await()` method may throw NPE  
Fixed - ExecutionException handling in RExecutorService, RLock, RPermitExpirableSemaphore, RSemaphore objects  
Fixed - `ConcurrentModificationException` is thrown on RedissonSession save method if readMode = MEMORY  
Fixed - Spring Data Redis zPopMin() and zPopMax() methods don't work (thanks to @bimslab)  

### 12-Mar-2024 - 3.27.2 released

Feature - `RShardedTopic.countSubscribers()` method implemented  
Feature - `RedissonMultiLock` implements `isHeldByThread()` and `isHeldByCurrentThread()` methods  

Fixed - Multiple locking of RLock reset remainTimeToLive to config default  
Fixed - exception thrown by `natMapper` is not shown in logs  
Fixed - OSGi jdk.net package import should be optional  
Fixed - `ServiceManager.resolveAll()` method throws NPE  
Fixed - `RObject.addListenerAsync()` method throws `UnsupportedOperationException`  
Fixed - `StatusListener` doesn't work with `RShardedTopic`  
Fixed - NPE is thrown in cluster mode if slave node added  
Fixed - continuously reconnecting to a removed slave node in cluster mode  
Fixed - incorrect handling of TrackingListener removal  
Fixed - `FlushListener` receives duplicate events  
Fixed - `SlotsDecoder` throws NPE on empty result  
Fixed - Clash between RedissonCache and Spring (6.1+) Cache interface methods  
Fixed - `RedissonClient.shutdown()` method hangs at `serviceManager.getShutdownLatch()` invocation  
Fixed - "Failed to submit a listener notification task. Event loop shut down?" error caused by `PingConnectionHandler`  
Fixed - `JsonCodecWrapper` isn't cached properly  

### 28-Feb-2024 - 3.27.1 released

Feature - added `TrackingListener` support to `RList`, `RQueue`, `RDeque`, `RBlockingQueue`, `RBlockingDeque`, `RDelayedQueue`, `RRingBuffer` objects  
Feature - `addListener()`, `random()` methods added to `RLexSortedSet` object  

Improvement - show log warning "DNS TCP fallback on UDP query timeout disabled" if Netty version is lower 4.1.105  
Improvement - ChannelName.toString() conversion optimization  

Fixed - `retryInterval` and `retryAttempts` settings aren't applied in case of 'READONLY You can't write against a read only replica.' error  
Fixed - `RRemoteService` may cause CPU spike after Master failover  
Fixed - `FlushListener` causes `ClassCastException`  
Fixed - `TrackingListener` causes `ClassCastException`  
Fixed - `RedissonSetCache.addIfAbsentAsync()` uses incorrect argument for zadd (thanks @fooooxxxx)  

### 20-Feb-2024 - 3.27.0 released

Feature - [client tracking](https://github.com/redisson/redisson/wiki/10.-additional-features/#109-client-tracking-listener) support. `TrackingListener` is available for `RBucket`, `RStream`, `RScoredSortedSet`, `RSet`, `RMap` and `RBucket` objects  
Feature - added `RKeys.addListener()` method to register global listeners  
Feature - `FlushListener` added to track flushdb/flushall command invocation  
Feature - `Kryo5Codec` constructor with `registrationRequired` parameter added  
Feature - `nettyExecutor` setting added  
Feature - enable DNS TCP fallback when UDP query timeout for `RoundRobinDnsAddressResolverGroupFactory` and `SequentialDnsAddressResolverFactory`  

Improvement - cache result of `INFO REPLICATION` command for `RLock` objects  

Fixed - Spring Data Redis `ReactiveKeyCommands.pExpire()` method throws an exception  
Fixed - NPE is thrown by `RedisExecutor.handleError()` method  
Fixed - sharded pubsub detection for `Apache Tomcat Session Manager`, `RMapCache` and `RLocalCachedMap` objects  
Fixed - Redisson's threads aren't shutdown if Redis node address isn't defined  
Fixed - NPE is thrown while creating `RLocalCacheMap` object without WriteMode value  
Fixed - incorrect RESP3 protocol parsing causes `SlaveConnectionPool no available Redis entries` error  
Fixed - repeated new connections with AWS Elasticache serverless  
Fixed - internal `LRUCacheMap` object throws `ConcurrentModificationException`  

### 12-Feb-2024 - 3.26.1 released

Feature - enable DNS TCP fallback when UDP query timeout (thanks to @hellojukay)  
Feature - `StreamMessageId.autogenerateSequenceId()` method added (thanks to @mrmx)  
Feature - `RLockReactive.isHeldByThread()` method added (thanks to @sanail)  

Fixed - missed implementation of Spring Data Redis `ReactiveStringCommands.bitField()` method  
Fixed - Spring Data Redis `opsForCluster().randomKey()` method throws `UnsupportedOperationException`  
Fixed - `JCache.close()` method throws `IllegalStateException` if statistics enabled  
Fixed - doubled connections to the master node if `readMode = MASTER_SLAVE` or there are no slave nodes  
Fixed - `RSearch.delDict()` and `RSearch.addDict()` methods throw NPE  
Fixed - connection ping handler doesn't use `commandTimeout` setting  
Fixed - repeated new connections with AWS Elasticache serverless  
Fixed - `RLock` throws `ERR unknown command 'wait'` with AWS Elasticache serverless  
Fixed - `RSearchReactive.dropIndex()` method doesn't call onComplete() handler  


### 16-Jan-2024 - 3.26.0 released

Feature - ability to specify `retryInterval`, `retryAttempts`, `timeout` settings per Redisson object. Please refer to the [documentation](https://github.com/redisson/redisson/wiki/3.-operations-execution)  
Feature - `LocalCachedMapOptions.expirationEventPolicy` setting added  
Feature - `StreamAddListener`, `StreamCreateConsumerListener`, `StreamCreateGroupListener`, `StreamRemoveConsumerListener`, `StreamRemoveGroupListener`, `StreamRemoveListener`, `StreamTrimListener` listeners added for `RStream` object  

Fixed - Spring Data Redis `RedissonConnection.setCommands()` method returns null  
Fixed - continuously reconnecting to a removed slave node in cluster mode  
Fixed - `EntryExpiredListener` isn't invoked by `RMapCache` instance in Redis Cluster 7+ and if `nameMapper` is defined  
Fixed - `Skipped slave up ...` error is thrown in Sentinel mode if nodes use IPv6  
Fixed - NPE is thrown when adding or removing shards in ElastiCache  
Fixed - `RAtomicDouble`, `RAtomicLong`, `RMap`, `RScoredSortedSet`, `RSet` listeners aren't removed properly  
Fixed - connection isn't reconnected on `WRONGPASS` Redis error  
Fixed - connection timeout during Redisson start ignores connections amount  
Fixed - `RSearch.search()` method doesn't execute query with aliases properly  
Fixed - `FCALL_RO` command isn't used when `RFunction` method called with `FunctionMode.READ` parameter  
Fixed - `IllegalReferenceCountException` is thrown when canceling a method call  
Fixed - Redis scan cursor exceed `Long.MAX_VALUE` in AWS Elasticache  
Fixed - internal `ServiceManager.calcSHA()` method should use UTF-8 encoding by default  

### 20-Dec-2023 - 3.25.2 released

Fixed - SSL connection can't be established  

### 18-Dec-2023 - 3.25.1 released

Improvement - JDK21 Virtual Threads compatibility  

Fixed - EvictionTask keeps running even after `destroy()` method called  
Fixed - Sprint Data Redis throws `Subscription registration timeout exceeded`  
Fixed - Sprint Data Redis `RedisMessageListenerContainer.addMessageListener()` method hangs if called after container start  
Fixed - NPE is thrown if `lazyInitialization = true`  
Fixed - `PriorityQueue` methods may hang due to unreleased lock after exception  
Fixed - `RMap.getAll()` method throws `IndexOutOfBoundsException`  
Fixed - natMapper isn't applied to slaves and master nodes at start in Sentinel mode  
Fixed - method invocation hangs after failover if `retryInterval = 0`  
Fixed - transactional Map and MapCache keySet method returns inconsistent state  
Fixed - Multilock lock method doesn't work properly with non-MILLISECONDS TimeUnit  

### 05-Dec-2023 - 3.25.0 released
Feature - RESP3 protocol support. [protocol](https://github.com/redisson/redisson/wiki/2.-Configuration#protocol) setting added  
Feature - Spring Data Redis 3.2.0 implementation  
Feature - `RSetCacheV2`, `RSetV2` objects with more effecient partitioning in Cluster added in [PRO](https://redisson.pro) version  

Improvement - SLF4j updated to 2.0.9 version  

Fixed - RFunction/RScript keys parameter accepts only String values  
Fixed - IP address instead of hostname is used in sentinel mode with SSL connection  
Fixed - Resources leak in `Version.logVersion()` method  
Fixed - `RLiveObjectService.persist()` method with varargs hangs in cluster mode  
Fixed - Redisson connection process may hang at start in some cases  
Fixed - `EntryExpiredListener` isn't invoked by RMapCache instance in Redis Cluster 7+  
Fixed - slave node in sentinel mode can't be recovered if it's a master node  
Fixed - `JsonJacksonCodec` fails to serialize Throwable on Java17 (thanks to @tomjankes)  
Fixed - `RBlockingDeque.move()` throws an exception for empty result  
Fixed - `RScoredSortedSet.pollFirstEntries(count)` and `pollLastEntries(count)` methods return wrong result  
Fixed - `BZMPOP` command timeout isn't applied  
Fixed - `getBlockingDeque()`, `getDeque()`, `getPriorityDeque()`, `getPriorityBlockingDeque()` throw `NoClassDefFoundError` if JDK version < 21  
Fixed - `RLocalCachedMap.containsKey()` method does not work properly if `storeCacheMiss = true`  
Fixed - `RedissonRemoteService` exceptions handling (thanks to @mrmx)  
Fixed - `RSearch.info()` method throws `NumberFormatException`  
Fixed - `HttpSessionListener.sessionDestroyed()` method isn't called if Tomcat Session deleted by the node which didn't create it  
Fixed - `LZ4CodecV2` isn't compatible with `LZ4Codec`  
Fixed - `RSearch` GroupBy.reduce alias isn't applied (thanks to @arjunE1395)  

### 24-Oct-2023 - 3.24.3 released

Feature - Helidon 4.0 integration

Fixed - `ERR invalid expire time` error is thrown during `RLock.unlock()` call if `retryAttempts = 0`  

### 22-Oct-2023 - 3.24.2 released

Fixed - `IllegalArgument timeout...` error is throw during Redisson shutdown  
Fixed - Intermittent Exception when creating `RLocalCachedMap` (regression since 3.24.1)  
Fixed - `RSearch.aggregate()` doesn't apply `withCursor()` and `sortBy()` options correctly  
Fixed - `MOVED redirection loop detected` error should be thrown only if both source and target addresses are equal

### 18-Oct-2023 - 3.24.1 released

Feature - `writerRetryAttempts` and `writerRetryInterval` settings added to `MapOptions` object (thanks to @zzhlhc)  
Feature - `RSortedSet` implements `RExpirable`  
Feature - `RBlockingQueue.pollFromAnyWithName()` method added  
Feature - `org.redisson.codec.LZ4CodecV2` codec based on apache commons-compress added  
Feature - Redis Cache async methods implementation introduced in Spring 6.1.0  
Feature - `tcpKeepAliveCount`, `tcpKeepAliveIdle`, `tcpKeepAliveInterval`, `tcpUserTimeout` settings added  
Feature - `subscriptionTimeout` setting added  

Fixed - `RedissonClient.shutdown()` method should be completed within timeout (thanks @dgolombek)  
Fixed - `RBuckets.trySet()`, `RBuckets.set()`, `RBuckets.get()`, `RKeys.touch()`, `RKeys.unlink()`, `RKeys.delete()`, `RKeys.countExists()` methods may hang after failover in Redis cluster  
Fixed - exceptions aren't wrapped in `CacheException` for `containsKey()`, `getAll()` and `removeAll()` methods of `JCache`  
Fixed - Command execution timeout for command: (PING)  
Fixed - `RBucketReactive.delete()` method doesn't work in Quarkus Native mode (thanks to @DicksengA)  
Fixed - auto configuration with Spring Boot 2.7.x+  
Fixed - `RSortedSet` doesn't work correctly if `NameMapper` object was specified  
Fixed - `RPriorityQueue` has incorrect lock name if `NameMapper` object was specified  
Fixed - `RMapCache.expireEntries()` and `expireEntry()` methods don't update `maxIdle` parameter properly  
Fixed - non-volatile `RedisConnection.lastUsageTime` field may cause incorrect idle time calculation  
Fixed - `attempt to unlock lock, not locked by current thread` error occurs in rare cases even if `RLock.unlock()` method called by lock owner thread  
Fixed - `RCountDownLatch` only notifying the first async listener after countdown reaches 0 (thanks to @Sinbios)  
Fixed - `RStream.trim()` and `trimNonStrict()` methods don't work with Redis 6.2+  
Fixed - `RReadWriteLock.readLock().isLocked()` method returns incorrect result if acquired by writeLock owner thread  
Fixed - `RedissonClient.getLiveObjectService()` method causes an attempt to connect to Redis if `lazyInitialization = true`  

### 19-Sep-2023 - 3.23.5 released
Feature - `failedSlaveNodeDetector` setting added to Cluster, Sentinel, Replicated, Master/Slave modes  
Feature - module name added to redisson jar (thanks to @KrogerWalt)  
Feature - `putAll()` method with TTL added to RMapCacheRx and RMapCacheReactive objects  
Feature - Fallback to TCP in case of a UDP DNS truncation  
Feature - `RMapCacheV2`, Spring `RedissonSpringCacheV2Manager` and Hibernate `RedissonRegionV2Factory` objects with more effecient partitioning in Cluster added in [PRO](https://redisson.pro) version  

Fixed - NPE is thrown by `RedissonAutoConfiguration` if Spring Boot 3.1+  
Fixed - `WeightedRoundRobinBalancer` doesn't support hostnames  
Fixed - NPE is thrown by CommandPubSubDecoder in rare cases  
Fixed - during connection initialization a new attempt isn't made for Busy, ClusterDown, TryAgain and Wait Redis errors  
Fixed - `RJsonBucket.getType()` method throws NPE if type is null  
Fixed - `IllegalStateException` is thrown if `RedisConnectionDetails` object registered in Spring Context with settings for Cluster or Sentinel  
Fixed - `RSearch` can not create Vector field  
Fixed - `RSearch` vector field doesn't support alias  


### 29-Aug-2023 - 3.23.4 released

Feature - methods for multiple permits support added to `RPermitExpirableSemaphore` object (thanks to @ikss)  
Feature - `ProtobufCodec` codec added (thanks to @dumbbell-5kg)  
Feature - `WAITAOF` command support through `BatchOptions.syncAOF()` setting  
Feature - `bgSave()`, `scheduleBgSave()`, `save()`, `getLastSaveTime()`, `bgRewriteAOF()`, `size()` methods added to `RedisNode` object  

Improvement - `RSemaphore` and `RLock` operations should have slave synchronization even if `readMode = MASTER` and `subscriptionMode = MASTER`  

Fixed - wrong order call of RSearch's `FieldIndex` tag `caseSensitive()` and `separator()` settings  
Fixed - `RedisConnectionDetails` object isn't used for Redisson configuration in Spring Boot 3.1+  
Fixed - incorrect `slots added`,`slots removed` messages in Redis Cluster mode  
Fixed - Tomcat Manager "Session can't be found" message should have debug level  
Fixed - `RBoundedBlockingQueue` can't be deleted if `nameMapper` was defined  
Fixed - `RLock` isn't unlocked after `RTransaction.commit()`  

### 28-Jul-2023 - 3.23.3 released
Feature - `TransportMode.IO_URING` added (thanks to @sgammon)  
Feature - `LocalCachedMapOptions.useKeyEventsPattern()` setting introduced  

Improvement - Long as string cache in CommandEncoder (thanks to @tomerarazy)  
Improvement - each `AddressResolver` created by `SequentialDnsAddressResolverFactory` should share common DnsCache and DnsCnameCache instances  
Improvement - RedisURI optimization (thanks to @ikss)  

Fixed - codec errors during Quarkus native build  
Fixed - extra subscription topic allocation by `RLocalCachedMap` object (regression since 3.23.2)  

### 28-Jul-2023 - 3.23.2 released
Feature - Micronaut 4.0 integration  

Improvement - PubSub channels should be reconnected back to Slave from Master node if `SubscriptionMode = SLAVE`

Fixed - Setting `retryAttempts` to 0 causes an exception (regression since 3.23.1)  
Fixed - `RTopic` subscribes only to a single master in cluster if `__keyspace` or `__keyevent` channel is defined  
Fixed - `SlaveConnectionPool no available Redis entries` error may arise in some cases  
Fixed - StackOverflowError is thrown by `AggregationOptions.groupBy()` method  
Fixed - `failedSlaveCheckInterval` value should be greater than zero before it can be applied  
Fixed - `RedissonLocalCachedMap.putAllOperation()` method throws `ClassCastException` if `SyncStrategy = UPDATE`  

### 18-Jul-2023 - 3.23.1 released
Improvement - the scope of key event subscriptions reduced for `RLiveObjectService` object. Now it uses key-space channel  
Improvement - the scope of key event subscriptions reduced for `RLocalCachedMap` object. Now it uses key-space channel  

Fixed - codecs defined via Spring Native application.properties file can't be recognized during application run  
Fixed - `retryAttempt` setting isn't applied during Redisson startup  
Fixed - Quarkus 2/3 native image can't be built  
Fixed - unknown property `quarkus.redisson.*` warnings in quarkus  
Fixed - Redisson settings defined in Quarkus application.properties file can't be used in native mode  

### 10-Jul-2023 - 3.23.0 released
Feature - added `RBloomFilter` `contains()` and `add()` methods with element collection support  
Feature - RMapCache and RLocalCachedMap should use sharded pubsub in Redis Cluster 7.0+  
Feature - [lazyInitialization](https://github.com/redisson/redisson/wiki/2.-Configuration#lazyinitialization) setting added  
Feature - `expireEntryIfNotSet()`, `expireEntries()`, `expireEntry()`, `expireEntriesIfNotSet()` methods added to `RMapCache` object  
Feature - `MapCacheOptions` object with `removeEmptyEvictionTask()` setting introduced. Removes `RMapCache` eviction task from memory if map is empty upon entries eviction process completion  

__Breaking change - RMapCache and RLocalCachedMap should use sharded pubsub in Redis Cluster 7.0+__  
__Breaking change - RMapCache object uses MapCacheOptions object__  

Improvement - `RMapCache` shouldn't emit events if no listeners added  

Fixed - canceling tasks that scheduled with cron expression does not interrupt the thread (thanks to @zcxsythenew)  
Fixed - `RExecutorService` task response should be deleted if task was canceled  
Fixed - `RedisConnection.close()` method has private visibility  
Fixed - `ConcurrentModificationException` occasionally thrown during batch execution  
Fixed - `StringIndexOutOfBoundsException` is thrown if Redis port isn't defined in configuration  
Fixed - missed methods implementation of Spring Data Redis module: `zRevRangeByLex()`, `time(TimeUnit)`, `zRemRangeByLex()`, `zLexCount()`, `rewriteConfig()`, `zRangeStoreByLex()`, `zRangeStoreRevByLex()`, `zRangeStoreByScore()`, `zRangeStoreRevByScore()`, `flushDb()`, `flushAll()`, `replicaOf()`, `replicaOfNoOne()`
Fixed - transactional `RMap.fastRemove()` method throws `UnsupportedOperationException`  
Fixed - `RBloomFilter` `contains()` and `add()` methods don't return accurate results if false probability is high  
Fixed - incorrect handling "unknown command" response for `RTopic` operations  
Fixed - `RLiveObjectService.delete(class, id)` method doesn't delete indexes  
Fixed - `RMultimapCache` throws an exception if entry removed before expiration moment  
Fixed - `keepPubSubOrder` setting isn't applied  

### 19-Jun-2023 - 3.22.1 released
Feature - Apache Tomcat Manager should use sharded pubsub in Redis Cluster 7.0+  
Feature - Micronaut Session store should use sharded pubsub in Redis Cluster 7.0+  
Feature - RClusteredScoredSortedSet object implemented  
Feature - `maxDeletedEntryId`, `entriesAdded`, `recordedFirstEntryId` properties added to `StreamInfo` object  
Feature - `inactive` property added to `StreamConsumer` object  
Feature - `LocalCachedMapOptions.cacheSize = -1` should disable local cache storage  

__Breaking change - Apache Tomcat Manager and Micronaut Session store now use sharded PubSub in Redis Cluster 7.0+__  

Fixed - map index entry isn't deleted when `RLiveObjectService` expires  
Fixed - `RMultimap.fastRemoveValue()` method doesn't delete entry completely if no values retain  
Fixed - Default getCache method checks for Object class equality (thanks @agupta-hw)  
Fixed - `RScoredSortedSet.distributedIterator()` doesn't work (regression since 3.21.0)  
Fixed - Memory leak if `RLocalCachedMap` created with `storeMode=LOCALCACHE` and `syncStrategy=UPDATE` params  
Fixed - wait time handling in RedissonSpinLock (thanks @vladimirkl)  
Fixed - java.lang.ClassCastException is thrown by RDequeReactive.pollLast() and RDequeReactive.pollFirst() methods (thanks @wynn5a)  
Fixed - `RSearch.search()` method throws "Parameters must be specified in PARAM VALUE pairs" error  
Fixed - `RRateLimiter.setRate()` method does not behave as expected when RateType is pre_client (thanks @wynn5a)  
Fixed - collection fields aren't deleted when `RLiveObject` expires or is deleted  

### 05-Jun-2023 - 3.22.0 released
Feature - Spring Data Redis 3.1.0 implementation  
Feature - Spring Boot 3.1.0 support  
Feature - `lastEntry()`, `firstEntry()`, `pollLastEntries()`, `pollFirstEntries()`, `entryIterator()`, `rankEntry()`, `revRankEntry()` methods added to `RScoredSortedSet` object  
Feature - `RCountDownLatch`, `RLock`, `RPermitExpirableSemaphore`, `RSemaphore` objects use sharded PubSub in Redis Cluster 7.0+  
Feature - `slavesSyncTimeout`, `commandMapper`, `sslCiphers`, `sslTrustManagerFactory`, `sslKeyManagerFactory` settings added  
Feature - `RMultimap.fastRemoveValue()` method added  
Feature - `allowedClasses` setting added to `SerializationCodec`  
Feature - `entriesRead` and `makeStream` parameters added to `RStream.createGroup()` method  

__Breaking change - RCountDownLatch, RLock, RPermitExpirableSemaphore, RSemaphore objects now use sharded PubSub in Redis Cluster 7.0+__  

Fixed - Expired LiveObjects with RIndex annotations retain indexed data  
Fixed - `RRingBuffer` doesn't implement `expire()`, `expireAt()`, `delete()`, `clearExpire()` methods properly  
Fixed - `RLocalCachedMap` local cache isn't cleared after instance expiration  

### 18-May-2023 - 3.21.3 released

Fixed - default retryInterval isn't applied to RBatch object  
Fixed - RBatches no longer respect the default number of retries (regression since 3.21.1)  

### 17-May-2023 - 3.21.2 released
Feature - `RBitSet.set(long[], boolean)` method added (thanks to @skasj)  

Fixed - Pattern Topic messages are missed/duplicated after failover in cluster if channel starts with `__keyspace` and `__keyevent` and `subscriptionMode = SLAVE`  
Fixed - to many pubsub topics may cause memory leak  
Fixed - RBatches no longer respect the default number of retries (regression since 3.21.1)  

### 11-May-2023 - 3.21.1 released

Feature - `addIfAbsent()`, `addIfExists()`, `addIfGreater()`, `addIfLess()`, `addAll()`, `addAllIfAbsent()`, `addAllIfExist()`, `addAllIfGreater()`, `addAllIfLess()` methods added to `RSetCache` object  
Feature - `SetAddListener`, `SetRemoveListener`, `SetRemoveRandomListener` added to `RSet` object  
Feature - `ScoredSortedSetAddListener`, `ScoredSortedSetRemoveListener` added to `RScoredSortedSet` object  
Feature - `MapPutListener`, `MapRemoveListener` added to `RMap` object  
Feature - `IncrByListener` added to `RAtomicDouble` and `RAtomicLong` objects  
Feature - `RMapCache.getAllWithTTLOnly()` method added  

__Breaking change - RedissonDelayedQueue internal data format changed__  
__Breaking change - RedissonReliableTopic internal data format changed__  

Improvement - `RedissonReliableTopic` internal structure optimization  

Fixed - `RReliableTopic` data loss  
Fixed - failover isn't handled correctly in some cases  
Fixed - `BatchOptions.retryAttempts = 0` isn't applied to `RBatch` object  
Fixed - `RMap.keySet()` throws NPE if CompositeCodec used  
Fixed - RediSearch NumericFilter shouldn't set exclusive range by default  
Fixed - RediSearch NumericFilter throws NPE  
Fixed - `RLocalCachedMap.removeListener()` method doesn't remove loca cache listeners  

### 29-Apr-2023 - 3.21.0 released
Feature - [RediSearch module support](https://github.com/redisson/redisson/wiki/9.-distributed-services/#96-redisearch-service)  
Feature - [Tracing support](https://github.com/redisson/redisson/wiki/16.-Observability/#162-tracing)  
Feature - `RSetCache` extends `RSet` interface  
Feature - `RSemaphore` and `RPermitExpirableSemaphore` objects wait for sync operations completion  
Feature - Quarkus 3 support  
Feature - `LocalCacheUpdateListener` and `LocalCacheInvalidateListener` listeners support added to RLocalCachedMap object  

Improvement - `RRateLimiter` object uses 128-bit random  
Improvement - EVAL script cache applied to `RBatch` executed in `IN_MEMORY` mode  
Improvement - `RMap.keySet()` method shouldn't load map values  
Improvement - `SequentialDnsAddressResolverFactory` default concurrencyLevel set to 6  

Fixed - `RMapCache.fastPut()` method doesn't clear ttl and idleTime params if entry reinserted after expiration  
Fixed - Unable to find session error arise if Tomcat session was deleted or expired  
Fixed - `MasterSlaveEntry.getClient()` method may throw NPE  
Fixed - initialize Decoders LinkedHashMap with correct initial size to avoid unnecessary resizing (thanks @theigl)  
Fixed - failover handling may cause temporary connections spike  
Fixed - `RedissonCache.invalidate()` method breaks cache configuration  

### 28-Mar-2023 - 3.20.1 released

Feature - `LoadBalancer.getEntry(List<ClientConnectionsEntry>, RedisCommand<?>)` method added  
Feature - [CommandsLoadBalancer](https://github.com/redisson/redisson/blob/master/redisson/src/main/java/org/redisson/connection/balancer/CommandsLoadBalancer.java) added  
Feature - NodeType parameter added to ConnectionListener methods  

Improvement - command should be redirected to a master node if slave node returns LOADING error  

Fixed - `NameMapper` is called twice when acquiring a `RReadWriteLock`  
Fixed - closing idle connections causes connection listener to fire  
Fixed - `Unable to init enough connections amount!` error  
Fixed - no retry attempts are made for `None of slaves were synced` error  
Fixed - `READONLY You can't write against a read only replica..` is thrown after failover in sentinel mode (thanks @alexworkgit)  
Fixed - continuously attempts of `INFO REPLICATION` command execution until attempts limit reached by RLock object after failover  
Fixed - Node hasn't been discovered yet error isn't resolved by a new attempt for RBatch and RLock objects  
Fixed - `RedisClusterDownException`, `RedisLoadingException`, `RedisBusyException`, `RedisTryAgainException`, `RedisWaitException` are thrown by RBatch and RLock objects even if these errors disappeared after new attempts  
Fixed - `Unable to init enough connections amount! Only 0 of ... were initialized` error (thanks @alexworkgit)  
Fixed - `nameMapper` isn't applied to some methods of `RSet` and `RScoredSortedSet` objects  
Fixed - `readUnion()`, `readDiff()` and `readIntersection()` methods of `RSet` object don't use Redis slave nodes  

### 01-Mar-2023 - 3.20.0 released
Feature - new [Multi cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration/#210-multi-cluster-mode) which supports [AWS Redis Global Datastore](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/Redis-Global-Datastore.html) and [Azure Redis Cache active-passive replication](https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-how-to-geo-replication)  
Feature - [Proxy mode](https://github.com/redisson/redisson/wiki/2.-Configuration/#29-proxy-mode) supports [RLEC Active-Active databases](https://docs.redis.com/latest/rs/databases/active-active/get-started/)  
Feature - [monitorIPChanges](https://github.com/redisson/redisson/wiki/2.-Configuration#monitoripchanges) setting added for replicated servers mode  
Feature - auto-detection of unavailable master in replicated mode (thanks @nicdard)  

Fixed - `RLock` can only be obtained by single redisson node if `None of slaves were synced` error occurred  
Fixed - `RSetMultimapReactive.get()` method throws `ClassCastException`  
Fixed - Redisson doesn't start in Spring Boot Native image  
Fixed - `RedissonClient.shutdown(long, long, TimeUnit)` method isn't overridden by cluster, replicated and sentinel managers  
Fixed - Node hasn't been discovered yet error isn't resolved by a new attempt for RBatch and RLock objects  
Fixed - `RMapCache.addAndGet()` method doesn't handle Long type properly  
Fixed - `eventLoopGroup`, `connectionListener` and `executor` settings can't be defined through YAML configuration  
Fixed - `keySet()`, `values()`, `entrySet()` methods of `RLocalCachedMap` return empty result if `storeMode == LOCALCACHE`

### 06-Feb-2023 - 3.19.3 released
Fixed - a new attempt should be made on WAIT error during failover  
Fixed - Kryo5Codec fails to (de)serialize Object without no-args constructor (regression since 3.19.2)  

### 01-Feb-2023 - 3.19.2 released

Fixed - `RLock` instance can acquire lock with previous leaseTime if it's not specified  
Fixed - `RMap.computeAsync()` method causes deadlock if MapLoader is defined  
Fixed - `RBoundedBlockingQueue.offer()` methods always use global codec  
Fixed - Spring Boot `clientName` setting isn't used  
Fixed - `connectTimeout` setting is set incorrectly if Spring Boot 2.4.0+  
Fixed - command replies don't match if exception is thrown in CommandEncoder  
Fixed - empty result of BLMPOP command causes IndexOutOfBoundsException  
Fixed - canceled blocking operation isn't interrupted immediately in some cases  
Fixed - RStream.read() and RStream.readGroup() methods are hang forever is timeout > 0 and < 1000 milliseconds  
Fixed - `CacheLoader.loadAll()` method isn't called by `JCache.getAll()` method if readThrough=true  
Fixed - `Kryo5Codec` Serializers don't work in GraalVM native image mode  
Fixed - `LinkedHashMap` and `LinkedHashSet` objects can't be decoded properly by `Kryo5Codec`  
Fixed - `NameMapper` isn't applied to `RFunction` and `RScript` objects  
Fixed - `RFunction.callAsync()` method called with `RBatch` object throws `MOVED` errors in Redis cluster mode  
Fixed - `RFunction.loadAndReplace()` method uses incorrect command parameters  
Fixed - `codec`, `nettyHook`, `addressResolverGroupFactory`, `connectionListener` settings can't be defined through Quarkus or Helidon config  
Fixed - `RFunction.load()` method uses incorrect command parameters  
Fixed - empty `RTopic` message handling (thanks @MooRoakee)  

### 06-Jan-2023 - 3.19.1 released
Feature - `containsEach()` method added to RSet object (thanks to @slovvik)  
Feature - `getPermits()`, `acquiredPermits()`, `setPermits()` methods added to `RPermitExpirableSemaphore` object (thanks to @kscaldef, @derekroller)

__Breaking change - Kryo5Codec uses own serializators to serialize UUID, URI and Pattern objects__

Fixed - `RReliableTopic` doesn't remove all expired subscribers at once  
Fixed - `RPatternTopic` messages duplication after failover in cluster if channel starts with `__keyspace@` and `__keyevent@`  
Fixed - `RBatch.getListMultimapCache()` method should return `RMultimapCacheAsync` interface  
Fixed - SharedPubSub listener isn't being triggered (thanks to @MrChaos1993)  
Fixed - `RSetCacheRx` and `RSetCacheReactive` miss `tryAdd()` method  
Fixed - `RSetRx` and `RSetReactive` objects miss `tryAdd()` method  
Fixed - `RBloomFilter` bitset can't be expired and deleted if `nameMapper` is used (thanks to @javed119)  
Fixed - `RMapCacheRx` and `RMapCacheReactive` interfaces miss `addListener()` method  
Fixed - `RMapCacheAsync` interface misses `addListenerAsync()` method  
Fixed - `RTopicAsync.addListenerAsync()` method uses wrong generic pattern for MessageListener object  
Fixed - `RPermitExpirableSemaphore` throws CROSSSLOT error in cluster if nameMapper is used  

### 16-Dec-2022 - 3.19.0 released

Feature - implementation of Spring Cache methods added in Spring 5.2  
Feature - `entriesRead` and `lag` fields added to `StreamGroup` object  
Feature - added [RFencedLock](https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers/#810-fenced-lock) implementation  
Feature - [credentialsResolver](https://github.com/redisson/redisson/wiki/2.-Configuration#credentialsresolver) setting added  

__Breaking change - default codec changed to Kryo5Codec__  

Fixed - new Redis node isn't discovered between PubSub subscription attempts  
Fixed - `codec`,`nettyHook`,`addressResolverGroupFactory`,`connectionListener` settings can't be defined through Micronaut config  
Fixed - evictions metrics doesn't work for RedissonCache (thanks @Nicola Dardanis)  
Fixed - PubSub connection isn't reused if it reached subscriptions limit before unsubscribe operation  
Fixed - PubSub connection returns to connection pool only if subscriptions limit was reached  
Fixed - use slf4j late-binding when logging instead of string concat (thanks @vatarasov)  
Fixed - most of pubsub subscriptions fail to resubscribe after failover  
Fixed - `RBatch` with `executionMode = REDIS_WRITE_ATOMIC` throws NPE in case of connection starvation  
Fixed - `CommandDecoder.messageDecoder()` method throws NPE if `RBatch` object used with `executionMode = IN_MEMORY` (regression since 3.18.1)  
Fixed - some scheduled tasks aren't executed (regression since 3.17.5)  
Fixed - `RFunction` doesn't pass keys to Redis correctly (thanks @@jordanrmerrick)  
Fixed - incorrectly reset jackson type factory (thanks @noelvo)  
Fixed - cluster partitions parsing error isn't logged  

### 30-Nov-2022 - 3.18.1 released

Feature - Spring Data Redis 3.0.0 module added  

Fixed - PubSub subscription in cluster sometimes doesn't apply to all nodes  
Fixed - command replies don't match if connection pool size < 10 and at least one command failed  
Fixed - `RLock` throws `CancellationException` continuously  
Fixed - `None of slaves were synced` error is thrown after failover during RLock acquisition  
Fixed - AWS Elasticache cluster failover  
Fixed - `hRandFieldWithValues()` and `hRandField()` methods of Spring Data Redis module throw `ClassCastException`  
Fixed - `trySetPermitsAsync()` method of RPermitExpirableSemaphore object shouldn't allow to overwrite the number of permits if value == 0 (thanks @kscaldef)  
Fixed - `RKeys` object doesn't use `nameMapper`  
Fixed - connection leak after master failover  

### 11-Nov-2022 - 3.18.0 released

Feature - Tomcat 10.1.x support  
Feature - labels support for RTimeSeries object  
Feature - compatibility with Spring Boot 3 (thanks @olivierboudet)  
Feature - RxJava and Reactive interfaces for RLocalCachedMap object  
Feature - local cache support for JsonBucket object  

Improvement - StringCodec now implements JsonCodec  

Fixed - `RDoubleAdder` and `RLongAdder` objects don't work with `nameMapper`  
Fixed - `RBlockingQueue` methods should return null if negative timeout defined  
Fixed - `RLocalCachedMap.clearLocalCacheAsync()` method shouldn't retain semaphore after invocation  
Fixed - Spring Data Redis methods weren't implemented: `zRandMember()`, `zRandMemberWithScore()`, `zPopMin()`, `bZPopMin()`, `zPopMax()`, `bZPopMax()`, `zMScore()`, `zDiff()`, `zDiffWithScores()`, `zDiffStore()`, `zInter()`, `zInterWithScores()`, `zUnion()`, `zUnionWithScores()`, `hRandField()`, `hRandFieldWithValues()`, `copy()`, `lMove()`, `bLMove()`, `lPop()`, `rPop()`, `sMIsMember()`, `getEx()`, `getDel()`  
Fixed - attempts to connect to the failed master after failover in cluster mode  
Fixed - `RMapCache` `MapEntryListener` doesn't work with `nameMapper`  
Fixed - `RJsonBucket.getKeys()` method doesn't use path parameter  
Fixed - `RRateLimiter.getConfig().getRate()` throws NPE if it doesn't exist (thanks @Tanky-Zhang)  
Fixed - `RTransaction` objects should be the same instances on each "get..." call  
Fixed - `RScheduledExecutorService` cron triggers fire continuously for hours for some time zones (regression since 3.16.5)  
Fixed - `RSortedSet.add()` throws NPE (thanks @yuwei)  
Fixed - `RKeysReactive.getKeysByPattern()` method isn't giving all entries if downstream consumer is slow  
Fixed - "Unable to unfreeze entry" errors in sentinel mode  
Fixed - `JsonBucket.compareAndSet()` method with null as update value deletes whole object  
Fixed - Redis Cluster topology scanned partially in case of DNS resolution error  
Fixed - Slave nodes failed to pass complete initialization shouldn't be added as nodes  
Fixed - ByteBuf leaks when one of multiple parameters can't be encoded  
Fixed - `SearchDomainUnknownHostException` is thrown occasionally  

### 2-Oct-2022 - 3.17.7 released

Improvement - Failed connection ping isn't taken in account in Redis slave health check  

Fixed - RScheduledExecutorService cron expression doesn't support year  
Fixed - `replaceValues()` method of `RListMultimap` and `RSetMultimap` throws exception for empty collection  
Fixed - RedissonBaseLock throws NPE after failover  
Fixed - Spring Data Redis `evalsha()` method doesn't use key for Redis node routing in Cluster mode  
Fixed - DNS change isn't detected in replicated mode  
Fixed - `RCollectionReactive.addAll()` method is executed without subscription  
Fixed - `RKeysAsync.countExists()` method throws errors in cluster mode  
Fixed - Spring Data Redis reactive setIfAbsent should return `false` on error (thanks @zhuangzibin)  
Fixed - Micronaut native image configuration  
Fixed - RBatchReactive execution stuck forever if `useScriptCache = true`  
Fixed - NameMapper is applied incorrectly to RBoundedBlockingQueue object  
Fixed - incorrect IPv6 conversion  
Fixed - Spring Boot Module ignores username parameter set via Spring Redis config  
Fixed - SpringBoot yaml configuration parsing errors shouldn't be suppressed  

### 24-Aug-2022 - 3.17.6 released

Feature - Helidon 3.0 support  
Feature - ability to specify `MapWriterAsync` and `MapLoaderAsync` in `MapOptions` object  

Improvement - log output string expanded to 1000 characters by default

Fixed - `RBuckets` methods don't use `nameMapper`  
Fixed - PingConnectionHandler should close channel on RedisLoadingException, RedisTryAgainException, RedisClusterDownException, RedisBusyException  
Fixed - Invocation timeout isn't applied for `RTopic.removeListenerAsync()` methods  
Fixed - `WriteBehind` task isn't stopped after `RMap.destroy()` method invocation  
Fixed - Connection pinging works incorrectly if new connections were created in pool  
Fixed - "SlaveConnectionPool no available Redis entries" error occurs in Cluster caused by early excluding of master node from nodes for reading  
Fixed - Permanent blocking calling threads  

### 22-July-2022 - 3.17.5 released

Feature - `touch()`, `unlink()` and `delete()` methods implemented for transactional `RSetCache` and `RSet` objects  
Feature - transactional `RBucket`, `RMap`, `RMapCache`, `RSetCache`, `RSet` objects support `expire()`, `expireAt()` and `clearExpire()` methods  
Feature - `ExecutorOptions.idGenerator()` setting added  
Feature - methods with task id added to RExecutorService interface  

Fixed - duplicate subscriptions with RedisMessageListenerContainer in Spring Data Redis 2.7  
Fixed - `NameMapper` applied twice to transactional `RBucket`  
Fixed - some Quarkus environment variables clear all Redisson properties set through config file  
Fixed - `RJsonBucket.delete()` method doesn't work  
Fixed - `RExecutorService.submitAsync(Callable, long, TimeUnit)` method throws ClassCastException (thanks @xyqshi)
Fixed - Lock synced slaves check  
Fixed - reactive scripting commands throw ClassCastException if result is list of list  
Fixed - `RBatch.getJsonBucket()` method should return RJsonBucketAsync interface  

### 16-June-2022 - 3.17.4 released

Feature - [RJsonBucket](https://github.com/redisson/redisson/wiki/6.-distributed-objects/#615-json-object-holder) object added for `JSON.*` commands support  
Feature - `RFunction` and `RShardedTopic` objects added to `RBatch`  

Fixed - continuous "Unable to unfreeze entry" error in Sentinel mode  
Fixed - `nameMapper` setting isn't applied to `RExecutorService` and `RScheduledExecutorService`  
Fixed - channel write exception may lead to wrong commands order  
Fixed - don't connect to sentinel resolved by DNS if it's not included in result of SENTINEL SENTINELS command  
Fixed - `RScript.load()` method shouldn't use failed Redis nodes  
Fixed - `RPermitExpirableSemaphore.acquireAsync()` method hangs until leaseTimeout occurs. (regression since 3.16.8)  
Fixed - use 60 seconds polling instead of take command for RRemoteService responses  
Fixed - `eval()` and `evalSha()` methods of Spring Data Redis ReactiveScriptingCommands object throw IndexOutOfBoundsException  
Fixed - expired entries eviction process is limited to 5000 per call  
Fixed - sharded topic isn't resubscribed after channel reconnection  
Fixed - execution of blpop command leads to reconnection  

### 27-May-2022 - 3.17.3 released

Feature - Hibernate 6 support  

Improvement - amount of created connections in parallel reduced to 2 for better stability  

Fixed - Spring Boot Starter doesn't start with Spring Boot 2.7  
Fixed - RRateLimiter doesn't allow to set expiration time of permits and values  

### 23-May-2022 - 3.17.2 released
Feature - `RScoredSortedSet.replace()` method added  
Feature - Spring Data Redis 2.7.0 module added  
Feature - `RPatternTopic.removeAllListenersAsync()` method added  
Feature - `RShardedTopic` object added (requires Redis 7.0+)  
Feature - allow to specify username and password in redis connection url  
Feature - JCache data partitioning with local cache support  

Fixed - "Can't add slave" exceptions after fail over in cluster mode  
Fixed - "Unable to acquire subscription" error after connection interruption  
Fixed - JCache hangs forever when getting value from cache with useScriptCache=true  
Fixed - `RMap.merge()` method hangs if MapLoader specified  
Fixed - `FairLock` thread counter should start from 1 (thanks to @thisiswanghy)  

### 25-Apr-2022 - 3.17.1 released

Feature - transient fields support for LiveObjects to avoid data serialization  
Feature - `removeAllListenersAsync()` method added to `RTopic` object  
Feature - `transactionAware` setting added to RedissonSpringCacheManager  

Improvement - amount of created connections in parallel reduced to 5, for better stability

Fixed - `RedissonReactiveClient.getMultilock()` method should accept RLockReactive objects  
Fixed - `RedissonRxClient.getMultilock()` method should accept RLockRx objects  
Fixed - don't close connection on error response during topology scan  
Fixed - SET command should be an idempotent operation  
Fixed - MasterSlaveConnectionManager throws ClassCastException if host unknown  
Fixed - `RReadWriteLock` renewal doesn't work if writeLock released before readLock then both were acquired  
Fixed - Spring Data Redis module. Scan In cluster mode, other nodes cannot be scanned  
Fixed - `RReliableTopic` object throws "attempt to compare nil with number" error  
Fixed - `RedissonSpinLock.tryLock()` method returns false instead of true if the remaining wait time is negative  
Fixed - an error should be thrown if `merge()`, `compute()`, `computeIfAbsent()` and `computeIfPresent()` of `RMap` used in batch  
Fixed - Unable to specify timezone in CronSchedule object  
Fixed - `RMapCache.destroy()` method throws NPE  
Fixed - `RLock.tryLock()` method throws `CancellationException`  
Fixed - Unable to connect to Redis server error is thrown due to NPE  
Fixed - `RBlockingQueue.pollLastAndOfferFirstTo()` throws `ClassCastException` if result is empty  
Fixed - internal AsyncSemaphore doesn't skip canceled tasks in the same thread  
Fixed - `RLocalCachedMap.getAll()` method doesn't respect `storeCacheMiss` setting  
Fixed - 0 value for waitTime and leastTime isn't handled correctly by RMultiLock object  
Fixed - Spring Data Redis module. RedissonConnection.execute() method doesn't invoke overloaded methods correctly  

### 20-Mar-2022 - 3.17.0 released

Feature - `RFunction` object added (requires Redis 7.0+)  
Feature - `pollLastEntriesFromAny()` and `pollFirstEntriesFromAny()` methods added to RScoredSortedSet object (requires Redis 7.0+)  
Feature - `expireIfSet()`, `expireIfNotSet()`, `expireIfGreater()` and `expireIfLess()` methods added to RExpirable interface (requires Redis 7.0+)  
Feature - `checkLockSyncedSlaves` setting added  
Feature - `getAndExpire` and `getAndClearExpire()` methods added to `RBucket` object (requires Redis 6.2.0+)  
Feature - `pollFirstFromAny()` and `pollLastFromAny()` methods with timeout and count added to `RScoredSortedSet` object (requires Redis 7.0+)  
Feature - `pollFirst()` and `pollLast()` methods with timeout and count added to `RScoredSortedSet` object (requires Redis 7.0+)  
Feature - `addAllIfLess()`, `addAllIfGreater()`, `addAllIfExist()`, `addAllIfAbsent()` methods added to `RScoredSortedSet` object  
Feature - `RExpirable.expire(Duration)` method added  
Feature - `RExpirable.expireTime()` method added (requires Redis 7.0+)  
Feature - `range()`, `rangeReversed()`, `entryRange()`, `entryRangeReversed()` methods with limit parameter added to `RTimeSeries` object  
Feature - `TransactionalOperation.syncSlaves` setting added  
Feature - `pollFirstFromAny()` and `pollLastFromAny()` methods added to RBlockingQueue object (requires Redis 7.0+)  

Improvement - read-only cached scripts should be executed on slaves (requires Redis 7.0+)  
Improvement - SORT_RO command is used for slave nodes (requires Redis 7.0+)  
Improvement - decrease size of allocated data by RPermitExpirableSemaphore  

Fixed - `RedissonLocalCachedMap.clearLocalCache()` method throws IllegalArgumentException  
Fixed - RedissonMultiLock doesn't work properly with RedissonSpinLock  
Fixed - SlaveConnectionPool no available Redis entries error occurs in Cluster mode  
Fixed - `RKeys.deleteByPattern()` method does not always delete keys correctly  
Fixed - `expireAt(Instant)` method of RExpirableReactive and `RExpirableRx` interfaces doesn't work  
Fixed - wrong detection of added and removed slots in Redis Cluster mode  
Fixed - `RScoredSortedSet.addIfGreater()` and `RScoredSortedSet.addIfLess()` methods always return false  
Fixed - Spring Data Connection in multi mode causes thread stuck (regression since 3.16.7)  
Fixed - Sentinel username setting is not applied (thanks to @nicolas-tg-ch)  
Fixed - RTimeSeries doesn't handle same values for different timestamps  
Fixed - Quarkus environment variables aren't parsed correctly  
Fixed - check expiration before release in RPermitExpirableSemaphore (thanks to @randomVariable2)  
Fixed - RedisTimeoutException: Command execution timeout for command: (PING) (regression since 3.16.3)  
Fixed - wrong wait time calculation in RedissonMultiLock lock method causes deadlock  
Fixed - RLocalCachedMap throws NPE if cache update listener receives message during init  
Fixed - AsyncRemoteProxy throws Redisson is shutdown exception  
Fixed - RedisClusterNode.clusterSlots() method throws Exception  

### 21-Jan-2022 - 3.16.8 released

Fixed - Quarkus redisson config fails to load in cluster mode with one node address  
Fixed - registered `RReliableTopic` listener doesn't get old messages  
Fixed - pubsub channel isn't released if subscription timeout occurred  
Fixed - Quarkus Redisson config should be read at runtime  
Fixed - `RTopic` channels aren't unsubscribed  
Fixed - race condition causes Subscription timeout  
Fixed - `RMapCache.readAllKeySet()` doesn't use MapKey codec  
Fixed - Spring Data Redis RedissonConnection doesn't implement `lpos` command (thanks @woodyDM)  
Fixed - master host isn't formatted into compressed format for IPV6 addresses in Sentinel mode  
Fixed - Spring Data Redis `restore()` method throws Busy exception  

### 23-Dec-2021 - 3.16.7 released

Improvement - MessageListener should be annotated by @FunctionalInterface  

Fixed - RScript.scriptLoad() doesn't load script into Slave nodes  
Fixed - Spring Data RedissonConnection eval should use ByteArrayCodec (thanks @woodyDM)  
Fixed - `RSet.distributedIterator()` and `RScoredSortedSet.distributedIterator()` methods throw script error  
Fixed - synced slaves amount is not checked in RLock object  
Fixed - race condition during hostname resolution in sentinel mode which may cause slave shutdown  
Fixed - error should be thrown if slaves aren't defined in MasterSlave mode and readMode != MASTER  
Fixed - master node shouldn't be initialized as slave in single mode  
Fixed - `can't find node` error arise in replicated mode  

### 06-Dec-2021 - 3.16.6 released

Fixed - race condition causes wrong detection of failed slaves in Replicated mode. (regression since 3.16.5)

### 30-Nov-2021 - 3.16.5 released
Feature - `countIntersection()` method added to RSet object  
Feature - added reactive interface for `RListMultimapCache` and `RSetMultimapCache` objects  
Feature - `sentinelUsername` setting added  
Feature - added distributed iterator (thanks @Danila Varatyntsev)  
Feature - added Spring Data Redis 2.6.0 support  

Fixed - RedissonConnectionFactory.getReactiveConnection() method of Spring Data Redis isn't compatible with Redis cluster mode  
Fixed - Mybatis RedissonCache should search redisson.yaml config at root package  
Fixed - `Can't find host in slaves!` error after failover with a new IP of master host  
Fixed - failed slaves aren't detected in Replicated mode  
Fixed - `get` operation before `put` may cause inconsistent state of local cache  
Fixed - `RList.remove(object, count)` throws exception if multiple objects were removed (thanks @cartermc24)  
Fixed - `RLocalCachedMap.delete()` method clears local cache asynchronously  
Fixed - `IdleConnectionWatcher` shouldn't close `RedisPubSubConnection` if it's in subscribed state  
Fixed - SSL is not used for Sentinel master host  
Fixed - update sync strategy of LocalCachedMap objects shouldn't apply updated value twice to instance of update source  
Fixed - JCache dependency updated to 1.1.1  
Fixed - Sentinel master-host = ? setting isn't handled properly during slave check  
Fixed - `RBuckets.trySet()` method throws CROSSSLOT error (thanks to @deerRule)  
Fixed - DNS monitor makes a new attempt to change master while current attempt wasn't finished  

### 29-Oct-2021 - 3.16.4 released
Feature - `sentinelsDiscovery` setting added  
Feature - `quarkus.redisson.file` setting added to `redisson-quarkus` module to define external Redisson config file  

Improvement - optimization of `ClusterConnectionManager.checkSlaveNodesChange()` and `ClusterConnectionManager.checkMasterNodesChange()` methods  

Fixed - master change monitoring task in Replicated mode stops execution if it's invoked before the dns change  
Fixed - RemoteService cannot be called if requestId is null (thanks to @jimichan)  
Fixed - codec is not applied to RBuckets.set() method in non Cluster mode  
Fixed - recovered slave shouldn't be added again in Redis Cluster mode  
Fixed - `releaseConnection` method may cause StackOverflowError  
Fixed - MOVED response with hostname isn't handled properly  
Fixed - `RStream.readGroup()` method throws `IndexOutOfBoundsException` if group has a message without data  
Fixed - NPE in CommandPubSubDecoder  
Fixed - RExecutorService may execute same task twice at the same time  
Fixed - dependencies for testing should use appropriate scope  
Fixed - `RPriorityQueue.add()` method uses async method  
Fixed - don't retry non-idempotent operations which were successfully sent  
Fixed - RMapCache.fastRemove throws RedisException: too many results to unpack  
Fixed - RRateLimiter decreases limit over the time in highly concurrent environment  
Fixed - don't PING connection if it's in use  

### 21-Sep-2021 - 3.16.3 released
Improvement - `RBuckets.get()` method should group keys by slot in Redis Cluster mode  
Improvement - `RBatch` result decoding optimization  

Fixed - RExecutorService, RRemoteService execution may hang if connection used for tasks pooling was interrupted  
Fixed - RBatch with skipResult() option affects result of other commands (regression since 3.16.1)  
Fixed - connection leak (regression since 3.16.1)  
Fixed - `getBuckets().set()` method throws CROSSSLOT error (thanks to @mikawudi)  
Fixed - `RedissonMapCache.addListener()` method throws NPE  
Fixed - master-host of Slave node isn't resolved in Sentinel mode  
Fixed - interrupted `RLock.tryLock()` method keeps renewing lock indefinitely (thanks to @Cesarla)  
Fixed - don't ping connection if it's in use  
Fixed - `natMapper` isn't applied to resolved Sentinel and Cluster hosts  

### 3-Sep-2021 - 3.16.2 released
Feature - Micronaut 3.0 integration  
Feature - added batched `merge()` method to `RLiveObjectService` interface  
Feature - resolve hostnames used in Redis Cluster topology  
Feature - resolve hostnames used in Redis Sentinel topology  
Feature - added batched `addLast()` and `addFirst()` methods to `RDeque`, `RDequeRx` and `RDequeReactive` interfaces  
Feature - added `addAllCounted()` and `removeAllCounted()` methods to `RSet`, `RSetRx` and `RSetReactive` interfaces  

Fixed - Redis Stream trim command with MINID strategy is not fully supported  
Fixed - Quarkus requires `AutowiredAnnotationBeanPostProcessor` class during native image execution  
Fixed - issues with Quarkus Netty dependencies  
Fixed - `MOVED redirection loop detected` error in Redis Cluster  
Fixed - handling master with empty slots in Redis Cluster topology  
Fixed - SentinelConnectionManager should use unified compressed format for IPv6  
Fixed - `RLocalCachedMap.readAllValues()` method uses key decoder instead of value  
Fixed - empty array passed to `RKeys.delete()` method causes thread blocking  
Fixed - cluster partition without address causes NPE  
Fixed - threads waiting for `RSemaphore` permits acquisition unable to acquire them if permits added  
Fixed - `RRateLimiter` allows limit overcome  
Fixed - `RMapCacheReactive` and `RMapCacheRx` interfaces miss method to define eviction algorithm  
Fixed - write-behind tasks aren't flushed after Redisson `shutdown()` method invocation  
Fixed - LiveObjects with indexed field can't be stored using batch persist method  
Fixed - failed master shouldn't skipped in Redis Cluster topology scan (thanks to @JerryWzc)  
Fixed - `RListReactive` iterator with filter returns non-deterministic result  
Fixed - `replicatedServers` mode should use ip addresses if nodes defined using hostnames  
Fixed - multiple masters check removed for `replicatedServers` mode  
Fixed - `MapWriter` should be defined along with writeBehind settings  

### 26-Jul-2021 - 3.16.1 released
Improvement - MarshallingCodec and JsonJacksonCodec warmup added  
Improvement - performance improvement for connection pool with few connections  

Fixed - connection leak after command error if Batch executed in REDIS_WRITE_ATOMIC mode
Fixed - AsyncSemaphore race condition issue  
Fixed - Quarkus native remote service invocation fails  
Fixed - `nameMapper` setting isn't applied to `RTopic` object  
Fixed - Batch in REDIS_WRITE_ATOMIC mode doesn't respect batch settings  
Fixed - `UndeclaredThrowableException` is thrown when cache down while executing `RLiveObjectService.get()` method  
Fixed - Reactive Transactions aren't unlocking transactional locks  
Fixed - keySet() method of transactional map throws Exception  
Fixed - lock expiration renewal should be canceled if owner doesn't exist (thanks to @regidio)  


### 28-Jun-2021 - 3.16.0 released
Feature - GraalVM native-image support  
Feature - Spring Data Redis 2.5.x support  
Feature - [Helidon CDI](https://github.com/redisson/redisson/tree/master/redisson-helidon) integration  
Feature - [Quarkus](https://github.com/redisson/redisson/tree/master/redisson-quarkus) integration  
Feature - [Micronaut](https://github.com/redisson/redisson/tree/master/redisson-micronaut) integration  
Feature - data partitioning support for JCache  

Fixed - Live Object `Conditions.in()` aren't considered if defined in `Conditions.and()` clause  
Fixed - Redisson shutdown takes much time after `RBatch` execution  
Fixed - `RBatch` object in REDIS_WRITE_ATOMIC or REDIS_READ_ATOMIC mode can be corrupted by PING command  
Fixed - `RKeysReactive.getKeysByPattern()` method returns wrong result  
Fixed - `RExpirable.expire(Instant)` method doesn't work for RBloomFilter, RBoundedBlockingQueue, RDelayedQueue, RLock, RIdGenerator, RMultimap, RMapCache, RPriorityQueue, RRateLimiter, RReliableTopic, RSetMultimap, RTimeSeries objects  
Fixed - `RBlockingDequeReactive.takeElements()` method does not consume all elements  
Fixed - `RScheduledExecutorService` stops to work if task timeout occurred  
Fixed - `RedissonReactiveSubscription` removes listener after first 32 messages  
Fixed - `RedisNodeNotFoundException` is thrown after cluster failover (thanks to @UzimakiNaruto)  

### 07-Jun-2021 - 3.15.6 released

Fixed - `RedisSentinel.getMaster()` method throws NPE  
Fixed - `RSemaphore.drainPermits()` throws `ClassCastException`  
Fixed - missed implementation of few methods in Spring Data's RedissonConnection  
Fixed - `RLocalCachedMap.containsKey()` method doesn't invoke map loader  
Fixed - `RSemaphore` permits can't be acquired due to "Maximum permit count exceeded" error  
Fixed - `RedissonNode` unable to start due to `ClassNotFoundException`  
Fixed - SENTINEL SENTINELS command timeout (thanks to @zhwq1216)  
Fixed - `JCachingProvider` shouldn't depend on class from Jackson 2.12.x  
Fixed - `JCache.get()` method swallows Redis errors  
Fixed - `RLocalCachedMap` doesn't used MapLoader if storeMode = LOCALCACHE  

### 12-May-2021 - 3.15.5 released

Feature - `discard()` method added to `RBatch` object  
Feature - `broadcastSessionUpdates` setting added to Tomcat Session Manager  

Fixed - no error if jcache has wrong configuration in yaml format  
Fixed - frequent Redis master failover causes memory leak in `IdleConnectionWatcher`  
Fixed - `RedisStreamCommands.xGroupDelConsumer()` method in Spring Data module uses incorrect Redis command  
Fixed - `RLock` can't be acquired anymore if pubsub connection limit was reached (thanks to @zhwq1216)  
Fixed - PubSub Lock entries memory-leak during Lock acquisition (thanks to @zhwq1216)  
Fixed - dns monitor shouldn't use IP addresses as hostnames  
Fixed - failover handling stops to work if Redis Cluster node returned empty topology  
Fixed - `mGet()` and `mSet()` methods of Spring Data `RedissonConnection` object throw CROSSSLOT error  
Fixed - `touch()`, `mDel()`, `mUnlink()`, `expire()`, `pExpire()`, `expireAt()`, `pExpireAt()`, `persist()` methods of Spring Data `ReactiveKeyCommands` interface should be executed as write operation  
Fixed - RMap.computeIfPresent() doesn't update mutable objects  
Fixed - `MapReduce` timeout isn't applied if `ExecutorService` node is down  
Fixed - Redisson tries reconnect to Redis nodes which marked as shutdown by topology manager  

### 20-Apr-2021 - 3.15.4 released

Feature - sslProtocols setting added  
Feature - nameMapper setting added  
Feature - `getSigned()`, `setSigned()`, `incrementAndGetSigned()`, `getUnsigned()`, `setUnsigned()`, `incrementAndGetUnsigned()` methods added to `RBitSet` object  
Feature - `updateEntryExpiration()`, `getWithTTLOnly()` methods added to `RMapCache` object  

Improvement - Spring Cache, MyBatis Cache, Hibernate Cache implementations should read data from Redis slave if idleTime and cache size weren't specified  

Fixed - `ClusterConnectionManager.upDownSlaves()` method throws `ConcurrentModificationException`  
Fixed - `ClusterConnectionManager.checkMasterNodesChange()` method throws NPE  
Fixed - `JCache` `CacheEntryUpdatedListener` doesn't get old value of changed entry (thanks to @testower)  


### 30-Mar-2021 - 3.15.3 released

Feature - [connectionListener](https://github.com/redisson/redisson/wiki/2.-Configuration#connectionlistener) setting added  

Fixed - `tryAcquire()` and `availablePermits()` method of `RRateLimiter` object throw too many results to unpack error  
Fixed - `RRateLimiter` object throws LUA-script error  
Fixed - connection leak in Topology Manager for Replicated Redis config  
Fixed - `ConnectionListener.onConnect()` method isn't triggered during Redisson start  
Fixed - `addLastIfExists()` and `addLastIfExists()` methods of `RDeque` object don't work  
Fixed - `ArrayIndexOutOfBoundsException` is thrown if Redis master change was unsuccessful  
Fixed - `RScheduledExecutorService.scheduleAtFixedRate()` starts multiple instances of the same task if multiple workers defined  
Fixed - tasks scheduled via `RScheduledExecutorService.scheduleAtFixedRate()` method aren't executed after some time  

### 22-Mar-2021 - 3.15.2 released

Feature - `move()` method added to `RDeque` and `RBlockingDeque` objects  
Feature - `MINID` trimming strategy and `LIMIT` argument added to `RStream.add()` method  
Feature - new config `checkSlaveStatusWithSyncing` setting added  
Feature - enable tcpNoDelay setting by default (thanks to @mikawudi)  
Feature - `RedissonClient.reactive()` and `RedissonClient.rxJava()` methods added  
Feature - Spring Boot auto configuration should create `Reactive` and `RxJava` instances  

Improvement - simplified API of `RStream.read()` and `RStream.readGroup()`  

Fixed - Hibernate modules prior 5.3 version don't support fallback for `nextTimestamp()` method  
Fixed - `MarshallingCodec` doesn't release allocated ByteBuf object if exception thrown during encoding  
Fixed - `retryInterval` isn't used for next attempt if Redis client didn't send response  
Fixed - lease timeout updated in non-safe way in `org.redisson.RedissonLock#tryLockInnerAsync` method (thanks to @coding-tortoise)  
Fixed - references in RxJava objects aren't supported  
Fixed - Spring Data Redis module doesn't support `StreamReadOptions.isNoack()` option in `RedisStreamCommands.xReadGroup()` method. 
Fixed - trying to authentificate sentinel server without password  
Fixed - `RStream.getInfo()` method doesn't decode entries  
Fixed - Redisson doesn't reconnect slave if it was excluded before due to errors in failedSlaveCheckInterval time range. (thanks to @mikawudi)  


### 03-Mar-2021 - 3.15.1 released

Feature - `expireAt(Instant)` method added to `RExpirable` object  
Feature - `random()` method added to `RScoredSortedSet` object  
Feature - `randomKeys()` and `randomEntries()` methods added to `RMap` object  
Feature - count with any parameter added for search in `RGeo` object  
Feature - ability to search in box added for `RGeo` object

Improvement - simplified RGeo API with `search` methods  
Improvement - added check for invocation of sync methods in async/rx/reactive listeners  

Fixed - continuous reconnecting to broken host if it was defined as hostname in Redisson Cluster config  
Fixed - `WeightedRoundRobinBalancer` filters master node in readMode=ReadMode.MASTER_SLAVE  
Fixed - `RPatternTopicReactive.removeListener()` method should return `Mono<Void>`  
Fixed - remove `authType` and `principal` attributes on Apache Tomcat logout  
Fixed - scheduled tasks via `RScheduledExecutorService` object can't be canceled  
Fixed - `RStream.claim()` method throws NPE if given id does not exist  
Fixed - `RPatternTopic` on keyspace/keyevent notification subscribes only to single master node in Redis cluster  
Fixed - Class cast exception is thrown during iteration of `RMapCache` entries  
Fixed - internal `RedissonBaseLock.evalWriteAsync()` method isn't executed again if cluster slaves amount > 0  
Fixed - CPU spike after Slave failover if `subscriptionMode=SLAVE`  
Fixed - `rename()` method throws throws RedisException if `RBloomFilter` is empty  
Fixed - output full exception stacktrace if unable connect to sentinel server  
Fixed - duplicated `PING` sent when Redis connection got reconnected  
Fixed - Optional class can't be used as a result object in RemoteService interface  
Fixed - `redisson-spring-boot-starter` should use `redisson-spring-data-24` module  
Fixed - `RMapCacheRx.getLock()` returns `org.redisson.RedissonLock` instead of `org.redisson.api.RLockRx`  
Fixed - `RMapCacheReactive.getLock()` returns `org.redisson.RedissonLock` instead of `org.redisson.api.RLockReactive`  

### 28-Jan-2021 - 3.15.0 released

Feature - **Apache Tomcat 10** support added  
Feature - **Spin Lock** added. Please refer to [documentation](https://github.com/redisson/redisson/wiki/8.-Distributed-locks-and-synchronizers#89-spin-lock) for more details (thanks to @Vorotyntsev)  
Feature - `sentinelPassword` setting added (thanks to @ghollies)  
Feature - `RedisNode.getMemoryStatistics()` method added  
Feature - `setAndKeepTTL()` method added to `RBucket` object  
Feature - min idle time parameter added to `listPending()` and `pendingRange()` methods of `RStream` object  
Feature - `autoClaim()`, `fastAutoClaim()` and `createConsumer()` methods added to `RStream` object  
Feature - `addIfExists()`, `addIfGreater()` and `addIfLess()` methods added to `RScoredSortedSet` object  
Feature - `putIfExists()` and `fastPutIfExists()` methods added to `RMap` object  
Feature - `tryAdd()` and `addIfExists()` methods added to `RGeo` object  
Feature - `readUnion()`, `readIntersection()`, `diff()`, `readDiff()`, `rangeTo()`, `revRangeTo()` methods added to `RScoredSortedSet` object  
Feature - `ScoredSortedSetAddListener` added to `RScoredSortedSet` object  

Improvement - use `System.nanoTime()` in `IdleConnectionWatcher` to avoid clock drifting  

Fixed - eval command executed on Redis cluster doesn't use key for master/slave selection  
Fixed - `MOVED` or `ASK` response from Redis causes `Unable to acquire connection!` error  
Fixed - Spring Redis Data PatternTopic listeners are invoked multiple times per message  
Fixed - don't add Redis Slave as active if connections can't be established (thanks to @yann9)  
Fixed - `RBatch` object throws Exception if not all slots are covered in Redis Cluster  
Fixed - stream and queue object may lost entry during execution of any blocking poll operation  
Fixed - Redis `BUSY` response handling (thanks to @wuqian0808)  
Fixed - InterruptedExceptions are hidden by RedisException  
Fixed - primitive class numbers aren't indexed correctly in LiveObject search engine  
Fixed - NPE is thrown if LiveObject index stored for the first time in Redis cluster  
Fixed - NPE is thrown if Redis node doesn't return "flags" parameter  

### 22-Dec-2020 - 3.14.1 released

Feature - added option `LocalCachedMapOptions.storeCacheMiss` to store cache miss in a local cache (thanks to @ipalbeniz)  
Feature - LFU eviction algorithm added to `trySetMaxSize` and `setMaxSize` methods of RMapCache interface  

Improvement - RedisNodes ping results check optimization (thanks to @blackstorm)  
Improvement - keySet().isEmpty() and values().isEmpty() methods of RMap object aren't efficient  

Fixed - connection leak if new discovered slaves in `LOADING Redis is loading the dataset in memory` state (thanks to @mikawudi)  
Fixed - `RMap.putIfAbsent()` method doesn't check value for null  
Fixed - Apache Tomcat Valve objects should be added in context pipeline instead of engine's pipeline  
Fixed - slaves synchronization timeout isn't respected during `RLock.lock()` method invocation  
Fixed - ConnectionWatchdog may cause connection leak (thanks to @mikawudi)  
Fixed - `Redisson.shutdown()` method throws RejectedExecutionException  
Fixed - `count()` and `addAll()` methods of `RScoredSortedSetReactive` and `RScoredSortedSetRx` interfaces throw ClassCastException  
Fixed - `GEORADIUS_RO` command should be used instead of `GEORADIUS` in Spring Redis Data module  
Fixed - Spring Data Redis `RedissonConnection.del()` method doesn't work in pipeline on Redis cluster  
Fixed - `RLocalCachedMap.putAll()` method updates in wrong way log used for Reconnection.LOAD strategy  
Fixed - `redisRepository.opsForSet().distinctRandomMembers()` method throws ClassCastException  

### 21-Nov-2020 - 3.14.0 released

Spring Session implementation is deprecated now. Please refer to [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#147-spring-session) for more details  

Feature - __`RReliableTopic` object added__. Please refer to [documentation](https://github.com/redisson/redisson/wiki/6.-distributed-objects/#613-reliable-topic) for more details  
Feature - __`RIdGenerator` object added__. Please refer to [documentation](https://github.com/redisson/redisson/wiki/6.-distributed-objects/#614-id-generator) for more details  
Feature - Spring Data Redis 2.4.0 integration  
Feature - `StreamMessageId.AUTO_GENERATED` const added  
Feature - Rx API for `RMultimapCache` object (thanks to @mlkammer)  
Feature - cluster-safe implementation of `rename`, `renameNX` methods of `RedissonClusterConnection` object (thanks to @eager)  
Feature - `RxJava2` API replaced with `RxJava3`  
Feature - `tryAdd()` method added to `RSet` and `RSetCache` objects  

Improvement - preventing sending CLUSTER NODES to the same host (thanks to @serssp)  

Fixed - `RSetMultimap` could throw a class cast exception on its `get()` method because it actually contained a list based multimap instance (thanks to @mlkammer)  
Fixed - Spring Data Redis `redisTemplate.opsForGeo().radius()` method doesn't work  
Fixed - `RKeys.deleteByPattern()` method executed in batch should throw `UnsupportedOperationException` in cluster mode  
Fixed - `CACHE_REGION_PREFIX` setting isn't applied for hibernate 5.3+  
Fixed - deprecation error log about JSON config even though it's not used  
Fixed - update new master record in DNS monitor only if it replaced old master successfully  
Fixed - `RQueue.removeIf()` method should throw `UnsupportedOperationException`  
Fixed - Lock watchdog won't renew after reconnection (thanks to @burgleaf)  
Fixed - `TimeSeries.iterator()` method doesn't respect the ordering  
Fixed - `RRateLimiter` throws "bad argument #2 to 'unpack' (string expected, got nil)."  
Fixed - `CROSSSLOT` error rised when clearing a redis-spring-data cache  
Fixed - `RLongAdder.sum()` and `RDoubleAdder.sum()` methods return wrong result  
Fixed - getting error while connecting to sentinel using password  
Fixed - result of `RStream.read()` method isn't sorted by key  

### 13-Oct-2020 - 3.13.6 released

Improvement - set pingConnectionInterval = 30000 by default

Fixed - CROSSLOT error thrown during RLiveObject update  
Fixed - `RRateLimiter.delete()` method returns false  
Fixed - `RBitSet.set(long bitIndex, boolean value)` should return boolean  
Fixed - `RBatch` doesn't handle `MOVED`, `ASK` Redis errors in Redis  
Fixed - "response has been skipped due to timeout" warnings were removed  
Fixed - additional check for blocking command added in PingConnectionHandler  
Fixed - object's name should be checked for null  
Fixed - redisson-spring-boot-starter doesn't load config file  
Fixed - `RTransaction` should be executed in IN_MEMORY_ATOMIC mode  
Fixed - high contention during connection acquisition from connection pool  


### 28-Sep-2020 - 3.13.5 released

**breaking change** - `spring.redis.redisson.config` setting renamed to `spring.redis.redisson.file`  

Feature - `RClusteredTopic` object added  
Feature - `RRingBuffer.setCapacity()` method added  
Feature - `merge()`, `compute()`, `computeIfAbsent()`, `computeIfPresent()` methods implemented for RMap-based objects  
Feature - spring config server support (thanks @anjia0532)  

Improvement - expand config variables from system properties if not found as environment variables (thanks to @jribble)  

Fixed - `RKeys.keysByPattern()` method doesn't use pattern (thanks to @sh1nj1)  
Fixed - `RObjectLiveService.delete()` method throws `ClassCastException`  
Fixed - fail to insert key with TTL = 0 if the same key was previously set with non-zero TTL  
Fixed - Pubsub channel isn't reattached to a new master after slot migration  
Fixed - `PingConnectionHandler` throws `CancellationException`  
Fixed - shared session between several Tomcats expires earlier if `readMode=Redis` and `broadcastSessionEvents=false`  
Fixed - incorrect session attributes being returned in `UpdateMode=AFTER_REQUEST` and `ReadMode=REDIS`  
Fixed - Tomcat UpdateValve object throws NullPointerException if url context doesn't exist  
Fixed - old value of RLiveObject's field isn't removed from index  
Fixed - Spring Data Redis `RedissonSubscription.onPatternMessage()` method throws `ClassCastException`  
Fixed - `RSemaphore.addPermits()` method doesn't work  
Fixed - `RMultimap.sizeInMemory()` method doesn't take in account size of all associated objects  

### 02-Sep-2020 - 3.13.4 released
Feature - batch support for `revRank`, `getScore`, `addAndGetRevRank` methods added to RScoredSortedSet object (thanks to @johnou)  
Feature - `RRateLimiter.setRate` method added (thanks to @AbhishekChandrasekaran)  
Feature - `RObject.getIdleTime()` method added  
Feature - `RKeys.getKeysWithLimit()` method added  

Fixed - `RRateLimiter.availablePermits()` method throws exception (regression since 3.13.3)  
Fixed - compatibility with Spring Data Redis 2.3.3  
Fixed - `UnsupportedOperationException` is thrown if Spring Data Redis connection executed in pipelined mode  
Fixed - multiple Tomcat requests share different instances stored in the same session in `readMode=REDIS`  
Fixed - Spring Data Redis can't be used with proxied RedissonClient instance  
Fixed - Classloading issues when `MarshallingCodec` used in Tomcat  
Fixed - Redis cluster slot calculation doesn't work properly if brace isn't closed (thanks to @dengliming)  
Fixed - `RBloomFilter` rename method doesn't rename config object (thanks to @dengliming)  
Fixed - `slf4j-simple` dependency excluded from redisson-all  
Fixed - `JCache.removeAsync` method throws NPE if operation fails  
Fixed - all cached Lua scripts are executed on Redis master nodes only  
Fixed - `XPENDING` command causes syntax error in redisson-spring-data-23  
Fixed - `CommandPubSubDecoder` throws NPE  
Fixed - `MasterSlaveConnectionManager` allocates superfluous 113Kb of memory for non-cluster Redis setup  

### 05-Aug-2020 - 3.13.3 released
Feature - BITFIELD command support added to `RBitSet` object  

Improvement - reset ClassIntrospector instance after `RLiveObjectService.persist()` method invocation  
Improvement - amount of simultaneously created connections during pool initialization reduced to 10  

Fixed - "SlaveConnectionPool no available Redis entries" error is thrown after failover  
Fixed - check RedisConnection status befor RedisConnection object join freeConnections (thanks to @mikawudi)  
Fixed - different topics subscribed to the same Redis node in Cluster  
Fixed - `RFairLock.tryLock()` method doesn't apply waitTimeout parameter  
Fixed - `RLiveObjectService.delete()` method works asynchronously  
Fixed - deserialization exception is thrown if `RMapCache.EntryRemovedListener` is set  
Fixed - incorrect registration of Sentinel node defined with hostname  
Fixed - OOM arise during `RLiveObjectService.persist()` method invocation  
Fixed - MarshallingCodec throws IllegalArgumentException: RIVER  
Fixed - `RLock.lock()` method throws java.util.NoSuchElementException  
Fixed - Spring Data Redis xReadGroup should use write operation  
Fixed - Spring Data Redis connection in multi mode may cause thread hang  
Fixed - Spring Data Redis connection in multi mode may cause connection leak  
Fixed - `RRateLimiter` rate interval might be exceeded  

### 02-Jul-2020 - 3.13.2 released
Feature - Partitioning (sharding) of Redis setup using [ShardedRedisson](https://github.com/redisson/redisson/wiki/5.-Data-partitioning-(sharding)#2-partitioning-sharding-of-redis-setup) object  
Feature - `CLUSTERDOWN` error handling  
Feature - `getConfig`, `setConfig` methods added to `RedisNode` interface  

Fixed - empty sentinels list handling  
Fixed - RMapCache.clear() method clears maxSize option  
Fixed - Redisson slowdowns access to hibernate in fallback mode  
Fixed - Sentinel hostname isn't used for ssl connection during Redisson startup  
Fixed - `RBloomFilter` methods throw NPE if RedisException thrown  
Fixed - `RAtomicDouble.getAndSet()` method throws NPE  
Fixed - `RAtomicLong.getAndSet()` method throws NPE  
Fixed - ClassCastException thrown in Tomcat environment  
Fixed - RSetMultimap.get().delete() and RListMultimap.get().delete() methods throw exception  
Fixed - blocking commands connected to Redis Cluster aren't resubscribed after Master node failover  
Fixed - connection leak if SSL connection got reconnected  

### 09-Jun-2020 - 3.13.1 released
Feature - Spring Data Redis 2.3.0 integration  
Feature - `setIfExists` method added to `RBucket`, `RBucketRx`, `RBucketReactive` interfaces  
Feature - RExpirable interface added to RRateLimiter  

Fixed - Topic channels connected to master node aren't resubscribed  
Fixed - RedissonCacheStatisticsAutoConfiguration conditions aren't match  
Fixed - `RTimeSeries.destroy()` method doesn't work  
Fixed - Redis Cluster topology scanning stopped if error occured while adding new master  
Fixed - StreamInfoDecoder to adapt to layout of XINFO response (thanks to @fawitte)  
Fixed - Redis Cluster manager throws error Slot hasn't been discovered yet after failover  
Fixed - Spring Data Redis `RedisConnection.set()` method returns null  
Fixed - `RQueueReactive.poll(int limit)` method should return `Mono<List<V>>`  
Fixed - `RQueueRx.poll(int limit)` method should return `Single<List<V>>`  
Fixed - `RedissonSetMultimap.removeAll` method removes reference to nested set  
Fixed - `WriteRedisConnectionException` is thrown after Redis Cluster failover  
Fixed - `RBatch` object doesn't wait ending of sync slaves process  
Fixed - ssl connection can't be established if Redis Sentinel was discovered by DNSMonitor  
Fixed - some tasks are not executed if RedissonNode shutdown  
Fixed - `NatMapper` is not applied to the first online Sentinel in list  

### 25-May-2020 - 3.13.0 released
Feature - __TimeSeries object added__. Please refer to [documentation](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#723-time-series) for more details  
Feature - `RLiveObjectService.findIds()` method implemented  
Feature - `RLiveObjectService.count()` method implemented  
Feature - `RLiveObjectService.delete()` method accepts multiple ids  

Improvement - __default codec changed to MarshallingCodec__  

Fixed - Lock acquisition hangs if Redis failed (regression since 3.12.5)  
Fixed - `RLiveObjectService.merge()` method doesn't override collection state  
Fixed - `PingConnectionHandler` doesn't report errors if PING command can't be sent  
Fixed - `RObject.unlink()` method calls `deleteAsync()` inside instead of `unlinkAsync()` method (thanks to @AayushyaVajpayee)  
Fixed - NPE thrown after logout Tomcat application  
Fixed - Tomcat `UpdateValue` object throws NPE if getNext() returns null  
Fixed - `RedissonTransactionalMapCache.put()` method inserts entries with incorrect TTLs  
Fixed - connections to Redis master/slave are doubled after redis cluster failover  
Fixed - `RejectedExecutionException` thrown by `RedisClient.connectAsync()` method during shutdown process  
Fixed - cache isn't applied to hibernate collection, that is joined by non primary key field  
Fixed - hibernate 5.3 doesn't wrap cache exception into CacheException object  
Fixed - RedissonReactiveStringCommands.set using wrong SET RedisCommand (thanks to @xJoeWoo)  
Fixed - netty errors should be logged at ERROR level  

### 16-Apr-2020 - 3.12.5 released

Improvement - increased `RLock` reliability during failover. `RedLock` was deprecated  

Fixed - Map object is not updated after session change (thanks to @eager)  
Fixed - `RedissonSessionRepository` doesn't handle PTTL = -2 (thanks to @eager)  
Fixed - `RedissonSessionRepository` topic listener NPE race condition (thanks to @eager)  
Fixed - `RedissonReactiveSubscription.subscribe()` and `receive()` methods aren't synchronized  
Fixed - `RLiveObjectService` search with `Conditions.and()` returns wrong result  
Fixed - Redisson Tomcat Manager doesn't store principal and authType session attributes  
Fixed - Redisson is unable to start if first sentinel node in list is down  
Fixed - Spring Data `RedissonConnection.del()` method doesn't participate in pipeline  
Fixed - `RTopic.countListeners()` method returns wrong result  
Fixed - `RRateLimiter.delete()` method doesn't delete all allocated Redis objects  
Fixed - `RedissonBloomFilter` throws NPE (regression since 3.12.4)  
Fixed - CommandBatchService throws NPE (regression since 3.12.4)  

### 30-Mar-2020 - 3.12.4 released

Feature - cacheProvider setting added to `LocalCacheConfiguration`, `ClusteredLocalCachedMapOptions`, `LocalCacheConfiguration` and Hibernate Local Cache regions  
Feature - `NettyHook` object added  
Feature - `LocalCachedMapOptions.storeMode` setting added  
Feature - `nameMapper` setting added to `DropwizardMeterRegistryProvider`  
Feature - `username` parameter added  
Feature - `RedissonClient.getRedisNodes()` method added  
Feature - `Config.useThreadClassLoader` setting added  
Feature - `ListAddListener`, `ListInsertListener`, `ListRemoveListener`, `ListSetListener`, `ListTrimListener` added to `RList` object 

Improvement - `MarshallingCodec` performance improvements  

Fixed - RedissonSessionRepository doesn't use map's codec during changeSessionId method execution  
Fixed - use `FSTConfiguration#deriveConfiguration` to preserve ConfType (thanks to Chris Eager)  
Fixed - MGET executed on Spring Data connection should use SLAVE if readMode = SLAVE (thanks to Gil Milow)  
Fixed - `XREADGROUP` and `XCLAIM` commands should be executed on Redis master  
Fixed - `JsonJacksonCodec` unable to serialize removed attributes of Tomcat Session  
Fixed - "response has been skipped due to timeout" error if pingConnectionInterval setting set and blocking command executed  
Fixed - semaphore used during local cache clearing process isn't deleted  
Fixed - `RPatternTopicRx()`, `RPatternTopicReactive()`, `RPatternTopic.addListenerAsync()` methods are don't work  
Fixed - cache entry can't be updated if `JCache` instance created with `CreatedExpiryPolicy`  
Fixed - `LocalCachedJCache.get()` method throws NPE  
Fixed - RedisURI throws MalformedURLException for IPv6 hosts  
Fixed - `LocalCachedJCache.removeAll()` and `LocalCachedJCache.clear()` methods are don't work  


### 28-Feb-2020 - 3.12.3 released

LZ4Codec, SnappyCodec, SnappyCodecV2 codecs now use Kryo5Codec by default  

Feature - `SetObjectListener` added to `RBucket` object  
Feature - `RBinaryStream` should expose `SeekableByteChannel` and `AsynchronousByteChannel` interfaces  
Feature - added `RBucketsReactive` and `RBucketsRx` objects  
Feature - added Caffeine support as alternative local cache implementation  
Feature - added `RBinaryStreamReactive` and `RBinaryStreamRx` objects  
Feature - added `RKeys.swapdb` method  
Feature - added `addFirstIfExists` and `addLastIfExists` methods to `RDeque` object  
Feature - `RPriorityDeque` extends `RDeque`  

Improvement - type of `RedisExecutor.CODECS` field changed to `LRUCacheMap`  

Fixed - `CommandPubSubDecoder` throws NPE  
Fixed - `RLock.unlock()` results in ERR hash value if RedisLabs hosting is used  
Fixed - `RPriorityBlockingQueue.poll()` method with limit implemented  
Fixed - `redisson__map_cache__last_access__set*` objects continuously grow in size if RMapCache.maxSize defined  
Fixed - Eviction task is not stopped after `RMapCache.destroy()` method invocation  

### 18-Feb-2020 - 3.12.2 released

Feature - Hibernate `hibernate.cache.redisson.fallback` setting introduced  
Feature - added `RLocalCachedMap.preloadCache` method with batch size  

Improvement - `RLocalCachedMap.putAllOperation` method optimization  

Fixed - exception thrown by `org.redisson.jcache.JCacheEventCodec`  
Fixed - connection leak occured during `RBatch` object usage  
Fixed - Tomcat session should return the same object during the same request for `readMode = REDIS` and `updateMode = AFTER_REQUEST` settings  
Fixed - `RPriorityQueue` comparator is not deleted or expired after corresponding methods invocation  
Fixed - memory leak caused by `ClientConnectionsEntry.allConnections` field  
Fixed - `maxIdleTimeout = 1 hour` set to `RedisExecutor.CODECS` map  
Fixed - use `RBatch` for all types of Redis setup (thanks to @basiszwo)  

### 31-Jan-2020 - 3.12.1 released

Feature - `RTransferQueue` object added. Please refer to [documentation](https://github.com/redisson/redisson/wiki/7.-distributed-collections#722-transfer-queue) for more details  
Feature - `availablePermits`, `availablePermitsAsync` and `drainPermitsAsync` methods added to `RSemaphoreAsync` object  
Feature - `tryExecute`, `tryExecuteAsync` and `getPendingInvocationsAsync` methods added to `RRemoteService` object  
Feature - `natMap` setting deprecated in favor of `natMapper`  
Feature - `checkSentinelsList` setting added  
Feature - `cleanUpKeysAmount` setting added  

Improvement - perform Sentinel DNS check when all connections fail (thanks to @markusdlugi)  

Fixed - `zRemRangeByScore` and `zcount` methods of `ReactiveZSetCommands` interfaсe don't use `-inf` and `+inf` values  
Fixed - connections to disconnected Redis nodes aren't removed in sentinel and cluster mode  
Fixed - `MalformedURLException` thrown during Redis host parsing in cluster mode  
Fixed - extra square bracket added during IPV6 Redis host conversion  
Fixed - defined codec's classloader is overwritten with Thread.currentThread().getContextClassLoader()  
Fixed - `RPriorityQueue.add` method throws NPE  
Fixed - connecting to a password protected Redis Sentinel fails (thanks to @stikku)  
Fixed - java.lang.IllegalStateException thrown during `org.redisson.spring.cache.RedissonCacheStatisticsAutoConfiguration` introspection  
Fixed - `RLock` expiration renewal not working after connection problem (thanks to @edallagnol)  
Fixed - Spring Data `RedissonConnectionFactory` should call shutdown method on destroy on created Redisson instance  

### 26-Dec-2019 - 3.12.0 released

Feature - `RExecutorService` task id injection  
Feature - `submit()` methods with ttl parameter added to `RExecutorService`  
Feature - `schedule()` methods with timeToLive parameter added to `RScheduledExecutorService`  
Feature - `MyBatis` cache implementation added  
Feature - added implementation of `doSuspend` and `doResume` methods of Spring Transaction Manager  
Feature - `WorkerOptions.taskTimeout` setting added  
Feature - `RSemaphore.addPermits` method added  
Feature - ability to define `TaskFailureListener`, `TaskFinishedListener`, `TaskStartedListener` and `TaskSuccessListener` task listeners with WorkerOptions object  

Improvement - Default workers amount of `RExecutorService` set to 1

Fixed - CommandDecoder throws NPE  
Fixed - ReplicatedConnectionManager triggers reactivation of slaves with ip address instead of hostname (thanks to @f-sander)  
Fixed - Reactive/Rxjava2 transaction doesn't unlock lock on rollback or commit  
Fixed - Spring Transaction Manager doesn't handle Redisson `TransactionException`  
Fixed - `RBuckets.get` throws NPE for non-existed buckets (thanks to @d10n)  
Fixed - `RPermitExpirableSemaphore` does not expire  
Fixed - `RedissonCacheStatisticsAutoConfiguration` should be conditional on CacheMeterBinderProvider presence (thanks to @ehontoria)  

### 28-Nov-2019 - 3.11.6 released

Feature - `RExecutorServiceAsync.cancelTaskAsync()` method added  
Feature - `RExecutorService.hasTask()` method added  
Feature - `RExecutorService.getTaskCount()` method added  
Feature - `RMap` write-behind queue should be fault-tolerant  
Feature - added `poll()` methods with limit to `RQueue`, `RDeque`, `RDelayedQueue` objects  
Feature - `checkSlotsCoverage` setting added to cluster config  
Feature - `RExecutorService.getTaskIds()` method added  
Feature - `awaitAsync()` methods added to `RCountDownLatch` object  
Feature - `RCountDownLatchRx` and `RCountDownLatchReactive` objects added  

Fixed - channel subscription proccess in interruptible methods can't be interrupted  
Fixed - `RMap.get()` method invokes `MapWriter.write()` method during value loading  
Fixed - interruptible blocking methods couldn't be canceled  
Fixed - ClusterNodesDecoder ignore unknown flag and avoid throwing exception  (thanks to ZhangJQ)  
Fixed - `AsyncSemaphore` counter field should be volatile  
Fixed - data encoding exception is not logged during remote call invocation  
Fixed - Spring Data ZRangeByScore method doesn't support Infinity Double value  
Fixed - spring metainfo files should be excluded in redisson-all artifact  
Fixed - `RKeysRx.getKeys()` and `RKeysReactive.getKeys()` methods throws Exception  

### 26-Oct-2019 - 3.11.5 released

Feature - Automatic-Module-Name entry added to manifest file  
Feature - `subscribeOnElements` method added to `RBlockingQueue` object  
Feature - `subscribeOnFirstElements` and `subscribeOnLastElements` methods added to `RBlockingDeque` object  
Feature - `subscribeOnFirstElements` and `subscribeOnLastElements` methods added to `RScoredSortedSet` object  
Feature - support of Spring Data Redis 2.2.x  
Feature - added batched persist method to `RLiveObjectService` object  
Feature - Kryo5Codec implemented  

Fixed - host details in Redis url cause extra bracket at the end  
Fixed - RBuckets.get() method doesn't work with custom codec  
Fixed - RLock.unlock() method hangs forever  
Fixed - `RStream.trim` method throws exception  
Fixed - Spring Data pubsub listeners executes multiple times  
Fixed - Redis commands processing handler consumes 100% of CPU resources  

### 24-Sep-2019 - 3.11.4 released

Feature - support search by numeric fields in LiveObject search engine. Follow conditions are supported: `greater than on equal`, `greater than`, `lower than`, `lower than or equal`  

Fixed - threads blocked waiting on CountDownLatch  
Fixed - `rename` and `renamenx` methods of `RMultimap` interface aren't working  
Fixed - memory leak caused by `FastThreadLocal` object used in `CodecDecoder`  
Fixed - blocking queue methods don't re-throw `InterruptedException`  
Fixed - `RExecutorService` tasks duplication after task retry event  
Fixed - `Redisson.shutdown` method blocks calling thread  
Fixed - Spring Boot default connection timeout is 0 seconds  
Fixed - IPv6 handling in Sentinel manager (thanks to [AndreevDm](https://github.com/AndreevDm))  
Fixed - `RMapCache.remainTimeToLive` method returns negative remain time  
Fixed - `RBuckets.get` method doesn't work in Redis cluster mode  
Fixed - wrong error message if Sentinel server has misconfiguration  

### 30-Aug-2019 - 3.11.3 released  

Feature - JBoss Marshalling codec implementation  
Feature - support for Spring's `@Autowired`, `@Value` and JSR-330 `@Inject` annotations in ExecutorService tasks  
Feature - SSL support for Sentinel mode  
Feature - `sizeInMemory()` method added to `RObjectReactive` and `RObjectRx` interfaces  
Feature - `getId()` method added to `RedissonClient`, `RedissonRxClient`, `RedissonReactiveClient` interfaces  

Improvement - `useCache` parameter added to `FstCodec`  
Improvement - URL object should be used for `sslKeystore` and `sslTruststore` settings  

Fixed - `RedisTimeoutException` thrown if retryAttempts set to 0  
Fixed - `RStream.readGroup` method doesn't work properly with `TypedJsonJacksonCodec`  
Fixed - semaphore object is not deleted after `RLocalCachedMap.clearLocalCache` method invocation  
Fixed - Redisson couldn't be shutdown if one of RBatch commands was canceled  

### 03-Aug-2019 - 3.11.2 released  

Improvement - `RLiveObject` interface should extend `RExpirable`  
Improvement - `RKeys.deleteByPattern` method performance improvements  

Fixed - `RBatch.execute` method throws `NoSuchElementException`  
Fixed - `RedisTimeoutException` is thrown if retryInterval set to 0  
Fixed - `Set.intersection`, `union` and `diff` methods should return Integer  
Fixed - `FSTCodec` state is not fully copied  
Fixed - `CommandAsyncService.CODECS` map changed to Soft reference map  
Fixed - `RKeys.deleteByPatternAsync` method doesn't work in batch mode  
Fixed - subscribe timeouts after failover  
Fixed - a new attempt is not made if node with defined slot wasn't discovered  
Fixed - some methods of `RScript` object doesn't use defined codec  
Fixed - `RedissonConnection.set` method returns null if invoked with expiration parameter  
Fixed - `removeAll` method doesn't work on collections returned by Multimap based objects  

### 25-Jun-2019 - 3.11.1 released  
Feature - `getPendingInvocations` method added to `RRemoteService` object  
Feature - `natMap` setting support for Sentinel mode (thanks to [fgiannetti](https://github.com/fgiannetti))  
Feature - `listPending` method added to `RStream`, `RStreamAsync`, `RStreamRx`, `RStreamReactive` interfaces  
Feature - implementation of Spring Session `ReactiveSessionRepository` added  
Feature - allow usage of multiple env variables with default values in one config entry (thanks to [tristanlins](https://github.com/tristanlins))  

Improvement - Use maven packaging for redisson project as jar instead of bundle (thanks to [jchobantonov](https://github.com/jchobantonov))  
Improvement - add default entries in MANIFEST.MF file and extra Build-Time entry as well (thanks to [jchobantonov](https://github.com/jchobantonov))  

Fixed - RMap.replace method doesn't update idle timeout (thanks to [mcacker](https://github.com/mcacker))  
Fixed - timeout drift in RedissonFairLock (thanks to [jncorpron](https://github.com/jncorpron))  
Fixed - dead Sentinel appears in logs as node added and down  
Fixed - Publish/Subscribe connections are not resubscribed properly after failover process  
Fixed - `RedissonLocalCachedMap.clearLocalCache` method is not executed asynchronously  
Fixed - Tomcat `RedissonSession.setAttribute` method doesn't check the value for null (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - Tomcat Manager UpdateValve should be installed only once (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - remove MessageListener from topic when Tomcat Session Manager stopInternal is invoked (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - `RStream.getInfo` method throws `java.lang.ClassCastException`  
Fixed - `RedissonMultiLock` could be acquired by multiple threads if `waitTime` == -1 and `leaseTime` != -1  
Fixed - `PRINCIPAL_NAME_INDEX_NAME` key is not created in redis  
Fixed - `SessionExpiredEvent` is not triggered in Spring Session implementation  
Fixed - host name containing underscore cause NPE  
Fixed - Illegal reflective access by org.redisson.misc.URIBuilder warning removed  
Fixed - `RedissonSessionRepository` doesn't trigger created event if `keyPrefix` setting is not null (thanks to [hs20xqy](https://github.com/hs20xqy))  
Fixed - `RRemoteService.getFreeWorkers` method removes registered service  
Fixed - zero timeout isn't respected in `RRateLimiter.tryAcquire` method  
Fixed - `RedissonObjectBuilder.REFERENCES` map should be filled one time  
Fixed - RReadWriteLock.readLock doesn't take in account expiration date of the last acquired read lock. (thanks to [Aimwhipy](https://github.com/Aimwhipy))  
Fixed - result object of RMapRx.iterator methods don't return `Flowable` object  
Fixed - Tomcat Session doesn't expire if `broadcastSessionEvents = true`  
Fixed - ClassNotFoundException thrown during `SerializationCodec.decode` method invocation (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - connections amount setting for mirrors is not applied in Proxy mode  

### 28-May-2019 - 3.11.0 released  
Feature - `radiusStoreSortedTo` methods added to `RGeo`, `RGeoAsync`, `RGeoRx`, `RGeoReactive` interfaces  
Feature - Local cache for `JCache` added. Read the [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#1442-jcache-api-local-cache) for more details  
Feature - `Async`, `Reactive`, `RxJava2` interfaces added to `JCache`. Read the [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#1441-jcache-api-asynchronous-reactive-and-rxjava2-interfaces) for more details  
Feature - `RRingBuffer` object added. Read the [documentation](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#721-ring-buffer) for more details  

Improvement - reduced memory consumption by ClusterConnectionManager  
Improvement - UpdateValve needs to execute manager.store only once at the end of the request (thanks to [jchobantonov](https://github.com/jchobantonov))  

Fixed - `HttpSessionListener.sessionDestoyed` method isn't invoked if session wasn't loaded by Tomcat instance  
Fixed - redisson-spring-data `ReactiveSubscription.receive` method throws NPE  
Fixed - Redis response isn't fully consumed after decoding error  
Fixed - Spring Session PRINCIPAL_NAME_INDEX_NAME session attribute has incorrect name  
Fixed - internal `AsyncSemaphore` object doesn't notify sleeping threads with permits more than one  
Fixed - `RedisTemplate.radius` and `RedisConnection.geoRadius` methods throws `IllegalArgumentException` during response decoding  
Fixed - `RedissonNodeConfig.mapReduceWorkers` setting couldn't be set (thanks to xiaoxuan.bai)  


### 29-Apr-2019 - 3.10.7 released  
Feature - Add support for [Reactive and RxJava2 interfaces](https://github.com/redisson/redisson/wiki/9.-distributed-services#913-remote-service-asynchronous-reactive-and-rxjava2-calls) to RemoteService object  
Feature - MILLISECONDS option added to RRateLimiter.RateIntervalUnit object  
Feature - range method added to RList, RListReactive and RListRx interfaces  

Improvement - `JCache.getAll` execution optimization for non-existing keys  
Improvement - 10X Performance boost for `JCache.putAll` method  

Fixed - disconnected sentinels shouldn't be used in sentinel list  
Fixed - Apache Tomcat `RedissonSessionManager` doesn't use classloader aware codec for session Map object (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - LiveObject field with Map type couldn't be persisted  
Fixed - `RRateLimiter` allows permits limit exceeding  
Fixed - `CompositeCodec.getMapValueDecoder` method uses `MapKeyDecoder` instead of `MapValueDecoder`  
Fixed - memory leak during blocking methods invocation of Queue objects  
Fixed - Apache Tomcat `RedissonSessionManager.findSession` shouldn't create a new one session (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - `JCache.removeAll` method doesn't notify Cache listeners  
Fixed - `UpdateValve` sould be removed from pipeline in Apache Tomcat `RedissonSessionManager.stopInternal` method (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - Redis Sentinel prior 5.0.1 version doesn't require password. Regression since 3.10.5 version  
Fixed - Redisson tries to renewed Lock expiration even if lock doesn't exist. Regression since 3.10.5 version  
Fixed - FstCodec can't deserialize ConcurrentHashMap based object with package visibility  

### 05-Apr-2019 - 3.10.6 released  
Feature - `broadcastSessionEvents` setting added to Tomcat Session Manager  
Feature - `remainTimeToLive` method added to `RLock`, `RLockAsync`, `RLockRx` and `RLockReactive` interfaces  
Feature - NAT mapping support for cluster mode  
Feature - `isLock` method added to `RLockAsync`, `RLockRx`, `RLockReactive` interfaces  
Feature - `writeBehindDelay` and `writeBehindBatchSize` settings added to `MapOptions` object  

Improvement - Eviction task logging added  
Improvement - `MapWriter` interface retains only two methods for handling batch updates  
Improvement - `MapOptions.writeBehindThreads` parameter removed  

Fixed - `RBitSet.asBitSet` methods throws NPE if RBitSet object doesn't exist  
Fixed - `JCache.getAll` method throws `RedisException: too many results to unpack`  
Fixed - `RLock.lock` method can be interrupted with `Thread.interrupt` method  
Fixed - Tomcat Session parameters aren't updated completely in `readMode=MEMORY`  
Fixed - `RLock.unlock` method returns true if lock doesn't exist  
Fixed - Tomcat Session Manager doesn't remove session attributes in `updateMode=AFTER_REQUEST`  
Fixed - Pattern topic listeners fail to re-attach on cluster failover (thanks to [shailender-bathula](https://github.com/shailender-bathula))  
Fixed - `CommandPubSubDecoder.decodeResult` throws `IllegalStateException` in JBOSS environment  
Fixed - NullValue object shouldn't be stored if `RedissonSpringCacheManager.allowNullValues = false`  
Fixed - `removeListener` method of `RTopicReactive` and `RTopicRx` interfaces throws NoSuchMethodException  

### 20-Mar-2019 - 3.10.5 released  
Feature - `getMultiLock`, `getRedLock` methods added to `RedissonClient`, `RedissonRxClient` and `RedissonReactiveClient` interfaces  
Feature - `getInfo`, `listGroups`, `listConsumers` methods added to `RStream`, `RStreamRx`, `RStreamReactive` interfaces  
Feature - `RPatternTopic.removeListenerAsync` method added  
Feature - `getAndSet` method with TTL support added `RBucket`, `RBucketAsync`, `RBucketReactive`, `RBucketRx` interfaces  
Feature - `addListener` and `removeListener` methods added to `RObject`, `RObjectAsync`, `RObjectReactive`, `RObjectRx` objects. It allows to add and remove listeners for Deleted and Expired keyspace events published by Redis  

Improvement - shuffle list of sentinels to distribute load (thanks to [hrakaroo](https://github.com/hrakaroo))  
Improvement - methods of RxJava2 interfaces should use full set of result objects: `Single`, `Maybe` or `Completable`  

Fixed - compatibility with hibernate 5.2.0 - 5.2.4 versions  
Fixed - ClassCastException during `RBatchReactive` and `RBatchRx` execution in `exectionMode` = `REDIS_WRITE_ATOMIC` or `REDIS_READ_ATOMIC`  
Fixed - sentinel mode doesn't support AUTH command  
Fixed - response shouldn't be printed in case of response timeout error  
Fixed - HP NONSTOP OS is not supported by `MapCacheEventCodec`  
Fixed - RStream.readGroup method doesn't handle empty result properly with Redis 5.0.3  
Fixed - AUTH password included in exceptions  
Fixed - locked lock isn't unlocked after disconnect to Redis  

### 08-Mar-2019 - version 3.10.4 released  
Feature - `getCountDownLatch`, `getPermitExpirableSemaphore`, `getSemaphore`, `getFairLock` methods added to `RMultimap` object  
Feature - `maxCleanUpDelay` and `minCleanUpDelay` settings added to `Config` object  

Fixed - `RLocalCachedMap.destroy` method doesn't clear local cache  
Fixed - HP NONSTOP OS is not supported by MapCacheEventCodec  
Fixed - `RedissonLocalCachedMap.readAll*` methods throw NPE if `evictionPolicy = WEAK` used  
Fixed - codec provided in Redisson configuration isn't used for attribute messages serialization  
Fixed - ClassNotFoundException is thrown by Tomcat Session Manager in `readMode = MEMORY`  
Fixed - fresh Tomcat instance unable to load session stored in Redis  
Fixed - negative delay shouldn't be allowed to define for `RDelayedQueue.offer` method  
Fixed - eviction delay for `RMapCache` object is not decreased  
Fixed - `RMultiLock.tryLockAsync` and `RMultiLock.lockAsync` methods may hang during invcation  

### 26-Feb-2019 - 3.10.3 released  
Feature - `RTransaction.getBuckets` method added  
Feature - ability to redefine `CacheKeysFactory` in Hibernate Cache Factory  
Feautre - ability to specify default values for environment variable (thanks to [aaabramov](https://github.com/aaabramov))  
Feature - `RRateLimiter.getConfig` method added  
Feature - `ping` and `pingAll` methods with timeout added to `Node` object  

Improvement - create Redis stream group if it doesn't exist  
Improvement - response decoding optimization  

Fixed - `RStream.listPending` throws `IndexOutOfBoundsException`  
Fixed - `LocalCacheView.toCacheKey` method throws `StackOverflowError`  
Fixed - `RedissonSessionManager` doesn't support async servlets (thanks to [dnouls](https://github.com/dnouls))  
Fixed - FSTCodec memory leak  
Fixed - rename and renamenx methods don't change name of current object  
Fixed - performance regression of RBatch result decoding (since 2.12.2 / 3.7.2 version)  
Fixed - Transactional RBucket object doesn't respect transaction timeout  

### 07-Feb-2019 - versions 2.15.2 and 3.10.2 released  
Feature - `StreamMessageId.NEVER_DELIVERED` added  
Feature - [decodeInExecutor](https://github.com/redisson/redisson/wiki/2.-Configuration#decodeinexecutor) setting added  
Feature - `lockAsync` and `tryLockAsync` methods with threadId param added to RedissonMultiLock object  

Improvement - default values of `nettyThreads` and `threads` settings set to `32` and `16` respectively  
Improvement - Redis nodes with empty names in cluster config should be skipped  

Fixed - `RFuture.cause()` method should return CancellationException and not throw it  
Fixed - continues handling of RedisLoadingException leads to excessive load  
Fixed - slave's connection pools are not initialized when it back from failover  
Fixed - Buffer leak during failover and RBatch object execution  
Fixed - NumberFormatException error appears in log during RStream.readGroup invocation  
Fixed - already wrapped IPv6 addresses shouldn't be wrapped in square brackets (thanks to [antimony](https://github.com/antimony))  
Fixed - NPE arise during Tomcat Session getAttribute/delete methods invocation (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - huge messages logged in case of decoding error  
Fixed - Redis password shouldn't be included in exceptions  
Fixed - Redis Password visible in log  
Fixed - infinite loop in RedLock or MultiLock (thanks to [zhaixiaoxiang](https://github.com/zhaixiaoxiang))  
Fixed - `RBatch` object in `REDIS_READ_ATOMIC` execution mode is not executed on Redis salve  
Fixed - MOVED loop redirect error while "CLUSTER FAILOVER" (thanks to [sKabYY](https://github.com/sKabYY))  
Fixed - redisson configuration in yaml format, located in classpath, couldn't be loaded by hibernate factory  
Fixed - class files should be excluded from META-INF folder  
Fixed - `JndiRedissonSessionManager` shouldn't shutdown Redisson instance (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - `RDestroyable` interface should be implemented by RMapCache and RSetCache rx/reactive objects  

### 21-Jan-2019 - versions 2.15.1 and 3.10.1 released  
Feature - `cachedKeySet`, `cachedValues`, `cachedEntrySet` and `getCachedMap` methods added to `RLocalCachedMap` object  
Feature - __Hibernate 5.4__ support  
Feature - [search LiveObjects](https://github.com/redisson/redisson/wiki/9.-Distributed-services#rindex) by field  
Feature - allow checking if lock is held by a thread (thanks to [hackworks](https://github.com/hackworks))  

Improvement - return `null` if Tomcat session attribute couldn't be parsed  
Improvement - Tomcat Session key codec changed to StringCodec  
Improvement - Spring Session key codec changed to StringCodec  
Improvement - Tomcat Session recycle method implementation (thanks to [jchobantonov](https://github.com/jchobantonov))  

Fixed - RRateLimiter RateType checking (thanks to [shengjie8329](https://github.com/shengjie8329))  
Fixed - implementation of workaround for DNS name resolver bug  
Fixed - running scheduleWithFixedDelay Job couldn't be canceled  
Fixed - master can't be changed anymore if new master wasn't added the first time  
Fixed - don't send PING command for blocking queues  
Fixed - getting `java.lang.ClassNotFoundException` if same Redisson instance used in tomcat and application (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - Tomcat Session manager throws `java.lang.ClassNotFoundException` if `readMode=MEMORY` (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - ReplicatedConnectionManager doesn't recover Master node as Slave after failover  
Fixed - Spring Session manager process changes of unnecessary keys  
Fixed - Tomcat Session expires in Redis earlier than scheduled by Tomcat (thanks to [jchobantonov](https://github.com/jchobantonov))  
Fixed - Tomcat Session `getAttribute` method throws NPE  
Fixed - `BlockingQueue.drainTo` doesn't work when queue contains only one element  
Fixed - `RTopic.removeListener` method throws `RejectedExecutionException`  
Fixed - connection is not reconnected if init command failed to send  
Fixed - `keepAlive` setting is not set for single server connection mode  
Fixed - NPE in CommandPubSubDecoder  
Fixed - `pollFromAny` doesn't support Redis cluster  
Fixed - `RGeo.pos` throws `ClassCastException`  
Fixed - `LRUCacheMap` throws `ArrayIndexOutOfBoundsException`  
Fixed - IPv6 hosts support (thanks to [antimony](https://github.com/antimony))

### 27-Dec-2018 - versions 2.15.0 and 3.10.0 released  
Feature - new __[Hibernate cache](https://github.com/redisson/redisson/tree/master/redisson-hibernate) implementation__  
Feature - __Hibernate 5.3__ support  
Feature - [TypedJsonJacksonCodec](https://github.com/redisson/redisson/blob/master/redisson/src/main/java/org/redisson/codec/TypedJsonJacksonCodec.java) added  
Feature - `getCountDownLatch`, `getSemaphore`, `getPermitExpirableSemaphore`, `getFairLock` methods added to `RMap` object  
Feature - `getCountDownLatch`, `getSemaphore`, `getPermitExpirableSemaphore`, `getFairLock` methods added to `RSet` object  
Feature - `RTopic.countSubscribers` method added  
Feature - `JndiRedissonFactory` and Tomcat `JndiRedissonSessionManager` added  
Feature - Hibernate Region Factories with JNDI support  
Feature - ability to use Environmental Variables in config files  
Feature - Spring Data Redis 2.1.x support added  
Feature - Spring Boot Starter 2.1.x support added  
Feature - Spring Data Redis 2.0.x and 2.1.x integrations support `ReactiveRedisTemplate`  
Feature - Support of [Different monitoring systems](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#1410-statistics-monitoring-jmx-and-other-systems)  

Improvement - RGeo.radius methods use GEORADIUS_RO and GEORADIUSBYMEMBER_RO commands  
Improvement - restored implementation of DnsAddressResolverGroupFactory  
Improvement - RedisConnectionClosedException removed  
Improvement - __default codec changed to FSTCodec__  

Fixed - `RMap.getAll` throws `ClassCastException` during transaction execution  
Fixed - `pingConnectionInterval` and `lock-watchdog-timeout` parameters added to `redisson.xsd`  
Fixed - zRevRangeWithScores does not work properly in Spring RedisTemplate  
Fixed - `CommandDecoder` throws `IndexOutOfBoundsException` if `pingConnectionInterval` param is used  
Fixed - NPE in `CommandDecoder`  
Fixed - error during channel initialization is not logged  
Fixed - `RBitSet` object couldn't be used as nested object  
Fixed - use `keyPrefix` for topic object used in Tomcat Session Manager  
Fixed - unable connect to Redis on Android  
Fixed - `RMapCache` element expiration doesn't work with map size = 1  
Fixed - MOVED handling  
Fixed - Pooled connection closed after MOVED redirection  
Fixed - Master node shouldn't be shutdown on slave down event in Sentinel mode  
Fixed - `RoundRobinLoadBalancer` doesn't distribute load equally if one of slave nodes failed  
Fixed - Spring Session `keyPrefix` setting isn't used in session name  
Fixed - failed Redis Master node is not shutdown properly  
Fixed - Redisson shouldn't be shutdown in Spring Data RedissonConnectionFactory  
Fixed - Redisson Spring Boot doesn't start properly without lettuce or jedis in classpath  
Fixed - validation of filled out Redis node address in Config  

### 21-Nov-2018 - versions 2.14.1 and 3.9.1 released
Feature - `takeFirstElements` and `takeLastElements` streaming methods added to `RBlockingDequeRx`  
Feature - `RBlockingQueueRx.takeElements` streaming method added  
Feature - `RTopicRx.getMessages` streaming method added  
Feature - async methods for listener removal added to `RTopic` object  
Feature - RxJava2 method call cancellation support added  
Feature - `RObject.getSizeInMemory` method added  
Feature - `RTopic.countListeners` method added  
Feature - `takeFirstElements` and `takeLastElements` added to `RScoredSortedSetReactive` and `RScoredSortedSetRx` objects  
Feature - `takeFirst` and `takeLast` methods added to `RScoredSortedSet` object  
Feature - `readGroup` method variations added to `RStream` object  
Feature - `remove`, `trim`, `fastClaim`, `removeGroup`, `removeConsumer`, `updateGroupMessageId` methods added to `RStream` object  

Improvement - JCache performance optimization up to 2x times  
Improvement - Redis url validation  

Fixed - Exception serialization by Jackson codec  
Fixed - DNS channels aren't pooled  
Fixed - RStream commands don't work with Redis 5.0.1 version  
Fixed - task scheduled with cron pattern isn't executed with single worker  
Fixed - Spring Boot Starter doesn't convert properly list of sentinel addresses  
Fixed - `RTopic` and all objects based on it stop work properly if Subscribe timeout occured  
Fixed - JDK 1.6 compatibility for 2.x version  
Fixed - IPv6 addresses format support for Sentinel mode (thanks to Mikhail Surin)  
Fixed - null value handling for Spring Boot Starter's `timeoutValue` setting (Thanks to junwu215177)  
Fixed - OOM during `RLocalCachedMap.fastPut` method invocation and Reference based EvictionPolicy is used  
Fixed - exception in CommandDecoder while using PubSub with ping  
Fixed - `RTopic.removeAllListeners` invocation leads to PubSub connections leak  
Fixed - all methods of `RSemaphoreRx` and `RAtomicDoubleRx` objects should return `Flowable` object  
Fixed - `Redisson.createRx` method should return RedissonRxClient  

### 31-Oct-2018 - versions 2.14.0 and 3.9.0 released
Feature - `RMap.putAll` with ttl param (thanks to [Tobias Wichtrey](https://github.com/wtobi))  
Feature - RxJava 2 support. Please refer to [documentation](https://github.com/redisson/redisson/wiki/3.-Operations-execution#32-reactive-way) for more details  
Feature - Lambda task definition for `RExecutorService`. Please refer to [documentation](https://github.com/redisson/redisson/wiki/9.-Distributed-services#932-distributed-executor-service-tasks) for more details  
Feature - multi-type listeners support for `RTopic` and `RPatternTopic` objects. Please refer to [documentation](https://github.com/redisson/redisson/wiki/6.-Distributed-objects#67-topic) for more details  
Feature - `useScriptCache` setting added. Manages by Lua-script caching on Redis side. Please refer to [documentation](https://github.com/redisson/redisson/wiki/2.-Configuration#usescriptcache) for more details  
Feature - added `RMap.putAllAsync` method with batch size  
Feature - added `RSet.random` method limited by count  

Improvement - memory allocation optimization during ExecutorService task execution  

Fixed - `keepAlive` is not being set  
Fixed - Redisson can't resolve short DNS name  
Fixed - Redisson shuts down executor it doesn't own  
Fixed - "spring.redis.cluster.nodes" param parsing for spring-boot  
Fixed - `Node.ping` throws Exception if node is unreachable  
Fixed - NPE in `CommandDecoder`  
Fixed - `DecoderException` thrown when `pingConnectionInterval` setting being set  
Fixed - `BlockingQueue.take` method doesn't survey failover  
Fixed - `SnappyCodecV2` codec doesn't decode properly underlying data encoded by FST codec  
Fixed - `UnsupportedOperationException` thrown when using Spring Actuator with redisson-spring-data lib  

### 06-Oct-2018 - versions 2.13.2 and 3.8.2 released
Feature - `RPermitExpirableSemaphore.updateLeaseTime` method added  

Improvements - cluster state check  

Fixed - DNS resolver fails to find valid DNS record  
Fixed - MultiLock should attempt to release locks if request was sent successfully  
Fixed - `RRateLimiter.tryAcquire` with timeout blocks forever  
Fixed - CommandAsyncService blocks indefinitely if MapLoader's methods throw exception  
Fixed - RedisConnection is not closed if QUIT command wasn't sent successfully  
Fixed - Spring Boot resource encoding  
Fixed - failed Redis slaves handling  
Fixed - read locks aren't acquire lock at the same moment when write released  
Fixed - empty RBoundedBlockingQueue's capacity increases when using poll method  
Fixed - tomcat session replication in IN_MEMORY mode (thanks to Yasin Koyuncu)  

### 18-Sep-2018 - versions 2.13.1 and 3.8.1 released
Feature - __RStream__ object added. Please refer to [documentation](https://github.com/redisson/redisson/wiki/7.-Distributed-collections#720-stream) for more details  

Fixed - `failedSlaveCheckInterval` setting is not applied under some conditions  
Fixed - `spring.factories` and `spring.provides` files added to Spring Boot module  
Fixed - `pingConnectionInterval` setting affects re-connection process with password enabled setting  
Fixed - Lock watchdog does not monitor read locks  
Fixed - Lock expiration renewal should be canceled if unlock method failed to execute  
Fixed - `BlockingQueue.take` method doesn't work properly after failover  
Fixed - Avoid to create connection per `RRemoteService/RScheduledExecutorService` worker  
Fixed - backward JDK 8 compatibility  

### 03-Sep-2018 - versions 2.13.0 and 3.8.0 released
Feature - __Spring Data Redis__ integration. Please refer to [documentation](https://github.com/redisson/redisson/tree/master/redisson-spring-data#spring-data-redis-integration) for more details  
Feature - __Spring Boot Starter__ implementation. Please refer to [documentation](https://github.com/redisson/redisson/tree/master/redisson-spring-boot-starter#spring-boot-starter) for more details  
Feature - `RBlockingDequeReactive` object added  
Feature - `sharedSession` setting for Tomcat Session Manager. Appropriate solution for migration of EAR based application with multiple WARs hosted previously on WebLogic or other servers. Please refer to [documentation](https://github.com/redisson/redisson/tree/master/redisson-tomcat) for more details  

Improvement - Redis request/response handling performance improvement  
Improvement - CompositeIterator decoupled from CompositeIterable (thanks to [Pepe-Lu](https://github.com/Pepe-Lu))  

Fixed - task scheduled with time more than 1 hour is not executed  
Fixed - RScheduledExecutorService doesn't handle delayed tasks correctly  
Fixed - `RMapCache` and `RSetCache` objects should implement `RDestroyable`  
Fixed - `RBucket.set` method with ttl throws NPE if value is null  
Fixed - false HashedWheelTimer resource leak message  
Fixed - `RExecutorService` task execution performance regression  
Fixed - locking in multiple parallel transactions created with the same thread  
Fixed - `JCache.removeAll` doesn't work  
Fixed - Batch in `ExecutionMode.REDIS_WRITE_ATOMIC` and `ExecutionMode.REDIS_READ_ATOMIC` returns `QUEUED` instead of real result  
Fixed - tasks scheduled with cron expression don't work in different timezones (thanks to [Arpit Agrawal](https://github.com/arpit728))  
Fixed - global config codec is not registered in codec cache for reference objects (thanks to [Rui Gu](https://github.com/jackygurui))  

### 19-Jul-2018 - versions 2.12.5 and 3.7.5 released
Feature - `RScoredSortedSetReactive`, `RSetReactive`, `RListReactive` objects implement `RSortableReactive` interface  
Feature - `RGeoReactive` object added  
Feature - reactive version of FairLock added  
Feature - `RRateLimiterReactive` object added  

Improvement - RObjectReactive and RScoredSortedSetReactive interfaces synced with `RObjectAsync` and `RScoredSortedSetAsync`  

Fixed - readonly command shouldn't be executed on master node used as slave  
Fixed - connection is closed per command execution for master node used as slave in `readMode=MASTER_SLAVE`  
Fixed - `RLiveObjectService` should use entityClass's classloader  

### 16-Jul-2018 - versions 2.12.4 and 3.7.4 released
Feature - dump and restore methods added to `RObject` interface  

Fixed - Redis response hangs if `RLiveObject` stored as nested object  
Fixed - slow Redisson initialization in Sentinel  
Fixed - missing PubSub messages when pingConnectionInterval setting is specified  
Fixed - `RBatch` in `ExecutionMode.REDIS_WRITE_ATOMIC` and `ExecutionMode.REDIS_READ_ATOMIC` modes throws NumberFormatException exception  
Fixed - `RRedissonRateLimiter.acquire` blocks forever  
Fixed - lot of connections remain in TIME_WAIT state after Redisson shutdown  
Fixed - `ClassNotFoundException` arise in Tomcat session manager  
Fixed - `RHyperLogLog.addAll` method doesn't add all elements  

### 27-Jun-2018 - versions 2.12.3 and 3.7.3 released
Feature - added `RKeys.getKeys` method with batch size  
Feature - added `SnappyCodecV2` codec  

Fixed - `SerializationCodec` doesn't support proxied classes  
Fixed - NPE if `RScheduledExecutorService`'s task scheduled with cron expression for finite number of execution  
Fixed - validation of cron expression parameter of `RScheduledExecutorService.schedule` method  
Feature - Iterator with batch size param for all `RSet`, `RMap`, `RMapCached` objects  
Fixed - missing PubSub messages when `pingConnectionInterval` setting is specified  
Fixed - excessive memory consumption if batched commands queued on Redis side  
Fixed - `RRateLimiter.acquire` method throws NPE  

### 14-Jun-2018 - versions 2.12.2 and 3.7.2 released

Feature - `RBatchOptions.executionMode` setting added. Please refer to [documentation](https://github.com/redisson/redisson/wiki/10.-additional-features#103-execution-batches-of-commands) for more details  

Fixed - NPE in JCacheManager.close method  
Fixed - ExecutorService tasks aren't reloaded properly  
Fixed - removed unnecessary creation of HashMap instances in cluster mode  
Fixed - `RedisNode.info` doesn't work  
Fixed - NullPointerException when using setPingConnectionInterval in Pub/Sub mode  
Fixed - LocalCachedMapDisable should implement Serializable  
Fixed - `ConcurrentModificationException` in `RTransaction.execute` method  
Fixed - exception handling in Fst and Json codec  
Fixed - `RedissonScoredSortedSet.contains` and `RedissonScoredSortedSet.getScoreAsync` methods use wrong codec  
Fixed - NPE if `RedissonLocalCachedMap` init with `ReconnectionStrategy.LOAD` param  
Fixed - transactional object methods get blocked at high concurrency  

### 02-Jun-2018 - versions 2.12.1 and 3.7.1 released
Feature - `RRateLimiter` object moved to open-source version  
Feature - ExecutorService task failover. Default failover interval is 60 seconds  
Feature - `RScoredSortedSet.pollFirst` and `pollLast` methods with count parameter added  
Feature - `RScoredSortedSet.pollFirst` and `pollLast` methods with timeout added  
Feature - `RScoredSortedSet.pollFirstFromAny` and `pollLastFromAny` methods added  

Improvement - `Node.time()` method returns `Time` object  
Improvement -  RListReactive, RMapCacheReactive, RSetCacheReactive and RSetReactive are up-to-date to Async interfaces  

Fixed - setPingConnectionInterval is not propagated for single server configuration  
Fixed - ClusterConnectionManager should use shared resolverGroup  
Fixed - value can't be added to BloomFilter  
Fixed - Redis nodes with noaddr flag should be parsed correctly  
Fixed - methods belongs to transactional objects get blocked at high concurrency  
Fixed - Collection iterator doesn't use the same Redis node  
Fixed - ExecuteService response queue expiration time set to one hour  
Fixed - Executed remote tasks are not removed from Redis  
Fixed - `reconnectionTimeout` and `failedAttempts` renamed in xsd schema  

### 14-May-2018 - versions 2.12.0 and 3.7.0 released
Feature - __Proxy mode__  Please refer to [documentation](https://github.com/redisson/redisson/wiki/2.-Configuration#29-proxy-mode) for more details  
Feature - __Transaction API implementation__  Please refer to [documentation](https://github.com/redisson/redisson/wiki/10.-additional-features/#104-transactions) for more details  
Feature - __Spring Transaction API implementation__  Please refer to [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#147-spring-transaction-manager) for more details  
Feature - __XA Transaction API implementation__  Please refer to [documentation](https://github.com/redisson/redisson/wiki/10.-additional-features/#105-xa-transactions) for more details  
Feature - `RPermitExpirableSemaphoreReactive` object added  
Feature - `RMap.fastReplace` method added  
Feature - PING support for Pub/Sub connections  

Improvement - `RBatch` object settings extracted as BatchOptions object  
Improvement - `RBitSet.set` method should return boolean  

Fixed - New IP discovery through DNS doesn't work for cluster mode  
Fixed - replication for Tomcat Session Manager with `readMode=INMEMORY`  
Fixed - `pingConnectionInterval` is not applied  
Fixed - JDK10 compatibility  
Fixed - `RLiveObjectService.delete` throws NPE if entity doesn't exist  
Fixed - `RSemaphore` object doesn't work with zero permit  
Fixed - `RExecutorService.countActiveWorkers` hangs if there are no registered workers  
Fixed - Iterator loop and incorrect result returning  
Fixed - SSL connection to Azure redis is failed  
Fixed - NPE in SentinelConnectionManager  
Fixed - RemoteService synchronous invocations aren't thread safe (regression since 2.10.5 / 3.5.5 versions)  
Fixed - `bad argument #1 to 'len' (string expected, got boolean)` error arise for `RMapCache` object  
Fixed - `RedisTimeoutException` arise during blocking command execution on RBlockingQueue and RBlockingDeque objects  

### 09-Apr-2018 - versions 2.11.5 and 3.6.5 released
Feature - `RKeys.copy` method added  
Feature - `RObject.copy` method added  
Feature - `RSetCache.getLock` method added  

Fixed - `ClusterConnectionManager` throws `IllegalArgumentException`  
Fixed - `CommandDecoder` doesn't remove command from commands queue when response was decoded with error  
Fixed - `RSetMultimap.get()` doesn't create multimap entry in case of absence  
Fixed - an error shouldn't appear if Redisson successfully got the information at least from one sentinel/cluster Redis node  
Fixed - `RObject.migrate` method  
Fixed - hdel comand wasn't used during remote service task removal  

### 27-Mar-2018 - versions 2.11.4 and 3.6.4 released

Feature - `RSet.getLock` method added  

Fixed - race condition with load balancer node selection  
Fixed - `READONLY can't write against a read only slave` error during failover  
Fixed - NPE during failover in Sentinel mode  
Fixed - `JCache.getAll` causes io.netty.util.IllegalReferenceCountException  
Fixed - NPE in CommandDecoder handler while using RBatch object  
Fixed - `RSortedSet` object tries to compare wrong types  
Fixed - `ClassCastException` in `RListMultimapCache.removeAll` method  

### 14-Mar-2018 - versions 2.11.3 and 3.6.3 released

Feature - DNS monitoring for Sentinel nodes  

Fixed - Old/stale nodes not removed from NodesGroup  
Fixed - CertificateException while connecting over SSL to Azure or AWS Elasticache config endpoint  
Fixed - publish subscribe connections couldn't be resubscribed during failover  
Fixed - RedissonRedLock.tryLock doesn't work for some values of wait time parameter  
Fixed - NPE in JCache.getAndRemoveValue  
Fixed - memory leak in publish subscribe  
Fixed - codec classLoader wasn't used in `ExecutorService` and `RemoteService` objects  
Fixed - warning for disconnected slaves in sentinel mode  

### 05-Mar-2018 - versions 2.11.2 and 3.6.2 released

[Redisson PRO](https://redisson.pro) performance improvements for follow `performanceMode` values:

`HIGHER_THROUGHPUT` - up to **25%** performance growth  
`LOWER_LATENCY_AUTO` - up to **100%** performance growth  
`LOWER_LATENCY_MODE_2` - up to **100%** performance growth  
`LOWER_LATENCY_MODE_1` - up to **100%** performance growth  

Feature - new values added to `performanceMode` setting  
Feature - `lockAsync` and `unlockAsync` methods added to `RedissonMultiLock`  
Feature - `RMapCache.remainTimeToLive` method added  
Feature - `Config.addressResolverGroupFactory` setting added (thanks @Hai Saadon)  

Improvement - `UpdateMode.AFTER_REQUEST` update mode optimization in tomcat session manager  
Improvement - removed ByteBuf object usage during collection iteration  

Fixed - `Unable to send command` error under heavy load using Redisson PRO  
Fixed - `expire`, `expireAt` and `clearExpire` commands aren't implemented properly for `RBloomFilter`, `RDelayedQueue`, `RFairLock`, `RLocalCachedMap` and `RPermitExpirableSemaphore` object  
Fixed - Redis clients duplication during discovering ip change of hostname  
Fixed - tomcat session renewal in tomcat session manager  
Fixed - `failedAttempts` setting should be applied to Slave nodes only  

### 15-Feb-2018 - versions 2.11.1 and 3.6.1 released

Feature - `RedissonClusteredSpringLocalCachedCacheManager` added. Please read [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#1421-spring-cache-local-cache-and-data-partitioning) for more details  
Feature - `rangeTailReversed`, `rangeHeadReversed` and `rangeReversed` methods added to `RLexSortedSet` object  
Feature - `RBucketsAsync` interface added  
Feature - `scanInterval` setting added for Sentinel mode  
Feature - `RLocalCachedMap.clearLocalCache` method added  

Fixed - remove `hset` command invocation during `RMapCache` entry loading  
Fixed - buffer leak in `replace` and `remove` methods of `RLocalCachedMap` object  
Fixed - `RRemoteService` object throws NPE  
Fixed - Multimap cluster compatibility  
Fixed - Enum support for Live Objects  
Fixed - Jackson 2.9 compatibility  
Fixed - `RTopic.removeAllListeners` got blocked on invocation  
Fixed - possible pubsub listeners leak  
Fixed - `RBatch` throws NPE with big pipeline in atomic mode  
Fixed - Warning about `CommandDecoder.decode()` method  

### 29-Jan-2018 - versions 2.11.0 and 3.6.0 released

Feature - __`atomic` setting added to RBatch object__  Please read [documentation](https://github.com/redisson/redisson/wiki/10.-additional-features#103-execution-batches-of-commands) for more details  
Feature - __`updateMode` setting added to Tomcat Redis Session Manager__  Please read [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#145-tomcat-redis-session-manager) for more details  
Feature - __`RateLimiter` object added__  Please read [documentation](https://github.com/redisson/redisson/wiki/6.-distributed-objects/#612-ratelimiter) for more details  
Feature - __`RClusteredBloomFilter` object added__  Please read [documentation](https://github.com/redisson/redisson/wiki/6.-Distributed-objects#681-bloom-filter-data-partitioning) for more details  
Feature - __`KQueue` support added__  Please read [documentation](https://github.com/redisson/redisson/wiki/2.-Configuration#eventloopgroup) for more details  
Feature - __`Tomcat 9` support added__  Please read [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#145-tomcat-redis-session-manager) for more details  
Feature - __`RPriorityBlockingQueue` object added__ Please read [documentation](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#718-priority-blocking-queue) for more details  
Feature - __`RPriorityBlockingDeque` object added__ Please read [documentation](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#719-priority-blocking-deque) for more details  
Feature - __`RLongAdder` object added__ Please read [documentation](https://github.com/redisson/redisson/wiki/6.-distributed-objects/#610-longadder) for more details  
Feature - __`DoubleAdder` object added__ Please read [documentation](https://github.com/redisson/redisson/wiki/6.-distributed-objects/#611-doubleadder) for more details  
Feature - `RBucket.getAndDelete`, `RAtomicLong.getAndDelete` and `RAtomicDouble.getAndDelete` methods added  
Feature - __`RAtomicDoubleReactive` object added__  
Feature - `RPriorityQueue.pollLastAndOfferFirstTo` method added  

Improvement - support single config endpoint node for cluster mode  
Improvement - hash functions replaced with https://github.com/google/highwayhash  

Fixed - JDK 1.6+ compatibility for RemoteService  
Fixed - `setDnsMonitoringInterval(-1)` doesn't disable DNS monitoring  
Fixed - `RLocalCachedMap.putAll` gets stuck if map passed as parameter contains > 10000 elements  
Fixed - `RLocalCachedMap.put` value encoding  
Fixed - `RKeys.countExists` and `RKeys.touch` return wrong result in cluster mode  
Fixed - Wrong parsing of RScript.ReturnType.MULTI result  
Fixed - RedissonReadLock by name with colon couldn't be unlocked properly  
Fixed - `rg.springframework.cache.Cache$ValueRetrievalException`shouldn't be wrapped by IllegalStateException  
Fixed - `RMapCache` listeners are not working on cross-platform environment  
Fixed - JsonJacksonCoded shouldn't override provided objectMapper settings (thanks to @gzeskas)  

### 25-Dec-2017 - versions 2.10.7 and 3.5.7 released

Feature - __`RClusteredBitSet` object added__ Please read [documentation](https://github.com/redisson/redisson/wiki/6.-Distributed-objects/#641-bitset-data-partitioning) for more details  

Improvement - Memory consumption optimization. `RExecutorFuture` and `RScheduledFuture` shouldn't be tracked if they weren't stored  
Improvement - Config settings `codecProvider` and `redissonReferenceEnabled` renamed to `referenceCodecProvider` and `referenceEnabled`  
Fixed - `RedissonRedLock` couldn't be locked in some cases  
Fixed - `RRemoteService` can't ack when running redis in cluster mode  
Fixed - DNSMonitor fails on bootstrap with custom event loop  
Fixed - Redis domain name IP address renew process  

### 08-Dec-2017 - versions 2.10.6 and 3.5.6 released

Feature - `RSetMultimapReactive` object added  
Feature - `RListMultimapReactive` object added  
Feature - `ReconnectionStrategy` and `SyncStrategy` added to `LocalCachedMapOptions`  
Feature - `pingConnectionInterval` setting added  

Improvement - added sync on key for `JCache.invoke` method  

Fixed - arguments encoding for `RScript`  
Fixed - `MapLoader` hangs if loaded value is null  
Fixed - OutOfMemory during `RExecutorService` usage  
Fixed - NPE in `RedissonSession.save` method  
Fixed - NPE during `RExecutorService` usage  
Fixed - `SnappyCodec` can't handle data more than 32Kb  
Fixed - failed to continue poll remote responses if current waiting for response has been canceled  
Fixed - SSL support for cluster mode  
Fixed - task with longer start time shouldn't overlap current task with shorter start time  
Fixed - DNS monitor caches host name binding  
Fixed - ReadMode.MASTER_SLAVE should be taken in account  
Fixed - slave nodes should be added without freeze state  
Fixed - master node should be disabled if any slave up  
Fixed - handling connection to Redis nodes returned to online state  

### 31-Oct-2017 - versions 2.10.5 and 3.5.5 released
`ProjectReactor` dependency for `3.5.5` version was updated to `3.1.1` version  

Feature - Added pingConnection, keepAlive, tcpNoDelay settings  
Feature - Slaves synchronization support for `RBatch`/`RBatchReactive` objects  

Improvement - Data encoding should be executed on client thread only  
Improvement - Handling Redis redirect optimization  
Improvement - Better collection handling for RedissonReference (thanks to Rui Gu)  

Fixed - `RedisLoadingException` handling during re-connection process  
Fixed - `RedisClient` can't be shutdown properly  
Fixed - timeout drift for `RFairLock`  
Fixed - expiration handling of reentrant write lock  
Fixed - `RReadWriteLock` doesn't work in cluster  
Fixed - Blocking queues are't rethrow exceptions  
Fixed - out of connections problem on high load during `RemoteExecutorService`/`ExecutorService` usage  
Fixed - NPE during `RemoteService` object usage  
Fixed - Getting memory leak warnings when gracefully shutting down tomcat  
Fixed - `RMapCache.getAll` doesn't support large keySet  

### 28-Sep-2017 - versions 2.10.4 and 3.5.4 released
Feature - added `maxSize` setting for `SpringCacheManager`  
Feature - allow `LiveObjectService` to work with classes that inherit from REntities (thanks to @sdjacobs)  

Improvement - `RMapCache` entires eviction process optimized  
Improvement - handling of slave down process  

Fixed - operation on slave coldn't be executed after its restart (thanks to @xavierfacq) 
Fixed - `ArrayIndexOutOfBoundsException` in RedissonSessionRepository  
Fixed - storing Live Objects in Redisson's collection objects (thanks to Rui Gu)  
Fixed - cancel write operation for commands belong to disconnected connection  
Fixed - possible race-condition during cancellation of write operation to Redis connection  
Fixed - accessor methods in Live Objects break if they start with 'is' (thanks to @sdjacobs)  
Fixed - MapReduce `Collator` couldn't be executed if timeout was defined  
Fixed - RedissonKeys.delete throws `NullPointerException` in some cases  
Fixed - `CancellationException` handling during RemotePromise cancellation  
Fixed - `RedisNodeNotFoundException` should be supplied to Failed Promise  

### 13-Sep-2017 - versions 2.10.3 and 3.5.3 released
Fixed - ByteBufs are not released properly in SnappyCodec and LZ4Codec (regression since 2.10.2 and 3.5.2)

### 12-Sep-2017 - versions 2.10.2 and 3.5.2 released
Feature - added `addScoreAndGetRank` and `addScoreAndGetRevRank` methods to `RScoredSortedSet` object  
Feature - added `addAndGetRank` and `addAndGetRevRank` methods to `RScoredSortedSet` object (thanks to @zuanoc)  
Feature - added support for bounded `RMapCache` object using `trySetMaxSize` `setMaxSize` methods (thanks to @johnou)  
Feature - added search by pattern using `iterator` method of `RSet` objects  
Feature - added search by pattern using `keySet`, `values` and `entrySet` methods of `RMap` objects  
Feature - `addScoreAndGetRank` and `addScoreAndGetRevRank` methods were added to `RScoredSortedSet` object  
Improvement - memory allocation optimization during encoding process  
Fixed - fixed NPE in LoadBalancerManager (regression since 2.10.1 and 3.5.1)  
Fixed - `RAtomicDouble.decrementAndGet`  
Fixed - connection could be in closed state during reconnection process for blocking queue  


### 29-Aug-2017 - versions 2.10.1 and 3.5.1 released

Feature - DNS monitoring support for Sentinel, Master/Slave and Replicated mode  
Feature - `org.redisson.codec.CompositeCodec` added  
Feature - added `readMode` property for Tomcat RedissonSessionManager  

Fixed - `RMapCache.putAll` couldn't handle map with entries amount larger than 5000  
Fixed - master entry should be shutdown in slaveConnectionPool during master change process  
Fixed - Redisson's Tomcat Session attributes should be read first to avoid invalidated session exception  

### 28-Jul-2017 - versions 2.10.0 and 3.5.0 released

Feature - __Local Cache support for Hibernate Cache__ Please read [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#1431-hibernate-cache-local-cache) for more details  
Feature - __Local Cache support for Spring Cache__ Please read [documentation](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#1421-spring-cache-local-cache) for more details  
Feature - __`RedissonLocalCachedMapCache` object added__ Please read [documentation](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#713-map-local-cache-for-expiring-entries) for more details  
Feature - __`BlockingFairDeque` object added__ Please read [documentation](https://github.com/redisson/redisson/wiki/7.-distributed-collections#714-blocking-fair-deque) for more details  
Feature - __`RLockReactive` object added__  
Feature - __`RReadWriteLockReactive` object added__  
Feature - __`RSemaphoreReactive` object added__  
Feature - `unlink`, `flushdbParallel`, `flushallParallel` methods added  

Fixed - ContextClassLoader should be used by Redisson Codec for Tomcat session's object serialization  
Fixed - Spring Cache `NullValue` does not implement Serializable  
Fixed - `RLocalCachedMap` doesn't work with non-json and non-binary codecs  
Fixed - Tomcat RedissonSessionManager doesn't remove session on invalidation/expiration  
Fixed - `RedissonBatch` shouldn't require `reactor.fn.Supplier` dependency  
Fixed - Spring Session 1.3.x compatibility (thanks to Vcgoyo)  
Fixed - priority queues should acquire lock before polling the element  

### 12-Jul-2017 - versions 2.9.4 and 3.4.4 released

Feature - [Config.performanceMode](https://github.com/redisson/redisson/wiki/2.-Configuration/_edit#performancemode) setting added  
Feature - JsonJacksonMapCodec codec added  
Feature - [Amazon Ion](https://amzn.github.io/ion-docs/) codec added  
Feature - [read-through, write-through and write-behind](https://github.com/redisson/redisson/wiki/7.-Distributed-collections/#714-map-persistence) support for RMap objects  
Feature - `RExecutorService` should return RExecutorFuture object with taskId  
Feature - added `RList.get` method to load elements in a batch  
Feature - ability to submit few tasks atomically (in batch) through `RExecutorService` interface  
Feature - [Config.keepPubSubOrder](https://github.com/redisson/redisson/wiki/2.-Configuration#keeppubsuborder) setting added  

Improvement - make `RMapReactive` and `RMapCacheReactive` interfaces match with `RMap` and `RMapCache`  
Improvement - `RLexSortedSet` should extend `RSortedSet`  

Fixed - connection listener is not invoked in some cases  
Fixed - `RMapCache` `remove`, `put`, `putIfAbsent` and `replace` methods aren't respect entry expiration  
Fixed - `SCAN` command should be used in `RKeys.deleteByPattern` method  
Fixed - `RBinaryStream` doesn't work in Redis cluster environment  
Fixed - `SELECT` command shouldn't be executed on Sentinel servers  
Fixed - Stackoverflow error arise during decoding of large amount of PubSub messages  
Fixed - `LocalCachedMapInvalidate` object can't be serialized by Kryo codec  
Fixed - `XMLGregorianCalendar` type handling in JSON codec  
Fixed - Reactive Stream methods shouldn't be executed immediately after `Publisher` object creation  

### 10-Jun-2017 - versions 2.9.3 and 3.4.3 released

Since this version, if you use programmatic config definition you should define full url with schema.

```java
config.setAddress("redis://127.0.0.1:6739");
// or for SSL support
config.setAddress("rediss://127.0.0.1:6739");
```

Feature - __SSL support__  
Feature - __[RedisLabs](http://redislabs.com) hosting support__  
Feature - `RBlockingQueue.takeLastAndOfferFirstTo` method added  
Feature - `RScoredSortedSet.firstScore, lastScore` methods added  
Feature - `RedissonCacheManager.setAllowNullValues` method added  
Feature - `RedissonSpringCacheManager.setCacheNames` method added  
Feature - Map Entry listeners support added for `RMapCache` object  
Feature - `Config.lockWatchdogTimeout` parameter added  

Improvement - NPE checking for key and value added for RedissonMapCache  
Improvement - `RKeys.deleteByPatternAsync` uses scan command  

Fixed - `RBoundedBlockingQueue.pollAsync` blocks if timeout is less than 1 second  
Fixed - unlocking of nested `RReadWriteLock.readLock` deletes current acquired `RReadWriteLock.writeLock`  
Fixed - wrong null values checking in RDelayedQueue methods  
Fixed - probability of infinite scan for all iterators  
Fixed - `Node.InfoSection` should be public  
Fixed - JSR107 cache implementation should throw `javax.cache.CacheException` in case of any error  

### 10-May-2017 - versions 2.9.2 and 3.4.2 released

Feature - __Dropwizard metrics integration__ More details [here](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks#147-dropwizard-metrics)  
Feature - `RLocalCachedMap.preloadCache` method added (thanks to Steve Draper)  
Feature - `RGeo.radiusStoreTo` methods added (thanks to Cory Sherman)  

Fixed - NoClassDefFoundError exception during using `redisson-all` module

### 27-Apr-2017 - versions 2.9.1 and 3.4.1 released

Fixed - `RLocalCachedMap.getAll` didn't use cache (thanks to Steve Draper)  
Fixed - reference to avro module has been removed

### 26-Apr-2017 - versions 2.9.0 and 3.4.0 released

Feature - __`MapReduceService` added__ More details [here](https://github.com/redisson/redisson/wiki/9.-distributed-services/#95-distributed-mapreduce-service)  
Feature - `readAllMap` and `readAllMapAsync` methods added to `RMap`  
Feature - `readAllKeySet` and `getReadWriteLock` methods added to `RMultimap`  
Feature - `RKeys.delete` by objects method added  
Feature - `RRemoteService.deregister` method added  
Feature - `retryAttempts`, `retryInterval` and `timeout` methods added to `RBatch` object  
Feature - `RMapCache.fastPutIfAbsent` with ttl added (thanks to Dobi)  
Feature - `EvictionPolicy.WEAK` added for `RLocalCachedMap`  
Feature - `LocalCachedMapOptions.invalidationPolicy` introduced for `RLocalCachedMap`  
Feature - `expire`, `expireAt`, `move`, `migrate`, `clearExpire`, `renamenx`, `rename`, `remainTimeToLive` methods added to RKey  

Improvement - `EvictionPolicy.LRU` optimization for `RLocalCachedMap`  

Fixed - `RTopic.onSubscribe` should be invoked after failover process  
Fixed -  Spring boot with redisson 3.3.2 fails without optional actuator dependency (thanks to  Rick Perkowski)  
Fixed - `RedissonCacheMap.putIfAbsentAsync` doesn't take in account ttl and minIdleTime params (thanks to Dobi)  
Fixed - Spring cache should put NullValue object instead of null  
Fixed - Fixed error - No field factory in class Ljava/net/URL  
Fixed - Spring cache's method with `@Cacheable(sync=true)` annotation never expires (thanks to Dobi)  
Fixed - spring schema file corrected (thanks to Rui Gu)  
Fixed - Prevent to set URL.factory to null in case of concurrent URL creation in the URLBuilder (thanks to Björn-Ole Ebers)  
Fixed - `RMap.addAndGet` causes bad argument (thanks to Rui Gu)  
Fixed - `RedissonSpringCacheManager` creates new cache on each `getCache` call  
Fixed - wrong value codec encoder usage for `RedissonLocalCachedMap.fastPutAsync` method

### 21-Mar-2017 - versions 2.8.2 and 3.3.2 released

Feature - Redisson's Spring custom namespace support (thanks to Rui Gu)  
Feature - ability to set custom connection manager (thanks to Saikiran Daripelli)  
Feature - autoconfigured Spring Boot CacheStatisticsProvider implementation (thanks to Craig Andrews)  
Feature - `RKeys.touch` and `RObject.touch` methods added  
Feature - `RedissonCompletionService` implementation added  
Feature - `RMap.getReadWriteLock` method added  

Fixed - NPE during `RLocalCachedMap.fastRemove` invocation  
Fixed - `redisson-tomcat-8` module is not compatible with Tomcat 8.5  
Fixed - URLBuilder methods should be synchronized  
Fixed - use PSETEX in `RBucket.set` method  
Fixed - `DelayedQueue.remove()` and `DelayedQueue.removeAll()`  
Fixed - unable to define Type and AvroSchema for AvroJacksonCodec  
Fixed - ReadWriteLock leaseTimeout calculation  
Fixed - `Config.fromJson(file)` method, throws StackOverflowError

### 04-Mar-2017 - versions 2.8.1 and 3.3.1 released

Feature - Cache with SoftReference support added for `RLocalCachedMap`  
Feature - `Config.subscriptionMode` setting added  

Improvement - errors handling during RBatch execution  

Fixed - StackOverflowException in URLBuilder  
Fixed - TomcatSessionManager can't be used in Tomcat if Redisson has been deployed in web application  
Fixed - skip cluster nodes with the "handshake" flag (thanks to @dcheckoway)

### 19-Feb-2017 - versions 2.8.0 and 3.3.0 released

Feature - __`RClusteredLocalCachedMap` object added__ More details [here](https://github.com/redisson/redisson/wiki/7.-distributed-collections#713-map-data-partitioning)  
Feature - __`RClusteredMapCache` object added__ More details [here](https://github.com/redisson/redisson/wiki/7.-distributed-collections#713-map-data-partitioning)  
Feature - __`RClusteredSetCache` object added__ More details [here](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#732-set-data-partitioning)  
Feature - __`RPriorityQueue` object added__ More details [here](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#715-priority-queue)  
Feature - __`RPriorityDeque` object added__ More details [here](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#716-priority-deque)  
Feature - `removeAllListeners` and `removeListener` by instance methods added for `RTopic` and `RPatternTopic`  
Feature - `RLockAsync` interface added  

Improvement - `RRemoteService` is now able to support method overload  

Fixed - `RLocalCachedMap` is not Redis cluster compatible  
Fixed - cascade slaves are not supported in cluster mode  
Fixed - shutdown checking during master change state check added  
Fixed - master isn't checked during new slave discovery in Sentinel mode  

### 02-Feb-2017 - versions 2.7.4 and 3.2.4 released

Feature - Allow to specify Redisson instance/config during JCache cache creation  

Fixed - `ByteBuf.release` method invocation is missed in `LZ4Codec` and `SnappyCodec`  
Fixed - AssertionError during Redisson shutdown  
Fixed -  `RReadWriteLock.readLock` couldn't be acquired by same thread which has already acquired `writeLock`  
Fixed -  failed `RFairLock.tryLock` attempt retains caller thread in fairLock queue  
Fixed - `factory already defined` error  
Fixed - `JCache` expiration listener doesn't work  
Fixed - `RLocalCachedMap` doesn't work with `SerializationCodec`  
Fixed - `Can't find entry` error during operation execution on slave nodes  

### 19-Jan-2017 - versions 2.7.3 and 3.2.3 released

Redisson Team is pleased to announce __ULTRA-FAST__ Redisson PRO edition.  
Performance measure results available in [Benchmark whitepaper](https://redisson.pro/Redisson%20PRO%20benchmark%20whitepaper.pdf)

Feature - `RMap.getLock(key)` and `RMultimap.getLock(key)` methods added  

Improvement - `RedissonSpringCacheManager` constructor with Redisson instance only added  
Improvement - `CronSchedule` moved to `org.redisson.api` package  

Fixed - RedissonBaseIterator.hasNext() doesn't return false in some cases  
Fixed - NoSuchFieldError exception in `redisson-tomcat` modules  
Fixed - ConnectionPool size not respected during redirect of cluster request  
Fixed - `RSortedSet.removeAsync` and `RSortedSet.addAsync`  
Fixed - `RBloomFilter.tryInit` were not validated properly  
Fixed - CommandDecoder should print all replay body on error  

### 19-Dec-2016 - versions 2.7.2 and 3.2.2 released

Feature - `RList`, `RSet` and `RScoredSortedSet` implements `RSortable` interface with SORT command support  
Feature - `NodeAsync` interface  
Feature - `Node.info`, `Node.getNode` methods added  

Fixed - elements distribution of `RBlockingFairQueue` across consumers  
Fixed - `factory already defined` error during Redisson initialization under Apache Tomcat  

### 14-Dec-2016 - versions 2.7.1 and 3.2.1 released

Url format used in config files __has changed__. For example:

"//127.0.0.1:6739" now should be written as "redis://127.0.0.1:6739"

Feature - `RSet.removeRandom` allows to remove several members at once  

Fixed - exceptions during shutdown  
Fixed - redis url couldn't contain underscore in host name  
Fixed - IndexOutOfBoundsException during response decoding  
Fixed - command timeout didn't respect during topic subscription  
Fixed - possible PublishSubscribe race-condition  
Fixed - blocking queue/deque poll method blocks infinitely if delay less than 1 second  

### 26-Nov-2016 - versions 2.7.0 and 3.2.0 released

Feature - __Spring Session implementation__. More details [here](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#145-spring-session)  
Feature - __Tomcat Session Manager implementation__. More details [here](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#144-tomcat-redis-session-manager)  
Feature - __RDelayedQueue object added__. More details [here](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#714-delayed-queue)  
Feature - __RBlockingFairQueue object added__. More details [here](https://github.com/redisson/redisson/wiki/7.-distributed-collections/#713-blocking-fair-queue)  
Feature - `RSortedSet.readAll` and `RQueue.readAll` methods added  
Fixed - `RMap.getAll` doesn't not preserve the order of elements  
Fixed - Wrong nodes parsing in result of cluster info command  
Fixed - NullPointerException in CommandDecoder.handleResult  
Fixed - Redisson shutdown status should be checked during async command invocation  

### 07-Nov-2016 - versions 2.6.0 and 3.1.0 released

Feature - __new object added__ `RBinaryStream`. More info about it [here](https://github.com/redisson/redisson/wiki/6.-distributed-objects/#62-binary-stream-holder)  
Improvement - limit Payload String on RedisTimeoutException  
Improvement - Elasticache master node change detection process optimization  

### 27-Oct-2016 - versions 2.5.1 and 3.0.1 released

Include all code changes from __2.2.27__ version

Fixed - RMapCache.fastPutIfAbsentAsync doesn't take in account expiration  
Fixed - timer field of RedisClient hasn't been initialized properly in some cases  

### 27-Oct-2016 - version 2.2.27 released

This version fixes old and annonying problem with `ConnectionPool exhusted` error. From this moment connection pool waits for free connection instead of throwing pool exhausted error. This leads to more effective Redis connection utilization.

Improvement - remove `Connection pool exhausted` exception  

### 17-Oct-2016 - version 3.0.0 released
Fully compatible with JDK 8. Includes all code changes from __2.5.0__ version

Feature - `RFeature` extends `CompletionStage`

### 17-Oct-2016 - version 2.5.0 released
This version brings greatly improved version of `RLiveObjectService` and adds cascade handling, cyclic dependency resolving, simplified object creation. Read more in this [article](https://dzone.com/articles/java-distributed-in-memory-data-model-powered-by-r)

Includes all code changes from __2.2.26__ version

Feautre - COUNT and ASC/DESC support for `RGeo` radius methods  
Feature - `RGeo` extends `RScoredSortedSet`  
Feature - `RCascade` annotation support LiveObjectService  
Improvement - `RId` generator should be empty by default  
Improvement - support setter/getter with protected visibility scope for LiveObject  
Fixed - `RMapCache` doesn't keep entries insertion order during iteration  
Fixed - `@RId` is returned/overwritten by similarly named methods (thanks to Rui Gu)  
Fixed - typo `getRemoteSerivce` -> `getRemoteService` (thanks to Slava Rosin)  
Fixed - `RPermitExpirableSemaphore.availablePermits` doesn't return actual permits account under certain conditions  
Fixed - `readAllValues` and `readAllEntrySet` methods of `RLocalCacheMap` return wrong values  
Fixed - setter for collection field of LiveObject entity should rewrite collection content  
Fixed - `RSetCache` TTL not updated if element already present  
Fixed - `RLiveObjectService` swallow exceptions during `merge` or `persist` operation
Fixed - `RLiveObjectService` doesn't support protected constructors  
Fixed - object with cyclic dependencies lead to stackoverflow during `RLiveObjectService.detach` process  
Fixed - not persisted `REntity` object allowed to store automatically  
Fixed - `RLexSortedSet.addAll` doesn't work  
Fixed - `RLiveObjectService` can't detach content of List object  
Fixed - `RLiveObjectService` doesn't create objects mapped to Redisson objects in runtime during getter accesss  
Fixed - `RLiveObjectService` can't recognize id field of object without setter  

### 17-Oct-2016 - version 2.2.26 released
Fixed - NPE in CommandDecoder  
Fixed - PubSub connection re-subscription doesn't work in case when there is only one slave available

### 27-Sep-2016 - version 2.4.0 released
Includes all code changes from __2.2.25__ version

Feature - __new object added__ `RPermitExpirableSemaphore`. More info about it [here](https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers#87-permitexpirablesemaphore)  
Feature - __new object added__ `RLocalCachedMap`. More info about it [here](https://github.com/redisson/redisson/wiki/7.-distributed-collections#712-map-local-cache)  
Feature - __references support__ (thanks to Rui Gu) More info about it [here](https://github.com/redisson/redisson/wiki/10.-Additional-features#102-references-to-redisson-objects)  
Feature - __Docker support__ More info about it [here](https://github.com/redisson/redisson/wiki/12.-Standalone-node#126-how-to-run-using-docker)  
Feature -  `RSemaphore.reducePermits` method added  
Feature - `nettyThreads` and `executor` settings added  
Feature - `valueRangeReversed`, `entryRangeReversed`, `union` and `intersection` methods added to `RScoredSortedSet` object  
Feature - `Node.time` method added  
Feature - `RMap.valueSize` method added  
Feature - `RBucket.size` method added  
Feature - `RBatch.executeSkipResult` method added  
Improvement - Redisson Node could be created with existing Redisson instance  
Improvement - `RMultimap.get` should return `RSet` or `RList` interface instead of `Set` and `List`  
Fixed - `RExecutorService` should reject non-static inner task class  
Fixed - wrong object encoding in `RScoredSortedSet.addScore` method  

### 27-Sep-2016 - version 2.2.25 released
Improvement - log unexpected errors in netty handlers  
Improvement - `Not all slots are covered` error should be more informative  
Improvement - implement random wait time in `lock` method of `RedissonRedLock` and `RedissonMultiLock` objects  
Fixed - `ClassCastException` error when there are no slaves in sentinel mode  
Fixed - keep RMap insertion ordering while iteration  
Fixed - thread stuck at `lock` method of `RedissonRedLock` and `RedissonMultiLock` objects  
Fixed - incorrect `tryLock` behaviour of `RedissonRedLock` and `RedissonMultiLock` objects  
Fixed - connection shouldn't be closed on exceptionCaught  
Fixed - Jackson 2.8.x compatibility  
Fixed - TRYAGAIN error handling in cluster mode  
Fixed - sync commands in connectionListener leads to connection timeout exception  
Fixed - can't find slave error in cluster mode if failed slave hasn't been added before  

### 24-Aug-2016 - version 2.3.0 released
Starting from this version Redisson could be run as standalone node to execute distributed tasks. More features will be added to it in future. Read docs about it [here](https://github.com/mrniko/redisson/wiki/12.-Standalone-node)

Feature - __new service added__ `RExecutorService`. More info about it [here](https://github.com/mrniko/redisson/wiki/9.-distributed-services/#93-distributed-executor-service)  
Feature - __new service added__ `RScheduledExecutorService`. More info about it
[here](https://github.com/mrniko/redisson/wiki/9.-distributed-services#94-distributed-scheduled-executor-service)  
Feature - __new service added__ `RLiveObjectService`. More info about it
[here](https://github.com/mrniko/redisson/wiki/9.-distributed-services/#92-live-object-service) (big thanks to Rui Gu for this amazing feature)  
Feature - __new object added__ `RBoundedBlockingQueue`. More info about it [here](https://github.com/mrniko/redisson/wiki/7.-distributed-collections/#711-bounded-blocking-queue)  
Feature - __Redis deployment tool__. More info about it
[here](https://github.com/mrniko/redisson/wiki/13.-Tools#131-redis-deployment-tool)  
Feature - __Cluster management tool__. More info about it [here](https://github.com/mrniko/redisson/wiki/13.-Tools#132-cluster-management-tool)  
Feature - Avro and Smile codecs added  
__Breaking api change__ - all config classes moved to `org.redisson.config` package  
__Breaking api change__ - all classes moved from `org.redisson.core` to `org.redisson.api` package  
__Breaking api change__ - switched from `io.netty.util.concurrent.Future` to `org.redisson.api.RFuture` interface  
Fixed - division by zero in WeightedRoundRobinBalancer (thanks to Shailender R Bathula)

### 08-Aug-2016 - version 2.2.24 released
Fixed - PubSub connection in cluster mode should be connected to node according slot derived from channel name  
Fixed - `RLock.tryLock` could block forever under some conditions  

### 04-Aug-2016 - version 2.2.23 released
Improvement - `Future.cancel` method handling for RemoteService async call  
Fixed - unable to redefine RedisClient command execution timeout  
Fixed - exception occured in CommandEncoder leads to reponse timeout exception  
Fixed - exception occured in CommandDecoder leads to reponse timeout exception  
Fixed - BLPOP timeout calculation fixed  
Fixed - object used in RemoteService to prevent race-condition during ack receiving should be created per request  

### 26-Jul-2016 - version 2.2.22 released  
Fixed -  java.lang.UnsupportedOperationException during command batch usage with netty 4.0.38 and higher  

### 15-Jul-2016 - version 2.2.21 released  
Fixed - `RLock`, `RReadWriteLock`, `RSemaphore` and `RCountDownLatch` can blocks forever under some conditions  

### 14-Jul-2016 - version 2.2.20 released  
Fixed - NPE during pubsub re-subscription (regression since 2.2.18)  
Fixed - `RSortedSet` doesn't work in cluster mode (regression since 2.2.16)  
Fixed - PubSub connection pool initialization in cluster mode  
Fixed - NPE during pubsub usage in cluster mode (regression since 2.2.18)  

### 13-Jul-2016 - version 2.2.19 released  
Feature - `RSetReactive.readIntersection`, `RSetReactive.diff` and `RSetReactive.intersection` added  
Fixed - cluster commands handling regression (regression since 2.2.18)

### 13-Jul-2016 - version 2.2.18 released  
Feature - `RSet.randomAsync` and `RSet.random` commands added (thanks to dcheckoway)  
Feature - commandTimeout param added to RedisClient  
Feature - `JsonJacksonMapValueCodec` basic typed map value codec added (thanks to andrejserafim)  
Improvement - PubSub management has been reimplemented this resolves some issues with RLock, RSemaphore objects  
Fixed - disconnected pubsub connection leads to missed response for unsubscribe/punsubscribe operations  
Fixed - cluster slot changes discovery  
Fixed - execute all lock, semaphore and countdownlatch commands on master node  
Fixed - shutdown listeners added during blocking operations usage weren't removing in some cases  
Fixed - response parsing of cluster nodes command  
Fixed - Connections weren't closing during `RedisClient` shutdown  
Fixed - `RedissonRedLock.unlock`  

### 30-Jun-2016 - version 2.2.17 released  
Feature - `RMultimap.keySize` method added  
Feature - `RKeys.getType` method added  
Feature - `RKeys.getKeysByPattern` method with count param added  
Improvement - `RedissonMultiLock.lock` method optimization  
Feature - `RedissonRedLock` implemented  
Fixed - `RMapCache.delete` doesn't delete redisson__idle__set__  
Fixed - integer comparison in EvictionScheduler  
Fixed - ByteBuf leak (thanks to jackygurui)  
Fixed - `RTopic.addListener` method worked asynchronous sometimes  
Fixed - ClastCastException occurred if multi-type PubSub channels were used with single connection  
Fixed - PubSub status message decoding  
Fixed - RLock.lock can hang in some cases  
Fixed - PubSub subscription may stuck in some cases  
Fixed - return value of `RedissonMultimap.keySet.size` method  

### 12-Jun-2016 - version 2.2.16 released  
Feature - `RGeo`, `RMultimapCache` added to `RBatch`  
Feature - `fastRemove` and `fastRemoveAsync` methods were added to `RList`  
Improvement - added Spring 4.3.0 support to RedissonSpringCacheManager  
Improvement - `RSortedSet` performance boost up to __x4__  
Improvement - `RList.remove` optimization  
Improvement - ability to define `Codec` for `RRemoteService`  
Fixed - cluster state managing with redis masters only  
Fixed - dead lock during `RLock`, `RSemaphore`, `RReadWriteLock`, `RCountDownLatch` usage under heavy load  

### 08-Jun-2016 - version 2.2.15 released  
Improvement - Performance boost up to 30% for `RSortedSet.add` method  
Fixed - auth during reconnection (thanks to fransiskusx)  
Fixed - Infinity loop with iterator  
Fixed - NPE in `RSortedSet`  
Fixed - `RSortedSet.remove` and `iterator.remove` methods can break elements ordering  

### 27-May-2016 - version 2.2.14 released  
Redisson Team is pleased to announce [Redisson PRO](http://redisson.pro) edition. This version is based on open-source edition and has 24x7 support and some features.  

Feature - __data sharding for `RMap`, `RSet` structures in cluster mode__ available only in [Redisson PRO](http://redisson.pro) edition  
Feature - __new object added__ `RLock` with fair mode support  
Feature -  Ability to execute interface methods used for RemoteService in asynchronous way  
Feature - `RSemaphoreAsync` interface implemented  
Feature - `addBefore`, `addBeforeAsync`, `addAfter`,  `addAfterAsync` methods added to `RList` object  
Feature - `readAll`, `pollFirst`, `pollLast`, `first`, `last`, `revRankAsync`, `readAllAsync`, `pollFirstAsync`, `pollLastAsync`, `firstAsync`, `lastAsync` and `revRankAsync` methods added to `RLexSortedSet` object  
Feature - `count`, `countAsync`, `readAll`, `readAllAsync` methods added to `RScoredSortedSet` object  
Feature - `entryValueReversed`, `entryValueReversedAsync` methods added to`RScoredSortedSet` (thanks to weiqiyiji)  
Feature - Ability to specify the name prefix for `RRemoteService` (thanks to pierredavidbelanger)  
Feature - Ability to make remote call in fire-and-forget and ack-response modes only  (thanks to pierredavidbelanger)  
Improvement - optimized cluster redirect handling during `RBatch` execution  
Fixed - `RScoredSortedSet.retainAll` method works incorrectly in some cases  
Fixed - `getBlockingQueue` throws `IndexOutOfBoundsException` (thanks to jackygurui)  
Fixed - `GEODIST` command handling in `RGeo` object  (thanks to jackygurui)  
Fixed - `RObject.expireAt` method uses second instead of ms  
Fixed - don't make a remote call when toString, equals and hashCode are called via remote interface (thanks to pierredavidbelanger)  
Fixed - `RRemoteService` doesn't work correctly with serialzation codecs (thanks to pierredavidbelanger)  
Fixed - executors amount is not enforced (thanks to pierredavidbelanger)  
Fixed - FSTObjectOutput shouldn't be closed after write  
Fixed - possible race-condition during ack waiting in `RRemoteService` object  
Fixed - timeWait checking fixed in `RLock.tryLockAsync`  

### 30-Apr-2016 - version 2.2.13 released  

Feature - `RSet.diff` and `RSet.intersection` methods added  
Imporovement - `RScoredSortedSet`.`containsAll`, `removeAll` and `retainAll` methods speed optimization  
Imporovement - `RSetCache` memory and speed optimization  
Imporovement - `RSet`.`retainAll`, `containsAll`, `removeAll` methods speed optimized up to 100x  
Fixed - possible infinity `RLock` expiration renewal process  
Fixed - error during `RSetCache.readAll` invocation.  
Fixed - expiration override wasn't work in `RSetCache.add`  

### 22-Apr-2016 - version 2.2.12 released  

Imporovement - Replaying phase handling in CommandDecoder  
Fixed - cluster state update manager can't try next node if current node has failed to response   
Fixed - cluster initialization  
Fixed - items removing during `RMap` iteration  
Fixed - `RGeo.addAsync` codec definition  
Fixed - `RMapCache` iterator and readAll methods  
Fixed - unnecessary slots migration in cluster mode  
Fixed - Command batches redirect in cluster mode  
Fixed - cluster mode compatibility for `RedissonMultimap.fastRemove` method  
Fixed - `RedissonMultiLock` deadlock  
Fixed - MultiDecoder empty result handling  
Fixed - array start index in LUA scripts  
Fixed - RMap iterator  
Fixed - probably thread blocking issues  

### 04-Apr-2016 - version 2.2.11 released  

Since this version Redisson has __perfomance boost up to 43%__

Feature - __new object added__ `RGeo`  
Feature - __new object added__ `RBuckets`  
Feature - travis-ci integration (thanks to jackygurui)  
Improvement - `RScoredSortedSet.removeAllAsync` & `removeAll` methods optimization  
Improvement - `RemoteService` reliability tuned up  
Improvement - Reattaching RBlockingQueue\Deque blocking commands (poll, take ...) after Redis failover process or channel reconnection  
Fixed - iterator objects may skip results in some cases  
Fixed - RTopic listeners hangs during synchronous commands execution inside it  
Fixed - Redisson hangs during shutdown if `RBlockingQueue\Deque.take` or `RBlockingQueue\Deque.poll` methods were invoked  


### 23-Mar-2016 - version 2.2.10 released  

Feature - __new object added__ `RRemoteService`  
Feature - __new object added__ `RSetMultimapCache`  
Feature - __new object added__ `RListMultimapCache`  
Improvement - ability to cancel BRPOP and BLPOP async command execution  
Improvement - Config params validation  
Improvement - test RedisRunner improvements (thanks to jackygurui)  
Improvement - `Double.NEGATIVE_INFINITY` and `Double.POSITIVE_INFINITY` handling for ScoredSortedSet (thanks to jackygurui)  
Fixed - MOVED, ASK handling in cluster mode using RBatch  
Fixed - delete and expire logic for Multimap objects  
Fixed - `RLock.tryLockAsync` NPE  
Fixed - possible NPE during Redisson version logging  
Fixed - Netty threads shutdown after connection error  

### 04-Mar-2016 - version 2.2.9 released  

Feature - __new object added__ `RSetMultimap`  
Feature - __new object added__ `RListMultimap`  
Feature - `valueRangeReversed` and `valueRangeReversedAsync` methods were added to `RScoredSortedSet` object  
Improvement - Throw `RedisOutOfMemoryException` when OOM error from Redis server has occured  
Improvement - Node type added to optimization in Cluster mode  
Improvement - Add DynamicImport-Package to OSGi headers  
Fixed - `RedissonSpringCacheManager` Sentinel compatibility  
Fixed - `RAtomicLong.compareAndSet` doesn't work when expected value is 0 and it wasn't initialized  

### 12-Feb-2016 - version 2.2.8 released  

Feature - `union`, `unionAsync`, `readUnion` and `readUnionAsync` methods were added to `RSet` object  
Feature - `readAll` and `readAllAsync` methods were added to `RSetCache` object  
Improvement - `RKeys.delete` optimization in Cluster mode  
Fixed - Script error during `RSetCache.toArray` and `RSetCache.readAll` methods invocation  
Fixed - Sentinel doesn't support AUTH command  
Fixed - RMap iterator  

### 03-Feb-2016 - version 2.2.7 released  

Feature - `readAllKeySet`, `readAllValues`, `readAllEntry`, `readAllKeySetAsync`, `readAllValuesAsync`, `readAllEntryAsync` methods were added to `RMap` object  
Improvement - `RKeys.delete` optimization in Cluster mode  
Fixed - minimal connections amount initialization  
Fixed - `RKeys.deleteByPattern` throws an error in cluster mode  
Fixed - `RKeys.deleteAsync` throws error in Cluster mode  
Fixed - Redisson failed to start when one of sentinel servers is down  
Fixed - Redisson failed to start when there are no slaves in Sentinel mode  
Fixed - slave nodes up/down state discovery in Cluster mode  
Fixed - slave can stay freezed when it has been just added in Sentinel mode  
Fixed - offline slaves handling during Redisson start in Sentinel mode  
Fixed - `SELECT` command can't be executed in Sentinel mode  
Fixed - `database` setting removed from cluster config  

### 28-Jan-2016 - version 2.2.6 released  

Feature - __new object added__ `RedissonMultiLock`  
Feature - `move` method added to `RSet`, `RSetReactive` objects (thanks to thrau)  
Feature - `put` methods with `maxIdleTime` param added to `RMapCache` object  
Feature - `RList.subList` returns `live` view object  
Feature - `readAll` method added to `RList` and `RSet` objects  
Feature - `trim` method added to `RList` object  
Feature - ability to read/write Redisson config object from/to `JSON` or `YAML` format  
Feature - [Spring cache](http://docs.spring.io/spring/docs/current/spring-framework-reference/html/cache.html) integration  
Feature - `readMode` setting added  
Improvement - `RSetCache` object entry eviction optimization  
Improvement - `RList` object optimization  
Improvement - `RedissonCountDownLatchAsync` interface added  
Improvement - cluster restrictions removed from `loadBucketValues` and `saveBuckets` methods  
Fixed - wrong ByteBuf read position in all codecs based on `StringCodec`  
Fixed - can't connect with password to Sentinel and Elasticache servers  
Fixed - Cluster slave discovery (regression since 2.1.5)  
Fixed - Sentinel slave discovery (regression since 2.1.5)  

### 09-Jan-2015 - version 2.2.5 released  

Feature - __new object added__ `RBloomFilter`  
Feature - __new object added__ `RAtomicDouble`  
Feature - `tryAdd`, `tryAddAsync`, `addAll` and `addAllAsync` methods added to `RScoredSortedSet`  
Feature - `RKeys.count` and `RKeys.countAsync` methods added  
Feature - `RedissonClient.saveBuckets` method added  
Feature - `trySet`, `trySetAsync`, `compareAndSet` and `getAndSet` methods added to `RBucket`  
Feature - `fastPutIfAbsent` and `fastPutIfAbsentAsync` methods added to `RMap`  
Improvement - `RMap.putIfAbsent` optimization  
Improvement - `RBitSet` index range extended to Integer.MAX_VALUE*2  
Improvement - `RAtomicLong.getAndAdd` optimization  
Fixed - infinity loop during `RMap` iteration  
Fixed -  wrong timeout value used during `RBatch` execution  
Fixed - connection handling when `isReadFromSlaves = false`  
Fixed - `RMap.addAndGetAsync` key encoding  
Fixed - `RBatch` errors handling  
Fixed - `RBlockingQueue.pollLastAndOfferFirstToAsync` does not block properly  

### 25-Dec-2015 - version 2.2.4 released  
Please update to this version ASAP due to connection leak discovered in previous versions since Redisson 2.1.4.

Feature - __new object added__ `RBlockingDeque`  
Feature - __new object added__ `RSemaphore`  
Feature - `RMapCache.fastPut` method with TTL support added  
Feature - `WeightedRoundRobinBalancer` slaves balancer added  
Improvement - Memory consumption optimization  
Improvement - storing value with ttl = 0 in `RSetCache` or `RMapCache` saves it infinitely  
Fixed - reconnection handling when Sentinel servers are restarted  
Fixed - RedisConnectionException should be throw if Redisson can't connect to servers at startup   
Fixed - Connection leak (regression bug since 2.1.4)  
Fixed - ConnectionWatchdog throws exception when eventloop in shutdown state  
Fixed - `RReadWriteLock.forceUnlock` works only for current thread  
Fixed - MapKeyDecoder and MapValueDecoder are called in wrong order  
Fixed - `RReadWriteLock` doesn't work in cluster mode  

### 15-Dec-2015 - version 2.2.3 released  
Feature - ability to set connection listener via `Config.connectionListener` setting  
Fixed - `RLock` expiration bug fixed (regression bug since 2.2.2)  
Fixed - NPE in `RedissonSortedSet` constructor  

### 14-Dec-2015 - version 2.2.2 released  
Feature - `isShuttingDown` and `isShutdown` methods were added to RedissonClient and RedissonReactiveClient  
Feature - __new object added__ `RSetCacheReactive`  
Fixed - RLock expiration renewal task scheduling fixed (regression bug since 2.2.1)  
Fixed - RExpirable.expireAsync timeUnit precision fixed (regression bug since 2.2.1)  

### 11-Dec-2015 - version 2.2.1 released  
Feature - __new object added__ `RReadWriteLock` with reentrant read/write locking  
Feature - __new object added__ `RMapCache` map-based cache with TTL support for each entry  
Feature - __new object added__ `RSetCache` set-based cache with TTL support for each value  
Feature - `RBatchReactive.getKeys` method added  
Feature - `RMap.values()`, `RMap.keySet()`, `RMap.entrySet()` reimplemented with live-view objects  
Feature - `RObjectReactive.isExists`, `RObject.isExists` and `RObject.isExistsAsync` added  
Fixed - `RLock.unlock` not thrown IllegalMonitorStateException  

### 04-Dec-2015 - version 2.2.0 released  
Since 2.2.0 version Redisson supports [Reactive Streams](http://www.reactive-streams.org). Use `Redisson.createReactive` method to access Reactive objects.

Feature - [Reactive Streams](http://www.reactive-streams.org) support  
Feature - `RList.addAllAsync` and `RMap.getAllAsync` methods added  
Feature - `RList.equals` and `RList.hashCode` methods implemented  
Feature - `pollFirst`, `pollFirstAsync`, `pollLast`, `pollLastAsync` methods added to `RScoredSortedSet`  
Improvement - `RLock` and `RCountDownLatch` switched to `LongCodec`  
__Breaking api change__ - `RExpirable` methods now uses milliseconds instead of seconds  
Fixed - `RLock.delete` didn't check lock existence  

`Config.useMasterSlaveConnection` and `Config.useSentinelConnection` methods renamed to `Config.useSentinelServers` and `Config.useMasterSlaveServers` respectively  
Deprecated methods are dropped


### 30-Nov-2015 - version 2.1.6 released  
Fixed - connection pool regression bug  
Fixed - connection init during `Node.ping` and `ClusterNode.info` invocation  


### 24-Nov-2015 - version 2.1.5 released  
Feature - new methods with `limit` option support were added to `RLexSortedSet`: `lexRange`, `lexRangeHead`, `lexRangeHeadAsync`, `lexRangeTail`, `lexRangeTailAsync`, `lexRangeAsync` (thanks to jackygurui)  
Feature - new methods with `limit` option support were added to `RScoredSortedSet`: `valueRange`, `valueRangeAsync`, `entryRange`, `entryRangeAsync`, `valueRange`, `valueRangeAsync` (thanks to jackygurui)  
Feature - `LOADING` Redis server response handling  
Feature - `RKeys.getSlot` method added  
Feature - new config options `reconnectionTimeout`, `failedAttempts`, `connectTimeout`, `slaveSubscriptionConnectionMinimumIdleSize`, `masterConnectionMinimumIdleSize`, `'slaveConnectionMinimumIdleSize`, `idleConnectionTimeout`  
Feature - `RBitSet` object added  
Feature - `RBlockingQueue.pollFromAny` and `RBlockingQueue.pollFromAnyAsync` methods added  
Improvements - `LoadBalancer` interface refactored  
Fixed - RedisTimeoutException message  
Fixed - command execution timeout handling  
Fixed - `RList.addAllAsync`  
Fixed - `RSet.iterator`  
Fixed - `RBatch.execute` and `RBatch.executeAsync` errors handling  


### 11-Nov-2015 - version 2.1.4 released  
Cluster support improvements. New codecs. Stability improvements.

Feature - [LZ4](https://github.com/jpountz/lz4-java) compression codec support  
Feature - [CBOR](http://cbor.io/) binary json codec support (thanks to lefay)  
Feature - [MsgPack](http://msgpack.org/) binary json codec support  
Feature - [Fst](https://github.com/RuedigerMoeller/fast-serialization) serialization codec support  
Feature - [Snappy](https://github.com/xerial/snappy-java) compression codec support  
Feature - cluster slave nodes change monitoring  
Feature - `Config.slaveFailedAttempts` and `Config.slaveReconnectionTimeout` config params added  
Feature - `ClusterServersConfig.readFromSlaves` config param added  
Improvement - async channel reconnection  
Improvement - connection acquisition in async way  
Improvement - cluster slot change/migration handling  
Improvement - get cluster info from new cluster nodes not defined in initial config  
__Deprecated__ - `refreshConnectionAfterFails` config param  
Fixed - `RList.add(pos, element)` fixed  
Fixed - Publish/Subscribe message decoding under heavy load  
Fixed - cluster ASK response handling  
Fixed - `RMap.putAll` fixed  
Fixed - parsing cluster nodes info  
Fixed - NPE during Publish/Subscribe event handling  
Fixed - Redisson shutdown handling  
Fixed - EOFException during RLock usage with SerializationCodec (thanks to Oleg Ternovoi)

### 17-Sep-2015 - version 2.1.3 released  
Feature - Ability to define `Codec` for each object  
Feature - `refreshConnectionAfterFails` setting added  
Feature - [AWS Elasticache](https://aws.amazon.com/elasticache/) support via `Config.useElasticacheServers` method (thanks to Steve Ungerer)  
Feature - `RScoredSortedSet` and `RLexSortedSet` added. Both uses native Redis Sorted Set commands. `RLexSortedSet`s stores only String objects and work with ZLEX-commands  
Fixed - missed AUTH during channel reconnection  
Fixed - resubscribe to subscribed topics during channel reconnection  

### 05-Sep-2015 - version 2.1.2 released  
Fixed - possible NPE during channel reconnection  
Fixed - executeAsync freezes in cluster mode  
Fixed - use same node for SCAN/SSCAN/HSCAN during iteration  
Fixed - possible race-condition during master change  
Fixed - `BlockingQueue.peek` race-condition  
Fixed - NPE with empty sentinel servers  
Fixed - unable to read `clientName` config param in Master\Slave and Sentinel modes  
Fixed - "Too many open files" error in cluster mode

### 15-Aug-2015 - version 2.1.1 released  
Feature - all keys operations extracted to `RKeys` interface  
Feature - `RKeys.getKeys`, `RKeys.getKeysByPattern` and `RKeys.randomKey`methods added  
Feature - `RBlockingQueueAsync.drainToAsync` method added  
Feature - Redis nodes info and ping operations via `Redisson.getNodesGroup` or `Redisson.getClusterNodesGroup` now available  
Improvement - added sentinel nodes discovery  
Fixed - command encoding errors handling  
Fixed - cluster empty slot handling  
Fixed - connection hangs when there are no slaves in sentinel mode  
Fixed - activate master as slave when there are no more available slaves in sentinel mode  
Fixed - skip disconnected sentinels during startup  
Fixed - slave node discovery in sentinel mode which has been disconnected since start  
__Deprecated__ - Redisson methods `deleteAsync`, `delete`, `deleteByPatternAsync`, `deleteByPattern`, `findKeysByPatternAsync`, `findKeysByPattern`. Use same methods with `RKeys` interface  

### 03-Aug-2015 - version 2.1.0 released  
Feature - `RTopic` subscribtion/unsubscription status listener added  
Feature - `RSet`: `removeRandom` and `removeRandomAsync` methods added  
Improvement - `RList`: `retainAll`,`containsAll`, `indexOf`, `lastIndexOf` optimization  
__Breaking api change__ - `findKeysByPattern` response interface changed to `Collection`  
__Breaking api change__ - `RTopic` message listener interface changed  
Fixed - NPE during cluster mode start  
Fixed - timeout timer interval calculation  
Fixed - `RBatch` NPE's with very big commands list  
Fixed - `RBucket.set` with timeout  

### 26-Jul-2015 - version 2.0.0 released  
Starting from 2.0.0 version Redisson has a new own async and lock-free Redis client under the hood. Thanks to the new architecture pipline (command batches) support has been implemented and a lot of code has gone.  

Feature - new `RObject` methods: `move`, `moveAsync`, `migrate`, `migrateAsync`  
Feature - new async interfaces: `RAsyncMap`, `RAtomicLongAsync`, `RBlockingQueueAsync`, `RCollectionAsync`, `RDequeAsync`, `RExpirableAsync`, `RHyperLogLogAsync`, `RListAsync`, `RObjectAsync`, `RQueueAsync`, `RScriptAsync`, `RSetAsync`, `RTopicAsync`  
Feature - multiple commands batch (Redis pipelining) support via `Redisson.createBatch` method  
Feature - new methods `flushall`, `deleteAsync`, `delete`, `deleteByPatternAsync`, `deleteByPattern`, `findKeysByPatternAsync`, `findKeysByPattern` added to `RedissonClient` interface  
Improvement - closed channel detection speedup  

### 22-Jul-2015 - version 1.3.1 released  
Fixed - requests state sync during shutdown  
Fixed - netty-transport-native-epoll is now has a provided scope  
Fixed - NPE during `BlockingQueue.poll` invocation  

### 04-Jul-2015 - version 1.3.0 released
Feature - `RQueue.pollLastAndOfferFirstTo` method added  
Feature - `RObject.rename`, `RObject.renameAsync`, `RObject.renamenx`, `RObject.renamenxAsync` methods added  
Feature - `RList.getAsync`, `RList.addAsync`, `RList.addAllAsync` methods added  
Feature - `RObject.deleteAsync` method added  
Feature - unix sockets support via `Configuration.useLinuxNativeEpoll` setting  
Feature - `Redisson.getTopicPattern` method added (thanks to alex-sherwin)  
Improvement - `RLock` auto-unlock then client lock-owner is gone (thanks to AndrewKolpakov)  
Improvement - lua scripts used instead of multi/exec commands to avoid connection errors during execution (thanks to AndrewKolpakov)  
Improvement - `RObject.delete` method now returns `boolean` status  
Improvement - propagate Command processing exceptions to ConnectionManager (thanks to marko-stankovic)  
Improvement - KryoCodec classes registration ability added  
Fixed - slave status handling in Sentinel mode  
Fixed - String codec  
Fixed - Cluster ASKING command support  
Fixed - `RedissonBlockingQueue#drainTo` method (thanks to Sergey Poletaev)  
Fixed - Cluster.STATE.HANDSHAKE enum added  
Fixed - `RedissonClient.getScript` method added  
Fixed - `BlockingQueue.poll` method  
Fixed - Incorrect map key encoding makes hmget return no fields when string keys are used (thanks to sammiq)  

### 02-Apr-2015 - version 1.2.1 released
Feature - all redis-script commands via 'RScript' object  
Feature - implementation of `java.util.concurrent.BlockingQueue` (thanks to pdeschen)  
Feature - buckets load by pattern (thanks to mathieucarbou)  
Improvement - IPv6 support  
Improvement - `isEmpty` checks for added collections  
Fixed - KryoCodec keys decoding (thanks to mathieucarbou)  
Fixed - `RMap.addAndGet()` data format  
Fixed - timeout support in cluster, sentinel and single connections configurations  
Fixed - ClassCastException in `RedissonCountDownLatch.trySetCount`  
Fixed - `RMap.replace` concurrency issue (thanks to AndrewKolpakov)  
Fixed - `RLock` subscription timeout units fixed (thanks to AndrewKolpakov)  
Fixed - Re-throw async exceptions (thanks to AndrewKolpakov)  

### 09-Jan-2015 - version 1.2.0 released
Feature - cluster mode support  
Fixed - `RList` iterator race conditions  
Fixed - `RDeque.addFirst` `RDeque.addLast` methods  
Fixed - OSGi support

### 16-Dec-2014 - version 1.1.7 released
Improvement - `RAtomicLong` optimization  
Fixed - `RMap.fastRemove` and `RMap.getAll` methods  
Fixed - `RTopic` listeners re-subscribing in sentinel mode  
Fixed - `RSet.toArray` and `RSet.iterator` values order  
Fixed - keys iteration in `RMap.getAll`  
Fixed - `RSet` iteration  
Fixed - `RAtomicLong` NPE  
Fixed - infinity loop during master/slave connection acquiring  
Fixed - `RedissonList.addAll` result  

### 18-Nov-2014 - version 1.1.6 released
Feature - `RBucket.exists` and `RBucket.existsAsync` methods added  
Feature - `RMap.addAndGet` method added  
Feature -  database index via `database` and operation timeout via `timeout` config params added  
Improvement - `RLock` optimization  
__Breaking api change__ - Redisson now uses `RedissonClient` interface  
Fixed - NPE in `CommandOutput`  
Fixed - unsubscribing during `RTopic.removeListener`  
Fixed - all object names encoding, no more quotes  
Fixed - HashedWheelTimer shutdown  
Fixed - `RLock` race conditions (thanks to jsotuyod and AndrewKolpakov)  
Fixed - `RCountDownLatch` race conditions  

### 23-Jul-2014 - version 1.1.5 released
Feature - operations auto-retry. `retryAttempts` and `retryInterval` params added for each connection type  
Feature - `RMap.filterEntries`, `RMap.getAll`, `RMap.filterKeys`, `RMap.filterValues` methods added  
Feature - `RMap.fastRemove`, `RMap.fastRemoveAsync`, `RMap.fastPut` & `RMap.fastPutAsync` methods added  
Fixed - async operations timeout handling  
Fixed - sorting algorithm used in `RSortedSet`.  

### 15-Jul-2014 - version 1.1.4 released
Feature - new `RLock.lockInterruptibly`, `RLock.tryLock`, `RLock.lock` methods with TTL support  
Fixed - pub/sub connections reattach then slave/master down  
Fixed - turn off connection watchdog then slave/master down  
Fixed - sentinel master switch  
Fixed - slave down connection closing  

### 13-Jul-2014 - version 1.1.3 released
Improvement - RedissonCountDownLatch optimization  
Improvement - RedissonLock optimization  
Fixed - RedissonLock thread-safety  
Fixed - master/slave auth using Sentinel servers  
Fixed - slave down handling using Sentinel servers  

### 03-Jul-2014 - version 1.1.2 released
Improvement - RedissonSet.iterator implemented with sscan  
Improvement - RedissonSortedSet.iterator optimization  
Feature - `RSortedSet.removeAsync`, `RSortedSet.addAsync`, `RSet.removeAsync`, RSet.addAsync methods added  
Feature - slave up/down detection in Sentinel servers connection mode  
Feature - new-slave automatic discovery in Sentinel servers connection mode  

### 17-June-2014 - version 1.1.1 released
Feature - sentinel servers support  
Fixed - connection leak in `RTopic`  
Fixed - setted password not used in single server connection  

### 07-June-2014 - version 1.1.0 released
Feature - master/slave connection management  
Feature - simple set/get object support via `org.redisson.core.RBucket`  
Feature - hyperloglog support via `org.redisson.core.RHyperLogLog`  
Feature - new methods `getAsync`, `putAsync` and `removeAsync` added to `org.redisson.core.RMap`  
Feature - new method `publishAsync` added to `org.redisson.core.RTopic`  
Feature - [Kryo](https://github.com/EsotericSoftware/kryo) codec added (thanks to mathieucarbou)  
__Breaking api change__ - `org.redisson.Config` model changed  
Fixed - score calucaltion algorithm used in `RSortedSet`.  
Fixed - `RMap.put` & `RMap.remove` result consistency fixed.  
Fixed - `RTopic.publish` now returns the number of clients that received the message  
Fixed - reconnection handling (thanks to renzihui)  
Improvement - `org.redisson.core.RTopic` now use lazy apporach for subscribe/unsubscribe  

### 04-May-2014 - version 1.0.4 released
Feature - distributed implementation of `java.util.Deque`  
Feature - some objects implements `org.redisson.core.RExpirable`  
Fixed - JsonJacksonCodec lazy init  

### 26-Mar-2014 - version 1.0.3 released
Fixed - RedissonAtomicLong state format  
Fixed - Long serialization in JsonJacksonCodec  

### 05-Feb-2014 - version 1.0.2 released
Feature - distributed implementation of `java.util.SortedSet`  
Fixed - OSGi compability  

### 17-Jan-2014 - version 1.0.1 released
Improvement - forceUnlock, isLocked, isHeldByCurrentThread and getHoldCount methods added to RLock  
Feature - connection load balancer to use multiple Redis servers  
Feature - published in maven central repo  

### 11-Jan-2014 - version 1.0.0 released
First stable release.

