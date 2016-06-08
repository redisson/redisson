Redis based In-Memory Data Grid for Java. Redisson.
====

[![Maven Central](https://img.shields.io/maven-central/v/org.redisson/redisson.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.redisson/redisson/) 

Use familiar Java data structures with power of [Redis](http://redis.io).

Based on high-performance async and lock-free Java Redis client and [Netty 4](http://netty.io) framework.  
Redis 2.8+ and JDK 1.6+ compatible.


Please read [documentation](https://github.com/mrniko/redisson/wiki) for more details.  
Redisson [releases history](https://github.com/mrniko/redisson/blob/master/CHANGELOG.md).


Licensed under the Apache License 2.0.

Welcome to support chat - [![Join the chat at https://gitter.im/mrniko/redisson](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/mrniko/redisson?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

##Please take part in [Redisson survey](https://ru.surveymonkey.com/r/LP7RG8Q)
####Try [Redisson PRO](http://redisson.pro) edition

Features
================================
* [AWS ElastiCache](https://aws.amazon.com/elasticache/) servers mode:
    1. automatic new master server discovery
    2. automatic new slave servers discovery
* Cluster servers mode:
    1. automatic master and slave servers discovery
    2. automatic new master server discovery
    3. automatic new slave servers discovery
    4. automatic slave servers offline/online discovery
    5. automatic slots change discovery
* Sentinel servers mode: 
    1. automatic master and slave servers discovery
    2. automatic new master server discovery
    3. automatic new slave servers discovery
    4. automatic slave servers offline/online discovery  
    5. automatic sentinel servers discovery  
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
* Distributed implementation of `java.util.concurrent.BlockingDeque`  
* Distributed implementation of `java.util.Map` with TTL support for each entry 
* Distributed implementation of `java.util.concurrent.ConcurrentMap` with TTL support for each entry 
* Distributed implementation of reentrant `java.util.concurrent.locks.Lock` with TTL support  
* Distributed implementation of reentrant `java.util.concurrent.locks.ReadWriteLock` with TTL support  
* Distributed alternative to the `java.util.concurrent.atomic.AtomicLong`  
* Distributed alternative to the `java.util.concurrent.CountDownLatch`  
* Distributed alternative to the `java.util.concurrent.Semaphore`  
* Distributed publish/subscribe messaging via `org.redisson.core.RTopic`  
* Distributed HyperLogLog via `org.redisson.core.RHyperLogLog`  
* Asynchronous interface for each object  
* Asynchronous connection pool  
* Thread-safe implementation  
* All commands executes in an atomic way  
* [Spring cache](http://docs.spring.io/spring/docs/current/spring-framework-reference/html/cache.html) integration  
* Supports [Reactive Streams](http://www.reactive-streams.org)
* Supports [Redis pipelining](http://redis.io/topics/pipelining) (command batches)  
* Supports [Remote services](https://github.com/mrniko/redisson/wiki/6.-distributed-objects#69-remote-service)
* Supports Android platform  
* Supports auto-reconnect  
* Supports failed to send command auto-retry  
* Supports OSGi  
* Supports many popular codecs ([Jackson JSON](https://github.com/FasterXML/jackson), [CBOR](http://cbor.io/), [MsgPack](http://msgpack.org/), [Kryo](https://github.com/EsotericSoftware/kryo), [FST](https://github.com/RuedigerMoeller/fast-serialization), [LZ4](https://github.com/jpountz/lz4-java), [Snappy](https://github.com/xerial/snappy-java) and JDK Serialization)
* With over 500 unit tests  

Projects using Redisson
================================
[Setronica](http://setronica.com/), [Monits](http://monits.com/), [Brookhaven National Laboratory](http://bnl.gov/), [Netflix Dyno client] (https://github.com/Netflix/dyno), [武林Q传](http://www.nbrpg.com/), [Ocous](http://www.ocous.com/), [Invaluable](http://www.invaluable.com/), [Clover](https://www.clover.com/)

### Maven 

Include the following to your dependency list:

    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>2.2.15</version>
    </dependency>

### Gradle

    compile 'org.redisson:redisson:2.2.15'

### Supported by

YourKit is kindly supporting this open source project with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
<a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
<a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>.
