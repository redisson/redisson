## Object holder
Java implementation of Valkey or Redis based [RBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucket.html) object is a holder for a single object of any type. The value is serialized and stored under one key, the maximum size is 512Mb, and the object is thread-safe.

Every operation runs as an atomic command on Valkey or Redis, so reads, conditional writes, and value swaps stay consistent across all clients without external locking. A value can be given a time to live so the holder expires on its own, and the conditional `setIfAbsent`, `setIfExists`, and `compareAndSet` operations make `RBucket` a natural building block for single-value caches, flags, and lightweight coordination on one key. The [RBuckets](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBuckets.html) interface extends the same operations to many keys at once.

Code examples:

=== "Sync"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    bucket.set(new AnyObject(1));
    AnyObject obj = bucket.get();
    ```
=== "Async"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    RFuture<Void> setFuture = bucket.setAsync(new AnyObject(1));
    RFuture<AnyObject> getFuture = bucket.getAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<AnyObject> bucket = redisson.getBucket("anyObject");

    Mono<Void> setMono = bucket.set(new AnyObject(1));
    Mono<AnyObject> getMono = bucket.get();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<AnyObject> bucket = redisson.getBucket("anyObject");

    Completable setRx = bucket.set(new AnyObject(1));
    Maybe<AnyObject> getRx = bucket.get();
    ```

**Setting a value with expiration**

A value can be stored with a time to live so the holder expires on its own. `set` accepts a `Duration`, `setAndKeepTTL` replaces the value while preserving the remaining time to live, `getAndExpire` reads the current value and applies a new expiration in one step, and `getAndClearExpire` reads the value and makes it persistent by removing any expiration.

=== "Sync"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    // store with a 10-minute time to live
    bucket.set(new AnyObject(1), Duration.ofMinutes(10));

    // replace the value but keep the remaining time to live
    bucket.setAndKeepTTL(new AnyObject(2));

    // read the value and apply a new expiration
    AnyObject extended = bucket.getAndExpire(Duration.ofMinutes(30));

    // read the value and make it persistent
    AnyObject persistent = bucket.getAndClearExpire();
    ```
=== "Async"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    RFuture<Void> setFuture = bucket.setAsync(new AnyObject(1), Duration.ofMinutes(10));
    RFuture<Void> keepFuture = bucket.setAndKeepTTLAsync(new AnyObject(2));
    RFuture<AnyObject> expireFuture = bucket.getAndExpireAsync(Duration.ofMinutes(30));
    RFuture<AnyObject> clearFuture = bucket.getAndClearExpireAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<AnyObject> bucket = redisson.getBucket("anyObject");

    Mono<Void> setMono = bucket.set(new AnyObject(1), Duration.ofMinutes(10));
    Mono<Void> keepMono = bucket.setAndKeepTTL(new AnyObject(2));
    Mono<AnyObject> expireMono = bucket.getAndExpire(Duration.ofMinutes(30));
    Mono<AnyObject> clearMono = bucket.getAndClearExpire();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<AnyObject> bucket = redisson.getBucket("anyObject");

    Completable setRx = bucket.set(new AnyObject(1), Duration.ofMinutes(10));
    Completable keepRx = bucket.setAndKeepTTL(new AnyObject(2));
    Maybe<AnyObject> expireRx = bucket.getAndExpire(Duration.ofMinutes(30));
    Maybe<AnyObject> clearRx = bucket.getAndClearExpire();
    ```

**Conditional set**

`setIfAbsent` stores the value only when the holder does not yet exist, and `setIfExists` only when it already does. Both return whether the write happened and accept an optional `Duration` time to live. `setIfAbsent` replaces the older `trySet`, which is deprecated.

=== "Sync"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    // write only if the key does not exist yet
    boolean created = bucket.setIfAbsent(new AnyObject(1));

    // write only if the key already exists, with a new time to live
    boolean refreshed = bucket.setIfExists(new AnyObject(2), Duration.ofMinutes(10));
    ```
=== "Async"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    RFuture<Boolean> createdFuture = bucket.setIfAbsentAsync(new AnyObject(1));
    RFuture<Boolean> refreshedFuture = bucket.setIfExistsAsync(new AnyObject(2), Duration.ofMinutes(10));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<AnyObject> bucket = redisson.getBucket("anyObject");

    Mono<Boolean> createdMono = bucket.setIfAbsent(new AnyObject(1));
    Mono<Boolean> refreshedMono = bucket.setIfExists(new AnyObject(2), Duration.ofMinutes(10));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<AnyObject> bucket = redisson.getBucket("anyObject");

    Single<Boolean> createdRx = bucket.setIfAbsent(new AnyObject(1));
    Single<Boolean> refreshedRx = bucket.setIfExists(new AnyObject(2), Duration.ofMinutes(10));
    ```

**Atomic read-modify operations**

These combine a read with a write in a single atomic step. `compareAndSet` updates the value only if it still equals the expected one, `getAndSet` replaces the value and returns the previous one, and `getAndDelete` returns the value and removes the holder.

=== "Sync"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    // update only if the current value still matches the expected one
    boolean updated = bucket.compareAndSet(new AnyObject(1), new AnyObject(2));

    // replace the value and return the previous one
    AnyObject previous = bucket.getAndSet(new AnyObject(3));

    // read the value and remove the holder
    AnyObject last = bucket.getAndDelete();
    ```
=== "Async"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    RFuture<Boolean> updatedFuture = bucket.compareAndSetAsync(new AnyObject(1), new AnyObject(2));
    RFuture<AnyObject> previousFuture = bucket.getAndSetAsync(new AnyObject(3));
    RFuture<AnyObject> lastFuture = bucket.getAndDeleteAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<AnyObject> bucket = redisson.getBucket("anyObject");

    Mono<Boolean> updatedMono = bucket.compareAndSet(new AnyObject(1), new AnyObject(2));
    Mono<AnyObject> previousMono = bucket.getAndSet(new AnyObject(3));
    Mono<AnyObject> lastMono = bucket.getAndDelete();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<AnyObject> bucket = redisson.getBucket("anyObject");

    Single<Boolean> updatedRx = bucket.compareAndSet(new AnyObject(1), new AnyObject(2));
    Maybe<AnyObject> previousRx = bucket.getAndSet(new AnyObject(3));
    Maybe<AnyObject> lastRx = bucket.getAndDelete();
    ```

**Object information**

`size` returns the size in bytes of the serialized value stored under the key. For buckets holding string values, `findCommon` returns the longest content shared with another bucket and `findCommonLength` returns just its length.

=== "Sync"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    long sizeInBytes = bucket.size();

    // for string-valued buckets: longest shared content with another bucket
    RBucket<String> a = redisson.getBucket("keyA");
    String common = a.findCommon("keyB");
    long commonLength = a.findCommonLength("keyB");
    ```
=== "Async"
    ```
    RBucket<AnyObject> bucket = redisson.getBucket("anyObject");

    RFuture<Long> sizeFuture = bucket.sizeAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<AnyObject> bucket = redisson.getBucket("anyObject");

    Mono<Long> sizeMono = bucket.size();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<AnyObject> bucket = redisson.getBucket("anyObject");

    Single<Long> sizeRx = bucket.size();
    ```

**Operations over multiple objects**

Use the [RBuckets](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBuckets.html) interface to run operations over several [RBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBucket.html) objects in a single round trip - reading many keys at once, writing a whole map of values, or initializing them all-or-nothing.

=== "Sync"
    ```
    RBuckets buckets = redisson.getBuckets();

    // get all bucket values
    Map<String, V> loadedBuckets = buckets.get("myBucket1", "myBucket2", "myBucket3");

    Map<String, Object> map = new HashMap<>();
    map.put("myBucket1", new MyObject());
    map.put("myBucket2", new MyObject());

    // sets all or nothing if some bucket already exists
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

    // sets all or nothing if some bucket already exists
    RFuture<Boolean> tsFuture = buckets.trySetAsync(map);
    // store all at once
    RFuture<Void> sFuture = buckets.setAsync(map);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketsReactive buckets = redisson.getBuckets();

    // get all bucket values
    Mono<Map<String, V>> bucketsMono = buckets.get("myBucket1", "myBucket2", "myBucket3");

    Map<String, Object> map = new HashMap<>();
    map.put("myBucket1", new MyObject());
    map.put("myBucket2", new MyObject());

    // sets all or nothing if some bucket already exists
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

    // sets all or nothing if some bucket already exists
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

### Use Cases

RBucket is the simplest holder: one key holding one serialized object of up to 512 MB. It backs a single-value cache with an optional expiry, atomic single-key coordination through `trySet`, `compareAndSet`, and `getAndSet`, and - via `RBuckets` - reading or writing many independent keys in one round trip.

**Single-Value Cache with Expiry**

A computed result, rendered fragment, configuration snapshot, or DTO can be cached under a key with a time to live so it refreshes on its own. `setIfAbsent` populates the entry only on a miss without clobbering a fresh one, and `getAndDelete` reads and removes a value in a single step for consume-once data.

=== "Sync"
    ```
    RBucket<Report> cache = redisson.getBucket("report:daily");

    // cache a computed object for 10 minutes; it expires on its own
    cache.set(buildReport(), Duration.ofMinutes(10));

    Report cached = cache.get();

    // populate only on a miss, without overwriting a fresh entry
    cache.setIfAbsent(buildReport(), Duration.ofMinutes(10));

    // consume-once: read and remove in a single operation
    Report once = cache.getAndDelete();
    ```
=== "Async"
    ```
    RBucket<Report> cache = redisson.getBucket("report:daily");

    RFuture<Void> cached = cache.setAsync(buildReport(), Duration.ofMinutes(10));
    RFuture<Report> value = cache.getAsync();
    RFuture<Report> once = cache.getAndDeleteAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<Report> cache = redisson.getBucket("report:daily");

    Mono<Void> cached = cache.set(buildReport(), Duration.ofMinutes(10));
    Mono<Report> value = cache.get();
    Mono<Boolean> populated = cache.setIfAbsent(buildReport(), Duration.ofMinutes(10));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<Report> cache = redisson.getBucket("report:daily");

    Completable cached = cache.set(buildReport(), Duration.ofMinutes(10));
    Maybe<Report> value = cache.get();
    Single<Boolean> populated = cache.setIfAbsent(buildReport(), Duration.ofMinutes(10));
    ```

**Atomic Flags and Lock-Free Updates**

A single key often stands in for a shared flag or a small piece of mutable state. `trySet` gives set-once semantics - one-time initialization, an idempotency marker, or a simple ownership flag where only the first caller wins. `compareAndSet` applies an update only while the value still matches what was read, and `getAndSet` swaps in a new value while returning the previous one, each atomically and without an external lock.

