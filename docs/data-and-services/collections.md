## Map
Redis or Valkey based distributed [Map](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMap.html) object for Java implements [ConcurrentMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html) interface. This object is thread-safe. Consider to use [Live Object service](services.md/#live-object-service) to store POJO object as Redis or Valkey Map. Redis or Valkey uses serialized state to check key uniqueness instead of key's `hashCode()`/`equals()` methods.

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
|getMap()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|getLocalCachedMap()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ✔️ | ❌ | ✔️ |
|getClusteredMap()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|getClusteredLocalCachedMap()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |
<br/>

**2. Scripted eviction** 

Allows to define `time to live` or `max idle time` parameters per map entry. Eviction is done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

Each object implements [RMapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCache.html), [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheRx.html) interfaces.

Available implementations:

|RedissonClient<br/>method name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :-----------:| :---------:|
|getMapCache()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|getMapCache()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|getLocalCachedMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ❌ | ✔️ |
|getClusteredMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|getClusteredLocalCachedMapCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |
<br/>

**3. Advanced eviction**

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis or Valkey side.

Each object implements [RMapCacheV2](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheV2.html), [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheV2Async.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheV2Reactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheV2Rx.html) interfaces.

Available implementations:

|RedissonClient<br/>method name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :-----------:| :---------:|
|getMapCacheV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|getLocalCachedMapCacheV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |
<br/>

**4. Native eviction**

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side. Requires **Redis 7.4+**.

Each object implements [RMapCacheNative](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheNative.html), [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheNativeAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheNativeReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMapCacheNativeRx.html) interfaces.

Available implementations:

|RedissonClient<br/>method name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :-----------:| :---------:|
|getMapCacheNative()<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|getMapCacheNative()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|getLocalCachedMapCacheNative()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ❌ | ✔️ |
|getClusteredMapCacheNative()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
<br/>

Redisson also provides various [Cache API](../cache-API-implementations.md) implementations.

It's recommended to use single instance of Map instance with the same name for each Redisson client instance.

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
RMapCache<String, SomeObject> map = redisson.getLocalCachedMapCache("anyMap", LocalCachedMapOptions.defaults());
// or
RMapCache<String, SomeObject> map = redisson.getClusteredLocalCachedMapCache("anyMap", LocalCachedMapOptions.defaults());
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

Map object with local cache support implements [RLocalCachedMap](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLocalCachedMap.html) which extends [ConcurrentMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html) interface. This object is thread-safe.

It's recommended to use single instance of `LocalCachedMap` instance per name for each Redisson client instance. Same `LocalCachedMapOptions` object should be used across all instances with the same name.

Follow options can be supplied during object creation:
```java
      LocalCachedMapOptions options = LocalCachedMapOptions.defaults()

      // Defines whether to store a cache miss into the local cache.
      // Default value is false.
      .storeCacheMiss(false);

      // Defines store mode of cache data.
      // Follow options are available:
      // LOCALCACHE - store data in local cache only and use Redis or Valkey only for data update/invalidation.
      // LOCALCACHE_REDIS - store data in both Redis or Valkey and local cache.
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

     // Defines how to listen expired event sent by Redis or Valkey upon this instance deletion
     //
     // Follow expiration policies are available:
     // DONT_SUBSCRIBE - Don't subscribe on expire event
     // SUBSCRIBE_WITH_KEYEVENT_PATTERN - Subscribe on expire event using `__keyevent@*:expired` pattern
     // SUBSCRIBE_WITH_KEYSPACE_CHANNEL - Subscribe on expire event using `__keyspace@N__:name` channel
     .expirationEventPolicy(ExpirationEventPolicy.SUBSCRIBE_WITH_KEYEVENT_PATTERN);
```

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

Redisson allows to store Map data in external storage along with Redis or Valkey store.  
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

Usage of `RLocalCachedMap` and `RLocalCachedMapCache` objects boost Redis or Valkey read-operations up to **45x times** and give almost instant speed for database, web service or any other data source.

### Listeners

Redisson allows binding listeners per `RMap` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

`RMap` object allows to track follow events over the data.

|Listener class name|Event description | Redis or Valkey<br/>`notify-keyspace-events` value|
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
Map object which implements `RMapCache` interface could be bounded using [Least Recently Used (LRU)](https://en.wikipedia.org/wiki/Cache_replacement_policies#LRU) or [Least Frequently Used (LFU)](https://en.wikipedia.org/wiki/Least_frequently_used) order. Bounded Map allows to store map entries within defined limit and retire entries in defined order. 

Use cases: limited Redis or Valkey memory.

```java
RMapCache<String, SomeObject> map = redisson.getMapCache("anyMap");
// or
RMapCache<String, SomeObject> map = redisson.getMapCache("anyMap", MapCacheOptions.defaults());
// or
RMapCache<String, SomeObject> map = redisson.getLocalCachedMapCache("anyMap", LocalCachedMapOptions.defaults());
// or
RMapCache<String, SomeObject> map = redisson.getClusteredLocalCachedMapCache("anyMap", LocalCachedMapOptions.defaults());
// or
RMapCache<String, SomeObject> map = redisson.getClusteredMapCache("anyMap");
// or
RMapCache<String, SomeObject> map = redisson.getClusteredMapCache("anyMap", MapCacheOptions.defaults());


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
Redis or Valkey based [Multimap](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RMultimap.html) for Java allows to bind multiple values per key. This object is thread-safe. Keys amount limited to `4 294 967 295` elements. Redis or Valkey uses serialized state to check key uniqueness instead of key's `hashCode()`/`equals()` methods.

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
### Multimap eviction
Multimap distributed object for Java with eviction support implemented by separated MultimapCache object. There are [RSetMultimapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSetMultimapCache.html) and [RListMultimapCache](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RListMultimapCache.html) objects for Set and List based Multimaps respectively.  

Eviction task is started once per unique object name at the moment of getting Multimap instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

RSetMultimapCache example:
```java
RSetMultimapCache<String, String> multimap = redisson.getSetMultimapCache("myMultimap");
multimap.put("1", "a");
multimap.put("1", "b");
multimap.put("1", "c");

multimap.put("2", "e");
multimap.put("2", "f");

multimap.expireKey("2", 10, TimeUnit.MINUTES);

// if object is not used anymore
multimap.destroy();
```

## JSON Store

_This feature is available only in [Redisson PRO](https://redisson.pro) edition._

[RJsonStore](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RJsonStore.html) is a distributed Key Value store for JSON objects. Compatible with Redis or Valkey. This object is thread-safe. Allows to store JSON value mapped by key. Operations can be executed per key or group of keys. Value is stored/retrieved using `JSON.*` commands. Both key and value are POJO objects. 

Allows to define `time to live` parameter per entry. Doesn't use an entry eviction task, entries are cleaned on Redis or Valkey side.

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

It's recommended to use a single instance of `RLocalCachedJsonStore` instance per name for each Redisson client instance. Same `LocalCachedJsonStoreOptions` object should be used across all instances with the same name.

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
      // LOCALCACHE - store data in local cache only and use Redis or Valkey only for data update/invalidation.
      // LOCALCACHE_REDIS - store data in both Redis or Valkey and local cache.
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

     // Defines how to listen expired event sent by Redis or Valkey upon this instance deletion
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
Redis or Valkey based [Set](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSet.html) object for Java implements [Set](https://docs.oracle.com/javase/8/docs/api/java/util/Set.html) interface. This object is thread-safe. Keeps elements uniqueness via element state comparison. Set size limited to `4 294 967 295` elements. Redis or Valkey uses serialized state to check value uniqueness instead of value's `hashCode()`/`equals()` methods.

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

**entry eviction** - allows to define `time to live` parameter per SetCache entry. Redis or Valkey set structure doesn't support eviction thus it's done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting SetCache instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique SetCache object name. 

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
|getSet()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | ❌ | ✔️ |
|getSetCache()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ✔️ | ❌ | ✔️ |
|getSetCacheV2()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ❌ | ✔️ | ✔️ |
|getClusteredSet()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ❌ | ❌ | ✔️ |
|getClusteredSetCache()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | ❌ | ✔️ |

### Listeners

Redisson allows binding listeners per `RSet` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

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
Redis or Valkey based distributed [SortedSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RSortedSet.html) for Java implements [SortedSet](https://docs.oracle.com/javase/8/docs/api/java/util/SortedSet.html) interface. This object is thread-safe. It uses comparator to sort elements and keep uniqueness. For String data type it's recommended to use [LexSortedSet](#lexsortedset) object due to performance gain.
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
Redis or Valkey based distributed [ScoredSortedSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSet.html) object. Sorts elements by score defined during element insertion. Keeps elements uniqueness via element state comparison. 

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
|getScoredSortedSet()<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ✔️ |
|getClusteredScoredSortedSet()<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ |

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

Redisson allows binding listeners per `RScoredSortedSet` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

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

## LexSortedSet
Redis or Valkey based distributed [Set](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLexSortedSet.html) object for Java allows String objects only and implements `java.util.Set<String>` interface. It keeps elements in lexicographical order and maintain elements uniqueness via element state comparison. 

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLexSortedSetAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLexSortedSetReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLexSortedSetRx.html) interfaces.
```java
RLexSortedSet set = redisson.getLexSortedSet("simple");
set.add("d");
set.addAsync("e");
set.add("f");

set.rangeTail("d", false);
set.countHead("e");
set.range("d", true, "z", false);
```

### Listeners

Redisson allows binding listeners per `RLexSortedSet` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Element created/removed/updated after read operation|-|
|org.redisson.api.listener.ScoredSortedSetAddListener|Element created/updated|Ez|
|org.redisson.api.listener.ScoredSortedSetRemoveListener|Element removed|Ez|
|org.redisson.api.ExpiredObjectListener|`RScoredSortedSet` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RScoredSortedSet` object deleted|Eg|

Usage example:

```java
RLexSortedSet<String> set = redisson.getLexSortedSet("anySet");

int listenerId = set.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

set.removeListener(listenerId);
```

## List
Redis or Valkey based distributed [List](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RList.html) object for Java implements `java.util.List` interface. It keeps elements in insertion order. 

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RListAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RListReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RListRx.html) interfaces. List size is limited to `4 294 967 295` elements.
```java
RList<SomeObject> list = redisson.getList("anyList");
list.add(new SomeObject());
list.get(0);
list.remove(new SomeObject());
```

### Listeners

Redisson allows binding listeners per `RList` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Element created/removed/updated after read operation|-|
|org.redisson.api.listener.ListAddListener|Element created|El|
|org.redisson.api.listener.ListInsertListener|Element inserted|El|
|org.redisson.api.listener.ListSetListener|Element set/updated|El|
|org.redisson.api.listener.ListRemoveListener|Element removed|El|
|org.redisson.api.listener.ListTrimListener|List trimmed|El|
|org.redisson.api.ExpiredObjectListener|`RList` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RList` object deleted|Eg|

Usage example:

```java
RList<String> list = redisson.getList("anyList");

int listenerId = list.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

list.removeListener(listenerId);
```


## Queue
Redis or Valkey based distributed unbounded [Queue](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RQueue.html) object for Java implements [java.util.Queue](https://docs.oracle.com/javase/8/docs/api/java/util/Queue.html) interface. This object is thread-safe.  

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RQueueAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RQueueReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RQueueRx.html) interfaces.

```java
RQueue<SomeObject> queue = redisson.getQueue("anyQueue");
queue.add(new SomeObject());
SomeObject obj = queue.peek();
SomeObject someObj = queue.poll();
```

### Listeners

Redisson allows binding listeners per `RQueue` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Element created/removed/updated after read operation|-|
|org.redisson.api.listener.ListAddListener|Element created|El|
|org.redisson.api.listener.ListRemoveListener|Element removed|El|
|org.redisson.api.ExpiredObjectListener|`RQueue` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RQueue` object deleted|Eg|

Usage example:

```java
RQueue<String> queue = redisson.getQueue("anyList");

int listenerId = queue.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

queue.removeListener(listenerId);
```

## Deque
Redis or Valkey based distributed unbounded [Deque](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RDeque.html) object for Java implements `java.util.Deque` interface. This object is thread-safe.  

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RDequeAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RDequeReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RDequeRx.html) interfaces.
```java
RDeque<SomeObject> queue = redisson.getDeque("anyDeque");
queue.addFirst(new SomeObject());
queue.addLast(new SomeObject());
SomeObject obj = queue.removeFirst();
SomeObject someObj = queue.removeLast();
```

