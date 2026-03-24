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
|org.redisson.api.listener.MapPutListener|Entry created/updated|Eh|
|org.redisson.api.listener.MapRemoveListener|Entry removed|Eh|
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
     public void onPut(String name) {
        // ...
     }
});

int listenerId = map.addListener(new MapRemoveListener() {
     @Override
     public void onRemove(String name) {
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
Java implementation of Valkey or Redis based [Multimap](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMultimap.html) object for  allows to store multiple values per key. Keys amount limited to `4 294 967 295` elements. Valkey and Redis use serialized key state to its uniqueness instead of key's `hashCode()`/`equals()` methods. This object is thread-safe.

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMultimapAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMultimapReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMultimapRx.html) interfaces.

### Set based Multimap
Set based Multimap doesn't allow duplications for values per key.
```java
RSetMultimap<SimpleKey, SimpleValue> map = redisson.getSetMultimap("myMultimap");
map.put(new SimpleKey("0"), new SimpleValue("1"));
map.put(new SimpleKey("0"), new SimpleValue("2"));
map.put(new SimpleKey("3"), new SimpleValue("4"));

Set<SimpleValue> allValues = map.get(new SimpleKey("0"));

List<SimpleValue> newValues = Arrays.asList(new SimpleValue("7"), new SimpleValue("6"), new SimpleValue("5"));
Set<SimpleValue> oldValues = map.replaceValues(new SimpleKey("0"), newValues);

Set<SimpleValue> removedValues = map.removeAll(new SimpleKey("0"));
```
### List based Multimap
List based [Multimap](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMultimap.html) object for Java stores entries in insertion order and allows duplicates for values mapped to key.
```java
RListMultimap<SimpleKey, SimpleValue> map = redisson.getListMultimap("test1");
map.put(new SimpleKey("0"), new SimpleValue("1"));
map.put(new SimpleKey("0"), new SimpleValue("2"));
map.put(new SimpleKey("0"), new SimpleValue("1"));
map.put(new SimpleKey("3"), new SimpleValue("4"));

List<SimpleValue> allValues = map.get(new SimpleKey("0"));

Collection<SimpleValue> newValues = Arrays.asList(new SimpleValue("7"), new SimpleValue("6"), new SimpleValue("5"));
List<SimpleValue> oldValues = map.replaceValues(new SimpleKey("0"), newValues);

List<SimpleValue> removedValues = map.removeAll(new SimpleKey("0"));
```

### Eviction
Multimap entries eviction implemented by a separate MultimapCache object. There are [RSetMultimapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetMultimapCache.html) and [RListMultimapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RListMultimapCache.html) objects for Set and List based Multimaps respectively.  

Eviction task is started once per unique object name at the moment of getting Multimap instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Valkey or Redis calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

Redis 7.4.0 and higher version implements native eviction. It's supported by [RSetMultimapCacheNative](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RSetMultimapCacheNative.html) and [RListMultimapCacheNative](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RListMultimapCacheNative.html) objects.

Code examples:

=== "Sync"
	```java
	// scripted eviction implementation
	RSetMultimapCache<String, String> multimap = redisson.getSetMultimapCache("myMultimap");

	// native eviction implementation
	RSetMultimapCacheNative<String, String> multimap = redisson.getSetMultimapCacheNative("myMultimap");

	multimap.put("1", "a");
	multimap.put("1", "b");
	multimap.put("1", "c");

	multimap.put("2", "e");
	multimap.put("2", "f");

	multimap.expireKey("2", 10, TimeUnit.MINUTES);

	// if object is not used anymore
	multimap.destroy();
	```
=== "Async"
	```java
	// scripted eviction implementation
	RSetMultimapCacheAsync<String, String> multimap = redisson.getSetMultimapCache("myMultimap");

	// native eviction implementation
	RSetMultimapCacheNativeAsync<String, String> multimap = redisson.getSetMultimapCacheNative("myMultimap");

	RFuture<Boolean> f1 = multimap.putAsync("1", "a");
	RFuture<Boolean> f2 = multimap.putAsync("1", "b");
	RFuture<Boolean> f3 = multimap.putAsync("1", "c");

	RFuture<Boolean> f4 = multimap.putAsync("2", "e");
	RFuture<Boolean> f5 = multimap.putAsync("2", "f");

	RFuture<Boolean> exfeature = multimap.expireKeyAsync("2", 10, TimeUnit.MINUTES);

	// if object is not used anymore
	multimap.destroy();
	```
=== "Reactive"
    ```java
	RedissonReactiveClient redissonReactive = redisson.reactive();
	
	// scripted eviction implementation
	RSetMultimapCacheReactive<String, String> multimap = redissonReactive.getSetMultimapCache("myMultimap");

	// native eviction implementation
	RSetMultimapCacheNativeReactive<String, String> multimap = redissonReactive.getSetMultimapCacheNative("myMultimap");
	
	Mono<Boolean> f1 = multimap.put("1", "a");
	Mono<Boolean> f2 = multimap.put("1", "b");
	Mono<Boolean> f3 = multimap.put("1", "c");

	Mono<Boolean> f4 = multimap.put("2", "e");
	Mono<Boolean> f5 = multimap.put("2", "f");

	Mono<Boolean> exfeature = multimap.expireKey("2", 10, TimeUnit.MINUTES);

	// if object is not used anymore
	multimap.destroy();
    ```
=== "RxJava3"
    ```java
	RedissonRxClient redissonRx = redisson.rxJava();
	
	// scripted eviction implementation
	RSetMultimapCacheRx<String, String> multimap = redissonReactive.getSetMultimapCache("myMultimap");

	// native eviction implementation
	RSetMultimapCacheNativeRx<String, String> multimap = redissonReactive.getSetMultimapCacheNative("myMultimap");
	
	Single<Boolean> f1 = multimap.put("1", "a");
	Single<Boolean> f2 = multimap.put("1", "b");
	Single<Boolean> f3 = multimap.put("1", "c");

	Single<Boolean> f4 = multimap.put("2", "e");
	Single<Boolean> f5 = multimap.put("2", "f");

	Single<Boolean> exfeature = multimap.expireKey("2", 10, TimeUnit.MINUTES);

	// if object is not used anymore
	multimap.destroy();
    ```


### Listeners

Redisson allows binding listeners per `RSetMultimap` or `RListMultimap` object. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

`RSetMultimap` listeners:

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.ExpiredObjectListener|`RSetMultimap` object expired| Ex|
|org.redisson.api.DeletedObjectListener|`RSetMultimap` object deleted| Eg|
|org.redisson.api.listener.SetAddListener|Element added to entry| Es|
|org.redisson.api.listener.SetRemoveListener|Element removed from entry| Es|
|org.redisson.api.listener.MapPutListener|Entry created|Eh|
|org.redisson.api.listener.MapRemoveListener|Entry removed|Eh|

`RListMultimap` listeners:

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.ExpiredObjectListener|`RListMultimap` object expired| Ex|
|org.redisson.api.DeletedObjectListener|`RListMultimap` object deleted| Eg|
|org.redisson.api.listener.ListAddListener|Element added to entry| Es|
|org.redisson.api.listener.ListRemoveListener|Element removed from entry| Es|
|org.redisson.api.listener.MapPutListener|Entry created|Eh|
|org.redisson.api.listener.MapRemoveListener|Entry removed|Eh|

Usage example:

```java
RListMultimap<Integer, Integer> lmap = redisson.getListMultimap("mymap");

int listenerId = lmap.addListener(new MapPutListener() {
     @Override
     public void onPut(String name) {
        // ...
     }
});

// ...

lmap.removeListener(listenerId);
```


## JSON Store

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

[RJsonStore](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RJsonStore.html) is a distributed Key Value store for JSON objects. Compatible with Valkey and Redis. This object is thread-safe. Allows to store JSON value mapped by key. Operations can be executed per key or group of keys. Value is stored/retrieved using `JSON.*` commands. Both key and value are POJO objects. 

Allows to define `time to live` parameter per entry. Doesn't use an entry eviction task, entries are cleaned on Valkey or Redis side.

Code example of **[Async](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RJsonStoreAsync.html) interface** usage:

```java
RJsonStoreAsync<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec(MyObject.class));
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RJsonStoreReactive.html) interface** usage:

```java
RedissonReactiveClient redisson = redissonClient.reactive();
RJsonStoreReactive<AnyObject> bucket = redisson.getJsonStore("anyObject", new JacksonCodec<>(AnyObject.class));
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RJsonStoreRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RJsonStoreRx<AnyObject> bucket = redisson.getJsonStore("anyObject", new JacksonCodec<>(AnyObject.class));
```

Data write code example:
```java
RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec(MyObject.class));

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
RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec(MyObject.class));

