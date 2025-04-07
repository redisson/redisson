While all Redisson Java objects are fully compatible with Valkey and Redis cluster, it's important to note that the state of any single object instance remains confined to its assigned master node and cannot be distributed or partitioned across multiple master nodes within the cluster. This limitation is addressed by [Redisson PRO](https://redisson.pro/feature-comparison.html), which offers data partitioning capabilities for select object types, allowing their state to be spread across multiple master nodes in the cluster.

This partitioning feature provides significant advantages:  

1. State of single Redisson object evenly distributed across master nodes instead of single master node. This allows to avoid Redis or Valkey OutOfMemory problem.  
2. Scaling read/write operations to all master nodes.  

Redisson distributes data across **231 partitions by default**. Minimal number of partition is **3**. These partitions are evenly distributed across all nodes in cluster, ensuring each node manages approximately the same number of partitions.

For example:

- In a 4-master node cluster with the default 231 partitions, each node manages approximately 57 partitions
- In a 5-master node cluster, each node manages approximately 46 partitions

This balanced distribution is achieved through Redisson's specialized slot distribution algorithm, which optimizes data placement across your cluster.

Data partitioning is available for the following data structures and cache implementations:

- [JSON Store](collections.md/#json-store)
- [Set](collections.md/#set)
- [Map](collections.md/#map)
- [BitSet](objects.md/#bitset)
- [Bloom filter](objects.md/#bloom-filter)
- [JCache](../cache-api-implementations.md/#jcache-api-jsr-107)
- [Spring Cache](../integration-with-spring.md/#spring-cache)
- [Hibernate Cache](../cache-api-implementations.md/#hibernate-cache)
- [MyBatis Cache](../cache-api-implementations.md/#mybatis-cache)
- [Quarkus Cache](../cache-api-implementations.md/#quarkus-cache) 
- [Micronaut Cache](../cache-api-implementations.md/#micronaut-cache)



**Joined Valkey or Redis deployments**

Multiple Valkey or Redis deployments can be combined and utilized as a single partitioned (sharded) deployment, allowing for horizontal scaling of your data across several nodes.

```java
RedissonClient redisson1 = ...;
RedissonClient redisson2 = ...;
RedissonClient redisson3 = ...;

RedissonClient redisson = ShardedRedisson.create(redisson1, redisson2, redisson3);
```
