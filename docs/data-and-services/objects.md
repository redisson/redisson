## Object holder
Java implementation of Redis or Valkey based [RBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucket.html) object is a holder for any type of object. Size is limited to 512Mb.

Code example:
```java
RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

bucket.set(new AnyObject(1));
AnyObject obj = bucket.get();

bucket.trySet(new AnyObject(3));
bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
bucket.getAndSet(new AnyObject(6));
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucketAsync.html) interface** usage:
```java
RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

RFuture<Void> future = bucket.setAsync(new AnyObject(1));
RFuture<AnyObject> objfuture = bucket.getAsync();

RFuture<Boolean> tsFuture = bucket.trySetAsync(new AnyObject(3));
RFuture<Boolean> csFuture = bucket.compareAndSetAsync(new AnyObject(4), new AnyObject(5));
RFuture<AnyObject> gsFuture = bucket.getAndSetAsync(new AnyObject(6));
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucketReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RBucketReactive<AnyObject> bucket = redisson.getBucket("anyObject");

Mono<Void> mono = bucket.set(new AnyObject(1));
Mono<AnyObject> objMono = bucket.get();

Mono<Boolean> tsMono = bucket.trySet(new AnyObject(3));
Mono<Boolean> csMono = bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
Mono<AnyObject> gsMono = bucket.getAndSet(new AnyObject(6));
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucketRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RBucketRx<AnyObject> bucket = redisson.getBucket("anyObject");

Completable rx = bucket.set(new AnyObject(1));
Maybe<AnyObject> objRx = bucket.get();

Single<Boolean> tsRx = bucket.trySet(new AnyObject(3));
Single<Boolean> csRx = bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
Maybe<AnyObject> gsRx = bucket.getAndSet(new AnyObject(6));
```
<br/>
<br/>

Use [RBuckets](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBuckets.html) interface to execute operations over multiple [RBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucket.html) objects:

Code example:
```java
RBuckets buckets = redisson.getBuckets();

// get all bucket values
Map<String, V> loadedBuckets = buckets.get("myBucket1", "myBucket2", "myBucket3");

Map<String, Object> map = new HashMap<>();
map.put("myBucket1", new MyObject());
map.put("myBucket2", new MyObject());

// sets all or nothing if some bucket is already exists
buckets.trySet(map);
// store all at once
buckets.set(map);
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucketsAsync.html) interface** usage:
```java
RBuckets buckets = redisson.getBuckets();

// get all bucket values
RFuture<Map<String, V>> bucketsFuture = buckets.getAsync("myBucket1", "myBucket2", "myBucket3");

Map<String, Object> map = new HashMap<>();
map.put("myBucket1", new MyObject());
map.put("myBucket2", new MyObject());

// sets all or nothing if some bucket is already exists
RFuture<Boolean> tsFuture = buckets.trySetAsync(map);
// store all at once
RFuture<Void> sFuture = buckets.setAsync(map);
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucketsReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RBucketsReactive buckets = redisson.getBuckets();

// get all bucket values
Mono<Map<String, V>> bucketsMono = buckets.getAsync("myBucket1", "myBucket2", "myBucket3");

Map<String, Object> map = new HashMap<>();
map.put("myBucket1", new MyObject());
map.put("myBucket2", new MyObject());

// sets all or nothing if some bucket is already exists
Mono<Boolean> tsMono = buckets.trySet(map);
// store all at once
Mono<Void> sMono = buckets.set(map);
```