// multiple entries at once
Map<String, MyObject> entries = store.get(Set.of("1", "2"));

// or read entry per call
MyObject value1 = store.get("1");
MyObject value2 = store.get("2");
```

Data deletion code example:
```java
RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec(MyObject.class));

// multiple entries at once
long deleted = store.delete(Set.of("1", "2"));

// or delete entry per call
boolean status = store.delete("1");
boolean status = store.delete("2");
```

Keys access code examples:
```java
RJsonStore<String, MyObject> store = redisson.getJsonStore("test", new JacksonCodec(MyObject.class));

// iterate keys
Set<String> keys = store.keySet();

// read all keys at once
Set<String> keys = store.readAllKeySet();
```

### Search by Object properties

For data searching, index prefix should be defined in `<object_name>:` format. For example for object name "test" prefix is "test:".

`StringCodec` should be used as object codec to enable searching by field.

Data search code example:
```java
RSearch s = redisson.getSearch();
s.createIndex("idx", IndexOptions.defaults()
                        .on(IndexType.JSON)
                        .prefix(Arrays.asList("test:")),
                    FieldIndex.text("name"));

RJsonStore<String, MyObject> store = redisson.getJsonStore("test", StringCodec.INSTANCE, new JacksonCodec(MyObject.class));

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