=== "Sync"
    ```
    RBucket<String> owner = redisson.getBucket("job:owner");

    // set-once: only the first caller wins
    boolean claimed = owner.trySet(nodeId);

    // optimistic concurrency: update only if the value hasn't changed
    boolean updated = owner.compareAndSet(nodeId, newNodeId);

    // atomically swap in a new value and read the previous one
    String previous = owner.getAndSet(newNodeId);
    ```
=== "Async"
    ```
    RBucket<String> owner = redisson.getBucket("job:owner");

    RFuture<Boolean> claimed = owner.trySetAsync(nodeId);
    RFuture<Boolean> updated = owner.compareAndSetAsync(nodeId, newNodeId);
    RFuture<String> previous = owner.getAndSetAsync(newNodeId);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketReactive<String> owner = redisson.getBucket("job:owner");

    Mono<Boolean> claimed = owner.trySet(nodeId);
    Mono<Boolean> updated = owner.compareAndSet(nodeId, newNodeId);
    Mono<String> previous = owner.getAndSet(newNodeId);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketRx<String> owner = redisson.getBucket("job:owner");

    Single<Boolean> claimed = owner.trySet(nodeId);
    Single<Boolean> updated = owner.compareAndSet(nodeId, newNodeId);
    Maybe<String> previous = owner.getAndSet(newNodeId);
    ```

**Bulk Multi-Key Operations**

When several independent values are loaded or stored together - a group of configuration keys, a set of warmed cache entries - the `RBuckets` interface batches them into a single round trip. `get` reads many keys at once, `set` writes a whole map, and `trySet` initializes all-or-nothing, writing nothing if any of the keys already exists.

=== "Sync"
    ```
    RBuckets buckets = redisson.getBuckets();

    // load several independent values in one round trip
    Map<String, Object> loaded = buckets.get("config:a", "config:b", "config:c");

    Map<String, Object> values = new HashMap<>();
    values.put("config:a", configA);
    values.put("config:b", configB);

    // write them all at once
    buckets.set(values);

    // or initialize all-or-nothing: nothing is written if any key already exists
    boolean initialized = buckets.trySet(values);
    ```
=== "Async"
    ```
    RBuckets buckets = redisson.getBuckets();

    // values: Map<String, Object> prepared as in the Sync tab
    RFuture<Map<String, Object>> loaded = buckets.getAsync("config:a", "config:b");
    RFuture<Void> written = buckets.setAsync(values);
    RFuture<Boolean> initialized = buckets.trySetAsync(values);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBucketsReactive buckets = redisson.getBuckets();

    // values: Map<String, Object> prepared as in the Sync tab
    Mono<Map<String, Object>> loaded = buckets.get("config:a", "config:b");
    Mono<Void> written = buckets.set(values);
    Mono<Boolean> initialized = buckets.trySet(values);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBucketsRx buckets = redisson.getBuckets();

    // values: Map<String, Object> prepared as in the Sync tab
    Single<Map<String, Object>> loaded = buckets.get("config:a", "config:b");
    Completable written = buckets.set(values);
    Single<Boolean> initialized = buckets.trySet(values);
    ```

