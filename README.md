Redis based In-Memory Data Grid for Java. Redisson.
====

[![Maven Central](https://img.shields.io/maven-central/v/org.redisson/redisson.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/org.redisson/redisson/) 

Based on high-performance async and lock-free Java Redis client and [Netty](http://netty.io) framework.  
Redis 2.8+ and JDK 1.6+ compatible.

Please read [documentation](https://github.com/mrniko/redisson/wiki) for more details.  
Redisson [releases history](https://github.com/mrniko/redisson/blob/master/CHANGELOG.md).


Licensed under the Apache License 2.0.

Welcome to support chat - [![Join the chat at https://gitter.im/mrniko/redisson](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/mrniko/redisson?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


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
* Master with Slave servers mode  
* Single server mode  
* Asynchronous interface for each object  
* Asynchronous connection pool  
* Thread-safe implementation  
* Lua scripting  
* [Distributed objects](https://github.com/mrniko/redisson/wiki/6.-Distributed-objects)
* [Distributed collections](https://github.com/mrniko/redisson/wiki/7.-Distributed-collections)
* [Distributed locks and synchronizers](https://github.com/mrniko/redisson/wiki/8.-Distributed-locks-and-synchronizers)
* [Distributed services](https://github.com/mrniko/redisson/wiki/9.-distributed-services)
* [Spring cache](https://github.com/mrniko/redisson/wiki/14.-Integration%20with%20frameworks/#141-spring-cache) integration  
* [Hibernate](https://github.com/mrniko/redisson/wiki/14.-Integration%20with%20frameworks/#142-hibernate) integration  
* [Reactive Streams](https://github.com/mrniko/redisson/wiki/3.-operations-execution#32-reactive-way)
* [Redis pipelining](https://github.com/mrniko/redisson/wiki/10.-additional-features#102-execution-batches-of-commands) (command batches)  
* Supports Android platform  
* Supports auto-reconnect  
* Supports failed to send command auto-retry  
* Supports OSGi  
* Supports many popular codecs ([Jackson JSON](https://github.com/FasterXML/jackson), [Avro](http://avro.apache.org/), [Smile](http://wiki.fasterxml.com/SmileFormatSpec), [CBOR](http://cbor.io/), [MsgPack](http://msgpack.org/), [Kryo](https://github.com/EsotericSoftware/kryo), [FST](https://github.com/RuedigerMoeller/fast-serialization), [LZ4](https://github.com/jpountz/lz4-java), [Snappy](https://github.com/xerial/snappy-java) and JDK Serialization)
* With over 900 unit tests  

Projects using Redisson
================================
[Setronica](http://setronica.com/), [Monits](http://monits.com/), [Brookhaven National Laboratory](http://bnl.gov/), [Netflix Dyno client] (https://github.com/Netflix/dyno), [武林Q传](http://www.nbrpg.com/), [Ocous](http://www.ocous.com/), [Invaluable](http://www.invaluable.com/), [Clover](https://www.clover.com/) , [Apache Karaf Decanter](https://karaf.apache.org/projects.html#decanter), [Atmosphere Framework](http://async-io.org/)

Articles
================================

[Java data structures powered by Redis. Introduction to Redisson. PDF](http://redisson.org/Redisson.pdf)  
[Introducing Redisson Live Objects (Object Hash Mapping)](https://dzone.com/articles/introducing-redisson-live-object-object-hash-mappi)  
[Java Remote Method Invocation with Redisson](https://dzone.com/articles/java-remote-method-invocation-with-redisson)  
[Java Multimaps With Redis](https://dzone.com/articles/multimaps-with-redis)  

Easy to use. Quick start
===============================

#### Maven 

    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>2.3.0</version>
    </dependency>

#### Gradle

    compile 'org.redisson:redisson:2.3.0'
    
#### Java

```java
// 1. Create config object
Config = ...

// 2. Create Redisson instance
RedissonClient redisson = Redisson.create(config);

// 3. Get object you need
RMap<MyKey, MyValue> map = redisson.getMap("myMap");

RSet<MyValue> set = redisson.getSet("mySet");

RList<MyValue> list = redisson.getList("myList");

RLock lock = redisson.getLock("myLock");

RExecutorService executor = redisson.getExecutorService("myExecutorService");

// over 30 different objects and services ...

```

Downloads
===============================
   
[Redisson 2.3.0](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson&v=2.3.0&e=jar)  
[Redisson node 2.3.0](https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.redisson&a=redisson-all&v=2.3.0&e=jar)  

### Supported by

YourKit is kindly supporting this open source project with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
<a href="http://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a> and
<a href="http://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>.
