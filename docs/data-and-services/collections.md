## Map
Java implementation of Valkey or Redis based [Map](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMap.html) object for Java implements [ConcurrentMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html) interface. This object is thread-safe. Consider to use [Live Object service](services.md/#live-object-service) to store POJO object as Valkey or Redis Map. Valkey or Redis uses serialized state to check key uniqueness instead of key's `hashCode()`/`equals()` methods.

If Map used mostly for read operations and/or network roundtrips are undesirable use Map with [Local cache](#eviction-local-cache-and-data-partitioning) support.

Code examples:

```java
RMap<String, SomeObject> map = redisson.getMap("anyMap");
SomeObject prevObject = map.put("123", new SomeObject());
SomeObject currentObject = map.putIfAbsent("323", new SomeObject());
SomeObject obj = map.remove("123");

// use fast* methods when previous value is not required
map.fastPut("a", new SomeObject());
map.fastPutIfAbsent("d", new SomeObject());
map.fastRemove("b");

RFuture<SomeObject> putAsyncFuture = map.putAsync("321");
RFuture<Void> fastPutAsyncFuture = map.fastPutAsync("321");

map.fastPutAsync("321", new SomeObject());
map.fastRemoveAsync("321");
```
RMap object allows to bind a [Lock](locks-and-synchronizers.md/#lock)/[ReadWriteLock](locks-and-synchronizers.md/#readwritelock)/[Semaphore](locks-and-synchronizers.md/#semaphore)/[CountDownLatch](locks-and-synchronizers.md/#countdownlatch) object per key:
```java
RMap<MyKey, MyValue> map = redisson.getMap("anyMap");
MyKey k = new MyKey();
RLock keyLock = map.getLock(k);
keyLock.lock();
try {
   MyValue v = map.get(k);
   // process value ...
} finally {
   keyLock.unlock();
}

RReadWriteLock rwLock = map.getReadWriteLock(k);
rwLock.readLock().lock();
try {
   MyValue v = map.get(k);
   // process value ...
} finally {
   keyLock.readLock().unlock();
}
```

### Eviction, local cache and data partitioning

Redisson provides various Map structure implementations with multiple important features:  

**local cache** - so called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state. It's recommended to use each local cached instance as a singleton per unique name since it has own state for local cache.

**data partitioning** - although any Map object is cluster compatible its content isn't scaled/partitioned across multiple master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in cluster.  

**1. No eviction** 

Each object implements [RMap](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMap.html), [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapRx.html) interfaces.

Available implementations:

|RedissonClient<br/>method name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :-----------:| :---------:|
|getMap()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|getLocalCachedMap()<br/><sub><i>open-source version</i></sub> | ✔️ | ❌ | ❌ |
|getMap()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
|getLocalCachedMap()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ✔️ | ❌ | ✔️ |
|getClusteredMap()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
|getClusteredLocalCachedMap()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |
<br/>

**2. Scripted eviction** 

Allows to define `time to live` or `max idle time` parameters per map entry. Eviction is done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Valkey or Redis calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

Each object implements [RMapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCache.html), [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheRx.html) interfaces.

Available implementations:

|RedissonClient<br/>method name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :-----------:| :---------:|
|getMapCache()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|getMapCache()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
|getLocalCachedMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ✔️ |
|getClusteredMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
|getClusteredLocalCachedMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |
<br/>

**3. Advanced eviction**

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Valkey or Redis side.

Each object implements [RMapCacheV2](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheV2.html), [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheV2Async.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheV2Reactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheV2Rx.html) interfaces.

Available implementations:

|RedissonClient<br/>method name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :-----------:| :---------:|
|getMapCacheV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
|getLocalCachedMapCacheV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |
<br/>

**4. Native eviction**

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Valkey or Redis side.  
Requires **Valkey 9.0+** or **Redis 7.4+**.

Each object implements [RMapCacheNative](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheNative.html), [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheNativeAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheNativeReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheNativeRx.html) interfaces.

Available implementations:

|RedissonClient<br/>method name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write | Size limit |
| ------------- | :-----------: | :-----------:| :---------:| :---------:|
|getMapCacheNative()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ | ❌ | ❌ |
|getMapCacheNative()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ | ❌ | ❌ |
|getLocalCachedMapCacheNative()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ✔️ | ❌ | ❌ |
|getClusteredMapCacheNative()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ | ❌ | ❌ |
|getClusteredLocalCachedMapCacheNative()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ | ❌ | ❌ |
|getMapCacheNativeV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ❌ | ✔️ | ✔️ | ✔️ |
|getLocalCachedMapCacheNativeV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ✔️ | ✔️ | ✔️ |
|getClusteredMapCacheNativeV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ | ✔️ | ✔️ |
|getClusteredLocalCachedMapCacheNativeV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ |

Redisson also provides various [Cache API](../cache-API-implementations.md) implementations.

Code example:
```java
RMapCache<String, SomeObject> map = redisson.getMapCache("anyMap");
// or
RMapCache<String, SomeObject> map = redisson.getMapCache("anyMap", MapCacheOptions.defaults());
// or
RMapCacheV2<String, SomeObject> map = redisson.getMapCacheV2("anyMap");
// or
RMapCacheV2<String, SomeObject> map = redisson.getMapCacheV2("anyMap", MapOptions.defaults());
// or
RMapCache<String, SomeObject> map = redisson.getClusteredMapCache("anyMap");
// or
RMapCache<String, SomeObject> map = redisson.getClusteredMapCache("anyMap", MapCacheOptions.defaults());


// ttl = 10 minutes, 
map.put("key1", new SomeObject(), 10, TimeUnit.MINUTES);
// ttl = 10 minutes, maxIdleTime = 10 seconds
map.put("key1", new SomeObject(), 10, TimeUnit.MINUTES, 10, TimeUnit.SECONDS);

// ttl = 3 seconds
map.putIfAbsent("key2", new SomeObject(), 3, TimeUnit.SECONDS);
// ttl = 40 seconds, maxIdleTime = 10 seconds
map.putIfAbsent("key2", new SomeObject(), 40, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);

// if object is not used anymore
map.destroy();
```

**Local cache**  

Map object with local cache support implements [RLocalCachedMap](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLocalCachedMap.html) or [RLocalCachedMapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLocalCachedMapCache.html) which extends [ConcurrentMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html) interface. This object is thread-safe.

Follow options can be supplied during object creation:
```java
      LocalCachedMapOptions options = LocalCachedMapOptions.defaults()

      // Defines whether to store a cache miss into the local cache.
      // Default value is false.
      .storeCacheMiss(false);

      // Defines store mode of cache data.
      // Follow options are available:
      // LOCALCACHE - store data in local cache only and use Valkey or Redis only for data update/invalidation.
      // LOCALCACHE_REDIS - store data in both Valkey or Redis and local cache.
      .storeMode(StoreMode.LOCALCACHE_REDIS)

      // Defines Cache provider used as local cache store.
      // Follow options are available:
      // REDISSON - uses Redisson own implementation
      // CAFFEINE - uses Caffeine implementation
      .cacheProvider(CacheProvider.REDISSON)

      // Defines local cache eviction policy.
      // Follow options are available:
      // LFU - Counts how often an item was requested. Those that are used least often are discarded first.
      // LRU - Discards the least recently used items first
      // SOFT - Uses soft references, entries are removed by GC
      // WEAK - Uses weak references, entries are removed by GC
      // NONE - No eviction
     .evictionPolicy(EvictionPolicy.NONE)

      // If cache size is 0 then local cache is unbounded.
     .cacheSize(1000)

      // Defines strategy for load missed local cache updates after connection failure.
      //
      // Follow reconnection strategies are available:
      // CLEAR - Clear local cache if map instance has been disconnected for a while.
      // LOAD - Store invalidated entry hash in invalidation log for 10 minutes
      //        Cache keys for stored invalidated entry hashes will be removed 
      //        if LocalCachedMap instance has been disconnected less than 10 minutes
      //        or whole cache will be cleaned otherwise.
      // NONE - Default. No reconnection handling
     .reconnectionStrategy(ReconnectionStrategy.NONE)

      // Defines local cache synchronization strategy.
      //
      // Follow sync strategies are available:
      // INVALIDATE - Default. Invalidate cache entry across all LocalCachedMap instances on map entry change
      // UPDATE - Insert/update cache entry across all LocalCachedMap instances on map entry change
      // NONE - No synchronizations on map changes
     .syncStrategy(SyncStrategy.INVALIDATE)

      // time to live for each entry in local cache
     .timeToLive(Duration.ofSeconds(10))

      // max idle time for each map entry in local cache
     .maxIdle(Duration.ofSeconds(10))

     // Defines how to listen expired event sent by Valkey or Redis upon this instance deletion
     //
     // Follow expiration policies are available:
     // DONT_SUBSCRIBE - Don't subscribe on expire event
     // SUBSCRIBE_WITH_KEYEVENT_PATTERN - Subscribe on expire event using `__keyevent@*:expired` pattern
     // SUBSCRIBE_WITH_KEYSPACE_CHANNEL - Subscribe on expire event using `__keyspace@N__:name` channel
     .expirationEventPolicy(ExpirationEventPolicy.SUBSCRIBE_WITH_KEYEVENT_PATTERN);
```

!!! warning

    It's recommended to use a single instance of local cached Map instance per unique name for each Redisson instance. Same `LocalCachedMapOptions` object should be used across all instances with the same name.

Code example:

```java
RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
// or
RLocalCachedMap<String, SomeObject> map = redisson.getLocalCachedMapCache("anyMap", LocalCachedMapCacheOptions.defaults());
// or
RLocalCachedMap<String, SomeObject> map = redisson.getClusteredLocalCachedMapCache("anyMap", LocalCachedMapCacheOptions.defaults());
// or
RLocalCachedMap<String, SomeObject> map = redisson.getClusteredLocalCachedMap("anyMap", LocalCachedMapOptions.defaults());

        
String prevObject = map.put("123", 1);
String currentObject = map.putIfAbsent("323", 2);
String obj = map.remove("123");

// use fast* methods when previous value is not required
map.fastPut("a", 1);
map.fastPutIfAbsent("d", 32);
map.fastRemove("b");

RFuture<String> putAsyncFuture = map.putAsync("321");
RFuture<Void> fastPutAsyncFuture = map.fastPutAsync("321");

map.fastPutAsync("321", new SomeObject());
map.fastRemoveAsync("321");
```

Object should be destroyed if it not used anymore, but it's not necessary to call destroy method if Redisson goes shutdown.
```java
RLocalCachedMap<String, Integer> map = ...
map.destroy();
```


**How to load data and avoid invalidation messages traffic.**

Code example:

```java
    public void loadData(String cacheName, Map<String, String> data) {
        RLocalCachedMap<String, String> clearMap = redisson.getLocalCachedMap(cacheName, 
                LocalCachedMapOptions.defaults().cacheSize(1).syncStrategy(SyncStrategy.INVALIDATE));
        RLocalCachedMap<String, String> loadMap = redisson.getLocalCachedMap(cacheName, 
                LocalCachedMapOptions.defaults().cacheSize(1).syncStrategy(SyncStrategy.NONE));
        
        loadMap.putAll(data);
        clearMap.clearLocalCache();
    }
```

**Data partitioning**

Map object with data partitioning support implements `org.redisson.api.RClusteredMap` which extends `java.util.concurrent.ConcurrentMap` interface. Read more details about data partitioning [here](data-partitioning.md).

Code example:

```java
RClusteredMap<String, SomeObject> map = redisson.getClusteredMap("anyMap");
// or
RClusteredMap<String, SomeObject> map = redisson.getClusteredLocalCachedMapCache("anyMap", LocalCachedMapCacheOptions.defaults());
// or
RClusteredMap<String, SomeObject> map = redisson.getClusteredLocalCachedMap("anyMap", LocalCachedMapOptions.defaults());
// or
RClusteredMap<String, SomeObject> map = redisson.getClusteredMapCache("anyMap");

SomeObject prevObject = map.put("123", new SomeObject());
SomeObject currentObject = map.putIfAbsent("323", new SomeObject());
SomeObject obj = map.remove("123");

map.fastPut("321", new SomeObject());
map.fastRemove("321");
```

### Persistence

Redisson allows to store Map data in external storage along with Valkey or Redis store.  
Use cases:

1. Redisson Map object as a cache between an application and external storage.
2. Increase durability of Redisson Map data and life-span of evicted entries.
3. Caching for databases, web services or any other data source.

**Read-through strategy**

If requested entry doesn't exist in the Redisson Map object 
when it will be loaded using provided [MapLoader](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/map/MapLoader.html) object. Code example:

```java
        MapLoader<String, String> mapLoader = new MapLoader<String, String>() {
            
            @Override
            public Iterable<String> loadAllKeys() {
                List<String> list = new ArrayList<String>();
                Statement statement = conn.createStatement();
                try {
                    ResultSet result = statement.executeQuery("SELECT id FROM student");
                    while (result.next()) {
                        list.add(result.getString(1));
                    }
                } finally {
                    statement.close();
                }

                return list;
            }
            
            @Override
            public String load(String key) {
                PreparedStatement preparedStatement = conn.prepareStatement("SELECT name FROM student where id = ?");
                try {
                    preparedStatement.setString(1, key);
                    ResultSet result = preparedStatement.executeQuery();
                    if (result.next()) {
                        return result.getString(1);
                    }
                    return null;
                } finally {
                    preparedStatement.close();
                }
            }
        };
```
Configuration example:
```java
MapOptions<K, V> options = MapOptions.<K, V>defaults()
                              .loader(mapLoader);

MapCacheOptions<K, V> mcoptions = MapCacheOptions.<K, V>defaults()
                              .loader(mapLoader);


RMap<K, V> map = redisson.getMap("test", options);
// or
RMapCache<K, V> map = redisson.getMapCache("test", mcoptions);
// or with performance boost up to 45x times 
RLocalCachedMap<K, V> map = redisson.getLocalCachedMap("test", options);
// or with performance boost up to 45x times 
RLocalCachedMapCache<K, V> map = redisson.getLocalCachedMapCache("test", mcoptions);
```

**Write-through (synchronous) strategy**

When the Map entry is being updated method won't return until 
Redisson update it in an external storage using [MapWriter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/map/MapWriter.html) object. Code example:

```java
        MapWriter<String, String> mapWriter = new MapWriter<String, String>() {
            
            @Override
            public void write(Map<String, String> map) {
                PreparedStatement preparedStatement = conn.prepareStatement("INSERT INTO student (id, name) values (?, ?)");
                try {
                    for (Entry<String, String> entry : map.entrySet()) {
                        preparedStatement.setString(1, entry.getKey());
                        preparedStatement.setString(2, entry.getValue());
                        preparedStatement.addBatch();
                    }
                    preparedStatement.executeBatch();
                } finally {
                    preparedStatement.close();
                }
            }
            
            @Override
            public void delete(Collection<String> keys) {
                PreparedStatement preparedStatement = conn.prepareStatement("DELETE FROM student where id = ?");
                try {
                    for (String key : keys) {
                        preparedStatement.setString(1, key);
                        preparedStatement.addBatch();
                    }
                    preparedStatement.executeBatch();
                } finally {
                    preparedStatement.close();
                }
            }
        };
```
Configuration example:
```java
MapOptions<K, V> options = MapOptions.<K, V>defaults()
                              .writer(mapWriter)
                              .writeMode(WriteMode.WRITE_THROUGH);

MapCacheOptions<K, V> mcoptions = MapCacheOptions.<K, V>defaults()
                              .writer(mapWriter)
                              .writeMode(WriteMode.WRITE_THROUGH);


RMap<K, V> map = redisson.getMap("test", options);
// or
RMapCache<K, V> map = redisson.getMapCache("test", mcoptions);
// or with performance boost up to 45x times 
RLocalCachedMap<K, V> map = redisson.getLocalCachedMap("test", options);
// or with performance boost up to 45x times 
RLocalCachedMapCache<K, V> map = redisson.getLocalCachedMapCache("test", mcoptions);
```

**Write-behind (asynchronous) strategy**

Updates of Map object are accumulated in batches and asynchronously written with defined delay to external storage through [MapWriter](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/map/MapWriter.html) object.  
`writeBehindDelay` - delay of batched write or delete operation. Default value is 1000 milliseconds.
`writeBehindBatchSize` - size of batch. Each batch contains Map Entry write or delete commands. Default value is 50.

Configuration example:

```java
MapOptions<K, V> options = MapOptions.<K, V>defaults()
                              .writer(mapWriter)
                              .writeMode(WriteMode.WRITE_BEHIND)
                              .writeBehindDelay(5000)
                              .writeBehindBatchSize(100);

MapCacheOptions<K, V> mcoptions = MapCacheOptions.<K, V>defaults()
                              .writer(mapWriter)
                              .writeMode(WriteMode.WRITE_BEHIND)
                              .writeBehindDelay(5000)
                              .writeBehindBatchSize(100);


RMap<K, V> map = redisson.getMap("test", options);
// or
RMapCache<K, V> map = redisson.getMapCache("test", mcoptions);
// or with performance boost up to 45x times 
RLocalCachedMap<K, V> map = redisson.getLocalCachedMap("test", options);
// or with performance boost up to 45x times 
RLocalCachedMapCache<K, V> map = redisson.getLocalCachedMapCache("test", mcoptions);
```

This feature available for `RMap`, `RMapCache`, `RLocalCachedMap` and `RLocalCachedMapCache` objects.

Usage of `RLocalCachedMap` and `RLocalCachedMapCache` objects boost Valkey or Redis read-operations up to **45x times** and give almost instant speed for database, web service or any other data source.

### Listeners

Redisson allows binding listeners per `RMap` object. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

`RMap` object allows to track follow events over the data.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Entry created/removed/updated after read operation| - |
|org.redisson.api.listener.MapPutListener|Entry created/updated|Eh or Th|
|org.redisson.api.listener.MapRemoveListener|Entry removed|Eh or Th|
|org.redisson.api.ExpiredObjectListener|`RMap` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RMap` object deleted|Eg|

Usage examples:
```java
RMap<String, SomeObject> map = redisson.getMap("anyMap");

int listenerId = map.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

int listenerId = map.addListener(new ExpiredObjectListener() {
     @Override
     public void onExpired(String name) {
        // ...
     }
});

int listenerId = map.addListener(new MapPutListener() {
     @Override
     public void onPut(String name, String fieldName) {
        // ...
     }
});

int listenerId = map.addListener(new MapRemoveListener() {
     @Override
     public void onRemove(String name, String fieldName) {
        // ...
     }
});

map.removeListener(listenerId);
```

`RMapCache` object allows to track additional events over the data.

|Listener class name|Event description |
|:--:|:--:|
|org.redisson.api.map.event.EntryCreatedListener|Entry created|
|org.redisson.api.map.event.EntryExpiredListener|Entry expired|
|org.redisson.api.map.event.EntryRemovedListener|Entry removed|
|org.redisson.api.map.event.EntryUpdatedListener|Entry updated|

!!! note "Important" 
    For optimization purposes, RMapCache entry events are emitted only when there are registered listeners. This means that listener registration affects the internal map state.

Usage examples:

```java
RMapCache<String, SomeObject> map = redisson.getMapCache("anyMap");
// or
RMapCache<String, SomeObject> map = redisson.getLocalCachedMapCache(LocalCachedMapCacheOptions.name("anyMap"));
// or
RMapCache<String, SomeObject> map = redisson.getClusteredLocalCachedMapCache("anyMap", LocalCachedMapOptions.defaults());
// or
RMapCache<String, SomeObject> map = redisson.getClusteredMapCache("anyMap");


int listenerId = map.addListener(new EntryUpdatedListener<Integer, Integer>() {
     @Override
     public void onUpdated(EntryEvent<Integer, Integer> event) {
          event.getKey(); // key
          event.getValue() // new value
          event.getOldValue() // old value
          // ...
     }
});

int listenerId = map.addListener(new EntryCreatedListener<Integer, Integer>() {
     @Override
     public void onCreated(EntryEvent<Integer, Integer> event) {
          event.getKey(); // key
          event.getValue() // value
          // ...
     }
});

int listenerId = map.addListener(new EntryExpiredListener<Integer, Integer>() {
     @Override
     public void onExpired(EntryEvent<Integer, Integer> event) {
          event.getKey(); // key
          event.getValue() // value
          // ...
     }
});

int listenerId = map.addListener(new EntryRemovedListener<Integer, Integer>() {
     @Override
     public void onRemoved(EntryEvent<Integer, Integer> event) {
          event.getKey(); // key
          event.getValue() // value
          // ...
     }
});

map.removeListener(listenerId);
```

### LRU/LFU bounded Map
Map object which implements `RMapCache` or `RMapCacheNativeV2` interface can be bounded using [Least Recently Used (LRU)](https://en.wikipedia.org/wiki/Cache_replacement_policies#LRU) or [Least Frequently Used (LFU)](https://en.wikipedia.org/wiki/Least_frequently_used) order. Bounded Map allows to store map entries within defined limit and retire entries in defined order. 

The size limit is applied either by the number of entries or the number of bytes stored in the Map object.

Available implementations:

|RedissonClient<br/>method name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write | Size limit | Native eviction |
| ------------- | :-----------: | :-----------:| :---------:| :---------:| :---------:|
|getMapCache()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ | ✔️ | ❌ |
|getMapCache()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ | ✔️ | ❌ |
|getLocalCachedMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ✔️ | ✔️ | ❌ |
|getClusteredMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ | ✔️ | ❌ |
|getClusteredLocalCachedMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ | ✔️ | ❌ |
|getMapCacheNativeV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ❌ | ✔️ | ✔️ | ✔️ |
|getLocalCachedMapCacheNativeV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ✔️ | ✔️ | ✔️ |
|getClusteredMapCacheNativeV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ | ✔️ | ✔️ |
|getClusteredLocalCachedMapCacheNativeV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ |


Use cases: limited Valkey or Redis memory.

```java
RMapCache<String, SomeObject> map = redisson.getMapCache("anyMap");
// or
RMapCache<String, SomeObject> map = redisson.getLocalCachedMapCache("anyMap", LocalCachedMapOptions.defaults());
// or
RMapCache<String, SomeObject> map = redisson.getClusteredLocalCachedMapCache("anyMap", LocalCachedMapOptions.defaults());
// or
RMapCache<String, SomeObject> map = redisson.getClusteredMapCache("anyMap");


RMapCacheNativeV2<String, SomeObject> map = redisson.getMapCacheNativeV2("anyMap");
// or
RMapCacheNativeV2<String, SomeObject> map = redisson.getLocalCachedMapCacheNativeV2("anyMap", LocalCachedMapOptions.defaults());
// or
RMapCacheNativeV2<String, SomeObject> map = redisson.getClusteredLocalCachedMapCacheNativeV2("anyMap", LocalCachedMapOptions.defaults());
// or
RMapCacheNativeV2<String, SomeObject> map = redisson.getClusteredMapCacheNativeV2("anyMap");


// tries to set limit map to 10 entries using LRU eviction algorithm
map.trySetMaxSize(10);
// ... using LFU eviction algorithm
map.trySetMaxSize(10, EvictionMode.LFU);

// set or change limit map to 10 entries using LRU eviction algorithm
map.setMaxSize(10);
// ... using LFU eviction algorithm
map.setMaxSize(10, EvictionMode.LFU);

map.put("1", "2");
map.put("3", "3", 1, TimeUnit.SECONDS);
```

## Multimap

Java implementation of a Valkey or Redis based [Multimap](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMultimap.html) maps each key to a collection of values rather than a single value, so one key can hold many entries at once. Redisson provides two implementations - set-based and list-based - that differ in whether duplicate values are allowed and whether order is preserved. The number of keys is limited to `4 294 967 295` elements, key uniqueness is determined from the serialized key state rather than the key's `hashCode()`/`equals()` methods, and the object is thread-safe.

### Choosing between set-based and list-based

Both implementations expose the same API and differ only in the semantics of the value collection held under each key.

| | Set-based (`RSetMultimap`) | List-based (`RListMultimap`) |
|:---|:---:|:---:|
| Duplicate values per key | not allowed | allowed |
| Value order | unordered | insertion order |
| `get(key)` returns | live [RSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSet.html) | live [RList](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RList.html) |
| `getAll` / `removeAll` / `replaceValues` return | `Set` | `List` |
| Backing structure | Valkey or Redis Set per key | Valkey or Redis List per key |

Prefer a multimap over a plain `RMap<K, Collection<V>>` when individual values have to be added, removed, or tested for membership atomically on the server: a multimap mutates the per-key collection in place, avoiding the read-modify-write race of loading a collection, changing it, and writing it back.

### Basic usage

Both implementations are obtained from the Redisson client and share the same methods; the examples below use the set-based multimap, and the list-based one behaves identically apart from keeping duplicates and order and returning `List` from its read methods. The asynchronous, reactive, and RxJava3 interfaces mirror the synchronous one - their methods return `RFuture`, `Mono`, and `Single` - so the tabs below differ only in those wrappers.

=== "Sync"
    ```java
    RSetMultimap<String, String> map = redisson.getSetMultimap("myMultimap");

    map.put("user:1", "admin");
    map.putAll("user:1", List.of("editor", "viewer"));

    int total = map.size();             // total number of key-value pairs
    long removed = map.fastRemove("user:1"); // remove one or more keys
    ```
=== "Async"
    ```java
    RSetMultimap<String, String> map = redisson.getSetMultimap("myMultimap");

    RFuture<Boolean> putFuture = map.putAsync("user:1", "admin");
    RFuture<Boolean> putAllFuture = map.putAllAsync("user:1", List.of("editor", "viewer"));

    RFuture<Integer> sizeFuture = map.sizeAsync();
    RFuture<Long> removeFuture = map.fastRemoveAsync("user:1");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RSetMultimapReactive<String, String> map = redisson.getSetMultimap("myMultimap");

    Mono<Boolean> putMono = map.put("user:1", "admin");
    Mono<Boolean> putAllMono = map.putAll("user:1", List.of("editor", "viewer"));

    Mono<Integer> sizeMono = map.size();
    Mono<Long> removeMono = map.fastRemove("user:1");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RSetMultimapRx<String, String> map = redisson.getSetMultimap("myMultimap");

    Single<Boolean> putRx = map.put("user:1", "admin");
    Single<Boolean> putAllRx = map.putAll("user:1", List.of("editor", "viewer"));

    Single<Integer> sizeRx = map.size();
    Single<Long> removeRx = map.fastRemove("user:1");
    ```

### Adding and reading values

`put` adds a single value under a key and returns whether the collection changed; `putAll` adds several values at once. Reads come in two forms: `get` returns a live view of the key's values, while `getAll` returns a detached snapshot - a `Set`/`List` on the synchronous, reactive, and RxJava3 interfaces, and a `Collection` on the asynchronous one.

!!! note

    The view returned by `get` is itself a full Redisson collection - an [RSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSet.html) for set-based multimaps or an [RList](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RList.html) for list-based ones - bound to the key. Calling its own methods, or iterating it, reads and writes straight through to the multimap, so it can be passed anywhere an `RSet`/`RList` is expected without copying the values out. The asynchronous interface has no live-view `get`; use `getAllAsync` instead.

=== "Sync"
    ```java
    RSetMultimap<String, String> map = redisson.getSetMultimap("myMultimap");

    map.put("user:1", "admin");
    map.putAll("user:1", List.of("editor", "viewer"));

    RSet<String> live = map.get("user:1");    // live view, writes through to the multimap
    Set<String> roles = map.getAll("user:1"); // detached snapshot
    ```
=== "Async"
    ```java
    RSetMultimap<String, String> map = redisson.getSetMultimap("myMultimap");

    RFuture<Boolean> putFuture = map.putAsync("user:1", "admin");
    RFuture<Boolean> putAllFuture = map.putAllAsync("user:1", List.of("editor", "viewer"));

    RFuture<Collection<String>> rolesFuture = map.getAllAsync("user:1"); // detached Collection
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RSetMultimapReactive<String, String> map = redisson.getSetMultimap("myMultimap");

    Mono<Boolean> putMono = map.put("user:1", "admin");
    Mono<Boolean> putAllMono = map.putAll("user:1", List.of("editor", "viewer"));

    RSetReactive<String> live = map.get("user:1");      // live view
    Mono<Set<String>> rolesMono = map.getAll("user:1"); // detached snapshot
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RSetMultimapRx<String, String> map = redisson.getSetMultimap("myMultimap");

    Single<Boolean> putRx = map.put("user:1", "admin");
    Single<Boolean> putAllRx = map.putAll("user:1", List.of("editor", "viewer"));

    RSetRx<String> live = map.get("user:1");            // live view
    Single<Set<String>> rolesRx = map.getAll("user:1"); // detached snapshot
    ```

Alongside per-key access, the multimap exposes map-wide views - `keySet`, `values`, and `entries` - plus `size` (the total number of key-value pairs) and the `containsKey`/`containsValue`/`containsEntry` checks. The map-wide views are read on the synchronous interface:

```java
Set<String> keys = map.keySet();
Collection<String> allValues = map.values();
Collection<Map.Entry<String, String>> allEntries = map.entries();
int total = map.size();
boolean hasRole = map.containsEntry("user:1", "admin");
```

### Removing and replacing values

`remove` deletes a single value from a key and reports whether it was present. `removeAll` deletes a key's entire collection and returns the removed values, while `fastRemove` deletes one or more keys without returning their values and is the most efficient way to drop keys. `replaceValues` swaps a key's whole collection for a new one and returns the previous values.

=== "Sync"
    ```java
    RSetMultimap<String, String> map = redisson.getSetMultimap("myMultimap");

    boolean removed = map.remove("user:1", "viewer");
    Set<String> previous = map.replaceValues("user:1", List.of("admin", "owner"));
    Set<String> dropped = map.removeAll("user:1");
    long count = map.fastRemove("user:1", "user:2");
    ```
=== "Async"
    ```java
    RSetMultimap<String, String> map = redisson.getSetMultimap("myMultimap");

    RFuture<Boolean> removeFuture = map.removeAsync("user:1", "viewer");
    RFuture<Collection<String>> replaceFuture = map.replaceValuesAsync("user:1", List.of("admin", "owner"));
    RFuture<Collection<String>> droppedFuture = map.removeAllAsync("user:1");
    RFuture<Long> countFuture = map.fastRemoveAsync("user:1", "user:2");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RSetMultimapReactive<String, String> map = redisson.getSetMultimap("myMultimap");

    Mono<Boolean> removeMono = map.remove("user:1", "viewer");
    Mono<Set<String>> replaceMono = map.replaceValues("user:1", List.of("admin", "owner"));
    Mono<Set<String>> droppedMono = map.removeAll("user:1");
    Mono<Long> countMono = map.fastRemove("user:1", "user:2");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RSetMultimapRx<String, String> map = redisson.getSetMultimap("myMultimap");

    Single<Boolean> removeRx = map.remove("user:1", "viewer");
    Single<Set<String>> replaceRx = map.replaceValues("user:1", List.of("admin", "owner"));
    Single<Set<String>> droppedRx = map.removeAll("user:1");
    Single<Long> countRx = map.fastRemove("user:1", "user:2");
    ```

### Eviction
The plain `RSetMultimap` and `RListMultimap` objects can be expired as a whole - they implement [RExpirable](common-methods.md#expiration), so `expire` and `clearExpire` apply to the entire multimap - but they don't support expiration of individual entries. Per-entry eviction with a `time to live` is provided by separate MultimapCache objects. There are [RSetMultimapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetMultimapCache.html) and [RListMultimapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RListMultimapCache.html) objects for Set and List based Multimaps respectively.  

With the scripted cache, an eviction task is started once per unique object name at the moment of getting the Multimap instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Valkey or Redis calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

Redis 7.4.0 and higher version implements native eviction. It's supported by [RSetMultimapCacheNative](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RSetMultimapCacheNative.html) and [RListMultimapCacheNative](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RListMultimapCacheNative.html) objects. Expiration is handled on the server side, so no client-side eviction task or `EvictionScheduler` is involved.

Code examples:

=== "Sync"
    ```java
    // scripted eviction implementation
    RSetMultimapCache<String, String> multimap = redisson.getSetMultimapCache("myMultimap");

    // or native eviction implementation (Redis 7.4.0 and higher):
    // RSetMultimapCacheNative<String, String> multimap = redisson.getSetMultimapCacheNative("myMultimap");

    multimap.put("1", "a");
    multimap.put("1", "b");
    multimap.put("1", "c");

    multimap.put("2", "e");
    multimap.put("2", "f");

    multimap.expireKey("2", 10, TimeUnit.MINUTES);

    // once the object is no longer used
    multimap.destroy();
    ```
=== "Async"
    ```java
    // scripted eviction implementation
    RSetMultimapCacheAsync<String, String> multimap = redisson.getSetMultimapCache("myMultimap");

    // or native eviction implementation (Redis 7.4.0 and higher):
    // RSetMultimapCacheNativeAsync<String, String> multimap = redisson.getSetMultimapCacheNative("myMultimap");

    RFuture<Boolean> f1 = multimap.putAsync("1", "a");
    RFuture<Boolean> f2 = multimap.putAsync("1", "b");
    RFuture<Boolean> f3 = multimap.putAsync("1", "c");

    RFuture<Boolean> f4 = multimap.putAsync("2", "e");
    RFuture<Boolean> f5 = multimap.putAsync("2", "f");

    RFuture<Boolean> exfeature = multimap.expireKeyAsync("2", 10, TimeUnit.MINUTES);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redissonReactive = redisson.reactive();

    // scripted eviction implementation
    RSetMultimapCacheReactive<String, String> multimap = redissonReactive.getSetMultimapCache("myMultimap");

    // or native eviction implementation (Redis 7.4.0 and higher):
    // RSetMultimapCacheNativeReactive<String, String> multimap = redissonReactive.getSetMultimapCacheNative("myMultimap");

    Mono<Boolean> f1 = multimap.put("1", "a");
    Mono<Boolean> f2 = multimap.put("1", "b");
    Mono<Boolean> f3 = multimap.put("1", "c");

    Mono<Boolean> f4 = multimap.put("2", "e");
    Mono<Boolean> f5 = multimap.put("2", "f");

    Mono<Boolean> exfeature = multimap.expireKey("2", 10, TimeUnit.MINUTES);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redissonRx = redisson.rxJava();

    // scripted eviction implementation
    RSetMultimapCacheRx<String, String> multimap = redissonRx.getSetMultimapCache("myMultimap");

    // or native eviction implementation (Redis 7.4.0 and higher):
    // RSetMultimapCacheNativeRx<String, String> multimap = redissonRx.getSetMultimapCacheNative("myMultimap");

    Single<Boolean> f1 = multimap.put("1", "a");
    Single<Boolean> f2 = multimap.put("1", "b");
    Single<Boolean> f3 = multimap.put("1", "c");

    Single<Boolean> f4 = multimap.put("2", "e");
    Single<Boolean> f5 = multimap.put("2", "f");

    Single<Boolean> exfeature = multimap.expireKey("2", 10, TimeUnit.MINUTES);
    ```

List-based caches are obtained the same way with `getListMultimapCache` and `getListMultimapCacheNative`, and expose the identical API with `List` value semantics.

### Listeners

Redisson allows binding listeners per `RSetMultimap` or `RListMultimap` object. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

`RSetMultimap` listeners:

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.ExpiredObjectListener|`RSetMultimap` object expired| Ex|
|org.redisson.api.DeletedObjectListener|`RSetMultimap` object deleted| Eg|
|org.redisson.api.listener.SetAddListener|Element added to entry| Es|
|org.redisson.api.listener.SetRemoveListener|Element removed from entry| Es|
|org.redisson.api.listener.MapPutListener|Entry created|Eh or Th|
|org.redisson.api.listener.MapRemoveListener|Entry removed|Eh or Th|

`RListMultimap` listeners:

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.ExpiredObjectListener|`RListMultimap` object expired| Ex|
|org.redisson.api.DeletedObjectListener|`RListMultimap` object deleted| Eg|
|org.redisson.api.listener.ListAddListener|Element added to entry| Es|
|org.redisson.api.listener.ListRemoveListener|Element removed from entry| Es|
|org.redisson.api.listener.MapPutListener|Entry created|Eh or Th|
|org.redisson.api.listener.MapRemoveListener|Entry removed|Eh or Th|

Listener callbacks receive the affected Redis key name - and, for entry-level events, the field - as `String` values, not the multimap's typed key and value.

Usage example:

```java
RListMultimap<Integer, Integer> lmap = redisson.getListMultimap("mymap");

int listenerId = lmap.addListener(new MapPutListener() {
     @Override
     public void onPut(String name, String fieldName) {
        // ...
     }
});

// ...

lmap.removeListener(listenerId);
```

### Use Cases

Multimaps fit wherever one key owns a changing collection of values - secondary indexes, one-to-many relationships, and grouping streams of items by a key - with the choice between the set- and list-based implementations deciding whether duplicates and order matter.

**Tag and Label Indexes**

A set-based multimap makes a natural inverted index: map each tag to the ids that carry it, look them up directly, and let set semantics keep ids unique no matter how often a tag is reapplied.

```java
RSetMultimap<String, String> tagIndex = redisson.getSetMultimap("tag-index");

tagIndex.put("premium", "user:1001");
tagIndex.put("premium", "user:1002");
tagIndex.put("beta", "user:1001");

// every id carrying a tag - duplicates are ignored
Set<String> premiumUsers = tagIndex.getAll("premium");
```

**One-to-Many Relationships**

Parent-to-children relationships - a customer's orders, an author's posts - map cleanly to a multimap keyed by the parent id. With a set-based multimap each child appears once, and the whole group is read, replaced, or removed in a single call.

```java
RSetMultimap<Long, Long> ordersByCustomer = redisson.getSetMultimap("orders-by-customer");

ordersByCustomer.put(1001L, 55001L);
ordersByCustomer.put(1001L, 55002L);

// all orders for a customer
Set<Long> orders = ordersByCustomer.getAll(1001L);

// remove the customer and all their orders at once
Set<Long> removed = ordersByCustomer.removeAll(1001L);
```

**Grouping Events in Order**

When the values are a sequence rather than a set - log lines per request, events per session, messages per conversation - a list-based multimap preserves insertion order and keeps duplicates. Paired with the cache variant, an idle group can expire on its own.

```java
RListMultimapCache<String, String> eventsBySession = redisson.getListMultimapCache("events-by-session");

eventsBySession.put("session:abc", "login");
eventsBySession.put("session:abc", "view:home");
eventsBySession.put("session:abc", "view:home"); // duplicates kept, in order

// expire the whole group if the session goes idle
eventsBySession.expireKey("session:abc", 30, TimeUnit.MINUTES);

List<String> timeline = eventsBySession.getAll("session:abc");
```

## JSON Store

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

[RJsonStore](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RJsonStore.html) is a distributed store of JSON documents, compatible with Valkey and Redis, and thread-safe. Each value is a POJO serialized to JSON and stored under a key with native `JSON.*` commands, so a stored document is not an opaque blob: individual fields, array elements, and numeric counters can be read and updated in place on the server - addressed by [JSONPath](https://redis.io/docs/latest/develop/data-types/json/path/) - without transferring the whole value. Operations run on a single key or on a group of keys in one round trip.

Entries can carry a per-entry `time to live` that is cleaned up on the server side with no eviction task, and can be indexed and queried by their fields through [RediSearch integration](#search-by-object-properties). A [local cache](#local-cache) variant serves hot reads without a network round trip.

### When to use JSON Store

Reach for `RJsonStore` instead of an `RMap` or `RBucket` of serialized objects when you need any of the following: updating part of a document in place - setting a field, pushing onto an array, incrementing a counter - without loading and rewriting the whole value, which also removes the read-modify-write race; a per-entry `time to live`; or field-level search through RediSearch.

### Basic usage

An instance is obtained from the Redisson client with the document codec (and optionally a key codec). The synchronous, asynchronous, reactive, and RxJava3 interfaces share the same methods and differ only in their return types - `RFuture`, `Mono`, and `Single`/`Maybe`/`Completable`.

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    MyObject obj = ...;

    store.set("1", obj);
    MyObject value = store.get("1");
    boolean removed = store.delete("1");
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    MyObject obj = ...;

    RFuture<Void> setFuture = store.setAsync("1", obj);
    RFuture<MyObject> getFuture = store.getAsync("1");
    RFuture<Boolean> deleteFuture = store.deleteAsync("1");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    MyObject obj = ...;

    Mono<Void> setMono = store.set("1", obj);
    Mono<MyObject> getMono = store.get("1");
    Mono<Boolean> deleteMono = store.delete("1");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    MyObject obj = ...;

    Completable setRx = store.set("1", obj);
    Maybe<MyObject> getRx = store.get("1");
    Single<Boolean> deleteRx = store.delete("1");
    ```

### Storing documents

`set` writes a document, overwriting any previous value, and `set(Map)` stores many documents in a single call. A `Duration` overload attaches a per-entry `time to live`. `setIfAbsent` writes only when the key is new and `setIfExists` only when it already exists; both also have `Duration` overloads.

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    store.set("1", t1);
    store.set(Map.of("1", t1, "2", t2));
    store.set("1", t1, Duration.ofSeconds(100)); // per-entry TTL
    boolean stored = store.setIfAbsent("1", t1);
    boolean updated = store.setIfExists("1", t1);
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    RFuture<Void> f1 = store.setAsync("1", t1);
    RFuture<Void> f2 = store.setAsync(Map.of("1", t1, "2", t2));
    RFuture<Void> f3 = store.setAsync("1", t1, Duration.ofSeconds(100));
    RFuture<Boolean> f4 = store.setIfAbsentAsync("1", t1);
    RFuture<Boolean> f5 = store.setIfExistsAsync("1", t1);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Mono<Void> m1 = store.set("1", t1);
    Mono<Void> m2 = store.set(Map.of("1", t1, "2", t2));
    Mono<Void> m3 = store.set("1", t1, Duration.ofSeconds(100));
    Mono<Boolean> m4 = store.setIfAbsent("1", t1);
    Mono<Boolean> m5 = store.setIfExists("1", t1);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Completable c1 = store.set("1", t1);
    Completable c2 = store.set(Map.of("1", t1, "2", t2));
    Completable c3 = store.set("1", t1, Duration.ofSeconds(100));
    Single<Boolean> s4 = store.setIfAbsent("1", t1);
    Single<Boolean> s5 = store.setIfExists("1", t1);
    ```

### Reading documents

`get` returns a single document, or `null` if the key is absent; `get(Set)` reads many keys in one round trip and returns a map of the present entries; and `getAndDelete` reads a document and removes it atomically. `readAllKeySet` returns every key at once, while the synchronous `keySet` iterates keys lazily with the server's `SCAN` cursor and accepts an optional pattern and batch count.

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    MyObject value = store.get("1");
    Map<String, MyObject> many = store.get(Set.of("1", "2"));
    MyObject taken = store.getAndDelete("1");
    Set<String> allKeys = store.readAllKeySet();
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    RFuture<MyObject> getFuture = store.getAsync("1");
    RFuture<Map<String, MyObject>> manyFuture = store.getAsync(Set.of("1", "2"));
    RFuture<MyObject> takenFuture = store.getAndDeleteAsync("1");
    RFuture<Set<String>> keysFuture = store.readAllKeySetAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Mono<MyObject> getMono = store.get("1");
    Mono<Map<String, MyObject>> manyMono = store.get(Set.of("1", "2"));
    Mono<MyObject> takenMono = store.getAndDelete("1");
    Mono<Set<String>> keysMono = store.readAllKeySet();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Maybe<MyObject> getRx = store.get("1");
    Single<Map<String, MyObject>> manyRx = store.get(Set.of("1", "2"));
    Maybe<MyObject> takenRx = store.getAndDelete("1");
    Single<Set<String>> keysRx = store.readAllKeySet();
    ```

### Deleting documents

`delete` removes a single key and reports whether it existed; `delete(Set)` removes many keys in one call and returns how many were present.

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    boolean removed = store.delete("1");
    long count = store.delete(Set.of("1", "2"));
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    RFuture<Boolean> removedFuture = store.deleteAsync("1");
    RFuture<Long> countFuture = store.deleteAsync(Set.of("1", "2"));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Mono<Boolean> removedMono = store.delete("1");
    Mono<Long> countMono = store.delete(Set.of("1", "2"));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Single<Boolean> removedRx = store.delete("1");
    Single<Long> countRx = store.delete(Set.of("1", "2"));
    ```

### Working inside a document

Because each value is a JSON document, parts of it can be read and modified by [JSONPath](https://redis.io/docs/latest/develop/data-types/json/path/) - evaluated entirely on the server, without fetching or rewriting the whole value. Each operation takes a path; the typed reads (`get`, `arrayPop`, and similar) take a `JsonCodec` - a `JacksonCodec` of the extracted type - to decode the sub-value. Most operations have a `*Multi` companion (`arrayAppendMulti`, `incrementAndGetMulti`, ...) that applies to every location a multi-valued path matches and returns one result per match.

**Fields and structure**

`set(key, path, value)` writes a single field, `merge` merges a partial object into the document, the path form of `get` extracts a typed sub-value, and `getType`/`countKeys`/`getKeys` inspect structure.

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    store.set("1", "$.name", "name2");
    store.merge("1", "$", new MyObject());
    String name = store.get("1", new JacksonCodec<>(String.class), "$.name");
    JsonType type = store.getType("1", "$.name");
    long fields = store.countKeys("1");
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    RFuture<Void> setFuture = store.setAsync("1", "$.name", "name2");
    RFuture<Void> mergeFuture = store.mergeAsync("1", "$", new MyObject());
    RFuture<String> nameFuture = store.getAsync("1", new JacksonCodec<>(String.class), "$.name");
    RFuture<JsonType> typeFuture = store.getTypeAsync("1", "$.name");
    RFuture<Long> fieldsFuture = store.countKeysAsync("1");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Mono<Void> setMono = store.set("1", "$.name", "name2");
    Mono<Void> mergeMono = store.merge("1", "$", new MyObject());
    Mono<String> nameMono = store.get("1", new JacksonCodec<>(String.class), "$.name");
    Mono<JsonType> typeMono = store.getType("1", "$.name");
    Mono<Long> fieldsMono = store.countKeys("1");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Completable setRx = store.set("1", "$.name", "name2");
    Completable mergeRx = store.merge("1", "$", new MyObject());
    Maybe<String> nameRx = store.get("1", new JacksonCodec<>(String.class), "$.name");
    Single<JsonType> typeRx = store.getType("1", "$.name");
    Maybe<Long> fieldsRx = store.countKeys("1");
    ```

**Arrays**

`arrayAppend` and `arrayInsert` add elements, `arraySize` reports the length, and `arrayPop` removes and returns an element at an index (`-1` for the last).

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    long size = store.arrayAppend("1", "$.tags", "premium");
    long size2 = store.arrayInsert("1", "$.tags", 0, "vip");
    long length = store.arraySize("1", "$.tags");
    String last = store.arrayPop("1", new JacksonCodec<>(String.class), "$.tags", -1);
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    RFuture<Long> appendFuture = store.arrayAppendAsync("1", "$.tags", "premium");
    RFuture<Long> insertFuture = store.arrayInsertAsync("1", "$.tags", 0, "vip");
    RFuture<Long> lengthFuture = store.arraySizeAsync("1", "$.tags");
    RFuture<String> lastFuture = store.arrayPopAsync("1", new JacksonCodec<>(String.class), "$.tags", -1);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Mono<Long> appendMono = store.arrayAppend("1", "$.tags", "premium");
    Mono<Long> insertMono = store.arrayInsert("1", "$.tags", 0L, "vip");
    Mono<Long> lengthMono = store.arraySize("1", "$.tags");
    Mono<String> lastMono = store.arrayPop("1", new JacksonCodec<>(String.class), "$.tags", -1L);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Single<Long> appendRx = store.arrayAppend("1", "$.tags", "premium");
    Single<Long> insertRx = store.arrayInsert("1", "$.tags", 0L, "vip");
    Single<Long> lengthRx = store.arraySize("1", "$.tags");
    Maybe<String> lastRx = store.arrayPop("1", new JacksonCodec<>(String.class), "$.tags", -1L);
    ```

**Numbers, booleans, and strings**

`incrementAndGet` atomically bumps a numeric field, `toggle` flips a boolean, and `stringAppend` appends to a string field, returning its new length.

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Integer views = store.incrementAndGet("1", "$.views", 1);
    boolean active = store.toggle("1", "$.active");
    long length = store.stringAppend("1", "$.name", " (verified)");
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    RFuture<Integer> viewsFuture = store.incrementAndGetAsync("1", "$.views", 1);
    RFuture<Boolean> activeFuture = store.toggleAsync("1", "$.active");
    RFuture<Long> lengthFuture = store.stringAppendAsync("1", "$.name", " (verified)");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Mono<Integer> viewsMono = store.incrementAndGet("1", "$.views", 1);
    Mono<Boolean> activeMono = store.toggle("1", "$.active");
    Mono<Long> lengthMono = store.stringAppend("1", "$.name", " (verified)");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Maybe<Integer> viewsRx = store.incrementAndGet("1", "$.views", 1);
    Single<Boolean> activeRx = store.toggle("1", "$.active");
    Single<Long> lengthRx = store.stringAppend("1", "$.name", " (verified)");
    ```

### Atomic and conditional updates

`compareAndSet` swaps a document only if it currently equals an expected value, and its path form does the same for a single field. `getAndSet` replaces a document and returns the previous value. The conditional `setIfAbsent`/`setIfExists` shown under [Storing documents](#storing-documents) round out this group.

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    boolean swapped = store.compareAndSet("1", t1, t2);
    boolean fieldSwapped = store.compareAndSet("1", "$.name", "name1", "name2");
    MyObject previous = store.getAndSet("1", t2);
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    RFuture<Boolean> swapFuture = store.compareAndSetAsync("1", t1, t2);
    RFuture<Boolean> fieldFuture = store.compareAndSetAsync("1", "$.name", "name1", "name2");
    RFuture<MyObject> prevFuture = store.getAndSetAsync("1", t2);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Mono<Boolean> swapMono = store.compareAndSet("1", t1, t2);
    Mono<Boolean> fieldMono = store.compareAndSet("1", "$.name", "name1", "name2");
    Mono<MyObject> prevMono = store.getAndSet("1", t2);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Single<Boolean> swapRx = store.compareAndSet("1", t1, t2);
    Single<Boolean> fieldRx = store.compareAndSet("1", "$.name", "name1", "name2");
    Maybe<MyObject> prevRx = store.getAndSet("1", t2);
    ```

### Expiration

A per-entry `time to live` is attached when writing (`set` with a `Duration`); expiration is handled on the Valkey or Redis side with no eviction task. `getAndExpire` reads a document and (re)sets its expiration in one step, `remainTimeToLive` reports the milliseconds left, and `setAndKeepTTL` overwrites a value while preserving its current expiration.

=== "Sync"
    ```java
    RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    store.set("1", t1, Duration.ofSeconds(100));
    MyObject value = store.getAndExpire("1", Duration.ofMinutes(5));
    long ttl = store.remainTimeToLive("1");
    store.setAndKeepTTL("1", t2);
    ```
=== "Async"
    ```java
    RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    RFuture<Void> setFuture = store.setAsync("1", t1, Duration.ofSeconds(100));
    RFuture<MyObject> getFuture = store.getAndExpireAsync("1", Duration.ofMinutes(5));
    RFuture<Long> ttlFuture = store.remainTimeToLiveAsync("1");
    RFuture<Void> keepFuture = store.setAndKeepTTLAsync("1", t2);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RJsonStoreReactive<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Mono<Void> setMono = store.set("1", t1, Duration.ofSeconds(100));
    Mono<MyObject> getMono = store.getAndExpire("1", Duration.ofMinutes(5));
    Mono<Long> ttlMono = store.remainTimeToLive("1");
    Mono<Void> keepMono = store.setAndKeepTTL("1", t2);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RJsonStoreRx<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec<>(MyObject.class));

    Completable setRx = store.set("1", t1, Duration.ofSeconds(100));
    Maybe<MyObject> getRx = store.getAndExpire("1", Duration.ofMinutes(5));
    Single<Long> ttlRx = store.remainTimeToLive("1");
    Completable keepRx = store.setAndKeepTTL("1", t2);
    ```

These TTLs apply to individual entries. The store itself implements [RExpirable](common-methods.md#expiration), so `expire` and `clearExpire` set an expiration on the entire store, which is a separate mechanism from the per-entry expiration above.

### Search by Object properties

Because values are stored as JSON, a JSON Store can be indexed and queried by the fields of its documents through [RediSearch](services.md#redisearch-service). Point lookups by key and ad-hoc field queries then run against the same data, with no separate search system to keep in sync.

For data searching, index prefix should be defined in `<object_name>:` format. For example for object name "test" prefix is "test:".

`StringCodec` should be used as object codec to enable searching by field.

Data search code example:
```java
RSearch s = redisson.getSearch();
s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.JSON)
                        .prefix(Arrays.asList("test:")),
                    FieldIndex.text("name"));

RJsonStore<String, MyObject> store = redisson.getJsonStore("test", StringCodec.INSTANCE, new JacksonCodec<>(MyObject.class));

MyObject t1 = new MyObject();
t1.setName("name1");
MyObject t2 = new MyObject();
t2.setName("name2");

Map<String, MyObject> entries = new HashMap<>();
entries.put("1", t1);
entries.put("2", t2);
store.set(entries);

// search
SearchResult r = s.search("idx", "*", QueryOptions.defaults()
                                                  .returnAttributes(new ReturnAttribute("name")));

// aggregation
AggregationResult ar = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                 .withCursor().load("name"));
```

### Local Cache

Redisson provides [JSON Store](#json-store) implementation with local cache.

**local cache** - so called near cache used to speed up read operations and avoid network roundtrips. It caches JSON Store entries on Redisson side and executes read operations up to **45x faster** in comparison with regular implementation. Local cached instances with the same name are connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state. It's recommended to use each local cached instance as a singleton per unique name since it has own state for local cache.

!!! warning

    It's recommended to use a single instance of local cached JsonStore instance per unique name for each Redisson instance. Same `LocalCachedJsonStoreOptions` object should be used across all instances with the same name.

Follow options can be supplied during object creation:
```java
      LocalCachedJsonStoreOptions options = LocalCachedJsonStoreOptions.name("object_name_example")

      // Defines codec used for key
      .keyCodec(codec)

      // Defines codec used for JSON value
      .valueCodec(codec)

      // Defines whether to store a cache miss into the local cache.
      // Default value is false.
      .storeCacheMiss(false);

      // Defines store mode of cache data.
      // Follow options are available:
      // LOCALCACHE - store data in local cache only and use Valkey or Redis only for data update/invalidation.
      // LOCALCACHE_REDIS - store data in both Valkey or Redis and local cache.
      .storeMode(StoreMode.LOCALCACHE_REDIS)

      // Defines Cache provider used as local cache store.
      // Follow options are available:
      // REDISSON - uses Redisson own implementation
      // CAFFEINE - uses Caffeine implementation
      .cacheProvider(CacheProvider.REDISSON)

      // Defines local cache eviction policy.
      // Follow options are available:
      // LFU - Counts how often an item was requested. Those that are used least often are discarded first.
      // LRU - Discards the least recently used items first
      // SOFT - Uses soft references, entries are removed by GC
      // WEAK - Uses weak references, entries are removed by GC
      // NONE - No eviction
     .evictionPolicy(EvictionPolicy.NONE)

      // If cache size is 0 then local cache is unbounded.
     .cacheSize(1000)

      // Defines strategy for load missed local cache updates after connection failure.
      //
      // Follow reconnection strategies are available:
      // CLEAR - Clear local cache if map instance has been disconnected for a while.
      // NONE - Default. No reconnection handling
     .reconnectionStrategy(ReconnectionStrategy.NONE)

      // Defines local cache synchronization strategy.
      //
      // Follow sync strategies are available:
      // INVALIDATE - Default. Invalidate cache entry across all RLocalCachedJsonStore instances on map entry change
      // UPDATE - Insert/update cache entry across all RLocalCachedJsonStore instances on map entry change
      // NONE - No synchronizations on map changes
     .syncStrategy(SyncStrategy.INVALIDATE)

      // time to live for each entry in local cache
     .timeToLive(Duration.ofSeconds(10))

      // max idle time for each entry in local cache
     .maxIdle(Duration.ofSeconds(10));

     // Defines how to listen expired event sent by Valkey or Redis upon this instance deletion
     //
     // Follow expiration policies are available:
     // DONT_SUBSCRIBE - Don't subscribe on expire event
     // SUBSCRIBE_WITH_KEYEVENT_PATTERN - Subscribe on expire event using `__keyevent@*:expired` pattern
     // SUBSCRIBE_WITH_KEYSPACE_CHANNEL - Subscribe on expire event using `__keyspace@N__:name` channel
     .expirationEventPolicy(ExpirationEventPolicy.SUBSCRIBE_WITH_KEYEVENT_PATTERN)
