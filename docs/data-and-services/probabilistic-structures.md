Probabilistic structures trade exact answers for dramatic memory savings. Rather than storing the elements themselves, they keep a small, fixed-size summary and answer questions approximately - *is this element present?* (with a tunable false-positive rate), *how many distinct elements are there?* (within a small error margin), *which elements are most frequent?*, or *what value falls at a given percentile?* - using far less memory than an exact structure would.

Redisson provides six: the [Bloom filter](#bloom-filter) and its [Bloom filter (Native)](#bloom-filter-native) variant and the [Cuckoo filter](#cuckoo-filter) for membership testing (the Cuckoo filter also supports deletion), [HyperLogLog](#hyperloglog) for estimating the number of distinct elements, [TopK](#topk) for tracking the most frequent elements, and [TDigest](#tdigest) for estimating quantiles and the distribution of a stream of values.

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

### Adding elements

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

### Testing membership

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

### Filter information

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

### Adding elements

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

### Counting distinct elements

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

### Combining multiple logs

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

## TopK

Java implementation of Redis based [RTopK](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTopK.html) object is a probabilistic data structure that keeps track of the `k` most frequent items in a stream using the HeavyKeeper algorithm, with a fixed amount of memory regardless of the number of distinct items seen. It is backed by the `TOPK.*` commands of the Redis Bloom module. This object is thread-safe.

A Top-K must be initialized once before items are added, after which occurrences are recorded and the current leaders can be queried at any time.

### Initialization

`init(int)` reserves space for the given number of top items using default tuning, while `init(TopKInitArgs)` additionally controls the underlying sketch: `width` (counters per array, default 8), `depth` (number of counter arrays, default 7) and `decay` (probability of a counter being decreased on collision, default 0.9).

=== "Sync"
    ```java
    RTopK<String> topK = redisson.getTopK("searchTerms");

    // track the 50 most frequent items with default tuning
    topK.init(50);

    // or tune the underlying sketch
    topK.init(TopKInitArgs.topK(50)
                    .width(2000)
                    .depth(7)
                    .decay(0.925));
    ```
=== "Async"
    ```java
    RTopKAsync<String> topK = redisson.getTopK("searchTerms");

    RFuture<Void> future = topK.initAsync(50);

    RFuture<Void> tuned = topK.initAsync(TopKInitArgs.topK(50)
                    .width(2000).depth(7).decay(0.925));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTopKReactive<String> topK = redisson.getTopK("searchTerms");

    Mono<Void> result = topK.init(50);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTopKRx<String> topK = redisson.getTopK("searchTerms");

    Completable result = topK.init(50);
    ```

### Adding items

Items are recorded with `add()`, or with an explicit weight using `incrementBy()`. Both return the item, if any, that was pushed out of the top-K list as a result of the call, or `null` when nothing was evicted. The list- and map-based overloads return their results positionally aligned to the input.

=== "Sync"
    ```java
    RTopK<String> topK = redisson.getTopK("searchTerms");

    // record an occurrence; returns the evicted item or null
    String evicted = topK.add("redis");

    // record several at once (result aligned to input)
    List<String> evictedItems = topK.add(List.of("redis", "valkey", "redis"));

    // record with an explicit weight
    topK.incrementBy("redis", 5);

    // weight several items at once
    topK.incrementBy(Map.of("redis", 5, "valkey", 2));
    ```
=== "Async"
    ```java
    RTopKAsync<String> topK = redisson.getTopK("searchTerms");

    RFuture<String> evicted = topK.addAsync("redis");
    RFuture<List<String>> evictedItems = topK.addAsync(List.of("redis", "valkey"));
    RFuture<String> incremented = topK.incrementByAsync("redis", 5);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTopKReactive<String> topK = redisson.getTopK("searchTerms");

    Mono<String> evicted = topK.add("redis");
    Mono<List<String>> evictedItems = topK.add(List.of("redis", "valkey"));
    Mono<String> incremented = topK.incrementBy("redis", 5);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTopKRx<String> topK = redisson.getTopK("searchTerms");

    Maybe<String> evicted = topK.add("redis");
    Single<List<String>> evictedItems = topK.add(List.of("redis", "valkey"));
    Maybe<String> incremented = topK.incrementBy("redis", 5);
    ```

### Querying membership and leaders

`contains()` reports whether an item is currently among the leaders, `list()` returns the current top-K items and `listWithCount()` returns them together with their approximate counts. The older `count()` method is deprecated since Redis Bloom 2.4.0 because its estimate can be inaccurate; prefer `listWithCount()`.

=== "Sync"
    ```java
    RTopK<String> topK = redisson.getTopK("searchTerms");

    // is an item currently among the leaders?
    boolean present = topK.contains("redis");
    List<Boolean> presence = topK.contains(List.of("redis", "valkey"));

    // current leaders, optionally with their approximate counts
    List<String> leaders = topK.list();
    Map<String, Long> leadersWithCount = topK.listWithCount();
    ```
=== "Async"
    ```java
    RTopKAsync<String> topK = redisson.getTopK("searchTerms");

    RFuture<Boolean> present = topK.containsAsync("redis");
    RFuture<List<String>> leaders = topK.listAsync();
    RFuture<Map<String, Long>> leadersWithCount = topK.listWithCountAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTopKReactive<String> topK = redisson.getTopK("searchTerms");

    Mono<Boolean> present = topK.contains("redis");
    Mono<List<String>> leaders = topK.list();
    Mono<Map<String, Long>> leadersWithCount = topK.listWithCount();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTopKRx<String> topK = redisson.getTopK("searchTerms");

    Single<Boolean> present = topK.contains("redis");
    Single<List<String>> leaders = topK.list();
    Single<Map<String, Long>> leadersWithCount = topK.listWithCount();
    ```

### Top-K information

`getInfo()` returns the configured parameters of the structure - the number of tracked items together with the sketch `width`, `depth` and `decay`.

=== "Sync"
    ```java
    RTopK<String> topK = redisson.getTopK("searchTerms");

    TopKInfo info = topK.getInfo();
    long tracked = info.getTopK();
    long width = info.getWidth();
    long depth = info.getDepth();
    double decay = info.getDecay();
    ```
=== "Async"
    ```java
    RTopKAsync<String> topK = redisson.getTopK("searchTerms");

    RFuture<TopKInfo> info = topK.getInfoAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTopKReactive<String> topK = redisson.getTopK("searchTerms");

    Mono<TopKInfo> info = topK.getInfo();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTopKRx<String> topK = redisson.getTopK("searchTerms");

    Single<TopKInfo> info = topK.getInfo();
    ```

### Use Cases

Top-K answers *which items are most frequent?* over an unbounded stream while using only a small, fixed amount of memory, which makes it a fit for trend detection and heavy-hitter analysis at high volume, where keeping an exact counter per distinct item would be too expensive.

**Trending content**

Leaderboards of trending search terms, hashtags, products or pages can be maintained directly from the event stream. Each view or query is fed in with `add` (or weighted with `incrementBy`), and the current leaders - with their approximate counts - are read back with `listWithCount`, without storing a counter per distinct item.

=== "Sync"
    ```java
    RTopK<String> trending = redisson.getTopK("trending:searches");
    trending.init(20);

    // record each search as it happens
    trending.add(query);

    // current top searches with approximate counts
    Map<String, Long> top = trending.listWithCount();
    ```
=== "Async"
    ```java
    RTopKAsync<String> trending = redisson.getTopK("trending:searches");

    RFuture<String> evicted = trending.addAsync(query);
    RFuture<Map<String, Long>> top = trending.listWithCountAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTopKReactive<String> trending = redisson.getTopK("trending:searches");

    Mono<String> evicted = trending.add(query);
    Mono<Map<String, Long>> top = trending.listWithCount();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTopKRx<String> trending = redisson.getTopK("trending:searches");

    Maybe<String> evicted = trending.add(query);
    Single<Map<String, Long>> top = trending.listWithCount();
    ```

**Heavy-hitter detection**

In high-volume traffic - API calls, log lines, network flows - a Top-K surfaces the handful of clients, IPs or keys responsible for a disproportionate share of requests, so hot keys and potential abuse can be spotted in bounded memory. `contains` then gives a cheap test for whether a given client is currently among the top talkers.

=== "Sync"
    ```java
    RTopK<String> talkers = redisson.getTopK("api:top-callers");
    talkers.init(10);

    // weight by request cost as traffic arrives
    talkers.incrementBy(apiKey, requestCost);

    // is this caller currently a heavy hitter?
    boolean isHeavyHitter = talkers.contains(apiKey);
    List<String> worst = talkers.list();
    ```
=== "Async"
    ```java
    RTopKAsync<String> talkers = redisson.getTopK("api:top-callers");

    RFuture<String> evicted = talkers.incrementByAsync(apiKey, requestCost);
    RFuture<Boolean> isHeavyHitter = talkers.containsAsync(apiKey);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTopKReactive<String> talkers = redisson.getTopK("api:top-callers");

    Mono<String> evicted = talkers.incrementBy(apiKey, requestCost);
    Mono<Boolean> isHeavyHitter = talkers.contains(apiKey);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTopKRx<String> talkers = redisson.getTopK("api:top-callers");

    Maybe<String> evicted = talkers.incrementBy(apiKey, requestCost);
    Single<Boolean> isHeavyHitter = talkers.contains(apiKey);
    ```

## TDigest

Java implementation of Redis based [RTDigest](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTDigest.html) object is a t-digest - a probabilistic data structure that estimates quantiles, ranks and the cumulative distribution of a stream of observations with sub-linear memory and high accuracy at the distribution's tails. It is backed by the `TDIGEST.*` commands of the Redis Bloom module. This object is thread-safe.

The sketch must be created with `create()` before use.

### Initialization

Create the sketch before adding observations. An optional compression factor trades memory for accuracy - a higher value yields more accurate estimates, particularly at the tails, at the cost of more memory. `reset()` empties the sketch and re-initializes it.

=== "Sync"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    // create with default compression
    tdigest.create();

    // or create with a higher compression for better tail accuracy
    tdigest.create(200);

    // empty and re-initialize the sketch
    tdigest.reset();
    ```
=== "Async"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    RFuture<Void> createFuture = tdigest.createAsync(200);
    RFuture<Void> resetFuture = tdigest.resetAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive tdigest = redisson.getTDigest("latencies");

    Mono<Void> createMono = tdigest.create(200);
    Mono<Void> resetMono = tdigest.reset();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx tdigest = redisson.getTDigest("latencies");

    Completable createRx = tdigest.create(200);
    Completable resetRx = tdigest.reset();
    ```

### Adding observations

Feed observations into the sketch one at a time or several at once.

=== "Sync"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    tdigest.add(12.5);
    tdigest.add(8.0, 15.3, 22.1, 9.7);
    ```
=== "Async"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    RFuture<Void> addFuture = tdigest.addAsync(12.5);
    RFuture<Void> addAllFuture = tdigest.addAsync(8.0, 15.3, 22.1, 9.7);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive tdigest = redisson.getTDigest("latencies");

    Mono<Void> addMono = tdigest.add(12.5);
    Mono<Void> addAllMono = tdigest.add(8.0, 15.3, 22.1, 9.7);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx tdigest = redisson.getTDigest("latencies");

    Completable addRx = tdigest.add(12.5);
    Completable addAllRx = tdigest.add(8.0, 15.3, 22.1, 9.7);
    ```

### Merging sketches

Several sketches can be merged into one destination, which is useful for combining per-node or per-shard sketches into a single global view. `mergeWith(String...)` merges the named source sketches on top of the destination's current contents, while `mergeWith(TDigestMergeArgs)` additionally controls the resulting compression and can override the destination, discarding its existing observations before the merge.

=== "Sync"
    ```java
    RTDigest global = redisson.getTDigest("latencies:global");
    global.create();

    // merge per-node sketches into the destination
    global.mergeWith("latencies:node1", "latencies:node2");

    // set the resulting compression and discard the destination's current contents
    global.mergeWith(TDigestMergeArgs.keys("latencies:node1", "latencies:node2")
                        .compression(200)
                        .override());
    ```
=== "Async"
    ```java
    RTDigest global = redisson.getTDigest("latencies:global");

    RFuture<Void> mergeFuture = global.mergeWithAsync("latencies:node1", "latencies:node2");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive global = redisson.getTDigest("latencies:global");

    Mono<Void> mergeMono = global.mergeWith("latencies:node1", "latencies:node2");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx global = redisson.getTDigest("latencies:global");

    Completable mergeRx = global.mergeWith("latencies:node1", "latencies:node2");
    ```

### Quantiles and cumulative distribution

`quantile()` estimates, for each input fraction, the value below which that fraction of observations fall - for example the median or the 99th percentile. `cumulativeProbability()` is the inverse: for each input value it estimates the fraction of observations less than or equal to it. Both accept several inputs and return one result per input.

=== "Sync"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    // median, 95th and 99th percentile latency
    List<Double> percentiles = tdigest.quantile(0.5, 0.95, 0.99);

    // fraction of requests served at or under 100ms and 250ms
    List<Double> fractions = tdigest.cumulativeProbability(100.0, 250.0);
    ```
=== "Async"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    RFuture<List<Double>> percentilesFuture = tdigest.quantileAsync(0.5, 0.95, 0.99);
    RFuture<List<Double>> fractionsFuture = tdigest.cumulativeProbabilityAsync(100.0, 250.0);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive tdigest = redisson.getTDigest("latencies");

    Mono<List<Double>> percentilesMono = tdigest.quantile(0.5, 0.95, 0.99);
    Mono<List<Double>> fractionsMono = tdigest.cumulativeProbability(100.0, 250.0);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx tdigest = redisson.getTDigest("latencies");

    Single<List<Double>> percentilesRx = tdigest.quantile(0.5, 0.95, 0.99);
    Single<List<Double>> fractionsRx = tdigest.cumulativeProbability(100.0, 250.0);
    ```

### Ranks

`rank()` returns, for each value, the number of observations less than it, and `revRank()` the number greater than it (both return `-1` for a value outside the observed range). `byRank()` and `byRevRank()` are the inverse, returning the value at a given rank counting from the smallest or largest observation respectively.

=== "Sync"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    // how many observations fall below / above given values
    List<Long> ranks = tdigest.rank(100.0, 250.0);
    List<Long> reverseRanks = tdigest.revRank(100.0, 250.0);

    // the value at given ranks, from the smallest / largest observation
    List<Double> values = tdigest.byRank(0, 99);
    List<Double> valuesFromTop = tdigest.byRevRank(0, 9);
    ```
=== "Async"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    RFuture<List<Long>> ranksFuture = tdigest.rankAsync(100.0, 250.0);
    RFuture<List<Long>> reverseRanksFuture = tdigest.revRankAsync(100.0, 250.0);
    RFuture<List<Double>> valuesFuture = tdigest.byRankAsync(0, 99);
    RFuture<List<Double>> valuesFromTopFuture = tdigest.byRevRankAsync(0, 9);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive tdigest = redisson.getTDigest("latencies");

    Mono<List<Long>> ranksMono = tdigest.rank(100.0, 250.0);
    Mono<List<Double>> valuesMono = tdigest.byRank(0, 99);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx tdigest = redisson.getTDigest("latencies");

    Single<List<Long>> ranksRx = tdigest.rank(100.0, 250.0);
    Single<List<Double>> valuesRx = tdigest.byRank(0, 99);
    ```

### Summary statistics

`getMin()` and `getMax()` return the smallest and largest observations (or `NaN` when the sketch is empty), and `trimmedMean()` returns the mean of the observations between two cut quantiles, ignoring outliers at the tails.

=== "Sync"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    double min = tdigest.getMin();
    double max = tdigest.getMax();

    // mean ignoring the bottom 10% and top 10% of observations
    double trimmed = tdigest.trimmedMean(0.1, 0.9);
    ```
=== "Async"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    RFuture<Double> minFuture = tdigest.getMinAsync();
    RFuture<Double> maxFuture = tdigest.getMaxAsync();
    RFuture<Double> trimmedFuture = tdigest.trimmedMeanAsync(0.1, 0.9);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive tdigest = redisson.getTDigest("latencies");

    Mono<Double> minMono = tdigest.getMin();
    Mono<Double> trimmedMono = tdigest.trimmedMean(0.1, 0.9);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx tdigest = redisson.getTDigest("latencies");

    Single<Double> minRx = tdigest.getMin();
    Single<Double> trimmedRx = tdigest.trimmedMean(0.1, 0.9);
    ```

### Sketch information

`getInfo()` returns a `TDigestInfo` describing the sketch: its compression and capacity, the number of merged and unmerged nodes and their weights, the total number of observations, the number of compressions performed, and the estimated memory usage.

=== "Sync"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    TDigestInfo info = tdigest.getInfo();
    long compression = info.getCompression();
    long observations = info.getObservations();
    long memoryUsage = info.getMemoryUsage();
    ```
=== "Async"
    ```java
    RTDigest tdigest = redisson.getTDigest("latencies");

    RFuture<TDigestInfo> infoFuture = tdigest.getInfoAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive tdigest = redisson.getTDigest("latencies");

    Mono<TDigestInfo> infoMono = tdigest.getInfo();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx tdigest = redisson.getTDigest("latencies");

    Single<TDigestInfo> infoRx = tdigest.getInfo();
    ```

### Use Cases

A t-digest answers *what does the value distribution look like?* over an unbounded stream of numbers - any quantile, rank or trimmed mean - while using only a small, fixed amount of memory and staying most accurate at the tails, where percentile monitoring needs it most. This makes it a fit for latency and SLA tracking and for distribution-based thresholds at high volume, where retaining every observation to compute exact percentiles would be too expensive.

**Latency and SLA monitoring**

Request or operation latencies are fed into the sketch with `add` as they complete, and tail percentiles - p50, p95, p99 - are read back at any time with `quantile`, giving a continuously updated picture of the latency distribution in a tiny, fixed footprint. `cumulativeProbability` answers the inverse SLA question directly - what fraction of requests came in under the target - and per-instance sketches gathered across a fleet can be folded into one cluster-wide view with `mergeWith`.

=== "Sync"
    ```java
    RTDigest latencies = redisson.getTDigest("latencies:checkout");
    latencies.create();

    // record each request's latency as it completes
    latencies.add(requestMillis);

    // p50 / p95 / p99 for the dashboard
    List<Double> percentiles = latencies.quantile(0.5, 0.95, 0.99);

    // SLA check: fraction of requests served under the 200ms target
    double withinSla = latencies.cumulativeProbability(200.0).get(0);
    ```
=== "Async"
    ```java
    RTDigest latencies = redisson.getTDigest("latencies:checkout");

    RFuture<Void> recordFuture = latencies.addAsync(requestMillis);
    RFuture<List<Double>> percentilesFuture = latencies.quantileAsync(0.5, 0.95, 0.99);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive latencies = redisson.getTDigest("latencies:checkout");

    Mono<Void> recordMono = latencies.add(requestMillis);
    Mono<List<Double>> percentilesMono = latencies.quantile(0.5, 0.95, 0.99);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx latencies = redisson.getTDigest("latencies:checkout");

    Completable recordRx = latencies.add(requestMillis);
    Single<List<Double>> percentilesRx = latencies.quantile(0.5, 0.95, 0.99);
    ```

**Adaptive thresholds and outlier detection**

Because the sketch tracks the whole distribution, it can supply thresholds that adapt to the data instead of being hard-coded. `quantile` yields a dynamic cutoff - flag anything above the current 99th percentile - while `cumulativeProbability` and `rank` place a new observation within the historical distribution to show how extreme it is. `trimmedMean` provides a stable central estimate that ignores the heaviest outliers at both tails.

=== "Sync"
    ```java
    RTDigest amounts = redisson.getTDigest("tx:amounts");
    amounts.create();

    // dynamic threshold: the current 99th percentile
    double cutoff = amounts.quantile(0.99).get(0);

    // how extreme is a new value relative to history?
    double fractionBelow = amounts.cumulativeProbability(newAmount).get(0);
    boolean isOutlier = newAmount > cutoff;

    // robust baseline that ignores the top and bottom 5%
    double baseline = amounts.trimmedMean(0.05, 0.95);
    ```
=== "Async"
    ```java
    RTDigest amounts = redisson.getTDigest("tx:amounts");

    RFuture<List<Double>> cutoffFuture = amounts.quantileAsync(0.99);
    RFuture<Double> baselineFuture = amounts.trimmedMeanAsync(0.05, 0.95);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTDigestReactive amounts = redisson.getTDigest("tx:amounts");

    Mono<List<Double>> cutoffMono = amounts.quantile(0.99);
    Mono<Double> baselineMono = amounts.trimmedMean(0.05, 0.95);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTDigestRx amounts = redisson.getTDigest("tx:amounts");

    Single<List<Double>> cutoffRx = amounts.quantile(0.99);
    Single<Double> baselineRx = amounts.trimmedMean(0.05, 0.95);
    ```
