Probabilistic structures trade exact answers for dramatic memory savings. Rather than storing the elements themselves, they keep a small, fixed-size summary and answer questions approximately - either *is this element present?* (with a tunable false-positive rate) or *how many distinct elements are there?* (within a small error margin) - using far less memory than an exact structure would.

Redisson provides four: the [Bloom filter](#bloom-filter) and its [Bloom filter (Native)](#bloom-filter-native) variant and the [Cuckoo filter](#cuckoo-filter) for membership testing (the Cuckoo filter also supports deletion), and [HyperLogLog](#hyperloglog) for estimating the number of distinct elements.

## Bloom filter
Java implementation of Valkey or Redis based [RBloomFilter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBloomFilter.html) object is a Bloom filter - a compact, probabilistic structure that tests whether an element has been added to a set. It never reports a false negative (an element that was added always tests as present), but allows a tunable rate of false positives (an element that was never added may occasionally test as present), in return for using far less memory than holding the elements themselves. The number of contained bits is limited to `2^32`, raised to `2^63` with [data partitioning](data-partitioning.md), and the object is thread-safe.

It must be initialized with a capacity before use by calling `tryInit(expectedInsertions, falseProbability)`: the bit-array size and the number of hash functions are derived from the expected number of insertions and the acceptable false-positive probability.

Code examples:

=== "Sync"
	```
	RBloomFilter<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
	bloomFilter.tryInit(55000000L, 0.03);
	
	bloomFilter.add(new SomeObject("field1Value", "field2Value"));
	boolean contains = bloomFilter.contains(new SomeObject("field1Value", "field8Value"));
	long count = bloomFilter.count();
	```

=== "Async"
	```
	RBloomFilterAsync<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
	RFuture<Boolean> initFuture = bloomFilter.tryInitAsync(55000000L, 0.03);
	
	RFuture<Boolean> addFuture = bloomFilter.addAsync(new SomeObject("field1Value", "field2Value"));
	RFuture<Boolean> containsFuture = bloomFilter.containsAsync(new SomeObject("field1Value", "field8Value"));
	RFuture<Long> countFuture = bloomFilter.countAsync();
	```

=== "Reactive"
	```
	RedissonReactiveClient redissonReactive = redisson.reactive();
	RBloomFilterReactive<SomeObject> bloomFilter = redissonReactive.getBloomFilter("sample");
	
	Mono<Boolean> initMono = bloomFilter.tryInit(55000000L, 0.03);
	Mono<Boolean> addMono = bloomFilter.add(new SomeObject("field1Value", "field2Value"));
	Mono<Boolean> containsMono = bloomFilter.contains(new SomeObject("field1Value", "field8Value"));
	Mono<Long> countMono = bloomFilter.count();
	```

=== "RxJava3"
	```
	RedissonRxClient redissonRx = redisson.rxJava();
	RBloomFilterRx<SomeObject> bloomFilter = redissonRx.getBloomFilter("sample");
	
	Single<Boolean> initSingle = bloomFilter.tryInit(55000000L, 0.03);
	Single<Boolean> addSingle = bloomFilter.add(new SomeObject("field1Value", "field2Value"));
	Single<Boolean> containsSingle = bloomFilter.contains(new SomeObject("field1Value", "field8Value"));
	Single<Long> countSingle = bloomFilter.count();
	```

**Adding elements**

`add` inserts a single element and returns `true` if it changed the filter, or `false` if the element was already present. The collection overload `add(Collection)` inserts many elements at once and returns how many were newly added.

=== "Sync"
	```
	RBloomFilter<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
	
	// true if newly added, false if already present
	boolean added = bloomFilter.add(new SomeObject("field1Value", "field2Value"));
	
	// add many at once, returns how many were newly added
	long addedCount = bloomFilter.add(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	```

=== "Async"
	```
	RBloomFilterAsync<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
	
	RFuture<Boolean> addedFuture = bloomFilter.addAsync(new SomeObject("field1Value", "field2Value"));
	RFuture<Long> addedCountFuture = bloomFilter.addAsync(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	```

=== "Reactive"
	```
	RedissonReactiveClient redissonReactive = redisson.reactive();
	RBloomFilterReactive<SomeObject> bloomFilter = redissonReactive.getBloomFilter("sample");
	
	Mono<Boolean> addedMono = bloomFilter.add(new SomeObject("field1Value", "field2Value"));
	Mono<Long> addedCountMono = bloomFilter.add(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	```

=== "RxJava3"
	```
	RedissonRxClient redissonRx = redisson.rxJava();
	RBloomFilterRx<SomeObject> bloomFilter = redissonRx.getBloomFilter("sample");
	
	Single<Boolean> addedSingle = bloomFilter.add(new SomeObject("field1Value", "field2Value"));
	Single<Long> addedCountSingle = bloomFilter.add(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	```

**Testing membership**

`contains` returns `true` if an element is probably present; because of the false-positive rate a `true` result is not a guarantee, but a `false` result is definitive. `contains(Collection)` returns how many of the given elements are probably present, and `exists(Collection)` returns the subset of them that are.

=== "Sync"
	```
	RBloomFilter<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
	
	// true means probably present (subject to the false-positive rate); false is definitive
	boolean maybePresent = bloomFilter.contains(new SomeObject("field1Value", "field2Value"));
	
	// how many of these are probably present
	long presentCount = bloomFilter.contains(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	
	// which of these are probably present
	Set<SomeObject> present = bloomFilter.exists(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	```

=== "Async"
	```
	RBloomFilterAsync<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
	
	RFuture<Boolean> maybeFuture = bloomFilter.containsAsync(new SomeObject("field1Value", "field2Value"));
	RFuture<Long> presentCountFuture = bloomFilter.containsAsync(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	RFuture<Set<SomeObject>> presentFuture = bloomFilter.existsAsync(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	```

=== "Reactive"
	```
	RedissonReactiveClient redissonReactive = redisson.reactive();
	RBloomFilterReactive<SomeObject> bloomFilter = redissonReactive.getBloomFilter("sample");
	
	Mono<Boolean> maybeMono = bloomFilter.contains(new SomeObject("field1Value", "field2Value"));
	Mono<Long> presentCountMono = bloomFilter.contains(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	Mono<Set<SomeObject>> presentMono = bloomFilter.exists(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	```

=== "RxJava3"
	```
	RedissonRxClient redissonRx = redisson.rxJava();
	RBloomFilterRx<SomeObject> bloomFilter = redissonRx.getBloomFilter("sample");
	
	Single<Boolean> maybeSingle = bloomFilter.contains(new SomeObject("field1Value", "field2Value"));
	Single<Long> presentCountSingle = bloomFilter.contains(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	Single<Set<SomeObject>> presentSingle = bloomFilter.exists(Arrays.asList(new SomeObject("a", "b"), new SomeObject("c", "d")));
	```

**Filter information**

`count` returns the estimated number of elements added so far. The configured sizing can be read back with `getExpectedInsertions` (the capacity passed to `tryInit`) and `getFalseProbability` (the target false-positive rate), and the derived structure with `getSize` (the number of bits) and `getHashIterations` (the number of hash functions).

=== "Sync"
	```
	RBloomFilter<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
	
	long inserted = bloomFilter.count();                 // estimated number of added elements
	long capacity = bloomFilter.getExpectedInsertions(); // capacity passed to tryInit
	double rate = bloomFilter.getFalseProbability();     // target false-positive rate
	long bits = bloomFilter.getSize();                   // number of bits
	int hashes = bloomFilter.getHashIterations();        // number of hash functions
	```

=== "Async"
	```
	RBloomFilterAsync<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
	
	RFuture<Long> insertedFuture = bloomFilter.countAsync();
	RFuture<Long> capacityFuture = bloomFilter.getExpectedInsertionsAsync();
	RFuture<Double> rateFuture = bloomFilter.getFalseProbabilityAsync();
	RFuture<Long> bitsFuture = bloomFilter.getSizeAsync();
	RFuture<Integer> hashesFuture = bloomFilter.getHashIterationsAsync();
	```

=== "Reactive"
	```
	RedissonReactiveClient redissonReactive = redisson.reactive();
	RBloomFilterReactive<SomeObject> bloomFilter = redissonReactive.getBloomFilter("sample");
	
	Mono<Long> insertedMono = bloomFilter.count();
	Mono<Long> capacityMono = bloomFilter.getExpectedInsertions();
	Mono<Double> rateMono = bloomFilter.getFalseProbability();
	Mono<Long> bitsMono = bloomFilter.getSize();
	Mono<Integer> hashesMono = bloomFilter.getHashIterations();
	```

=== "RxJava3"
	```
	RedissonRxClient redissonRx = redisson.rxJava();
	RBloomFilterRx<SomeObject> bloomFilter = redissonRx.getBloomFilter("sample");
	
	Single<Long> insertedSingle = bloomFilter.count();
	Single<Long> capacitySingle = bloomFilter.getExpectedInsertions();
	Single<Double> rateSingle = bloomFilter.getFalseProbability();
	Single<Long> bitsSingle = bloomFilter.getSize();
	Single<Integer> hashesSingle = bloomFilter.getHashIterations();
	```

### Data partitioning

_This feature available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Although 'RBloomFilter' object is cluster compatible its content isn't scaled across multiple master nodes. Bloom Filter data partitioning support available only in cluster mode and implemented by separate `RClusteredBloomFilter` object. This implementation uses more efficient distributed memory allocation algorithm. It allows to "shrink" memory space consumed by unused bits across all Valkey or Redis nodes. State of each instance is partitioned across all nodes in Valkey or Redis cluster. Number of contained bits is limited to `2^63`. More details about partitioning [here](data-partitioning.md).

Below is the list of all available BloomFilter implementations:  

|RedissonClient <br/> method name | Data partitioning <br/> support | Ultra-fast read/write | Bits amount limit |
| ------------- | :----------:| :----------:| :----------:|
|getBloomFilter()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | 2^32 |
|getBloomFilter()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ✔️ | 2^32 |
|getClusteredBloomFilter()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | **2^63** |

```java
RClusteredBloomFilter<SomeObject> bloomFilter = redisson.getClusteredBloomFilter("sample");
// initialize bloom filter with 
// expectedInsertions = 255000000
// falseProbability = 0.03
bloomFilter.tryInit(255000000L, 0.03);
bloomFilter.add(new SomeObject("field1Value", "field2Value"));
bloomFilter.add(new SomeObject("field5Value", "field8Value"));
bloomFilter.contains(new SomeObject("field1Value", "field8Value"));
```

### Use Cases

A Bloom filter is a compact probabilistic structure for set membership: `contains` may report a false positive but never a false negative, so a negative answer is definitive while a positive answer means the element is probably present. Elements are only ever added, never removed, which keeps it extremely space-efficient. The filter is sized up front with `tryInit(expectedInsertions, falseProbability)` for a target false-positive rate, and `count()` reports the approximate number of elements added. It suits workloads dominated by membership questions over very large sets, where a small, tunable false-positive rate is an acceptable trade for tiny, fixed memory.

**Skipping Lookups for Absent Keys**

The classic use is a guard in front of a slow backend. Before querying a database, remote cache, or object store, `contains` is checked first: because a negative answer is definitive, a `false` means the key is certainly absent and the expensive lookup can be skipped entirely. This blocks "cache penetration", where a flood of requests for non-existent keys would otherwise reach the database. Every stored key is also added to the filter.

=== "Sync"
	```
	RBloomFilter<String> stored = redisson.getBloomFilter("keys:products");
	stored.tryInit(50000000L, 0.01);
	
	// on write: record the key
	stored.add(productId);
	
	// on read: a false result means the key is definitely absent
	if (!stored.contains(productId)) {
	    return null;                     // skip the database entirely
	}
	return database.load(productId);     // possibly present - confirm in the store
	```
=== "Async"
	```
	RBloomFilterAsync<String> stored = redisson.getBloomFilter("keys:products");
	
	// false means the key is definitely absent - skip the lookup
	RFuture<Boolean> mayExist = stored.containsAsync(productId);
	```
=== "Reactive"
	```
	RedissonReactiveClient redissonReactive = redisson.reactive();
	RBloomFilterReactive<String> stored = redissonReactive.getBloomFilter("keys:products");
	
	// false means the key is definitely absent - skip the lookup
	Mono<Boolean> mayExist = stored.contains(productId);
	```
=== "RxJava3"
	```
	RedissonRxClient redissonRx = redisson.rxJava();
	RBloomFilterRx<String> stored = redissonRx.getBloomFilter("keys:products");
	
	// false means the key is definitely absent - skip the lookup
	Single<Boolean> mayExist = stored.contains(productId);
	```

**Pre-Screening Against Large Reference Sets**

Screening a value against a huge reference set - breached password hashes, known-malicious domains, reserved usernames - is a fast negative check. A `false` from `contains` proves the value is not in the set, so it clears immediately; only a positive needs to fall through to an authoritative lookup. The reference set, however large, compresses to a fixed-size filter, and a whole batch can be screened in one call: `exists` returns just the subset of candidates that might be present.

=== "Sync"
	```
	RBloomFilter<String> breached = redisson.getBloomFilter("breached-passwords");
	breached.tryInit(600000000L, 0.001);
	
	// load the reference set once (bulk add)
	breached.add(knownBreachedHashes);
	
	// single check: false proves the password was never breached
	if (breached.contains(candidateHash)) {
	    // possible match - verify against the authoritative source
	}
	
	// batch screen: only the hashes that may be present come back
	Set<String> suspicious = breached.exists(candidateHashes);
	```
=== "Async"
	```
	RBloomFilterAsync<String> breached = redisson.getBloomFilter("breached-passwords");
	
	// only the hashes that may be present come back
	RFuture<Set<String>> suspicious = breached.existsAsync(candidateHashes);
	```
=== "Reactive"
	```
	RedissonReactiveClient redissonReactive = redisson.reactive();
	RBloomFilterReactive<String> breached = redissonReactive.getBloomFilter("breached-passwords");
	
	// only the hashes that may be present come back
	Mono<Set<String>> suspicious = breached.exists(candidateHashes);
	```
=== "RxJava3"
	```
	RedissonRxClient redissonRx = redisson.rxJava();
	RBloomFilterRx<String> breached = redissonRx.getBloomFilter("breached-passwords");
	
	// only the hashes that may be present come back
	Single<Set<String>> suspicious = breached.exists(candidateHashes);
	```

**Suppressing Duplicate Work**

For best-effort deduplication over a high-volume stream - notifications already sent, records already processed, URLs already crawled - `add` doubles as a test: it returns `false` when the element was already present, so a single call both records the element and reports whether it is new. A Bloom filter never removes elements and stores no ids, so memory stays small and fixed even as the seen-set grows into the millions. A rare false positive skips a genuinely new item, which is acceptable when occasional misses are tolerable, and `count()` tracks the approximate number of distinct items seen.

=== "Sync"
	```
	RBloomFilter<String> seen = redisson.getBloomFilter("sent-notifications");
	seen.tryInit(20000000L, 0.01);
	
	// add returns false if the id was already present
	if (seen.add(notificationId)) {
	    send(notificationId);   // first time seen - deliver it
	}
	
	// approximate number of distinct ids recorded
	long distinct = seen.count();
	```
=== "Async"
	```
	RBloomFilterAsync<String> seen = redisson.getBloomFilter("sent-notifications");
	
	// true if newly added, false if already seen
	RFuture<Boolean> isNew = seen.addAsync(notificationId);
	```
=== "Reactive"
	```
	RedissonReactiveClient redissonReactive = redisson.reactive();
	RBloomFilterReactive<String> seen = redissonReactive.getBloomFilter("sent-notifications");
	
	// true if newly added, false if already seen
	Mono<Boolean> isNew = seen.add(notificationId);
	```
=== "RxJava3"
	```
	RedissonRxClient redissonRx = redisson.rxJava();
	RBloomFilterRx<String> seen = redissonRx.getBloomFilter("sent-notifications");
	
	// true if newly added, false if already seen
	Single<Boolean> isNew = seen.add(notificationId);
	```

{% include 'data-and-services/bloom-filter-native.md' %}

{% include 'data-and-services/cuckoo-filter.md' %}

## HyperLogLog
Java implementation of Valkey or Redis based [RHyperLogLog](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RHyperLogLog.html) object is a probabilistic data structure that estimates the number of distinct elements (the cardinality) of a very large set using a small, fixed amount of memory - on the order of a few kilobytes - no matter how many elements are added. The estimate carries a small standard error of about 0.81%, and the object is thread-safe.

Because it keeps only the estimate and not the elements themselves, it cannot list members or test whether a specific one is present - it answers "how many distinct items" rather than "is this item here". Elements are added one at a time or in bulk, the running estimate is read with `count`, and several logs can be combined or merged to count distinct items across them.

Code examples:

=== "Sync"
    ```
    RHyperLogLog<Integer> log = redisson.getHyperLogLog("log");

    log.add(1);
    log.add(2);
    log.add(3);

    long count = log.count();
    ```
=== "Async"
    ```
    RHyperLogLogAsync<Integer> log = redisson.getHyperLogLog("log");

    RFuture<Boolean> add1 = log.addAsync(1);
    RFuture<Boolean> add2 = log.addAsync(2);
    RFuture<Boolean> add3 = log.addAsync(3);

    RFuture<Long> countFuture = log.countAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RHyperLogLogReactive<Integer> log = redisson.getHyperLogLog("log");

    Mono<Boolean> add1 = log.add(1);
    Mono<Boolean> add2 = log.add(2);
    Mono<Boolean> add3 = log.add(3);

    Mono<Long> countMono = log.count();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RHyperLogLogRx<Integer> log = redisson.getHyperLogLog("log");

    Single<Boolean> add1 = log.add(1);
    Single<Boolean> add2 = log.add(2);
    Single<Boolean> add3 = log.add(3);

    Single<Long> countRx = log.count();
    ```

**Adding elements**

`add` records a single element and `addAll` records a whole collection at once. Both return `true` if the addition changed the structure's internal state - that is, if the element was probably new - and `false` if it almost certainly had no effect on the estimate.

=== "Sync"
    ```
    RHyperLogLog<String> log = redisson.getHyperLogLog("visitors");

    // record a single element
    boolean changed = log.add("user-1");

    // record many at once
    log.addAll(Arrays.asList("user-2", "user-3", "user-4"));
    ```
=== "Async"
    ```
    RHyperLogLogAsync<String> log = redisson.getHyperLogLog("visitors");

    RFuture<Boolean> changedFuture = log.addAsync("user-1");
    RFuture<Boolean> bulkFuture = log.addAllAsync(Arrays.asList("user-2", "user-3", "user-4"));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RHyperLogLogReactive<String> log = redisson.getHyperLogLog("visitors");

    Mono<Boolean> changedMono = log.add("user-1");
    Mono<Boolean> bulkMono = log.addAll(Arrays.asList("user-2", "user-3", "user-4"));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RHyperLogLogRx<String> log = redisson.getHyperLogLog("visitors");

    Single<Boolean> changedRx = log.add("user-1");
    Single<Boolean> bulkRx = log.addAll(Arrays.asList("user-2", "user-3", "user-4"));
    ```

**Counting distinct elements**

`count` returns the estimated number of distinct elements added to this log. The result is approximate - HyperLogLog trades exactness for a fixed, tiny memory footprint - so it suits large-scale counting where an exact figure is not required.

=== "Sync"
    ```
    RHyperLogLog<String> log = redisson.getHyperLogLog("visitors");

    long distinct = log.count();   // estimated number of distinct elements
    ```
=== "Async"
    ```
    RHyperLogLogAsync<String> log = redisson.getHyperLogLog("visitors");

    RFuture<Long> distinctFuture = log.countAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RHyperLogLogReactive<String> log = redisson.getHyperLogLog("visitors");

    Mono<Long> distinctMono = log.count();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RHyperLogLogRx<String> log = redisson.getHyperLogLog("visitors");

    Single<Long> distinctRx = log.count();
    ```

**Combining multiple logs**

Several logs can be counted together. `countWith` returns the estimated number of distinct elements across this log and the named ones without changing any of them - a combined total over, say, daily logs. `mergeWith` instead folds the named logs into this one, so its own count reflects their union from then on.

=== "Sync"
    ```
    RHyperLogLog<String> today = redisson.getHyperLogLog("visitors:today");

    // combined distinct count across several logs, without modifying them
    long weekly = today.countWith("visitors:mon", "visitors:tue", "visitors:wed");

    // merge other logs into this one; its count now reflects the union
    today.mergeWith("visitors:mon", "visitors:tue");
    ```
=== "Async"
    ```
    RHyperLogLogAsync<String> today = redisson.getHyperLogLog("visitors:today");

    RFuture<Long> weeklyFuture = today.countWithAsync("visitors:mon", "visitors:tue", "visitors:wed");
    RFuture<Void> mergeFuture = today.mergeWithAsync("visitors:mon", "visitors:tue");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RHyperLogLogReactive<String> today = redisson.getHyperLogLog("visitors:today");

    Mono<Long> weeklyMono = today.countWith("visitors:mon", "visitors:tue", "visitors:wed");
    Mono<Void> mergeMono = today.mergeWith("visitors:mon", "visitors:tue");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RHyperLogLogRx<String> today = redisson.getHyperLogLog("visitors:today");

    Single<Long> weeklyRx = today.countWith("visitors:mon", "visitors:tue", "visitors:wed");
    Completable mergeRx = today.mergeWith("visitors:mon", "visitors:tue");
    ```

### Use Cases

HyperLogLog estimates the number of distinct elements in a set using a fixed, tiny amount of memory (around 12KB) no matter how many items are counted, with a typical error of about 1%. It fits problems where the count of unique items matters but the items themselves do not need to be stored, and where exact counting would otherwise require memory proportional to the cardinality.

**Unique Visitor and Audience Counting**

Counting distinct users, IP addresses, or devices over high-volume traffic is the canonical use: each event is added to the log, and `count()` returns the approximate number of unique values seen. A set holding every id would grow with the audience, while the log stays a fixed ~12KB whether it has seen a thousand ids or a billion.

=== "Sync"
    ```
    RHyperLogLog<String> visitors = redisson.getHyperLogLog("visitors:2026-06-01");

    // record each visit - duplicates collapse automatically
    visitors.add(userId);

    // ingest a batch in a single call
    visitors.addAll(List.of("user:a", "user:b", "user:c"));

    // approximate number of unique visitors (about 1% error)
    long unique = visitors.count();
    ```
=== "Async"
    ```
    RHyperLogLogAsync<String> visitors = redisson.getHyperLogLog("visitors:2026-06-01");

    RFuture<Boolean> added = visitors.addAsync(userId);

    // approximate number of unique visitors
    RFuture<Long> unique = visitors.countAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RHyperLogLogReactive<String> visitors = redisson.getHyperLogLog("visitors:2026-06-01");

    Mono<Boolean> added = visitors.add(userId);

    // approximate number of unique visitors
    Mono<Long> unique = visitors.count();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RHyperLogLogRx<String> visitors = redisson.getHyperLogLog("visitors:2026-06-01");

    Single<Boolean> added = visitors.add(userId);

    // approximate number of unique visitors
    Single<Long> unique = visitors.count();
    ```

**Rolling Unique Counts Across Time Windows**

Reporting unique visitors per day, week, and month runs into a trap: distinct counts cannot simply be summed, because a user active on several days would be counted more than once. HyperLogLogs are mergeable, which solves this directly. Keeping one log per day, `countWith` estimates the de-duplicated union over a range without modifying the daily logs, while `mergeWith` folds several daily logs into a persistent weekly or monthly log.

=== "Sync"
    ```
    RHyperLogLog<String> mon = redisson.getHyperLogLog("visitors:2026-06-01");
    RHyperLogLog<String> tue = redisson.getHyperLogLog("visitors:2026-06-02");
    RHyperLogLog<String> wed = redisson.getHyperLogLog("visitors:2026-06-03");

    // unique visitors across the three days, counting returning users once
    long weekToDate = mon.countWith(tue.getName(), wed.getName());

    // roll the daily logs up into a persistent weekly log
    RHyperLogLog<String> week = redisson.getHyperLogLog("visitors:2026-W23");
    week.mergeWith(mon.getName(), tue.getName(), wed.getName());
    long weeklyUnique = week.count();
    ```
=== "Async"
    ```
    RHyperLogLogAsync<String> mon = redisson.getHyperLogLog("visitors:2026-06-01");

    // de-duplicated union across days without modifying the daily logs
    RFuture<Long> weekToDate = mon.countWithAsync("visitors:2026-06-02", "visitors:2026-06-03");

    // persistent roll-up into a weekly log
    RHyperLogLogAsync<String> week = redisson.getHyperLogLog("visitors:2026-W23");
    RFuture<Void> merged = week.mergeWithAsync("visitors:2026-06-01", "visitors:2026-06-02");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RHyperLogLogReactive<String> mon = redisson.getHyperLogLog("visitors:2026-06-01");

    // de-duplicated union across days without modifying the daily logs
    Mono<Long> weekToDate = mon.countWith("visitors:2026-06-02", "visitors:2026-06-03");

    // persistent roll-up into a weekly log
    RHyperLogLogReactive<String> week = redisson.getHyperLogLog("visitors:2026-W23");
    Mono<Void> merged = week.mergeWith("visitors:2026-06-01", "visitors:2026-06-02");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RHyperLogLogRx<String> mon = redisson.getHyperLogLog("visitors:2026-06-01");

    // de-duplicated union across days without modifying the daily logs
    Single<Long> weekToDate = mon.countWith("visitors:2026-06-02", "visitors:2026-06-03");

    // persistent roll-up into a weekly log
    RHyperLogLogRx<String> week = redisson.getHyperLogLog("visitors:2026-W23");
    Completable merged = week.mergeWith("visitors:2026-06-01", "visitors:2026-06-02");
    ```

**Estimating Audience Overlap**

Since the union of two logs can be estimated with `countWith`, the overlap between two large sets follows from inclusion-exclusion: the number of common elements is approximately the sum of the two individual counts minus the count of their union. This estimates how many users are active on both web and mobile, or how many customers two campaigns reached in common, without storing either set of ids.

=== "Sync"
    ```
    RHyperLogLog<String> web = redisson.getHyperLogLog("active:web");
    RHyperLogLog<String> mobile = redisson.getHyperLogLog("active:mobile");

    long onWeb = web.count();                       // users seen on web
    long onMobile = mobile.count();                 // users seen on mobile
    long onEither = web.countWith(mobile.getName());// users on either platform

    // inclusion-exclusion: both = web + mobile - either
    long onBoth = onWeb + onMobile - onEither;
    ```
=== "Async"
    ```
    RHyperLogLogAsync<String> web = redisson.getHyperLogLog("active:web");

    RFuture<Long> onWeb = web.countAsync();
    RFuture<Long> onEither = web.countWithAsync("active:mobile");
    // combine onWeb, the mobile count, and onEither via inclusion-exclusion
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RHyperLogLogReactive<String> web = redisson.getHyperLogLog("active:web");

    Mono<Long> onWeb = web.count();
    Mono<Long> onEither = web.countWith("active:mobile");
    // combine with the mobile count via inclusion-exclusion
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RHyperLogLogRx<String> web = redisson.getHyperLogLog("active:web");

    Single<Long> onWeb = web.count();
    Single<Long> onEither = web.countWith("active:mobile");
    // combine with the mobile count via inclusion-exclusion
    ```
