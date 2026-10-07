## Time Series (Native)

Java implementation of Valkey or Redis based [RTimeSeriesNative](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTimeSeriesNative.html) object is a time series backed by the native `TS.*` commands of the RedisTimeSeries module. Each sample pairs a millisecond timestamp with a `double` value, and a series carries *labels* - name/value pairs that describe it and that queries use to select it. This object is thread-safe.

Unlike [Time Series](collections.md#time-series), which stores arbitrary Java objects in a sorted set, this object stores numbers in the module's own compressed representation. In exchange for that narrower value type it gains retention policies, downsampling into aggregation buckets, server-side compaction rules, and queries that select series by label across the whole keyspace.

Two objects make up the API:

* `RTimeSeriesNative` - one named series: create it, add samples, query it, compact it.
* [RTimeSeriesNatives](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTimeSeriesNatives.html) - many series at once, selected by label filter rather than by name.

Requires the **RedisTimeSeries** module, which is built into Redis 8.0+ and available as a module for earlier versions.

### Creating a series

A series must exist before samples can be added to it. `create` fails if the key is already taken; `createIfAbsent` returns `false` instead, so it is the one to call on a startup path that may run more than once.

`TSCreateArgs` sets the retention window, chunk encoding and size, what to do about a duplicate timestamp, the labels that queries select on, and an ignore window that discards samples too close to the last one.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    // simple creation
    ts.create();

    // returns false if the series already exists
    boolean created = ts.createIfAbsent();

    // creation with parameters
    ts.create(TSCreateArgs.defaults()
                    .retention(Duration.ofDays(7))
                    .encoding(TSEncoding.COMPRESSED)
                    .chunkSize(4096)
                    .duplicatePolicy(TSDuplicatePolicy.LAST)
                    .label("sensor", "1")
                    .label("area", "warehouse"));

    // change a series that already exists - labels are replaced, not merged
    ts.alter(TSAlterArgs.defaults()
                    .retention(Duration.ofDays(30))
                    .label("area", "office"));
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    RFuture<Void> createFuture = ts.createAsync();

    RFuture<Boolean> createdFuture = ts.createIfAbsentAsync();

    RFuture<Void> argsFuture = ts.createAsync(TSCreateArgs.defaults()
                    .retention(Duration.ofDays(7))
                    .encoding(TSEncoding.COMPRESSED)
                    .chunkSize(4096)
                    .duplicatePolicy(TSDuplicatePolicy.LAST)
                    .label("sensor", "1")
                    .label("area", "warehouse"));

    RFuture<Void> alterFuture = ts.alterAsync(TSAlterArgs.defaults()
                    .retention(Duration.ofDays(30))
                    .label("area", "office"));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("sensor:temperature");

    Mono<Void> createMono = ts.create();

    Mono<Boolean> createdMono = ts.createIfAbsent();

    Mono<Void> argsMono = ts.create(TSCreateArgs.defaults()
                    .retention(Duration.ofDays(7))
                    .encoding(TSEncoding.COMPRESSED)
                    .chunkSize(4096)
                    .duplicatePolicy(TSDuplicatePolicy.LAST)
                    .label("sensor", "1")
                    .label("area", "warehouse"));

    Mono<Void> alterMono = ts.alter(TSAlterArgs.defaults()
                    .retention(Duration.ofDays(30))
                    .label("area", "office"));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("sensor:temperature");

    Completable createRx = ts.create();

    Single<Boolean> createdRx = ts.createIfAbsent();

    Completable argsRx = ts.create(TSCreateArgs.defaults()
                    .retention(Duration.ofDays(7))
                    .encoding(TSEncoding.COMPRESSED)
                    .chunkSize(4096)
                    .duplicatePolicy(TSDuplicatePolicy.LAST)
                    .label("sensor", "1")
                    .label("area", "warehouse"));

    Completable alterRx = ts.alter(TSAlterArgs.defaults()
                    .retention(Duration.ofDays(30))
                    .label("area", "office"));
    ```

A retention of `Duration.ZERO` keeps samples forever, which is the default. `TSDuplicatePolicy` decides what happens when a sample arrives at a timestamp that already holds one: `BLOCK` (the default) rejects it, while `FIRST`, `LAST`, `MIN` and `MAX` keep one of the two values. `TSEncoding.COMPRESSED` is the default and is far more compact for slowly-changing values; `UNCOMPRESSED` suits values that jump.

### Adding samples

`add` stores one sample and returns the timestamp it was stored at. `addCurrent` uses the server's clock, which is what a collector writing "now" wants. `addAll` stores a whole map in one command.

Unlike `TS.MADD`, a plain `add` creates the series if it does not exist, so a `TSAddArgs` may carry the same creation parameters as `TSCreateArgs` for that first call.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    // add a sample at an explicit timestamp
    long timestamp = ts.add(1670000000000L, 21.5);

    // add a sample at the server's current time
    long now = ts.addCurrent(22.1);

    // add several samples at once
    Map<Long, Double> samples = new LinkedHashMap<>();
    samples.put(1670000001000L, 21.7);
    samples.put(1670000002000L, 21.9);
    List<Long> timestamps = ts.addAll(samples);

    // add with parameters, creating the series if it is absent
    ts.add(TSAddArgs.sample(1670000003000L, 22.4)
                    .onDuplicate(TSDuplicatePolicy.LAST)
                    .retention(Duration.ofDays(7))
                    .label("sensor", "1"));

    // remove every sample in a closed range, returning how many went
    long removed = ts.removeRange(1670000000000L, 1670000002000L);
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    RFuture<Long> addFuture = ts.addAsync(1670000000000L, 21.5);

    RFuture<Long> nowFuture = ts.addCurrentAsync(22.1);

    Map<Long, Double> samples = new LinkedHashMap<>();
    samples.put(1670000001000L, 21.7);
    samples.put(1670000002000L, 21.9);
    RFuture<List<Long>> addAllFuture = ts.addAllAsync(samples);

    RFuture<Long> argsFuture = ts.addAsync(TSAddArgs.sample(1670000003000L, 22.4)
                    .onDuplicate(TSDuplicatePolicy.LAST)
                    .retention(Duration.ofDays(7))
                    .label("sensor", "1"));

    RFuture<Long> removedFuture = ts.removeRangeAsync(1670000000000L, 1670000002000L);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("sensor:temperature");

    Mono<Long> addMono = ts.add(1670000000000L, 21.5);

    Mono<Long> nowMono = ts.addCurrent(22.1);

    Map<Long, Double> samples = new LinkedHashMap<>();
    samples.put(1670000001000L, 21.7);
    samples.put(1670000002000L, 21.9);
    Mono<List<Long>> addAllMono = ts.addAll(samples);

    Mono<Long> argsMono = ts.add(TSAddArgs.sample(1670000003000L, 22.4)
                    .onDuplicate(TSDuplicatePolicy.LAST)
                    .retention(Duration.ofDays(7))
                    .label("sensor", "1"));

    Mono<Long> removedMono = ts.removeRange(1670000000000L, 1670000002000L);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("sensor:temperature");

    Single<Long> addRx = ts.add(1670000000000L, 21.5);

    Single<Long> nowRx = ts.addCurrent(22.1);

    Map<Long, Double> samples = new LinkedHashMap<>();
    samples.put(1670000001000L, 21.7);
    samples.put(1670000002000L, 21.9);
    Single<List<Long>> addAllRx = ts.addAll(samples);

    Single<Long> argsRx = ts.add(TSAddArgs.sample(1670000003000L, 22.4)
                    .onDuplicate(TSDuplicatePolicy.LAST)
                    .retention(Duration.ofDays(7))
                    .label("sensor", "1"));

    Single<Long> removedRx = ts.removeRange(1670000000000L, 1670000002000L);
    ```

`addAll` returns one timestamp per sample, in the order the map was iterated - pass a `LinkedHashMap` when that order matters.

### Incrementing counters

`incrementBy` and `decrementBy` add to the value of the most recent sample rather than storing an independent one, which is how a running counter is kept without reading it first. Called without a timestamp they use the server's clock; `TSIncrArgs.timestamp` pins one explicitly.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("page:views");

    // add to the latest sample, timestamped by the server
    long timestamp = ts.incrementBy(1);
    long lower = ts.decrementBy(0.5);

    // increment at an explicit timestamp, creating the series if absent
    ts.incrementBy(TSIncrArgs.value(5)
                    .timestamp(1670000000000L)
                    .retention(Duration.ofDays(1))
                    .label("page", "home"));
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("page:views");

    RFuture<Long> incrFuture = ts.incrementByAsync(1);
    RFuture<Long> decrFuture = ts.decrementByAsync(0.5);

    RFuture<Long> argsFuture = ts.incrementByAsync(TSIncrArgs.value(5)
                    .timestamp(1670000000000L)
                    .retention(Duration.ofDays(1))
                    .label("page", "home"));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("page:views");

    Mono<Long> incrMono = ts.incrementBy(1);
    Mono<Long> decrMono = ts.decrementBy(0.5);

    Mono<Long> argsMono = ts.incrementBy(TSIncrArgs.value(5)
                    .timestamp(1670000000000L)
                    .retention(Duration.ofDays(1))
                    .label("page", "home"));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("page:views");

    Single<Long> incrRx = ts.incrementBy(1);
    Single<Long> decrRx = ts.decrementBy(0.5);

    Single<Long> argsRx = ts.incrementBy(TSIncrArgs.value(5)
                    .timestamp(1670000000000L)
                    .retention(Duration.ofDays(1))
                    .label("page", "home"));
    ```

### Reading single samples

`get` returns the most recent sample, `first` the oldest. `getLatest` differs from `get` only on a compaction destination, where it also reports the bucket still being filled. `size`, `firstTimestamp` and `lastTimestamp` answer one fact about the series without pulling any samples, and `getLabels` returns the labels queries select on.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    TSSample latest = ts.get();
    TSSample oldest = ts.first();

    // on a compaction destination, includes the bucket still being filled
    TSSample latestIncludingPartial = ts.getLatest();

    long total = ts.size();
    long from = ts.firstTimestamp();
    long to = ts.lastTimestamp();

    Map<String, String> labels = ts.getLabels();

    // a sample is a timestamp and the values reported at it
    long time = latest.getTimestamp();
    double value = latest.getValue();
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    RFuture<TSSample> latestFuture = ts.getAsync();
    RFuture<TSSample> oldestFuture = ts.firstAsync();
    RFuture<TSSample> latestIncludingPartialFuture = ts.getLatestAsync();

    RFuture<Long> totalFuture = ts.sizeAsync();
    RFuture<Long> fromFuture = ts.firstTimestampAsync();
    RFuture<Long> toFuture = ts.lastTimestampAsync();

    RFuture<Map<String, String>> labelsFuture = ts.getLabelsAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("sensor:temperature");

    Mono<TSSample> latestMono = ts.get();
    Mono<TSSample> oldestMono = ts.first();
    Mono<TSSample> latestIncludingPartialMono = ts.getLatest();

    Mono<Long> totalMono = ts.size();
    Mono<Long> fromMono = ts.firstTimestamp();
    Mono<Long> toMono = ts.lastTimestamp();

    Mono<Map<String, String>> labelsMono = ts.getLabels();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("sensor:temperature");

    Maybe<TSSample> latestRx = ts.get();
    Maybe<TSSample> oldestRx = ts.first();
    Maybe<TSSample> latestIncludingPartialRx = ts.getLatest();

    Single<Long> totalRx = ts.size();
    Single<Long> fromRx = ts.firstTimestamp();
    Single<Long> toRx = ts.lastTimestamp();

    Single<Map<String, String>> labelsRx = ts.getLabels();
    ```

Labels belong to the series rather than to its samples - every sample shares them, which is why no sample-returning method reports them. They are what `RTimeSeriesNatives` filters on, and `alter` replaces the whole set.

### Range queries

`range` reads samples in increasing timestamp order and `rangeReversed` in decreasing order. `TSRangeArgs` bounds the range, caps the number of samples, filters by value or by exact timestamp, and downsamples into aggregation buckets.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    // a closed range
    List<TSSample> samples = ts.range(1670000000000L, 1670000900000L);

    // the whole series, newest first
    List<TSSample> newestFirst = ts.rangeReversed(TSRangeArgs.all());

    // open-ended bounds
    List<TSSample> since = ts.range(TSRangeArgs.from(1670000000000L));
    List<TSSample> until = ts.range(TSRangeArgs.to(1670000900000L));

    // at most 100 samples whose value falls between 20 and 25
    List<TSSample> filtered = ts.range(TSRangeArgs.all()
                    .filterByValue(20, 25)
                    .count(100));

    // only these exact timestamps
    List<TSSample> picked = ts.range(TSRangeArgs.all()
                    .filterByTimestamp(1670000000000L, 1670000060000L));
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    RFuture<List<TSSample>> samplesFuture = ts.rangeAsync(1670000000000L, 1670000900000L);

    RFuture<List<TSSample>> newestFirstFuture = ts.rangeReversedAsync(TSRangeArgs.all());

    RFuture<List<TSSample>> sinceFuture = ts.rangeAsync(TSRangeArgs.from(1670000000000L));
    RFuture<List<TSSample>> untilFuture = ts.rangeAsync(TSRangeArgs.to(1670000900000L));

    RFuture<List<TSSample>> filteredFuture = ts.rangeAsync(TSRangeArgs.all()
                    .filterByValue(20, 25)
                    .count(100));

    RFuture<List<TSSample>> pickedFuture = ts.rangeAsync(TSRangeArgs.all()
                    .filterByTimestamp(1670000000000L, 1670000060000L));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("sensor:temperature");

    Mono<List<TSSample>> samplesMono = ts.range(1670000000000L, 1670000900000L);

    Mono<List<TSSample>> newestFirstMono = ts.rangeReversed(TSRangeArgs.all());

    Mono<List<TSSample>> sinceMono = ts.range(TSRangeArgs.from(1670000000000L));
    Mono<List<TSSample>> untilMono = ts.range(TSRangeArgs.to(1670000900000L));

    Mono<List<TSSample>> filteredMono = ts.range(TSRangeArgs.all()
                    .filterByValue(20, 25)
                    .count(100));

    Mono<List<TSSample>> pickedMono = ts.range(TSRangeArgs.all()
                    .filterByTimestamp(1670000000000L, 1670000060000L));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("sensor:temperature");

    Single<List<TSSample>> samplesRx = ts.range(1670000000000L, 1670000900000L);

    Single<List<TSSample>> newestFirstRx = ts.rangeReversed(TSRangeArgs.all());

    Single<List<TSSample>> sinceRx = ts.range(TSRangeArgs.from(1670000000000L));
    Single<List<TSSample>> untilRx = ts.range(TSRangeArgs.to(1670000900000L));

    Single<List<TSSample>> filteredRx = ts.range(TSRangeArgs.all()
                    .filterByValue(20, 25)
                    .count(100));

    Single<List<TSSample>> pickedRx = ts.range(TSRangeArgs.all()
                    .filterByTimestamp(1670000000000L, 1670000060000L));
    ```

### Aggregation

`aggregation` divides the range into fixed-width buckets and reduces each one, which is how a long series is read at a resolution a chart can use. Several aggregators may be given at once, and each bucket then carries one value per aggregator, in the order they were passed.

By default a bucket starts at a multiple of its own duration. `alignStart`, `alignEnd` and `align` move that origin; `bucketTimestamp` chooses whether a bucket reports its start, middle or end; and `empty` reports buckets that held no samples, carrying `Double.NaN`.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    // hourly averages
    List<TSSample> hourly = ts.range(TSRangeArgs.all()
                    .aggregation(Duration.ofHours(1), TSAggregation.AVG));

    // several aggregators per bucket, in the order given
    List<TSSample> summary = ts.range(TSRangeArgs.all()
                    .aggregation(Duration.ofHours(1),
                                 TSAggregation.MIN, TSAggregation.MAX, TSAggregation.AVG));
    for (TSSample bucket : summary) {
        double[] values = bucket.getValues();
        double min = values[0];
        double max = values[1];
        double avg = values[2];
    }

    // buckets aligned to the range start, stamped at their middle, empty ones included
    List<TSSample> aligned = ts.range(TSRangeArgs.range(1670000000000L, 1670003600000L)
                    .aggregation(Duration.ofMinutes(5), TSAggregation.SUM)
                    .alignStart()
                    .bucketTimestamp(TSBucketTimestamp.MID)
                    .empty());
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    RFuture<List<TSSample>> hourlyFuture = ts.rangeAsync(TSRangeArgs.all()
                    .aggregation(Duration.ofHours(1), TSAggregation.AVG));

    RFuture<List<TSSample>> summaryFuture = ts.rangeAsync(TSRangeArgs.all()
                    .aggregation(Duration.ofHours(1),
                                 TSAggregation.MIN, TSAggregation.MAX, TSAggregation.AVG));

    RFuture<List<TSSample>> alignedFuture = ts.rangeAsync(
                    TSRangeArgs.range(1670000000000L, 1670003600000L)
                    .aggregation(Duration.ofMinutes(5), TSAggregation.SUM)
                    .alignStart()
                    .bucketTimestamp(TSBucketTimestamp.MID)
                    .empty());
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("sensor:temperature");

    Mono<List<TSSample>> hourlyMono = ts.range(TSRangeArgs.all()
                    .aggregation(Duration.ofHours(1), TSAggregation.AVG));

    Mono<List<TSSample>> summaryMono = ts.range(TSRangeArgs.all()
                    .aggregation(Duration.ofHours(1),
                                 TSAggregation.MIN, TSAggregation.MAX, TSAggregation.AVG));

    Mono<List<TSSample>> alignedMono = ts.range(
                    TSRangeArgs.range(1670000000000L, 1670003600000L)
                    .aggregation(Duration.ofMinutes(5), TSAggregation.SUM)
                    .alignStart()
                    .bucketTimestamp(TSBucketTimestamp.MID)
                    .empty());
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("sensor:temperature");

    Single<List<TSSample>> hourlyRx = ts.range(TSRangeArgs.all()
                    .aggregation(Duration.ofHours(1), TSAggregation.AVG));

    Single<List<TSSample>> summaryRx = ts.range(TSRangeArgs.all()
                    .aggregation(Duration.ofHours(1),
                                 TSAggregation.MIN, TSAggregation.MAX, TSAggregation.AVG));

    Single<List<TSSample>> alignedRx = ts.range(
                    TSRangeArgs.range(1670000000000L, 1670003600000L)
                    .aggregation(Duration.ofMinutes(5), TSAggregation.SUM)
                    .alignStart()
                    .bucketTimestamp(TSBucketTimestamp.MID)
                    .empty());
    ```

Available aggregators are `AVG`, `SUM`, `MIN`, `MAX`, `RANGE`, `COUNT`, `COUNT_NAN`, `COUNT_ALL`, `FIRST`, `LAST`, `STD_POPULATION`, `STD_SAMPLE`, `VAR_POPULATION`, `VAR_SAMPLE` and `TWA` (time-weighted average).

### Iteration

`iterator` walks the whole series a window at a time rather than loading it into memory, which is what makes it safe on a series too large to hold. `stream` is the same walk as a Java `Stream`. The Reactive and RxJava3 facades return a `Flux` and a `Flowable` that fetch lazily, so taking a few samples does not read the rest.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    // the object is Iterable<TSSample>
    for (TSSample sample : ts) {
        process(sample);
    }

    // a larger window means fewer round trips
    Iterator<TSSample> iterator = ts.iterator(500);

    double average = ts.stream()
                       .mapToDouble(TSSample::getValue)
                       .average()
                       .orElse(0);
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    AsyncIterator<TSSample> iterator = ts.iteratorAsync(500);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("sensor:temperature");

    Flux<TSSample> samples = ts.iterator(500);

    // only the first three windows' worth is fetched
    Flux<TSSample> firstThree = ts.iterator().take(3);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("sensor:temperature");

    Flowable<TSSample> samples = ts.iterator(500);

    Flowable<TSSample> firstThree = ts.iterator().take(3);
    ```

### Following a growing series

`read` is a cursor over a series that is still being written to. It takes a lower bound and no upper one, and can wait for samples that have not arrived yet - which is what a consumer tailing a live feed wants instead of polling `range`.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    // everything from a timestamp onwards
    List<TSSample> batch = ts.read(1670000000000L);

    // start at the oldest sample, the newest one, or the one after it
    List<TSSample> fromStart = ts.read(TSReadArgs.fromEarliest());
    List<TSSample> fromLast = ts.read(TSReadArgs.fromLast());

    // wait up to 5 seconds for at least one new sample, take at most 100
    List<TSSample> tail = ts.read(TSReadArgs.fromNext()
                    .block(Duration.ofSeconds(5), 1)
                    .maxCount(100));
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    RFuture<List<TSSample>> batchFuture = ts.readAsync(1670000000000L);

    RFuture<List<TSSample>> tailFuture = ts.readAsync(TSReadArgs.fromNext()
                    .block(Duration.ofSeconds(5), 1)
                    .maxCount(100));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("sensor:temperature");

    Mono<List<TSSample>> batchMono = ts.read(1670000000000L);

    Mono<List<TSSample>> tailMono = ts.read(TSReadArgs.fromNext()
                    .block(Duration.ofSeconds(5), 1)
                    .maxCount(100));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("sensor:temperature");

    Single<List<TSSample>> batchRx = ts.read(1670000000000L);

    Single<List<TSSample>> tailRx = ts.read(TSReadArgs.fromNext()
                    .block(Duration.ofSeconds(5), 1)
                    .maxCount(100));
    ```

A blocking `read` holds its connection for the whole timeout, so give it a timeout it is meant to reach rather than an open-ended wait.

### Compaction rules

A compaction rule downsamples one series into another as samples arrive, which is how a high-resolution feed is kept cheaply for a long time: raw samples expire on a short retention while their hourly averages live on in the destination.

The destination series must already exist, and only samples added *after* the rule is created reach it - existing ones are not back-filled.

=== "Sync"
    ```java
    RTimeSeriesNative raw = redisson.getTimeSeriesNative("sensor:temperature");
    RTimeSeriesNative hourly = redisson.getTimeSeriesNative("sensor:temperature:hourly");

    // keep raw samples for a day, hourly averages for a year
    raw.createIfAbsent(TSCreateArgs.defaults().retention(Duration.ofDays(1)));
    hourly.createIfAbsent(TSCreateArgs.defaults().retention(Duration.ofDays(365)));

    raw.createRule(TSRuleArgs.destination("sensor:temperature:hourly",
                                          TSAggregation.AVG, Duration.ofHours(1)));

    // shift the bucket origin
    raw.createRule(TSRuleArgs.destination("sensor:temperature:shifted",
                                          TSAggregation.AVG, Duration.ofHours(1))
                    .alignTimestamp(1800000L));

    raw.deleteRule("sensor:temperature:hourly");
    ```
=== "Async"
    ```java
    RTimeSeriesNative raw = redisson.getTimeSeriesNative("sensor:temperature");

    RFuture<Void> ruleFuture = raw.createRuleAsync(
                    TSRuleArgs.destination("sensor:temperature:hourly",
                                           TSAggregation.AVG, Duration.ofHours(1)));

    RFuture<Void> deleteFuture = raw.deleteRuleAsync("sensor:temperature:hourly");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive raw = redisson.getTimeSeriesNative("sensor:temperature");

    Mono<Void> ruleMono = raw.createRule(
                    TSRuleArgs.destination("sensor:temperature:hourly",
                                           TSAggregation.AVG, Duration.ofHours(1)));

    Mono<Void> deleteMono = raw.deleteRule("sensor:temperature:hourly");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx raw = redisson.getTimeSeriesNative("sensor:temperature");

    Completable ruleRx = raw.createRule(
                    TSRuleArgs.destination("sensor:temperature:hourly",
                                           TSAggregation.AVG, Duration.ofHours(1)));

    Completable deleteRx = raw.deleteRule("sensor:temperature:hourly");
    ```

### Series information

`getInfo` reports a series' configuration and statistics; `getDebugInfo` adds per-chunk detail, which is what to look at when memory use is being investigated.

=== "Sync"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    TSInfo info = ts.getInfo();

    long totalSamples = info.getTotalSamples();
    long memoryUsage = info.getMemoryUsage();
    Duration retention = info.getRetentionTime();
    TSEncoding encoding = info.getChunkType();
    TSDuplicatePolicy policy = info.getDuplicatePolicy();
    Map<String, String> labels = info.getLabels();

    // set on a compaction destination: the series feeding it
    String sourceKey = info.getSourceKey();

    for (TSRule rule : info.getRules()) {
        String destination = rule.getDestinationKey();
        TSAggregation aggregation = rule.getAggregation();
        Duration bucket = rule.getBucketDuration();
    }

    // per-chunk detail
    for (TSChunkInfo chunk : ts.getDebugInfo().getChunks()) {
        long start = chunk.getStartTimestamp();
        long samples = chunk.getSamples();
        double bytesPerSample = chunk.getBytesPerSample();
    }
    ```
=== "Async"
    ```java
    RTimeSeriesNative ts = redisson.getTimeSeriesNative("sensor:temperature");

    RFuture<TSInfo> infoFuture = ts.getInfoAsync();
    RFuture<TSInfo> debugFuture = ts.getDebugInfoAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativeReactive ts = redisson.getTimeSeriesNative("sensor:temperature");

    Mono<TSInfo> infoMono = ts.getInfo();
    Mono<TSInfo> debugMono = ts.getDebugInfo();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativeRx ts = redisson.getTimeSeriesNative("sensor:temperature");

    Single<TSInfo> infoRx = ts.getInfo();
    Single<TSInfo> debugRx = ts.getDebugInfo();
    ```

### Working with many series

`RTimeSeriesNatives` queries across series rather than within one. Series are selected by *label filter*:

| Filter | Matches |
|---|---|
| `area=warehouse` | series whose `area` label is `warehouse` |
| `area!=warehouse` | series whose `area` label is anything else |
| `area=(warehouse,office)` | series whose `area` label is one of the listed values |
| `area!=(warehouse,office)` | series whose `area` label is none of them |
| `area!=` | series that have an `area` label, whatever its value |
| `area=` | series that have no `area` label |

At least one filter must be a positive match - a query built only from `!=` and `=` forms is rejected with `ERR TSDB: please provide at least one matcher`.

Results come back as a map keyed by series name, so a caller reads one series with a lookup rather than a scan.

=== "Sync"
    ```java
    RTimeSeriesNatives multi = redisson.getTimeSeriesNatives();

    // add to several series in one call
    Map<String, Map<Long, Double>> samples = new LinkedHashMap<>();
    samples.put("sensor:1", Map.of(1670000000000L, 21.5));
    samples.put("sensor:2", Map.of(1670000000000L, 23.1));
    Map<String, List<Long>> timestamps = multi.addAll(samples);

    // the latest sample of every matching series, with its labels
    Map<String, TSSeriesSample> latest =
            multi.getAll(TSMultiGetArgs.filter("area=warehouse").withLabels());
    TSSample sample = latest.get("sensor:1").getSample();

    // ranges across matching series
    Map<String, TSSeriesSamples> ranges =
            multi.range(TSMultiRangeArgs.filter("area=warehouse")
                    .range(1670000000000L, 1670003600000L)
                    .aggregation(Duration.ofMinutes(5), TSAggregation.AVG)
                    .withLabels());
    List<TSSample> perSeries = ranges.get("sensor:1").getSamples();

    // one entry per label value rather than per series,
    // keyed "area=warehouse" rather than by series name
    Map<String, TSSeriesSamples> byArea =
            multi.range(TSMultiRangeArgs.filter("area=(warehouse,office)")
                    .groupBy("area", TSReducer.AVG));

    // named keys aligned into one timeline: one row per timestamp,
    // one value per key, NaN where a series had nothing
    List<TSSample> aligned =
            multi.groupedRange(TSGroupedRangeArgs.keys("sensor:1", "sensor:2").all());

    // which series and labels exist
    Set<String> keys = multi.queryIndex("area=warehouse");
    Set<String> names = multi.labelNames("area=warehouse");
    Set<String> values = multi.labelValues("area");
    ```
=== "Async"
    ```java
    RTimeSeriesNativesAsync multi = redisson.getTimeSeriesNatives();

    Map<String, Map<Long, Double>> samples = new LinkedHashMap<>();
    samples.put("sensor:1", Map.of(1670000000000L, 21.5));
    samples.put("sensor:2", Map.of(1670000000000L, 23.1));
    RFuture<Map<String, List<Long>>> addFuture = multi.addAllAsync(samples);

    RFuture<Map<String, TSSeriesSample>> latestFuture =
            multi.getAllAsync(TSMultiGetArgs.filter("area=warehouse").withLabels());

    RFuture<Map<String, TSSeriesSamples>> rangesFuture =
            multi.rangeAsync(TSMultiRangeArgs.filter("area=warehouse")
                    .aggregation(Duration.ofMinutes(5), TSAggregation.AVG));

    RFuture<List<TSSample>> alignedFuture =
            multi.groupedRangeAsync(TSGroupedRangeArgs.keys("sensor:1", "sensor:2").all());

    RFuture<Set<String>> keysFuture = multi.queryIndexAsync("area=warehouse");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesNativesReactive multi = redisson.getTimeSeriesNatives();

    Map<String, Map<Long, Double>> samples = new LinkedHashMap<>();
    samples.put("sensor:1", Map.of(1670000000000L, 21.5));
    samples.put("sensor:2", Map.of(1670000000000L, 23.1));
    Mono<Map<String, List<Long>>> addMono = multi.addAll(samples);

    Mono<Map<String, TSSeriesSample>> latestMono =
            multi.getAll(TSMultiGetArgs.filter("area=warehouse").withLabels());

    Mono<Map<String, TSSeriesSamples>> rangesMono =
            multi.range(TSMultiRangeArgs.filter("area=warehouse")
                    .aggregation(Duration.ofMinutes(5), TSAggregation.AVG));

    Mono<List<TSSample>> alignedMono =
            multi.groupedRange(TSGroupedRangeArgs.keys("sensor:1", "sensor:2").all());

    Mono<Set<String>> keysMono = multi.queryIndex("area=warehouse");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesNativesRx multi = redisson.getTimeSeriesNatives();

    Map<String, Map<Long, Double>> samples = new LinkedHashMap<>();
    samples.put("sensor:1", Map.of(1670000000000L, 21.5));
    samples.put("sensor:2", Map.of(1670000000000L, 23.1));
    Single<Map<String, List<Long>>> addRx = multi.addAll(samples);

    Single<Map<String, TSSeriesSample>> latestRx =
            multi.getAll(TSMultiGetArgs.filter("area=warehouse").withLabels());

    Single<Map<String, TSSeriesSamples>> rangesRx =
            multi.range(TSMultiRangeArgs.filter("area=warehouse")
                    .aggregation(Duration.ofMinutes(5), TSAggregation.AVG));

    Single<List<TSSample>> alignedRx =
            multi.groupedRange(TSGroupedRangeArgs.keys("sensor:1", "sensor:2").all());

    Single<Set<String>> keysRx = multi.queryIndex("area=warehouse");
    ```

`withLabels` returns every label of each matching series; `selectedLabels` returns only the named ones, and the two are mutually exclusive. `groupBy` collapses the result to one entry per label value, reduced by a `TSReducer`. Those entries are keyed `<label>=<value>` rather than by series name, and their labels carry `__reducer__` and `__source__` describing how each was built.

In cluster mode the filter-based methods run on a single node, because the module keeps a cluster-wide index of series. `groupedRange` is the exception: it names its keys and the server aligns them into one timeline, which it cannot do across nodes, so those keys must share a hash slot.

### Use Cases

**Infrastructure and application metrics.** Write CPU, memory and latency readings with `addCurrent`, label each series by host and service, and read them back downsampled with `aggregation` at whatever resolution the dashboard needs. A compaction rule keeps a year of hourly averages while raw samples expire after a day.

**IoT and sensor telemetry.** One series per device, labelled by site and sensor type. `RTimeSeriesNatives.range` then answers "every temperature sensor in the warehouse over the last hour" in one call, and `groupedRange` aligns several sensors into a single timeline for comparison.

**Rate and counter tracking.** `incrementBy` keeps a running count without a read-modify-write, so page views, API calls or error counts accumulate into the latest sample and are read back as per-minute sums.

**Financial ticks.** `duplicatePolicy(TSDuplicatePolicy.LAST)` keeps the newest price for a timestamp, and `TSAggregation.FIRST`, `MAX`, `MIN` and `LAST` over a bucket duration produce OHLC candles directly from the server.

**Live tailing.** A consumer follows a series with `read(TSReadArgs.fromNext().block(...))`, waking when samples arrive rather than polling.
