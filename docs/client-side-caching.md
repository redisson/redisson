## Native implementation

Client Side caching is implemented using client tracking listener through `RESP3` protocol, which is available in both Valkey and Redis.

This technique is used for performance optimization of read operations and avoid network roundtrips. Client Side caching stores data entries on Redisson side which allows to execute read operations up to **45x faster** in comparison with common implementation.

Requires [protocol](configuration.md) setting value set to `RESP3`.

Available for [RBucket](data-and-services/objects.md/#object-holder), [RStream](data-and-services/objects.md/#stream), [RSet](data-and-services/objects.md/#set), [RMap](data-and-services/objects.md/#map), [RScoredSortedSet](data-and-services/collections.md/#scoredsortedset), [RList](data-and-services/collections.md/#list), [RQueue](data-and-services/collections.md/#queue), [RDeque](data-and-services/collections.md/#deque), [RBlockingQueue](data-and-services/collections.md/#blocking-queue), [RBlockingDeque](data-and-services/collections.md/#blocking-deque) objects.

!!! warning
    Client side caching feature invalidates whole object per entry change which is ineffective.  
	**Use [Advanced implementation](#advanced-implementation) instead which implements invalidation per entry.**

RClientSideCaching object is stateful and each instance creates own cache.

Code usage example:
```java
RClientSideCaching csc = redisson.getClientSideCaching(ClientSideCachingOptions.defaults());

RBucket<String> b = csc.getBucket("test");

// data requested and change is now tracked
b.get();

// ...

// call destroy() method if object and all related objects aren't used anymore
csc.destroy();
```

## Advanced implementation

Native client side caching technique in the previous section is effective only on a per-object basis. Unfortunately, this is not sufficient for data structures like `Map` or `Hash`. Because the cache invalidation message doesn't contain any information about the invalidated entry and only includes the object name, it's impossible to evict a specific entry. As a result, only clearing the entire cache can help keep the client cache up to date. 

To address this issue Redisson provides own client cache aka `local cache` implementations for the structures below:

* [Map](data-and-services/collections.md/#eviction-local-cache-and-data-partitioning)
* [JSON Store](data-and-services/collections.md/#local-cache)
* [JCache](cache-api-implementations.md/#local-cache-and-data-partitioning)
* [Spring cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning)
* [Hibernate cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_1)
* [MyBatis cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_2)
* [Quarkus cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_3)
* [Micronaut cache](cache-api-implementations.md/#eviction-local-cache-and-data-partitioning_4)