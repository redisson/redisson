# Redis based Hibernate Cache implementation

Implements [Hibernate 2nd level Cache](https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#caching) provider based on Redis.  
Supports all Hibernate cache strategies: `READ_ONLY`, `NONSTRICT_READ_WRITE`, `READ_WRITE` and `TRANSACTIONAL`.  

Compatible with Hibernate 4.x, 5.1.x, 5.2.x, 5.3.3+ up to 5.6.x and 6.0.2+ up to 6.x.x

Redisson provides various Hibernate Cache factories including those with features below:

**local cache** - so called `near cache`, which is useful for use cases when Hibernate Cache used mostly for read operations and/or network roundtrips are undesirable. It caches Map entries on Redisson side and executes read operations up to **5x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

**data partitioning** - although all implementations are cluster compatible thier content isn't scaled/partitioned across multiple Redis master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Hibernate Cache instance in Redis cluster.  

#### 1. Scripted eviction

Allows to define `time to live` or `max idle time` parameters per map entry. Redis hash structure doesn't support eviction thus it's done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](https://github.com/redisson/redisson/wiki/2.-Configuration#cleanupkeysamount) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](https://github.com/redisson/redisson/wiki/2.-Configuration#mincleanupdelay) and [maxCleanUpDelay](https://github.com/redisson/redisson/wiki/2.-Configuration#maxcleanupdelay). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

Available implementations:

|Class name | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonRegionFactory<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|RedissonRegionFactory<br/><sub><i>[Redisson PRO](http://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|RedissonLocalCachedRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|RedissonClusteredRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|RedissonClusteredLocalCachedRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |

#### 2. Advanced eviction

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side.

Available implementations:

|Class name | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonRegionV2Factory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ❌ | ✔️ | ✔️ |
|RedissonLocalCachedV2RegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ✔️ | ✔️ |

#### 3. Native eviction

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side.  
Requires **Redis 7.4+**.

Available implementations:

|Class name | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonRegionNativeFactory<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|RedissonRegionNativeFactory<br/><sub><i>[Redisson PRO](http://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|RedissonLocalCachedNativeRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|RedissonClusteredNativeRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |

## Hibernate Cache Usage

### 1. Add `redisson-hibernate` dependency into your project:

Maven

```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <!-- for Hibernate v4.x -->
         <artifactId>redisson-hibernate-4</artifactId>
         <!-- for Hibernate v5.0.x - v5.1.x -->
         <artifactId>redisson-hibernate-5</artifactId>
         <!-- for Hibernate v5.2.x -->
         <artifactId>redisson-hibernate-52</artifactId>
         <!-- for Hibernate v5.3.3+ - v5.6.x -->
         <artifactId>redisson-hibernate-53</artifactId>
         <!-- for Hibernate v6.0.2+ - v6.x.x -->
         <artifactId>redisson-hibernate-6</artifactId>
         <version>3.36.0</version>
     </dependency>
```

Gradle

```groovy
     // for Hibernate v4.x
     compile 'org.redisson:redisson-hibernate-4:3.36.0'
     // for Hibernate v5.0.x - v5.1.x
     compile 'org.redisson:redisson-hibernate-5:3.36.0'
     // for Hibernate v5.2.x
     compile 'org.redisson:redisson-hibernate-52:3.36.0'
     // for Hibernate v5.3.3+ - v5.6.x
     compile 'org.redisson:redisson-hibernate-53:3.36.0'
     // for Hibernate v6.0.2+ - v6.x.x
     compile 'org.redisson:redisson-hibernate-6:3.36.0'
```

### 2. Specify hibernate cache settings

Define Redisson Region Cache Factory:

```xml
<!-- Redisson Region Cache factory -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonRegionV2Factory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonLocalCachedRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonLocalCachedV2RegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonClusteredRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonClusteredLocalCachedRegionFactory" />
```

By default each Region Factory creates own Redisson instance. For multiple applications, using the same Redis setup and deployed in the same JVM, amount of Redisson instances could be reduced using JNDI registry:

```xml
<!-- name of Redisson instance registered in JNDI -->
<property name="hibernate.cache.redisson.jndi_name" value="redisson_instance" />

<!-- JNDI Redisson Region Cache factory -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.JndiRedissonRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.JndiRedissonRegionV2Factory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.JndiRedissonLocalCachedRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.JndiRedissonLocalCachedV2RegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.JndiRedissonClusteredRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.JndiRedissonClusteredLocalCachedRegionFactory" />
```

```xml
<!-- 2nd level cache activation -->
<property name="hibernate.cache.use_second_level_cache" value="true" />
<property name="hibernate.cache.use_query_cache" value="true" />

<!-- Redisson can fallback on database if Redis cache is unavailable -->
<property name="hibernate.cache.redisson.fallback" value="true" />

<!-- Redisson YAML config (located in filesystem or classpath) -->
<property name="hibernate.cache.redisson.config" value="/redisson.yaml" />
```

#### Redisson allows to define follow cache settings per entity, collection, naturalid, query and timestamp regions:

`REGION_NAME` - is a name of region which is defined in @Cache annotation otherwise it's a fully qualified class name.

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].eviction.max_entries`** |
|Description| Max size of cache. Superfluous entries in Redis are evicted using LRU algorithm.<br/>`0` value means unbounded cache. |
|Default value| `0` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].expiration.time_to_live`** |
|Description| Time to live per cache entry in Redis. Defined in milliseconds.<br/>`0` value means this setting doesn't affect expiration. |
|Default value| `0` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].expiration.max_idle_time`** |
|Description| Max idle time per cache entry in Redis. Defined in milliseconds.<br/>`0` value means this setting doesn't affect expiration.  |
|Default value| `0` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].localcache.cache_provider`** |
|Description| Cache provider used as local cache store.<br/>`REDISSON` and `CAFFEINE` providers are available.  |
|Default value| `REDISSON` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].localcache.store_mode`** |
|Description| Store mode of cache data.<br/>`LOCALCACHE` - store data in local cache only and use Redis only for data update/invalidation<br/>`LOCALCACHE_REDIS` - store data in both Redis and local cache |
|Default value| `LOCALCACHE` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].localcache.max_idle_time`** |
|Description| Max idle time per entry in local cache. Defined in milliseconds.<br/>`0` value means this setting doesn't affect expiration |
|Default value| `0` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].localcache.time_to_live`** |
|Description| Time to live per entry in local cache. Defined in milliseconds.<br/>`0` value means this setting doesn't affect expiration |
|Default value| `0` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].localcache.eviction_policy`** |
|Description| Eviction policy applied to local cache entries when cache size limit reached.<br/>`LFU`, `LRU`, `SOFT`, `WEAK` and `NONE` policies are available. |
|Default value| `NONE` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].localcache.sync_strategy`** |
|Description| Sync strategy used to synchronize local cache changes across all instances.<br/>`INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change<br/>`UPDATE` - Update cache entry across all LocalCachedMap instances on map entry change<br/>`NONE` - No synchronizations on map changes |
|Default value| `INVALIDATE` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].localcache.reconnection_strategy`** |
|Description| Reconnection strategy used to load missed local cache updates through Hibernate during any connection failures to Redis.<br/>`CLEAR` - Clear local cache if map instance has been disconnected for a while<br/>`LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise<br/>`NONE` - No reconnection handling |
|Default value| `NONE` |

