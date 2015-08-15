##Redisson - distributed and scalable Java data structures on top of Redis server. Advanced Java Redis client

Use familiar Java data structures with power of [Redis](http://redis.io).

Based on high-performance async and lock-free Java Redis client and [Netty 4](http://netty.io) framework.  
Redis 2.8+ and JDK 1.6+ compatible  


Read [wiki](https://github.com/mrniko/redisson/wiki) for more Redisson usage details


Licensed under the Apache License 2.0.


Features
================================
* Cluster servers mode:
    1. automatic master discovery
    2. automatic new master server discovery
    3. automatic slots change discovery
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
* Distributed implementation of `java.util.List`  
* Distributed implementation of `java.util.Set`  
* Distributed implementation of `java.util.SortedSet`  
* Distributed implementation of `java.util.Queue`  
* Distributed implementation of `java.util.concurrent.BlockingQueue`  
* Distributed implementation of `java.util.Deque`  
* Distributed implementation of `java.util.Map`  
* Distributed implementation of `java.util.concurrent.ConcurrentMap`  
* Distributed implementation of reentrant `java.util.concurrent.locks.Lock` with TTL support  
* Distributed alternative to the `java.util.concurrent.atomic.AtomicLong`  
* Distributed alternative to the `java.util.concurrent.CountDownLatch`  
* Distributed publish/subscribe messaging via `org.redisson.core.RTopic`  
* Distributed HyperLogLog via `org.redisson.core.RHyperLogLog`  
* Asynchronous interface for each object
* Thread-safe implementation  
* All commands are executed in an atomic way
* Supports [Redis pipelining](http://redis.io/topics/pipelining) (command batches)
* Supports OSGi  
* With over 210 unit tests  

Projects using Redisson
================================
Setronica: [setronica.com](http://setronica.com/)  
Monits: [monits.com](http://monits.com/)  
Brookhaven National Laboratory: [bnl.gov](http://bnl.gov/)  
Netflix Dyno client: [dyno] (https://github.com/Netflix/dyno)  
武林Q传:[nbrpg](http://www.nbrpg.com/)  
Recent Releases
================================
####Please Note: trunk is current development branch.

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
       <version>2.1.1</version>
    </dependency>

### Supported by

YourKit is kindly supporting this open source project with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
<a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
<a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>.