### Listeners

Redisson allows binding listeners per `RDeque` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Element created/removed/updated after read operation|-|
|org.redisson.api.listener.ListAddListener|Element created|El|
|org.redisson.api.listener.ListRemoveListener|Element removed|El|
|org.redisson.api.ExpiredObjectListener|`RDeque` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RDeque` object deleted|Eg|

Usage example:

```java
RDeque<String> deque = redisson.getDeque("anyList");

int listenerId = deque.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

deque.removeListener(listenerId);
```

## Blocking Queue
Redis or Valkey based distributed unbounded [BlockingQueue](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBlockingQueue.html) object for Java implements `java.util.concurrent.BlockingQueue` interface. This object is thread-safe.  

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBlockingQueueAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBlockingQueueReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBlockingQueueRx.html) interfaces.

```java
RBlockingQueue<SomeObject> queue = redisson.getBlockingQueue("anyQueue");

queue.offer(new SomeObject());

SomeObject obj = queue.peek();
SomeObject obj = queue.poll();
SomeObject obj = queue.poll(10, TimeUnit.MINUTES);
```
`poll`, `pollFromAny`, `pollLastAndOfferFirstTo` and `take` methods are resubscribed automatically during re-connection to server or failover.

## Bounded Blocking Queue
Redis or Valkey based distributed [BoundedBlockingQueue](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBoundedBlockingQueue.html) for Java implements `java.util.concurrent.BlockingQueue` interface. BoundedBlockingQueue size limited to `4 294 967 295` elements. This object is thread-safe.

Queue capacity should be defined once by `trySetCapacity()` method before the usage:
```java
RBoundedBlockingQueue<SomeObject> queue = redisson.getBoundedBlockingQueue("anyQueue");
// returns `true` if capacity set successfully and `false` if it already set.
queue.trySetCapacity(2);

