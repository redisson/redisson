## Object holder
Java implementation of Valkey or Redis based [RBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucket.html) object is a holder for any type of object. Size is limited to 512Mb. This object is thread-safe.

Code examples:
=== "Sync"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    bucket.set(new AnyObject(1));
    AnyObject obj = bucket.get();

    bucket.trySet(new AnyObject(3));
    bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
    bucket.getAndSet(new AnyObject(6));
    ```
=== "Async"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    RFuture<Void> future = bucket.setAsync(new AnyObject(1));
    RFuture<AnyObject> objfuture = bucket.getAsync();

    RFuture<Boolean> tsFuture = bucket.trySetAsync(new AnyObject(3));
    RFuture<Boolean> csFuture = bucket.compareAndSetAsync(new AnyObject(4), new AnyObject(5));
    RFuture<AnyObject> gsFuture = bucket.getAndSetAsync(new AnyObject(6));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<AnyObject> bucket = redisson.getBucket("anyObject");

    Mono<Void> mono = bucket.set(new AnyObject(1));
    Mono<AnyObject> objMono = bucket.get();

    Mono<Boolean> tsMono = bucket.trySet(new AnyObject(3));
    Mono<Boolean> csMono = bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
    Mono<AnyObject> gsMono = bucket.getAndSet(new AnyObject(6));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<AnyObject> bucket = redisson.getBucket("anyObject");

    Completable rx = bucket.set(new AnyObject(1));
    Maybe<AnyObject> objRx = bucket.get();

    Single<Boolean> tsRx = bucket.trySet(new AnyObject(3));
    Single<Boolean> csRx = bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
    Maybe<AnyObject> gsRx = bucket.getAndSet(new AnyObject(6));
    ```

Use [RBuckets](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBuckets.html) interface to execute operations over multiple [RBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucket.html) objects:

Code examples:

=== "Sync"
    ```
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
=== "Async"
    ```
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
=== "Reactive"
    ```
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
=== "RxJava3"
    ```
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

Redisson allows binding listeners per `RBucket` object. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

|Listener class name|Event description |Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Data created/updated after read operation| - |
|org.redisson.api.listener.SetObjectListener|Data created/updated|E$|
|org.redisson.api.ExpiredObjectListener|`RBucket` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RBucket` object deleted|Ex|

Code examples:

=== "Sync"
    ```
    RBucket<String> bucket = redisson.getBucket("anyObject");

    int setListenerId = bucket.addListener(new SetObjectListener() {
        @Override
        public void onSet(String name) {
            // handle set event
        }
    });

    int deletedListenerId = bucket.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            // handle delete event
        }
    });

    // ...

    bucket.removeListener(setListenerId);
    bucket.removeListener(deletedListenerId);
    ```
=== "Async"
    ```
    RBucketAsync<String> bucket = redisson.getBucket("anyObject");

    RFuture<Integer> setListenerFuture = bucket.addListenerAsync(new SetObjectListener() {
        @Override
        public void onSet(String name) {
            // handle set event
        }
    });

    RFuture<Integer> deletedListenerFuture = bucket.addListenerAsync(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            // handle delete event
        }
    });

    // ...

    bucket.removeListenerAsync(setListenerFuture.get());
    bucket.removeListenerAsync(deletedListenerFuture.get());
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<String> bucket = redisson.getBucket("anyObject");

    Mono<Integer> setListenerMono = bucket.addListener(new SetObjectListener() {
        @Override
        public void onSet(String name) {
            // handle set event
        }
    });

    Mono<Integer> deletedListenerMono = bucket.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            // handle delete event
        }
    });

    // ...

    setListenerMono.flatMap(bucket::removeListener).subscribe();
    deletedListenerMono.flatMap(bucket::removeListener).subscribe();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<String> bucket = redisson.getBucket("anyObject");

    Single<Integer> setListenerRx = bucket.addListener(new SetObjectListener() {
        @Override
        public void onSet(String name) {
            // handle set event
        }
    });

    Single<Integer> deletedListenerRx = bucket.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            // handle delete event
        }
    });

    // ...

    setListenerRx.flatMapCompletable(bucket::removeListener).subscribe();
    deletedListenerRx.flatMapCompletable(bucket::removeListener).subscribe();
    ```

## Binary stream holder
Java implementation of Valkey or Redis based [RBinaryStream](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RBinaryStream.html) object holds sequence of bytes. It extends [RBucket](#object-holder) interface and size is limited to 512Mb. This object is thread-safe.

Code examples:

=== "Sync"
    ```
    RBinaryStream stream = redisson.getBinaryStream("anyStream");

    byte[] content = ...
    stream.set(content);
    stream.getAndSet(content);
    stream.trySet(content);
    stream.compareAndSet(oldContent, content);
    ```
=== "Async"
    ```
    RBinaryStream stream = redisson.getBinaryStream("anyStream");

    byte[] content = ...
    RFuture<Void> future = stream.set(content);
    RFuture<byte[]> future = stream.getAndSet(content);
    RFuture<Boolean> future = stream.trySet(content);
    RFuture<Boolean> future = stream.compareAndSet(oldContent, content);
    ```
=== "Reactive"
    ```
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
=== "RxJava3"
    ```
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

Redisson allows binding listeners per `RBinaryStream` object. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

|Listener class name|Event description |Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Data created/updated after read operation|-|
|org.redisson.api.listener.SetObjectListener|Data created/updated|E$|
|org.redisson.api.ExpiredObjectListener|`RBinaryStream` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RBinaryStream` object deleted|Ex|