```

Data write code example:
```java
LocalCachedJsonStoreOptions ops = LocalCachedJsonStoreOptions.name("test")
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(new JacksonCodec<>(MyObject.class));
RLocalCachedJsonStore<String, MyObject> store = redisson.getLocalCachedJsonStore(ops);

MyObject t1 = new MyObject();
t1.setName("name1");
MyObject t2 = new MyObject();
t2.setName("name2");

Map<String, MyObject> entries = new HashMap<>();
entries.put("1", t1);
entries.put("2", t2);

// multiple entries at once
store.set(entries);

// or set entry per call
store.set("1", t1);
store.set("2", t2);

// with ttl
store.set("1", t1, Duration.ofSeconds(100));

// set if not set previously
store.setIfAbsent("1", t1);

// set if entry already exists
store.setIfExists("1", t1);
```

Data read code example:
```java
LocalCachedJsonStoreOptions ops = LocalCachedJsonStoreOptions.name("test")
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(new JacksonCodec<>(MyObject.class));
RLocalCachedJsonStore<String, MyObject> store = redisson.getLocalCachedJsonStore(ops);

// multiple entries at once
Map<String, MyObject> entries = store.get(Set.of("1", "2"));

// or read entry per call
MyObject value1 = store.get("1");
MyObject value2 = store.get("2");
```

Data deletion code example:
```java
LocalCachedJsonStoreOptions ops = LocalCachedJsonStoreOptions.name("test")
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(new JacksonCodec<>(MyObject.class));
RLocalCachedJsonStore<String, MyObject> store = redisson.getLocalCachedJsonStore(ops);