Code example of **[RxJava](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucketsRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RBucketsRx buckets = redisson.getBuckets();

// get all bucket values
Single<Map<String, V>> bucketsRx = buckets.get("myBucket1", "myBucket2", "myBucket3");

Map<String, Object> map = new HashMap<>();
map.put("myBucket1", new MyObject());
map.put("myBucket2", new MyObject());

// sets all or nothing if some bucket is already exists
Single<Boolean> tsRx = buckets.trySet(map);
// store all at once
Completable sRx = buckets.set(map);
```

### Listeners

Redisson allows to bind listeners per `RBucket` object.

|Listener class name|Event description |
|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Data created/updated after read operation|
|org.redisson.api.listener.SetObjectListener|Data created/updated|
|org.redisson.api.ExpiredObjectListener|`RBucket` object expired|
|org.redisson.api.DeletedObjectListener|`RBucket` object deleted|

Usage example:

```java
RBucket<String> set = redisson.getBucket("anyObject");

int listenerId = set.addListener(new SetObjectListener() {
     @Override
     public void onSet(String name) {
        // ...
     }
});


int listenerId = set.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

set.removeListener(listenerId);
```

## Binary stream holder
Java implementation of Redis or Valkey based [RBinaryStream](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RBinaryStream.html) object holds sequence of bytes. It extends [RBucket](#object-holder) interface and size is limited to 512Mb.

Code example:
```java
RBinaryStream stream = redisson.getBinaryStream("anyStream");

byte[] content = ...
stream.set(content);
stream.getAndSet(content);
stream.trySet(content);
stream.compareAndSet(oldContent, content);
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucketAsync.html) interface** usage:
```java
RBinaryStream stream = redisson.getBinaryStream("anyStream");

byte[] content = ...
RFuture<Void> future = stream.set(content);
RFuture<byte[]> future = stream.getAndSet(content);
RFuture<Boolean> future = stream.trySet(content);
RFuture<Boolean> future = stream.compareAndSet(oldContent, content);
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBinaryStreamReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RBinaryStreamReactive stream = redisson.getBinaryStream("anyStream");

ByteBuffer content = ...
Mono<Void> mono = stream.set(content);
Mono<byte[]> mono = stream.getAndSet(content);
Mono<Boolean> mono = stream.trySet(content);
Mono<Boolean> mono = stream.compareAndSet(oldContent, content);

Mono<Integer> mono = stream.write(content);
stream.position(0);
Mono<Integer> mono = stream.read(b);
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBinaryStreamRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RBinaryStreamRx stream = redisson.getBinaryStream("anyStream");

ByteBuffer content = ...
Completable rx = stream.set(content);
Maybe<byte[]> rx = stream.getAndSet(content);
Single<Boolean> rx = stream.trySet(content);
Single<Boolean> rx = stream.compareAndSet(oldContent, content);

Single<Integer> rx = stream.write(content);
stream.position(0);
Single<Integer> rx = stream.read(b);
```

