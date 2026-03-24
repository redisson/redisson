## Cuckoo filter

Java implementation of Valkey or Redis based [RCuckooFilter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RCuckooFilter.html) object is a cuckoo filter. A cuckoo filter is a probabilistic data structure for fast set membership testing, similar to a Bloom filter but with support for element deletion and counting. Covers `CF.*` commands of the Redis Bloom module. This object is thread-safe.

**Initialization**

The filter must be initialized before use. Simple initialization requires only a capacity value. Advanced initialization allows tuning of bucket size, max iterations, and expansion rate through `CuckooFilterInitArgs`.

Code examples:

=== "Sync"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // simple initialization with capacity only
    filter.init(100000);

    // advanced initialization with detailed parameters
    filter.init(CuckooFilterInitArgs.capacity(100000)
                    .bucketSize(4)
                    .maxIterations(500)
                    .expansion(2));
    ```
=== "Async"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // simple initialization with capacity only
    RFuture<Void> future = filter.initAsync(100000);

    // advanced initialization with detailed parameters
    RFuture<Void> advFuture = filter.initAsync(CuckooFilterInitArgs.capacity(100000)
                                        .bucketSize(4)
                                        .maxIterations(500)
                                        .expansion(2));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RCuckooFilterReactive<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // simple initialization with capacity only
    Mono<Void> mono = filter.init(100000);

    // advanced initialization with detailed parameters
    Mono<Void> advMono = filter.init(CuckooFilterInitArgs.capacity(100000)
                                        .bucketSize(4)
                                        .maxIterations(500)
                                        .expansion(2));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RCuckooFilterRx<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // simple initialization with capacity only
    Completable rx = filter.init(100000);

    // advanced initialization with detailed parameters
    Completable advRx = filter.init(CuckooFilterInitArgs.capacity(100000)
                                        .bucketSize(4)
                                        .maxIterations(500)
                                        .expansion(2));
    ```

**Adding elements**

Elements can be added individually or in bulk. The `add()` method allows adding the same element multiple times. The `addIfAbsent()` method adds an element only if it does not already exist in the filter. Bulk operations accept `CuckooFilterAddArgs` with optional `capacity` and `noCreate` parameters.

Code examples:

=== "Sync"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // add a single element (allows duplicates)
    boolean added = filter.add("element1");

    // add element only if it does not already exist
    boolean addedNew = filter.addIfAbsent("element2");

    // bulk add with optional parameters
    Set<String> addedItems = filter.add(
        CuckooFilterAddArgs.<String>items(List.of("a", "b", "c"))
                .capacity(50000)
                .noCreate());

    // bulk add only absent elements
    Set<String> newItems = filter.addIfAbsent(
        CuckooFilterAddArgs.<String>items(List.of("d", "e", "f"))
                .capacity(50000));
    ```
=== "Async"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // add a single element (allows duplicates)
    RFuture<Boolean> addFuture = filter.addAsync("element1");

    // add element only if it does not already exist
    RFuture<Boolean> addNxFuture = filter.addIfAbsentAsync("element2");

    // bulk add with optional parameters
    RFuture<Set<String>> bulkFuture = filter.addAsync(
        CuckooFilterAddArgs.<String>items(List.of("a", "b", "c"))
                .capacity(50000)
                .noCreate());

    // bulk add only absent elements
    RFuture<Set<String>> bulkNxFuture = filter.addIfAbsentAsync(
        CuckooFilterAddArgs.<String>items(List.of("d", "e", "f"))
                .capacity(50000));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RCuckooFilterReactive<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // add a single element (allows duplicates)
    Mono<Boolean> addMono = filter.add("element1");

    // add element only if it does not already exist
    Mono<Boolean> addNxMono = filter.addIfAbsent("element2");

    // bulk add with optional parameters
    Mono<Set<String>> bulkMono = filter.add(
        CuckooFilterAddArgs.<String>items(List.of("a", "b", "c"))
                .capacity(50000)
                .noCreate());

    // bulk add only absent elements
    Mono<Set<String>> bulkNxMono = filter.addIfAbsent(
        CuckooFilterAddArgs.<String>items(List.of("d", "e", "f"))
                .capacity(50000));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RCuckooFilterRx<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // add a single element (allows duplicates)
    Single<Boolean> addRx = filter.add("element1");

    // add element only if it does not already exist
    Single<Boolean> addNxRx = filter.addIfAbsent("element2");

    // bulk add with optional parameters
    Single<Set<String>> bulkRx = filter.add(
        CuckooFilterAddArgs.<String>items(List.of("a", "b", "c"))
                .capacity(50000)
                .noCreate());

    // bulk add only absent elements
    Single<Set<String>> bulkNxRx = filter.addIfAbsent(
        CuckooFilterAddArgs.<String>items(List.of("d", "e", "f"))
                .capacity(50000));
    ```