| | |
|-|-|
|Parameter| **`hibernate.cache.redisson.[REGION_NAME].localcache.size`** |
|Description| Max size of local cache. Superfluous entries in Redis are evicted using defined eviction policy.<br/>`0` value means unbounded cache. |
|Default value| `0` |

_**NOTE**: `hibernate.cache.redisson.[REGION_NAME].localcache.*` settings are available for `RedissonClusteredLocalCachedRegionFactory` and `RedissonLocalCachedRegionFactory` classes only._

#### Default region configuration used for all caches not specified in configuration:

```xml
<!-- cache definition applied to all caches in entity region -->
<property name="hibernate.cache.redisson.entity.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.entity.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.entity.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.entity.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.entity.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.entity.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.entity.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.entity.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.entity.localcache.size" value="5000" />

<!-- cache definition applied to all caches in collection region -->
<property name="hibernate.cache.redisson.collection.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.collection.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.collection.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.collection.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.collection.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.collection.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.collection.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.collection.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.collection.localcache.size" value="5000" />

<!-- cache definition applied to all caches in naturalid region -->
<property name="hibernate.cache.redisson.naturalid.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.naturalid.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.naturalid.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.naturalid.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.naturalid.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.naturalid.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.naturalid.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.naturalid.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.naturalid.localcache.size" value="5000" />

<!-- cache definition applied to all caches in query region -->
<property name="hibernate.cache.redisson.query.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.query.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.query.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.query.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.query.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.query.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.query.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.query.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.query.localcache.size" value="5000" />

<!-- cache definition for timestamps region -->
<property name="hibernate.cache.redisson.timestamps.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.timestamps.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.timestamps.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.timestamps.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.timestamps.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.timestamps.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.timestamps.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.timestamps.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.timestamps.localcache.size" value="5000" />
```

#### Configuration per entity/collection/naturalid/query region overrides default configuration:

```xml
<!-- cache definition for entity region. Example region name: "my_object" -->
<property name="hibernate.cache.redisson.my_object.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.my_object.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.my_object.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.my_object.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.my_object.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.my_object.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.my_object.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.my_object.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.my_object.localcache.size" value="5000" />

<!-- cache definition for collection region. Example region name: "my_list" -->
<property name="hibernate.cache.redisson.my_list.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.my_list.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.my_list.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.my_list.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.my_list.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.my_list.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.my_list.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.my_list.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.my_list.localcache.size" value="5000" />

<!-- cache definition for naturalid region. Suffixed by ##NaturalId. Example region name: "my_object" -->
<property name="hibernate.cache.redisson.my_object##NaturalId.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.my_object##NaturalId.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.my_object##NaturalId.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.my_object##NaturalId.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.my_object##NaturalId.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.my_object##NaturalId.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.my_object##NaturalId.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.my_object##NaturalId.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.my_object##NaturalId.localcache.size" value="5000" />

<!-- cache definition for query region. Example region name: "my_query" -->
<property name="hibernate.cache.redisson.my_query.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.my_query.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.my_query.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.my_query.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.my_query.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.my_query.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.my_query.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.my_query.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.my_query.localcache.size" value="5000" />
```