queue.offer(new SomeObject(1));
queue.offer(new SomeObject(2));
// will be blocked until free space available in queue
queue.put(new SomeObject());

SomeObject obj = queue.peek();
SomeObject someObj = queue.poll();
SomeObject ob = queue.poll(10, TimeUnit.MINUTES);
```

`poll`, `pollFromAny`, `pollLastAndOfferFirstTo` and `take` methods will be resubscribed automatically during reconnection to server or failover.

## Blocking Deque
Java implementation of Redis or Valkey based [BlockingDeque](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBlockingDeque.html) implements `java.util.concurrent.BlockingDeque` interface. This object is thread-safe.

It has [Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBlockingDequeAsync.html), [Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBlockingDequeReactive.html) and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBlockingDequeRx.html) interfaces.

```java
RBlockingDeque<Integer> deque = redisson.getBlockingDeque("anyDeque");
deque.putFirst(1);
deque.putLast(2);
Integer firstValue = queue.takeFirst();
Integer lastValue = queue.takeLast();
Integer firstValue = queue.pollFirst(10, TimeUnit.MINUTES);
Integer lastValue = queue.pollLast(3, TimeUnit.MINUTES);
```
`poll`, `pollFromAny`, `pollLastAndOfferFirstTo` and `take` methods are resubscribed automatically during re-connection to server or failover.

## Delayed Queue
Redis or Valkey based [DelayedQueue](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RDelayedQueue.html) object for Java allows to transfer each element to destination queue with specified delay. Destination queue could be any queue implemented `RQueue` interface. This object is thread-safe.  

Could be useful for exponential backoff strategy used for message delivery to consumer. If application is restarted, an instance of delayed queue should created in order for the pending items to be added to the destination queue.

```java
RBlockingQueue<String> distinationQueue = ...
RDelayedQueue<String> delayedQueue = getDelayedQueue(distinationQueue);
// move object to distinationQueue in 10 seconds
delayedQueue.offer("msg1", 10, TimeUnit.SECONDS);
// move object to distinationQueue in 1 minutes
delayedQueue.offer("msg2", 1, TimeUnit.MINUTES);


