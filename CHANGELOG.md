Redisson Releases History
================================
####Please Note: trunk is current development branch.

####08-Jun-2016 - version 2.2.15 released  
Improvement - Performance boost up to 30% for `RSortedSet.add` method  
Fixed - auth during reconnection (thanks to fransiskusx)  
Fixed - Infinity loop with iterator  
Fixed - NPE in `RSortedSet`  
Fixed - `RSortedSet.remove` and `iterator.remove` methods can break elements ordering  

####27-May-2016 - version 2.2.14 released  
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

####30-Apr-2016 - version 2.2.13 released  

Feature - `RSet.diff` and `RSet.intersection` methods added  
Imporovement - `RScoredSortedSet`.`containsAll`, `removeAll` and `retainAll` methods speed optimization  
Imporovement - `RSetCache` memory and speed optimization  
Imporovement - `RSet`.`retainAll`, `containsAll`, `removeAll` methods speed optimized up to 100x  
Fixed - possible infinity `RLock` expiration renewal process  
Fixed - error during `RSetCache.readAll` invocation.  
Fixed - expiration override wasn't work in `RSetCache.add`  

####22-Apr-2016 - version 2.2.12 released  

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

####04-Apr-2016 - version 2.2.11 released  

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


####23-Mar-2016 - version 2.2.10 released  

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

####04-Mar-2016 - version 2.2.9 released  

Feature - __new object added__ `RSetMultimap`  
Feature - __new object added__ `RListMultimap`  
Feature - `valueRangeReversed` and `valueRangeReversedAsync` methods were added to `RScoredSortedSet` object  
Improvement - Throw `RedisOutOfMemoryException` when OOM error from Redis server has occured  
Improvement - Node type added to optimization in Cluster mode  
Improvement - Add DynamicImport-Package to OSGi headers  
Fixed - `RedissonSpringCacheManager` Sentinel compatibility  
Fixed - `RAtomicLong.compareAndSet` doesn't work when expected value is 0 and it wasn't initialized  

####12-Feb-2016 - version 2.2.8 released  

Feature - `union`, `unionAsync`, `readUnion` and `readUnionAsync` methods were added to `RSet` object  
Feature - `readAll` and `readAllAsync` methods were added to `RSetCache` object  
Improvement - `RKeys.delete` optimization in Cluster mode  
Fixed - Script error during `RSetCache.toArray` and `RSetCache.readAll` methods invocation  
Fixed - Sentinel doesn't support AUTH command  
Fixed - RMap iterator  

####03-Feb-2016 - version 2.2.7 released  

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

####28-Jan-2016 - version 2.2.6 released  

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

####09-Jan-2015 - version 2.2.5 released  

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

####25-Dec-2015 - version 2.2.4 released  
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

####15-Dec-2015 - version 2.2.3 released  
Feature - ability to set connection listener via `Config.connectionListener` setting  
Fixed - `RLock` expiration bug fixed (regression bug since 2.2.2)  
Fixed - NPE in `RedissonSortedSet` constructor  

####14-Dec-2015 - version 2.2.2 released  
Feature - `isShuttingDown` and `isShutdown` methods were added to RedissonClient and RedissonReactiveClient  
Feature - __new object added__ `RSetCacheReactive`  
Fixed - RLock expiration renewal task scheduling fixed (regression bug since 2.2.1)  
Fixed - RExpirable.expireAsync timeUnit precision fixed (regression bug since 2.2.1)  

####11-Dec-2015 - version 2.2.1 released  
Feature - __new object added__ `RReadWriteLock` with reentrant read/write locking  
Feature - __new object added__ `RMapCache` map-based cache with TTL support for each entry  
Feature - __new object added__ `RSetCache` set-based cache with TTL support for each value  
Feature - `RBatchReactive.getKeys` method added  
Feature - `RMap.values()`, `RMap.keySet()`, `RMap.entrySet()` reimplemented with live-view objects  
Feature - `RObjectReactive.isExists`, `RObject.isExists` and `RObject.isExistsAsync` added  
Fixed - `RLock.unlock` not thrown IllegalMonitorStateException  

####04-Dec-2015 - version 2.2.0 released  
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


####30-Nov-2015 - version 2.1.6 released  
Fixed - connection pool regression bug  
Fixed - connection init during `Node.ping` and `ClusterNode.info` invocation  


####24-Nov-2015 - version 2.1.5 released  
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


####11-Nov-2015 - version 2.1.4 released  
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