## Binary stream holder
Java implementation of Valkey or Redis based [RBinaryStream](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RBinaryStream.html) object holds a sequence of raw bytes - up to 512Mb - under a single key. It extends the [RBucket](#object-holder) interface, so the whole payload can be stored, read, and replaced atomically with the same operations as the object holder, and the object is thread-safe.

Beyond whole-value access, it exposes the stored bytes through standard `java.io` streams and `java.nio` channels. This lets large content be written or read incrementally, or accessed at an arbitrary offset, without holding the entire payload in memory at once - useful for binary blobs such as files, images, or serialized data.

Code examples:

=== "Sync"
    ```
    RBinaryStream stream = redisson.getBinaryStream("anyStream");

    byte[] content = ...
    stream.set(content);
    byte[] current = stream.get();

    byte[] previous = stream.getAndSet(content);
    boolean created = stream.setIfAbsent(content);
    boolean updated = stream.compareAndSet(oldContent, content);
    ```
=== "Async"
    ```
    RBinaryStream stream = redisson.getBinaryStream("anyStream");

    byte[] content = ...
    RFuture<Void> setFuture = stream.setAsync(content);
    RFuture<byte[]> getFuture = stream.getAsync();

    RFuture<byte[]> previousFuture = stream.getAndSetAsync(content);
    RFuture<Boolean> createdFuture = stream.setIfAbsentAsync(content);
    RFuture<Boolean> updatedFuture = stream.compareAndSetAsync(oldContent, content);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBinaryStreamReactive stream = redisson.getBinaryStream("anyStream");

    byte[] content = ...
    Mono<Void> setMono = stream.set(content);
    Mono<byte[]> getMono = stream.get();

    Mono<byte[]> previousMono = stream.getAndSet(content);
    Mono<Boolean> createdMono = stream.setIfAbsent(content);
    Mono<Boolean> updatedMono = stream.compareAndSet(oldContent, content);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBinaryStreamRx stream = redisson.getBinaryStream("anyStream");

    byte[] content = ...
    Completable setRx = stream.set(content);
    Maybe<byte[]> getRx = stream.get();

    Maybe<byte[]> previousRx = stream.getAndSet(content);
    Single<Boolean> createdRx = stream.setIfAbsent(content);
    Single<Boolean> updatedRx = stream.compareAndSet(oldContent, content);
    ```

**Reading and writing through java.io streams**

`getInputStream` and `getOutputStream` expose the stored bytes as standard [java.io.InputStream](https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html) and [java.io.OutputStream](https://docs.oracle.com/javase/8/docs/api/java/io/OutputStream.html), so a large payload can be read or written in chunks without buffering it all in memory.

```java
RBinaryStream stream = redisson.getBinaryStream("anyStream");

InputStream is = stream.getInputStream();
byte[] readBuffer = ...
is.read(readBuffer);

OutputStream os = stream.getOutputStream();
byte[] contentToWrite = ...
os.write(contentToWrite);
```

**Random access with a seekable channel**

`getChannel` returns a [java.nio.channels.SeekableByteChannel](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/SeekableByteChannel.html), which reads or writes at an arbitrary offset selected with `position` and can shrink the stored value with `truncate` - suited to patching part of a stored object in place.

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

**Non-blocking access with an asynchronous channel**

`getAsynchronousChannel` returns a [java.nio.channels.AsynchronousByteChannel](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/AsynchronousByteChannel.html) whose `read` and `write` operate without blocking the calling thread.

```java
RBinaryStream stream = redisson.getBinaryStream("anyStream");

AsynchronousByteChannel channel = stream.getAsynchronousChannel();
ByteBuffer readBuffer = ...
channel.read(readBuffer);

ByteBuffer contentToWrite = ...
channel.write(contentToWrite);
```

**Reactive and RxJava3 streaming**

The Reactive and RxJava3 interfaces stream incrementally through `write(ByteBuffer)` and `read(ByteBuffer)`, moving to an offset with `position` between operations.

=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBinaryStreamReactive stream = redisson.getBinaryStream("anyStream");

    ByteBuffer contentToWrite = ...
    Mono<Integer> written = stream.write(contentToWrite);

    stream.position(0);

    ByteBuffer readBuffer = ...
    Mono<Integer> read = stream.read(readBuffer);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBinaryStreamRx stream = redisson.getBinaryStream("anyStream");

    ByteBuffer contentToWrite = ...
    Single<Integer> written = stream.write(contentToWrite);

    stream.position(0);

    ByteBuffer readBuffer = ...
    Single<Integer> read = stream.read(readBuffer);
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

### Use Cases

RBinaryStream holds a sequence of bytes - up to 512 MB - and extends the object holder, so a blob can be stored or replaced atomically, streamed through standard `java.io` streams, or accessed at arbitrary offsets through a seekable channel. It fits storing binary objects, moving large payloads without buffering them whole, and reading or patching part of a stored object.

**Binary Blob Storage**

Rendered reports, generated images, thumbnails, and serialized payloads can be stored whole under a key. `set` writes the bytes and `get` reads them back, while the atomic `trySet` and `compareAndSet` give publish-once and replace-if-unchanged semantics for a shared blob.

=== "Sync"
    ```
    RBinaryStream blob = redisson.getBinaryStream("report:2026-06");

    byte[] pdf = renderReport();
    blob.set(pdf);                 // store the whole blob (up to 512 MB)

    byte[] stored = blob.get();    // read it back

    // replace only if the current bytes still match what we last read
    boolean replaced = blob.compareAndSet(stored, renderReport());
    ```
=== "Async"
    ```
    RBinaryStream blob = redisson.getBinaryStream("report:2026-06");

    RFuture<Void> stored = blob.setAsync(pdf);
    RFuture<byte[]> loaded = blob.getAsync();
    RFuture<Boolean> created = blob.trySetAsync(pdf);   // publish once
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBinaryStreamReactive blob = redisson.getBinaryStream("report:2026-06");

    Mono<Void> stored = blob.set(pdf);
    Mono<byte[]> loaded = blob.get();
    Mono<Boolean> created = blob.trySet(pdf);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBinaryStreamRx blob = redisson.getBinaryStream("report:2026-06");

    Completable stored = blob.set(pdf);
    Maybe<byte[]> loaded = blob.get();
    Single<Boolean> created = blob.trySet(pdf);
    ```

**Streaming Large Content Without Full Buffering**

A large object can be produced or consumed incrementally instead of being held in a single array. `getOutputStream()` writes a payload into storage as it arrives - piping an upload straight through - and `getInputStream()` reads a stored object back in chunks to send onward, keeping memory use bounded regardless of the object's size. The Reactive and RxJava3 interfaces stream the same way through `write(ByteBuffer)` and `read(ByteBuffer)` together with `position`.

```java
RBinaryStream blob = redisson.getBinaryStream("upload:99");

// write a large payload incrementally, without buffering it all in memory
try (OutputStream out = blob.getOutputStream()) {
    incoming.transferTo(out);                 // e.g. an HTTP request body
}

// read it back in chunks to stream onward to a client
try (InputStream in = blob.getInputStream()) {
    in.transferTo(response.getOutputStream());
}
```

**Random-Access Reads and In-Place Edits**

For objects with a fixed layout, the seekable channel reads or rewrites a region without touching the rest. `getChannel()` returns a `SeekableByteChannel`: `position` moves to an offset, `read` and `write` operate from there, and `truncate` trims the object - useful for patching a header, updating a fixed-size record, or resuming an interrupted transfer.

```java
RBinaryStream blob = redisson.getBinaryStream("archive:1");
SeekableByteChannel channel = blob.getChannel();

// overwrite a fixed-size header at the start
channel.position(0);
channel.write(ByteBuffer.wrap(newHeader));

// read a record at a known offset
channel.position(4096);
ByteBuffer record = ByteBuffer.allocate(512);
channel.read(record);

// trim the object to a known length
channel.truncate(8192);
```

## JSON object holder
Java implementation of Valkey or Redis based [RJsonBucket](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RJsonBucket.html) object stores a value as a JSON document and manipulates it through Valkey or Redis `JSON.*` commands. Encoding and decoding are handled by a `JsonCodec`, a required constructor parameter; the available implementation is `org.redisson.codec.JacksonCodec`. It extends the [object holder](#object-holder) interface, so the whole value can be set, read, and replaced atomically, and the object is thread-safe.

Its distinctive capability is partial access: operations that take a JSONPath read or modify an individual field, array element, or nested object in place, without fetching and rewriting the whole document. Paths follow JSONPath syntax such as `$.obj.values`.

Use [JSON Store](collections.md/#json-store) for key-value implementation and local cache support.

Code examples:

=== "Sync"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    bucket.set(new AnyObject(1));
    AnyObject obj = bucket.get();
    ```
=== "Async"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    RFuture<Void> setFuture = bucket.setAsync(new AnyObject(1));
    RFuture<AnyObject> getFuture = bucket.getAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonBucketReactive<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Mono<Void> setMono = bucket.set(new AnyObject(1));
    Mono<AnyObject> getMono = bucket.get();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonBucketRx<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Completable setRx = bucket.set(new AnyObject(1));
    Maybe<AnyObject> getRx = bucket.get();
    ```

**Reading and updating fields by path**

Operations that take a JSONPath read or change part of the document in place. `get` reads the value at one or more paths, decoded with a `JsonCodec` for the expected type; `set` writes a value at a path; `merge` merges an object into the value at a path; and `delete` removes the value at a path.

=== "Sync"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    // read a nested field with the codec for its type
    List<String> values = bucket.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "$.obj.values");

    // write a value at a path
    bucket.set("$.obj.name", "updated");

    // merge an object into a path
    bucket.merge("$.obj", new AnyObject(2));

    // remove the value at a path
    long removed = bucket.delete("$.obj.tmp");
    ```
=== "Async"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    RFuture<List<String>> valuesFuture = bucket.getAsync(new JacksonCodec<>(new TypeReference<List<String>>() {}), "$.obj.values");
    RFuture<Void> setFuture = bucket.setAsync("$.obj.name", "updated");
    RFuture<Void> mergeFuture = bucket.mergeAsync("$.obj", new AnyObject(2));
    RFuture<Long> removedFuture = bucket.deleteAsync("$.obj.tmp");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonBucketReactive<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Mono<List<String>> valuesMono = bucket.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "$.obj.values");
    Mono<Void> setMono = bucket.set("$.obj.name", "updated");
    Mono<Void> mergeMono = bucket.merge("$.obj", new AnyObject(2));
    Mono<Long> removedMono = bucket.delete("$.obj.tmp");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonBucketRx<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Maybe<List<String>> valuesRx = bucket.get(new JacksonCodec<>(new TypeReference<List<String>>() {}), "$.obj.values");
    Completable setRx = bucket.set("$.obj.name", "updated");
    Completable mergeRx = bucket.merge("$.obj", new AnyObject(2));
    Single<Long> removedRx = bucket.delete("$.obj.tmp");
    ```

**Conditional and atomic field updates**

`setIfAbsent` and `setIfExists` write a path only when it is respectively missing or already present, `compareAndSet` updates a path only if its current value matches the expected one, and `getAndSet` replaces the value at a path and returns the previous one. `setIfAbsent` replaces the deprecated `trySet`.

=== "Sync"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    // write a path only if it does not exist yet
    boolean created = bucket.setIfAbsent("$.obj.name", "first");

    // write a path only if it already exists
    boolean updated = bucket.setIfExists("$.obj.name", "second");

    // update a path only if it still holds the expected value
    boolean swapped = bucket.compareAndSet("$.obj.name", "second", "third");

    // replace a path and return the previous value
    String previous = bucket.getAndSet(new JacksonCodec<>(String.class), "$.obj.name", "fourth");
    ```
=== "Async"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    RFuture<Boolean> createdFuture = bucket.setIfAbsentAsync("$.obj.name", "first");
    RFuture<Boolean> updatedFuture = bucket.setIfExistsAsync("$.obj.name", "second");
    RFuture<Boolean> swappedFuture = bucket.compareAndSetAsync("$.obj.name", "second", "third");
    RFuture<String> previousFuture = bucket.getAndSetAsync(new JacksonCodec<>(String.class), "$.obj.name", "fourth");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonBucketReactive<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Mono<Boolean> createdMono = bucket.setIfAbsent("$.obj.name", "first");
    Mono<Boolean> updatedMono = bucket.setIfExists("$.obj.name", "second");
    Mono<Boolean> swappedMono = bucket.compareAndSet("$.obj.name", "second", "third");
    Mono<String> previousMono = bucket.getAndSet(new JacksonCodec<>(String.class), "$.obj.name", "fourth");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonBucketRx<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Single<Boolean> createdRx = bucket.setIfAbsent("$.obj.name", "first");
    Single<Boolean> updatedRx = bucket.setIfExists("$.obj.name", "second");
    Single<Boolean> swappedRx = bucket.compareAndSet("$.obj.name", "second", "third");
    Maybe<String> previousRx = bucket.getAndSet(new JacksonCodec<>(String.class), "$.obj.name", "fourth");
    ```

**Array operations**

When a path points at a JSON array, these operations modify it in place. `arrayAppend` and `arrayInsert` add elements, `arraySize` returns its length, `arrayIndex` finds an element, `arrayPollFirst` and `arrayPollLast` remove and return an element from either end, and `arrayTrim` keeps only a range of indexes.

=== "Sync"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    long size = bucket.arrayAppend("$.obj.values", "t3", "t4");
    bucket.arrayInsert("$.obj.values", 0, "t0");

    long length = bucket.arraySize("$.obj.values");
    long index = bucket.arrayIndex("$.obj.values", "t3");

    String first = bucket.arrayPollFirst(new JacksonCodec<>(String.class), "$.obj.values");
    String last = bucket.arrayPollLast(new JacksonCodec<>(String.class), "$.obj.values");

    // keep only elements 0..2
    bucket.arrayTrim("$.obj.values", 0, 2);
    ```
=== "Async"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    RFuture<Long> sizeFuture = bucket.arrayAppendAsync("$.obj.values", "t3", "t4");
    RFuture<Long> insertFuture = bucket.arrayInsertAsync("$.obj.values", 0, "t0");
    RFuture<Long> lengthFuture = bucket.arraySizeAsync("$.obj.values");
    RFuture<Long> indexFuture = bucket.arrayIndexAsync("$.obj.values", "t3");
    RFuture<String> firstFuture = bucket.arrayPollFirstAsync(new JacksonCodec<>(String.class), "$.obj.values");
    RFuture<Long> trimFuture = bucket.arrayTrimAsync("$.obj.values", 0, 2);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonBucketReactive<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Mono<Long> sizeMono = bucket.arrayAppend("$.obj.values", "t3", "t4");
    Mono<Long> insertMono = bucket.arrayInsert("$.obj.values", 0L, "t0");
    Mono<Long> lengthMono = bucket.arraySize("$.obj.values");
    Mono<Long> indexMono = bucket.arrayIndex("$.obj.values", "t3");
    Mono<String> firstMono = bucket.arrayPollFirst(new JacksonCodec<>(String.class), "$.obj.values");
    Mono<Long> trimMono = bucket.arrayTrim("$.obj.values", 0L, 2L);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonBucketRx<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Single<Long> sizeRx = bucket.arrayAppend("$.obj.values", "t3", "t4");
    Single<Long> insertRx = bucket.arrayInsert("$.obj.values", 0L, "t0");
    Single<Long> lengthRx = bucket.arraySize("$.obj.values");
    Single<Long> indexRx = bucket.arrayIndex("$.obj.values", "t3");
    Maybe<String> firstRx = bucket.arrayPollFirst(new JacksonCodec<>(String.class), "$.obj.values");
    Single<Long> trimRx = bucket.arrayTrim("$.obj.values", 0L, 2L);
    ```

**In-place value changes and inspection**

`toggle` flips a boolean at a path and `stringAppend` appends to a string at a path, returning its new length. `getType` reports the JSON type at a path, `countKeys` counts the keys of an object, and `clear` empties the container at a path (or `clear()` empties the whole document).

=== "Sync"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    boolean enabled = bucket.toggle("$.obj.enabled");
    long newLength = bucket.stringAppend("$.obj.name", "-suffix");

    JsonType type = bucket.getType("$.obj");
    long keys = bucket.countKeys("$.obj");

    // empty the container at a path
    long cleared = bucket.clear("$.obj.values");
    ```
=== "Async"
    ```
    RJsonBucket<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    RFuture<Boolean> enabledFuture = bucket.toggleAsync("$.obj.enabled");
    RFuture<Long> lengthFuture = bucket.stringAppendAsync("$.obj.name", "-suffix");
    RFuture<JsonType> typeFuture = bucket.getTypeAsync("$.obj");
    RFuture<Long> keysFuture = bucket.countKeysAsync("$.obj");
    RFuture<Long> clearedFuture = bucket.clearAsync("$.obj.values");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonBucketReactive<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Mono<Boolean> enabledMono = bucket.toggle("$.obj.enabled");
    Mono<Long> lengthMono = bucket.stringAppend("$.obj.name", "-suffix");
    Mono<JsonType> typeMono = bucket.getType("$.obj");
    Mono<Long> keysMono = bucket.countKeys("$.obj");
    Mono<Long> clearedMono = bucket.clear("$.obj.values");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonBucketRx<AnyObject> bucket = redisson.getJsonBucket("anyObject", new JacksonCodec<>(AnyObject.class));

    Single<Boolean> enabledRx = bucket.toggle("$.obj.enabled");
    Single<Long> lengthRx = bucket.stringAppend("$.obj.name", "-suffix");
    Single<JsonType> typeRx = bucket.getType("$.obj");
    Single<Long> keysRx = bucket.countKeys("$.obj");
    Single<Long> clearedRx = bucket.clear("$.obj.values");
    ```

### Use Cases

RJsonBucket stores a single object as native JSON and operates on it with `JSON.*` commands, which means individual fields and nested arrays can be read and modified by path without transferring the whole document. That path access, together with atomic operations like `trySet`, `compareAndSet`, and `getAndSet`, makes it a fit for large documents updated a field at a time, shared state changed concurrently, and structures that grow over time.

**Updating Fields of a Large Document In Place**

A profile, product record, or settings document can be large, yet most operations touch only one field. Because it is stored as native JSON, individual members are read and written by path: `set(path, value)` replaces a field, `get(codec, path)` fetches just one, `merge(path, value)` patches a subtree, and `toggle(path)` flips a boolean - so a single nested change never serializes or transfers the whole document.

=== "Sync"
    ```
    RJsonBucket<Profile> profile = redisson.getJsonBucket("profile:42", new JacksonCodec<>(Profile.class));
    profile.set(loadedProfile);   // store the full document once

    // read just one field, decoded with its own codec
    String city = profile.get(new JacksonCodec<>(String.class), "address.city");

    // replace a single nested field without rewriting the whole document
    profile.set("$.address.city", "Berlin");

    // patch a subtree, then flip a boolean flag in place
    profile.merge("$.preferences", updatedPreferences);
    profile.toggle("$.preferences.newsletter");
    ```
=== "Async"
    ```
    RJsonBucket<Profile> profile = redisson.getJsonBucket("profile:42", new JacksonCodec<>(Profile.class));

    RFuture<String> city = profile.getAsync(new JacksonCodec<>(String.class), "address.city");
    RFuture<Void> updated = profile.setAsync("$.address.city", "Berlin");
    RFuture<Boolean> toggled = profile.toggleAsync("$.preferences.newsletter");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonBucketReactive<Profile> profile = redisson.getJsonBucket("profile:42", new JacksonCodec<>(Profile.class));

    Mono<String> city = profile.get(new JacksonCodec<>(String.class), "address.city");
    Mono<Void> updated = profile.set("$.address.city", "Berlin");
    Mono<Boolean> toggled = profile.toggle("$.preferences.newsletter");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonBucketRx<Profile> profile = redisson.getJsonBucket("profile:42", new JacksonCodec<>(Profile.class));

    Maybe<String> city = profile.get(new JacksonCodec<>(String.class), "address.city");
    Completable updated = profile.set("$.address.city", "Berlin");
    Single<Boolean> toggled = profile.toggle("$.preferences.newsletter");
    ```

**Atomic Updates and Optimistic Concurrency**

When several writers share one JSON document, updates must be safe without external locks. `trySet` initializes the document only if it is absent, `compareAndSet` applies a change only while the current value still matches what was read, and `getAndSet` swaps in a new value and returns the previous one - each in a single atomic operation, applied to the whole document or to a specific path.

=== "Sync"
    ```
    RJsonBucket<Order> order = redisson.getJsonBucket("order:1001", new JacksonCodec<>(Order.class));

    // initialize once - a no-op if another writer already created it
    boolean created = order.trySet(newOrder);

    // optimistic concurrency: change the status only if it hasn't moved on
    boolean confirmed = order.compareAndSet("$.status", "PENDING", "CONFIRMED");

    // atomically replace the document, returning the previous value
    Order previous = order.getAndSet(updatedOrder);
    ```
=== "Async"
    ```
    RJsonBucket<Order> order = redisson.getJsonBucket("order:1001", new JacksonCodec<>(Order.class));

    RFuture<Boolean> created = order.trySetAsync(newOrder);
    RFuture<Boolean> confirmed = order.compareAndSetAsync("$.status", "PENDING", "CONFIRMED");
    RFuture<Order> previous = order.getAndSetAsync(updatedOrder);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonBucketReactive<Order> order = redisson.getJsonBucket("order:1001", new JacksonCodec<>(Order.class));

    Mono<Boolean> created = order.trySet(newOrder);
    Mono<Boolean> confirmed = order.compareAndSet("$.status", "PENDING", "CONFIRMED");
    Mono<Order> previous = order.getAndSet(updatedOrder);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonBucketRx<Order> order = redisson.getJsonBucket("order:1001", new JacksonCodec<>(Order.class));

    Single<Boolean> created = order.trySet(newOrder);
    Single<Boolean> confirmed = order.compareAndSet("$.status", "PENDING", "CONFIRMED");
    Maybe<Order> previous = order.getAndSet(updatedOrder);
    ```

**Bounded Append-Only Arrays**

Documents often embed a growing list - an entity's recent events, an audit trail, a feed of items. `arrayAppend` adds to a nested array on the server, `arraySize` reports its length, and `arrayTrim` caps it to the most recent entries, keeping the embedded list bounded without reading or rewriting the surrounding document.

=== "Sync"
    ```
    RJsonBucket<Account> account = redisson.getJsonBucket("account:42", new JacksonCodec<>(Account.class));

    // append events to a nested array, server-side; returns the new length
    long size = account.arrayAppend("$.recentEvents", "login", "purchase");

    // keep only the last 100 entries
    account.arrayTrim("$.recentEvents", -100, -1);

    // current length of the nested array
    long count = account.arraySize("$.recentEvents");
    ```
=== "Async"
    ```
    RJsonBucket<Account> account = redisson.getJsonBucket("account:42", new JacksonCodec<>(Account.class));

    RFuture<Long> size = account.arrayAppendAsync("$.recentEvents", "login", "purchase");
    RFuture<Long> trimmed = account.arrayTrimAsync("$.recentEvents", -100, -1);
    RFuture<Long> count = account.arraySizeAsync("$.recentEvents");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonBucketReactive<Account> account = redisson.getJsonBucket("account:42", new JacksonCodec<>(Account.class));

    Mono<Long> size = account.arrayAppend("$.recentEvents", "login", "purchase");
    Mono<Long> trimmed = account.arrayTrim("$.recentEvents", -100L, -1L);
    Mono<Long> count = account.arraySize("$.recentEvents");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonBucketRx<Account> account = redisson.getJsonBucket("account:42", new JacksonCodec<>(Account.class));

    Single<Long> size = account.arrayAppend("$.recentEvents", "login", "purchase");
    Single<Long> trimmed = account.arrayTrim("$.recentEvents", -100L, -1L);
    Single<Long> count = account.arraySize("$.recentEvents");
    ```

## Geospatial holder
Java implementation of Valkey or Redis based [RGeo](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RGeo.html) object is a holder for geospatial items - members tagged with a longitude and latitude. It extends the [scored sorted set](collections.md/#scoredsortedset), storing each member's coordinates so they can be measured and searched by location, and the object is thread-safe.

Members are added with their coordinates, the distance between any two of them can be measured in a chosen unit, and the holder can be searched for the members within a radius or rectangle of a point or another member - returning their identifiers, positions, or distances.

Code examples:

=== "Sync"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"),
            new GeoEntry(15.087269, 37.502669, "Catania"));

    Map<String, GeoPosition> positions = geo.pos("Palermo", "Catania");
    ```
=== "Async"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    RFuture<Long> addFuture = geo.addAsync(new GeoEntry(13.361389, 38.115556, "Palermo"),
            new GeoEntry(15.087269, 37.502669, "Catania"));
    RFuture<Map<String, GeoPosition>> positionsFuture = geo.posAsync("Palermo", "Catania");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> geo = redisson.getGeo("cities");

    Mono<Long> addMono = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"),
            new GeoEntry(15.087269, 37.502669, "Catania"));
    Mono<Map<String, GeoPosition>> positionsMono = geo.pos("Palermo", "Catania");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> geo = redisson.getGeo("cities");

    Single<Long> addRx = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"),
            new GeoEntry(15.087269, 37.502669, "Catania"));
    Maybe<Map<String, GeoPosition>> positionsRx = geo.pos("Palermo", "Catania");
    ```

**Adding members**

Members are added with their coordinates as `GeoEntry(longitude, latitude, member)`, or one at a time with `add(longitude, latitude, member)`. `add` returns the number of newly stored members, `addIfExists` updates only members that are already present, and `tryAdd` adds only members that are not present yet.

=== "Sync"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    // add new members, returns how many were newly stored
    long added = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"),
            new GeoEntry(15.087269, 37.502669, "Catania"));

    // or add a single member by coordinates
    geo.add(2.349014, 48.864716, "Paris");

    // update only members that already exist
    geo.addIfExists(new GeoEntry(13.361389, 38.115556, "Palermo"));

    // add only members that are not present yet
    geo.tryAdd(new GeoEntry(4.835659, 45.764043, "Lyon"));
    ```
=== "Async"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    RFuture<Long> addedFuture = geo.addAsync(new GeoEntry(13.361389, 38.115556, "Palermo"),
            new GeoEntry(15.087269, 37.502669, "Catania"));
    RFuture<Long> existsFuture = geo.addIfExistsAsync(new GeoEntry(13.361389, 38.115556, "Palermo"));
    RFuture<Long> tryFuture = geo.tryAddAsync(new GeoEntry(4.835659, 45.764043, "Lyon"));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> geo = redisson.getGeo("cities");

    Mono<Long> addedMono = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"),
            new GeoEntry(15.087269, 37.502669, "Catania"));
    Mono<Long> existsMono = geo.addIfExists(new GeoEntry(13.361389, 38.115556, "Palermo"));
    Mono<Long> tryMono = geo.tryAdd(new GeoEntry(4.835659, 45.764043, "Lyon"));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> geo = redisson.getGeo("cities");

    Single<Long> addedRx = geo.add(new GeoEntry(13.361389, 38.115556, "Palermo"),
            new GeoEntry(15.087269, 37.502669, "Catania"));
    Single<Long> existsRx = geo.addIfExists(new GeoEntry(13.361389, 38.115556, "Palermo"));
    Single<Long> tryRx = geo.tryAdd(new GeoEntry(4.835659, 45.764043, "Lyon"));
    ```

**Distance and position**

`dist` measures the distance between two members in a chosen `GeoUnit`. `pos` returns the coordinates of members as `GeoPosition` (read with `getLongitude` and `getLatitude`), and `hash` returns their Geohash string representations.

=== "Sync"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    Double distance = geo.dist("Palermo", "Catania", GeoUnit.KILOMETERS);

    Map<String, GeoPosition> positions = geo.pos("Palermo", "Catania");
    double lon = positions.get("Palermo").getLongitude();
    double lat = positions.get("Palermo").getLatitude();

    Map<String, String> hashes = geo.hash("Palermo", "Catania");
    ```
