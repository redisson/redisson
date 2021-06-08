# Redis based Hibernate Cache implementation

Implements [Hibernate 2nd level Cache](https://docs.jboss.org/hibernate/orm/5.4/userguide/html_single/Hibernate_User_Guide.html#caching) provider based on Redis.  
Supports all Hibernate cache strategies: `READ_ONLY`, `NONSTRICT_READ_WRITE`, `READ_WRITE` and `TRANSACTIONAL`.  

Compatible with Hibernate 4.x, 5.1.x, 5.2.x and 5.3.3+ up to 5.4.x

Redisson provides various Hibernate Cache factories including those with features below:

**local cache** - so called `near cache`, which is useful for use cases when Hibernate Cache used mostly for read operations and/or network roundtrips are undesirable. It caches Map entries on Redisson side and executes read operations up to **5x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between instances.  

**data partitioning** - data partitioning in cluster mode. Scales available memory, read/write operations and entry eviction process for individual Hibernate Cache instance in Redis cluster.

Below is the list of all available factories with local cache and/or data partitioning support:

|Class name | Local cache | Data<br/>partitioning | Ultra-fast read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonRegionFactory<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|RedissonRegionFactory<br/><sub><i>[Redisson PRO](http://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|RedissonLocalCachedRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|RedissonClusteredRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|RedissonClusteredLocalCachedRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |

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
         <!-- for Hibernate v5.3.3+ - v5.4.x -->
         <artifactId>redisson-hibernate-53</artifactId>
         <version>3.15.6</version>
     </dependency>
```

Gradle

```groovy
     // for Hibernate v4.x
     compile 'org.redisson:redisson-hibernate-4:3.15.6'
     // for Hibernate v5.0.x - v5.1.x
     compile 'org.redisson:redisson-hibernate-5:3.15.6'
     // for Hibernate v5.2.x
     compile 'org.redisson:redisson-hibernate-52:3.15.6'
     // for Hibernate v5.3.3+ - v5.4.x
     compile 'org.redisson:redisson-hibernate-53:3.15.6'
```

### 2. Specify hibernate cache settings

Define Redisson Region Cache Factory:

```xml
<!-- Redisson Region Cache factory -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonLocalCachedRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonClusteredRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.RedissonClusteredLocalCachedRegionFactory" />
```

By default each Region Factory creates own Redisson instance. For multiple applications, using the same Redis setup and deployed in the same JVM, amount of Redisson instances could be reduced using JNDI registry:

```xml
<!-- name of Redisson instance registered in JNDI -->
<property name="hibernate.cache.region.jndi_name" value="redisson_instance" />
<!-- JNDI Redisson Region Cache factory -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.JndiRedissonRegionFactory" />
<!-- or -->
<property name="hibernate.cache.region.factory_class" value="org.redisson.hibernate.JndiRedissonLocalCachedRegionFactory" />
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

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].eviction.max_entries` |
|Description| Max size of cache. Superfluous entries in Redis are evicted using LRU algorithm.<br/>`0` value means unbounded cache. |
|Default value| `0` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].expiration.time_to_live` |
|Description| Time to live per cache entry in Redis. Defined in milliseconds.<br/>`0` value means this setting doesn't affect expiration. |
|Default value| `0` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].expiration.max_idle_time` |
|Description| Max idle time per cache entry in Redis. Defined in milliseconds.<br/>`0` value means this setting doesn't affect expiration.  |
|Default value| `0` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].localcache.cache_provider` |
|Description| Cache provider used as local cache store.<br/>`REDISSON` and `CAFFEINE` providers are available.  |
|Default value| `REDISSON` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].localcache.store_mode` |
|Description| Store mode of cache data.<br/>`LOCALCACHE` - store data in local cache only and use Redis only for data update/invalidation<br/>`LOCALCACHE_REDIS` - store data in both Redis and local cache |
|Default value| `LOCALCACHE` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].localcache.max_idle_time` |
|Description| Max idle time per entry in local cache. Defined in milliseconds.<br/>`0` value means this setting doesn't affect expiration |
|Default value| `0` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].localcache.time_to_live` |
|Description| Time to live per entry in local cache. Defined in milliseconds.<br/>`0` value means this setting doesn't affect expiration |
|Default value| `0` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].localcache.eviction_policy` |
|Description| Eviction policy applied to local cache entries when cache size limit reached.<br/>`LFU`, `LRU`, `SOFT`, `WEAK` and `NONE` policies are available. |
|Default value| `NONE` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].localcache.sync_strategy` |
|Description| Sync strategy used to synchronize local cache changes across all instances.<br/>`INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change<br/>`UPDATE` - Update cache entry across all LocalCachedMap instances on map entry change<br/>`NONE` - No synchronizations on map changes |
|Default value| `INVALIDATE` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].localcache.reconnection_strategy` |
|Description| Reconnection strategy used to load missed local cache updates through Hibernate during any connection failures to Redis.<br/>`CLEAR` - Clear local cache if map instance has been disconnected for a while<br/>`LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise<br/>`NONE` - No reconnection handling |
|Default value| `NONE` |

| | |
|-|-|
|Parameter| `hibernate.cache.redisson.[REGION_NAME].localcache.size` |
|Description| Max size of local cache. Superfluous entries in Redis are evicted using defined eviction policy.<br/>`0` value means unbounded cache. |
|Default value| `0` |

_Please note: `hibernate.cache.redisson.[REGION_NAME].localcache.*` settings are available for `RedissonClusteredLocalCachedRegionFactory` and `RedissonLocalCachedRegionFactory` classes only._

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
