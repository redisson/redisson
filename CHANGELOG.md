Redisson Releases History
================================
### Please Note: trunk is current development branch.

Try __[Redisson PRO](https://redisson.pro)__ version.

### 14-Jun-2018 - versions 2.12.1 and 3.7.1 released

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