Code example of [java.io.InputStream](https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html) and [java.io.OutputStream](https://docs.oracle.com/javase/8/docs/api/java/io/OutputStream.html) interfaces usage:
```java
RBinaryStream stream = redisson.getBinaryStream("anyStream");

InputStream is = stream.getInputStream();
byte[] readBuffer = ...
is.read(readBuffer);

OutputStream os = stream.getOuputStream();
byte[] contentToWrite = ...
os.write(contentToWrite);
```

Code example of [java.nio.channels.SeekableByteChannel](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/SeekableByteChannel.html) interface usage:
```java
RBinaryStream stream = redisson.getBinaryStream("anyStream");

SeekableByteChannel sbc = stream.getChannel();
ByteBuffer readBuffer = ...
sbc.read(readBuffer);

sbc.position(0);

ByteBuffer contentToWrite = ...
sbc.write(contentToWrite);

sbc.truncate(234);
```

Code example of [java.nio.channels.AsynchronousByteChannel](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/AsynchronousByteChannel.html) interface usage:
```java
RBinaryStream stream = redisson.getBinaryStream("anyStream");

AsynchronousByteChannel sbc = stream.getAsynchronousChannel();
ByteBuffer readBuffer = ...
sbc.read(readBuffer);

ByteBuffer contentToWrite = ...
sbc.write(contentToWrite);
```

### Listeners

Redisson allows to bind listeners per `RBinaryStream` object.

|Listener class name|Event description |
|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Data created/updated after read operation|
|org.redisson.api.listener.SetObjectListener|Data created/updated|
|org.redisson.api.ExpiredObjectListener|`RBinaryStream` object expired|
|org.redisson.api.DeletedObjectListener|`RBinaryStream` object deleted|

Usage example:

```java
RBinaryStream stream = redisson.getBinaryStream("anyObject");

int listenerId = set.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

set.removeListener(listenerId);
```

## JSON object holder
Java implementation of [RJsonBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RJsonBucket.html) object stores data in JSON format using `JSON.*` commands. JSON data encoding/decoding handled by `JsonCodec` which is a required parameter. Available implementation is `org.redisson.codec.JacksonCodec` which is thread-safe. 

Use [JSON Store](collections.md/#json-store) for key-value implementation and local cache support.

Code example:
```java
RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

bucket.set(new AnyObject(1));
AnyObject obj = bucket.get();

bucket.trySet(new AnyObject(3));
bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
bucket.getAndSet(new AnyObject(6));

List<String> values = bucket.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "values");
long aa = bucket.arrayAppend("$.obj.values", "t3", "t4");
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RJsonBucketAsync.html) interface** usage:
```java
RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

RFuture<Void> future = bucket.setAsync(new AnyObject(1));
RFuture<AnyObject> objfuture = bucket.getAsync();

RFuture<Boolean> tsFuture = bucket.trySetAsync(new AnyObject(3));
RFuture<Boolean> csFuture = bucket.compareAndSetAsync(new AnyObject(4), new AnyObject(5));
RFuture<AnyObject> gsFuture = bucket.getAndSetAsync(new AnyObject(6));

RFutue<List<String>> gFuture = bucket.getAsync(new JacksonCodec<>(new TypeReference<List<String>>() {}), "obj.values");
RFutue<Long> aaFuture = bucket.arrayAppendAsync("$.obj.values", "t3", "t4");
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RJsonBucketReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RJsonBucketReactive<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

Mono<Void> mono = bucket.set(new AnyObject(1));
Mono<AnyObject> objMono = bucket.get();

Mono<Boolean> tsMono = bucket.trySet(new AnyObject(3));
Mono<Boolean> csMono = bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
Mono<AnyObject> gsMono = bucket.getAndSet(new AnyObject(6));

Mono<List<String>> vsMono = bucket.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "values");
Mono<Long> aaMono = bucket.arrayAppend("$.obj.values", "t3", "t4");
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RJsonBucketRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RJsonBucketRx<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

Completable rx = bucket.set(new AnyObject(1));
Maybe<AnyObject> objRx = bucket.get();

Single<Boolean> tsRx = bucket.trySet(new AnyObject(3));
Single<Boolean> csRx = bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
Maybe<AnyObject> gsRx = bucket.getAndSet(new AnyObject(6));

Single<List<String>> valuesRx = bucket.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "values");
Single<Long> aaRx = bucket.arrayAppend("$.obj.values", "t3", "t4");
```

## Geospatial holder
Java implementation of Redis or Valkey based [RGeo](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RGeo.html) object is a holder for geospatial items.

Code example:
```java
RGeo<String> geo = redisson.getGeo("test");

geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), 
        new GeoEntry(15.087269, 37.502669, "Catania"));

Double distance = geo.dist("Palermo", "Catania", GeoUnit.METERS);
Map<String, GeoPosition> positions = geo.pos("test2", "Palermo", "test3", "Catania", "test1");