=== "Async"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    RFuture<Double> distanceFuture = geo.distAsync("Palermo", "Catania", GeoUnit.KILOMETERS);
    RFuture<Map<String, GeoPosition>> positionsFuture = geo.posAsync("Palermo", "Catania");
    RFuture<Map<String, String>> hashesFuture = geo.hashAsync("Palermo", "Catania");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> geo = redisson.getGeo("cities");

    Mono<Double> distanceMono = geo.dist("Palermo", "Catania", GeoUnit.KILOMETERS);
    Mono<Map<String, GeoPosition>> positionsMono = geo.pos("Palermo", "Catania");
    Mono<Map<String, String>> hashesMono = geo.hash("Palermo", "Catania");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> geo = redisson.getGeo("cities");

    Single<Double> distanceRx = geo.dist("Palermo", "Catania", GeoUnit.KILOMETERS);
    Maybe<Map<String, GeoPosition>> positionsRx = geo.pos("Palermo", "Catania");
    Maybe<Map<String, String>> hashesRx = geo.hash("Palermo", "Catania");
    ```

**Searching by radius or box**

`search` returns the members inside a region described by `GeoSearchArgs`. The region is centred on a coordinate with `from(longitude, latitude)` or on an existing member with `from(member)`, and shaped as a circle with `radius` or a rectangle with `box`. Results can be limited with `count` and ordered by distance with `order(GeoOrder.ASC)` or `order(GeoOrder.DESC)`. `searchWithDistance` and `searchWithPosition` return the same matches together with each member's distance from the centre or its position.

=== "Sync"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    // members within 200 km of a coordinate, nearest first, at most 10
    List<String> nearby = geo.search(GeoSearchArgs.from(15, 37)
            .radius(200, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));

    // members within a 400 x 400 km box around an existing member
    List<String> inBox = geo.search(GeoSearchArgs.from("Palermo")
            .box(400, 400, GeoUnit.KILOMETERS));

    // matches together with their distance from the centre
    Map<String, Double> withDistance = geo.searchWithDistance(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));

    // matches together with their positions
    Map<String, GeoPosition> withPosition = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```