**Checking element existence and counting occurrences**

The `exists()` method checks if an element may exist in the filter. A return value of `false` guarantees the element is not present. A return value of `true` means the element may exist (false positives are possible). Multiple elements can be checked at once with `exists(Collection)`. The `count()` method returns the approximate number of times an element has been added to the filter.

Code examples:

=== "Sync"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // check single element
    boolean mayExist = filter.exists("element1");

    // check multiple elements at once
    Set<String> existing = filter.exists(List.of("a", "b", "c", "d"));

    // get approximate count of times an element was added
    long approxCount = filter.count("element1");
    ```
=== "Async"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // check single element
    RFuture<Boolean> existsFuture = filter.existsAsync("element1");

    // check multiple elements at once
    RFuture<Set<String>> mExistsFuture = filter.existsAsync(List.of("a", "b", "c", "d"));

    // get approximate count of times an element was added
    RFuture<Long> countFuture = filter.countAsync("element1");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RCuckooFilterReactive<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // check single element
    Mono<Boolean> existsMono = filter.exists("element1");

    // check multiple elements at once
    Mono<Set<String>> mExistsMono = filter.exists(List.of("a", "b", "c", "d"));

    // get approximate count of times an element was added
    Mono<Long> countMono = filter.count("element1");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RCuckooFilterRx<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    // check single element
    Single<Boolean> existsRx = filter.exists("element1");

    // check multiple elements at once
    Single<Set<String>> mExistsRx = filter.exists(List.of("a", "b", "c", "d"));

    // get approximate count of times an element was added
    Single<Long> countRx = filter.count("element1");
    ```

**Removing elements**

Unlike Bloom filters, cuckoo filters support element deletion. The `remove()` method deletes an element from the filter and returns `true` if it was found and removed.

**Note:** Deleting an element that was never added to the filter may cause false negatives for other elements.

Code examples:

=== "Sync"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    boolean removed = filter.remove("element1");
    ```
=== "Async"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    RFuture<Boolean> removeFuture = filter.removeAsync("element1");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RCuckooFilterReactive<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    Mono<Boolean> removeMono = filter.remove("element1");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RCuckooFilterRx<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    Single<Boolean> removeRx = filter.remove("element1");
    ```

**Filter information**

The `getInfo()` method returns a `CuckooFilterInfo` object containing filter statistics: memory size in bytes, number of buckets, number of sub-filters, number of inserted items, number of deleted items, bucket size, expansion rate, and maximum iterations.

Code examples:

=== "Sync"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    CuckooFilterInfo info = filter.getInfo();
    info.getSize();                 // memory size in bytes
    info.getNumberOfBuckets();      // number of buckets
    info.getNumberOfFilters();      // number of sub-filters
    info.getNumberOfInsertedItems();// number of inserted items
    info.getNumberOfDeletedItems(); // number of deleted items
    info.getBucketSize();           // items per bucket
    info.getExpansionRate();        // expansion rate
    info.getMaxIterations();        // max swap attempts
    ```
=== "Async"
    ```java
    RCuckooFilter<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    RFuture<CuckooFilterInfo> infoFuture = filter.getInfoAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RCuckooFilterReactive<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    Mono<CuckooFilterInfo> infoMono = filter.getInfo();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RCuckooFilterRx<String> filter = redisson.getCuckooFilter("myCuckooFilter");

    Single<CuckooFilterInfo> infoRx = filter.getInfo();
    ```