List<String> cities = geo.search(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
Map<String, GeoPosition> citiesWithPositions = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RGeoAsync.html) interface** usage:
```java
RGeo<String> geo = redisson.getGeo("test");

RFuture<Long> addFuture = geo.addAsync(new GeoEntry(13.361389, 38.115556, "Palermo"), 
        new GeoEntry(15.087269, 37.502669, "Catania"));

RFuture<Double> distanceFuture = geo.distAsync("Palermo", "Catania", GeoUnit.METERS);
RFuture<Map<String, GeoPosition>> positionsFuture = geo.posAsync("test2", "Palermo", "test3", "Catania", "test1");

RFuture<List<String>> citiesFuture = geo.searchAsync(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
RFuture<Map<String, GeoPosition>> citiesWithPositions = geo.searchWithPositionAsync(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RGeoReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RGeoReactive<String> bucket = redisson.getGeo("test");

Mono<Long> addFuture = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), 
        new GeoEntry(15.087269, 37.502669, "Catania"));

Mono<Double> distanceFuture = geo.dist("Palermo", "Catania", GeoUnit.METERS);
Mono<Map<String, GeoPosition>> positionsFuture = geo.pos("test2", "Palermo", "test3", "Catania", "test1");

Mono<List<String>> citiesFuture = geo.search(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
Mono<Map<String, GeoPosition>> citiesWithPositions = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RGeoRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RGeoRx<String> bucket = redisson.getGeo("test");

Single<Long> addFuture = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), 
        new GeoEntry(15.087269, 37.502669, "Catania"));

Single<Double> distanceFuture = geo.dist("Palermo", "Catania", GeoUnit.METERS);
Single<Map<String, GeoPosition>> positionsFuture = geo.pos("test2", "Palermo", "test3", "Catania", "test1");

Single<List<String>> citiesFuture = geo.search(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
Single<Map<String, GeoPosition>> citiesWithPositions = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
```