// multiple entries at once
long deleted = store.delete(Set.of("1", "2"));

// or delete entry per call
boolean status = store.delete("1");
boolean status = store.delete("2");
```

### Use Cases

JSON Store fits applications that keep many structured records — each a self-contained JSON document addressed by a key — where individual entries are read, written, expired, and queried independently. Values are stored with `JSON.*` commands, expiration is handled on the Valkey or Redis side without an eviction task, many keys can be read or written in a single round trip, and entries can be indexed and queried by their fields through [RediSearch integration](#search-by-object-properties).

**Session and Token Store**

Web sessions, refresh tokens, and short-lived authorization grants are rich objects (user id, roles, device metadata, issue/expiry timestamps) that map one-to-one to a key and should disappear automatically once they lapse. A `time to live` is attached per entry so cleanup happens on the Valkey or Redis side with no eviction task, while `setIfExists` refreshes only sessions that are still active.

```java
RJsonStore<String, Session> sessions =
        redisson.getJsonStore("session", new JacksonCodec<>(Session.class));

Session s = new Session(userId, roles, deviceId, Instant.now());

// store the session with a 30-minute TTL, expiry handled on the Valkey or Redis side
sessions.set("sess:" + sessionId, s, Duration.ofMinutes(30));

// validate by key on each incoming request
Session current = sessions.get("sess:" + sessionId);

