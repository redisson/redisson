All Redisson Java objects are Redis or Valkey cluster compatible, but their state isn't scaled/partitioned to multiple master nodes in cluster. [Redisson PRO](https://redisson.pro/) offers data partitioning for some of them. This feature offers several advantages:  

1. State of single Redisson object evenly distributed across master nodes instead of single master node. This allows to avoid Redis or Valkey OutOfMemory problem.  
2. Scaling read/write operations to all master nodes.  

Redisson splits data to **231 partitions by default**. Minimal number of partition is **3**. Partitions are uniformly distributed across all cluster nodes. This means that each node contains nearly equal amount of partitions. For default partitions amount (231) and 4 master nodes in cluster, each node contains nearly 57 data partitions. 46 data partitions per node for 5 master nodes cluster and so on. This feature achieved thanks to special slot distribution algorithm used in Redisson.

Data partitioning supported for [Set](collections.md/#set), [Map](collections.md/#map), [BitSet](objects.md/#bitset), [Bloom filter](objects.md/#bloom-filter), [Spring Cache](../integration-with-spring.md/#spring-cache), [Hibernate Cache](../cache-api-implementations.md/#hibernate-cache), [JCache](../cache-api-implementations.md/#jcache-api-jsr-107), [Quarkus Cache](../cache-api-implementations.md/#quarkus-cache) and [Micronaut Cache](../cache-api-implementations.md/#micronaut-cache) structures.

**Joined Redis deployments**

Multiple Redis deployments could be joined and used as a single partitioned (sharded) Redis deployment.

```java
RedissonClient redisson1 = ...;
RedissonClient redisson2 = ...;
RedissonClient redisson3 = ...;

RedissonClient redisson = ShardedRedisson.create(redisson1, redisson2, redisson3);
```

_This feature available only in [Redisson PRO](https://redisson.pro) edition._