=== "Async"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    RFuture<List<String>> nearbyFuture = geo.searchAsync(GeoSearchArgs.from(15, 37)
            .radius(200, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));
    RFuture<Map<String, Double>> withDistanceFuture = geo.searchWithDistanceAsync(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    RFuture<Map<String, GeoPosition>> withPositionFuture = geo.searchWithPositionAsync(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> geo = redisson.getGeo("cities");

    Mono<List<String>> nearbyMono = geo.search(GeoSearchArgs.from(15, 37)
            .radius(200, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));
    Mono<Map<String, Double>> withDistanceMono = geo.searchWithDistance(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    Mono<Map<String, GeoPosition>> withPositionMono = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> geo = redisson.getGeo("cities");

    Maybe<List<String>> nearbyRx = geo.search(GeoSearchArgs.from(15, 37)
            .radius(200, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));
    Maybe<Map<String, Double>> withDistanceRx = geo.searchWithDistance(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    Maybe<Map<String, GeoPosition>> withPositionRx = geo.searchWithPosition(GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```

**Storing search results**

`storeSearchTo` runs a search and writes the matching members into another destination object instead of returning them, while `storeSortedSearchTo` stores them ordered by distance from the centre. Both return the number of members stored.

=== "Sync"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    // store matches into another object, return how many were stored
    long stored = geo.storeSearchTo("nearbyCities", GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));

    // store them ordered by distance from the centre
    long storedSorted = geo.storeSortedSearchTo("nearbyCities", GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS).order(GeoOrder.ASC));
    ```
=== "Async"
    ```
    RGeo<String> geo = redisson.getGeo("cities");

    RFuture<Long> storedFuture = geo.storeSearchToAsync("nearbyCities", GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> geo = redisson.getGeo("cities");

    Mono<Long> storedMono = geo.storeSearchTo("nearbyCities", GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> geo = redisson.getGeo("cities");

    Single<Long> storedRx = geo.storeSearchTo("nearbyCities", GeoSearchArgs.from(15, 37).radius(200, GeoUnit.KILOMETERS));
    ```

### Use Cases

RGeo stores points by longitude and latitude under a member id and answers proximity questions on the server. Members are added with `add`, the distance between two of them is read with `dist` and their coordinates with `pos`, and `search` (with the `searchWithDistance` and `searchWithPosition` variants) returns the members inside a radius or box around a point, optionally nearest-first and capped with a count. It fits store and venue locators, proximity matching, and region queries.

**Proximity Search**

Locators and discovery features answer "what is close to here". Each place is stored once under its id at its coordinates, and a radius search around the user's position returns the matches ordered nearest-first and capped with `count`. The `searchWithDistance` variant returns each match together with how far away it is, ready to display.

=== "Sync"
    ```
    RGeo<String> stores = redisson.getGeo("stores");

    // points are added as (longitude, latitude, member)
    stores.add(new GeoEntry(13.361389, 38.115556, "store:palermo"),
               new GeoEntry(15.087269, 37.502669, "store:catania"));

    // 10 nearest stores within 50 km of the user, closest first
    List<String> nearby = stores.search(GeoSearchArgs.from(15.0, 37.5)
            .radius(50, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));

    // same query, with each store's distance from the user
    Map<String, Double> withDistance = stores.searchWithDistance(GeoSearchArgs.from(15.0, 37.5)
            .radius(50, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));
    ```
=== "Async"
    ```
    RGeo<String> stores = redisson.getGeo("stores");

    // 10 nearest stores within 50 km, closest first, each with its distance
    RFuture<Map<String, Double>> nearby = stores.searchWithDistanceAsync(GeoSearchArgs.from(15.0, 37.5)
            .radius(50, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> stores = redisson.getGeo("stores");

    Mono<Map<String, Double>> nearby = stores.searchWithDistance(GeoSearchArgs.from(15.0, 37.5)
            .radius(50, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> stores = redisson.getGeo("stores");

    Single<Map<String, Double>> nearby = stores.searchWithDistance(GeoSearchArgs.from(15.0, 37.5)
            .radius(50, GeoUnit.KILOMETERS)
            .order(GeoOrder.ASC)
            .count(10));
    ```

**Distance and Position Between Entities**

When two entities are already known, `dist` returns the distance between them in the chosen unit, computed on the server, and `pos` returns their coordinates when the application needs to render them. In a dispatch or ride-hailing flow this gives the distance from a driver to a pickup point, or the gap between a courier and a drop-off.

=== "Sync"
    ```
    RGeo<String> drivers = redisson.getGeo("drivers");
    drivers.add(2.35, 48.85, "driver:7");   // add(longitude, latitude, member)

    // distance between two known members, in meters
    Double metres = drivers.dist("driver:7", "driver:12", GeoUnit.METERS);

    // current coordinates of specific members
    Map<String, GeoPosition> positions = drivers.pos("driver:7", "driver:12");
    GeoPosition p = positions.get("driver:7");
    double lon = p.getLongitude();
    double lat = p.getLatitude();
    ```
=== "Async"
    ```
    RGeo<String> drivers = redisson.getGeo("drivers");

    RFuture<Double> metres = drivers.distAsync("driver:7", "driver:12", GeoUnit.METERS);
    RFuture<Map<String, GeoPosition>> positions = drivers.posAsync("driver:7", "driver:12");
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> drivers = redisson.getGeo("drivers");

    Mono<Double> metres = drivers.dist("driver:7", "driver:12", GeoUnit.METERS);
    Mono<Map<String, GeoPosition>> positions = drivers.pos("driver:7", "driver:12");
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> drivers = redisson.getGeo("drivers");

    Single<Double> metres = drivers.dist("driver:7", "driver:12", GeoUnit.METERS);
    Single<Map<String, GeoPosition>> positions = drivers.pos("driver:7", "driver:12");
    ```

**Region and Bounding-Box Queries**

Beyond a radius, `box` matches everything inside a rectangular area - a delivery zone, a map viewport, a grid cell. For pipelines that act on the matched set rather than display it, `storeSearchTo` writes the matching members into another object so they can be ranked or processed separately, and returns how many were stored.

=== "Sync"
    ```
    RGeo<String> requests = redisson.getGeo("delivery-requests");

    // everything inside a 20 km x 20 km box centred on the depot
    List<String> inZone = requests.search(GeoSearchArgs.from(13.36, 38.11)
            .box(20, 20, GeoUnit.KILOMETERS));

    // store up to 500 matches into another object for downstream processing
    long stored = requests.storeSearchTo("zone:north", GeoSearchArgs.from(13.36, 38.11)
            .box(20, 20, GeoUnit.KILOMETERS)
            .count(500));
    ```
=== "Async"
    ```
    RGeo<String> requests = redisson.getGeo("delivery-requests");

    // store the matches into another object for downstream processing
    RFuture<Long> stored = requests.storeSearchToAsync("zone:north", GeoSearchArgs.from(13.36, 38.11)
            .box(20, 20, GeoUnit.KILOMETERS)
            .count(500));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGeoReactive<String> requests = redisson.getGeo("delivery-requests");

    Mono<List<String>> inZone = requests.search(GeoSearchArgs.from(13.36, 38.11)
            .box(20, 20, GeoUnit.KILOMETERS));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGeoRx<String> requests = redisson.getGeo("delivery-requests");

    Single<List<String>> inZone = requests.search(GeoSearchArgs.from(13.36, 38.11)
            .box(20, 20, GeoUnit.KILOMETERS));
    ```

## BitSet
Java implementation of Valkey or Redis based [RBitSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitSet.html) object provides an API similar to [java.util.BitSet](https://docs.oracle.com/javase/8/docs/api/java/util/BitSet.html). It stores a vector of bits, addressed by index, that grows as needed up to `4 294 967 295` bits, and the object is thread-safe.

One bit per index makes it the most compact way to keep a boolean per entity. Individual bits and whole ranges can be set, cleared, and read; whole bitsets can be combined with bitwise operations on the server; `cardinality` counts the bits that are set; and regions of bits can be read and updated as fixed-width integers.

Code examples:

=== "Sync"
    ```
    RBitSet set = redisson.getBitSet("simpleBitset");

    set.set(0, true);          // set bit 0
    boolean bit = set.get(0);  // read bit 0
    set.clear(0);              // clear bit 0
    ```
=== "Async"
    ```
    RBitSetAsync set = redisson.getBitSet("simpleBitset");

    RFuture<Boolean> setFuture = set.setAsync(0, true);
    RFuture<Boolean> getFuture = set.getAsync(0);
    RFuture<Boolean> clearFuture = set.clearAsync(0);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive set = redisson.getBitSet("simpleBitset");

    Mono<Boolean> setMono = set.set(0, true);
    Mono<Boolean> getMono = set.get(0);
    Mono<Boolean> clearMono = set.clear(0);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx set = redisson.getBitSet("simpleBitset");

    Single<Boolean> setRx = set.set(0, true);
    Single<Boolean> getRx = set.get(0);
    Single<Boolean> clearRx = set.clear(0);
    ```

**Setting, reading, and clearing bits**

`set` turns a bit on, or to a given value, and returns the bit's previous value; `get` reads a bit; and `clear` turns a bit off. Whole ranges are addressed with the two-index overloads `set(fromIndex, toIndex)` and `clear(fromIndex, toIndex)`, and `clear()` empties the whole bitset.

=== "Sync"
    ```
    RBitSet set = redisson.getBitSet("simpleBitset");

    boolean previous = set.set(7);   // turn bit 7 on, return its previous value
    set.set(1812, false);            // set a bit to a given value
    boolean bit = set.get(7);

    set.set(0, 64);                  // turn on bits 0 through 63
    set.clear(0, 64);                // clear bits 0 through 63
    set.clear();                     // clear the whole bitset
    ```
=== "Async"
    ```
    RBitSetAsync set = redisson.getBitSet("simpleBitset");

    RFuture<Boolean> previousFuture = set.setAsync(7);
    RFuture<Void> rangeFuture = set.setAsync(0, 64);
    RFuture<Void> clearRangeFuture = set.clearAsync(0, 64);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive set = redisson.getBitSet("simpleBitset");

    Mono<Boolean> previousMono = set.set(7);
    Mono<Void> rangeMono = set.set(0, 64);
    Mono<Void> clearRangeMono = set.clear(0, 64);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx set = redisson.getBitSet("simpleBitset");

    Single<Boolean> previousRx = set.set(7);
    Completable rangeRx = set.set(0, 64);
    Completable clearRangeRx = set.clear(0, 64);
    ```

**Counting and length**

`cardinality` returns the number of bits set to one - the count of present entities, not a byte length. `length` returns the index just past the highest set bit, and `size` the number of bits the underlying value can address.

=== "Sync"
    ```
    RBitSet set = redisson.getBitSet("simpleBitset");

    long count = set.cardinality();   // number of bits set to one
    long highest = set.length();      // index past the highest set bit
    long bits = set.size();           // number of addressable bits
    ```
=== "Async"
    ```
    RBitSetAsync set = redisson.getBitSet("simpleBitset");

    RFuture<Long> countFuture = set.cardinalityAsync();
    RFuture<Long> lengthFuture = set.lengthAsync();
    RFuture<Long> sizeFuture = set.sizeAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive set = redisson.getBitSet("simpleBitset");

    Mono<Long> countMono = set.cardinality();
    Mono<Long> lengthMono = set.length();
    Mono<Long> sizeMono = set.size();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx set = redisson.getBitSet("simpleBitset");

    Single<Long> countRx = set.cardinality();
    Single<Long> lengthRx = set.length();
    Single<Long> sizeRx = set.size();
    ```

**Combining bitsets**

`and`, `or`, `xor`, and `not` combine this bitset with one or more named bitsets - intersecting or unioning sets of ids, for example - and store the result back into this bitset. They return the byte length of the resulting value, so to count the matching bits afterwards read `cardinality`.

=== "Sync"
    ```
    RBitSet set = redisson.getBitSet("activeToday");

    // intersect with another set and store the result in this bitset
    set.and("activeYesterday");

    // union, symmetric difference, and inversion
    set.or("activeYesterday");
    set.xor("activeYesterday");
    set.not();

    // the bitwise ops return a byte length; count set bits with cardinality
    long matching = set.cardinality();
    ```
=== "Async"
    ```
    RBitSetAsync set = redisson.getBitSet("activeToday");

    RFuture<Long> andFuture = set.andAsync("activeYesterday");
    RFuture<Long> orFuture = set.orAsync("activeYesterday");
    RFuture<Long> xorFuture = set.xorAsync("activeYesterday");
    RFuture<Long> notFuture = set.notAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive set = redisson.getBitSet("activeToday");

    Mono<Long> andMono = set.and("activeYesterday");
    Mono<Long> orMono = set.or("activeYesterday");
    Mono<Long> xorMono = set.xor("activeYesterday");
    Mono<Long> notMono = set.not();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx set = redisson.getBitSet("activeToday");

    Single<Long> andRx = set.and("activeYesterday");
    Single<Long> orRx = set.or("activeYesterday");
    Single<Long> xorRx = set.xor("activeYesterday");
    Single<Long> notRx = set.not();
    ```

**Bit fields as integers**

A region of the bitset can be read and updated as a fixed-width integer. `getByte`, `getShort`, `getInteger`, and `getLong` read a value at a bit offset - each with matching `set...` and `incrementAndGet...` methods - while `getSigned` and `getUnsigned` work with an arbitrary bit width. This packs many small counters into one compact structure.

=== "Sync"
    ```
    RBitSet set = redisson.getBitSet("counters");

    // treat 8 bits at offset 0 as a byte counter
    set.setByte(0, (byte) 10);
    byte b = set.getByte(0);
    set.incrementAndGetByte(0, (byte) 1);

    // a 32-bit integer at a bit offset
    set.setInteger(64, 1000);
    int i = set.getInteger(64);

    // an arbitrary-width unsigned field: 5 bits at offset 100
    set.setUnsigned(5, 100, 17);
    long u = set.getUnsigned(5, 100);
    ```
=== "Async"
    ```
    RBitSetAsync set = redisson.getBitSet("counters");

    RFuture<Byte> byteFuture = set.getByteAsync(0);
    RFuture<Long> longFuture = set.getLongAsync(0);
    RFuture<Long> signedFuture = set.getSignedAsync(5, 100);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive set = redisson.getBitSet("counters");

    Mono<Byte> byteMono = set.getByte(0);
    Mono<Long> longMono = set.getLong(0);
    Mono<Long> signedMono = set.getSigned(5, 100);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx set = redisson.getBitSet("counters");

    Single<Byte> byteRx = set.getByte(0);
    Single<Long> longRx = set.getLong(0);
    Single<Long> signedRx = set.getSigned(5, 100);
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

### Use Cases

RBitSet stores a vector of bits addressed by index, mirroring `java.util.BitSet` but living in Valkey or Redis. One bit per index makes it the most memory-dense way to keep a boolean per entity, `cardinality()` counts the set bits in a single call, and `and`/`or`/`xor` combine whole bitsets server-side. It suits boolean-per-id attributes, bitmap analytics, and progress tracking over dense integer ids.

**Per-Entity Boolean Flags**

A single boolean attribute for each entity - email verified, feature-rollout membership, subscription active - maps to one bit at the entity's numeric id. Millions of flags fit in a few hundred kilobytes, far less than a set of ids would take, and `cardinality()` answers "how many" in one call without scanning.

=== "Sync"
    ```
    RBitSet verified = redisson.getBitSet("users:email-verified");

    // mark user 42 verified, clear user 99
    verified.set(42);
    verified.clear(99);

    // check a single user
    boolean ok = verified.get(42);

    // how many users are verified
    long total = verified.cardinality();
    ```
=== "Async"
    ```
    RBitSetAsync verified = redisson.getBitSet("users:email-verified");

    RFuture<Boolean> updated = verified.setAsync(42);
    RFuture<Boolean> ok = verified.getAsync(42);
    RFuture<Long> total = verified.cardinalityAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive verified = redisson.getBitSet("users:email-verified");

    Mono<Boolean> updated = verified.set(42);
    Mono<Boolean> ok = verified.get(42);
    Mono<Long> total = verified.cardinality();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx verified = redisson.getBitSet("users:email-verified");

    Single<Boolean> updated = verified.set(42);
    Single<Boolean> ok = verified.get(42);
    Single<Long> total = verified.cardinality();
    ```

**Active-User Bitmaps and Set Algebra**

Keeping one bitset per day, where the bit index is a user id set when that user is active, turns common analytics into bit operations. The `cardinality()` of a day is its active-user count, and combining days with `or`, `and`, and `xor` yields unique users over a range, users active on every day (retention), and day-to-day changes. These operations run server-side and store their result into the destination bitset - so a separate result key keeps the daily data intact - and the count is then read with `cardinality()`.

=== "Sync"
    ```
    RBitSet mon = redisson.getBitSet("active:2026-06-01");
    RBitSet tue = redisson.getBitSet("active:2026-06-02");

    // bit index = user id; set when the user is active that day
    mon.set(userId);
    tue.set(userId);

    // daily active users
    long dau = mon.cardinality();

    // unique users across the range: OR the days into a fresh result, then count
    RBitSet union = redisson.getBitSet("active:range-union");
    union.or("active:2026-06-01", "active:2026-06-02");
    long uniqueUsers = union.cardinality();

    // users active on BOTH days (retention)
    RBitSet both = redisson.getBitSet("active:range-both");
    both.or("active:2026-06-01");      // seed the result with day one
    both.and("active:2026-06-02");     // intersect with day two
    long retained = both.cardinality();
    ```
=== "Async"
    ```
    RBitSetAsync union = redisson.getBitSet("active:range-union");

    // OR the day bitsets into this destination; the return is the byte length
    RFuture<Long> bytes = union.orAsync("active:2026-06-01", "active:2026-06-02");

    // read the unique-user count from the result
    RFuture<Long> uniqueUsers = union.cardinalityAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive union = redisson.getBitSet("active:range-union");

    // OR the day bitsets into this destination; the return is the byte length
    Mono<Long> bytes = union.or("active:2026-06-01", "active:2026-06-02");

    // read the unique-user count from the result
    Mono<Long> uniqueUsers = union.cardinality();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx union = redisson.getBitSet("active:range-union");

    // OR the day bitsets into this destination; the return is the byte length
    Single<Long> bytes = union.or("active:2026-06-01", "active:2026-06-02");

    // read the unique-user count from the result
    Single<Long> uniqueUsers = union.cardinality();
    ```

**Resumable Job Progress over Dense IDs**

A long-running pass over a dense range of integer ids - reindexing, a data migration, a recomputation - can record each completed id as a set bit. On restart the job consults `get` to skip ids already done, `cardinality()` reports how many are complete for progress reporting, and `clear()` resets the whole bitset for a fresh run.

=== "Sync"
    ```
    RBitSet done = redisson.getBitSet("job:reindex:done");

    // resume: skip ids that were already processed
    for (long id = 0; id < totalIds; id++) {
        if (done.get(id)) {
            continue;
        }
        process(id);
        done.set(id);
    }

    // progress so far
    long completed = done.cardinality();

    // reset for a fresh run
    done.clear();
    ```
=== "Async"
    ```
    RBitSetAsync done = redisson.getBitSet("job:reindex:done");

    // skip on resume / mark complete
    RFuture<Boolean> alreadyDone = done.getAsync(id);
    RFuture<Boolean> marked = done.setAsync(id);

    // progress count
    RFuture<Long> completed = done.cardinalityAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBitSetReactive done = redisson.getBitSet("job:reindex:done");

    Mono<Boolean> alreadyDone = done.get(id);
    Mono<Boolean> marked = done.set(id);
    Mono<Long> completed = done.cardinality();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RBitSetRx done = redisson.getBitSet("job:reindex:done");

    Single<Boolean> alreadyDone = done.get(id);
    Single<Boolean> marked = done.set(id);
    Single<Long> completed = done.cardinality();
    ```

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

{% include 'data-and-services/bloom-filter-native.md' %}

{% include 'data-and-services/cuckoo-filter.md' %}


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


## RateLimiter
Java implementation of Valkey or Redis based [RRateLimiter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRateLimiter.html) object restricts how often an operation may run across a cluster. A rate is configured as a number of permits per time interval; each call consumes one or more permits, and once an interval's permits are used up, further calls block or fail until permits become available again. It does not guarantee fairness, and the object is thread-safe.

The scope is chosen with `RateType`: `OVERALL` limits the combined rate across every thread and every Redisson instance that shares the limiter, while `PER_CLIENT` applies the limit separately to each Redisson instance. Permits are taken either by blocking until they are free or by trying without blocking, optionally up to a timeout.

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
    RRateLimiterRx limiter = redisson.getRateLimiter("myLimiter");

    Single<Boolean> setRateRx = limiter.trySetRate(RateType.OVERALL, 5, 2, RateIntervalUnit.SECONDS);
    Completable acquireRx = limiter.acquire(3);
    ```

**Configuring the rate**

`trySetRate` configures the limiter the first time and returns `false` if a rate was already set, while `setRate` always overwrites it. The arguments choose the scope (`RateType.OVERALL` or `RateType.PER_CLIENT`) and the number of permits per interval, and an overload taking a `keepAliveTime` lets an idle limiter expire on its own - useful for short-lived per-user limiters.

=== "Sync"
    ```
    RRateLimiter limiter = redisson.getRateLimiter("myLimiter");

    // configure once: 100 permits per minute across the whole cluster
    boolean configured = limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.MINUTES);

    // overwrite the existing rate
    limiter.setRate(RateType.OVERALL, 200, 1, RateIntervalUnit.MINUTES);

    // per-client limit that expires after 10 minutes idle
    limiter.trySetRate(RateType.PER_CLIENT, 20, Duration.ofSeconds(1), Duration.ofMinutes(10));
    ```
=== "Async"
    ```
    RRateLimiterAsync limiter = redisson.getRateLimiter("myLimiter");

    RFuture<Boolean> configuredFuture = limiter.trySetRateAsync(RateType.OVERALL, 100, 1, RateIntervalUnit.MINUTES);
    RFuture<Void> overwriteFuture = limiter.setRateAsync(RateType.OVERALL, 200, 1, RateIntervalUnit.MINUTES);
    RFuture<Boolean> perClientFuture = limiter.trySetRateAsync(RateType.PER_CLIENT, 20, Duration.ofSeconds(1), Duration.ofMinutes(10));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive limiter = redisson.getRateLimiter("myLimiter");

    Mono<Boolean> configuredMono = limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.MINUTES);
    Mono<Void> overwriteMono = limiter.setRate(RateType.OVERALL, 200, 1, RateIntervalUnit.MINUTES);
    Mono<Boolean> perClientMono = limiter.trySetRate(RateType.PER_CLIENT, 20, Duration.ofSeconds(1), Duration.ofMinutes(10));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx limiter = redisson.getRateLimiter("myLimiter");

    Single<Boolean> configuredRx = limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.MINUTES);
    Single<Void> overwriteRx = limiter.setRate(RateType.OVERALL, 200, 1, RateIntervalUnit.MINUTES);
    Single<Boolean> perClientRx = limiter.trySetRate(RateType.PER_CLIENT, 20, Duration.ofSeconds(1), Duration.ofMinutes(10));
    ```

**Acquiring permits by blocking**

`acquire` takes one permit, or `acquire(permits)` takes several, blocking the calling thread until they are available. This paces work to the configured rate without the caller having to handle rejection.

=== "Sync"
    ```
    RRateLimiter limiter = redisson.getRateLimiter("myLimiter");

    limiter.acquire();      // block until one permit is free
    limiter.acquire(3);     // block until three permits are free
    ```
=== "Async"
    ```
    RRateLimiterAsync limiter = redisson.getRateLimiter("myLimiter");

    RFuture<Void> oneFuture = limiter.acquireAsync();
    RFuture<Void> manyFuture = limiter.acquireAsync(3);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive limiter = redisson.getRateLimiter("myLimiter");

    Mono<Void> oneMono = limiter.acquire();
    Mono<Void> manyMono = limiter.acquire(3);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx limiter = redisson.getRateLimiter("myLimiter");

    Completable oneRx = limiter.acquire();
    Completable manyRx = limiter.acquire(3);
    ```

**Acquiring permits without blocking**

`tryAcquire` returns immediately - `true` if a permit was taken, `false` if none was available - the basis for fast-fail admission control. The timeout overloads `tryAcquire(timeout, unit)` and `tryAcquire(permits, timeout, unit)` wait up to the given time for permits before giving up.

=== "Sync"
    ```
    RRateLimiter limiter = redisson.getRateLimiter("myLimiter");

    // take a permit if one is free right now, otherwise return false
    boolean acquired = limiter.tryAcquire();

    // take several permits if available
    boolean acquiredMany = limiter.tryAcquire(3);

    // wait up to 5 seconds for a permit
    boolean waited = limiter.tryAcquire(1, 5, TimeUnit.SECONDS);
    ```
=== "Async"
    ```
    RRateLimiterAsync limiter = redisson.getRateLimiter("myLimiter");

    RFuture<Boolean> acquiredFuture = limiter.tryAcquireAsync();
    RFuture<Boolean> manyFuture = limiter.tryAcquireAsync(3);
    RFuture<Boolean> waitedFuture = limiter.tryAcquireAsync(1, 5, TimeUnit.SECONDS);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive limiter = redisson.getRateLimiter("myLimiter");

    Mono<Boolean> acquiredMono = limiter.tryAcquire();
    Mono<Boolean> manyMono = limiter.tryAcquire(3);
    Mono<Boolean> waitedMono = limiter.tryAcquire(1, 5, TimeUnit.SECONDS);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx limiter = redisson.getRateLimiter("myLimiter");

    Single<Boolean> acquiredRx = limiter.tryAcquire();
    Single<Boolean> manyRx = limiter.tryAcquire(3);
    Single<Boolean> waitedRx = limiter.tryAcquire(1, 5, TimeUnit.SECONDS);
    ```

**Inspecting the limiter**

`availablePermits` reports how many permits remain in the current interval. `getConfig` returns the configured `RateType`, rate, and interval as a `RateLimiterConfig` (available on the synchronous and asynchronous interfaces).

=== "Sync"
    ```
    RRateLimiter limiter = redisson.getRateLimiter("myLimiter");

    long remaining = limiter.availablePermits();

    RateLimiterConfig config = limiter.getConfig();
    RateType type = config.getRateType();
    Long rate = config.getRate();
    Long interval = config.getRateInterval();
    ```
=== "Async"
    ```
    RRateLimiterAsync limiter = redisson.getRateLimiter("myLimiter");

    RFuture<Long> remainingFuture = limiter.availablePermitsAsync();
    RFuture<RateLimiterConfig> configFuture = limiter.getConfigAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive limiter = redisson.getRateLimiter("myLimiter");

    Mono<Long> remainingMono = limiter.availablePermits();
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx limiter = redisson.getRateLimiter("myLimiter");

    Single<Long> remainingRx = limiter.availablePermits();
    ```

### Use Cases

The RateLimiter caps how often an operation may run across a cluster, reimplementing Guava's RateLimiter on top of Valkey or Redis so the limit holds for every thread and every application instance sharing the limiter's name. It can block until a permit frees up or fail fast when none is available, permits may be acquired in batches to weight heavier operations, and the rate can be scoped globally with `RateType.OVERALL` or to each Redisson instance with `RateType.PER_CLIENT`.

**Pacing Background and Outbound Work**

Batch jobs, crawlers, and producers that feed a downstream system often need to run at a steady, capped throughput rather than as fast as the loop allows. A blocking `acquire()` paces the work: the rate is configured once with `trySetRate`, then each unit of work waits for a permit before proceeding, smoothing a bursty loop into a fixed rate. Acquiring several permits at once charges heavier work proportionally, and with `RateType.OVERALL` the cap holds no matter how many worker instances run.

=== "Sync"
    ```
    RRateLimiter limiter = redisson.getRateLimiter("outbound:emails");

    // configure once: 100 operations per second across all workers
    limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.SECONDS);

    for (EmailBatch batch : batches) {
        // block until enough permits are free, charging by batch size
        limiter.acquire(batch.size());
        send(batch);
    }
    ```
=== "Async"
    ```
    RRateLimiterAsync limiter = redisson.getRateLimiter("outbound:emails");

    RFuture<Boolean> rate = limiter.trySetRateAsync(RateType.OVERALL, 100, 1, RateIntervalUnit.SECONDS);

    // completes once the requested permits are available
    RFuture<Void> acquired = limiter.acquireAsync(10);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive limiter = redisson.getRateLimiter("outbound:emails");

    Mono<Boolean> rate = limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.SECONDS);

    // completes once the requested permits are available
    Mono<Void> acquired = limiter.acquire(10);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx limiter = redisson.getRateLimiter("outbound:emails");

    Single<Boolean> rate = limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.SECONDS);

    // completes once the requested permits are available
    Completable acquired = limiter.acquire(10);
    ```

**Fast-Fail Admission Control**

When excess load should be rejected rather than queued, `tryAcquire()` returns immediately: `false` means no permit was available, so the request can be shed (for example with HTTP 429) instead of piling up. When a short wait is acceptable, `tryAcquire(Duration)` blocks only up to the given timeout before giving up, which bounds tail latency, and `availablePermits()` exposes how much headroom remains.

=== "Sync"
    ```
    RRateLimiter limiter = redisson.getRateLimiter("api:search");
    limiter.trySetRate(RateType.OVERALL, 50, 1, RateIntervalUnit.SECONDS);

    // reject immediately when no permit is free
    if (!limiter.tryAcquire()) {
        rejectWith(429);
        return;
    }
    handleRequest();

    // or wait briefly before giving up, to absorb short spikes
    boolean admitted = limiter.tryAcquire(Duration.ofMillis(200));

    // remaining headroom
    long remaining = limiter.availablePermits();
    ```
=== "Async"
    ```
    RRateLimiterAsync limiter = redisson.getRateLimiter("api:search");

    // true if a permit was free, false to shed load
    RFuture<Boolean> admitted = limiter.tryAcquireAsync();

    // remaining headroom
    RFuture<Long> remaining = limiter.availablePermitsAsync();
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive limiter = redisson.getRateLimiter("api:search");

    // true if a permit was free, false to shed load
    Mono<Boolean> admitted = limiter.tryAcquire();

    // wait briefly before giving up
    Mono<Boolean> admittedWithWait = limiter.tryAcquire(Duration.ofMillis(200));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx limiter = redisson.getRateLimiter("api:search");

    // true if a permit was free, false to shed load
    Single<Boolean> admitted = limiter.tryAcquire();

    // wait briefly before giving up
    Single<Boolean> admittedWithWait = limiter.tryAcquire(Duration.ofMillis(200));
    ```

**Global vs Per-Instance Limits**

The same limiter serves two different scopes. `RateType.OVERALL` enforces a single ceiling shared by every application instance, which is the right choice for protecting a resource with a fixed global budget such as a metered third-party API or a database that must not exceed a total query rate, however many app servers are running. `RateType.PER_CLIENT` instead gives each Redisson instance (typically each JVM) its own allowance, spreading capacity across instances so one busy node cannot consume the whole budget.

=== "Sync"
    ```
    // OVERALL: 1000 calls per second shared across the entire deployment
    RRateLimiter global = redisson.getRateLimiter("vendor-api");
    global.trySetRate(RateType.OVERALL, 1000, 1, RateIntervalUnit.SECONDS);
    global.acquire();

    // PER_CLIENT: 1000 calls per second for each JVM independently
    RRateLimiter perJvm = redisson.getRateLimiter("local-disk-io");
    perJvm.trySetRate(RateType.PER_CLIENT, 1000, 1, RateIntervalUnit.SECONDS);
    perJvm.acquire();
    ```
=== "Async"
    ```
    RRateLimiterAsync global = redisson.getRateLimiter("vendor-api");

    // one ceiling shared by every instance
    RFuture<Boolean> rate = global.trySetRateAsync(RateType.OVERALL, 1000, 1, RateIntervalUnit.SECONDS);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive perJvm = redisson.getRateLimiter("local-disk-io");

    // a separate allowance per Redisson instance
    Mono<Boolean> rate = perJvm.trySetRate(RateType.PER_CLIENT, 1000, 1, RateIntervalUnit.SECONDS);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx perJvm = redisson.getRateLimiter("local-disk-io");

    // a separate allowance per Redisson instance
    Single<Boolean> rate = perJvm.trySetRate(RateType.PER_CLIENT, 1000, 1, RateIntervalUnit.SECONDS);
    ```

**Per-User Limiters with Automatic Cleanup**

Applying a separate limit to each user, tenant, or API key means one limiter per entity, with the id encoded in the limiter name. Left unmanaged, those keys accumulate as the set of seen entities grows. The `trySetRate` overload that accepts a `keepAliveTime` bounds this: once a limiter goes unused for that period it is removed on the Valkey or Redis side, so only active entities retain state. Because `trySetRate` applies the rate only when it has not been set already, calling it on every request is safe and idempotent.

=== "Sync"
    ```
    // one limiter per user - the id is encoded in the key
    RRateLimiter limiter = redisson.getRateLimiter("ratelimit:user:" + userId);

    // 20 requests per minute per user; reclaim the limiter after 1 hour idle.
    // trySetRate sets the rate only the first time, so this is safe per request
    limiter.trySetRate(RateType.OVERALL, 20, Duration.ofMinutes(1), Duration.ofHours(1));

    if (!limiter.tryAcquire()) {
        rejectWith(429);
        return;
    }
    handleRequest();
    ```
=== "Async"
    ```
    RRateLimiterAsync limiter = redisson.getRateLimiter("ratelimit:user:" + userId);

    // set the rate once; the limiter is reclaimed after 1 hour without acquisitions
    RFuture<Boolean> rate = limiter.trySetRateAsync(RateType.OVERALL, 20, Duration.ofMinutes(1), Duration.ofHours(1));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RRateLimiterReactive limiter = redisson.getRateLimiter("ratelimit:user:" + userId);

    // set the rate once; the limiter is reclaimed after 1 hour without acquisitions
    Mono<Boolean> rate = limiter.trySetRate(RateType.OVERALL, 20, Duration.ofMinutes(1), Duration.ofHours(1));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RRateLimiterRx limiter = redisson.getRateLimiter("ratelimit:user:" + userId);

    // set the rate once; the limiter is reclaimed after 1 hour without acquisitions
    Single<Boolean> rate = limiter.trySetRate(RateType.OVERALL, 20, Duration.ofMinutes(1), Duration.ofHours(1));
    ```

## GCRA Rate Limiter
Java implementation of Redis based [RGcra](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RGcra.html) object is a rate limiter based on the [Generic Cell Rate Algorithm](https://en.wikipedia.org/wiki/Generic_cell_rate_algorithm). It restricts the rate of operations using a burst capacity and a token replenishment rate. State is stored in a single Redis key, so the limit applies across all threads regardless of the Redisson instance. This object is thread-safe.

Requires Redis 8.8.0 or higher.

Each call to `tryAcquire()` requests one or more tokens and returns a `GcraResult` with the following information:

* `isLimited()` - `true` if the requested tokens couldn't be acquired
* `getMaxTokens()` - maximum burst capacity
* `getAvailableTokens()` - tokens currently available
* `getRetryAfterSeconds()` - seconds to wait before the requested tokens can be acquired; `-1` when the call wasn't limited
* `getFullBurstAfterSeconds()` - seconds to wait before full burst capacity is restored

Code example:

=== "Sync"
    ```
    RGcra gcra = redisson.getGcra("myLimiter");

    // up to 4 tokens per second, with burst of 2 additional tokens
    GcraResult result = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
    if (!result.isLimited()) {
        // request accepted
    }

    // acquire 3 tokens at once
    GcraResult batch = gcra.tryAcquire(2, 4, Duration.ofSeconds(1), 3);
    ```

=== "Async"
    ```
    RGcraAsync gcra = redisson.getGcra("myLimiter");

    RFuture<GcraResult> resultFuture = gcra.tryAcquireAsync(2, 4, Duration.ofSeconds(1));
    RFuture<GcraResult> batchFuture = gcra.tryAcquireAsync(2, 4, Duration.ofSeconds(1), 3);
    ```

=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGcraReactive gcra = redisson.getGcra("myLimiter");

    Mono<GcraResult> resultMono = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
    Mono<GcraResult> batchMono = gcra.tryAcquire(2, 4, Duration.ofSeconds(1), 3);
    ```

=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGcraRx gcra = redisson.getGcra("myLimiter");

    Single<GcraResult> resultRx = gcra.tryAcquire(2, 4, Duration.ofSeconds(1));
    Single<GcraResult> batchRx = gcra.tryAcquire(2, 4, Duration.ofSeconds(1), 3);
    ```

### Use Cases

The GCRA rate limiter enforces a smooth request rate with a configurable burst allowance, keeping all state in a single Redis key so one limit applies across every thread and Redisson instance. Each `tryAcquire` call is non-blocking and returns a `GcraResult` that reports whether the call was limited and, when it was, how long to wait. That makes it a fit for guarding APIs, pacing calls to downstream services, and enforcing per-client quotas.

**Per-Client API Rate Limiting**

API gateways limit each caller independently and tell a throttled client when to come back. Encoding the client id in the limiter name gives one limiter per API key, user, or IP. On each request a single token is requested, and the `GcraResult` supplies everything needed for the response: `getRetryAfterSeconds()` for the HTTP `Retry-After` header on a 429, and `getMaxTokens()`/`getAvailableTokens()` for `X-RateLimit-*` headers.

=== "Sync"
    ```
    // one limiter per API key - state lives in a single Redis key per client
    RGcra gcra = redisson.getGcra("ratelimit:" + apiKey);

    // up to 100 requests per second per client, with a burst of 50 additional
    GcraResult result = gcra.tryAcquire(50, 100, Duration.ofSeconds(1));

    if (result.isLimited()) {
        // reject with HTTP 429 and tell the client when to retry
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
    } else {
        // surface remaining quota, then handle the request
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getMaxTokens()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getAvailableTokens()));
    }
    ```
=== "Async"
    ```
    RGcraAsync gcra = redisson.getGcra("ratelimit:" + apiKey);

    // isLimited(), getRetryAfterSeconds() and getAvailableTokens() read off the result
    RFuture<GcraResult> result = gcra.tryAcquireAsync(50, 100, Duration.ofSeconds(1));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGcraReactive gcra = redisson.getGcra("ratelimit:" + apiKey);

    // isLimited(), getRetryAfterSeconds() and getAvailableTokens() read off the result
    Mono<GcraResult> result = gcra.tryAcquire(50, 100, Duration.ofSeconds(1));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGcraRx gcra = redisson.getGcra("ratelimit:" + apiKey);

    // isLimited(), getRetryAfterSeconds() and getAvailableTokens() read off the result
    Single<GcraResult> result = gcra.tryAcquire(50, 100, Duration.ofSeconds(1));
    ```

**Protecting Downstream Dependencies**

When calling a fragile third-party API, payment provider, or email/SMS gateway that enforces its own limits, the caller paces itself to stay under that limit while still letting short spikes through. A `tryAcquire` precedes each outbound call; when the result is limited, the work is deferred by `getRetryAfterSeconds()` instead of hammering the dependency, and the burst allowance absorbs brief surges without dropping requests.

=== "Sync"
    ```
    RGcra gcra = redisson.getGcra("downstream:payments-api");

    // the provider tolerates ~10 calls per second; permit a small burst of 5
    GcraResult result = gcra.tryAcquire(5, 10, Duration.ofSeconds(1));

    if (result.isLimited()) {
        // pace ourselves - schedule a retry once tokens replenish
        long retryAfter = result.getRetryAfterSeconds();
        scheduleRetry(retryAfter);
    } else {
        callPaymentsApi();
    }
    ```
=== "Async"
    ```
    RGcraAsync gcra = redisson.getGcra("downstream:payments-api");

    // when limited, defer the call by result.getRetryAfterSeconds()
    RFuture<GcraResult> result = gcra.tryAcquireAsync(5, 10, Duration.ofSeconds(1));
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGcraReactive gcra = redisson.getGcra("downstream:payments-api");

    // when limited, defer the call by result.getRetryAfterSeconds()
    Mono<GcraResult> result = gcra.tryAcquire(5, 10, Duration.ofSeconds(1));
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGcraRx gcra = redisson.getGcra("downstream:payments-api");

    // when limited, defer the call by result.getRetryAfterSeconds()
    Single<GcraResult> result = gcra.tryAcquire(5, 10, Duration.ofSeconds(1));
    ```

**Weighted, Cost-Based Quotas**

Not every operation costs the same: a bulk export, a large upload, or a heavy query consumes more of a budget than a single cheap read. The multi-token form of `tryAcquire` charges a variable cost per call against one shared limit, so a single limiter enforces a unified budget regardless of the mix of operations.

=== "Sync"
    ```
    RGcra gcra = redisson.getGcra("quota:" + accountId);

    // charge each request by its cost (rows exported, payload size, ...)
    int cost = estimateCost(request);

    // budget of 1000 tokens per minute, with a burst of 200
    GcraResult result = gcra.tryAcquire(200, 1000, Duration.ofMinutes(1), cost);

    if (result.isLimited()) {
        // not enough budget right now - retry after result.getRetryAfterSeconds()
    } else {
        // budget charged for `cost` tokens - proceed
    }
    ```
=== "Async"
    ```
    RGcraAsync gcra = redisson.getGcra("quota:" + accountId);

    int cost = estimateCost(request);
    RFuture<GcraResult> result = gcra.tryAcquireAsync(200, 1000, Duration.ofMinutes(1), cost);
    ```
=== "Reactive"
    ```
    RedissonReactiveClient redisson = redissonClient.reactive();
    RGcraReactive gcra = redisson.getGcra("quota:" + accountId);

    int cost = estimateCost(request);
    Mono<GcraResult> result = gcra.tryAcquire(200, 1000, Duration.ofMinutes(1), cost);
    ```
=== "RxJava3"
    ```
    RedissonRxClient redisson = redissonClient.rxJava();
    RGcraRx gcra = redisson.getGcra("quota:" + accountId);

    int cost = estimateCost(request);
    Single<GcraResult> result = gcra.tryAcquire(200, 1000, Duration.ofMinutes(1), cost);
    ```
