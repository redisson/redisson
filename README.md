Redisson: Redis based In-Memory Data Grid for Java.
====
[Quick start](https://github.com/redisson/redisson#quick-start) | [Documentation](https://github.com/redisson/redisson/wiki) | [Javadocs](http://www.javadoc.io/doc/org.redisson/redisson/3.3.2) | [Changelog](https://github.com/redisson/redisson/blob/master/CHANGELOG.md) | [Code examples](https://github.com/redisson/redisson-examples) | [Support chat](https://gitter.im/mrniko/redisson) | [Ultra-fast version](https://redisson.pro)

Based on high-performance async and lock-free Java Redis client and [Netty](http://netty.io) framework.  
## Please take part in [Redisson survey](https://www.surveymonkey.com/r/QXQZH5D)

| Stable Release Version | JDK Version compatibility | Release Date |
| ------------- | ------------- | ------------|
| 3.3.2  | 1.8+ | 21.03.2017 |
| 2.8.2 | 1.6, 1.7, 1.8 and Android | 21.03.2017 |

__NOTE__: Both version lines have same features except `CompletionStage` interface added in 3.x.x


Features
================================
* Replicated servers mode (also supports [AWS ElastiCache](http://docs.aws.amazon.com/AmazonElastiCache/latest/UserGuide/Replication.html) and [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)):
    1. automatic master server change discovery
* Cluster servers mode (also supports [AWS ElastiCache Cluster](http://docs.aws.amazon.com/AmazonElastiCache/latest/UserGuide/Clusters.html) and [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)):
    1. automatic master and slave servers discovery
    2. automatic status and topology update
    3. automatic slots change discovery
* Sentinel servers mode: 
    1. automatic master, slave and sentinel servers discovery
    2. automatic status and topology update
* Master with Slave servers mode  
* Single server mode  
* Asynchronous interface for each object  
* Asynchronous connection pool  
* Thread-safe implementation  
* Lua scripting  
* [Distributed objects](https://github.com/redisson/redisson/wiki/6.-Distributed-objects)  
    Object holder, Binary stream holder, Geospatial holder, BitSet, AtomicLong, AtomicDouble, PublishSubscribe,
    Bloom filter, HyperLogLog
* [Distributed collections](https://github.com/redisson/redisson/wiki/7.-Distributed-collections)  
    Map, Multimap, Set, List, SortedSet, ScoredSortedSet, LexSortedSet, Queue, Deque, Blocking Queue, Bounded Blocking Queue, Blocking Deque, Delayed Queue, Priority Queue, Priority Deque
* [Distributed locks and synchronizers](https://github.com/redisson/redisson/wiki/8.-Distributed-locks-and-synchronizers)  
    Lock, FairLock, MultiLock, RedLock, ReadWriteLock, Semaphore, PermitExpirableSemaphore, CountDownLatch
* [Distributed services](https://github.com/redisson/redisson/wiki/9.-distributed-services)  
    Remote service, Live Object service, Executor service, Scheduler service
* [Spring Cache](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#141-spring-cache) implementation  
* [Hibernate Cache](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#142-hibernate-cache) implementation  
* [JCache API (JSR-107)](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#143-jcache-api-jsr-107-implementation) implementation  
* [Tomcat Session Manager](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks#144-tomcat-redis-session-manager) implementation  
* [Spring Session](https://github.com/redisson/redisson/wiki/14.-Integration%20with%20frameworks/#145-spring-session) implementation  
* [Reactive Streams](https://github.com/redisson/redisson/wiki/3.-operations-execution#32-reactive-way)  
* [Redis pipelining](https://github.com/redisson/redisson/wiki/10.-additional-features#102-execution-batches-of-commands) (command batches)
* Supports Android platform  
* Supports auto-reconnect  
* Supports failed to send command auto-retry  
* Supports OSGi  
* Supports many popular codecs ([Jackson JSON](https://github.com/FasterXML/jackson), [Avro](http://avro.apache.org/), [Smile](http://wiki.fasterxml.com/SmileFormatSpec), [CBOR](http://cbor.io/), [MsgPack](http://msgpack.org/), [Kryo](https://github.com/EsotericSoftware/kryo), [FST](https://github.com/RuedigerMoeller/fast-serialization), [LZ4](https://github.com/jpountz/lz4-java), [Snappy](https://github.com/xerial/snappy-java) and JDK Serialization)
* With over 1000 unit tests  

Who uses Redisson
================================
[Electronic Arts](http://ea.com), [Baidu](http://baidu.com), [New Relic Synthetics](https://newrelic.com/synthetics), [Brookhaven National Laboratory](http://bnl.gov/), [Singtel](http://singtel.com), [Infor](http://www.infor.com/), [Netflix Dyno client](https://github.com/Netflix/dyno), [Ocous](http://www.ocous.com/), [Invaluable](http://www.invaluable.com/), [Clover](https://www.clover.com/) , [Apache Karaf Decanter](https://karaf.apache.org/projects.html#decanter), [Atmosphere Framework](http://async-io.org/), [BrandsEye](http://brandseye.com), [Datorama](http://datorama.com/), [BrightCloud](http://brightcloud.com/), [Azar](http://azarlive.com/), [Snapfish](http://snapfish.com), [Crimson Hexagon](http://www.crimsonhexagon.com), [Quby](http://quby.com/), [Base CRM](http://getbase.com)

Articles
================================

[Java data structures powered by Redis. Introduction to Redisson (pdf)](https://redisson.org/Redisson.pdf)  
[Redisson PRO vs. Jedis: Which Is Faster?](https://dzone.com/articles/redisson-pro-vs-jedis-which-is-faster)  
[A Look at the Java Distributed In-Memory Data Model (Powered by Redis)](https://dzone.com/articles/java-distributed-in-memory-data-model-powered-by-r)  
[Distributed tasks Execution and Scheduling in Java, powered by Redis](https://dzone.com/articles/distributed-tasks-execution-and-scheduling-in-java)  
[Introducing Redisson Live Objects (Object Hash Mapping)](https://dzone.com/articles/introducing-redisson-live-object-object-hash-mappi)  
[Java Remote Method Invocation with Redisson](https://dzone.com/articles/java-remote-method-invocation-with-redisson)  
[Java Multimaps With Redis](https://dzone.com/articles/multimaps-with-redis)  
[Distributed lock with Redis](https://evuvatech.com/2016/02/05/distributed-lock-with-redis/)

Success stories
================================

[Moving from Hazelcast to Redis](https://engineering.datorama.com/moving-from-hazelcast-to-redis-b90a0769d1cb)  

Quick start
===============================

#### Maven 
    <!-- JDK 1.8+ compatible -->
    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>3.3.2</version>
    </dependency>  

    <!-- JDK 1.6+ compatible -->
    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>2.8.2</version>
    </dependency>


#### Gradle
    // JDK 1.8+ compatible
    compile 'org.redisson:redisson:3.3.2'  

    // JDK 1.6+ compatible
    compile 'org.redisson:redisson:2.8.2'

#### Java

```java
// 1. Create config object
Config = ...

// 2. Create Redisson instance
RedissonClient redisson = Redisson.create(config);

// 3. Get object you need
RMap<MyKey, MyValue> map = redisson.getMap("myMap");

RLock lock = redisson.getLock("myLock");

RExecutorService executor = redisson.getExecutorService("myExecutorService");

// over 30 different objects and services ...

```

Downloads
===============================
   
[Redisson 3.3.2](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson&v=3.3.2&e=jar),
[Redisson node 3.3.2](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=3.3.2&e=jar)  

[Redisson 2.8.2](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson&v=2.8.2&e=jar),
[Redisson node 2.8.2](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=2.8.2&e=jar)  

### Supported by

YourKit is kindly supporting this open source project with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
<a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
<a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>.