// sliding refresh - only extend a session that still exists
sessions.setIfExists("sess:" + sessionId, current);

// explicit logout
sessions.delete("sess:" + sessionId);
```

**Searchable Document Store**

User profiles, product catalogs, and other entity records are stored as JSON documents and queried by field rather than only by key. With a JSON index defined over the store's key prefix, the same data backs both point lookups by id and ad-hoc field queries, full-text matching, and aggregation, without copying it into a separate search system. `StringCodec` is used for keys so fields are indexable.

```java
RSearch search = redisson.getSearch();
search.createIndex("idx:product", IndexOptions.defaults()
                        .on(IndexType.JSON)
                        .prefix(Arrays.asList("product:")),
                    FieldIndex.text("name"));

RJsonStore<String, Product> products =
        redisson.getJsonStore("product", StringCodec.INSTANCE, new JacksonCodec<>(Product.class));
products.set("product:1001", new Product("Wireless Mouse"));

// point lookup by id
Product p = products.get("product:1001");

// field query backed by the same data - full-text match on the name field
SearchResult found = search.search("idx:product", "@name:wireless", QueryOptions.defaults()
                                                  .returnAttributes(new ReturnAttribute("name")));
```

**Shopping Carts and Workflow State**

Carts, checkout sessions, multi-step form drafts, and long-running workflow state are JSON documents that change over their lifetime and are often touched in groups - load every cart in a batch job, expire abandoned ones, or purge a customer's drafts at once. Bulk `set`, `get`, and `delete` over a set of keys collapse these into a single round trip, while a per-entry `time to live` reclaims abandoned state automatically.

```java
RJsonStore<String, Cart> carts =
        redisson.getJsonStore("cart", new JacksonCodec<>(Cart.class));

// write several carts at once
Map<String, Cart> batch = new HashMap<>();
batch.put("cart:a1", cartA);
batch.put("cart:b2", cartB);
carts.set(batch);

// abandoned-cart expiry handled on the Valkey or Redis side
carts.set("cart:a1", cartA, Duration.ofHours(24));

// read or purge a group of carts in one call
Map<String, Cart> loaded = carts.get(Set.of("cart:a1", "cart:b2"));
long removed = carts.delete(Set.of("cart:a1", "cart:b2"));
```

**Read-Heavy Reference Data with Local Cache**

Feature configuration, pricing tables, and catalog metadata are read constantly but updated rarely, and for these the network round trip dominates cost. The [local cached](#local-cache) JSON Store keeps entries on the Redisson side for reads up to **45x faster** than the regular implementation, while a shared pub/sub channel invalidates cached copies across all instances whenever an entry changes, so every node converges on the latest value.

```java
LocalCachedJsonStoreOptions options = LocalCachedJsonStoreOptions.name("pricing")
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(new JacksonCodec<>(PricingRule.class))
                .syncStrategy(SyncStrategy.INVALIDATE)
                .evictionPolicy(EvictionPolicy.LRU)
                .cacheSize(10000);
RLocalCachedJsonStore<String, PricingRule> pricing = redisson.getLocalCachedJsonStore(options);

// served from the local cache after the first read, no network round trip
PricingRule rule = pricing.get("rule:default");

// this update is propagated to every other instance's local cache
pricing.set("rule:default", updatedRule);
```

## Set

Redisson's [RSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSet.html) is a distributed implementation of Java's [Set](https://docs.oracle.com/javase/8/docs/api/java/util/Set.html) interface, backed by a Valkey or Redis set. It is thread-safe, cluster-compatible, holds up to 4,294,967,295 elements, and is available through synchronous, [asynchronous](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetAsync.html), [reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetReactive.html), and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetRx.html) interfaces.

Element uniqueness is determined by the serialized form of the element rather than by its `hashCode()`/`equals()` methods. `RSet` also implements [RExpirable](common-methods.md#expiration), so an expiration can be set on the set as a whole; expiring individual elements is a separate feature, described under [Entry eviction and TTL](#entry-eviction-and-ttl).

Variants add per-element eviction and data partitioning across a cluster, summarized under [Choosing a Set implementation](#choosing-a-set-implementation).

### Basic operations

`add`, `remove`, and `contains` operate on single elements, while `addAll`, `removeAll`, `retainAll`, and `containsAll` operate on collections. `tryAdd` adds one or more elements only if all of them are absent, returning whether the set changed.

=== "Sync"
    ```java
    RSet<SomeObject> set = redisson.getSet("mySet");

    boolean added = set.add(new SomeObject());
    boolean removed = set.remove(new SomeObject());
    boolean exists = set.contains(new SomeObject());
    boolean changed = set.tryAdd(new SomeObject(), new SomeObject());
    int size = set.size();
    ```
=== "Async"
    ```java
    RSetAsync<SomeObject> set = redisson.getSet("mySet");

    RFuture<Boolean> added = set.addAsync(new SomeObject());
    RFuture<Boolean> removed = set.removeAsync(new SomeObject());
    RFuture<Boolean> exists = set.containsAsync(new SomeObject());
    RFuture<Boolean> changed = set.tryAddAsync(new SomeObject(), new SomeObject());
    RFuture<Integer> size = set.sizeAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RSetReactive<SomeObject> set = redisson.getSet("mySet");

    Mono<Boolean> added = set.add(new SomeObject());
    Mono<Boolean> removed = set.remove(new SomeObject());
    Mono<Boolean> exists = set.contains(new SomeObject());
    Mono<Boolean> changed = set.tryAdd(new SomeObject(), new SomeObject());
    Mono<Integer> size = set.size();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RSetRx<SomeObject> set = redisson.getSet("mySet");

    Single<Boolean> added = set.add(new SomeObject());
    Single<Boolean> removed = set.remove(new SomeObject());
    Single<Boolean> exists = set.contains(new SomeObject());
    Single<Boolean> changed = set.tryAdd(new SomeObject(), new SomeObject());
    Single<Integer> size = set.size();
    ```

### Set algebra

Sets can be combined with other named sets on the server. The `read*` methods return the result and combine the named sets with this set, leaving it unchanged; `union`, `diff`, and `intersection` instead compute the combination of the named sets, **overwrite this set** with the result, and return its new size. `countIntersection` returns the size of an intersection without materializing it.

!!! note
    `union`, `diff`, and `intersection` overwrite this set with the result. Use `readUnion`, `readDiff`, and `readIntersection` to combine sets without changing this one.

=== "Sync"
    ```java
    RSet<SomeObject> set = redisson.getSet("mySet");

    // non-destructive: returns the result, this set is unchanged
    Set<SomeObject> u = set.readUnion("set2", "set3");
    Set<SomeObject> i = set.readIntersection("set2");
    Set<SomeObject> d = set.readDiff("set2");

    // destructive: overwrites this set, returns the new size
    int unionSize = set.union("set2", "set3");

    Integer common = set.countIntersection("set2");
    ```
=== "Async"
    ```java
    RSetAsync<SomeObject> set = redisson.getSet("mySet");

    RFuture<Set<SomeObject>> u = set.readUnionAsync("set2", "set3");
    RFuture<Set<SomeObject>> i = set.readIntersectionAsync("set2");
    RFuture<Set<SomeObject>> d = set.readDiffAsync("set2");

    RFuture<Integer> unionSize = set.unionAsync("set2", "set3");

    RFuture<Integer> common = set.countIntersectionAsync("set2");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RSetReactive<SomeObject> set = redisson.getSet("mySet");

    Mono<Set<SomeObject>> u = set.readUnion("set2", "set3");
    Mono<Set<SomeObject>> i = set.readIntersection("set2");
    Mono<Set<SomeObject>> d = set.readDiff("set2");

    Mono<Integer> unionSize = set.union("set2", "set3");

    Mono<Integer> common = set.countIntersection("set2");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RSetRx<SomeObject> set = redisson.getSet("mySet");

    Maybe<Set<SomeObject>> u = set.readUnion("set2", "set3");
    Maybe<Set<SomeObject>> i = set.readIntersection("set2");
    Maybe<Set<SomeObject>> d = set.readDiff("set2");

    Single<Integer> unionSize = set.union("set2", "set3");

    Single<Integer> common = set.countIntersection("set2");
    ```

### Random elements and moving

`random` returns a random element, or a subset, without removing it; `removeRandom` pops one or several elements at random; and `move` atomically transfers an element to another set.

=== "Sync"
    ```java
    RSet<SomeObject> set = redisson.getSet("mySet");

    SomeObject one = set.random();
    Set<SomeObject> some = set.random(3);

    SomeObject popped = set.removeRandom();
    Set<SomeObject> poppedMany = set.removeRandom(2);

    boolean moved = set.move("otherSet", one);
    ```
=== "Async"
    ```java
    RSetAsync<SomeObject> set = redisson.getSet("mySet");

    RFuture<SomeObject> one = set.randomAsync();
    RFuture<Set<SomeObject>> some = set.randomAsync(3);

    RFuture<SomeObject> popped = set.removeRandomAsync();
    RFuture<Set<SomeObject>> poppedMany = set.removeRandomAsync(2);

    RFuture<Boolean> moved = set.moveAsync("otherSet", one);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RSetReactive<SomeObject> set = redisson.getSet("mySet");

    Mono<SomeObject> one = set.random();
    Mono<Set<SomeObject>> some = set.random(3);

    Mono<SomeObject> popped = set.removeRandom();
    Mono<Set<SomeObject>> poppedMany = set.removeRandom(2);

    Mono<Boolean> moved = set.move("otherSet", one);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RSetRx<SomeObject> set = redisson.getSet("mySet");

    Maybe<SomeObject> one = set.random();
    Maybe<Set<SomeObject>> some = set.random(3);

    Maybe<SomeObject> popped = set.removeRandom();
    Maybe<Set<SomeObject>> poppedMany = set.removeRandom(2);

    Single<Boolean> moved = set.move("otherSet", one);
    ```

### Iterating

`readAll` pulls the whole set into memory in a single call - convenient for small sets, expensive for large ones.

=== "Sync"
    ```java
    RSet<SomeObject> set = redisson.getSet("mySet");

    Set<SomeObject> all = set.readAll();
    ```
=== "Async"
    ```java
    RSetAsync<SomeObject> set = redisson.getSet("mySet");

    RFuture<Set<SomeObject>> all = set.readAllAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RSetReactive<SomeObject> set = redisson.getSet("mySet");

    Mono<Set<SomeObject>> all = set.readAll();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RSetRx<SomeObject> set = redisson.getSet("mySet");

    Maybe<Set<SomeObject>> all = set.readAll();
    ```

To traverse a large set without loading it all at once, the synchronous `iterator()` streams elements through the server's `SCAN` cursor, and `distributedIterator` spreads the scan across a cluster.

### Per-value locks

An `RSet` can bind a [lock](locks-and-synchronizers.md#lock), [read/write lock](locks-and-synchronizers.md#readwritelock), or [semaphore](locks-and-synchronizers.md#semaphore) to an individual element, which is convenient for guarding work on one member.

```java
RSet<MyObject> set = redisson.getSet("anySet");
MyObject value = new MyObject();

RLock lock = set.getLock(value);
lock.lock();
try {
   // process value ...
} finally {
   lock.unlock();
}
```

### Choosing a Set implementation

Every implementation shares the operations above and differs in two capabilities - per-element eviction and data partitioning across a cluster - plus whether it requires [Redisson PRO](https://redisson.pro/feature-comparison.html). Data-partitioned sets (`getClusteredSet` and `getClusteredSetCache`) implement `RClusteredSet` and scale a single logical set across master nodes; see [data partitioning](data-partitioning.md). The table lists the main entry points, and the [feature comparison](https://redisson.pro/feature-comparison.html) enumerates every variant; in PRO, all types additionally provide ultra-fast read/write.

| Client method | Data partitioning | Per-element eviction | Availability |
| ------------- | :---------------: | :------------------: | ------------ |
| `getSet()` | ❌ | ❌ | open-source |
| `getSetCache()` | ❌ | ✔️ (scripted) | open-source |
| `getSetCacheV2()` | ✔️ | ✔️ (server-side) | PRO |
| `getClusteredSet()` | ✔️ | ❌ | PRO |
| `getClusteredSetCache()` | ✔️ | ✔️ | PRO |

### Entry eviction and TTL

Beyond the whole-set expiration that `RSet` inherits from `RExpirable`, the cache implementations attach a `time to live` to individual elements. `RSetCache` removes expired elements with a Redisson eviction task (one task per unique name, with extra calls; call `destroy()` when the instance is no longer used), while `RSetCacheV2` cleans them on the Valkey or Redis side without a task. `add` takes the TTL as arguments.

=== "Sync"
    ```java
    RSetCache<SomeObject> set = redisson.getSetCache("mySet");

    // ttl = 10 minutes
    set.add(new SomeObject(), 10, TimeUnit.MINUTES);

    set.destroy(); // when no longer used (scripted eviction)
    ```
=== "Async"
    ```java
    RSetCacheAsync<SomeObject> set = redisson.getSetCache("mySet");

    RFuture<Boolean> added = set.addAsync(new SomeObject(), 10, TimeUnit.MINUTES);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RSetCacheReactive<SomeObject> set = redisson.getSetCache("mySet");

    Mono<Boolean> added = set.add(new SomeObject(), 10, TimeUnit.MINUTES);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RSetCacheRx<SomeObject> set = redisson.getSetCache("mySet");

    Single<Boolean> added = set.add(new SomeObject(), 10, TimeUnit.MINUTES);
    ```

### Listeners

Redisson allows binding listeners per `RSet` object. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Element added/removed/updated after read operation| -|
|org.redisson.api.ExpiredObjectListener|`RSet` object expired| Ex|
|org.redisson.api.DeletedObjectListener|`RSet` object deleted| Eg|
|org.redisson.api.listener.SetAddListener|Element added| Es|
|org.redisson.api.listener.SetRemoveListener|Element removed| Es|
|org.redisson.api.listener.SetRemoveRandomListener|Element randomly removed|Es|

Usage example:

```java
RSet<String> set = redisson.getSet("anySet");

int listenerId = set.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

set.removeListener(listenerId);
```

## SortedSet

Redisson's [RSortedSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSortedSet.html) is a distributed implementation of Java's [SortedSet](https://docs.oracle.com/javase/8/docs/api/java/util/SortedSet.html) interface, backed by Valkey or Redis. It is thread-safe and keeps its elements in sorted order - by their natural ordering, or by a [Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html) supplied through `trySetComparator` - using that ordering to enforce uniqueness.

`RSortedSet` maintains order on the client side and exposes only the synchronous interface together with a few asynchronous methods; it has no reactive or RxJava3 variant, and the `SortedSet` range views (`subSet`, `headSet`, `tailSet`) are not supported. For most needs one of the purpose-built sorted structures is a better fit - see [Choosing between the sorted structures](#choosing-between-the-sorted-structures).

### Choosing between the sorted structures

Redisson offers three sorted structures with different strengths:

| Structure | Ordering | Elements | Interfaces |
| --------- | -------- | -------- | ---------- |
| `RSortedSet` | natural ordering or a `Comparator` | any serializable object | synchronous (plus some async) |
| [`RLexSortedSet`](#lexsortedset) | lexicographic | `String` only | sync, async, reactive, RxJava3 |
| [`RScoredSortedSet`](#scoredsortedset) | by an explicit numeric score | any serializable object | sync, async, reactive, RxJava3 |

`RLexSortedSet` and `RScoredSortedSet` are backed by a native sorted set and scale far better, so prefer [LexSortedSet](#lexsortedset) for ordered `String` data and [ScoredSortedSet](#scoredsortedset) when elements are ranked by a score. Reach for `RSortedSet` only when you specifically need `Comparator`-based ordering of custom objects over a small set.

### Basic usage

If you need a custom ordering, call `trySetComparator` before adding any elements; it returns `false` if the set already contains elements (otherwise the natural ordering of `Comparable` elements is used). `add`, `remove`, and `contains` then behave as on any set.

```java
RSortedSet<Integer> set = redisson.getSortedSet("mySet");
set.trySetComparator(Comparator.reverseOrder()); // optional; before the first add