## Set
Valkey or Redis based [Set](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSet.html) object for Java implements [Set](https://docs.oracle.com/javase/8/docs/api/java/util/Set.html) interface. This object is thread-safe. Keeps elements uniqueness via element state comparison. Set size limited to `4 294 967 295` elements. Valkey or Redis uses serialized state to check value uniqueness instead of value's `hashCode()`/`equals()` methods.

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetRx.html) interfaces.

```java
RSet<SomeObject> set = redisson.getSet("anySet");
set.add(new SomeObject());
set.remove(new SomeObject());
```
RSet object allows to bind a [Lock](locks-and-synchronizers.md/#lock)/[ReadWriteLock](locks-and-synchronizers.md/#readwritelock)/[Semaphore](locks-and-synchronizers.md/#semaphore)/[CountDownLatch](locks-and-synchronizers.md/#countdownlatch) object per value:
```
RSet<MyObject> set = redisson.getSet("anySet");
MyObject value = new MyObject();
RLock lock = map.getLock(value);
lock.lock();
try {
   // process value ...
} finally {
   lock.unlock();
}
```

### Eviction and data partitioning

Redisson provides various Set structure implementations with a few important features:  

**data partitioning** - although any Set object is cluster compatible its content isn't scaled/partitioned across multiple master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Set instance in cluster.  

**entry eviction** - allows to define `time to live` parameter per SetCache entry. Valkey or Redis set structure doesn't support eviction thus it's done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting SetCache instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Valkey or Redis calls and eviction task per unique SetCache object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

**advanced entry eviction** - improved version of the **entry eviction** process. Doesn't use an entry eviction task.

**Eviction**

Set object with eviction support implements [RSetCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetCache.html),  [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetCacheAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetCacheReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetCacheRx.html) interfaces.

Code example:
```java
RSetCache<SomeObject> set = redisson.getSetCache("mySet");
// or
RMapCache<SomeObject> set = redisson.getClusteredSetCache("mySet");

// ttl = 10 minutes, 
set.add(new SomeObject(), 10, TimeUnit.MINUTES);

// if object is not used anymore
map.destroy();
```

**Data partitioning**
Map object with data partitioning support implements `org.redisson.api.RClusteredSet`. Read more details about data partitioning [here](data-partitioning.md).

Code example:

```java
RClusteredSet<SomeObject> set = redisson.getClusteredSet("mySet");
// or
RClusteredSet<SomeObject> set = redisson.getClusteredSetCache("mySet");

// ttl = 10 minutes, 
map.add(new SomeObject(), 10, TimeUnit.MINUTES);
```

Below is the list of all available Set implementations:  

|RedissonClient <br/> method name | Data<br/>partitioning | Entry<br/>eviction | Advanced<br/>entry eviction | Ultra-fast<br/>read/write |
| ------------- | :----------:| :----------:| :----------:| :----------:|
|getSet()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ | ❌ |
|getSetCache()<br/><sub><i>open-source version</i></sub> | ❌ | ✔️ | ❌ | ❌ |
|getSet()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ❌ | ✔️ |
|getSetCache()<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ✔️ | ❌ | ✔️ |
|getSetCacheV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ✔️ | ✔️ |
|getClusteredSet()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ❌ | ✔️ |
|getClusteredSetCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ❌ | ✔️ |

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
Valkey or Redis based distributed [SortedSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSortedSet.html) for Java implements [SortedSet](https://docs.oracle.com/javase/8/docs/api/java/util/SortedSet.html) interface. This object is thread-safe. It uses comparator to sort elements and keep uniqueness. For String data type it's recommended to use [LexSortedSet](#lexsortedset) object due to performance gain.
```java
RSortedSet<Integer> set = redisson.getSortedSet("anySet");
set.trySetComparator(new MyComparator()); // set object comparator
set.add(3);
set.add(1);
set.add(2);

set.removeAsync(0);
set.addAsync(5);
```
## ScoredSortedSet
Valkey or Redis based distributed [ScoredSortedSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSet.html) object. Sorts elements by score defined during element insertion. Keeps elements uniqueness via element state comparison. 

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSetAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSetReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSetRx.html) interfaces. Set size is limited to `4 294 967 295` elements.
```java
RScoredSortedSet<SomeObject> set = redisson.getScoredSortedSet("simple");

set.add(0.13, new SomeObject(a, b));
set.addAsync(0.251, new SomeObject(c, d));
set.add(0.302, new SomeObject(g, d));

set.pollFirst();
set.pollLast();

int index = set.rank(new SomeObject(g, d)); // get element index
Double score = set.getScore(new SomeObject(g, d)); // get element score
```

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
RClusteredScoredSortedSet set = redisson.getClusteredScoredSortedSet("simpleBitset");
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
    
    // Remove first occurrence of element
    list.fastRemove(0);
    
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
    
    // Remove first occurrence at index
    RFuture<Void> future5 = list.fastRemoveAsync(0);
    
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
    
    // Remove first occurrence at index
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
    
    // Remove first occurrence at index
    Completable completable = list.fastRemove(0);
    
    // Clear all elements
    Single<Boolean> single5 = list.delete();
    ```

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

Java implementation of Valkey or Redis based [RTimeSeries](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTimeSeries.html) object is a specialized data structure for storing and querying time-stamped data. It allows storing values indexed by timestamp with optional TTL (time-to-live) per entry. Values are automatically ordered by timestamp, making it ideal for metrics, sensor data, financial data, and event logging. 

This object is thread-safe.

### Timestamp-based storage

Time Series stores entries as value-timestamp pairs where each value is associated with a unique timestamp. Timestamps serve as keys for data retrieval and ordering. If an entry with the same timestamp already exists, it will be overwritten with the new value.

=== "Sync"
    ```java
    RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add entries with timestamps
    ts.add(201908110501, "10%");
    ts.add(201908110502, "30%");
    ts.add(201908110504, "10%");
    ts.add(201908110508, "75%");
    
    // Add multiple entries at once
    ts.addAll(Map.of(201908110601L, "15%", 
                     201908110602L, "25%",
                     201908110603L, "35%"));
    
    // Retrieve value by timestamp
    String value = ts.get(201908110508);
    
    // Retrieve entry (value with timestamp) by timestamp
    TimeSeriesEntry<String> entry = ts.getEntry(201908110508);
    
    // Remove entry by timestamp
    ts.remove(201908110508);
    ```
=== "Async"
    ```java
    RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add entries with timestamps
    RFuture<Void> f1 = ts.addAsync(201908110501, "10%");
    RFuture<Void> f2 = ts.addAsync(201908110502, "30%");
    RFuture<Void> f3 = ts.addAsync(201908110504, "10%");
    RFuture<Void> f4 = ts.addAsync(201908110508, "75%");
    
    // Add multiple entries at once
    RFuture<Void> f5 = ts.addAllAsync(Map.of(201908110601L, "15%", 
                                              201908110602L, "25%",
                                              201908110603L, "35%"));
    
    // Retrieve value by timestamp
    RFuture<String> value = ts.getAsync(201908110508);
    
    // Retrieve entry (value with timestamp) by timestamp
    RFuture<TimeSeriesEntry<String>> entry = ts.getEntryAsync(201908110508);
    
    // Remove entry by timestamp
    RFuture<Boolean> removed = ts.removeAsync(201908110508);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add entries with timestamps
    Mono<Void> m1 = ts.add(201908110501, "10%");
    Mono<Void> m2 = ts.add(201908110502, "30%");
    Mono<Void> m3 = ts.add(201908110504, "10%");
    Mono<Void> m4 = ts.add(201908110508, "75%");
    
    // Add multiple entries at once
    Mono<Void> m5 = ts.addAll(Map.of(201908110601L, "15%", 
                                      201908110602L, "25%",
                                      201908110603L, "35%"));
    
    // Retrieve value by timestamp
    Mono<String> value = ts.get(201908110508);
    
    // Retrieve entry (value with timestamp) by timestamp
    Mono<TimeSeriesEntry<String>> entry = ts.getEntry(201908110508);
    
    // Remove entry by timestamp
    Mono<Boolean> removed = ts.remove(201908110508);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add entries with timestamps
    Completable c1 = ts.add(201908110501, "10%");
    Completable c2 = ts.add(201908110502, "30%");
    Completable c3 = ts.add(201908110504, "10%");
    Completable c4 = ts.add(201908110508, "75%");
    
    // Add multiple entries at once
    Completable c5 = ts.addAll(Map.of(201908110601L, "15%", 
                                       201908110602L, "25%",
                                       201908110603L, "35%"));
    
    // Retrieve value by timestamp
    Maybe<String> value = ts.get(201908110508);
    
    // Retrieve entry (value with timestamp) by timestamp
    Maybe<TimeSeriesEntry<String>> entry = ts.getEntry(201908110508);
    
    // Remove entry by timestamp
    Single<Boolean> removed = ts.remove(201908110508);
    ```

### TTL per entry

Time Series supports defining time-to-live (TTL) for each entry individually. Expired entries are automatically removed by Valkey or Redis. This is useful for implementing data retention policies where older data should be automatically cleaned up.

=== "Sync"
    ```java
    RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add entry with TTL of 10 hours
    ts.add(201908110510, "85%", 10, TimeUnit.HOURS);
    
    // Add entry with TTL of 30 minutes
    ts.add(201908110520, "90%", 30, TimeUnit.MINUTES);
    
    // Add entry with TTL as Duration
    ts.add(201908110530, "95%", Duration.ofDays(1));
    
    // Add multiple entries with the same TTL
    ts.addAll(Map.of(201908110601L, "15%", 
                     201908110602L, "25%"), 1, TimeUnit.HOURS);
    ```
=== "Async"
    ```java
    RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add entry with TTL of 10 hours
    RFuture<Void> f1 = ts.addAsync(201908110510, "85%", 10, TimeUnit.HOURS);
    
    // Add entry with TTL of 30 minutes
    RFuture<Void> f2 = ts.addAsync(201908110520, "90%", 30, TimeUnit.MINUTES);
    
    // Add entry with TTL as Duration
    RFuture<Void> f3 = ts.addAsync(201908110530, "95%", Duration.ofDays(1));
    
    // Add multiple entries with the same TTL
    RFuture<Void> f4 = ts.addAllAsync(Map.of(201908110601L, "15%", 
                                              201908110602L, "25%"), 1, TimeUnit.HOURS);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add entry with TTL of 10 hours
    Mono<Void> m1 = ts.add(201908110510, "85%", 10, TimeUnit.HOURS);
    
    // Add entry with TTL of 30 minutes
    Mono<Void> m2 = ts.add(201908110520, "90%", 30, TimeUnit.MINUTES);
    
    // Add entry with TTL as Duration
    Mono<Void> m3 = ts.add(201908110530, "95%", Duration.ofDays(1));
    
    // Add multiple entries with the same TTL
    Mono<Void> m4 = ts.addAll(Map.of(201908110601L, "15%", 
                                      201908110602L, "25%"), 1, TimeUnit.HOURS);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Add entry with TTL of 10 hours
    Completable c1 = ts.add(201908110510, "85%", 10, TimeUnit.HOURS);
    
    // Add entry with TTL of 30 minutes
    Completable c2 = ts.add(201908110520, "90%", 30, TimeUnit.MINUTES);
    
    // Add entry with TTL as Duration
    Completable c3 = ts.add(201908110530, "95%", Duration.ofDays(1));
    
    // Add multiple entries with the same TTL
    Completable c4 = ts.addAll(Map.of(201908110601L, "15%", 
                                       201908110602L, "25%"), 1, TimeUnit.HOURS);
    ```

### Range queries

Time Series provides powerful range query capabilities for retrieving entries within a specified timestamp range. You can also poll entries (retrieve and remove) from the beginning or end of the series, making it suitable for queue-like processing of time-ordered data.

=== "Sync"
    ```java
    RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Get values within timestamp range (inclusive)
    Collection<String> values = ts.range(201908110501, 201908110508);
    
    // Get entries (values with timestamps) within range
    Collection<TimeSeriesEntry<String>> entries = ts.entryRange(201908110501, 201908110508);
    
    // Get entries in reverse order within range
    Collection<TimeSeriesEntry<String>> reversed = ts.entryRangeReversed(201908110501, 201908110508);
    
    // Poll (get and remove) first N entries
    Collection<String> firstValues = ts.pollFirst(2);
    Collection<TimeSeriesEntry<String>> firstEntries = ts.pollFirstEntries(2);
    
    // Poll (get and remove) last N entries
    Collection<String> lastValues = ts.pollLast(2);
    Collection<TimeSeriesEntry<String>> lastEntries = ts.pollLastEntries(2);
    
    // Poll entries within timestamp range with a limit
    Collection<TimeSeriesEntry<String>> polled = ts.pollEntries(201908110501, 201908110508, 10);
    
    // Get first and last entries
    TimeSeriesEntry<String> first = ts.first();
    TimeSeriesEntry<String> last = ts.last();
    
    // Get first and last timestamps
    Long firstTimestamp = ts.firstTimestamp();
    Long lastTimestamp = ts.lastTimestamp();
    ```
=== "Async"
    ```java
    RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Get values within timestamp range (inclusive)
    RFuture<Collection<String>> values = ts.rangeAsync(201908110501, 201908110508);
    
    // Get entries (values with timestamps) within range
    RFuture<Collection<TimeSeriesEntry<String>>> entries = ts.entryRangeAsync(201908110501, 201908110508);
    
    // Get entries in reverse order within range
    RFuture<Collection<TimeSeriesEntry<String>>> reversed = ts.entryRangeReversedAsync(201908110501, 201908110508);
    
    // Poll (get and remove) first N entries
    RFuture<Collection<String>> firstValues = ts.pollFirstAsync(2);
    RFuture<Collection<TimeSeriesEntry<String>>> firstEntries = ts.pollFirstEntriesAsync(2);
    
    // Poll (get and remove) last N entries
    RFuture<Collection<String>> lastValues = ts.pollLastAsync(2);
    RFuture<Collection<TimeSeriesEntry<String>>> lastEntries = ts.pollLastEntriesAsync(2);
    
    // Poll entries within timestamp range with a limit
    RFuture<Collection<TimeSeriesEntry<String>>> polled = ts.pollEntriesAsync(201908110501, 201908110508, 10);
    
    // Get first and last entries
    RFuture<TimeSeriesEntry<String>> first = ts.firstAsync();
    RFuture<TimeSeriesEntry<String>> last = ts.lastAsync();
    
    // Get first and last timestamps
    RFuture<Long> firstTimestamp = ts.firstTimestampAsync();
    RFuture<Long> lastTimestamp = ts.lastTimestampAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RTimeSeriesReactive<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Get values within timestamp range (inclusive)
    Mono<Collection<String>> values = ts.range(201908110501, 201908110508);
    
    // Get entries (values with timestamps) within range
    Mono<Collection<TimeSeriesEntry<String>>> entries = ts.entryRange(201908110501, 201908110508);
    
    // Get entries in reverse order within range
    Mono<Collection<TimeSeriesEntry<String>>> reversed = ts.entryRangeReversed(201908110501, 201908110508);
    
    // Poll (get and remove) first N entries
    Mono<Collection<String>> firstValues = ts.pollFirst(2);
    Mono<Collection<TimeSeriesEntry<String>>> firstEntries = ts.pollFirstEntries(2);
    
    // Poll (get and remove) last N entries
    Mono<Collection<String>> lastValues = ts.pollLast(2);
    Mono<Collection<TimeSeriesEntry<String>>> lastEntries = ts.pollLastEntries(2);
    
    // Poll entries within timestamp range with a limit
    Mono<Collection<TimeSeriesEntry<String>>> polled = ts.pollEntries(201908110501, 201908110508, 10);
    
    // Get first and last entries
    Mono<TimeSeriesEntry<String>> first = ts.first();
    Mono<TimeSeriesEntry<String>> last = ts.last();
    
    // Get first and last timestamps
    Mono<Long> firstTimestamp = ts.firstTimestamp();
    Mono<Long> lastTimestamp = ts.lastTimestamp();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RTimeSeriesRx<String> ts = redisson.getTimeSeries("myTimeSeries");
    
    // Get values within timestamp range (inclusive)
    Single<Collection<String>> values = ts.range(201908110501, 201908110508);
    
    // Get entries (values with timestamps) within range
    Single<Collection<TimeSeriesEntry<String>>> entries = ts.entryRange(201908110501, 201908110508);
    
    // Get entries in reverse order within range
    Single<Collection<TimeSeriesEntry<String>>> reversed = ts.entryRangeReversed(201908110501, 201908110508);
    
    // Poll (get and remove) first N entries
    Single<Collection<String>> firstValues = ts.pollFirst(2);
    Single<Collection<TimeSeriesEntry<String>>> firstEntries = ts.pollFirstEntries(2);
    
    // Poll (get and remove) last N entries
    Single<Collection<String>> lastValues = ts.pollLast(2);
    Single<Collection<TimeSeriesEntry<String>>> lastEntries = ts.pollLastEntries(2);
    
    // Poll entries within timestamp range with a limit
    Single<Collection<TimeSeriesEntry<String>>> polled = ts.pollEntries(201908110501, 201908110508, 10);
    
    // Get first and last entries
    Maybe<TimeSeriesEntry<String>> first = ts.first();
    Maybe<TimeSeriesEntry<String>> last = ts.last();
    
    // Get first and last timestamps
    Maybe<Long> firstTimestamp = ts.firstTimestamp();
    Maybe<Long> lastTimestamp = ts.lastTimestamp();
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
    RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");
    
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
    RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");
    
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
    RTimeSeriesReactive<String> ts = redisson.getTimeSeries("myTimeSeries");
    
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
    RTimeSeriesRx<String> ts = redisson.getTimeSeries("myTimeSeries");
    
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

### Similarity Search (HNSW Algorithm)

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
	
### Use Cases (AI/ML Applications)

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