Code examples:

=== "Sync"
    ```
    RBinaryStream stream = redisson.getBinaryStream("anyStream");

    int setListenerId = stream.addListener(new SetObjectListener() {
        @Override
        public void onSet(String name) {
            // handle set event
        }
    });

    int deletedListenerId = stream.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            // handle delete event
        }
    });

    // ...

    stream.removeListener(setListenerId);
    stream.removeListener(deletedListenerId);
    ```
=== "Async"
    ```
    RBinaryStreamAsync stream = redisson.getBinaryStream("anyStream");

    RFuture<Integer> setListenerFuture = stream.addListenerAsync(new SetObjectListener() {
        @Override
        public void onSet(String name) {
            // handle set event
        }
    });

    RFuture<Integer> deletedListenerFuture = stream.addListenerAsync(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            // handle delete event
        }
    });

    // ...

    stream.removeListenerAsync(setListenerFuture.get());
    stream.removeListenerAsync(deletedListenerFuture.get());
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBinaryStreamReactive stream = redisson.getBinaryStream("anyStream");

    Mono<Integer> setListenerMono = stream.addListener(new SetObjectListener() {
        @Override
        public void onSet(String name) {
            // handle set event
        }
    });

    Mono<Integer> deletedListenerMono = stream.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            // handle delete event
        }
    });

    // ...

    setListenerMono.flatMap(stream::removeListener).subscribe();
    deletedListenerMono.flatMap(stream::removeListener).subscribe();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBinaryStreamRx stream = redisson.getBinaryStream("anyStream");

    Single<Integer> setListenerRx = stream.addListener(new SetObjectListener() {
        @Override
        public void onSet(String name) {
            // handle set event
        }
    });

    Single<Integer> deletedListenerRx = stream.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            // handle delete event
        }
    });

    // ...

    setListenerRx.flatMapCompletable(stream::removeListener).subscribe();
    deletedListenerRx.flatMapCompletable(stream::removeListener).subscribe();
    ```

## JSON object holder
Java implementation of Valkey or Redis based [JsonBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RJsonBucket.html) object stores data in JSON format using `JSON.*` commands. JSON data encoding/decoding handled by `JsonCodec` which is a required parameter. Available implementation is `org.redisson.codec.JacksonCodec`. This object is thread-safe.

