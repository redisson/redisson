Redisson - distributed and scalable Java data structures on top of Redis server. Advanced Java Redis client
====

[![Maven Central](https://img.shields.io/maven-central/v/org.redisson/redisson.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.redisson/redisson/)

Use familiar Java data structures with power of [Redis](http://redis.io).

Based on high-performance async and lock-free Java Redis client and [Netty 4](http://netty.io) framework.  
Redis 2.8+ and JDK 1.6+ compatible  


Read [wiki](https://github.com/mrniko/redisson/wiki) for more Redisson usage details


Licensed under the Apache License 2.0.

Welcome to support chat - [![Join the chat at https://gitter.im/mrniko/redisson](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/mrniko/redisson?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Features
================================
* [AWS ElastiCache](https://aws.amazon.com/elasticache/) servers mode:
    1. automatic new master server discovery
    2. automatic new slave servers discovery
    3. read data using slave servers, write data using master server
* Cluster servers mode:
    1. automatic master and slave discovery
    2. automatic new master server discovery
    3. automatic new slave servers discovery
    4. automatic slots change discovery
    5. read data using slave servers, write data using master server
* Sentinel servers mode: 
    1. automatic master and slave servers discovery
    2. automatic new master server discovery
    3. automatic new slave servers discovery
    4. automatic slave servers offline/online discovery  
    5. automatic sentinel servers discovery  
    6. read data using slave servers, write data using master server
* Master with Slave servers mode: read data using slave servers, write data using master server
* Single server mode: read and write data using single server
* Lua scripting  
* Distributed implementation of `java.util.BitSet`  
* Distributed implementation of `java.util.List`  
* Distributed implementation of `java.util.Set` with TTL support for each entry
* Distributed implementation of `java.util.SortedSet`  
* Distributed implementation of `java.util.Queue`  
* Distributed implementation of `java.util.concurrent.BlockingQueue`  
* Distributed implementation of `java.util.Deque`  
* Distributed implementation of `java.util.Map` with TTL support for each entry 
* Distributed implementation of `java.util.concurrent.ConcurrentMap` with TTL support for each entry 
* Distributed implementation of reentrant `java.util.concurrent.locks.Lock` with TTL support  
* Distributed implementation of reentrant `java.util.concurrent.locks.ReadWriteLock` with TTL support  
* Distributed alternative to the `java.util.concurrent.atomic.AtomicLong`  
* Distributed alternative to the `java.util.concurrent.CountDownLatch`  
* Distributed publish/subscribe messaging via `org.redisson.core.RTopic`  
* Distributed HyperLogLog via `org.redisson.core.RHyperLogLog`  
* Asynchronous interface for each object  
* Thread-safe implementation  
* All commands are executed in an atomic way  
* Supports [Reactive Streams](http://www.reactive-streams.org)
* Supports [Redis pipelining](http://redis.io/topics/pipelining) (command batches)  
* Supports Android platform  
* Supports auto-reconnect  
* Supports failed to send command auto-retry  
* Supports OSGi  
* Supports many popular codecs ([Jackson JSON](https://github.com/FasterXML/jackson), [CBOR](http://cbor.io/), [MsgPack](http://msgpack.org/), [Kryo](https://github.com/EsotericSoftware/kryo), [FST](https://github.com/RuedigerMoeller/fast-serialization), [LZ4](https://github.com/jpountz/lz4-java), [Snappy](https://github.com/xerial/snappy-java) and JDK Serialization)
* With over 250 unit tests  

Projects using Redisson
================================
Setronica: [setronica.com](http://setronica.com/)  
Monits: [monits.com](http://monits.com/)  
Brookhaven National Laboratory: [bnl.gov](http://bnl.gov/)  
Netflix Dyno client: [dyno] (https://github.com/Netflix/dyno)  
武林Q传:[nbrpg](http://www.nbrpg.com/)  
Ocous: [ocous](http://www.ocous.com/)  

Recent Releases
================================
####Please Note: trunk is current development branch.

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

### Maven 

Include the following to your dependency list:

    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>2.2.1</version>
    </dependency>

### Gradle

    compile 'org.redisson:redisson:2.2.1'

### Supported by

YourKit is kindly supporting this open source project with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
<a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
<a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>.