// msg1 will appear in 10 seconds
distinationQueue.poll(15, TimeUnit.SECONDS);

// msg2 will appear in 2 seconds
distinationQueue.poll(2, TimeUnit.SECONDS);

```

Object should be destroyed if it not used anymore, but it's not necessary to call destroy method if Redisson goes shutdown.
```java
RDelayedQueue<String> delayedQueue = ...
delayedQueue.destroy();
```

## Priority Queue
Java implementation of Redis or Valkey based [PriorityQueue](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPriorityQueue.html) implements [java.util.Queue](https://docs.oracle.com/javase/8/docs/api/java/util/Queue.html) interface. Elements are ordered according to natural order of [Comparable](https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html) interface or defined [Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html). This object is thread-safe.  

Use `trySetComparator()` method to define own [Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html). 

Code example:
```java
public class Entry implements Comparable<Entry>, Serializable {

    private String key;
    private Integer value;

    public Entry(String key, Integer value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public int compareTo(Entry o) {
        return key.compareTo(o.key);
    }

}

RPriorityQueue<Entry> queue = redisson.getPriorityQueue("anyQueue");
queue.add(new Entry("b", 1));
queue.add(new Entry("c", 1));
queue.add(new Entry("a", 1));

// Entry [a:1]
Entry e = queue.poll();
// Entry [b:1]
Entry e = queue.poll();
// Entry [c:1]
Entry e = queue.poll();
```

## Priority Deque
Java implementation of Redis or Valkey based [PriorityDeque](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPriorityDeque.html) implements [java.util.Deque](https://docs.oracle.com/javase/8/docs/api/java/util/Deque.html) interface. Elements are ordered according to natural order of [java.lang.Comparable](https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html) interface or defined [java.util.Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html). This object is thread-safe.  

Use `trySetComparator()` method to define own [Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html). 

Code example:
```java
public class Entry implements Comparable<Entry>, Serializable {

    private String key;
    private Integer value;