set.add(3);
set.add(1);
set.add(2);

boolean removed = set.remove(1);
boolean exists = set.contains(2);
```

`add`, `remove`, `readAll`, and the polling methods also have asynchronous (`RFuture`) forms, for example `addAsync` and `removeAsync`.

### Reading in order

`first` and `last` return the lowest and highest elements, `readAll` returns every element in sorted order, and the set can be traversed lazily with `iterator()` or, across a cluster, `distributedIterator()`.

```java
Integer lowest = set.first();
Integer highest = set.last();

Collection<Integer> ordered = set.readAll(); // all elements, in order
int size = set.size();

for (Integer value : set) {
    // iterates in sorted order
}
```

### Polling

`pollFirst` and `pollLast` remove and return the lowest or highest element. Count variants return several at once, and `Duration` variants block until an element is available or the timeout elapses.

```java
Integer first = set.pollFirst();               // remove and return the lowest
Collection<Integer> firstThree = set.pollFirst(3);

Integer last = set.pollLast();                 // remove and return the highest

// block up to 10 seconds for an element to appear
Integer awaited = set.pollFirst(Duration.ofSeconds(10));
```

### Limitations

`RSortedSet` keeps elements ordered on the client side, so insertions and reads grow more expensive as the set grows; it is not suited to large or high-churn data. It also has no reactive or RxJava3 interface, and the `java.util.SortedSet` range views - `subSet`, `headSet`, and `tailSet` - throw `UnsupportedOperationException`. For ordered `String` data use [LexSortedSet](#lexsortedset), and for score-ranked data use [ScoredSortedSet](#scoredsortedset).

## ScoredSortedSet

Redisson's [RScoredSortedSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSet.html) is a distributed sorted set (a Valkey or Redis sorted set): every element is stored with an associated `double` score, and the set is kept ordered by that score. Elements are unique by their serialized state, the set holds up to 4,294,967,295 of them, and it is available through synchronous, [asynchronous](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSetAsync.html), [reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSetReactive.html), and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSetRx.html) interfaces.

Elements can be queried two ways - by **rank** (their 0-based position in score order) or by **score range**. Read methods come in a `value*` form that returns the elements and an `entry*` form that returns [ScoredEntry](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/ScoredEntry.html) objects pairing each value with its score.

For lexicographic ordering of `String` elements see [LexSortedSet](#lexsortedset), and for `Comparator`-based ordering of arbitrary objects see [SortedSet](#sortedset).

### Basic operations

`add` stores an element with a score (replacing the score if the element already exists), and `addAll` stores many at once. `addScore` atomically increments an element's score and returns the new value, `getScore` reads it, and `remove` deletes an element. The conditional forms `addIfAbsent`, `addIfExists`, `addIfGreater`, `addIfLess`, and `tryAdd` add only when their condition holds.

=== "Sync"
    ```java
    RScoredSortedSet<SomeObject> set = redisson.getScoredSortedSet("mySet");

    SomeObject value = new SomeObject();
    boolean added = set.add(1.5, value);
    set.addAll(Map.of(new SomeObject(), 2.0, new SomeObject(), 3.0));
    Double newScore = set.addScore(value, 0.5); // increment the score
    Double score = set.getScore(value);
    boolean removed = set.remove(value);
    ```
=== "Async"
    ```java
    RScoredSortedSetAsync<SomeObject> set = redisson.getScoredSortedSet("mySet");

    SomeObject value = new SomeObject();
    RFuture<Boolean> added = set.addAsync(1.5, value);
    RFuture<Integer> count = set.addAllAsync(Map.of(new SomeObject(), 2.0));
    RFuture<Double> newScore = set.addScoreAsync(value, 0.5);
    RFuture<Double> score = set.getScoreAsync(value);
    RFuture<Boolean> removed = set.removeAsync(value);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RScoredSortedSetReactive<SomeObject> set = redisson.getScoredSortedSet("mySet");

    SomeObject value = new SomeObject();
    Mono<Boolean> added = set.add(1.5, value);
    Mono<Integer> count = set.addAll(Map.of(new SomeObject(), 2.0));
    Mono<Double> newScore = set.addScore(value, 0.5);
    Mono<Double> score = set.getScore(value);
    Mono<Boolean> removed = set.remove(value);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RScoredSortedSetRx<SomeObject> set = redisson.getScoredSortedSet("mySet");

    SomeObject value = new SomeObject();
    Single<Boolean> added = set.add(1.5, value);
    Single<Integer> count = set.addAll(Map.of(new SomeObject(), 2.0));
    Single<Double> newScore = set.addScore(value, 0.5);
    Maybe<Double> score = set.getScore(value);
    Single<Boolean> removed = set.remove(value);
    ```

### Ranking

`rank` returns the 0-based position of an element in ascending score order and `revRank` in descending order; both are empty when the element is absent. `addAndGetRank` adds an element at an absolute score and returns its rank, while `addScoreAndGetRank` increments an element's score and returns its new rank - the typical leaderboard update. `rankEntry`/`revRankEntry` return the rank together with the score.

=== "Sync"
    ```java
    RScoredSortedSet<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Integer rank = set.rank(value);          // lowest score = 0
    Integer revRank = set.revRank(value);    // highest score = 0
    Integer newRank = set.addAndGetRank(2.5, value);
    Integer afterBump = set.addScoreAndGetRank(value, 0.5); // increment, get new rank
    ```
=== "Async"
    ```java
    RScoredSortedSetAsync<SomeObject> set = redisson.getScoredSortedSet("mySet");

    RFuture<Integer> rank = set.rankAsync(value);
    RFuture<Integer> revRank = set.revRankAsync(value);
    RFuture<Integer> newRank = set.addAndGetRankAsync(2.5, value);
    RFuture<Integer> afterBump = set.addScoreAndGetRankAsync(value, 0.5);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RScoredSortedSetReactive<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Mono<Integer> rank = set.rank(value);
    Mono<Integer> revRank = set.revRank(value);
    Mono<Integer> newRank = set.addAndGetRank(2.5, value);
    Mono<Integer> afterBump = set.addScoreAndGetRank(value, 0.5);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RScoredSortedSetRx<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Maybe<Integer> rank = set.rank(value);
    Maybe<Integer> revRank = set.revRank(value);
    Single<Integer> newRank = set.addAndGetRank(2.5, value);
    Single<Integer> afterBump = set.addScoreAndGetRank(value, 0.5);
    ```

### Range queries

Elements can be read by rank or by score. Rank ranges use 0-based indices, where negative values count back from the end (`-1` is the last element). Score ranges take a lower and upper bound, each with a flag marking it inclusive or exclusive, and accept an optional `offset`/`count` for paging. Every query has a `Reversed` form that walks from the highest score down, and an `entryRange` counterpart that returns `ScoredEntry` results carrying the scores.

=== "Sync"
    ```java
    RScoredSortedSet<SomeObject> set = redisson.getScoredSortedSet("mySet");

    // by rank (position)
    Collection<SomeObject> top3 = set.valueRangeReversed(0, 2);  // 3 highest
    Collection<SomeObject> all = set.valueRange(0, -1);          // ascending
    Collection<ScoredEntry<SomeObject>> withScores = set.entryRange(0, -1);

    // by score: 1.0 <= score < 5.0
    Collection<SomeObject> band = set.valueRange(1.0, true, 5.0, false);
    Collection<SomeObject> page = set.valueRange(1.0, true, 5.0, false, 0, 25); // first 25 in band
    int inBand = set.count(1.0, true, 5.0, false);
    ```
=== "Async"
    ```java
    RScoredSortedSetAsync<SomeObject> set = redisson.getScoredSortedSet("mySet");

    RFuture<Collection<SomeObject>> top3 = set.valueRangeReversedAsync(0, 2);
    RFuture<Collection<SomeObject>> all = set.valueRangeAsync(0, -1);
    RFuture<Collection<ScoredEntry<SomeObject>>> withScores = set.entryRangeAsync(0, -1);

    RFuture<Collection<SomeObject>> band = set.valueRangeAsync(1.0, true, 5.0, false);
    RFuture<Collection<SomeObject>> page = set.valueRangeAsync(1.0, true, 5.0, false, 0, 25);
    RFuture<Integer> inBand = set.countAsync(1.0, true, 5.0, false);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RScoredSortedSetReactive<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Mono<Collection<SomeObject>> top3 = set.valueRangeReversed(0, 2);
    Mono<Collection<SomeObject>> all = set.valueRange(0, -1);
    Mono<Collection<ScoredEntry<SomeObject>>> withScores = set.entryRange(0, -1);

    Mono<Collection<SomeObject>> band = set.valueRange(1.0, true, 5.0, false);
    Mono<Collection<SomeObject>> page = set.valueRange(1.0, true, 5.0, false, 0, 25);
    Mono<Integer> inBand = set.count(1.0, true, 5.0, false);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RScoredSortedSetRx<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Maybe<Collection<SomeObject>> top3 = set.valueRangeReversed(0, 2);
    Maybe<Collection<SomeObject>> all = set.valueRange(0, -1);
    Maybe<Collection<ScoredEntry<SomeObject>>> withScores = set.entryRange(0, -1);

    Maybe<Collection<SomeObject>> band = set.valueRange(1.0, true, 5.0, false);
    Maybe<Collection<SomeObject>> page = set.valueRange(1.0, true, 5.0, false, 0, 25);
    Single<Integer> inBand = set.count(1.0, true, 5.0, false);
    ```

### First, last, and polling

`first`/`last` and `firstScore`/`lastScore` read the extreme elements and their scores, and `firstEntry`/`lastEntry` return both as a `ScoredEntry`. `pollFirst`/`pollLast` remove and return the lowest or highest element, with count variants for several at once.

=== "Sync"
    ```java
    RScoredSortedSet<SomeObject> set = redisson.getScoredSortedSet("mySet");

    SomeObject lowest = set.first();
    SomeObject highest = set.last();
    Double lowestScore = set.firstScore();
    ScoredEntry<SomeObject> firstEntry = set.firstEntry();

    SomeObject popped = set.pollFirst();      // remove and return the lowest
    Collection<SomeObject> poppedFew = set.pollFirst(3);
    ```
=== "Async"
    ```java
    RScoredSortedSetAsync<SomeObject> set = redisson.getScoredSortedSet("mySet");

    RFuture<SomeObject> lowest = set.firstAsync();
    RFuture<SomeObject> highest = set.lastAsync();
    RFuture<Double> lowestScore = set.firstScoreAsync();
    RFuture<ScoredEntry<SomeObject>> firstEntry = set.firstEntryAsync();

    RFuture<SomeObject> popped = set.pollFirstAsync();
    RFuture<Collection<SomeObject>> poppedFew = set.pollFirstAsync(3);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RScoredSortedSetReactive<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Mono<SomeObject> lowest = set.first();
    Mono<SomeObject> highest = set.last();
    Mono<Double> lowestScore = set.firstScore();
    Mono<ScoredEntry<SomeObject>> firstEntry = set.firstEntry();

    Mono<SomeObject> popped = set.pollFirst();
    Mono<Collection<SomeObject>> poppedFew = set.pollFirst(3);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RScoredSortedSetRx<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Maybe<SomeObject> lowest = set.first();
    Maybe<SomeObject> highest = set.last();
    Maybe<Double> lowestScore = set.firstScore();
    Maybe<ScoredEntry<SomeObject>> firstEntry = set.firstEntry();

    Maybe<SomeObject> popped = set.pollFirst();
    Maybe<Collection<SomeObject>> poppedFew = set.pollFirst(3);
    ```

`pollFirst`/`pollLast` also have blocking forms that take a `Duration` timeout, `pollFirstEntry`/`pollLastEntry` return the popped element with its score, and `pollFirstFromAny`/`pollLastFromAny` pop across several sets in one call.

### Removing by rank or score

`removeRangeByRank` and `removeRangeByScore` delete a whole slice of the set in one call and return how many elements were removed - the natural complement to the range queries above.

=== "Sync"
    ```java
    RScoredSortedSet<SomeObject> set = redisson.getScoredSortedSet("mySet");

    int byRank = set.removeRangeByRank(0, 9);                   // the 10 lowest
    int byScore = set.removeRangeByScore(0.0, true, 1.0, false); // 0.0 <= score < 1.0
    ```
=== "Async"
    ```java
    RScoredSortedSetAsync<SomeObject> set = redisson.getScoredSortedSet("mySet");

    RFuture<Integer> byRank = set.removeRangeByRankAsync(0, 9);
    RFuture<Integer> byScore = set.removeRangeByScoreAsync(0.0, true, 1.0, false);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RScoredSortedSetReactive<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Mono<Integer> byRank = set.removeRangeByRank(0, 9);
    Mono<Integer> byScore = set.removeRangeByScore(0.0, true, 1.0, false);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RScoredSortedSetRx<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Single<Integer> byRank = set.removeRangeByRank(0, 9);
    Single<Integer> byScore = set.removeRangeByScore(0.0, true, 1.0, false);
    ```

### Set algebra

A scored sorted set can be combined with other named sets. The `read*` methods return the result and leave this set unchanged, while `union`, `diff`, and `intersection` overwrite this set with the result and return its size. `countIntersection` returns the size of an intersection without materializing it.

!!! note
    `union`, `diff`, and `intersection` overwrite this set with the result. Use `readUnion`, `readDiff`, and `readIntersection` to combine sets without changing this one.

=== "Sync"
    ```java
    RScoredSortedSet<SomeObject> set = redisson.getScoredSortedSet("mySet");

    // non-destructive: returns the result, this set is unchanged
    Collection<SomeObject> u = set.readUnion("set2", "set3");
    Collection<SomeObject> i = set.readIntersection("set2");
    // SUM scores across sets, weighting set3 twice
    Collection<SomeObject> weighted = set.readUnion(Aggregate.SUM, Map.of("set2", 1.0, "set3", 2.0));

    // destructive: overwrites this set, returns the new size
    int unionSize = set.union("set2", "set3");

    Integer common = set.countIntersection("set2");
    ```
=== "Async"
    ```java
    RScoredSortedSetAsync<SomeObject> set = redisson.getScoredSortedSet("mySet");

    RFuture<Collection<SomeObject>> u = set.readUnionAsync("set2", "set3");
    RFuture<Collection<SomeObject>> i = set.readIntersectionAsync("set2");
    RFuture<Collection<SomeObject>> weighted = set.readUnionAsync(Aggregate.SUM, Map.of("set2", 1.0, "set3", 2.0));

    RFuture<Integer> unionSize = set.unionAsync("set2", "set3");

    RFuture<Integer> common = set.countIntersectionAsync("set2");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RScoredSortedSetReactive<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Mono<Collection<SomeObject>> u = set.readUnion("set2", "set3");
    Mono<Collection<SomeObject>> i = set.readIntersection("set2");
    Mono<Collection<SomeObject>> weighted = set.readUnion(Aggregate.SUM, Map.of("set2", 1.0, "set3", 2.0));

    Mono<Integer> unionSize = set.union("set2", "set3");

    Mono<Integer> common = set.countIntersection("set2");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RScoredSortedSetRx<SomeObject> set = redisson.getScoredSortedSet("mySet");

    Maybe<Collection<SomeObject>> u = set.readUnion("set2", "set3");
    Maybe<Collection<SomeObject>> i = set.readIntersection("set2");
    Maybe<Collection<SomeObject>> weighted = set.readUnion(Aggregate.SUM, Map.of("set2", 1.0, "set3", 2.0));

    Single<Integer> unionSize = set.union("set2", "set3");

    Single<Integer> common = set.countIntersection("set2");
    ```

Overloads accept an `Aggregate` (`SUM`, `MIN`, or `MAX`) and per-set weights, and `readUnionEntries`/`readIntersectionEntries`/`readDiffEntries` return `ScoredEntry` results; see the javadoc for the full set.

### Data partitioning

Although 'RScoredSortedSet' object is cluster compatible its content isn't scaled across multiple master nodes. `RScoredSortedSet` data partitioning available only in cluster mode and implemented by separate `RClusteredScoredSortedSet` object. Size is limited by whole Cluster memory. More about partitioning [here](data-partitioning.md).

Below is the list of all available `RScoredSortedSet` implementations:  

|RedissonClient <br/> method name | Data partitioning <br/> support | Ultra-fast read/write |
| ------------- | :----------:| :----------:|
|getScoredSortedSet()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ |
|getScoredSortedSet()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ✔️ |
|getClusteredScoredSortedSet()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ |

Code example:
```java
RClusteredScoredSortedSet set = redisson.getClusteredScoredSortedSet("myScoredSet");
set.add(1.1, "v1");
set.add(1.2, "v2");
set.add(1.3, "v3");

ScoredEntry<String> s = set.firstEntry();
ScoredEntry<String> e = set.pollFirstEntry();
```

### Listeners

Redisson allows binding listeners per `RScoredSortedSet` object. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Element created/removed/updated after read operation| - |
|org.redisson.api.listener.ScoredSortedSetAddListener|Element created/updated|Ez|
|org.redisson.api.listener.ScoredSortedSetRemoveListener|Element removed|Ez|
|org.redisson.api.ExpiredObjectListener|`RScoredSortedSet` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RScoredSortedSet` object deleted|Eg|

Usage example:

```java
RScoredSortedSet<String> set = redisson.getScoredSortedSet("anySet");

int listenerId = set.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

set.removeListener(listenerId);
```

{% include 'data-and-services/lexsortedset.md' %}

## List

Redisson's `RList` object implements the [List](https://docs.oracle.com/javase/8/docs/api/java/util/List.html) interface, providing a distributed and concurrent list backed by Valkey or Redis. This allows multiple applications or servers to share and manipulate list data seamlessly.

### Basic Operations

`RList` provides all standard Java List operations including adding, removing, and checking for elements.