Use [JSON Store](collections.md/#json-store) for key-value implementation and local cache support.

Code examples:

=== "Sync"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    bucket.set(new AnyObject(1));
    AnyObject obj = bucket.get();

    bucket.trySet(new AnyObject(3));
    bucket.compareAndSet(new AnyObject(4), new AnyObject(5));
    bucket.getAndSet(new AnyObject(6));

    List<String> values = bucket.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "values");
    long aa = bucket.arrayAppend("$.obj.values", "t3", "t4");
    ```
=== "Async"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    RFuture<Void> future = bucket.setAsync(new AnyObject(1));
    RFuture<AnyObject> objfuture = bucket.getAsync();

    RFuture<Boolean> tsFuture = bucket.trySetAsync(new AnyObject(3));
    RFuture<Boolean> csFuture = bucket.compareAndSetAsync(new AnyObject(4), new AnyObject(5));
    RFuture<AnyObject> gsFuture = bucket.getAndSetAsync(new AnyObject(6));

    RFuture<List<String>> gFuture = bucket.getAsync(new JacksonCodec<>(new TypeReference<List<String>>() {}), "obj.values");
    RFuture<Long> aaFuture = bucket.arrayAppendAsync("$.obj.values", "t3", "t4");
    ```
=== "Reactive"
    ```
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
=== "RxJava3"
    ```
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
Java implementation of Valkey or Redis based [RGeo](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RGeo.html) object is a holder for geospatial items. This object is thread-safe.

Code examples:

=== "Sync"
    ```
    RGeo<String> geo = redisson.getGeo("test");

    geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), 
            new GeoEntry(15.087269, 37.502669, "Catania"));
    Double distance = geo.dist("Palermo", "Catania", GeoUnit.METERS);
    Map<String, GeoPosition> positions = geo.pos("test2", "Palermo", "test3", "Catania", "test1");

    List<String> cities = geo.search(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    Map<String, GeoPosition> citiesWithPositions = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```
=== "Async"
    ```
    RGeo<String> geo = redisson.getGeo("test");

    RFuture<Long> addFuture = geo.addAsync(new GeoEntry(13.361389, 38.115556, "Palermo"), 
            new GeoEntry(15.087269, 37.502669, "Catania"));

    RFuture<Double> distanceFuture = geo.distAsync("Palermo", "Catania", GeoUnit.METERS);
    RFuture<Map<String, GeoPosition>> positionsFuture = geo.posAsync("test2", "Palermo", "test3", "Catania", "test1");

    RFuture<List<String>> citiesFuture = geo.searchAsync(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    RFuture<Map<String, GeoPosition>> citiesWithPositions = geo.searchWithPositionAsync(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> geo = redisson.getGeo("test");

    Mono<Long> addFuture = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), 
            new GeoEntry(15.087269, 37.502669, "Catania"));

    Mono<Double> distanceFuture = geo.dist("Palermo", "Catania", GeoUnit.METERS);
    Mono<Map<String, GeoPosition>> positionsFuture = geo.pos("test2", "Palermo", "test3", "Catania", "test1");

    Mono<List<String>> citiesFuture = geo.search(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    Mono<Map<String, GeoPosition>> citiesWithPositions = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> geo = redisson.getGeo("test");

    Single<Long> addFuture = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"), 
            new GeoEntry(15.087269, 37.502669, "Catania"));

    Single<Double> distanceFuture = geo.dist("Palermo", "Catania", GeoUnit.METERS);
    Single<Map<String, GeoPosition>> positionsFuture = geo.pos("test2", "Palermo", "test3", "Catania", "test1");

    Single<List<String>> citiesFuture = geo.search(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    Single<Map<String, GeoPosition>> citiesWithPositions = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```

