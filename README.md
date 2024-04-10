# Redisson - Easy Redis Java client<br/>with features of an in-memory data grid
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.redisson/redisson/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.redisson/redisson)
[![JavaDoc](http://www.javadoc.io/badge/org.redisson/redisson.svg)](http://www.javadoc.io/doc/org.redisson/redisson)
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

[Quick start](https://github.com/redisson/redisson#quick-start) | [Documentation](https://github.com/redisson/redisson/wiki/Table-of-Content) | [Changelog](https://github.com/redisson/redisson/blob/master/CHANGELOG.md) | [Code examples](https://github.com/redisson/redisson-examples) | [FAQs](https://github.com/redisson/redisson/wiki/16.-FAQ) | [Report an issue](https://github.com/redisson/redisson/issues/new)

Based on high-performance async and lock-free Java Redis client and [Netty](http://netty.io) framework.  
Supported JDK:   1.8 ... 21 and Android  
Supported Redis: 3.0 ... 7.2  

## Features

* Thread-safe implementation  
* Compatible with multiple Redis deployment types
    * [Redis Proxy](https://github.com/redisson/redisson/wiki/2.-Configuration/#29-proxy-mode)
    * [Redis Multi-Cluster](https://github.com/redisson/redisson/wiki/2.-Configuration/#210-multi-cluster-mode)
    * [Redis Multi-Sentinel](https://github.com/redisson/redisson/wiki/2.-Configuration/#211-multi-sentinel-mode)
    * [Redis Single](https://github.com/redisson/redisson/wiki/2.-Configuration/#26-single-instance-mode)
    * [Redis Cluster](https://github.com/redisson/redisson/wiki/2.-Configuration/#24-cluster-mode)
    * [Redis Sentinel](https://github.com/redisson/redisson/wiki/2.-Configuration/#27-sentinel-mode)
    * [Redis Replicated](https://github.com/redisson/redisson/wiki/2.-Configuration/#25-replicated-mode)
    * [Redis Master and Slaves](https://github.com/redisson/redisson/wiki/2.-Configuration/#28-master-slave-mode)
* Amazon Web Services compatible
     * [AWS Redis Global Datastore](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/Redis-Global-Datastore.html)
     * [AWS ElastiCache](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/WhatIs.html)
     * [Amazon MemoryDB](https://aws.amazon.com/memorydb)
* Microsoft Azure compatible
     * [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)
     * [Azure Redis Cache active-passive replication](https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-how-to-geo-replication)
     * [Azure Redis Cache active-active replication](https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-how-to-active-geo-replication)
* Google Cloud Memorystore compatible
     * [Google Cloud Redis](https://cloud.google.com/memorystore/docs/redis/)
     * [Google Cloud Redis High availability](https://cloud.google.com/memorystore/docs/redis/high-availability)
* Redis Enterprise compatible
     * [Redis Enterprise](https://redis.com/redis-enterprise/)
     * [Redis Enterprise Active-Active databases](https://docs.redis.com/latest/rs/databases/active-active/get-started/)
     * [Redis Enterprise Multiple Active Proxy](https://docs.redis.com/latest/rs/databases/configure/proxy-policy/#about-multiple-active-proxy-support)
* IBM Cloud compatible
     * [IBM Cloud Databases for Redis](https://www.ibm.com/cloud/databases-for-redis)
* Aiven compatible
     * [Aiven for Redis](https://aiven.io/redis)
* Supports auto-reconnection  
* Supports failed to send command auto-retry  
* Supports OSGi  
* Supports SSL  
* Asynchronous connection pool  
* Lua scripting  
* [RediSearch](https://github.com/redisson/redisson/wiki/9.-distributed-services/#96-redisearch-service)
* [JSON datatype](https://github.com/redisson/redisson/wiki/6.-distributed-objects/#615-json-object-holder)
* [Reactive Streams](https://github.com/redisson/redisson/wiki/3.-operations-execution#32-reactive-way) API  
* [RxJava3](https://github.com/redisson/redisson/wiki/3.-operations-execution#32-reactive-way) API  
* [Asynchronous](https://github.com/redisson/redisson/wiki/3.-operations-execution#31-async-way) API  
* Local cache support including [Caffeine](https://github.com/ben-manes/caffeine)-based implementation
* [Distributed Java objects](https://github.com/redisson/redisson/wiki/6.-Distributed-objects)  
    Object holder, Binary stream holder, Geospatial holder, BitSet, AtomicLong, AtomicDouble, PublishSubscribe,
    Bloom filter, HyperLogLog
* [Distributed Java collections](https://github.com/redisson/redisson/wiki/7.-Distributed-collections)  
    Map, Multimap, Set, List, SortedSet, ScoredSortedSet, LexSortedSet, Queue, Deque, Blocking Queue, Bounded Blocking Queue, Blocking Deque, Delayed Queue, Priority Queue, Priority Deque
* [Distributed Java locks and synchronizers](https://github.com/redisson/redisson/wiki/8.-Distributed-locks-and-synchronizers)  
    Lock, FairLock, MultiLock, RedLock, ReadWriteLock, Semaphore, PermitExpirableSemaphore, CountDownLatch
* [Distributed services](https://github.com/redisson/redisson/wiki/9.-distributed-services)  
    Remote service, Live Object service, Executor service, Scheduler service, MapReduce service
* [Helidon](https://github.com/redisson/redisson/tree/master/redisson-helidon) integration  
* [Micronaut](https://github.com/redisson/redisson/tree/master/redisson-micronaut) integration  
* [Quarkus](https://github.com/redisson/redisson/tree/master/redisson-quarkus) integration  
* [Spring Cache](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#142-spring-cache) implementation
* [Spring Transaction API](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#148-spring-transaction-manager) implementation
* [Spring Data Redis](https://github.com/redisson/redisson/tree/master/redisson-spring-data) integration
* [Spring Boot Starter](https://github.com/redisson/redisson/tree/master/redisson-spring-boot-starter) implementation
* [Hibernate Cache](https://github.com/redisson/redisson/tree/master/redisson-hibernate) implementation
* [MyBatis Cache](https://github.com/redisson/redisson/tree/master/redisson-mybatis) implementation
* [Transactions API](https://github.com/redisson/redisson/wiki/10.-Additional-features#104-transactions)
* [JCache API (JSR-107)](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#144-jcache-api-jsr-107-implementation) implementation
* [Tomcat Session Manager](https://github.com/redisson/redisson/tree/master/redisson-tomcat) implementation
* [Spring Session](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#147-spring-session) implementation
* [Redis pipelining](https://github.com/redisson/redisson/wiki/10.-additional-features#103-execution-batches-of-commands) (command batches)
* Supports many popular codecs ([Kryo](https://github.com/EsotericSoftware/kryo), [Jackson JSON](https://github.com/FasterXML/jackson), [Avro](http://avro.apache.org/), [Smile](http://wiki.fasterxml.com/SmileFormatSpec), [CBOR](http://cbor.io/), [MsgPack](http://msgpack.org/), [Amazon Ion](https://amzn.github.io/ion-docs/), [LZ4](https://github.com/jpountz/lz4-java), [Snappy](https://github.com/xerial/snappy-java), [Protobuf](https://github.com/protocolbuffers/protobuf) and JDK Serialization)
* 2000+ unit tests  
<!--
Used by
================================
[![Siemens](https://redisson.org/assets/logos/client29.png "Siemens")](https://www.siemens.com) &nbsp;&nbsp;&nbsp;
[![BMW GROUP](https://redisson.org/assets/logos/client27.png "BMW GROUP")](https://www.bmwgroup.com) &nbsp;&nbsp;&nbsp;
[![AIG](https://redisson.org/assets/logos/client24.png "AIG")](https://www.aig.com/) &nbsp;&nbsp;&nbsp;
[![S&P Global](https://redisson.org/assets/logos/client20.png "S&P Global")](https://www.spglobal.com/) &nbsp;&nbsp;&nbsp;
[![SAP](https://redisson.org/assets/logos/client12.png "SAP")](http://www.sap.com/) &nbsp;&nbsp;&nbsp;
[![EA](https://redisson.org/assets/logos/client1.png "EA")](http://ea.com/) &nbsp;&nbsp;&nbsp;
[![Adobe](https://redisson.org/assets/logos/client23.png "Adobe")](https://www.adobe.com/)  

[![Jeppesen](https://redisson.org/assets/logos/client25.png "Jeppesen")](https://www.jeppesen.com/) &nbsp;&nbsp;&nbsp;
[![BROOKHAVEN](https://redisson.org/assets/logos/client6.png "Brookhaven National Laboratory")](http://bnl.gov/) &nbsp;&nbsp;&nbsp;
[![New Relic Synthetics](https://redisson.org/assets/logos/client3.png "New Relic Synthetics")](http://newrelic.com/synthetics) &nbsp;&nbsp;&nbsp;
[![Netflix](https://redisson.org/assets/logos/client10.png "Netflix")](https://netflix.com/) &nbsp;&nbsp;&nbsp;
[![Personal Capital](https://redisson.org/assets/logos/client26.png "Personal Capital")](https://www.personalcapital.com)  

[![Singtel](https://redisson.org/assets/logos/client5.png "New Relic Synthetics")](http://singtel.com/) &nbsp;&nbsp;&nbsp;
[![Baidu](https://redisson.org/assets/logos/client2.png "Baidu")](http://baidu.com/) &nbsp;&nbsp;&nbsp;
[![Infor](https://redisson.org/assets/logos/client4.png "Infor")](http://www.infor.com/) &nbsp;&nbsp;&nbsp;
[![Crimson Hexagon](https://redisson.org/assets/logos/client7.png "Crimson Hexagon")](https://www.crimsonhexagon.com/) &nbsp;&nbsp;&nbsp;
[![ContaAzul](https://redisson.org/assets/logos/client18.png "ContaAzul")](https://contaazul.com/)&nbsp;&nbsp;&nbsp;
[![马蜂窝](https://redisson.org/assets/logos/client33.png "马蜂窝")](http://www.mafengwo.cn/)  

[![Datorama](https://redisson.org/assets/logos/client8.png "Datorama")](https://datorama.com/)&nbsp;&nbsp;&nbsp;
[![Ticketmaster](https://redisson.org/assets/logos/client14.png "Ticketmaster")](http://www.ticketmaster.com/)&nbsp;&nbsp;&nbsp;
[![NAB](https://redisson.org/assets/logos/client11.png "NAB")](https://www.nab.com.au/)&nbsp;&nbsp;&nbsp;
[![Juniper](https://redisson.org/assets/logos/client31.png "Juniper")](https://www.juniper.net/)&nbsp;&nbsp;&nbsp;
[![火币](https://redisson.org/assets/logos/client32.png "火币")](https://www.huobi.com/)&nbsp;&nbsp;&nbsp;

[![Alibaba](https://redisson.org/assets/logos/client19.png "Alibaba")](http://www.alibaba-inc.com)&nbsp;&nbsp;&nbsp;
[![Flipkart](https://redisson.org/assets/logos/client21.png "Flipkart")](https://www.flipkart.com/)&nbsp;&nbsp;&nbsp;
[![Invaluable](https://redisson.org/assets/logos/client13.png "Invaluable")](http://www.invaluable.com/)&nbsp;&nbsp;&nbsp;
[![BBK](https://redisson.org/assets/logos/client22.png "BBK")](http://www.gdbbk.com/)  
[![SULAKE](https://redisson.org/assets/logos/client17.png "SULAKE")](http://www.sulake.com/)

<sub>Logos, product names and all other trademarks displayed on this page belong to their respective holders and used for identification purposes only. Use of these trademarks, names and brands does not imply endorsement.</sub>
-->
## Comparing solutions

### [Redisson vs Jedis](https://redisson.org/feature-comparison-redisson-vs-jedis.html)
### [Redisson vs Lettuce](https://redisson.org/feature-comparison-redisson-vs-lettuce.html)
### [Redis vs Apache Ignite](https://redisson.org/feature-comparison-redis-vs-ignite.html)
### [Redis vs Hazelcast](https://redisson.org/feature-comparison-redis-vs-hazelcast.html)
### [Redis vs Ehcache](https://redisson.org/feature-comparison-redis-vs-ehcache.html)

## Success stories

### [Moving from Hazelcast to Redis  /  Datorama](https://engineering.datorama.com/moving-from-hazelcast-to-redis-b90a0769d1cb)  
### [Migrating from Hazelcast to Redis  /  Halodoc](https://blogs.halodoc.io/why-and-how-we-move-from-hazelcast-to-redis-2/)
### [Distributed Locking with Redis (Migration from Hazelcast)  /  ContaAzul](https://carlosbecker.com/posts/distributed-locks-redis/)  
### [Migrating from Coherence to Redis](https://www.youtube.com/watch?v=JF5R2ucKTEg)  


## Quick start

#### Maven 
    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>3.27.2</version>
    </dependency>  

#### Gradle
    compile 'org.redisson:redisson:3.27.2'  

#### SBT
    libraryDependencies += "org.redisson" % "redisson" % "3.27.2"

#### Java

```java
// 1. Create config object
Config config = new Config();
config.useClusterServers()
       // use "rediss://" for SSL connection
      .addNodeAddress("redis://127.0.0.1:7181");

// or read config from file
config = Config.fromYAML(new File("config-file.yaml")); 
```

```java
// 2. Create Redisson instance

// Sync and Async API
RedissonClient redisson = Redisson.create(config);

// Reactive API
RedissonReactiveClient redissonReactive = redisson.reactive();

// RxJava3 API
RedissonRxClient redissonRx = redisson.rxJava();
```

```java
// 3. Get Redis based implementation of java.util.concurrent.ConcurrentMap
RMap<MyKey, MyValue> map = redisson.getMap("myMap");

RMapReactive<MyKey, MyValue> mapReactive = redissonReactive.getMap("myMap");

RMapRx<MyKey, MyValue> mapRx = redissonRx.getMap("myMap");
```

```java
// 4. Get Redis based implementation of java.util.concurrent.locks.Lock
RLock lock = redisson.getLock("myLock");

RLockReactive lockReactive = redissonReactive.getLock("myLock");

RLockRx lockRx = redissonRx.getLock("myLock");
```

```java
// 4. Get Redis based implementation of java.util.concurrent.ExecutorService
RExecutorService executor = redisson.getExecutorService("myExecutorService");

// over 50 Redis based Java objects and services ...

```

Upgrade to __[Redisson PRO](https://redisson.pro)__ with **advanced features**.

## Downloads
   
[Redisson 3.27.2](https://repo1.maven.org/maven2/org/redisson/redisson/3.27.2/redisson-3.27.2.jar),
[Redisson node 3.27.2](https://repo1.maven.org/maven2/org/redisson/redisson-all/3.27.2/redisson-all-3.27.2.jar)  

## FAQs

[Q: What is the cause of RedisTimeoutException?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-what-is-the-cause-of-redistimeoutexception)

[Q: When do I need to shut down a Redisson instance, at the end of each request or the end of the life of a thread?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-when-do-i-need-to-shut-down-a-redisson-instance-at-the-end-of-each-request-or-the-end-of-the-life-of-a-thread)

[Q: In MapCache/SetCache/SpringCache/JCache, I have set an expiry time to an entry, why is it still in Redis when it should be disappeared?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-in-mapcachesetcachespringcachejcache-i-have-set-an-expiry-time-to-an-entry-why-is-it-still-in-redis-when-it-should-be-disappeared)

[Q: How can I perform Pipelining/Transaction through Redisson?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-how-can-i-perform-pipeliningtransaction-through-redisson)

[Q: Is Redisson thread safe? Can I share an instance of it between different threads?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-is-redisson-thread-safe-can-i-share-an-instance-of-it-between-different-threads)

[Q: Can I use different encoder/decoders for different tasks?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-can-i-use-different-encoderdecoders-for-different-tasks)