Code example of creating and adding elements:

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Add elements to the end
    list.add("element1");
    list.add("element2");
    
    // Add multiple elements at once
    list.addAll(Arrays.asList("element3", "element4", "element5"));
    
    // Add element at specific position
    list.add(0, "firstElement");
    
    // Add element before another element
    list.addBefore("element2", "beforeElement2");
    
    // Add element after another element
    list.addAfter("element2", "afterElement2");
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Add elements to the end
    RFuture<Boolean> future1 = list.addAsync("element1");
    RFuture<Boolean> future2 = list.addAsync("element2");
    
    // Add multiple elements at once
    RFuture<Boolean> future3 = list.addAllAsync(Arrays.asList("element3", "element4", "element5"));
    
    // Add element at specific position
    RFuture<Void> future4 = list.addAsync(0, "firstElement");
    
    // Add element before another element
    RFuture<Integer> future5 = list.addBeforeAsync("element2", "beforeElement2");
    
    // Add element after another element
    RFuture<Integer> future6 = list.addAfterAsync("element2", "afterElement2");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    // Add elements to the end
    Mono<Boolean> mono1 = list.add("element1");
    Mono<Boolean> mono2 = list.add("element2");
    
    // Add multiple elements at once
    Mono<Boolean> mono3 = list.addAll(Arrays.asList("element3", "element4", "element5"));
    
    // Add element at specific position
    Mono<Void> mono4 = list.add(0, "firstElement");
    
    // Add element before another element
    Mono<Integer> mono5 = list.addBefore("element2", "beforeElement2");
    
    // Add element after another element
    Mono<Integer> mono6 = list.addAfter("element2", "afterElement2");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    // Add elements to the end
    Single<Boolean> single1 = list.add("element1");
    Single<Boolean> single2 = list.add("element2");
    
    // Add multiple elements at once
    Single<Boolean> single3 = list.addAll(Arrays.asList("element3", "element4", "element5"));
    
    // Add element at specific position
    Completable completable = list.add(0, "firstElement");
    
    // Add element before another element
    Single<Integer> single4 = list.addBefore("element2", "beforeElement2");
    
    // Add element after another element
    Single<Integer> single5 = list.addAfter("element2", "afterElement2");
    ```

Code example of removing elements:

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Remove by object
    boolean removed = list.remove("element1");
    
    // Remove by index
    String removedElement = list.remove(0);
    
    // Remove multiple elements
    list.removeAll(Arrays.asList("element2", "element3"));
    
    // Remove elements not in the specified collection
    list.retainAll(Arrays.asList("element4", "element5"));
    
    // Fast remove the element at the given index (no return value)
    list.fastRemove(0);
    
    // Remove up to N occurrences of a value (LREM)
    boolean removedOccurrences = list.remove("element2", 2);
    
    // Clear all elements
    list.clear();
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Remove by object
    RFuture<Boolean> future1 = list.removeAsync("element1");
    
    // Remove by index
    RFuture<String> future2 = list.removeAsync(0);
    
    // Remove multiple elements
    RFuture<Boolean> future3 = list.removeAllAsync(Arrays.asList("element2", "element3"));
    
    // Remove elements not in the specified collection
    RFuture<Boolean> future4 = list.retainAllAsync(Arrays.asList("element4", "element5"));
    
    // Fast remove the element at the given index (no return value)
    RFuture<Void> future5 = list.fastRemoveAsync(0);
    
    // Remove up to N occurrences of a value (LREM)
    RFuture<Boolean> future7 = list.removeAsync("element2", 2);
    
    // Clear all elements
    RFuture<Boolean> future6 = list.deleteAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    // Remove by object
    Mono<Boolean> mono1 = list.remove("element1");
    
    // Remove by index
    Mono<String> mono2 = list.remove(0);
    
    // Remove multiple elements
    Mono<Boolean> mono3 = list.removeAll(Arrays.asList("element2", "element3"));
    
    // Remove elements not in the specified collection
    Mono<Boolean> mono4 = list.retainAll(Arrays.asList("element4", "element5"));
    
    // Fast remove the element at the given index (no return value)
    Mono<Void> mono5 = list.fastRemove(0);
    
    // Clear all elements
    Mono<Boolean> mono6 = list.delete();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    // Remove by object
    Single<Boolean> single1 = list.remove("element1");
    
    // Remove by index
    Single<String> single2 = list.remove(0);
    
    // Remove multiple elements
    Single<Boolean> single3 = list.removeAll(Arrays.asList("element2", "element3"));
    
    // Remove elements not in the specified collection
    Single<Boolean> single4 = list.retainAll(Arrays.asList("element4", "element5"));
    
    // Fast remove the element at the given index (no return value)
    Completable completable = list.fastRemove(0);
    
    // Clear all elements
    Single<Boolean> single5 = list.delete();
    ```

The `remove(element, count)` form shown above removes up to a given number of occurrences of a value and is available on the synchronous and asynchronous interfaces only.

Code example of checking and searching:

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Check if element exists
    boolean contains = list.contains("element1");
    
    // Check if all elements exist
    boolean containsAll = list.containsAll(Arrays.asList("element1", "element2"));
    
    // Check if list is empty
    boolean isEmpty = list.isEmpty();
    
    // Get list size
    int size = list.size();
    
    // Find index of element
    int index = list.indexOf("element1");
    int lastIndex = list.lastIndexOf("element1");
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Check if element exists
    RFuture<Boolean> future1 = list.containsAsync("element1");
    
    // Check if all elements exist
    RFuture<Boolean> future2 = list.containsAllAsync(Arrays.asList("element1", "element2"));
    
    // Get list size
    RFuture<Integer> future3 = list.sizeAsync();
    
    // Find index of element
    RFuture<Integer> future4 = list.indexOfAsync("element1");
    RFuture<Integer> future5 = list.lastIndexOfAsync("element1");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    // Check if element exists
    Mono<Boolean> mono1 = list.contains("element1");
    
    // Check if all elements exist
    Mono<Boolean> mono2 = list.containsAll(Arrays.asList("element1", "element2"));
    
    // Get list size
    Mono<Integer> mono3 = list.size();
    
    // Find index of element
    Mono<Integer> mono4 = list.indexOf("element1");
    Mono<Integer> mono5 = list.lastIndexOf("element1");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    // Check if element exists
    Single<Boolean> single1 = list.contains("element1");
    
    // Check if all elements exist
    Single<Boolean> single2 = list.containsAll(Arrays.asList("element1", "element2"));
    
    // Get list size
    Single<Integer> single3 = list.size();
    
    // Find index of element
    Single<Integer> single4 = list.indexOf("element1");
    Single<Integer> single5 = list.lastIndexOf("element1");
    ```

### Indexing and Access Patterns

`RList` provides multiple ways to access elements by index, including bulk operations for efficient data retrieval.

Code example of single element access:

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Get element at index (0-based)
    String element = list.get(0);
    
    // Set element at index
    String oldValue = list.set(0, "newElement");
    
    // Fast set without returning old value
    list.fastSet(0, "anotherElement");
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Get element at index (0-based)
    RFuture<String> future1 = list.getAsync(0);
    
    // Set element at index
    RFuture<String> future2 = list.setAsync(0, "newElement");
    
    // Fast set without returning old value
    RFuture<Void> future3 = list.fastSetAsync(0, "anotherElement");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    // Get element at index (0-based)
    Mono<String> mono1 = list.get(0);
    
    // Set element at index
    Mono<String> mono2 = list.set(0, "newElement");
    
    // Fast set without returning old value
    Mono<Void> mono3 = list.fastSet(0, "anotherElement");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    // Get element at index (0-based)
    Single<String> single1 = list.get(0);
    
    // Set element at index
    Single<String> single2 = list.set(0, "newElement");
    
    // Fast set without returning old value
    Completable completable = list.fastSet(0, "anotherElement");
    ```

Code example of bulk element access:

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Get elements at multiple indexes
    List<String> elements = list.get(0, 2, 4, 6);
    
    // Read all elements at once
    List<String> allElements = list.readAll();
    
    // Get range of elements (inclusive indexes)
    // Negative indexes supported: -1 = last element, -2 = second to last, etc.
    List<String> rangeEnd = list.range(5);         // Elements from index 0 to 5
    List<String> rangeFromTo = list.range(2, 8);   // Elements from index 2 to 8
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Get elements at multiple indexes
    RFuture<List<String>> future1 = list.getAsync(0, 2, 4, 6);
    
    // Read all elements at once
    RFuture<List<String>> future2 = list.readAllAsync();
    
    // Get range of elements (inclusive indexes)
    RFuture<List<String>> future3 = list.rangeAsync(5);        // Elements from index 0 to 5
    RFuture<List<String>> future4 = list.rangeAsync(2, 8);     // Elements from index 2 to 8
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    // Get elements at multiple indexes
    Mono<List<String>> mono1 = list.get(0, 2, 4, 6);
    
    // Read all elements at once
    Mono<List<String>> mono2 = list.readAll();
    
    // Get range of elements (inclusive indexes)
    Mono<List<String>> mono3 = list.range(5);       // Elements from index 0 to 5
    Mono<List<String>> mono4 = list.range(2, 8);    // Elements from index 2 to 8
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    // Get elements at multiple indexes
    Single<List<String>> single1 = list.get(0, 2, 4, 6);
    
    // Read all elements at once
    Single<List<String>> single2 = list.readAll();
    
    // Get range of elements (inclusive indexes)
    Single<List<String>> single3 = list.range(5);       // Elements from index 0 to 5
    Single<List<String>> single4 = list.range(2, 8);    // Elements from index 2 to 8
    ```

Code example of subList and trimming:

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Get a sublist (returns a view backed by the original list)
    RList<String> subList = list.subList(2, 5);
    
    // Trim list to keep only elements in specified range (inclusive)
    // All other elements are removed
    list.trim(0, 9);    // Keep only first 10 elements
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Trim list to keep only elements in specified range (inclusive)
    RFuture<Void> future = list.trimAsync(0, 9);    // Keep only first 10 elements
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    // Trim list to keep only elements in specified range (inclusive)
    Mono<Void> mono = list.trim(0, 9);    // Keep only first 10 elements
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    // Trim list to keep only elements in specified range (inclusive)
    Completable completable = list.trim(0, 9);    // Keep only first 10 elements
    ```

Code example of iteration:

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Standard iteration (bulk-loaded for efficiency)
    for (String element : list) {
        System.out.println(element);
    }
    
    // Using ListIterator
    ListIterator<String> iterator = list.listIterator();
    while (iterator.hasNext()) {
        String element = iterator.next();
        // Can also use iterator.previous(), iterator.set(), etc.
    }
    
    // Distributed iterator (shared across multiple applications)
    Iterator<String> distIterator = list.distributedIterator("iteratorName", 100);
    while (distIterator.hasNext()) {
        String element = distIterator.next();
    }
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Read all and iterate
    RFuture<List<String>> future = list.readAllAsync();
    future.whenComplete((elements, exception) -> {
        if (exception == null) {
            for (String element : elements) {
                System.out.println(element);
            }
        }
    });
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    // Iterate using Flux
    Flux<String> flux = list.iterator();
    flux.subscribe(element -> System.out.println(element));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    // Iterate using Flowable
    Flowable<String> flowable = list.iterator();
    flowable.subscribe(element -> System.out.println(element));
    ```

### Sorting

`RList` is sortable. `readSort` returns the elements ordered numerically without modifying the list, while `sortTo` sorts and stores the result into another list, returning the destination size. The read method is named `readSorted` on the Reactive and RxJava3 interfaces.

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Return elements sorted, without modifying the list
    List<String> sorted = list.readSort(SortOrder.ASC);
    
    // Sort and store the result into another list; returns its size
    int size = list.sortTo("destList", SortOrder.ASC);
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    RFuture<List<String>> sorted = list.readSortAsync(SortOrder.ASC);
    RFuture<Integer> size = list.sortToAsync("destList", SortOrder.ASC);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    Mono<List<String>> sorted = list.readSorted(SortOrder.ASC);
    Mono<Integer> size = list.sortTo("destList", SortOrder.ASC);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    Single<List<String>> sorted = list.readSorted(SortOrder.ASC);
    Single<Integer> size = list.sortTo("destList", SortOrder.ASC);
    ```

By default `readSort` orders elements numerically; the `readSortAlpha` variants sort lexicographically, and further overloads sort by an external key pattern, fetch other keys per element, and page the result - see the [RSortable](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSortable.html) javadoc.

### Listeners

Redisson allows binding listeners to `RList` objects to receive notifications on list modifications. 
This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

Available Listener Types:

| Listener | Event | Description | Valkey or Redis <br/> `notify-keyspace-events` value |
|----------|:------:|:-----------:|:------------:|
| `ListAddListener` | `onListAdd` | Triggered when an element is added to the list | El |
| `ListInsertListener` | `onListInsert` | Triggered when an element is inserted into the list | El |
| `ListSetListener` | `onListSet` | Triggered when an element value is changed | El |
| `ListRemoveListener` | `onListRemove` | Triggered when an element is removed from the list | El |
| `ListTrimListener` | `onListTrim` | Triggered when the list is trimmed | El |
| `ExpiredObjectListener` | `onExpired` | Triggered when the list expires | Ex |
| `DeletedObjectListener` | `onDeleted` | Triggered when the list is deleted | Eg |

Code example of adding Listeners:

=== "Sync"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Add listener for element additions
    int addListenerId = list.addListener(new ListAddListener() {
        @Override
        public void onListAdd(String name) {
            System.out.println("Element added to list: " + name);
        }
    });
    
    // Add listener for element insertions
    int insertListenerId = list.addListener(new ListInsertListener() {
        @Override
        public void onListInsert(String name) {
            System.out.println("Element inserted into list: " + name);
        }
    });
    
    // Add listener for element value changes
    int setListenerId = list.addListener(new ListSetListener() {
        @Override
        public void onListSet(String name) {
            System.out.println("Element value changed in list: " + name);
        }
    });
    
    // Add listener for element removals
    int removeListenerId = list.addListener(new ListRemoveListener() {
        @Override
        public void onListRemove(String name) {
            System.out.println("Element removed from list: " + name);
        }
    });
    
    // Add listener for list trimming
    int trimListenerId = list.addListener(new ListTrimListener() {
        @Override
        public void onListTrim(String name) {
            System.out.println("List trimmed: " + name);
        }
    });
    
    // Add listener for list expiration
    int expireListenerId = list.addListener(new ExpiredObjectListener() {
        @Override
        public void onExpired(String name) {
            System.out.println("List expired: " + name);
        }
    });
    
    // Add listener for list deletion
    int deleteListenerId = list.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            System.out.println("List deleted: " + name);
        }
    });
    
    // Remove listeners when no longer needed
    list.removeListener(addListenerId);
    list.removeListener(insertListenerId);
    list.removeListener(setListenerId);
    list.removeListener(removeListenerId);
    list.removeListener(trimListenerId);
    list.removeListener(expireListenerId);
    list.removeListener(deleteListenerId);
    ```
=== "Async"
    ```java
    RList<String> list = redisson.getList("myList");
    
    // Add listener for element additions
    RFuture<Integer> addListenerFuture = list.addListenerAsync(new ListAddListener() {
        @Override
        public void onListAdd(String name) {
            System.out.println("Element added to list: " + name);
        }
    });
    
    // Add listener for element removals
    RFuture<Integer> removeListenerFuture = list.addListenerAsync(new ListRemoveListener() {
        @Override
        public void onListRemove(String name) {
            System.out.println("Element removed from list: " + name);
        }
    });
    
    // Add listener for list expiration
    RFuture<Integer> expireListenerFuture = list.addListenerAsync(new ExpiredObjectListener() {
        @Override
        public void onExpired(String name) {
            System.out.println("List expired: " + name);
        }
    });
    
    // Add listener for list deletion
    RFuture<Integer> deleteListenerFuture = list.addListenerAsync(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            System.out.println("List deleted: " + name);
        }
    });
    
    // Remove listeners
    addListenerFuture.thenAccept(listenerId -> {
        list.removeListenerAsync(listenerId);
    });
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RListReactive<String> list = redisson.getList("myList");
    
    // Add listener for element additions
    Mono<Integer> addListenerMono = list.addListener(new ListAddListener() {
        @Override
        public void onListAdd(String name) {
            System.out.println("Element added to list: " + name);
        }
    });
    
    // Add listener for element removals
    Mono<Integer> removeListenerMono = list.addListener(new ListRemoveListener() {
        @Override
        public void onListRemove(String name) {
            System.out.println("Element removed from list: " + name);
        }
    });
    
    // Add listener for list expiration
    Mono<Integer> expireListenerMono = list.addListener(new ExpiredObjectListener() {
        @Override
        public void onExpired(String name) {
            System.out.println("List expired: " + name);
        }
    });
    
    // Add listener for list deletion
    Mono<Integer> deleteListenerMono = list.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            System.out.println("List deleted: " + name);
        }
    });
    
    // Remove listener
    addListenerMono
        .flatMap(listenerId -> list.removeListener(listenerId))
        .subscribe();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RListRx<String> list = redisson.getList("myList");
    
    // Add listener for element additions
    Single<Integer> addListenerSingle = list.addListener(new ListAddListener() {
        @Override
        public void onListAdd(String name) {
            System.out.println("Element added to list: " + name);
        }
    });
    
    // Add listener for element removals
    Single<Integer> removeListenerSingle = list.addListener(new ListRemoveListener() {
        @Override
        public void onListRemove(String name) {
            System.out.println("Element removed from list: " + name);
        }
    });
    
    // Add listener for list expiration
    Single<Integer> expireListenerSingle = list.addListener(new ExpiredObjectListener() {
        @Override
        public void onExpired(String name) {
            System.out.println("List expired: " + name);
        }
    });
    
    // Add listener for list deletion
    Single<Integer> deleteListenerSingle = list.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            System.out.println("List deleted: " + name);
        }
    });
    
    // Remove listener
    addListenerSingle
        .flatMapCompletable(listenerId -> list.removeListener(listenerId))
        .subscribe();
    ```

## Time Series

Redisson's `RTimeSeries` object is a distributed structure for storing and querying time-stamped data on Valkey or Redis. Each entry pairs a unique `long` timestamp with a value and, optionally, a *label* - a secondary value stored and returned alongside the entry. Entries are kept ordered by timestamp, which makes `RTimeSeries` a natural fit for metrics, sensor readings, financial ticks, and event logs.

The object is typed as `RTimeSeries<V, L>`, where `V` is the value type and `L` the label type (use any type, such as `Object`, when labels aren't needed). It is thread-safe and implements `Iterable<V>`.

### Storing entries

Each value is stored against a unique timestamp; adding a value at an existing timestamp overwrites the previous one. A label can be attached per entry, and whole batches can be added in a single call.

=== "Sync"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add a value at a timestamp
    ts.add(201908110501L, "10%");
    ts.add(201908110502L, "30%");
    
    // Add a value together with a label
    ts.add(201908110504L, "10%", "host-1");
    
    // Add several values at once
    ts.addAll(Map.of(201908110601L, "15%",
                     201908110602L, "25%",
                     201908110603L, "35%"));
    
    // Add several labelled entries at once
    ts.addAll(List.of(new TimeSeriesEntry<>(201908110701L, "40%", "host-1"),
                      new TimeSeriesEntry<>(201908110702L, "45%", "host-2")));
    ```