    public Entry(String key, Integer value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public int compareTo(Entry o) {
        return key.compareTo(o.key);
    }

}

RPriorityDeque<Entry> queue = redisson.getPriorityDeque("anyQueue");
queue.add(new Entry("b", 1));
queue.add(new Entry("c", 1));
queue.add(new Entry("a", 1));

// Entry [a:1]
Entry e = queue.pollFirst();
// Entry [c:1]
Entry e = queue.pollLast();
```

## Priority Blocking Queue
Java implementation of Redis or Valkey based [PriorityBlockingQueue](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPriorityBlockingQueue.html) similar to JDK [java.util.concurrent.PriorityBlockingQueue](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/PriorityBlockingQueue.html) object. Elements are ordered according to natural order of [java.lang.Comparable](https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html) interface or defined [java.util.Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html). This object is thread-safe.  

Use `trySetComparator()` method to define own [java.util.Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html). 

`poll`, `pollLastAndOfferFirstTo` and `take` methods are resubscribed automatically during re-connection to a server or failover.

Code example:
```java
public class Entry implements Comparable<Entry>, Serializable {

    private String key;
    private Integer value;

    public Entry(String key, Integer value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public int compareTo(Entry o) {
        return key.compareTo(o.key);
    }

}

RPriorityBlockingQueue<Entry> queue = redisson.getPriorityBlockingQueue("anyQueue");
queue.add(new Entry("b", 1));
queue.add(new Entry("c", 1));
queue.add(new Entry("a", 1));

// Entry [a:1]
Entry e = queue.take();
```

## Priority Blocking Deque
Java implementation of Redis or Valkey based [PriorityBlockingDeque](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RPriorityBlockingDeque.html) implements [java.util.concurrent.BlockingDeque](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/BlockingDeque.html) interface. Elements are ordered according to natural order of [java.lang.Comparable](https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html) interface or defined [java.util.Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html). This object is thread-safe.  

Use `trySetComparator()` method to define own [java.util.Comparator](https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html). 

`poll`, `pollLastAndOfferFirstTo`, `take` methods are resubscribed automatically during re-connection to Redis or Valkey server or failover.

Code example:
```java
public class Entry implements Comparable<Entry>, Serializable {

    private String key;
    private Integer value;