## BitSet
Java implementation of Valkey or Redis based [RBitSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitSet.html) object provides API similar to [java.util.BitSet](https://docs.oracle.com/javase/8/docs/api/java/util/BitSet.html). It represents vector of bits that grows as needed. Size limited to `4 294 967 295` bits. This object is thread-safe.

Code examples:

=== "Sync"
    ```
    RBitSet set = redisson.getBitSet("simpleBitset");

    set.set(0, true);
    set.set(1812, false);

    set.clear(0);

    set.and("anotherBitset");
    set.xor("anotherBitset");
    ```
=== "Async"
    ```
    RBitSetAsync set = redisson.getBitSet("simpleBitset");

    RFuture<Boolean> setFuture = set.setAsync(0, true);
    RFuture<Boolean> setFuture = set.setAsync(1812, false);

    RFuture<Void> clearFuture = set.clearAsync(0);

    RFuture<Void> andFuture = set.andAsync("anotherBitset");
    RFuture<Void> xorFuture = set.xorAsync("anotherBitset");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive set = redisson.getBitSet("simpleBitset");

    Mono<Boolean> setMono = set.set(0, true);
    Mono<Boolean> setMono = set.set(1812, false);

    Mono<Void> clearMono = set.clear(0);

    Mono<Void> andMono = set.and("anotherBitset");
    Mono<Void> xorMono = set.xor("anotherBitset");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx set = redisson.getBitSet("simpleBitset");

    Single<Boolean> setRx = set.set(0, true);
    Single<Boolean> setRx = set.set(1812, false);

    Completable clearRx = set.clear(0);

    Completable andRx = set.and("anotherBitset");
    Completable xorRx = set.xor("anotherBitset");
    ```

### Data partitioning

Although 'RBitSet' object is cluster compatible its content isn't scaled across multiple master nodes. BitSet data partitioning available only in cluster mode and implemented by separate `RClusteredBitSet` object. It uses distributed implementation of roaring bitmap structure. Size is limited by whole Cluster memory.  More details about partitioning [here](data-partitioning.md).

Below is the list of all available BitSet implementations:  

|RedissonClient <br/> method name | Data partitioning <br/> support | Ultra-fast read/write |
| ------------- | :----------:| :----------:|
|getBitSet()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ |
|getBitSet()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ✔️ |
|getClusteredBitSet()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ |

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
Java implementation of Valkey or Redis based [RBloomFilter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBloomFilter.html) object is a bloom filter. Number of contained bits is limited to `2^32` with [data partitioning](data-partitioning.md) to `2^63`. This object is thread-safe.

Must be initialized with capacity size by `tryInit(expectedInsertions, falseProbability)` method before usage.

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


## HyperLogLog

Java implementation of Valkey or Redis based [HyperLogLog](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RHyperLogLog.html) object is a probabilistic data structure that lets you maintain counts of millions of items with extreme space efficiency. This object is thread-safe.

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


## RateLimiter
Java implementation of Valkey or Redis based [RateLimiter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRateLimiter.html) object  restricts the total rate of calls either from all threads regardless of Redisson instance or from all threads working with the same Redisson instance. Doesn't guarantee fairness. This object is thread-safe.

Code example:

=== "Sync"
    ```
    RRateLimiter limiter = redisson.getRateLimiter("myLimiter");
    
	// 5 permits per 2 seconds
    limiter.trySetRate(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);
    limiter.acquire(3);
    ```

=== "Async"
    ```
    RRateLimiterAsync limiter = redisson.getRateLimiter("myLimiter");
	
    RFuture<Boolean> setRateFuture = limiter.trySetRateAsync(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);
    RFuture<Void> acquireFuture = limiter.acquireAsync(3);
    ```

=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive limiter = redisson.getRateLimiter("myLimiter");
	
    Mono<Boolean> setRateMono = limiter.trySetRate(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);
    Mono<Void> acquireMono = limiter.acquire(3);
    ```

=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx limiter = redisson.getRateLimiter("anyObject");
    
	Single<Boolean> setRateRx = limiter.trySetRate(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);
    Completable acquireRx = limiter.acquire(3);
    ```