=== "Async"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    RFuture<Void> f1 = ts.addAsync(201908110501L, "10%");
    RFuture<Void> f2 = ts.addAsync(201908110504L, "10%", "host-1");
    
    RFuture<Void> f3 = ts.addAllAsync(Map.of(201908110601L, "15%",
                                             201908110602L, "25%"));
    
    RFuture<Void> f4 = ts.addAllAsync(List.of(
            new TimeSeriesEntry<>(201908110701L, "40%", "host-1"),
            new TimeSeriesEntry<>(201908110702L, "45%", "host-2")));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Mono<Void> m1 = ts.add(201908110501L, "10%");
    Mono<Void> m2 = ts.add(201908110504L, "10%", "host-1");
    
    Mono<Void> m3 = ts.addAll(Map.of(201908110601L, "15%",
                                     201908110602L, "25%"));
    
    Mono<Void> m4 = ts.addAll(List.of(
            new TimeSeriesEntry<>(201908110701L, "40%", "host-1"),
            new TimeSeriesEntry<>(201908110702L, "45%", "host-2")));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Completable c1 = ts.add(201908110501L, "10%");
    Completable c2 = ts.add(201908110504L, "10%", "host-1");
    
    Completable c3 = ts.addAll(Map.of(201908110601L, "15%",
                                      201908110602L, "25%"));
    
    Completable c4 = ts.addAll(List.of(
            new TimeSeriesEntry<>(201908110701L, "40%", "host-1"),
            new TimeSeriesEntry<>(201908110702L, "45%", "host-2")));
    ```

### Per-entry TTL

Each entry can be given its own time-to-live, after which Valkey or Redis removes it automatically - useful for retention policies. A TTL can be expressed as an amount plus `TimeUnit` or as a `Duration`, and applies to single entries or whole batches. A label and a TTL are combined through the `Duration` overload.

=== "Sync"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // TTL as amount + TimeUnit
    ts.add(201908110510L, "85%", 10, TimeUnit.HOURS);
    
    // TTL as Duration
    ts.add(201908110530L, "95%", Duration.ofDays(1));
    
    // Label together with a TTL
    ts.add(201908110540L, "99%", "host-1", Duration.ofHours(6));
    
    // Same TTL for a whole batch
    ts.addAll(Map.of(201908110601L, "15%",
                     201908110602L, "25%"), 1, TimeUnit.HOURS);
    ```
=== "Async"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    RFuture<Void> f1 = ts.addAsync(201908110510L, "85%", 10, TimeUnit.HOURS);
    RFuture<Void> f2 = ts.addAsync(201908110530L, "95%", Duration.ofDays(1));
    RFuture<Void> f3 = ts.addAsync(201908110540L, "99%", "host-1", Duration.ofHours(6));
    RFuture<Void> f4 = ts.addAllAsync(Map.of(201908110601L, "15%",
                                             201908110602L, "25%"), 1, TimeUnit.HOURS);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Mono<Void> m1 = ts.add(201908110510L, "85%", 10, TimeUnit.HOURS);
    Mono<Void> m2 = ts.add(201908110530L, "95%", Duration.ofDays(1));
    Mono<Void> m3 = ts.add(201908110540L, "99%", "host-1", Duration.ofHours(6));
    Mono<Void> m4 = ts.addAll(Map.of(201908110601L, "15%",
                                     201908110602L, "25%"), 1, TimeUnit.HOURS);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Completable c1 = ts.add(201908110510L, "85%", 10, TimeUnit.HOURS);
    Completable c2 = ts.add(201908110530L, "95%", Duration.ofDays(1));
    Completable c3 = ts.add(201908110540L, "99%", "host-1", Duration.ofHours(6));
    Completable c4 = ts.addAll(Map.of(201908110601L, "15%",
                                      201908110602L, "25%"), 1, TimeUnit.HOURS);
    ```

### Reading and removing entries

`get` returns a value by timestamp, while `getEntry` returns the full `TimeSeriesEntry` (its timestamp, value, and label). `getAndRemove` reads and deletes in one step, and `size` reports the entry count.

=== "Sync"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Value at a timestamp
    String value = ts.get(201908110504L);
    
    // Full entry: timestamp, value, label
    TimeSeriesEntry<String, String> entry = ts.getEntry(201908110504L);
    String label = entry.getLabel();
    
    // Number of entries
    int size = ts.size();
    
    // Remove by timestamp
    boolean removed = ts.remove(201908110504L);
    
    // Read and remove in one step
    String taken = ts.getAndRemove(201908110501L);
    TimeSeriesEntry<String, String> takenEntry = ts.getAndRemoveEntry(201908110502L);
    ```
=== "Async"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    RFuture<String> value = ts.getAsync(201908110504L);
    RFuture<TimeSeriesEntry<String, String>> entry = ts.getEntryAsync(201908110504L);
    RFuture<Integer> size = ts.sizeAsync();
    RFuture<Boolean> removed = ts.removeAsync(201908110504L);
    RFuture<String> taken = ts.getAndRemoveAsync(201908110501L);
    RFuture<TimeSeriesEntry<String, String>> takenEntry = ts.getAndRemoveEntryAsync(201908110502L);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Mono<String> value = ts.get(201908110504L);
    Mono<TimeSeriesEntry<String, String>> entry = ts.getEntry(201908110504L);
    Mono<Integer> size = ts.size();
    Mono<Boolean> removed = ts.remove(201908110504L);
    Mono<String> taken = ts.getAndRemove(201908110501L);
    Mono<TimeSeriesEntry<String, String>> takenEntry = ts.getAndRemoveEntry(201908110502L);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Maybe<String> value = ts.get(201908110504L);
    Maybe<TimeSeriesEntry<String, String>> entry = ts.getEntry(201908110504L);
    Single<Integer> size = ts.size();
    Single<Boolean> removed = ts.remove(201908110504L);
    Maybe<String> taken = ts.getAndRemove(201908110501L);
    Maybe<TimeSeriesEntry<String, String>> takenEntry = ts.getAndRemoveEntry(201908110502L);
    ```

### Range queries

Entries can be read over an inclusive timestamp range, in forward or reverse order, as values (`range`/`rangeReversed`) or as full entries (`entryRange`/`entryRangeReversed`). Each of these methods also has an overload that caps the number of results with a trailing `limit` argument.

=== "Sync"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Values within an inclusive timestamp range
    Collection<String> values = ts.range(201908110501L, 201908110508L);
    
    // Values in reverse order
    Collection<String> reversed = ts.rangeReversed(201908110501L, 201908110508L);
    
    // Cap the number of results
    Collection<String> firstThree = ts.range(201908110501L, 201908110508L, 3);
    
    // Full entries within a range (each carries timestamp, value, label)
    Collection<TimeSeriesEntry<String, String>> entries =
            ts.entryRange(201908110501L, 201908110508L);
    Collection<TimeSeriesEntry<String, String>> entriesReversed =
            ts.entryRangeReversed(201908110501L, 201908110508L);
    ```
=== "Async"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    RFuture<Collection<String>> values = ts.rangeAsync(201908110501L, 201908110508L);
    RFuture<Collection<String>> reversed = ts.rangeReversedAsync(201908110501L, 201908110508L);
    RFuture<Collection<String>> firstThree = ts.rangeAsync(201908110501L, 201908110508L, 3);
    RFuture<Collection<TimeSeriesEntry<String, String>>> entries =
            ts.entryRangeAsync(201908110501L, 201908110508L);
    RFuture<Collection<TimeSeriesEntry<String, String>>> entriesReversed =
            ts.entryRangeReversedAsync(201908110501L, 201908110508L);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Mono<Collection<String>> values = ts.range(201908110501L, 201908110508L);
    Mono<Collection<String>> reversed = ts.rangeReversed(201908110501L, 201908110508L);
    Mono<Collection<String>> firstThree = ts.range(201908110501L, 201908110508L, 3);
    Mono<Collection<TimeSeriesEntry<String, String>>> entries =
            ts.entryRange(201908110501L, 201908110508L);
    Mono<Collection<TimeSeriesEntry<String, String>>> entriesReversed =
            ts.entryRangeReversed(201908110501L, 201908110508L);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Single<Collection<String>> values = ts.range(201908110501L, 201908110508L);
    Single<Collection<String>> reversed = ts.rangeReversed(201908110501L, 201908110508L);
    Single<Collection<String>> firstThree = ts.range(201908110501L, 201908110508L, 3);
    Single<Collection<TimeSeriesEntry<String, String>>> entries =
            ts.entryRange(201908110501L, 201908110508L);
    Single<Collection<TimeSeriesEntry<String, String>>> entriesReversed =
            ts.entryRangeReversed(201908110501L, 201908110508L);
    ```

### Head, tail, and polling

`first`/`last` return the value at the earliest/latest timestamp; `firstEntry`/`lastEntry` return the full entries, and `firstTimestamp`/`lastTimestamp` return just the timestamps. `first(count)`/`last(count)` read several head or tail values **without** removing them. The `poll*` methods, by contrast, **remove** what they return, and `removeRange` deletes every entry within a timestamp range.

=== "Sync"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Earliest / latest value
    String firstValue = ts.first();
    String lastValue = ts.last();
    
    // Earliest / latest full entry
    TimeSeriesEntry<String, String> firstEntry = ts.firstEntry();
    TimeSeriesEntry<String, String> lastEntry = ts.lastEntry();
    
    // Earliest / latest timestamp
    Long firstTs = ts.firstTimestamp();
    Long lastTs = ts.lastTimestamp();
    
    // Peek at several head / tail values (no removal)
    Collection<String> head = ts.first(3);
    Collection<String> tail = ts.last(3);
    
    // Poll = read AND remove, from the head or tail
    Collection<String> polledHead = ts.pollFirst(2);
    Collection<TimeSeriesEntry<String, String>> polledTail = ts.pollLastEntries(2);
    String oldest = ts.pollFirst();
    
    // Delete every entry within a timestamp range
    int deleted = ts.removeRange(201908110501L, 201908110508L);
    ```
=== "Async"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    RFuture<String> firstValue = ts.firstAsync();
    RFuture<String> lastValue = ts.lastAsync();
    RFuture<TimeSeriesEntry<String, String>> firstEntry = ts.firstEntryAsync();
    RFuture<TimeSeriesEntry<String, String>> lastEntry = ts.lastEntryAsync();
    RFuture<Long> firstTs = ts.firstTimestampAsync();
    RFuture<Long> lastTs = ts.lastTimestampAsync();
    
    RFuture<Collection<String>> head = ts.firstAsync(3);
    RFuture<Collection<String>> tail = ts.lastAsync(3);
    
    RFuture<Collection<String>> polledHead = ts.pollFirstAsync(2);
    RFuture<Collection<TimeSeriesEntry<String, String>>> polledTail = ts.pollLastEntriesAsync(2);
    RFuture<String> oldest = ts.pollFirstAsync();
    
    RFuture<Integer> deleted = ts.removeRangeAsync(201908110501L, 201908110508L);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Mono<String> firstValue = ts.first();
    Mono<String> lastValue = ts.last();
    Mono<TimeSeriesEntry<String, String>> firstEntry = ts.firstEntry();
    Mono<TimeSeriesEntry<String, String>> lastEntry = ts.lastEntry();
    Mono<Long> firstTs = ts.firstTimestamp();
    Mono<Long> lastTs = ts.lastTimestamp();
    
    Mono<Collection<String>> head = ts.first(3);
    Mono<Collection<String>> tail = ts.last(3);
    
    Mono<Collection<String>> polledHead = ts.pollFirst(2);
    Mono<Collection<TimeSeriesEntry<String, String>>> polledTail = ts.pollLastEntries(2);
    Mono<String> oldest = ts.pollFirst();
    
    Mono<Integer> deleted = ts.removeRange(201908110501L, 201908110508L);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Maybe<String> firstValue = ts.first();
    Maybe<String> lastValue = ts.last();
    Maybe<TimeSeriesEntry<String, String>> firstEntry = ts.firstEntry();
    Maybe<TimeSeriesEntry<String, String>> lastEntry = ts.lastEntry();
    Single<Long> firstTs = ts.firstTimestamp();
    Single<Long> lastTs = ts.lastTimestamp();
    
    Single<Collection<String>> head = ts.first(3);
    Single<Collection<String>> tail = ts.last(3);
    
    Single<Collection<String>> polledHead = ts.pollFirst(2);
    Single<Collection<TimeSeriesEntry<String, String>>> polledTail = ts.pollLastEntries(2);
    Maybe<String> oldest = ts.pollFirst();
    
    Single<Integer> deleted = ts.removeRange(201908110501L, 201908110508L);
    ```

### Iteration

`RTimeSeries` implements `Iterable<V>`, so the synchronous interface can be looped over directly or streamed with `stream()`; `iterator(int count)` controls the batch size used to fetch elements. On the Reactive and RxJava3 interfaces, `iterator()` returns a `Flux<V>` and `Flowable<V>` respectively. (There is no streaming iterator on the asynchronous interface.)

=== "Sync"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Iterable<V> - values in timestamp order
    for (String value : ts) {
        System.out.println(value);
    }
    
    // Java Stream
    ts.stream().forEach(System.out::println);
    
    // Control the fetch batch size
    Iterator<String> it = ts.iterator(10);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Flux<String> flux = ts.iterator();
    flux.subscribe(value -> System.out.println(value));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Flowable<String> flowable = ts.iterator();
    flowable.subscribe(value -> System.out.println(value));
    ```

### Listeners

Redisson allows binding listeners per `RTimeSeries` object. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

| Listener class name | Event description | Valkey or Redis `notify-keyspace-events` value |
| --- | --- | --- |
| org.redisson.api.listener.TrackingListener | Element created/removed/updated after read operation | - |
| org.redisson.api.listener.ScoredSortedSetAddListener | Entry created/updated | Ez |
| org.redisson.api.listener.ScoredSortedSetRemoveListener | Entry removed | Ez |
| org.redisson.api.ExpiredObjectListener | `RTimeSeries` object expired | Ex |
| org.redisson.api.DeletedObjectListener | `RTimeSeries` object deleted | Eg |

=== "Sync"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    int listenerId = ts.addListener(new ScoredSortedSetAddListener() {
         @Override
         public void onAdd(String name) {
            // entry added or updated
         }
    });
    
    int listenerId = ts.addListener(new ScoredSortedSetRemoveListener() {
         @Override
         public void onRemove(String name) {
            // entry removed
         }
    });
    
    int listenerId = ts.addListener(new ExpiredObjectListener() {
         @Override
         public void onExpired(String name) {
            // time series expired
         }
    });
    
    int listenerId = ts.addListener(new DeletedObjectListener() {
         @Override
         public void onDeleted(String name) {
            // time series deleted
         }
    });
    
    // remove listener
    ts.removeListener(listenerId);
    ```
=== "Async"
    ```java
    RTimeSeries<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    RFuture<Integer> listenerFuture = ts.addListenerAsync(new ScoredSortedSetAddListener() {
         @Override
         public void onAdd(String name) {
            // entry added or updated
         }
    });
    
    RFuture<Integer> listenerFuture = ts.addListenerAsync(new ScoredSortedSetRemoveListener() {
         @Override
         public void onRemove(String name) {
            // entry removed
         }
    });
    
    RFuture<Integer> listenerFuture = ts.addListenerAsync(new ExpiredObjectListener() {
         @Override
         public void onExpired(String name) {
            // time series expired
         }
    });
    
    RFuture<Integer> listenerFuture = ts.addListenerAsync(new DeletedObjectListener() {
         @Override
         public void onDeleted(String name) {
            // time series deleted
         }
    });
    
    // remove listener
    RFuture<Void> removeFuture = ts.removeListenerAsync(listenerId);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Mono<Integer> listenerMono = ts.addListener(new ScoredSortedSetAddListener() {
         @Override
         public void onAdd(String name) {
            // entry added or updated
         }
    });
    
    Mono<Integer> listenerMono = ts.addListener(new ScoredSortedSetRemoveListener() {
         @Override
         public void onRemove(String name) {
            // entry removed
         }
    });
    
    Mono<Integer> listenerMono = ts.addListener(new ExpiredObjectListener() {
         @Override
         public void onExpired(String name) {
            // time series expired
         }
    });
    
    Mono<Integer> listenerMono = ts.addListener(new DeletedObjectListener() {
         @Override
         public void onDeleted(String name) {
            // time series deleted
         }
    });
    
    // remove listener
    Mono<Void> removeMono = ts.removeListener(listenerId);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String, String> ts = redisson.getTimeSeries("myTimeSeries");
    
    Single<Integer> listenerSingle = ts.addListener(new ScoredSortedSetAddListener() {
         @Override
         public void onAdd(String name) {
            // entry added or updated
         }
    });
    
    Single<Integer> listenerSingle = ts.addListener(new ScoredSortedSetRemoveListener() {
         @Override
         public void onRemove(String name) {
            // entry removed
         }
    });
    
    Single<Integer> listenerSingle = ts.addListener(new ExpiredObjectListener() {
         @Override
         public void onExpired(String name) {
            // time series expired
         }
    });
    
    Single<Integer> listenerSingle = ts.addListener(new DeletedObjectListener() {
         @Override
         public void onDeleted(String name) {
            // time series deleted
         }
    });
    
    // remove listener
    Completable removeCompletable = ts.removeListener(listenerId);
    ```

## Vector Set 

Java implementation of Redis based [Vector Set](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RVectorSet.html) object is a specialized data type designed for managing high-dimensional vector data and enabling fast vector similarity search. [Vector sets](https://redis.io/docs/latest/develop/ai/search-and-query/vectors/) are similar to sorted sets but instead of a score, each element has a string representation of a vector, making them ideal for AI applications, machine learning models, and semantic search use cases.

Requires **Redis 8.0+**.

### High-Dimensional Vector Data

Vector sets are optimized for storing and querying high-dimensional vectors commonly used in modern applications. Each element in a vector set consists of an element name (string identifier), a vector (list of floating-point values representing the element in vector space), and optional JSON attributes (metadata associated with the element).

Vector sets support configurable dimensionality to match your embedding model output, efficient storage of dense vector representations, and automatic vector normalization for cosine similarity calculations.

Usage examples:

=== "Sync"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("embeddings");

	// Add text embeddings (e.g., from OpenAI, Sentence Transformers, etc.)
	vectorSet.add(VectorAddArgs.element("doc1")
		.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)); // 1536-dim for OpenAI

	vectorSet.add(VectorAddArgs.element("doc2")
		.vector(-0.23, 0.45, -0.67, 0.89, 0.12, ...));

	// Retrieve stored vector
	List<Double> vector = vectorSet.getVector("doc1");

	// Get vector dimensions
	long dimensions = vectorSet.dim();

	// Get total number of elements
	long count = vectorSet.size();
	```
