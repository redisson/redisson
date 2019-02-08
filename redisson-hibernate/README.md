Redis based Hibernate Cache implementation
===

Implements [Hibernate 2nd level Cache](https://docs.jboss.org/hibernate/orm/5.4/userguide/html_single/Hibernate_User_Guide.html#caching) provider based on Redis.  
Supports all Hibernate cache strategies: `READ_ONLY`, `NONSTRICT_READ_WRITE`, `READ_WRITE` and `TRANSACTIONAL`.  
It's recommended to use FST or Snappy as [codec](https://github.com/redisson/redisson/wiki/4.-data-serialization).

Compatible with Hibernate 4.x, 5.x

Redisson provides various Hibernate Cache factories including those with features below:

**local cache** - so called `near cache`, which is useful for use cases when Hibernate Cache used mostly for read operations and/or network roundtrips are undesirable. It caches Map entries on Redisson side and executes read operations up to **5x faster** in comparison with common implementation.  

**data partitioning** - data partitioning in cluster mode. It allows to scale available memory, read/write operations and entry eviction process for individual Hibernate Cache instance in Redis cluster.  

Below is the list of all available factories with local cache and/or data partitioning support:  

|Class name | Local cache <br/> support | Data partitioning <br/> support |
| ------------- | ------------- | ------------|
|RedissonRegionFactory<br/>&nbsp; | No | No |
|RedissonLocalCachedRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro) edition</i></sub>  | **Yes** | No |
|RedissonClusteredRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro) edition</i></sub> | No | **Yes** |
|RedissonClusteredLocalCachedRegionFactory<br/><sub><i>available only in [Redisson PRO](http://redisson.pro) edition</i></sub> | **Yes** | **Yes** |

Hibernate Cache Usage
===

### 1.  Add `redisson-hibernate` dependency into your project:

1. __For JDK 1.8+__  

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
         <!-- for Hibernate v5.3.x - v5.4.x -->
         <artifactId>redisson-hibernate-53</artifactId>
         <version>3.10.2</version>
     </dependency>
     ```
     Gradle

     ```java
     // for Hibernate v4.x
     compile 'org.redisson:redisson-hibernate-4:3.10.2'
     // for Hibernate v5.0.x - v5.1.x
     compile 'org.redisson:redisson-hibernate-5:3.10.2'
     // for Hibernate v5.2.x
     compile 'org.redisson:redisson-hibernate-52:3.10.2'
     // for Hibernate v5.3.x - v5.4.x
     compile 'org.redisson:redisson-hibernate-53:3.10.2'
     ```  

2. __For JDK 1.6+__  

     Maven
     ```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <!-- for Hibernate v4.x -->
         <artifactId>redisson-hibernate-4</artifactId>
         <!-- for Hibernate v5.0.x - v5.1.x -->
         <artifactId>redisson-hibernate-5</artifactId>
         <version>2.15.2</version>
     </dependency>
     ```
     Gradle

     ```java
     // for Hibernate v4.x
     compile 'org.redisson:redisson-hibernate-4:2.15.2'
     // for Hibernate v5.0.x - v5.1.x
     compile 'org.redisson:redisson-hibernate-5:2.15.2'
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

<!-- Redisson YAML config (located in filesystem or classpath) -->
<property name="hibernate.cache.redisson.config" value="/redisson.yaml" />
<!-- Redisson JSON config (located in filesystem or classpath) -->
<property name="hibernate.cache.redisson.config" value="/redisson.json" />
```

#### Redisson allows to define follow cache settings per entity, collection, naturalid, query and timestamp regions:  

`hibernate.cache.redisson.[REGION_NAME].eviction.max_entries` - max size of cache. Superfluous entries in Redis are evicted using LRU algorithm. `0` value means unbounded cache. Default value: 0  

`hibernate.cache.redisson.[REGION_NAME].expiration.time_to_live` - time to live per cache entry in Redis. Defined in milliseconds. `0` value means this setting doesn't affect expiration. Default value: 0  

`hibernate.cache.redisson.[REGION_NAME].expiration.max_idle_time` - max idle time per cache entry in Redis. Defined in milliseconds. `0` value means this setting doesn't affect expiration. Default value: 0  

`hibernate.cache.redisson.[REGION_NAME].localcache.max_idle_time` - max idle time per entry in local cache. Defined in milliseconds. `0` value means this setting doesn't affect expiration. Default value: 0  

`hibernate.cache.redisson.[REGION_NAME].localcache.time_to_live` - time to live per entry in local cache. Defined in milliseconds. `0` value means this setting doesn't affect expiration. Default value: 0  

`hibernate.cache.redisson.[REGION_NAME].localcache.eviction_policy` - eviction policy applied to local cache entries when cache size limit reached. LFU, LRU, SOFT, WEAK and NONE policies are available. Default value: NONE  

`hibernate.cache.redisson.[REGION_NAME].localcache.sync_strategy` - sync strategy used to synchronize local cache changes across all instances. Follow sync strategies are available:  
INVALIDATE - Default. Invalidate cache entry across all LocalCachedMap instances on map entry change  
UPDATE - Update cache entry across all LocalCachedMap instances on map entry change  
NONE - No synchronizations on map changes  

Default value: INVALIDATE

`hibernate.cache.redisson.[REGION_NAME].localcache.reconnection_strategy` - reconnection strategy used to load missed updates through Hibernate during any connection failures to Redis. Since, local cache updates can't be get in absence of connection to Redis. Follow reconnection strategies are available:  
CLEAR - Clear local cache if map instance has been disconnected for a while.  
LOAD - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise.  
NONE - Default. No reconnection handling  

Default value: NONE  

`hibernate.cache.redisson.[REGION_NAME].localcache.size` - max size of local cache. Superfluous entries in Redis are evicted using defined eviction policy.  `0` value means unbounded cache. Default value: 0

_Please note: `*.localcache.*` settings are available for `RedissonClusteredLocalCachedRegionFactory` and `RedissonLocalCachedRegionFactory` classes only._

#### Default region configuration used for all caches not specified in configuration:

```xml
<!-- cache definition applied to all caches in entity region -->
<property name="hibernate.cache.redisson.entry.eviction.max_entries" value="10000" />
<property name="hibernate.cache.redisson.entry.expiration.time_to_live" value="600000" />
<property name="hibernate.cache.redisson.entry.expiration.max_idle_time" value="300000" />

<property name="hibernate.cache.redisson.entry.localcache.max_idle_time" value="300000" />
<property name="hibernate.cache.redisson.entry.localcache.time_to_live" value="300000" />
<property name="hibernate.cache.redisson.entry.localcache.eviction_policy" value="LRU" />
<property name="hibernate.cache.redisson.entry.localcache.sync_strategy" value="INVALIDATE" />
<property name="hibernate.cache.redisson.entry.localcache.reconnection_strategy" value="CLEAR" />
<property name="hibernate.cache.redisson.entry.localcache.size" value="5000" />

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