####17-Sep-2015 - version 2.1.3 released  
Feature - Ability to define `Codec` for each object  
Feature - `refreshConnectionAfterFails` setting added  
Feature - [AWS Elasticache](https://aws.amazon.com/elasticache/) support via `Config.useElasticacheServers` method (thanks to Steve Ungerer)  
Feature - `RScoredSortedSet` and `RLexSortedSet` added. Both uses native Redis Sorted Set commands. `RLexSortedSet`s stores only String objects and work with ZLEX-commands  
Fixed - missed AUTH during channel reconnection  
Fixed - resubscribe to subscribed topics during channel reconnection  

####05-Sep-2015 - version 2.1.2 released  
Fixed - possible NPE during channel reconnection  
Fixed - executeAsync freezes in cluster mode  
Fixed - use same node for SCAN/SSCAN/HSCAN during iteration  
Fixed - possible race-condition during master change  
Fixed - `BlockingQueue.peek` race-condition  
Fixed - NPE with empty sentinel servers  
Fixed - unable to read `clientName` config param in Master\Slave and Sentinel modes  
Fixed - "Too many open files" error in cluster mode

####15-Aug-2015 - version 2.1.1 released  
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

####03-Aug-2015 - version 2.1.0 released  
Feature - `RTopic` subscribtion/unsubscription status listener added  
Feature - `RSet`: `removeRandom` and `removeRandomAsync` methods added  
Improvement - `RList`: `retainAll`,`containsAll`, `indexOf`, `lastIndexOf` optimization  
__Breaking api change__ - `findKeysByPattern` response interface changed to `Collection`  
__Breaking api change__ - `RTopic` message listener interface changed  
Fixed - NPE during cluster mode start  
Fixed - timeout timer interval calculation  
Fixed - `RBatch` NPE's with very big commands list  
Fixed - `RBucket.set` with timeout  

####26-Jul-2015 - version 2.0.0 released  
Starting from 2.0.0 version Redisson has a new own async and lock-free Redis client under the hood. Thanks to the new architecture pipline (command batches) support has been implemented and a lot of code has gone.  

Feature - new `RObject` methods: `move`, `moveAsync`, `migrate`, `migrateAsync`  
Feature - new async interfaces: `RAsyncMap`, `RAtomicLongAsync`, `RBlockingQueueAsync`, `RCollectionAsync`, `RDequeAsync`, `RExpirableAsync`, `RHyperLogLogAsync`, `RListAsync`, `RObjectAsync`, `RQueueAsync`, `RScriptAsync`, `RSetAsync`, `RTopicAsync`  
Feature - multiple commands batch (Redis pipelining) support via `Redisson.createBatch` method  
Feature - new methods `flushall`, `deleteAsync`, `delete`, `deleteByPatternAsync`, `deleteByPattern`, `findKeysByPatternAsync`, `findKeysByPattern` added to `RedissonClient` interface  
Improvement - closed channel detection speedup  

####22-Jul-2015 - version 1.3.1 released  
Fixed - requests state sync during shutdown  
Fixed - netty-transport-native-epoll is now has a provided scope  
Fixed - NPE during `BlockingQueue.poll` invocation  

####04-Jul-2015 - version 1.3.0 released
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

####02-Apr-2015 - version 1.2.1 released
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

####09-Jan-2015 - version 1.2.0 released
Feature - cluster mode support  
Fixed - `RList` iterator race conditions  
Fixed - `RDeque.addFirst` `RDeque.addLast` methods  
Fixed - OSGi support

####16-Dec-2014 - version 1.1.7 released
Improvement - `RAtomicLong` optimization  
Fixed - `RMap.fastRemove` and `RMap.getAll` methods  
Fixed - `RTopic` listeners re-subscribing in sentinel mode  
Fixed - `RSet.toArray` and `RSet.iterator` values order  
Fixed - keys iteration in `RMap.getAll`  
Fixed - `RSet` iteration  
Fixed - `RAtomicLong` NPE  
Fixed - infinity loop during master/slave connection acquiring  
Fixed - `RedissonList.addAll` result  

####18-Nov-2014 - version 1.1.6 released
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

####23-Jul-2014 - version 1.1.5 released
Feature - operations auto-retry. `retryAttempts` and `retryInterval` params added for each connection type  
Feature - `RMap.filterEntries`, `RMap.getAll`, `RMap.filterKeys`, `RMap.filterValues` methods added  
Feature - `RMap.fastRemove`, `RMap.fastRemoveAsync`, `RMap.fastPut` & `RMap.fastPutAsync` methods added  
Fixed - async operations timeout handling  
Fixed - sorting algorithm used in `RSortedSet`.  

####15-Jul-2014 - version 1.1.4 released
Feature - new `RLock.lockInterruptibly`, `RLock.tryLock`, `RLock.lock` methods with TTL support  
Fixed - pub/sub connections reattach then slave/master down  
Fixed - turn off connection watchdog then slave/master down  
Fixed - sentinel master switch  
Fixed - slave down connection closing  

####13-Jul-2014 - version 1.1.3 released
Improvement - RedissonCountDownLatch optimization  
Improvement - RedissonLock optimization  
Fixed - RedissonLock thread-safety  
Fixed - master/slave auth using Sentinel servers  
Fixed - slave down handling using Sentinel servers  

####03-Jul-2014 - version 1.1.2 released
Improvement - RedissonSet.iterator implemented with sscan  
Improvement - RedissonSortedSet.iterator optimization  
Feature - `RSortedSet.removeAsync`, `RSortedSet.addAsync`, `RSet.removeAsync`, RSet.addAsync methods added  
Feature - slave up/down detection in Sentinel servers connection mode  
Feature - new-slave automatic discovery in Sentinel servers connection mode  

####17-June-2014 - version 1.1.1 released
Feature - sentinel servers support  
Fixed - connection leak in `RTopic`  
Fixed - setted password not used in single server connection  

####07-June-2014 - version 1.1.0 released
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

####04-May-2014 - version 1.0.4 released
Feature - distributed implementation of `java.util.Deque`  
Feature - some objects implements `org.redisson.core.RExpirable`  
Fixed - JsonJacksonCodec lazy init  

####26-Mar-2014 - version 1.0.3 released
Fixed - RedissonAtomicLong state format  
Fixed - Long serialization in JsonJacksonCodec  

####05-Feb-2014 - version 1.0.2 released
Feature - distributed implementation of `java.util.SortedSet`  
Fixed - OSGi compability  

####17-Jan-2014 - version 1.0.1 released
Improvement - forceUnlock, isLocked, isHeldByCurrentThread and getHoldCount methods added to RLock  
Feature - connection load balancer to use multiple Redis servers  
Feature - published in maven central repo  

####11-Jan-2014 - version 1.0.0 released
First stable release.

