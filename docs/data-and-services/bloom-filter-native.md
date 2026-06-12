## Bloom filter (Native)

Java implementation of Valkey or Redis based [RBloomFilterNative](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBloomFilterNative.html) object is a Bloom filter based on native `BF.*` commands. This object is thread-safe.

Must be initialized with error rate and capacity by `init(errorRate, capacity)` method before usage.

### Initialization

Initialize the filter with an error rate and expected capacity before use:

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");
    // errorRate = 0.03, capacity = 55000000
    bloomFilter.init(0.03, 55000000L);
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");
    // errorRate = 0.03, capacity = 55000000
    RFuture<Void> initFuture = bloomFilter.initAsync(0.03, 55000000L);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");
    // errorRate = 0.03, capacity = 55000000
    Mono<Void> initMono = bloomFilter.init(0.03, 55000000L);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");
    // errorRate = 0.03, capacity = 55000000
    Completable initRx = bloomFilter.init(0.03, 55000000L);
    ```

Use `BloomFilterInitArgs` builder for advanced initialization parameters such as expansion rate and non-scaling mode. Parameters `expansionRate` and `nonScaling` are mutually exclusive.

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    bloomFilter.init(BloomFilterInitArgs.create()
                        .errorRate(0.01)
                        .capacity(1000000L)
                        .expansionRate(4));
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    RFuture<Void> initFuture = bloomFilter.initAsync(BloomFilterInitArgs.create()
                                                .errorRate(0.01)
                                                .capacity(1000000L)
                                                .expansionRate(4));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Mono<Void> initMono = bloomFilter.init(BloomFilterInitArgs.create()
                                        .errorRate(0.01)
                                        .capacity(1000000L)
                                        .expansionRate(4));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Completable initRx = bloomFilter.init(BloomFilterInitArgs.create()
                                        .errorRate(0.01)
                                        .capacity(1000000L)
                                        .expansionRate(4));
    ```

Non-scaling mode prevents creation of sub-filters when capacity is reached:

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    bloomFilter.init(BloomFilterInitArgs.create()
                        .errorRate(0.01)
                        .capacity(1000000L)
                        .nonScaling(true));
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    RFuture<Void> initFuture = bloomFilter.initAsync(BloomFilterInitArgs.create()
                                                .errorRate(0.01)
                                                .capacity(1000000L)
                                                .nonScaling(true));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Mono<Void> initMono = bloomFilter.init(BloomFilterInitArgs.create()
                                        .errorRate(0.01)
                                        .capacity(1000000L)
                                        .nonScaling(true));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Completable initRx = bloomFilter.init(BloomFilterInitArgs.create()
                                        .errorRate(0.01)
                                        .capacity(1000000L)
                                        .nonScaling(true));
    ```

### Adding elements

Add single elements, or a collection of elements in one call:

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    bloomFilter.add("field1Value");
    bloomFilter.add("field2Value");

    Set<String> addedItems = bloomFilter.add(Arrays.asList("field3Value", "field4Value", "field5Value"));
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    RFuture<Boolean> addFuture = bloomFilter.addAsync("field1Value");
    RFuture<Boolean> addFuture2 = bloomFilter.addAsync("field2Value");

    RFuture<Set<String>> addedFuture = bloomFilter.addAsync(Arrays.asList("field3Value", "field4Value", "field5Value"));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Mono<Boolean> addMono = bloomFilter.add("field1Value");
    Mono<Boolean> addMono2 = bloomFilter.add("field2Value");

    Mono<Set<String>> addedMono = bloomFilter.add(Arrays.asList("field3Value", "field4Value", "field5Value"));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Single<Boolean> addRx = bloomFilter.add("field1Value");
    Single<Boolean> addRx2 = bloomFilter.add("field2Value");

    Single<Set<String>> addedRx = bloomFilter.add(Arrays.asList("field3Value", "field4Value", "field5Value"));
    ```

### Inserting elements

