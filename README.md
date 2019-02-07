Redisson: Redis Java client and In-Memory Data Grid
====
[Quick start](https://github.com/redisson/redisson#quick-start) | [Documentation](https://github.com/redisson/redisson/wiki) | [Javadocs](http://www.javadoc.io/doc/org.redisson/redisson/3.10.1) | [Changelog](https://github.com/redisson/redisson/blob/master/CHANGELOG.md) | [Code examples](https://github.com/redisson/redisson-examples) | [FAQs](https://github.com/redisson/redisson/wiki/16.-FAQ) | [Report an issue](https://github.com/redisson/redisson/issues/new) | **[Redisson PRO](https://redisson.pro)**

Based on high-performance async and lock-free Java Redis client and [Netty](http://netty.io) framework.  

| Stable <br/> Release Version | Release Date | JDK Version<br/> compatibility | `CompletionStage` <br/> support | `ProjectReactor` version<br/> compatibility |
| ------------- | ------------- | ------------| -----------| -----------|
| 3.10.2 | 07.02.2019 | 1.8 - 11, Android | Yes | 3.x.x |
| 2.15.2 | 07.02.2019 | 1.6 - 11, Android | No | 2.0.8 |


Features
================================
* Replicated Redis servers mode (also supports [AWS ElastiCache](http://docs.aws.amazon.com/AmazonElastiCache/latest/UserGuide/Replication.html) and [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)):
    1. automatic master server change discovery
* Clustered Redis servers mode (also supports [AWS ElastiCache Cluster](http://docs.aws.amazon.com/AmazonElastiCache/latest/UserGuide/Clusters.html) and [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)):
    1. automatic master and slave servers discovery
    2. automatic status and topology update
    3. automatic slots change discovery
* Sentinel Redis servers mode: 
    1. automatic master, slave and sentinel servers discovery
    2. automatic status and topology update
* Master with Slave Redis servers mode  
* Single Redis server mode  
* Thread-safe implementation  
* [Reactive Streams](https://github.com/redisson/redisson/wiki/3.-operations-execution#32-reactive-way) API  
* [Asynchronous](https://github.com/redisson/redisson/wiki/3.-operations-execution#31-async-way) API  
* Asynchronous connection pool  
* Lua scripting  
* [Distributed Java objects](https://github.com/redisson/redisson/wiki/6.-Distributed-objects)  
    Object holder, Binary stream holder, Geospatial holder, BitSet, AtomicLong, AtomicDouble, PublishSubscribe,
    Bloom filter, HyperLogLog
* [Distributed Java collections](https://github.com/redisson/redisson/wiki/7.-Distributed-collections)  
    Map, Multimap, Set, List, SortedSet, ScoredSortedSet, LexSortedSet, Queue, Deque, Blocking Queue, Bounded Blocking Queue, Blocking Deque, Delayed Queue, Priority Queue, Priority Deque
* [Distributed Java locks and synchronizers](https://github.com/redisson/redisson/wiki/8.-Distributed-locks-and-synchronizers)  
    Lock, FairLock, MultiLock, RedLock, ReadWriteLock, Semaphore, PermitExpirableSemaphore, CountDownLatch
* [Distributed services](https://github.com/redisson/redisson/wiki/9.-distributed-services)  
    Remote service, Live Object service, Executor service, Scheduler service, MapReduce service
* [Spring Framework](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks#141-spring-framework)
* [Spring Cache](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#142-spring-cache) implementation
* [Spring Transaction API](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#147-spring-transaction-manager) implementation
* [Spring Data Redis](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#148-spring-data-redis) integration
* [Spring Boot Starter](https://github.com/redisson/redisson/wiki/14.-Integration-with-frameworks/#149-spring-boot-starter) implementation
* [Hibernate Cache](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#143-hibernate-cache) implementation
* [Transactions API](https://github.com/redisson/redisson/wiki/10.-Additional-features#104-transactions)
* [XA Transaction API](https://github.com/redisson/redisson/wiki/10.-additional-features/#105-xa-transactions) implementation
* [JCache API (JSR-107)](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#144-jcache-api-jsr-107-implementation) implementation
* [Tomcat Session Manager](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks#145-tomcat-redis-session-manager) implementation
* [Spring Session](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#146-spring-session) implementation
* [Redis pipelining](https://github.com/redisson/redisson/wiki/10.-additional-features#102-execution-batches-of-commands) (command batches)
* Supports Android platform  
* Supports auto-reconnection  
* Supports failed to send command auto-retry  
* Supports OSGi  
* Supports SSL  
* Supports many popular codecs ([Jackson JSON](https://github.com/FasterXML/jackson), [Avro](http://avro.apache.org/), [Smile](http://wiki.fasterxml.com/SmileFormatSpec), [CBOR](http://cbor.io/), [MsgPack](http://msgpack.org/), [Kryo](https://github.com/EsotericSoftware/kryo), [Amazon Ion](https://amzn.github.io/ion-docs/), [FST](https://github.com/RuedigerMoeller/fast-serialization), [LZ4](https://github.com/jpountz/lz4-java), [Snappy](https://github.com/xerial/snappy-java) and JDK Serialization)
* With over 1800 unit tests  

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

Success stories
================================

## [Moving from Hazelcast to Redis  /  Datorama](https://engineering.datorama.com/moving-from-hazelcast-to-redis-b90a0769d1cb)  
## [Distributed Locking with Redis (Migration from Hazelcast)  /  ContaAzul](https://carlosbecker.com/posts/distributed-locks-redis/)  
## [Migrating from Coherence to Redis](https://www.youtube.com/watch?v=JF5R2ucKTEg)  


Quick start
===============================

#### Maven 
    <!-- JDK 1.8+ compatible -->
    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>3.10.2</version>
    </dependency>  

    <!-- JDK 1.6+ compatible -->
    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>2.15.2</version>
    </dependency>


#### Gradle
    // JDK 1.8+ compatible
    compile 'org.redisson:redisson:3.10.2'  

    // JDK 1.6+ compatible
    compile 'org.redisson:redisson:2.15.2'

#### Java

```java
// 1. Create config object
Config = ...

// 2. Create Redisson instance
RedissonClient redisson = Redisson.create(config);

// 3. Get Redis based object or service you need
RMap<MyKey, MyValue> map = redisson.getMap("myMap");

RLock lock = redisson.getLock("myLock");

RExecutorService executor = redisson.getExecutorService("myExecutorService");

// over 30 different Redis based objects and services ...

```

<sub>Please consider __[Redisson PRO](https://redisson.pro)__ version for advanced features and support by SLA.</sub>

Downloads
===============================
   
[Redisson 3.10.2](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson&v=3.10.2&e=jar),
[Redisson node 3.10.2](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=3.10.2&e=jar)  

[Redisson 2.15.2](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson&v=2.15.2&e=jar),
[Redisson node 2.15.2](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=2.15.2&e=jar)  

FAQs
===============================
[Q: I saw a RedisTimeOutException, What does it mean? What shall I do? Can Redisson Team fix it?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-i-saw-a-redistimeoutexception-what-does-it-mean-what-shall-i-do-can-redisson-team-fix-it)

[Q: I saw a com.fasterxml.jackson.databind.JsonMappingException during deserialization process, can you fix it?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-i-saw-a-comfasterxmljacksondatabindjsonmappingexception-during-deserialization-process-can-you-fix-it)

[Q: There were too many quotes appeared in the redis-cli console output, how do I fix it?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-there-were-too-many-quotes-appeared-in-the-redis-cli-console-output-how-do-i-fix-it)

[Q: When do I need to shut down a Redisson instance, at the end of each request or the end of the life of a thread?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-when-do-i-need-to-shut-down-a-redisson-instance-at-the-end-of-each-request-or-the-end-of-the-life-of-a-thread)

[Q: In MapCache/SetCache/SpringCache/JCache, I have set an expiry time to an entry, why is it still there when it should be disappeared?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-in-mapcachesetcachespringcachejcache-i-have-set-an-expiry-time-to-an-entry-why-is-it-still-there-when-it-should-be-disappeared)

[Q: How can I perform Pipelining/Transaction through Redisson?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-how-can-i-perform-pipeliningtransaction-through-redisson)

[Q: Is Redisson thread safe? Can I share an instance of it between different threads?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-is-redisson-thread-safe-can-i-share-an-instance-of-it-between-different-threads)

[Q: Can I use different encoder/decoders for different tasks?](https://github.com/redisson/redisson/wiki/16.-FAQ#q-can-i-use-different-encoderdecoders-for-different-tasks)