## BitSet
Java implementation of Redis or Valkey based [RBitSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitSet.html) object provides API similar to [java.util.BitSet](https://docs.oracle.com/javase/8/docs/api/java/util/BitSet.html). It represents vector of bits that grows as needed. Size limited to `4 294 967 295` bits. 

Code example:
```java
RBitSet set = redisson.getBitSet("simpleBitset");

set.set(0, true);
set.set(1812, false);

set.clear(0);

set.and("anotherBitset");
set.xor("anotherBitset");
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitSetAsync.html) interface** usage:
```java
RBitSetAsync set = redisson.getBitSet("simpleBitset");

RFuture<Boolean> setFuture = set.setAsync(0, true);
RFuture<Boolean> setFuture = set.setAsync(1812, false);

RFuture<Void> clearFuture = set.clearAsync(0);

RFuture<Void> andFuture = set.andAsync("anotherBitset);
RFuture<Void> xorFuture = set.xorAsync("anotherBitset");
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitSetReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RBitSetReactive stream = redisson.getBitSet("simpleBitset");

Mono<Boolean> setMono = set.set(0, true);
Mono<Boolean> setMono = set.set(1812, false);

Mono<Void> clearMono = set.clear(0);

Mono<Void> andMono = set.and("anotherBitset);
Mono<Void> xorMono = set.xor("anotherBitset");
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitSetRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RBitSetRx stream = redisson.getBitSet("simpleBitset");

Single<Boolean> setRx = set.set(0, true);
Single<Boolean> setRx = set.set(1812, false);

Completable clearRx = set.clear(0);

Completable andRx = set.and("anotherBitset);
Completable xorRx = set.xor("anotherBitset");
```

### Data partitioning

Although 'RBitSet' object is cluster compatible its content isn't scaled across multiple master nodes. BitSet data partitioning available only in cluster mode and implemented by separate `RClusteredBitSet` object. It uses distributed implementation of roaring bitmap structure. Size is limited by whole Cluster memory.  More details about partitioning [here](data-partitioning.md).

Below is the list of all available BitSet implementations:  

|RedissonClient <br/> method name | Data partitioning <br/> support | Ultra-fast read/write |
| ------------- | :----------:| :----------:|
|getBitSet()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ |
|getBitSet()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ✔️ |
|getClusteredBitSet()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ |

Code example:
```java
RClusteredBitSet set = redisson.getClusteredBitSet("simpleBitset");
set.set(0, true);
set.set(1812, false);
set.clear(0);
set.addAsync("e");
set.xor("anotherBitset");
```

## Bloom filter
Redis or Valkey based distributed [RBloomFilter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBloomFilter.html) bloom filter for Java. Number of contained bits is limited to `2^32` with [data partitioning](data-partitioning.md) to `2^63`

Must be initialized with capacity size by `tryInit(expectedInsertions, falseProbability)` method before usage.

```java
RBloomFilter<SomeObject> bloomFilter = redisson.getBloomFilter("sample");
// initialize bloom filter with 
// expectedInsertions = 55000000
// falseProbability = 0.03
bloomFilter.tryInit(55000000L, 0.03);

bloomFilter.add(new SomeObject("field1Value", "field2Value"));
bloomFilter.add(new SomeObject("field5Value", "field8Value"));

bloomFilter.contains(new SomeObject("field1Value", "field8Value"));
bloomFilter.count();
```

### Data partitioning

_This feature available only in [Redisson PRO](https://redisson.pro) edition._

Although 'RBloomFilter' object is cluster compatible its content isn't scaled across multiple master nodes. Bloom Filter data partitioning support available only in cluster mode and implemented by separate `RClusteredBloomFilter` object. This implementation uses more efficient distributed memory allocation algorithm. It allows to "shrink" memory space consumed by unused bits across all Redis or Valkey nodes. State of each instance is partitioned across all nodes in Redis or Valkey cluster. Number of contained bits is limited to `2^63`. More details about partitioning [here](data-partitioning.md).

Below is the list of all available BloomFilter implementations:  

|RedissonClient <br/> method name | Data partitioning <br/> support | Ultra-fast read/write | Bits amount limit |
| ------------- | :----------:| :----------:| :----------:|
|getBloomFilter()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | 2^32 |
|getBloomFilter()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ✔️ | 2^32 |
|getClusteredBloomFilter()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | **2^63** |

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


## HyperLogLog
Redis or Valkey based distributed [RHyperLogLog](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RHyperLogLog.html) object for Java. Probabilistic data structure that lets you maintain counts of millions of items with extreme space efficiency. 

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RHyperLogLogAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RHyperLogLogReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RHyperLogLogRx.html) interfaces.

```java
RHyperLogLog<Integer> log = redisson.getHyperLogLog("log");
log.add(1);
log.add(2);
log.add(3);

log.count();
```

## RateLimiter
Redis or Valkey based distributed [RateLimiter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRateLimiter.html) object for Java restricts the total rate of calls either from all threads regardless of Redisson instance or from all threads working with the same Redisson instance. Doesn't guarantee fairness. 

Code example:
```java
RRateLimiter limiter = redisson.getRateLimiter("myLimiter");
// Initialization required only once.
// 5 permits per 2 seconds
limiter.trySetRate(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);

// acquire 3 permits or block until they became available       
limiter.acquire(3);
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRateLimiterAsync.html) interface** usage:
```java
RRateLimiter limiter = redisson.getRateLimiter("myLimiter");
// Initialization required only once.
// 5 permits per 2 seconds
RFuture<Boolean> setRateFuture = limiter.trySetRate(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);

// acquire 3 permits or block until they became available       
RFuture<Void> aquireFuture = limiter.acquire(3);
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRateLimiterReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RRateLimiterReactive limiter = redisson.getRateLimiter("myLimiter");
// Initialization required only once.
// 5 permits per 2 seconds
Mono<Boolean> setRateMono = limiter.trySetRate(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);

// acquire 3 permits or block until they became available       
Mono<Void> aquireMono = limiter.acquire(3);
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRateLimiterRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RRateLimiterRx limiter = redisson.getRateLimiter("anyObject");

// Initialization required only once.
// 5 permits per 2 seconds
Single<Boolean> setRateRx = limiter.trySetRate(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);

// acquire 3 permits or block until they became available       
Completable aquireRx = limiter.acquire(3);
```
