本文档是 [README.md](README.md) 的中文翻译，若两者有出入以英文原版为准。

# Redisson：Valkey 与 Redis 的 Java 客户端<br>及实时数据平台

[快速开始](https://redisson.pro/docs/getting-started/) | [文档](https://redisson.pro/docs/) | [更新日志](https://github.com/redisson/redisson/blob/master/CHANGELOG.md) | [代码示例](https://github.com/redisson/redisson-examples) | [JavaDocs](https://www.javadoc.io/doc/org.redisson/redisson/latest/index.html)

Redisson 是面向 Valkey 和 Redis 的 Java 客户端与实时数据平台。

**按受欢迎程度位居 Maven Central 所有发布者的前 1%。**

Redisson 通过提供 Valkey 和 Redis 原生不具备的额外服务与数据结构，极大地扩展了这两个平台的能力。它包含分布式 Java 集合、对象、可靠队列以及各类服务实现。

## 功能特性

* 线程安全的实现
* 兼容 JDK 1.8+ 直至最新版本
* 兼容 Android
* 兼容 [Redis](https://redis.io) —— 从 3.0 到最新版本
* 兼容 [Valkey](https://valkey.io) —— 从 7.2.5 到最新版本
* 支持的 Valkey 与 Redis 部署类型
    * [代理模式（Proxy）](https://redisson.pro/docs/configuration/#proxy-mode)
    * [多集群模式（Multi-Cluster）](https://redisson.pro/docs/configuration/#multi-cluster-mode)
    * [多哨兵模式（Multi-Sentinel）](https://redisson.pro/docs/configuration/#multi-sentinel-mode)
    * [单机模式（Single）](https://redisson.pro/docs/configuration/#single-mode)
    * [集群模式（Cluster）](https://redisson.pro/docs/configuration/#cluster-mode)
    * [哨兵模式（Sentinel）](https://redisson.pro/docs/configuration/#sentinel-mode)
    * [复制模式（Replicated）](https://redisson.pro/docs/configuration/#replicated-mode)
    * [主从模式（Master and Slaves）](https://redisson.pro/docs/configuration/#master-slave-mode)
* 支持自动重连  
* 支持命令发送失败自动重试  
* 支持 OSGi  
* 支持 SSL  
* 异步连接池  
* Lua 脚本  
* [Reactive Streams](https://redisson.pro/docs/api-models/#reactive-api) API  
* [RxJava3](https://redisson.pro/docs/api-models/#rxjava-api) API  
* [异步](https://redisson.pro/docs/api-models/#synchronous-and-asynchronous-api) API  
* 本地缓存支持，包括基于 [Caffeine](https://github.com/ben-manes/caffeine) 的实现
* [RediSearch](https://redisson.pro/docs/data-and-services/services/#redisearch-service)
* [JSON 数据类型](https://redisson.pro/docs/data-and-services/objects/#json-object-holder)
* [JSON Store](https://redisson.pro/docs/data-and-services/collections/#json-store)
* [JMS API 实现](https://redisson.pro/docs/messaging/#jms-api-implementation)
* [Cache API 实现](https://redisson.pro/docs/cache-api-implementations)  
    Spring Cache、JCache API（JSR-107）、Hibernate Cache、MyBatis Cache、Quarkus Cache、Micronaut Cache
* [对象](https://redisson.pro/docs/data-and-services/objects)  
    对象持有器（Object holder）、JSON 持有器、二进制流持有器、地理空间持有器、BitSet、限流器（Rate Limiter）、GCRA 限流器
* [计数器](https://redisson.pro/docs/data-and-services/counters)  
    ID 生成器、AtomicLong、AtomicDouble、LongAdder、DoubleAdder
* [概率型数据结构](https://redisson.pro/docs/data-and-services/probabilistic-structures/)  
    布隆过滤器（Bloom filter）、HyperLogLog、布谷鸟过滤器（Cuckoo filter）、Top-k、T-digest
* [集合](https://redisson.pro/docs/data-and-services/collections)  
    JSON Store、Map、Multimap、Set、List、SortedSet、ScoredSortedSet、LexSortedSet、TimeSeries、VectorSet、BitVector Store
* [队列](https://redisson.pro/docs/data-and-services/queues)  
    ReliableQueue、Queue、Deque、阻塞队列（Blocking Queue）、阻塞双端队列（Blocking Deque）、优先队列（Priority Queue）、优先双端队列（Priority Deque）、Stream、环形缓冲区（Ring Buffer）、传输队列（Transfer Queue）
* [锁与同步器](https://redisson.pro/docs/data-and-services/locks-and-synchronizers)  
    Lock、FairLock、MultiLock、RedLock、ReadWriteLock、Semaphore、PermitExpirableSemaphore、CountDownLatch
* [发布/订阅](https://redisson.pro/docs/data-and-services/publish-subscribe)  
    可靠发布订阅（Reliable PubSub）、主题（Topic）、分片主题（Sharded Topic）
* [服务](https://redisson.pro/docs/data-and-services/services)  
    远程服务（Remote service）、Live Object 服务、执行器服务（Executor service）、调度器服务（Scheduler service）、MapReduce 服务
* [微服务集成](https://redisson.pro/docs/microservices-integration)  
    Helidon、Micronaut、Quarkus、Eclipse JNoSQL
* [与 Spring 框架集成](https://redisson.pro/docs/integration-with-spring)  
    Spring Boot Starter、Spring Cache、Spring Session、Spring 事务管理器（Transaction Manager）、Spring Cloud Stream、Spring Data Redis
* [Web 会话管理](https://redisson.pro/docs/web-session-management)  
    Apache Tomcat Session、Spring Session、Micronaut Session
* [事务 API](https://redisson.pro/docs/transactions)
* [Redis 管道](https://redisson.pro/docs/pipelining)（命令批处理）
* 支持多种流行编解码器（[Kryo](https://github.com/EsotericSoftware/kryo)、[Jackson JSON](https://github.com/FasterXML/jackson)、[Avro](http://avro.apache.org/)、[Smile](https://github.com/FasterXML/smile-format-specification)、[CBOR](http://cbor.io/)、[MsgPack](http://msgpack.org/)、[Amazon Ion](https://amzn.github.io/ion-docs/)、[LZ4](https://github.com/jpountz/lz4-java)、[Snappy](https://github.com/xerial/snappy-java)、[Protobuf](https://github.com/protocolbuffers/protobuf) 以及 JDK 序列化）
* 2000+ 单元测试  

### [Redisson PRO 与社区版对比 ➜](https://redisson.pro/feature-comparison.html)

## 方案对比
- [Redisson 与 Spring Data Redis 对比](https://redisson.pro/blog/feature-comparison-redisson-vs-spring-data-redis.html)
- [Redisson 与 Jedis 对比](https://redisson.pro/blog/feature-comparison-redisson-vs-jedis.html)
- [Redisson 与 Lettuce 对比](https://redisson.pro/blog/feature-comparison-redisson-vs-lettuce.html)
- [Redisson 与 Apache Ignite 对比](https://redisson.pro/blog/feature-comparison-redis-vs-ignite.html)
- [Redisson 与 Hazelcast 对比](https://redisson.pro/blog/feature-comparison-redis-vs-hazelcast.html)
- [Redisson 与 Ehcache 对比](https://redisson.pro/blog/feature-comparison-redis-vs-ehcache.html)
- [Redisson 与 Gemfire 对比](https://redisson.pro/blog/feature-comparison-valkey-and-redis-vs-vmware-tanzu-gemfire.html)
- [Redisson 与 WebSphere eXtreme Scale 对比](https://redisson.pro/blog/valkey-redis-vs-websphere-extreme-scale.html)
- [RabbitMQ 与基于 Valkey & Redis 的可靠发布订阅对比](https://redisson.pro/blog/rabbitmq-vs-valkey-redis-based-reliable-pubsub.html)
- [Google PubSub 与基于 Valkey & Redis 的可靠发布订阅对比](https://redisson.pro/blog/google-pubsub-vs-valkey-redis-based-reliable-pubsub.html)

## 迁移指南
- [从 Jedis 迁移到 Redisson](https://redisson.pro/blog/jedis-to-redisson-migration-guide.html)
- [从 Spring Data Redis 迁移到 Redisson](https://redisson.pro/blog/spring-data-redis-to-redisson-migration-guide.html)
- [从 Lettuce 迁移到 Redisson](https://redisson.pro/blog/lettuce-to-redisson-migration-guide.html)
- [从 Ehcache 迁移到 Redisson](https://redisson.pro/blog/ehcache-to-redisson-moving-to-distributed-java-cache.html)
- [从 Hazelcast 迁移到 Redisson](https://redisson.pro/blog/migrating-from-hazelcast-to-valkey-or-redis.html)
- [从 GemFire 和 Apache Geode 迁移到 Redisson](https://redisson.pro/blog/gemfire-to-valkey-redis-redisson-migration-guide.html)
- [从 Apache Ignite 迁移到 Redisson](https://redisson.pro/blog/apache-ignite-to-valkey-redis-redisson-migration-guide.html)