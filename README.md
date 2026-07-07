# Redisson: Valkey & Redis Java Client<br>and Real-Time Data Platform

[Quick start](https://redisson.pro/docs/getting-started/) | [Documentation](https://redisson.pro/docs/) | [Changelog](https://github.com/redisson/redisson/blob/master/CHANGELOG.md) | [Code examples](https://github.com/redisson/redisson-examples) | [JavaDocs](https://www.javadoc.io/doc/org.redisson/redisson/latest/index.html)

Redisson is the Java Client and Real-Time Data Platform for Valkey and Redis.

Redisson greatly extends the capabilities of Valkey and Redis by providing additional services and data structures not natively available in either platform. This enhancement includes distributed Java collections, objects, and service implementations.

## Features

* Thread-safe implementation
* JDK 1.8+ up to the latest version compatible
* Android compatible
* [Redis](https://redis.io) compatible - from 3.0 up to the latest version
* [Valkey](https://valkey.io) compatible - from 7.2.5 up to the latest version
* Supported Valkey and Redis deployment types
    * [Proxy](https://redisson.pro/docs/configuration/#proxy-mode)
    * [Multi-Cluster](https://redisson.pro/docs/configuration/#multi-cluster-mode)
    * [Multi-Sentinel](https://redisson.pro/docs/configuration/#multi-sentinel-mode)
    * [Single](https://redisson.pro/docs/configuration/#single-mode)
    * [Cluster](https://redisson.pro/docs/configuration/#cluster-mode)
    * [Sentinel](https://redisson.pro/docs/configuration/#sentinel-mode)
    * [Replicated](https://redisson.pro/docs/configuration/#replicated-mode)
    * [Master and Slaves](https://redisson.pro/docs/configuration/#master-slave-mode)
* Supports auto-reconnection  
* Supports failed to send command auto-retry  
* Supports OSGi  
* Supports SSL  
* Asynchronous connection pool  
* Lua scripting  
* [Reactive Streams](https://redisson.pro/docs/api-models/#reactive-api) API  
* [RxJava3](https://redisson.pro/docs/api-models/#rxjava-api) API  
* [Asynchronous](https://redisson.pro/docs/api-models/#synchronous-and-asynchronous-api) API  
* Local cache support including [Caffeine](https://github.com/ben-manes/caffeine)-based implementation
* [RediSearch](https://redisson.pro/docs/data-and-services/services/#redisearch-service)
* [JSON datatype](https://redisson.pro/docs/data-and-services/objects/#json-object-holder)
* [JSON Store](https://redisson.pro/docs/data-and-services/collections/#json-store)
* [JMS API implementation](https://redisson.pro/docs/messaging/#jms-api-implementation)
* [Cache API implementations](https://redisson.pro/docs/cache-api-implementations)  
    Spring Cache, JCache API (JSR-107), Hibernate Cache, MyBatis Cache, Quarkus Cache, Micronaut Cache
* [Objects](https://redisson.pro/docs/data-and-services/objects)  
    Object holder, JSON holder, Binary stream holder, Geospatial holder, BitSet, Rate Limiter, GCRA Rate Limiter
* [Counters](https://redisson.pro/docs/data-and-services/counters)  
    Id generator, AtomicLong, AtomicDouble, LongAdder, DoubleAdder
* [Probabilistic structures](https://redisson.pro/docs/data-and-services/probabilistic-structures/)  
    Bloom filter, HyperLogLog, Cuckoo filter, Top-k, T-digest
* [Collections](https://redisson.pro/docs/data-and-services/collections)  
    JSON Store, Map, Multimap, Set, List, SortedSet, ScoredSortedSet, LexSortedSet, TimeSeries, VectorSet, BitVector Store
* [Queues](https://redisson.pro/docs/data-and-services/queues)  
    ReliableQueue, Queue, Deque, Blocking Queue, Blocking Deque, Priority Queue, Priority Deque, Stream, Ring Buffer, Transfer Queue
* [Locks and synchronizers](https://redisson.pro/docs/data-and-services/locks-and-synchronizers)  
    Lock, FairLock, MultiLock, RedLock, ReadWriteLock, Semaphore, PermitExpirableSemaphore, CountDownLatch
* [Publish/subscribe](https://redisson.pro/docs/data-and-services/publish-subscribe)  
    Reliable PubSub, Topic, Sharded Topic
* [Services](https://redisson.pro/docs/data-and-services/services)  
    Remote service, Live Object service, Executor service, Scheduler service, MapReduce service
* [Microservices integration](https://redisson.pro/docs/microservices-integration)  
    Helidon, Micronaut, Quarkus
* [Integration with Spring framework](https://redisson.pro/docs/integration-with-spring)  
    Spring Boot Starter, Spring Cache, Spring Session, Spring Transaction Manager, Spring Cloud Stream, Spring Data Redis
* [Web Session Management](https://redisson.pro/docs/web-session-management)  
    Apache Tomcat Session, Spring Session, Micronaut Session
* [Transactions API](https://redisson.pro/docs/transactions)
* [Redis pipelining](https://redisson.pro/docs/pipelining) (command batches)
* Supports many popular codecs ([Kryo](https://github.com/EsotericSoftware/kryo), [Jackson JSON](https://github.com/FasterXML/jackson), [Avro](http://avro.apache.org/), [Smile](http://wiki.fasterxml.com/SmileFormatSpec), [CBOR](http://cbor.io/), [MsgPack](http://msgpack.org/), [Amazon Ion](https://amzn.github.io/ion-docs/), [LZ4](https://github.com/jpountz/lz4-java), [Snappy](https://github.com/xerial/snappy-java), [Protobuf](https://github.com/protocolbuffers/protobuf) and JDK Serialization)
* 2000+ unit tests  

### [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)

## Comparing solutions
- [Redisson vs Spring Data Redis](https://redisson.pro/blog/feature-comparison-redisson-vs-spring-data-redis.html)
- [Redisson vs Jedis](https://redisson.pro/blog/feature-comparison-redisson-vs-jedis.html)
- [Redisson vs Lettuce](https://redisson.pro/blog/feature-comparison-redisson-vs-lettuce.html)
- [Redisson vs Apache Ignite](https://redisson.pro/blog/feature-comparison-redis-vs-ignite.html)
- [Redisson vs Hazelcast](https://redisson.pro/blog/feature-comparison-redis-vs-hazelcast.html)
- [Redisson vs Ehcache](https://redisson.pro/blog/feature-comparison-redis-vs-ehcache.html)
- [Redisson vs Gemfire](https://redisson.pro/blog/feature-comparison-valkey-and-redis-vs-vmware-tanzu-gemfire.html)
- [Redisson vs WebSphere eXtreme Scale](https://redisson.pro/blog/valkey-redis-vs-websphere-extreme-scale.html)
- [RabbitMQ vs Valkey & Redis based Reliable PubSub](https://redisson.pro/blog/rabbitmq-vs-valkey-redis-based-reliable-pubsub.html)
- [Google PubSub vs Valkey & Redis based Reliable PubSub](https://redisson.pro/blog/google-pubsub-vs-valkey-redis-based-reliable-pubsub.html)

## Migration guides
- [Jedis to Redisson migration](https://redisson.pro/blog/jedis-to-redisson-migration-guide.html)
- [Spring Data Redis to Redisson migration](https://redisson.pro/blog/spring-data-redis-to-redisson-migration-guide.html)
- [Lettuce to Redisson migration](https://redisson.pro/blog/lettuce-to-redisson-migration-guide.html)