=== "Async"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("embeddings");

	// Add text embeddings
	RFuture<Boolean> f1 = vectorSet.addAsync(VectorAddArgs.element("doc1")
		.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...));

	RFuture<Boolean> f2 = vectorSet.addAsync(VectorAddArgs.element("doc2")
		.vector(-0.23, 0.45, -0.67, 0.89, 0.12, ...));

	// Retrieve stored vector
	RFuture<List<Double>> vectorFuture = vectorSet.getVectorAsync("doc1");

	// Get vector dimensions
	RFuture<Long> dimFuture = vectorSet.dimAsync();

	// Get total number of elements
	RFuture<Integer> sizeFuture = vectorSet.sizeAsync();

	vectorFuture.whenComplete((vector, exception) -> {
		// ...
	});	
	```
=== "Reactive"
    ```java
	RedissonReactiveClient redisson = redissonClient.reactive();
	RVectorSetReactive<String> vectorSet = redisson.getVectorSet("embeddings");

	// Add text embeddings
	Mono<Boolean> m1 = vectorSet.add(VectorAddArgs.element("doc1")
		.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...));

	Mono<Boolean> m2 = vectorSet.add(VectorAddArgs.element("doc2")
		.vector(-0.23, 0.45, -0.67, 0.89, 0.12, ...));

	// Retrieve stored vector
	Mono<List<Double>> vectorMono = vectorSet.getVector("doc1");

	// Get vector dimensions
	Mono<Long> dimMono = vectorSet.dim();

	// Get total number of elements
	Mono<Integer> sizeMono = vectorSet.size();

	vectorMono.doOnNext(vector -> {
		// ...
	}).subscribe();
    ```
=== "RxJava3"
    ```java
	RedissonRxClient redisson = redissonClient.rxJava();
	RVectorSetRx<String> vectorSet = redisson.getVectorSet("embeddings");

	// Add text embeddings
	Single<Boolean> s1 = vectorSet.add(VectorAddArgs.element("doc1")
		.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...));

	Single<Boolean> s2 = vectorSet.add(VectorAddArgs.element("doc2")
		.vector(-0.23, 0.45, -0.67, 0.89, 0.12, ...));

	// Retrieve stored vector
	Maybe<List<Double>> vectorMaybe = vectorSet.getVector("doc1");

	// Get vector dimensions
	Single<Long> dimSingle = vectorSet.dim();

	// Get total number of elements
	Single<Integer> sizeSingle = vectorSet.size();

	vectorMaybe.doOnSuccess(vector -> {
		// ...
	}).subscribe();
    ```

### Similarity Search

Vector sets use the Hierarchical Navigable Small World (HNSW) algorithm for approximate nearest neighbor search. HNSW provides sub-linear query time complexity and excellent recall rates, making it suitable for large-scale similarity search applications.

The similarity search returns elements ordered by their proximity to the query vector using cosine similarity metrics.

Usage examples:

=== "Sync"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("embeddings");

	// Find similar elements by vector
	List<String> similar = vectorSet.getSimilar(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)
			.count(10));  // Return top 10 most similar

	// Find similar elements by existing element name
	List<String> similarToDoc = vectorSet.getSimilar(
		VectorSimilarArgs.element("doc1")
			.count(5));

	// Get similarity scores along with elements
	List<ScoredEntry<String>> similarWithScores = vectorSet.getSimilarWithScores(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)
			.count(10));

	for (ScoredEntry<String> entry : similarWithScores) {
		String element = entry.getValue();
		Double score = entry.getScore();  // Similarity score
	}
	```
=== "Async"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("embeddings");

	// Find similar elements by vector
	RFuture<List<String>> similarFuture = vectorSet.getSimilarAsync(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)
			.count(10));

	// Find similar elements by existing element name
	RFuture<List<String>> similarToDocFuture = vectorSet.getSimilarAsync(
		VectorSimilarArgs.element("doc1")
			.count(5));

	// Get similarity scores along with elements
	RFuture<List<ScoredEntry<String>>> scoresFeature = vectorSet.getSimilarWithScoresAsync(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)
			.count(10));

	scoresFeature.whenComplete((entries, exception) -> {
		for (ScoredEntry<String> entry : entries) {
			String element = entry.getValue();
			Double score = entry.getScore();
		}
	});
	```
=== "Reactive"
    ```java
	RedissonReactiveClient redisson = redissonClient.reactive();
	RVectorSetReactive<String> vectorSet = redisson.getVectorSet("embeddings");

	// Find similar elements by vector
	Mono<List<String>> similarMono = vectorSet.getSimilar(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)
			.count(10));

	// Find similar elements by existing element name
	Mono<List<String>> similarToDocMono = vectorSet.getSimilar(
		VectorSimilarArgs.element("doc1")
			.count(5));

	// Get similarity scores along with elements
	Mono<List<ScoredEntry<String>>> scoresMono = vectorSet.getSimilarWithScores(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)
			.count(10));

	scoresMono.doOnNext(entries -> {
		for (ScoredEntry<String> entry : entries) {
			String element = entry.getValue();
			Double score = entry.getScore();
		}
	}).subscribe();
    ```
=== "RxJava3"
    ```java
	RedissonRxClient redisson = redissonClient.rxJava();
	RVectorSetRx<String> vectorSet = redisson.getVectorSet("embeddings");

	// Find similar elements by vector
	Single<List<String>> similarSingle = vectorSet.getSimilar(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)
			.count(10));

	// Find similar elements by existing element name
	Single<List<String>> similarToDocSingle = vectorSet.getSimilar(
		VectorSimilarArgs.element("doc1")
			.count(5));

	// Get similarity scores along with elements
	Single<List<ScoredEntry<String>>> scoresSingle = vectorSet.getSimilarWithScores(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, 0.78, -0.91, ...)
			.count(10));

	scoresSingle.doOnSuccess(entries -> {
		for (ScoredEntry<String> entry : entries) {
			String element = entry.getValue();
			Double score = entry.getScore();
		}
	}).subscribe();
    ```

### Attribute Management

Vector sets support attaching JSON attributes to each element, enabling rich metadata storage and filtered similarity searches. Attributes can be used to store additional information about vectors and to filter search results based on specific criteria.

Usage examples:

=== "Sync"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("products");

	// Add element with attributes
	vectorSet.add(VectorAddArgs.element("product-123")
		.vector(0.12, -0.34, 0.56, ...)
		.attributes("{\"category\": \"electronics\", \"price\": 299.99, \"inStock\": true}"));

	// Update attributes for existing element
	vectorSet.setAttributes("product-123", 
		"{\"category\": \"electronics\", \"price\": 249.99, \"inStock\": true, \"onSale\": true}");

	// Retrieve attributes
	String attrs = vectorSet.getAttributes("product-123");

	// Filtered similarity search using attribute expressions
	List<String> filtered = vectorSet.getSimilar(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, ...)
			.count(10)
			.filter(".price < 300 and .inStock == true"));

	// Complex filter expressions
	List<String> results = vectorSet.getSimilar(
		VectorSimilarArgs.element("query-product")
			.count(20)
			.filter(".category == \"electronics\" and .price >= 100 and .price <= 500"));
	```
=== "Async"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("products");

	// Add element with attributes
	RFuture<Boolean> addFuture = vectorSet.addAsync(VectorAddArgs.element("product-123")
		.vector(0.12, -0.34, 0.56, ...)
		.attributes("{\"category\": \"electronics\", \"price\": 299.99, \"inStock\": true}"));

	// Update attributes for existing element
	RFuture<Boolean> setAttrFuture = vectorSet.setAttributesAsync("product-123", 
		"{\"category\": \"electronics\", \"price\": 249.99, \"inStock\": true, \"onSale\": true}");

	// Retrieve attributes
	RFuture<String> attrsFuture = vectorSet.getAttributesAsync("product-123");

	// Filtered similarity search using attribute expressions
	RFuture<List<String>> filteredFuture = vectorSet.getSimilarAsync(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, ...)
			.count(10)
			.filter(".price < 300 and .inStock == true"));

	// Complex filter expressions
	RFuture<List<String>> resultsFuture = vectorSet.getSimilarAsync(
		VectorSimilarArgs.element("query-product")
			.count(20)
			.filter(".category == \"electronics\" and .price >= 100 and .price <= 500"));

	filteredFuture.whenComplete((results, exception) -> {
		// ...
	});
	```
=== "Reactive"
    ```java
	RedissonReactiveClient redisson = redissonClient.reactive();
	RVectorSetReactive<String> vectorSet = redisson.getVectorSet("products");

	// Add element with attributes
	Mono<Boolean> addMono = vectorSet.add(VectorAddArgs.element("product-123")
		.vector(0.12, -0.34, 0.56, ...)
		.attributes("{\"category\": \"electronics\", \"price\": 299.99, \"inStock\": true}"));

	// Update attributes for existing element
	Mono<Boolean> setAttrMono = vectorSet.setAttributes("product-123", 
		"{\"category\": \"electronics\", \"price\": 249.99, \"inStock\": true, \"onSale\": true}");

	// Retrieve attributes
	Mono<String> attrsMono = vectorSet.getAttributes("product-123");

	// Filtered similarity search using attribute expressions
	Mono<List<String>> filteredMono = vectorSet.getSimilar(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, ...)
			.count(10)
			.filter(".price < 300 and .inStock == true"));

	// Complex filter expressions
	Mono<List<String>> resultsMono = vectorSet.getSimilar(
		VectorSimilarArgs.element("query-product")
			.count(20)
			.filter(".category == \"electronics\" and .price >= 100 and .price <= 500"));

	filteredMono.doOnNext(results -> {
		// ...
	}).subscribe();
    ```
=== "RxJava3"
    ```java
	RedissonRxClient redisson = redissonClient.rxJava();
	RVectorSetRx<String> vectorSet = redisson.getVectorSet("products");

	// Add element with attributes
	Single<Boolean> addSingle = vectorSet.add(VectorAddArgs.element("product-123")
		.vector(0.12, -0.34, 0.56, ...)
		.attributes("{\"category\": \"electronics\", \"price\": 299.99, \"inStock\": true}"));

	// Update attributes for existing element
	Single<Boolean> setAttrSingle = vectorSet.setAttributes("product-123", 
		"{\"category\": \"electronics\", \"price\": 249.99, \"inStock\": true, \"onSale\": true}");

	// Retrieve attributes
	Maybe<String> attrsMaybe = vectorSet.getAttributes("product-123");

	// Filtered similarity search using attribute expressions
	Single<List<String>> filteredSingle = vectorSet.getSimilar(
		VectorSimilarArgs.vector(0.12, -0.34, 0.56, ...)
			.count(10)
			.filter(".price < 300 and .inStock == true"));

	// Complex filter expressions
	Single<List<String>> resultsSingle = vectorSet.getSimilar(
		VectorSimilarArgs.element("query-product")
			.count(20)
			.filter(".category == \"electronics\" and .price >= 100 and .price <= 500"));

	filteredSingle.doOnSuccess(results -> {
		// ...
	}).subscribe();
    ```
	
### Use Cases

Vector sets are particularly well-suited for AI and machine learning applications that require efficient similarity search over high-dimensional embeddings.

**Semantic Search**

Store text embeddings from language models and find semantically similar documents, enabling natural language search that understands meaning rather than just keywords.

Usage examples:

=== "Sync"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("documents");

	// Store document embeddings
	vectorSet.add(VectorAddArgs.element("doc-" + docId)
		.vector(embeddingModel.encode(documentText))
		.attributes("{\"title\": \"" + title + "\", \"date\": \"" + date + "\"}"));

	// Search by query embedding
	List<String> relevantDocs = vectorSet.getSimilar(
		VectorSimilarArgs.vector(embeddingModel.encode(userQuery))
			.count(10));
	```
=== "Async"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("documents");

	// Store document embeddings
	RFuture<Boolean> addFuture = vectorSet.addAsync(VectorAddArgs.element("doc-" + docId)
		.vector(embeddingModel.encode(documentText))
		.attributes("{\"title\": \"" + title + "\", \"date\": \"" + date + "\"}"));

	// Search by query embedding
	RFuture<List<String>> searchFuture = vectorSet.getSimilarAsync(
		VectorSimilarArgs.vector(embeddingModel.encode(userQuery))
			.count(10));

	searchFuture.whenComplete((relevantDocs, exception) -> {
		// Process relevant documents
	});
	```
=== "Reactive"
    ```java
	RedissonReactiveClient redisson = redissonClient.reactive();
	RVectorSetReactive<String> vectorSet = redisson.getVectorSet("documents");

	// Store document embeddings
	Mono<Boolean> addMono = vectorSet.add(VectorAddArgs.element("doc-" + docId)
		.vector(embeddingModel.encode(documentText))
		.attributes("{\"title\": \"" + title + "\", \"date\": \"" + date + "\"}"));

	// Search by query embedding
	Mono<List<String>> searchMono = vectorSet.getSimilar(
		VectorSimilarArgs.vector(embeddingModel.encode(userQuery))
			.count(10));

	searchMono.doOnNext(relevantDocs -> {
		// Process relevant documents
	}).subscribe();
    ```
=== "RxJava3"
    ```java
	RedissonRxClient redisson = redissonClient.rxJava();
	RVectorSetRx<String> vectorSet = redisson.getVectorSet("documents");

	// Store document embeddings
	Single<Boolean> addSingle = vectorSet.add(VectorAddArgs.element("doc-" + docId)
		.vector(embeddingModel.encode(documentText))
		.attributes("{\"title\": \"" + title + "\", \"date\": \"" + date + "\"}"));

	// Search by query embedding
	Single<List<String>> searchSingle = vectorSet.getSimilar(
		VectorSimilarArgs.vector(embeddingModel.encode(userQuery))
			.count(10));

	searchSingle.doOnSuccess(relevantDocs -> {
		// Process relevant documents
	}).subscribe();
    ```
	
**Recommendation Systems**

Store user and item embeddings to provide personalized recommendations based on vector similarity.

Usage examples:

=== "Sync"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("products");

	// Find similar products for recommendations
	List<String> recommendations = vectorSet.getSimilar(
		VectorSimilarArgs.element("user-" + userId + "-preferences")
			.count(20)
			.filter(".category == \"" + preferredCategory + "\""));
	```
=== "Async"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("products");

	// Find similar products for recommendations
	RFuture<List<String>> recFuture = vectorSet.getSimilarAsync(
		VectorSimilarArgs.element("user-" + userId + "-preferences")
			.count(20)
			.filter(".category == \"" + preferredCategory + "\""));

	recFuture.whenComplete((recommendations, exception) -> {
		// Process recommendations
	});
	```
=== "Reactive"
    ```java
	RedissonReactiveClient redisson = redissonClient.reactive();
	RVectorSetReactive<String> vectorSet = redisson.getVectorSet("products");

	// Find similar products for recommendations
	Mono<List<String>> recMono = vectorSet.getSimilar(
		VectorSimilarArgs.element("user-" + userId + "-preferences")
			.count(20)
			.filter(".category == \"" + preferredCategory + "\""));

	recMono.doOnNext(recommendations -> {
		// Process recommendations
	}).subscribe();
    ```
=== "RxJava3"
    ```java
	RedissonRxClient redisson = redissonClient.rxJava();
	RVectorSetRx<String> vectorSet = redisson.getVectorSet("products");

	// Find similar products for recommendations
	Single<List<String>> recSingle = vectorSet.getSimilar(
		VectorSimilarArgs.element("user-" + userId + "-preferences")
			.count(20)
			.filter(".category == \"" + preferredCategory + "\""));

	recSingle.doOnSuccess(recommendations -> {
		// Process recommendations
	}).subscribe();
    ```
	
**Image Similarity**

Store image feature vectors extracted from computer vision models to find visually similar images.

Usage examples:

=== "Sync"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("images");

	// Find similar images
	List<ScoredEntry<String>> similarImages = vectorSet.getSimilarWithScores(
		VectorSimilarArgs.vector(imageEncoder.encode(queryImage))
			.count(50));

	for (ScoredEntry<String> entry : similarImages) {
		String imageId = entry.getValue();
		Double similarity = entry.getScore();
	}
	```
=== "Async"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("images");

	// Find similar images
	RFuture<List<ScoredEntry<String>>> imageFuture = vectorSet.getSimilarWithScoresAsync(
		VectorSimilarArgs.vector(imageEncoder.encode(queryImage))
			.count(50));

	imageFuture.whenComplete((similarImages, exception) -> {
		for (ScoredEntry<String> entry : similarImages) {
			String imageId = entry.getValue();
			Double similarity = entry.getScore();
		}
	});
	```
=== "Reactive"
    ```java
	RedissonReactiveClient redisson = redissonClient.reactive();
	RVectorSetReactive<String> vectorSet = redisson.getVectorSet("images");

	// Find similar images
	Mono<List<ScoredEntry<String>>> imageMono = vectorSet.getSimilarWithScores(
		VectorSimilarArgs.vector(imageEncoder.encode(queryImage))
			.count(50));

	imageMono.doOnNext(similarImages -> {
		for (ScoredEntry<String> entry : similarImages) {
			String imageId = entry.getValue();
			Double similarity = entry.getScore();
		}
	}).subscribe();
    ```
=== "RxJava3"
    ```java
	RedissonRxClient redisson = redissonClient.rxJava();
	RVectorSetRx<String> vectorSet = redisson.getVectorSet("images");

	// Find similar images
	Single<List<ScoredEntry<String>>> imageSingle = vectorSet.getSimilarWithScores(
		VectorSimilarArgs.vector(imageEncoder.encode(queryImage))
			.count(50));

	imageSingle.doOnSuccess(similarImages -> {
		for (ScoredEntry<String> entry : similarImages) {
			String imageId = entry.getValue();
			Double similarity = entry.getScore();
		}
	}).subscribe();
    ```
	
**RAG (Retrieval-Augmented Generation)**

Use vector sets as a knowledge base for LLM applications, retrieving relevant context based on semantic similarity.

Usage examples:

=== "Sync"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("knowledge-base");

	// Retrieve relevant context for RAG
	List<String> contextChunks = vectorSet.getSimilar(
		VectorSimilarArgs.vector(embeddingModel.encode(userQuestion))
			.count(5));

	String context = contextChunks.stream()
		.map(chunkId -> getChunkContent(chunkId))
		.collect(Collectors.joining("\n"));

	// Pass context to LLM for answer generation
	String answer = llm.generate(userQuestion, context);
	```
=== "Async"
    ```java
	RVectorSet<String> vectorSet = redisson.getVectorSet("knowledge-base");

	// Retrieve relevant context for RAG
	RFuture<List<String>> contextFuture = vectorSet.getSimilarAsync(
		VectorSimilarArgs.vector(embeddingModel.encode(userQuestion))
			.count(5));

	contextFuture.thenApply(contextChunks -> {
		String context = contextChunks.stream()
			.map(chunkId -> getChunkContent(chunkId))
			.collect(Collectors.joining("\n"));
		
		// Pass context to LLM for answer generation
		return llm.generate(userQuestion, context);
	});
	```
=== "Reactive"
    ```java
	RedissonReactiveClient redisson = redissonClient.reactive();
	RVectorSetReactive<String> vectorSet = redisson.getVectorSet("knowledge-base");

	// Retrieve relevant context for RAG
	Mono<List<String>> contextMono = vectorSet.getSimilar(
		VectorSimilarArgs.vector(embeddingModel.encode(userQuestion))
			.count(5));

	contextMono.map(contextChunks -> {
		String context = contextChunks.stream()
			.map(chunkId -> getChunkContent(chunkId))
			.collect(Collectors.joining("\n"));
		
		// Pass context to LLM for answer generation
		return llm.generate(userQuestion, context);
	}).subscribe();
    ```
=== "RxJava3"
    ```java
	RedissonRxClient redisson = redissonClient.rxJava();
	RVectorSetRx<String> vectorSet = redisson.getVectorSet("knowledge-base");

	// Retrieve relevant context for RAG
	Single<List<String>> contextSingle = vectorSet.getSimilar(
		VectorSimilarArgs.vector(embeddingModel.encode(userQuestion))
			.count(5));

	contextSingle.map(contextChunks -> {
		String context = contextChunks.stream()
			.map(chunkId -> getChunkContent(chunkId))
			.collect(Collectors.joining("\n"));
		
		// Pass context to LLM for answer generation
		return llm.generate(userQuestion, context);
	}).subscribe();
    ```

{% include 'data-and-services/bitvectorstore.md' %}