The `insert()` method combines filter auto-creation (if it doesn't yet exist) with element insertion. It supports optional parameters including `capacity`, `errorRate`, `expansionRate`, `nonScaling`, and `noCreate`.

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Set<String> addedItems = bloomFilter.insert(
        BloomFilterInsertArgs.<String>elements(Arrays.asList("field1Value", "field2Value", "field3Value"))
                             .capacity(1000000L)
                             .errorRate(0.01));
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    RFuture<Set<String>> addedFuture = bloomFilter.insertAsync(
        BloomFilterInsertArgs.<String>elements(Arrays.asList("field1Value", "field2Value", "field3Value"))
                             .capacity(1000000L)
                             .errorRate(0.01));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Mono<Set<String>> addedMono = bloomFilter.insert(
        BloomFilterInsertArgs.<String>elements(Arrays.asList("field1Value", "field2Value", "field3Value"))
                             .capacity(1000000L)
                             .errorRate(0.01));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Single<Set<String>> addedRx = bloomFilter.insert(
        BloomFilterInsertArgs.<String>elements(Arrays.asList("field1Value", "field2Value", "field3Value"))
                             .capacity(1000000L)
                             .errorRate(0.01));
    ```

Setting `noCreate` to `true` prevents the filter from being created automatically and causes the command to fail if the filter does not already exist:

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Set<String> addedItems = bloomFilter.insert(
        BloomFilterInsertArgs.<String>elements(Arrays.asList("field1Value", "field2Value"))
                             .noCreate(true));
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    RFuture<Set<String>> addedFuture = bloomFilter.insertAsync(
        BloomFilterInsertArgs.<String>elements(Arrays.asList("field1Value", "field2Value"))
                             .noCreate(true));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Mono<Set<String>> addedMono = bloomFilter.insert(
        BloomFilterInsertArgs.<String>elements(Arrays.asList("field1Value", "field2Value"))
                             .noCreate(true));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Single<Set<String>> addedRx = bloomFilter.insert(
        BloomFilterInsertArgs.<String>elements(Arrays.asList("field1Value", "field2Value"))
                             .noCreate(true));
    ```

### Testing membership

Test whether elements are present. The collection-based call returns the subset of supplied elements found in the filter:

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    boolean isPresent = bloomFilter.exists("field1Value");
    Set<String> presentItems = bloomFilter.exists(Arrays.asList("field1Value", "field8Value"));
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    RFuture<Boolean> existsFuture = bloomFilter.existsAsync("field1Value");
    RFuture<Set<String>> existsAllFuture = bloomFilter.existsAsync(Arrays.asList("field1Value", "field8Value"));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Mono<Boolean> existsMono = bloomFilter.exists("field1Value");
    Mono<Set<String>> existsAllMono = bloomFilter.exists(Arrays.asList("field1Value", "field8Value"));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Single<Boolean> existsRx = bloomFilter.exists("field1Value");
    Single<Set<String>> existsAllRx = bloomFilter.exists(Arrays.asList("field1Value", "field8Value"));
    ```

### Counting elements

`count()` returns the approximate number of elements added to the filter:

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    long count = bloomFilter.count();
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    RFuture<Long> countFuture = bloomFilter.countAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Mono<Long> countMono = bloomFilter.count();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Single<Long> countRx = bloomFilter.count();
    ```

### Filter information

The `getInfo()` method returns a `BloomFilterInfo` object containing filter details: capacity, size, sub-filter count, item count, and expansion rate. Use `getInfo(BloomFilterInfoOption)` to query a specific metric individually.

=== "Sync"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    BloomFilterInfo info = bloomFilter.getInfo();
    long capacity = info.getCapacity();
    long size = info.getSize();
    long subFilterCount = info.getSubFilterCount();
    long itemCount = info.getItemCount();
    long expansionRate = info.getExpansionRate();

    // query a specific metric
    long currentCapacity = bloomFilter.getInfo(BloomFilterInfoOption.CAPACITY);
    long currentItems = bloomFilter.getInfo(BloomFilterInfoOption.ITEMS);
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> bloomFilter = redisson.getBloomFilterNative("sample");

    RFuture<BloomFilterInfo> infoFuture = bloomFilter.getInfoAsync();

    // query a specific metric
    RFuture<Long> capacityFuture = bloomFilter.getInfoAsync(BloomFilterInfoOption.CAPACITY);
    RFuture<Long> itemsFuture = bloomFilter.getInfoAsync(BloomFilterInfoOption.ITEMS);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Mono<BloomFilterInfo> infoMono = bloomFilter.getInfo();

    // query a specific metric
    Mono<Long> capacityMono = bloomFilter.getInfo(BloomFilterInfoOption.CAPACITY);
    Mono<Long> itemsMono = bloomFilter.getInfo(BloomFilterInfoOption.ITEMS);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> bloomFilter = redisson.getBloomFilterNative("sample");

    Single<BloomFilterInfo> infoRx = bloomFilter.getInfo();

    // query a specific metric
    Single<Long> capacityRx = bloomFilter.getInfo(BloomFilterInfoOption.CAPACITY);
    Single<Long> itemsRx = bloomFilter.getInfo(BloomFilterInfoOption.ITEMS);
    ```

Available `BloomFilterInfoOption` values: `CAPACITY`, `SIZE`, `FILTERS`, `ITEMS`, `EXPANSION`.

### Data dump and restore

The `scanDump()` and `loadChunk()` methods allow serialization and deserialization of a Bloom filter for backup, replication, or migration between Valkey or Redis instances. Iteration starts from `0` and completes when the returned iterator is `0` with empty data. Requires **Redis Bloom 1.0.0 and higher.**

=== "Sync"
    ```java
    RBloomFilterNative<String> sourceFilter = redisson.getBloomFilterNative("source");
    RBloomFilterNative<String> targetFilter = redisson.getBloomFilterNative("target");

    // dump all chunks from source filter
    long iterator = 0;
    do {
        BloomFilterScanDumpInfo dumpInfo = sourceFilter.scanDump(iterator);
        iterator = dumpInfo.getIterator();
        if (dumpInfo.getData() != null && dumpInfo.getData().length > 0) {
            // load chunk into target filter
            targetFilter.loadChunk(iterator, dumpInfo.getData());
        }
    } while (iterator != 0);
    ```
=== "Async"
    ```java
    RBloomFilterNative<String> sourceFilter = redisson.getBloomFilterNative("source");
    RBloomFilterNative<String> targetFilter = redisson.getBloomFilterNative("target");

    // dump all chunks from source filter
    long iterator = 0;
    do {
        RFuture<BloomFilterScanDumpInfo> dumpFuture = sourceFilter.scanDumpAsync(iterator);
        BloomFilterScanDumpInfo dumpInfo = dumpFuture.get();
        iterator = dumpInfo.getIterator();
        if (dumpInfo.getData() != null && dumpInfo.getData().length > 0) {
            // load chunk into target filter
            RFuture<Void> loadFuture = targetFilter.loadChunkAsync(iterator, dumpInfo.getData());
        }
    } while (iterator != 0);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBloomFilterNativeReactive<String> sourceFilter = redisson.getBloomFilterNative("source");
    RBloomFilterNativeReactive<String> targetFilter = redisson.getBloomFilterNative("target");

    // dump all chunks from source filter
    long iterator = 0;
    do {
        BloomFilterScanDumpInfo dumpInfo = sourceFilter.scanDump(iterator).block();
        iterator = dumpInfo.getIterator();
        if (dumpInfo.getData() != null && dumpInfo.getData().length > 0) {
            // load chunk into target filter
            targetFilter.loadChunk(iterator, dumpInfo.getData()).block();
        }
    } while (iterator != 0);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBloomFilterNativeRx<String> sourceFilter = redisson.getBloomFilterNative("source");
    RBloomFilterNativeRx<String> targetFilter = redisson.getBloomFilterNative("target");

    // dump all chunks from source filter
    long iterator = 0;
    do {
        BloomFilterScanDumpInfo dumpInfo = sourceFilter.scanDump(iterator).blockingGet();
        iterator = dumpInfo.getIterator();
        if (dumpInfo.getData() != null && dumpInfo.getData().length > 0) {
            // load chunk into target filter
            targetFilter.loadChunk(iterator, dumpInfo.getData()).blockingAwait();
        }
    } while (iterator != 0);
    ```
