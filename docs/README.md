# Redisson - Valkey & Redis Java client<br/>Real-Time Data Platform

High-performance async and lock-free Java client for Redis and Valkey based on [Netty](http://netty.io) framework.  


## Features

* Thread-safe implementation
* JDK 1.8+ up to the latest version compatible
* Android compatible
* [Redis](https://redis.io) compatible - from 3.0 up to the latest version
* [Valkey](https://valkey.io) compatible - from 7.2.5 up to the latest version
* Supported deployment types
    * [Proxy](configuration.md/#proxy-mode)
    * [Multi-Cluster](configuration.md/#multi-cluster-mode)
    * [Multi-Sentinel](configuration.md/#multi-sentinel-mode)
    * [Single](configuration.md/#single-mode)
    * [Cluster](configuration.md/#cluster-mode)
    * [Sentinel](configuration.md/#sentinel-mode)
    * [Replicated](configuration.md/#replicated-mode)
    * [Master and Slaves](configuration.md/#master-slave-mode)
* Amazon Web Services compatible
     * [AWS Elasticache Serverless](https://aws.amazon.com/elasticache/features/#Serverless)
     * [AWS Elasticache Global Datastore](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/Redis-Global-Datastore.html)
     * [AWS ElastiCache](https://docs.aws.amazon.com/AmazonElastiCache/latest/red-ug/WhatIs.html)
     * [Amazon MemoryDB](https://aws.amazon.com/memorydb)
* Microsoft Azure compatible
     * [Azure Redis Cache](https://azure.microsoft.com/en-us/services/cache/)
     * [Azure Redis Cache active-passive replication](https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-how-to-geo-replication)
     * [Azure Redis Cache active-active replication](https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-how-to-active-geo-replication)
* Google Cloud Memorystore compatible
     * [Google Cloud Memorystore for Redis](https://cloud.google.com/memorystore/docs/redis/)
     * [Google Cloud Memorystore for Valkey](https://cloud.google.com/memorystore/docs/valkey/)
     * [Google Cloud Memorystore for Redis Cluster](https://cloud.google.com/memorystore/docs/cluster)
     * [Google Cloud Memorystore for Redis High availability](https://cloud.google.com/memorystore/docs/redis/high-availability)
* Redis Enterprise compatible
     * [Redis Cloud](https://redis.io/cloud/)
     * [Redis Software](https://redis.io/software/)
     * [Redis Enterprise Active-Active databases](https://docs.redis.com/latest/rs/databases/active-active/get-started/)
     * [Redis Enterprise Multiple Active Proxy](https://redis.io/docs/latest/operate/rs/databases/configure/proxy-policy/#multiple-active-proxies)
* Redis on SAP BTP compatible
     * [Redis on SAP BTP](https://www.sap.com/products/technology-platform/redis-on-sap-btp-hyperscaler-option.html)
* IBM Cloud compatible
     * [IBM Cloud Databases for Redis](https://www.ibm.com/cloud/databases-for-redis)
* Aiven compatible
     * [Aiven for Caching](https://aiven.io/caching)
     * [Aiven for Vakey](https://aiven.io/valkey)
* Supports auto-reconnection  
* Supports failed to send command auto-retry  
* Supports OSGi  
* Supports SSL  
* Asynchronous connection pool  
* Lua scripting  
* [RediSearch](data-and-services/services.md/#redisearch-service)
* [JSON datatype](data-and-services/objects.md/#json-object-holder)
* [JSON Store](data-and-services/collections.md/#json-store) 
* [Reactive Streams](api-models.md/#reactive-api) API  
* [RxJava3](api-models.md/#rxjava-api) API  
* [Asynchronous](api-models.md/#synchronous-and-asynchronous-api) API  
* Local cache support including [Caffeine](https://github.com/ben-manes/caffeine)-based implementation
* [Cache API implementations](cache-api-implementations.md)
    Spring Cache, JCache API (JSR-107), Hibernate Cache, MyBatis Cache, Quarkus Cache, Micronaut Cache
* [Distributed Java objects](data-and-services/objects.md)  
    Object holder, JSON holder, Binary stream holder, Geospatial holder, BitSet, PublishSubscribe, Bloom filter, HyperLogLog
* [Distributed Java counters](data-and-services/counters.md)  
    AtomicLong, AtomicDouble, LongAdder, DoubleAdder
* [Distributed Java collections](data-and-services/collections.md)  
    JSON Store, Map, Multimap, Set, List, SortedSet, ScoredSortedSet, LexSortedSet, Queue, Deque, Blocking Queue, Bounded Blocking Queue, Blocking Deque, Delayed Queue, Priority Queue, Priority Deque
* [Distributed Java locks and synchronizers](data-and-services/locks-and-synchronizers.md)  
    Lock, FairLock, MultiLock, RedLock, ReadWriteLock, Semaphore, PermitExpirableSemaphore, CountDownLatch
* [Distributed services](data-and-services/services.md)  
    Remote service, Live Object service, Executor service, Scheduler service, MapReduce service
* [Microservices integration](microservices-integration.md)  
    Helidon, Micronaut, Quarkus
* [Integration with Spring framework](integration-with-spring.md)  
    Spring Boot Starter, Spring Cache, Spring Session, Spring Transaction Manager, Spring Cloud Stream, Spring Data Redis
* [Web Session Management](web-session-management.md)  
    Apache Tomcat Session, Spring Session, Micronaut Session
* [Transactions API](transactions.md)
* [Redis pipelining](pipelining.md) (command batches)
* Supports many popular codecs ([Kryo](https://github.com/EsotericSoftware/kryo), [Jackson JSON](https://github.com/FasterXML/jackson), [Avro](http://avro.apache.org/), [Smile](http://wiki.fasterxml.com/SmileFormatSpec), [CBOR](http://cbor.io/), [MsgPack](http://msgpack.org/), [Amazon Ion](https://amzn.github.io/ion-docs/), [LZ4](https://github.com/jpountz/lz4-java), [Snappy](https://github.com/xerial/snappy-java), [Protobuf](https://github.com/protocolbuffers/protobuf) and JDK Serialization)
* 2000+ unit tests  

## Comparing solutions
- [Redisson vs Jedis](https://redisson.org/feature-comparison-redisson-vs-jedis.html)
- [Redisson vs Lettuce](https://redisson.org/feature-comparison-redisson-vs-lettuce.html)
- [Redis vs Apache Ignite](https://redisson.org/feature-comparison-redis-vs-ignite.html)
- [Redis vs Hazelcast](https://redisson.org/feature-comparison-redis-vs-hazelcast.html)
- [Redis vs Ehcache](https://redisson.org/feature-comparison-redis-vs-ehcache.html)

## Success stories

- [Moving from Hazelcast to Redis  /  Datorama](https://engineering.datorama.com/moving-from-hazelcast-to-redis-b90a0769d1cb)  
- [Migrating from Hazelcast to Redis  /  Halodoc](https://blogs.halodoc.io/why-and-how-we-move-from-hazelcast-to-redis-2/)
- [Distributed Locking with Redis (Migration from Hazelcast)  /  ContaAzul](https://carlosbecker.com/posts/distributed-locks-redis/)  
- [Migrating from Coherence to Redis](https://www.youtube.com/watch?v=JF5R2ucKTEg)  

Upgrade to __[Redisson PRO](https://redisson.pro)__ with **advanced features**.