    public Entry(String key, Integer value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public int compareTo(Entry o) {
        return key.compareTo(o.key);
    }

}

RPriorityBlockingDeque<Entry> queue = redisson.getPriorityBlockingDeque("anyQueue");
queue.add(new Entry("b", 1));
queue.add(new Entry("c", 1));
queue.add(new Entry("a", 1));

// Entry [a:1]
Entry e = queue.takeFirst();
// Entry [c:1]
Entry e = queue.takeLast();
```

## Stream
Java implementation of Redis or Valkey based [Stream](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RStream.html) object wraps [Stream](https://redis.io/topics/streams-intro) feature. Basically it allows to create Consumers Group which consume data added by Producers. This object is thread-safe.  

```java
RStream<String, String> stream = redisson.getStream("test");

StreamMessageId sm = stream.add(StreamAddArgs.entry("0", "0"));

stream.createGroup("testGroup");
        
StreamId id1 = stream.add(StreamAddArgs.entry("1", "1"));
StreamId id2 = stream.add(StreamAddArgs.entry("2", "2"));
        
Map<StreamId, Map<String, String>> group = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());

// return entries in pending state after read group method execution
Map<StreamMessageId, Map<String, String>> pendingData = stream.pendingRange("testGroup", "consumer1", StreamMessageId.MIN, StreamMessageId.MAX, 100);

// transfer ownership of pending messages to a new consumer
List<StreamMessageId> transferedIds = stream.fastClaim("testGroup", "consumer2", 1, TimeUnit.MILLISECONDS, id1, id2);

// mark pending entries as correctly processed
long amount = stream.ack("testGroup", id1, id2);
```

Code example of **[Async interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RStreamAsync.html)** usage:

```java
RStream<String, String> stream = redisson.getStream("test");

RFuture<StreamMessageId> smFuture = stream.addAsync(StreamAddArgs.entry("0", "0"));

RFuture<Void> groupFuture = stream.createGroupAsync("testGroup");
        
RFuture<StreamId> id1Future = stream.addAsync(StreamAddArgs.entry("1", "1"));
RFuture<StreamId> id2Future = stream.addAsync(StreamAddArgs.entry("2", "2"));
        
RFuture<Map<StreamId, Map<String, String>>> groupResultFuture = stream.readGroupAsync("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());

// return entries in pending state after read group method execution
RFuture<Map<StreamMessageId, Map<String, String>>> pendingDataFuture = stream.pendingRangeAsync("testGroup", "consumer1", StreamMessageId.MIN, StreamMessageId.MAX, 100);

// transfer ownership of pending messages to a new consumer
RFuture<List<StreamMessageId>> transferedIdsFuture = stream.fastClaim("testGroup", "consumer2", 1, TimeUnit.MILLISECONDS, id1, id2);

// mark pending entries as correctly processed
RFuture<Long> amountFuture = stream.ackAsync("testGroup", id1, id2);

amountFuture.whenComplete((res, exception) -> {
    // ...
});
```

Code example of **[Reactive interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RStreamReactive.html)** usage:

```java
RedissonReactiveClient redisson = redissonClient.reactive();
RStreamReactive<String, String> stream = redisson.getStream("test");

Mono<StreamMessageId> smMono = stream.add(StreamAddArgs.entry("0", "0"));

Mono<Void> groupMono = stream.createGroup("testGroup");
        
Mono<StreamId> id1Mono = stream.add(StreamAddArgs.entry("1", "1"));
Mono<StreamId> id2Mono = stream.add(StreamAddArgs.entry("2", "2"));
        
Mono<Map<StreamId, Map<String, String>>> groupMono = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());

// return entries in pending state after read group method execution
Mono<Map<StreamMessageId, Map<String, String>>> pendingDataMono = stream.pendingRange("testGroup", "consumer1", StreamMessageId.MIN, StreamMessageId.MAX, 100);

// transfer ownership of pending messages to a new consumer
Mono<List<StreamMessageId>> transferedIdsMono = stream.fastClaim("testGroup", "consumer2", 1, TimeUnit.MILLISECONDS, id1, id2);

// mark pending entries as correctly processed
Mono<Long> amountMono = stream.ack("testGroup", id1, id2);

amountMono.doOnNext(res -> {
   // ...
}).subscribe();
```

Code example of **[RxJava3 interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RStreamRx.html)** usage:

```java
RedissonRxClient redisson = redissonClient.rxJava();
RStreamRx<String, String> stream = redisson.getStream("test");

Single<StreamMessageId> smRx = stream.add(StreamAddArgs.entry("0", "0"));

Completable groupRx = stream.createGroup("testGroup");
        
Single<StreamId> id1Rx = stream.add(StreamAddArgs.entry("1", "1"));
Single<StreamId> id2Rx = stream.add(StreamAddArgs.entry("2", "2"));
        
Single<Map<StreamId, Map<String, String>>> groupRx = stream.readGroup("testGroup", "consumer1", StreamReadGroupArgs.neverDelivered());

// return entries in pending state after read group method execution
Single<Map<StreamMessageId, Map<String, String>>> pendingDataRx = stream.pendingRange("testGroup", "consumer1", StreamMessageId.MIN, StreamMessageId.MAX, 100);

// transfer ownership of pending messages to a new consumer
Single<List<StreamMessageId>> transferedIdsRx = stream.fastClaim("testGroup", "consumer2", 1, TimeUnit.MILLISECONDS, id1, id2);

// mark pending entries as correctly processed
Single<Long> amountRx = stream.ack("testGroup", id1, id2);

amountRx.doOnSuccess(res -> {
   // ...
}).subscribe();
```

### Listeners

Redisson allows binding listeners per `RStream` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Element added/removed/updated after read operation|-|
|org.redisson.api.ExpiredObjectListener|`RStream` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RStream` object deleted|Eg|
|org.redisson.api.listener.StreamAddListener|Element added|Et|
|org.redisson.api.listener.StreamRemoveListener|Element removed|Et|
|org.redisson.api.listener.StreamCreateGroupListener|Group created|Et|
|org.redisson.api.listener.StreamRemoveGroupListener|Group removed|Et|
|org.redisson.api.listener.StreamCreateConsumerListener|Consumer created|Et|
|org.redisson.api.listener.StreamRemoveConsumerListener|Consumer removed|Et|
|org.redisson.api.listener.StreamTrimListener|Stream trimmed|Et|

Usage example:

```java
RStream<String, String> stream = redisson.getStream("anySet");

int listenerId = stream.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

int listenerId = stream.addListener(new StreamAddListener() {
    @Override
    public void onAdd(String name) {
        // ...
    }
});


// ...

stream.removeListener(listenerId);
```

## Ring Buffer

Java implementation of Redis or Valkey based [RingBuffer](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RRingBuffer.html) implements [java.util.Queue](https://docs.oracle.com/javase/8/docs/api/java/util/Queue.html) interface. This structure evicts elements from the head if queue capacity became full. This object is thread-safe.  

Should be initialized with capacity size by `trySetCapacity()` method before usage. 

Code example:

```java
RRingBuffer<Integer> buffer = redisson.getRingBuffer("test");

// buffer capacity is 4 elements
buffer.trySetCapacity(4);

buffer.add(1);
buffer.add(2);
buffer.add(3);
buffer.add(4);

// buffer state is 1, 2, 3, 4

buffer.add(5);
buffer.add(6);

// buffer state is 3, 4, 5, 6
```

Code example of **[Async interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRingBufferAsync.html)** usage:

```java
RRingBuffer<Integer> buffer = redisson.getRingBuffer("test");

// buffer capacity is 4 elements
RFuture<Boolean> capacityFuture = buffer.trySetCapacityAsync(4);

RFuture<Boolean> addFuture = buffer.addAsync(1);
RFuture<Boolean> addFuture = buffer.addAsync(2);
RFuture<Boolean> addFuture = buffer.addAsync(3);
RFuture<Boolean> addFuture = buffer.addAsync(4);

// buffer state is 1, 2, 3, 4

RFuture<Boolean> addFuture = buffer.addAsync(5);
RFuture<Boolean> addFuture = buffer.addAsync(6);

// buffer state is 3, 4, 5, 6

addFuture.whenComplete((res, exception) -> {
    // ...
});
```

Code example of **[Reactive interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRingBufferReactive.html)** usage:

```java
RedissonReactiveClient redisson = redissonClient.reactive();
RRingBufferReactive<Integer> buffer = redisson.getRingBuffer("test");

// buffer capacity is 4 elements
Mono<Boolean> capacityMono = buffer.trySetCapacity(4);

Mono<Boolean> addMono = buffer.add(1);
Mono<Boolean> addMono = buffer.add(2);
Mono<Boolean> addMono = buffer.add(3);
Mono<Boolean> addMono = buffer.add(4);

// buffer state is 1, 2, 3, 4

Mono<Boolean> addMono = buffer.add(5);
Mono<Boolean> addMono = buffer.add(6);

// buffer state is 3, 4, 5, 6

addMono.doOnNext(res -> {
   // ...
}).subscribe();
```

Code example of **[RxJava3 interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RRingBufferRx.html)** usage:

```java
RedissonRxClient redisson = redissonClient.rxJava();
RRingBufferRx<Integer> buffer = redisson.getRingBuffer("test");

// buffer capacity is 4 elements
Single<Boolean> capacityRx = buffer.trySetCapacity(4);

Single<Boolean> addRx = buffer.add(1);
Single<Boolean> addRx = buffer.add(2);
Single<Boolean> addRx = buffer.add(3);
Single<Boolean> addRx = buffer.add(4);

// buffer state is 1, 2, 3, 4

Single<Boolean> addRx = buffer.add(5);
Single<Boolean> addRx = buffer.add(6);

// buffer state is 3, 4, 5, 6

addRx.doOnSuccess(res -> {
   // ...
}).subscribe();
```

### Listeners

Redisson allows binding listeners per `RRingBuffer` object. This requires the `notify-keyspace-events` setting to be enabled on Redis or Valkey side.

|Listener class name|Event description | Valkey or Redis<br/>`notify-keyspace-events` value|
|:--:|:--:|:--:|
|org.redisson.api.listener.TrackingListener|Element created/removed/updated after read operation|-|
|org.redisson.api.listener.ListAddListener|Element created|El|
|org.redisson.api.listener.ListRemoveListener|Element removed|El|
|org.redisson.api.ExpiredObjectListener|`RRingBuffer` object expired|Ex|
|org.redisson.api.DeletedObjectListener|`RRingBuffer` object deleted|Eg|

Usage example:

```java
RRingBuffer<String> queue = redisson.getRingBuffer("anyList");

int listenerId = queue.addListener(new DeletedObjectListener() {
     @Override
     public void onDeleted(String name) {
        // ...
     }
});

// ...

queue.removeListener(listenerId);
```


## Transfer Queue

Java implementation of Redis or Valkey based [TransferQueue](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTransferQueue.html) implements [java.util.concurrent.TransferQueue](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/TransferQueue.html) interface. Provides set of `transfer` methods which return only when value was successfully hand off to consumer. This object is thread-safe.  

`poll` and `take` methods are resubscribed automatically during re-connection to a server or failover.

Code example:
```java
RTransferQueue<String> queue = redisson.getTransferQueue("myCountDownLatch");

queue.transfer("data");
// or try transfer immediately
queue.tryTransfer("data");
// or try transfer up to 10 seconds
queue.tryTransfer("data", 10, TimeUnit.SECONDS);

// in other thread or JVM

queue.take();
// or
queue.poll();
```

Code example of **[Async interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTransferQueueAsync.html)** usage:
```java
RTransferQueue<String> queue = redisson.getTransferQueue("myCountDownLatch");

RFuture<Void> future = queue.transferAsync("data");
// or try transfer immediately
RFuture<Boolean> future = queue.tryTransferAsync("data");
// or try transfer up to 10 seconds
RFuture<Boolean> future = queue.tryTransferAsync("data", 10, TimeUnit.SECONDS);

// in other thread or JVM

RFuture<String> future = queue.takeAsync();
// or
RFuture<String> future = queue.pollAsync();

future.whenComplete((res, exception) -> {
    // ...
});
```

Code example of **[Reactive interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTransferQueueReactive.html)** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RTransferQueueReactive<String> queue = redisson.getTransferQueue("myCountDownLatch");

Mono<Void> mono = queue.transfer("data");
// or try transfer immediately
Mono<Boolean> mono = queue.tryTransfer("data");
// or try transfer up to 10 seconds
Mono<Boolean> mono = queue.tryTransfer("data", 10, TimeUnit.SECONDS);

// in other thread or JVM

Mono<String> mono = queue.take();
// or
Mono<String> mono = queue.poll();

mono.doOnNext(res -> {
   // ...
}).subscribe();
```

Code example of **[RxJava3 interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RTransferQueueRx.html)** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RTransferQueueRx<String> queue = redisson.getTransferQueue("myCountDownLatch");

Completable res = queue.transfer("data");
// or try transfer immediately
Single<Boolean> resRx = queue.tryTransfer("data");
// or try transfer up to 10 seconds
Single<Boolean> resRx = queue.tryTransfer("data", 10, TimeUnit.SECONDS);

// in other thread or JVM

Single<String> resRx = queue.take();
// or
Maybe<String> resRx = queue.poll();

resRx.doOnSuccess(res -> {
   // ...
}).subscribe();
```

## Time Series
Java implementation of Redis or Valkey based [TimeSeries](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTimeSeries.html) object allows to store value by timestamp and define TTL(time-to-live) per entry. Values are ordered by timestamp. This object is thread-safe.  

Code example:
```java
RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");

ts.add(201908110501, "10%");
ts.add(201908110502, "30%");
ts.add(201908110504, "10%");
ts.add(201908110508, "75%");

// entry time-to-live is 10 hours
ts.add(201908110510, "85%", 10, TimeUnit.HOURS);
ts.add(201908110510, "95%", 10, TimeUnit.HOURS);

String value = ts.get(201908110508);
ts.remove(201908110508);

Collection<String> values = ts.pollFirst(2);
Collection<String> range = ts.range(201908110501, 201908110508);
```

Code example of **[Async interface](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTimeSeriesAsync.html)** usage:
```java
RTimeSeries<String> ts = redisson.getTimeSeries("myTimeSeries");

RFuture<Void> future = ts.addAsync(201908110501, "10%");
RFuture<Void> future = ts.addAsync(201908110502, "30%");
RFuture<Void> future = ts.addAsync(201908110504, "10%");
RFuture<Void> future = ts.addAsync(201908110508, "75%");

// entry time-to-live is 10 hours
RFuture<Void> future = ts.addAsync(201908110510, "85%", 10, TimeUnit.HOURS);
RFuture<Void> future = ts.addAsync(201908110510, "95%", 10, TimeUnit.HOURS);

RFuture<String> future = ts.getAsync(201908110508);
RFuture<Boolean> future = ts.removeAsync(201908110508);

RFuture<Collection<String>> future = t.pollFirstAsync(2);
RFuture<Collection<String>> future = t.rangeAsync(201908110501, 201908110508);

future.whenComplete((res, exception) -> {
    // ...
});
```

Code example of **[Reactive interface](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTimeSeriesReactive.html)** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RTimeSeriesReactive<String> ts = redisson.getTimeSeries("myTimeSeries");

Mono<Void> mono = ts.add(201908110501, "10%");
Mono<Void> mono = ts.add(201908110502, "30%");
Mono<Void> mono = ts.add(201908110504, "10%");
Mono<Void> mono = ts.add(201908110508, "75%");

// entry time-to-live is 10 hours
Mono<Void> mono = ts.add(201908110510, "85%", 10, TimeUnit.HOURS);
Mono<Void> mono = ts.add(201908110510, "95%", 10, TimeUnit.HOURS);

Mono<String> mono = ts.get(201908110508);
Mono<Boolean> mono = ts.remove(201908110508);

Mono<Collection<String>> mono = ts.pollFirst(2);
Mono<Collection<String>> mono = ts.range(201908110501, 201908110508);

mono.doOnNext(res -> {
   // ...
}).subscribe();
```

Code example of **[RxJava3 interface](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTimeSeriesRx.html)** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RTimeSeriesRx<String> ts = redisson.getTimeSeries("myTimeSeries");

Completable rx = ts.add(201908110501, "10%");
Completable rx = ts.add(201908110502, "30%");
Completable rx = ts.add(201908110504, "10%");
Completable rx = ts.add(201908110508, "75%");

// entry time-to-live is 10 hours
Completable rx = ts.add(201908110510, "85%", 10, TimeUnit.HOURS);
Completable rx = ts.add(201908110510, "95%", 10, TimeUnit.HOURS);

Maybe<String> rx = ts.get(201908110508);
Single<Boolean> rx = ts.remove(201908110508);

Single<Collection<String>> rx = ts.pollFirst(2);
Single<Collection<String>> rx = ts.range(201908110501, 201908110508);

rx.doOnSuccess(res -> {
   // ...
}).subscribe();
```