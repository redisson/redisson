{% include 'cache/Spring-cache.md' %}

## Hibernate Cache

Redisson implements [Hibernate 2nd level Cache](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#caching) provider based on Redis.  
All Hibernate cache strategies are supported: `READ_ONLY`, `NONSTRICT_READ_WRITE`, `READ_WRITE` and `TRANSACTIONAL`.  

Compatible with Hibernate 4.x, 5.1.x, 5.2.x, 5.3.3+ up to 5.6.x and 6.0.2+ up to 7.x.x

### Eviction, local cache and data partitioning

Redisson provides various Hibernate Cache factories including those with features below:

1. **Local cache**

    So called `near cache`, which is useful for use cases when Hibernate Cache used mostly for read operations and/or network roundtrips are undesirable. It caches Map entries on Redisson side and executes read operations up to **5x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

2. **Data partitioning**

    Although all implementations are cluster compatible thier content isn't scaled/partitioned across multiple Redis or Valkey master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Hibernate Cache instance in Redis or Valkey cluster.  

3. **Scripted eviction**

    Allows to define `time to live` or `max idle time` parameters per map entry. Eviction is executed by Redisson through a custom scheduled task which removes expired entries using Lua script on Redis or Valkey side. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique map object name. 

    Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](configuration.md) and [maxCleanUpDelay](configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

    Available implementations:

    |Class name | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |RedissonRegionFactory<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
    |RedissonRegionFactory<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
    |RedissonLocalCachedRegionFactory<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ❌ | ✔️ |
    |RedissonClusteredRegionFactory<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |RedissonClusteredLocalCachedRegionFactory<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |

4. **Advanced eviction**

    Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned by Valkey or Redis.

    Available implementations:

    |Class name | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |RedissonRegionV2Factory<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ❌ | ✔️ | ✔️ |
    |RedissonLocalCachedV2RegionFactory<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ✔️ | ✔️ |

5. **Native eviction**

    Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned by Valkey or Redis.  
    Requires **Valkey 9.0+** or **Redis 7.4+**.

    Available implementations:

    |Class name | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |RedissonRegionNativeFactory<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
    |RedissonRegionNativeFactory<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
    |RedissonLocalCachedNativeRegionFactory<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ❌ | ✔️ |
    |RedissonClusteredNativeRegionFactory<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |RedissonClusteredLocalCachedNativeRegionFactory<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |

### Usage

1. **Add `redisson-hibernate` dependency into your project:**

    <div class="grid cards" markdown>

    -   **Redisson PRO**

        Maven

        ```xml
        <dependency>
            <groupId>pro.redisson</groupId>
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
            <!-- for Hibernate v7.x.x -->
            <artifactId>redisson-hibernate-7</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Hibernate v4.x
        compile 'pro.redisson:redisson-hibernate-4:xVERSIONx'
        // for Hibernate v5.0.x - v5.1.x
        compile 'pro.redisson:redisson-hibernate-5:xVERSIONx'
        // for Hibernate v5.2.x
        compile 'pro.redisson:redisson-hibernate-52:xVERSIONx'
        // for Hibernate v5.3.3+ - v5.6.x
        compile 'pro.redisson:redisson-hibernate-53:xVERSIONx'
        // for Hibernate v6.0.2+ - v6.x.x
        compile 'pro.redisson:redisson-hibernate-6:xVERSIONx'
        // for Hibernate v7.x.x
        compile 'pro.redisson:redisson-hibernate-7:xVERSIONx'
        ```

    -   **Community Edition**

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
            <!-- for Hibernate v7.x.x -->
            <artifactId>redisson-hibernate-7</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Hibernate v4.x
        compile 'org.redisson:redisson-hibernate-4:xVERSIONx'
        // for Hibernate v5.0.x - v5.1.x
        compile 'org.redisson:redisson-hibernate-5:xVERSIONx'
        // for Hibernate v5.2.x
        compile 'org.redisson:redisson-hibernate-52:xVERSIONx'
        // for Hibernate v5.3.3+ - v5.6.x
        compile 'org.redisson:redisson-hibernate-53:xVERSIONx'
        // for Hibernate v6.0.2+ - v6.x.x
        compile 'org.redisson:redisson-hibernate-6:xVERSIONx'
        // for Hibernate v7.x.x
        compile 'org.redisson:redisson-hibernate-7:xVERSIONx'
        ```

    </div>

    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)


2. **Specify hibernate cache settings**

    ```xml
    <!-- 2nd level cache activation -->
    <property name="hibernate.cache.use_second_level_cache" value="true" />
    <property name="hibernate.cache.use_query_cache" value="true" />

    <!-- Redisson can fallback on database if Redis or Valkey cache is unavailable -->
    <property name="hibernate.cache.redisson.fallback" value="true" />

    <!-- Redisson YAML config (located in filesystem or classpath) -->
    <property name="hibernate.cache.redisson.config" value="/redisson.yaml" />
    ```

    Define Redisson Region Cache Factory in `hibernate.cfg.xml` file:

    ```xml
    <!-- Scripted Eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.RedissonRegionFactory" />

    <!-- Native Eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.RedissonRegionNativeFactory" />

    <!-- Data partitioning + Advanced eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.RedissonRegionV2Factory" />

    <!-- Local cache + Scripted eviction -->
    <property name="hibernate.cache.region.factory_class"
              value="org.redisson.hibernate.RedissonLocalCachedRegionFactory" />

    <!-- Local cache + Data partitioning + Advanced eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.RedissonLocalCachedV2RegionFactory" />

    <!-- Local cache + Native Eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.RedissonLocalCachedNativeRegionFactory" />

    <!-- Data partitioning + Scripted eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.RedissonClusteredRegionFactory" />

    <!-- Data partitioning + Native eviction -->
    <property name="hibernate.cache.region.factory_class"
              value="org.redisson.hibernate.RedissonClusteredNativeRegionFactory" />

    <!-- Local cache + Data partitioning + Scripted eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.RedissonClusteredLocalCachedRegionFactory" />

    <!-- Local cache + Data partitioning + Native eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.RedissonClusteredLocalCachedNativeRegionFactory" />
    ```

    By default each Region Factory creates own Redisson instance. For multiple applications, using the same Redis or Valkey setup and deployed in the same JVM, amount of Redisson instances could be reduced using JNDI registry:

    ```xml
    <!-- name of Redisson instance registered in JNDI -->
    <property name="hibernate.cache.redisson.jndi_name" value="redisson_instance" />


    <!-- Scripted Eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonRegionFactory" />

    <!-- Native Eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonRegionNativeFactory" />

    <!-- Data partitioning + Advanced eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonRegionV2Factory" />

    <!-- Local cache + Scripted eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonLocalCachedRegionFactory" />

    <!-- Local cache + Data partitioning + Advanced eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonLocalCachedV2RegionFactory" />

    <!-- Local cache + Native Eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonLocalCachedNativeRegionFactory" />

    <!-- Data partitioning + Scripted eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonClusteredRegionFactory" />

    <!-- Data partitioning + Native eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonClusteredNativeRegionFactory" />

    <!-- Local cache + Data partitioning + Scripted eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonClusteredLocalCachedRegionFactory" />

    <!-- Local cache + Data partitioning + Native eviction -->
    <property name="hibernate.cache.region.factory_class" 
              value="org.redisson.hibernate.JndiRedissonClusteredLocalCachedNativeRegionFactory" />
    ```

**Cache settings**

Redisson allows to define follow cache settings per entity, collection, naturalid, query and timestamp regions:

**REGION_NAME** - is a name of region which is defined in @Cache annotation otherwise it's a fully qualified class name.

Property name: **hibernate.cache.redisson.[REGION_NAME].eviction.max_entries**  
Description:  Max size of cache. Superfluous entries in Redis or Valkey are evicted using LRU algorithm. `0` value means unbounded cache.  
Default value: `0`  

Property name: **hibernate.cache.redisson.[REGION_NAME].expiration.time_to_live**  
Description: Time to live per cache entry in Redis. Defined in milliseconds. `0` value means this setting doesn't affect expiration.  
Default value: `0`  

Property name: **hibernate.cache.redisson.[REGION_NAME].expiration.max_idle_time**  
Description: Max idle time per cache entry in Redis. Defined in milliseconds.
             `0` value means this setting doesn't affect expiration.  
Default value: `0`  

Property name: **hibernate.cache.redisson.[REGION_NAME].localcache.cache_provider**  
Description: Cache provider used as local cache store. `REDISSON` and `CAFFEINE` providers are available.  
Default value: `REDISSON`  

Property name: **hibernate.cache.redisson.[REGION_NAME].localcache.store_mode**  
Description: Store mode of cache data. Available values:

* `LOCALCACHE` - store data in local cache only and use Redis or Valkey only for data update/invalidation. 
* `LOCALCACHE_REDIS` - store data in both Redis or  Valkey and local cache.  

Default value: `LOCALCACHE`  

Property name: **hibernate.cache.redisson.[REGION_NAME].localcache.max_idle_time**  
Description: Max idle time per entry in local cache. Defined in milliseconds. `0` value means this setting doesn't affect expiration  
Default value: `0`  

Property name: **hibernate.cache.redisson.[REGION_NAME].localcache.time_to_live**  
Description: Time to live per entry in local cache. Defined in milliseconds. `0` value means this setting doesn't affect expiration  
Default value: `0`  

Property name: **hibernate.cache.redisson.[REGION_NAME].localcache.eviction_policy**  
Description: Eviction policy applied to local cache entries when cache size limit reached. `LFU`, `LRU`, `SOFT`, `WEAK` and `NONE` policies are available.  
Default value: `NONE`  

Property name: **hibernate.cache.redisson.[REGION_NAME].localcache.sync_strategy**  
Description: Sync strategy used to synchronize local cache changes across all instances. Available values:

* `INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change
* `UPDATE` - Update cache entry across all LocalCachedMap instances on map entry change
* `NONE` - No synchronizations on map changes

Default value: `INVALIDATE`  

Property name: **hibernate.cache.redisson.[REGION_NAME].localcache.reconnection_strategy**  
Description: Reconnection strategy is used to load missed local cache updates through Hibernate during any connection failures to Redis. Available values:

* `CLEAR` - Clear local cache if map instance has been disconnected for a while
* `LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise
* `NONE` - No reconnection handling  

Default value: `NONE`  

Property name: **hibernate.cache.redisson.[REGION_NAME].localcache.size**  
Description: Max size of local cache. Superfluous entries in Redis or Valkey are evicted using defined eviction policy. `0` value means unbounded cache.  
Default value: `0`  

!!! note
    `hibernate.cache.redisson.[REGION_NAME].localcache.*` settings are available for `RedissonClusteredLocalCachedRegionFactory`, `RedissonLocalCachedV2RegionFactory` and `RedissonLocalCachedRegionFactory` classes only.

**Default cache settings**

Default region configuration used for all caches not specified in configuration:

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

### Overriding the default configuration

Configuration per entity/collection/naturalid/query region overrides default configuration:

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

## JCache API (JSR-107)
Redisson provides an implementation of JCache API ([JSR-107](https://www.jcp.org/en/jsr/detail?id=107)) for Redis.

Provided implementation fully passes TCK tests. Here is the [test](https://github.com/cruftex/jsr107-test-zoo/tree/master/redisson-V2-test) module.

### Eviction, local cache and data partitioning

Redisson provides JCache implementations with many important features:  

1. **Local cache**
 
    So called near cache used to speed up read operations and avoid network roundtrips. It caches JCache entries on Redisson side and executes read operations up to 45x faster in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use hashCode()/equals() methods of key object, instead it uses hash of serialized state.  

2. **Data partitioning**
 
    Although JCache instance is cluster compatible its content isn't scaled/partitioned across multiple Redis or Valkey master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual JCache instance in Redis or Valkey cluster.

3. **Fallback mode**

    If set to `true` and Redis or Valkey is down the errors won't be thrown allowing application continue to operate without Redis.

4. **Scripted eviction**

    Eviction is executed by Redisson through a custom scheduled task which removes expired entries using Lua script on Redis or Valkey side. Eviction task is started once per unique cache name at the moment of getting JCache instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique cache name. 

    Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](configuration.md) and [maxCleanUpDelay](configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

    Available implementations:

    |   | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write | Fallback<br/>mode |
    | -- | :-------------------: | :----------:| :----------:| :----------:|
    |JCache<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ | ❌ |
    |JCache<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ | ✔️ |
    |JCache with local cache<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ❌ | ✔️ | ✔️ |
    |JCache with data partitioning<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ | ✔️ |
    |JCache with local cache and data partitioning<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ | ✔️ |

5. **Advanced eviction**

    Doesn't use an entry eviction task, entries are cleaned by Valkey or Redis.  

    Available implementations:

    | | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write | Fallback<br/>mode |
    | ------------- | :-----------: | :----------:| :----------:| :----------:|
    |JCache V2<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ❌ | ✔️ | ✔️ | ✔️ |
    |JCache V2 with local cache<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ✔️ | ✔️ | ✔️ |

6. **Native eviction**

    Doesn't use an entry eviction task, entries are cleaned by Valkey or Redis.  
    Requires **Valkey 9.0+** or **Redis 7.4+**.

    Available implementations:

    | | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write | Fallback<br/>mode |
    | ------------- | :-----------: | :----------:| :----------:| :----------:|
    |JCache native <br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ❌ | ❌ | ✔️ | ✔️ |
    |JCache native with data partitioning<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ | ✔️ |
    |JCache native with local cache and data partitioning<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ✔️ | ✔️ | ✔️ |

### Usage

Below are the examples of JCache API usage.

1. Using default config located at `/redisson-jcache.yaml`:

    ```java
    // implementation with scripted eviction
    MutableConfiguration<String, String> config = new MutableConfiguration<>();

    // implementation with native eviction
    NativeConfiguration<String, String> config = new NativeConfiguration<>();

    // implementation with advanced eviction
    V2Configuration<String, String> config = new V2Configuration<>();

            
    CacheManager manager = Caching.getCachingProvider().getCacheManager();
    Cache<String, String> cache = manager.createCache("namedCache", config);
    ```

2. Using config file with custom location:

    ```java
    // implementation with scripted eviction
    MutableConfiguration<String, String> config = new MutableConfiguration<>();

    // implementation with native eviction
    NativeConfiguration<String, String> config = new NativeConfiguration<>();

    // implementation with advanced eviction
    V2Configuration<String, String> config = new V2Configuration<>();


    // yaml config
    URI redissonConfigUri = getClass().getResource("redisson-jcache.yaml").toURI();
    CacheManager manager = Caching.getCachingProvider().getCacheManager(redissonConfigUri, null);
    Cache<String, String> cache = manager.createCache("namedCache", config);
    ```

3. Using Redisson's config object:

    ```java
    // implementation with scripted eviction
    MutableConfiguration<String, String> jcacheConfig = new MutableConfiguration<>();

    // implementation with native eviction
    NativeConfiguration<String, String> jcacheConfig = new NativeConfiguration<>();

    // implementation with advanced eviction
    V2Configuration<String, String> jcacheConfig = new V2Configuration<>();


    Config redissonCfg = ...
    Configuration<String, String> config = RedissonConfiguration.fromConfig(redissonCfg, jcacheConfig);

    CacheManager manager = Caching.getCachingProvider().getCacheManager();
    Cache<String, String> cache = manager.createCache("namedCache", config);
    ```

4. Using Redisson instance object:
    ```java
    // implementation with scripted eviction
    MutableConfiguration<String, String> jcacheConfig = new MutableConfiguration<>();

    // implementation with native eviction
    NativeConfiguration<String, String> jcacheConfig = new NativeConfiguration<>();

    // implementation with advanced eviction
    V2Configuration<String, String> jcacheConfig = new V2Configuration<>();


    RedissonClient redisson = ...
    Configuration<String, String> config = RedissonConfiguration.fromInstance(redisson, jcacheConfig);

    CacheManager manager = Caching.getCachingProvider().getCacheManager();
    Cache<String, String> cache = manager.createCache("namedCache", config);
    ```

    Read more [here](configuration.md) about Redisson configuration.

**Local cache configuration**

```java
      LocalCacheConfiguration<String, String> configuration = new LocalCacheConfiguration<>()

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
      // SOFT - Uses weak references, entries are removed by GC
      // WEAK - Uses soft references, entries are removed by GC
      // NONE - No eviction
     .evictionPolicy(EvictionPolicy.NONE)

      // If cache size is 0 then local cache is unbounded.
     .cacheSize(1000)

      // Used to load missed updates during any connection failures to Redis. 
      // Since, local cache updates can't be get in absence of connection to Redis. 
      // Follow reconnection strategies are available:
      // CLEAR - Clear local cache if map instance has been disconnected for a while.
      // LOAD - Store invalidated entry hash in invalidation log for 10 minutes
      //        Cache keys for stored invalidated entry hashes will be removed 
      //        if LocalCachedMap instance has been disconnected less than 10 minutes
      //        or whole cache will be cleaned otherwise.
      // NONE - Default. No reconnection handling
     .reconnectionStrategy(ReconnectionStrategy.NONE)

      // Used to synchronize local cache changes.
      // Follow sync strategies are available:
      // INVALIDATE - Default. Invalidate cache entry across all LocalCachedMap instances on map entry change
      // UPDATE - Insert/update cache entry across all LocalCachedMap instances on map entry change
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

Usage example:

```java

// implementation with local cache + scripted eviction
LocalCacheConfiguration<String, String> config = new LocalCacheConfiguration<>();
                .setEvictionPolicy(EvictionPolicy.LFU)
                .setTimeToLive(48, TimeUnit.MINUTES)
                .setMaxIdle(24, TimeUnit.MINUTES)
                .setCacheSize(1000);

// implementation with local cache + native eviction
LocalCacheNativeConfiguration<String, String> config = new LocalCacheNativeConfiguration<>();
                .setEvictionPolicy(EvictionPolicy.LFU)
                .setTimeToLive(48, TimeUnit.MINUTES)
                .setMaxIdle(24, TimeUnit.MINUTES)
                .setCacheSize(1000);

// implementation with local cache + advanced eviction        
LocalCacheV2Configuration<String, String> config = new LocalCacheV2Configuration<>();
                .setEvictionPolicy(EvictionPolicy.LFU)
                .setTimeToLive(48, TimeUnit.MINUTES)
                .setMaxIdle(24, TimeUnit.MINUTES)
                .setCacheSize(1000);
        
CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("myCache", config);

// or

URI redissonConfigUri = getClass().getResource("redisson-jcache.yaml").toURI();
CacheManager manager = Caching.getCachingProvider().getCacheManager(redissonConfigUri, null);
Cache<String, String> cache = manager.createCache("myCache", config);

// or 

Config redissonCfg = ...
Configuration<String, String> rConfig = RedissonConfiguration.fromConfig(redissonCfg, config);

CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("namedCache", rConfig);
```

**Data partitioning**

Usage examples:

```java
// implementation with data partitioning + scripted eviction
ClusteredConfiguration<String, String> config = new ClusteredConfiguration<>();

// implementation with data partitioning + native eviction
ClusteredNativeConfiguration<String, String> config = new ClusteredNativeConfiguration<>();

// implementation with data partitioning + advanced eviction
V2Configuration<String, String> config = new V2Configuration<>();
        
CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("myCache", config);

// or

URI redissonConfigUri = getClass().getResource("redisson-jcache.yaml").toURI();
CacheManager manager = Caching.getCachingProvider().getCacheManager(redissonConfigUri, null);
Cache<String, String> cache = manager.createCache("myCache", config);

// or 

Config redissonCfg = ...
Configuration<String, String> rConfig = RedissonConfiguration.fromConfig(redissonCfg, config);

CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("namedCache", rConfig);
```

**Local cache with data partitioning configuration**

```java
      ClusteredLocalCacheConfiguration<String, String> configuration = new ClusteredLocalCacheConfiguration<>()

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
      // SOFT - Uses weak references, entries are removed by GC
      // WEAK - Uses soft references, entries are removed by GC
      // NONE - No eviction
     .evictionPolicy(EvictionPolicy.NONE)

      // If cache size is 0 then local cache is unbounded.
     .cacheSize(1000)

      // Used to load missed updates during any connection failures to Redis. 
      // Since, local cache updates can't be get in absence of connection to Redis. 
      // Follow reconnection strategies are available:
      // CLEAR - Clear local cache if map instance has been disconnected for a while.
      // LOAD - Store invalidated entry hash in invalidation log for 10 minutes
      //        Cache keys for stored invalidated entry hashes will be removed 
      //        if LocalCachedMap instance has been disconnected less than 10 minutes
      //        or whole cache will be cleaned otherwise.
      // NONE - Default. No reconnection handling
     .reconnectionStrategy(ReconnectionStrategy.NONE)

      // Used to synchronize local cache changes.
      // Follow sync strategies are available:
      // INVALIDATE - Default. Invalidate cache entry across all LocalCachedMap instances on map entry change
      // UPDATE - Insert/update cache entry across all LocalCachedMap instances on map entry change
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

Usage examples:

```java

// implementation with data partitioning + local cache + scripted eviction
ClusteredLocalCacheConfiguration<String, String> config = new ClusteredLocalCacheConfiguration<>();
config.cacheSize(1000);
config.timeToLive(Duration.ofSeconds(10));

// implementation with data partitioning + local cache + native eviction
ClusteredLocalCacheNativeConfiguration<String, String> config = new ClusteredLocalCacheNativeConfiguration<>();
config.cacheSize(1000);
config.timeToLive(Duration.ofSeconds(10));

// implementation with data partitioning + local cache + advanced eviction
LocalCacheV2Configuration<String, String> config = new LocalCacheV2Configuration<>();
config.cacheSize(1000);
config.timeToLive(Duration.ofSeconds(10));
        
CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("myCache", config);

// or

URI redissonConfigUri = getClass().getResource("redisson-jcache.yaml").toURI();
CacheManager manager = Caching.getCachingProvider().getCacheManager(redissonConfigUri, null);
Cache<String, String> cache = manager.createCache("myCache", config);

// or 

Config redissonCfg = ...
Configuration<String, String> rConfig = RedissonConfiguration.fromConfig(redissonCfg, config);

CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("namedCache", rConfig);
```

### Asynchronous, Reactive and RxJava3 interfaces

Along with usual JCache API, Redisson provides Asynchronous, Reactive and RxJava3 API.

**[Asynchronous interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/CacheAsync.html)**. Each method returns `org.redisson.api.RFuture` object.  
Example:

```java
// implementation with scripted eviction
MutableConfiguration<String, String> config = new MutableConfiguration<>();

// implementation with native eviction
NativeConfiguration<String, String> config = new NativeConfiguration<>();

// implementation with advanced eviction
V2Configuration<String, String> config = new V2Configuration<>();

        
CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("myCache", config);

CacheAsync<String, String> asyncCache = cache.unwrap(CacheAsync.class);
RFuture<Void> putFuture = asyncCache.putAsync("1", "2");
RFuture<String> getFuture = asyncCache.getAsync("1");
```

**[Reactive interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/CacheReactive.html)**. Each method returns `reactor.core.publisher.Mono` object.  
Example:

```java
// implementation with scripted eviction
MutableConfiguration<String, String> config = new MutableConfiguration<>();

// implementation with native eviction
NativeConfiguration<String, String> config = new NativeConfiguration<>();

// implementation with advanced eviction
V2Configuration<String, String> config = new V2Configuration<>();

        
CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("myCache", config);

CacheReactive<String, String> reactiveCache = cache.unwrap(CacheReactive.class);
Mono<Void> putFuture = reactiveCache.put("1", "2");
Mono<String> getFuture = reactiveCache.get("1");
```

**[RxJava3 interface](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/CacheRx.html)**. Each method returns one of the following object: `io.reactivex.Completable`, `io.reactivex.Single`, `io.reactivex.Maybe`.  
Example:

```java
// implementation with scripted eviction
MutableConfiguration<String, String> config = new MutableConfiguration<>();

// implementation with native eviction
NativeConfiguration<String, String> config = new NativeConfiguration<>();

// implementation with advanced eviction
V2Configuration<String, String> config = new V2Configuration<>();

        
CacheManager manager = Caching.getCachingProvider().getCacheManager();
Cache<String, String> cache = manager.createCache("myCache", config);

CacheRx<String, String> rxCache = cache.unwrap(CacheRx.class);
Completable putFuture = rxCache.put("1", "2");
Maybe<String> getFuture = rxCache.get("1");
```

### Open Liberty or WebSphere Liberty integration

Distributed Cache configuration example:

```xml
<library id="jCacheVendorLib">
    <file name="${shared.resource.dir}/redisson-all-3.43.0.jar"/>
</library>

<cache id="io.openliberty.cache.authentication" name="io.openliberty.cache.authentication"
    cacheManagerRef="CacheManager" />

<cacheManager id="CacheManager" uri="file:${server.config.dir}/redisson-jcache.yaml"> 
    <properties fallback="true"/>
    <cachingProvider jCacheLibraryRef="jCacheVendorLib"/>
</cacheManager>
```

Distributed Session persistence configuration example:

```xml
<featureManager>
    <feature>servlet-6.0</feature>
    <feature>sessionCache-1.0</feature>
</featureManager>

<httpEndpoint httpPort="${http.port}" httpsPort="${https.port}"
        id="defaultHttpEndpoint" host="*" />

<library id="jCacheVendorLib">
    <file name="${shared.resource.dir}/redisson-all-3.43.0.jar"/>
</library>

<httpSessionCache cacheManagerRef="CacheManager"/>

<cacheManager id="CacheManager" uri="file:${server.config.dir}/redisson-jcache.yaml"> 
    <properties fallback="true"/>
    <cachingProvider jCacheLibraryRef="jCacheVendorLib"/>
</cacheManager>
```

**JCache settings**

_Settings are available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

* **fallback**

    Default value: `false`  
    Description: Skip errors if Redis or Valkey cache is unavailable.  


* **implementation**

    Default value: `cache`  
    Description: Cache implementation. Available values:

    * `cache` - implementation with scripted eviction
    * `local-cache` - implementation with local cache and scripted eviction
    * `clustered-cache` - implementation with data partitioning and scripted eviction
    * `clustered-local-cache` - implementation with data partitioning, local cache support and scripted eviction

    * `cache-v2` - implementation with data partitioning and advanced eviction
    * `local-cache-v2` - implementation with local cache, data partitioning and advanced eviction

    * `native-cache` - implementation with native eviction
    * `local-cache-native` - implementation with local cache and native eviction
    * `clustered-native-cache` - implementation with data partitioning and native eviction
    * `clustered-local-cache-native` - implementation with data partitioning, local cache support and native eviction


* **localcache.store_cache_miss**

    Default value: `false`  
    Description: Defines whether to store a cache miss into the local cache.  

* **localcache.cache_provider**

    Default value: `REDISSON`  
    Description: Defines a cache provider used as local cache store. Available values:

    * `REDISSON`
    * `CAFFEINE` 

* **localcache.store_mode**

    Default value: `LOCALCACHE`  
    Description: Store mode of cache data. Available values:

    * `LOCALCACHE` - store data in local cache only and use Redis or Valkey only for data update/invalidation
    * `LOCALCACHE_REDIS` - store data in both Redis or Valkey and local cache

* **localcache.max_idle_time**

    Default value: `0`  
    Description: Max idle time per entry in local cache. Defined in milliseconds. `0` value means this setting doesn't affect expiration.

* **localcache.time_to_live**

    Default value: `0`  
    Description: Time to live per entry in local cache. Defined in milliseconds. `0` value means this setting doesn't affect expiration.

* **localcache.eviction_policy**

    Default value: `NONE`  
    Description: Eviction policy applied to local cache entries when cache size limit reached. Available values:

    * `LFU` - Counts how often an item was requested. Those that are used least often are discarded first.
    * `LRU` - Discards the least recently used items first
    * `SOFT` - Uses weak references, entries are removed by GC
    * `WEAK` - Uses soft references, entries are removed by GC
    * `NONE` - No eviction

* **localcache.sync_strategy**

    Default value: `INVALIDATE`  
    Description: Sync strategy used to synchronize local cache changes across all instances. Available values:

    * `INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change
    * `UPDATE` - Update cache entry across all LocalCachedMap instances on map entry change
    * `NONE` - No synchronizations on map changes |

* **localcache.reconnection_strategy**

    Default value: `NONE`  
    Description: Reconnection strategy used to load missed local cache updates through Hibernate during any connection failures to Redis.

    * `CLEAR` - Clear local cache if map instance has been disconnected for a while
    * `LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise
    * `NONE` - No reconnection handling

* **localcache.size**

    Default value: `0`  
    Description: Max size of local cache. Superfluous entries in Redis or Valkey are evicted using defined eviction policy. `0` value means unbounded cache.

## MyBatis Cache

Redisson implements [MyBatis Cache](https://mybatis.org/mybatis-3/sqlmap-xml.html#cache) based on Redis.  

Compatible with MyBatis 3.0.0+

### Eviction, local cache and data partitioning

Redisson provides multiple MyBatis Cache implementations which support features below:

1. **Local cache**

    So called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

2. **Data partitioning**
    
    Although Map object is cluster compatible its content isn't scaled/partitioned across multiple Redis or Valkey master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in Redis or Valkey cluster.  

3. **Scripted eviction**

    Allows to define `time to live` or `max idle time` parameters per entry. Eviction is executed by Redisson through a custom scheduled task which removes expired entries using Lua script on Redis or Valkey side. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique map object name. 

    Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](configuration.md) and [maxCleanUpDelay](configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

    Available implementations:

    |Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |RedissonCache<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
    |RedissonCache<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
    |RedissonLocalCachedCache<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ❌ |  ✔️ |
    |RedissonClusteredCache<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ |  ✔️ |
    |RedissonClusteredLocalCachedCache<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |

3. **Advanced eviction**

    Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned by Valkey or Redis.  

    Available implementations:

    |Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |RedissonCacheV2<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |RedissonLocalCachedCacheV2<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ✔️ | ✔️ |

4. **Native eviction**

    Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned by Valkey or Redis.  
    Requires **Valkey 9.0+** or **Redis 7.4+**.

    Available implementations:

    |Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |RedissonCacheNative<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
    |RedissonCacheNative<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ |  ✔️ |
    |RedissonLocalCachedCacheNative<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ❌ | ✔️ |
    |RedissonClusteredCacheNative<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ |  ✔️ |
    |RedissonClusteredLocalCachedCacheNative<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ |  ✔️ |

### Usage

1. **Add `redisson-mybatis` dependency into your project**

    <div class="grid cards" markdown>

    -   **Redisson PRO**

        Maven

        ```xml  
        <dependency>
             <groupId>pro.redisson</groupId>
             <artifactId>redisson-mybatis</artifactId>
             <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        compile 'pro.redisson:redisson-mybatis:xVERSIONx'
        ```

    -   **Community Edition**

        Maven

        ```xml  
        <dependency>
             <groupId>org.redisson</groupId>
             <artifactId>redisson-mybatis</artifactId>
             <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        compile 'org.redisson:redisson-mybatis:xVERSIONx'
        ```

    </div>

    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)


2. **Specify MyBatis cache settings**

    Redisson allows to define follow settings per Cache instance:

    * `timeToLive`

        Defines time to live per cache entry

    * `maxIdleTime`

        Defines max idle time per cache entry

    * `maxSize`

        Defines max size of entries amount stored in Redis or Valkey

    * `localCacheProvider`

        Cache provider used as local cache store. `REDISSON` and `CAFFEINE` providers are available. Default value: `REDISSON`

    * `localCacheEvictionPolicy`

        Local cache eviction policy. `LFU`, `LRU`, `SOFT`, `WEAK` and `NONE` eviction policies are available.

    * `localCacheSize`

        Local cache size. If size is `0` then local cache is unbounded.

    * `localCacheTimeToLive`
 
        Time to live in milliseconds for each map entry in local cache. If value equals to `0` then timeout is not applied.

    * `localCacheMaxIdleTime`

        Max idle time in milliseconds for each map entry in local cache. If value equals to `0` then timeout is not applied.

    * `localCacheSyncStrategy`
        
        Local cache sync strategy. `INVALIDATE`, `UPDATE` and `NONE` eviction policies are available.

    * `redissonConfig`
        
        Defines path to redisson config in YAML format

Cache definition examples:

```xml
<cache type="org.redisson.mybatis.RedissonCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<cache type="org.redisson.mybatis.RedissonCacheNative">
  <property name="timeToLive" value="200000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<cache type="org.redisson.mybatis.RedissonCacheV2">
  <property name="timeToLive" value="200000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<cache type="org.redisson.mybatis.RedissonLocalCachedCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>

  <property name="localCacheEvictionPolicy" value="LRU"/>
  <property name="localCacheSize" value="1000"/>
  <property name="localCacheTimeToLive" value="2000000"/>
  <property name="localCacheMaxIdleTime" value="1000000"/>
  <property name="localCacheSyncStrategy" value="INVALIDATE"/>
     
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<cache type="org.redisson.mybatis.RedissonLocalCachedCacheV2">
  <property name="timeToLive" value="200000"/>

  <property name="localCacheEvictionPolicy" value="LRU"/>
  <property name="localCacheSize" value="1000"/>
  <property name="localCacheTimeToLive" value="2000000"/>
  <property name="localCacheMaxIdleTime" value="1000000"/>
  <property name="localCacheSyncStrategy" value="INVALIDATE"/>
     
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<cache type="org.redisson.mybatis.RedissonClusteredCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<cache type="org.redisson.mybatis.RedissonClusteredLocalCachedCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
     
  <property name="localCacheEvictionPolicy" value="LRU"/>
  <property name="localCacheSize" value="1000"/>
  <property name="localCacheTimeToLive" value="2000000"/>
  <property name="localCacheMaxIdleTime" value="1000000"/>
  <property name="localCacheSyncStrategy" value="INVALIDATE"/>
     
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>
```

## Quarkus Cache

### Eviction, local cache and data partitioning

Redisson provides various [Quarkus Cache](https://quarkus.io/guides/cache) implementations with features below:  

1. **Local cache**

    So called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

2. **Data partitioning**

    Although Map object is cluster compatible its content isn't scaled/partitioned across multiple Redis or Valkey master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in cluster.  

3. **Scripted eviction**

    Allows to define `time to live` or `max idle time` parameters per map entry. Eviction is executed by Redisson through a custom scheduled task which removes expired entries using Lua script on Redis or Valkey side. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique map object name. 

    Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

    Available implementations:

    |`impementation`<br/>setting value | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :-----------:| :----------:|
    |`standard`<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
    |`standard`<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
    |`localcache`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ✔️ |
    |`clustered`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |`clustered_localcache`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |

4. **Advanced eviction**

    Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned by Valkey or Redis.  

    Available implementations:

    |`impementation`<br/>setting value | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :-----------:| :----------:|
    |`v2`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |`localcache_v2`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |

5. **Native eviction**

    Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned by Valkey or Redis.  
    Requires **Valkey 9.0+** or **Redis 7.4+**.

    Available implementations:

    |`impementation`<br/>setting value | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :-----------:| :----------:|
    |`native`<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
    |`native`<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
    |`localcache_native`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ❌ | ✔️ |
    |`clustered_native`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |`clustered_localcache_native`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |


### Usage

1. **Add `redisson-quarkus-cache` dependency into your project**

    <div class="grid cards" markdown>

    -   **Redisson PRO**

        Maven

        ```xml  
        <dependency>
            <groupId>pro.redisson</groupId>
            <!-- for Quarkus v3.x.x -->
            <artifactId>redisson-quarkus-30-cache</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Quarkus v3.x.x
        compile 'pro.redisson:redisson-quarkus-30-cache:xVERSIONx'
        ```

    -   **Community Edition**

        Maven

        ```xml  
        <dependency>
            <groupId>org.redisson</groupId>
            <!-- for Quarkus v3.x.x -->
            <artifactId>redisson-quarkus-30-cache</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Quarkus v3.x.x
        compile 'org.redisson:redisson-quarkus-30-cache:xVERSIONx'
        ```

    </div>

    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)

2. **Add settings into `application.properties` file**

    1. Basic settings

        * `max-size` 

            Setting defines maximum size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded. Default value is `0`.  

        * `expire-after-write` 

            Setting defines time to live of the item stored in the cache. Default value is `0`.  

        * `expire-after-access`

            Setting defines time to live added to the item after read operation. Default value is `0`.  

        * `implementation` 

            Setting defines the type of cache used. Default value is `standard`. Availabile values:  
           
            * `standard` - implementation with scripted eviction
            * `v2` - implementation with data partitioning and advanced eviction
            * `native` - implementation with native eviction
            * `clustered_native` - implementation with data partitioning and native eviction
            * `localcache` - implementation with local cache and scripted eviction
            * `localcache_v2` - implementation with local cache, data partitioning and advanced eviction
            * `localcache_native` - implementation with local cache and native eviction
            * `clustered_localcache` - implementation with data partitioning, local cache and scripted eviction
            * `clustered` - implementation with data partitioning and scripted eviction
            * `clustered_localcache_native` - implementation with data partitioning, local cache and native eviction

        Below is the cache configuration example.

        ```
        quarkus.cache.type=redisson
        quarkus.cache.redisson.implementation=standard

        # Default configuration for all caches
        quarkus.cache.redisson.max-size=10000
        quarkus.cache.redisson.expire-after-write=5s
        quarkus.cache.redisson.expire-after-access=1s

        # Configuration for `sampleCache` cache
        quarkus.cache.redisson.sampleCache.max-size=1000
        quarkus.cache.redisson.sampleCache.expire-after-write=100s
        quarkus.cache.redisson.sampleCache.expire-after-access=10s
        ```

    2. Local cache settings

        * `quarkus.cache.redisson.[CACHE_NAME].max-size`

            Max size of this cache. Superfluous elements are evicted using LRU algorithm. If 0 the cache is unbounded. Default value is `0`. 

        * `quarkus.cache.redisson.[CACHE_NAME].cache-size` 

            Local cache size. If size is 0 then local cache is unbounded. Default value is `0`. 

        * `quarkus.cache.redisson.[CACHE_NAME].reconnection-strategy` 

            Used to load missed updates during any connection failures to Redis. Default value is`CLEAR`. Since, local cache updates can't be executed in absence of connection to Redis. Available values: 
    	
            * `CLEAR` - Clear local cache if map instance has been disconnected for a while.
            * `LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise.
            * `NONE` - No reconnection handling

        * `quarkus.cache.redisson.[CACHE_NAME].sync-strategy` 

            Used to synchronize local cache changes. Default value is`INVALIDATE`. Available values: 
    	
            * `INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change.
            * `UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change.
            * `NONE` - No synchronizations on map changes.

        * `quarkus.cache.redisson.[CACHE_NAME].eviction-policy`

            Defines local cache eviction policy. Default value is`NONE`. Available values:
    	
            * `LRU` - uses local cache with LRU (least recently used) eviction policy.
            * `LFU` - uses local cache with LFU (least frequently used) eviction policy.
            * `SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory.
            * `WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable.
            * `NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.

        * `quarkus.cache.redisson.[CACHE_NAME].time-to-live` 

            Time to live duration of each map entry in local cache. If value equals to 0 then timeout is not applied. Default value is `0`. 

        * `redisson.cache.redisson.[CACHE_NAME].max-idle` 
            
            Defines max idle time duration of each map entry in local cache. If value equals to 0 then timeout is not applied. Default value is `0`. 

        * `quarkus.cache.redisson.[CACHE_NAME].store-mode` 

            Defines store mode of cache data. Default value is `LOCALCACHE_REDIS`. Available values:
    	
            * `LOCALCACHE` - store data in local cache only and use Redis or Valkey only for data update/invalidation  
            * `LOCALCACHE_REDIS` - store data in both Redis or Valkey and local cache  

        * `quarkus.cache.redisson.[CACHE_NAME].cache-provider` 

            Defines Cache provider used as local cache store. Default value is `REDISSON`. Available values:
    	
            * `REDISSON` - uses Redisson own implementation
            * `CAFFEINE` - uses Caffeine implementation

        * `quarkus.cache.redisson.[CACHE_NAME].store-cache-miss` 
           
            Defines whether to store a cache miss into the local cache. Default value is `false`.

Local cache configuration example:

```
quarkus.cache.type=redisson
# possible values for localcache: localcache, localcache_v2, clustered_localcache
quarkus.cache.redisson.implementation=localcache

# Default configuration for all caches
quarkus.cache.redisson.max-size=1000
quarkus.cache.redisson.expire-after-write=5s
quarkus.cache.redisson.expire-after-access=1s
quarkus.cache.redisson.cache-size=100
quarkus.cache.redisson.eviction-policy=LFU
quarkus.cache.redisson.time-to-live=10s
quarkus.cache.redisson.max-idle=5s

# Configuration for `sampleCache` cache
quarkus.cache.redisson.sampleCache.expire-after-write=100s
quarkus.cache.redisson.sampleCache.expire-after-access=10s
quarkus.cache.redisson.sampleCache.cache-size=100
quarkus.cache.redisson.sampleCache.eviction-policy=LFU
quarkus.cache.redisson.sampleCache.time-to-live=10s
quarkus.cache.redisson.sampleCache.max-idle=5s
```

{% include 'cache/Micronaut-cache.md' %}