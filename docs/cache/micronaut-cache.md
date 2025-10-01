## Micronaut Cache

### Eviction, local cache and data partitioning

Redisson provides various [Micronaut Cache](https://guides.micronaut.io/latest/micronaut-cache-maven-java.html) implementations with multiple important features:  

1. **Local cache**  

    So called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

2. **Data partitioning**
 
    Although any Cache object is cluster compatible its content isn't scaled/partitioned across multiple Redis or Valkey master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in cluster.  

3. **Scripted eviction**

    Allows to define `time to live` or `max idle time` parameters per map entry. Eviction is done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique map object name. 

    Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

    Available implementations:

    |Setting prefix | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |redisson.caches.*<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
    |redisson.caches.*<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
    |redisson.local-caches.*<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ❌ | ✔️ |
    |redisson.clustered-caches.*<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |redisson.clustered-local-caches.*<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |

4. **Advanced eviction**

    Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis or Valkey side.

    Available implementations:

    |Setting prefix | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |redisson.caches-v2.*<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |redisson.local-caches-v2.*<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ✔️ | ✔️ |

5. **Native eviction**

    Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side.  
    Requires **Valkey 9.0+** or **Redis 7.4+**.

    Available implementations:

    |Setting prefix | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
    | ------------- | :-----------: | :----------:| :----------:|
    |redisson.caches-native.*<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
    |redisson.caches-native.*<br/><sub><i>[Redisson PRO](https://redisson.pro/feature-comparison.html) version</i></sub> | ❌ | ❌ | ✔️ |
    |redisson.local-caches-native.*<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub>  | ✔️ | ❌ | ✔️ |
    |redisson.clustered-caches-native.*<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ❌ | ✔️ | ✔️ |
    |redisson.clustered-local-caches-native.*<br/><sub><i>available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)</i></sub> | ✔️ | ✔️ | ✔️ |

    
### Usage

1. **Add `redisson-micronaut` dependency into your project:**  

    <div class="grid cards" markdown>

    -   **Redisson PRO**

        Maven

        ```xml  
        <dependency>
        	<groupId>pro.redisson</groupId>
        	<!-- for Micronaut v2.0.x - v2.5.x -->
        	<artifactId>redisson-micronaut-20</artifactId>
        	<!-- for Micronaut v3.x.x -->
        	<artifactId>redisson-micronaut-30</artifactId>
        	<!-- for Micronaut v4.x.x -->
        	<artifactId>redisson-micronaut-40</artifactId>
        	<version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Micronaut v2.0.x - v2.5.x
        compile 'pro.redisson:redisson-micronaut-20:xVERSIONx'
        // for Micronaut v3.x.x
        compile 'pro.redisson:redisson-micronaut-30:xVERSIONx'
        // for Micronaut v4.x.x
        compile 'pro.redisson:redisson-micronaut-40:xVERSIONx'
        ```

    -   **Community Edition**

        Maven

        ```xml  
        <dependency>
        	<groupId>org.redisson</groupId>
        	<!-- for Micronaut v2.0.x - v2.5.x -->
        	<artifactId>redisson-micronaut-20</artifactId>
        	<!-- for Micronaut v3.x.x -->
        	<artifactId>redisson-micronaut-30</artifactId>
        	<!-- for Micronaut v4.x.x -->
        	<artifactId>redisson-micronaut-40</artifactId>
        	<version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Micronaut v2.0.x - v2.5.x
        compile 'org.redisson:redisson-micronaut-20:xVERSIONx'
        // for Micronaut v3.x.x
        compile 'org.redisson:redisson-micronaut-30:xVERSIONx'
        // for Micronaut v4.x.x
        compile 'org.redisson:redisson-micronaut-40:xVERSIONx'
        ```

    </div>

    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)

2. **Add settings into application.yml file**

	Config structure is a Redisson YAML configuration - 
	([single mode](../configuration.md/#single-settings),
	[replicated mode](../configuration.md/#replicated-settings),
	[cluster mode](../configuration.md/#cluster-settings),
	[sentinel mode](../configuration.md/#sentinel-settings),
	[proxy mode](../configuration.md/#proxy-mode-settings),
	[multi cluster mode](../configuration.md/#multi-cluster-settings), 
	[multi sentinel mode](../configuration.md/#multi-sentinel-settings))

	NOTE: Setting names in camel case should be joined with hyphens (-).

	Config example:  

	* Scripted eviction

		```yaml
		redisson:
		  single-server-config:
			 address: "redis://127.0.0.1:6379"
		  threads: 16
		  netty-threads: 32
		  caches:
			 my-cache1: 
				expire-after-write: 10s
				expire-after-access: 3s
				max-size: 1000
				codec: org.redisson.codec.Kryo5Codec
			 my-cache2: 
			   expire-after-write: 200s
			   expire-after-access: 30s

		```

		??? note "Map Cache settings. Click to expand"
			**Setting name**: `redisson.caches.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If `0` the cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.caches.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `org.redisson.codec.Kryo5Codec`  

			**Setting name**: `redisson.caches.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.caches.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  


			**Setting name**: `redisson.caches.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.caches.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.caches.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name**: `redisson.caches.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name**: `redisson.caches.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

	2. Data partitioning + Scripted eviction

		```yaml
		redisson:
		  single-server-config:
			address: "redis://127.0.0.1:6379"
		  clustered-caches:
			my-cache1: 
			  expire-after-write: 10s
			  expire-after-access: 3s
			  max-size: 1000
			  codec: org.redisson.codec.Kryo5Codec
			my-cache2: 
			  expire-after-write: 200s
			  expire-after-access: 30s
		```

		??? note "Clustered Map Cache settings. Click to expand"
			_These settings are available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)_    
				
			**Setting name**: `redisson.clustered-caches.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-caches.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `Kryo5Codec`  

			**Setting name**: `redisson.clustered-caches.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-caches.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-caches.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.clustered-caches.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.clustered-caches.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name** `redisson.clustered-caches.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name** `redisson.clustered-caches.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

	3. Local cache + Data partitioning + Scripted eviction

		```yaml
		redisson:
		  single-server-config:
			address: "redis://127.0.0.1:6379"
		  clustered-local-caches:
			my-cache1: 
			  expire-after-write: 10s
			  expire-after-access: 3s
			  max-size: 1000
			  codec: org.redisson.codec.Kryo5Codec
			  store-сache-miss: true
			  eviction-policy: `LFU`
			  cache-size: 5000
			  time-to-live: 2s
			  max-idle: 1s
			my-cache2: 
			  expire-after-write: 200s
			  expire-after-access: 30s
			  time-to-live: 10s
			  max-idle: 5s
		```

		??? note "Clustered Local Map Cache settings. Click to expand"  
			_These settings are available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)_
				
			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded.  
			Default value: `0` |

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `Kryo5Codec`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].cache-size`  
			Type: `java.lang.Integer`  
			Description: Local cache size. If size is <code>0</code> then local cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].reconnection-strategy`  
			Type: `java.lang.String`  
			Description: Used to load missed updates during any connection failures to Redis. Since, local cache updates can't be executed in absence of connection to Redis: 

			* `CLEAR` - Clear local cache if map instance has been disconnected for a while.  
			* `LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise.  
			* `NONE` - No reconnection handling  

			Default value: `NONE`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].sync-strategy`  
			Type: `java.lang.String`  
			Description: Used to synchronize local cache changes.  

			* `INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change.  
			* `UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change.  
			* `NONE` - No synchronizations on map changes.  

			Default value: `NONE`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].eviction-policy`  
			Type: `java.lang.String`  
			Description: Defines local cache eviction policy.

			* `LRU` - uses local cache with LRU (least recently used) eviction policy. 
			* `LFU` - uses local cache with LFU (least frequently used) eviction policy. 
			* `SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory. 
			* `WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable. 
			* `NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.  

			Default value: `NONE`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].time-to-live`  
			Type: `java.lang.Integer`  
			Description: Time to live duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].max-idle`  
			Type: `java.lang.Integer`  
			Description: Defines max idle time duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].cache-provider`  
			Type: `java.lang.String`  
			Description: Defines Cache provider used as local cache store.

			* `REDISSON` - uses Redisson own implementation.
			* `CAFFEINE` - uses Caffeine implementation.  

			Default value: `REDISSON`  

			**Setting name**: `redisson.clustered-local-caches.[CACHE_NAME].store-сache-miss`  
			Type: `java.lang.Boolean`  
			Description: Defines whether to store a cache miss into the local cache.  
			Default value: `false`  

	4. Local cache + Scripted eviction

		```yaml
		redisson:
		  single-server-config:
			address: "redis://127.0.0.1:6379"
		  local-caches:
			my-cache1: 
			  expire-after-write: 10s
			  expire-after-access: 3s
			  max-size: 1000
			  codec: org.redisson.codec.Kryo5Codec
			  store-сache-miss: true
			  eviction-policy: `LFU`
			  cache-size: 5000
			  time-to-live: 1s
			  max-idle: 1s
			my-cache2: 
			  expire-after-write: 200s
			  expire-after-access: 30s
			  eviction-policy: `LFU`
			  cache-size: 5000
			  time-to-live: 10s
			  max-idle: 5s
		```

		??? note "Local Cached Map Cache settings. Click to expand"  
			_These settings are available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)_   
				
			**Setting name**: `redisson.local-caches.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `Kryo5Codec`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].cache-size`  
			Type: `java.lang.Integer`  
			Description: Local cache size. If size is <code>0</code> then local cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].reconnection-strategy`  
			Type: `java.lang.String`  
			Description: Used to load missed updates during any connection failures to Redis. Since, local cache updates can't be executed in absence of connection to Redis. <br/>`CLEAR` - Clear local cache if map instance has been disconnected for a while. <br/>`LOAD` - Store invalidated entry hash in invalidation log for 10 minutes.<br/>Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise. <br/>`NONE` - No reconnection handling 
			Default value: `NONE`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].sync-strategy`  
			Type: `java.lang.String`  
			Description: Used to synchronize local cache changes. <br/>`INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change. <br/>`UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change. <br/>`NONE` - No synchronizations on map changes.  
			Default value: `NONE`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].eviction-policy`  
			Type: `java.lang.String`  
			Description: Defines local cache eviction policy. <br/>`LRU` - uses local cache with LRU (least recently used) eviction policy. <br/>`LFU` - uses local cache with LFU (least frequently used) eviction policy. <br/>`SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory. <br/>`WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable. <br/>`NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.  
			Default value: `NONE`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].time-to-live`  
			Type: `java.time.Duration`  
			Description: Time to live duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].max-idle`  
			Type: `java.time.Duration`  
			Description: Defines max idle time duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].cache-provider`  
			Type: `java.lang.String`  
			Description: Defines Cache provider used as local cache store.<br/>`REDISSON` - uses Redisson own implementation.<br/>`CAFFEINE` - uses Caffeine implementation.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches.[CACHE_NAME].store-сache-miss`  
			Type: `java.lang.Boolean`  
			Description: Defines whether to store a cache miss into the local cache.  
			Default value: `false`  

	5. Native eviction

		```yaml
		redisson:
		  single-server-config:
			 address: "redis://127.0.0.1:6379"
		  threads: 16
		  netty-threads: 32
		  caches-native:
			 my-cache1: 
				expire-after-write: 10s
				expire-after-access: 3s
				max-size: 1000
				codec: org.redisson.codec.Kryo5Codec
			 my-cache2: 
			   expire-after-write: 200s
			   expire-after-access: 30s

		```

		??? note "Map Cache Native settings. Click to expand"
			**Setting name**: `redisson.caches-native.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If `0` the cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.caches-native.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `org.redisson.codec.Kryo5Codec`  

			**Setting name**: `redisson.caches-native.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.caches-native.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.caches-native.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.caches-native.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.caches-native.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name**: `redisson.caches-native.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name**: `redisson.caches-native.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

	6. Data partitioning + Native eviction

		```yaml
		redisson:
		  single-server-config:
			address: "redis://127.0.0.1:6379"
		  clustered-caches-native:
			my-cache1: 
			  expire-after-write: 10s
			  expire-after-access: 3s
			  max-size: 1000
			  codec: org.redisson.codec.Kryo5Codec
			my-cache2: 
			  expire-after-write: 200s
			  expire-after-access: 30s
		```

		??? note "Clustered Map Cache Native settings. Click to expand"
			_These settings are available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)_    
				
			**Setting name**: `redisson.clustered-caches-native.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-caches-native.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `Kryo5Codec`  

			**Setting name**: `redisson.clustered-caches-native.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-caches-native.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-caches-native.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.clustered-caches-native.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.clustered-caches-native.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name** `redisson.clustered-caches-native.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name** `redisson.clustered-caches-native.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

	7. Local cache + Data partitioning + Scripted eviction

		```yaml
		redisson:
		  single-server-config:
			address: "redis://127.0.0.1:6379"
		  clustered-local-caches-native:
			my-cache1: 
			  expire-after-write: 10s
			  expire-after-access: 3s
			  max-size: 1000
			  codec: org.redisson.codec.Kryo5Codec
			  store-сache-miss: true
			  eviction-policy: `LFU`
			  cache-size: 5000
			  time-to-live: 2s
			  max-idle: 1s
			my-cache2: 
			  expire-after-write: 200s
			  expire-after-access: 30s
			  time-to-live: 10s
			  max-idle: 5s
		```

		??? note "Clustered Local Map Cache Native settings. Click to expand"  
			_These settings are available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)_
				
			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded.  
			Default value: `0` |

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `Kryo5Codec`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].cache-size`  
			Type: `java.lang.Integer`  
			Description: Local cache size. If size is <code>0</code> then local cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].reconnection-strategy`  
			Type: `java.lang.String`  
			Description: Used to load missed updates during any connection failures to Redis. Since, local cache updates can't be executed in absence of connection to Redis: 

			* `CLEAR` - Clear local cache if map instance has been disconnected for a while.  
			* `LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise.  
			* `NONE` - No reconnection handling  

			Default value: `NONE`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].sync-strategy`  
			Type: `java.lang.String`  
			Description: Used to synchronize local cache changes.  

			* `INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change.  
			* `UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change.  
			* `NONE` - No synchronizations on map changes.  

			Default value: `NONE`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].eviction-policy`  
			Type: `java.lang.String`  
			Description: Defines local cache eviction policy.

			* `LRU` - uses local cache with LRU (least recently used) eviction policy. 
			* `LFU` - uses local cache with LFU (least frequently used) eviction policy. 
			* `SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory. 
			* `WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable. 
			* `NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.  

			Default value: `NONE`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].time-to-live`  
			Type: `java.lang.Integer`  
			Description: Time to live duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].max-idle`  
			Type: `java.lang.Integer`  
			Description: Defines max idle time duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].cache-provider`  
			Type: `java.lang.String`  
			Description: Defines Cache provider used as local cache store.

			* `REDISSON` - uses Redisson own implementation.
			* `CAFFEINE` - uses Caffeine implementation.  

			Default value: `REDISSON`  

			**Setting name**: `redisson.clustered-local-caches-native.[CACHE_NAME].store-сache-miss`  
			Type: `java.lang.Boolean`  
			Description: Defines whether to store a cache miss into the local cache.  
			Default value: `false`  

	8. Local cache + Native eviction

		```yaml
		redisson:
		  single-server-config:
			address: "redis://127.0.0.1:6379"
		  local-caches-native:
			my-cache1: 
			  expire-after-write: 10s
			  expire-after-access: 3s
			  max-size: 1000
			  codec: org.redisson.codec.Kryo5Codec
			  store-сache-miss: true
			  eviction-policy: `LFU`
			  cache-size: 5000
			  time-to-live: 1s
			  max-idle: 1s
			my-cache2: 
			  expire-after-write: 200s
			  expire-after-access: 30s
			  eviction-policy: `LFU`
			  cache-size: 5000
			  time-to-live: 10s
			  max-idle: 5s
		```

		??? note "Local Cached Map Cache Native settings. Click to expand"  
			_These settings are available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)_   
				
			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `Kryo5Codec`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].cache-size`  
			Type: `java.lang.Integer`  
			Description: Local cache size. If size is <code>0</code> then local cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].reconnection-strategy`  
			Type: `java.lang.String`  
			Description: Used to load missed updates during any connection failures to Redis. Since, local cache updates can't be executed in absence of connection to Redis. <br/>`CLEAR` - Clear local cache if map instance has been disconnected for a while. <br/>`LOAD` - Store invalidated entry hash in invalidation log for 10 minutes.<br/>Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise. <br/>`NONE` - No reconnection handling 
			Default value: `NONE`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].sync-strategy`  
			Type: `java.lang.String`  
			Description: Used to synchronize local cache changes. <br/>`INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change. <br/>`UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change. <br/>`NONE` - No synchronizations on map changes.  
			Default value: `NONE`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].eviction-policy`  
			Type: `java.lang.String`  
			Description: Defines local cache eviction policy. <br/>`LRU` - uses local cache with LRU (least recently used) eviction policy. <br/>`LFU` - uses local cache with LFU (least frequently used) eviction policy. <br/>`SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory. <br/>`WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable. <br/>`NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.  
			Default value: `NONE`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].time-to-live`  
			Type: `java.time.Duration`  
			Description: Time to live duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].max-idle`  
			Type: `java.time.Duration`  
			Description: Defines max idle time duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].cache-provider`  
			Type: `java.lang.String`  
			Description: Defines Cache provider used as local cache store.<br/>`REDISSON` - uses Redisson own implementation.<br/>`CAFFEINE` - uses Caffeine implementation.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-native.[CACHE_NAME].store-сache-miss`  
			Type: `java.lang.Boolean`  
			Description: Defines whether to store a cache miss into the local cache.  
			Default value: `false`  

	9. Data partitioning + Advanced eviction

		```yaml
		redisson:
		  single-server-config:
			 address: "redis://127.0.0.1:6379"
		  threads: 16
		  netty-threads: 32
		  caches-v2:
			 my-cache1: 
				expire-after-write: 10s
				expire-after-access: 3s
				max-size: 1000
				codec: org.redisson.codec.Kryo5Codec
			 my-cache2: 
			   expire-after-write: 200s
			   expire-after-access: 30s

		```

		??? note "Map Cache V2 settings. Click to expand"
			**Setting name**: `redisson.caches-v2.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If `0` the cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.caches-v2.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `org.redisson.codec.Kryo5Codec`  

			**Setting name**: `redisson.caches-v2.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.caches-v2.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  


			**Setting name**: `redisson.caches-v2.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.caches-v2.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.caches-v2.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name**: `redisson.caches-v2.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name**: `redisson.caches-v2.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

	10. Local cache + Data partitioning + Advanced eviction

		```yaml
		redisson:
		  single-server-config:
			address: "redis://127.0.0.1:6379"
		  local-caches-v2:
			my-cache1: 
			  expire-after-write: 10s
			  expire-after-access: 3s
			  max-size: 1000
			  codec: org.redisson.codec.Kryo5Codec
			  store-сache-miss: true
			  eviction-policy: `LFU`
			  cache-size: 5000
			  time-to-live: 2s
			  max-idle: 1s
			my-cache2: 
			  expire-after-write: 200s
			  expire-after-access: 30s
			  time-to-live: 10s
			  max-idle: 5s
		```

		??? note "Local Map Cache V2 settings. Click to expand"  
			_These settings are available only in [Redisson PRO](https://redisson.pro/feature-comparison.html)_
				
			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].max-size`  
			Type: `java.lang.Integer`  
			Description: Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded.  
			Default value: `0` |

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].codec`  
			Type: `java.lang.Class`  
			Description: Data codec applied to cache entries.  
			Default value: `Kryo5Codec`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].expire-after-write`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].expire-after-access`  
			Type: `java.time.Duration`  
			Description: Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].write-behind-batch-size`  
			Type: `java.lang.Integer`  
			Description: Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size.  
			Default value: `50`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].write-behind-delay`  
			Type: `java.time.Duration`  
			Description: Write behind tasks execution delay. All updates would be applied with lag not more than specified delay.  
			Default value: `1000ms`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].writer`  
			Type: `java.lang.Class`  
			Description: `MapWriter` object used for write-through operations  
			Default value: `null`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].write-mode`  
			Type: `java.lang.String`  
			Description: Write mode. Default is `WRITE_THROUGH`  
			Default value: `null`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].loader`  
			Type: `java.lang.Class`  
			Description: `MapLoader` object used to load entries during read-operations execution  
			Default value: `null`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].cache-size`  
			Type: `java.lang.Integer`  
			Description: Local cache size. If size is <code>0</code> then local cache is unbounded.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].reconnection-strategy`  
			Type: `java.lang.String`  
			Description: Used to load missed updates during any connection failures to Redis. Since, local cache updates can't be executed in absence of connection to Redis: 

			* `CLEAR` - Clear local cache if map instance has been disconnected for a while.  
			* `LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise.  
			* `NONE` - No reconnection handling  

			Default value: `NONE`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].sync-strategy`  
			Type: `java.lang.String`  
			Description: Used to synchronize local cache changes.  

			* `INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change.  
			* `UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change.  
			* `NONE` - No synchronizations on map changes.  

			Default value: `NONE`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].eviction-policy`  
			Type: `java.lang.String`  
			Description: Defines local cache eviction policy.

			* `LRU` - uses local cache with LRU (least recently used) eviction policy. 
			* `LFU` - uses local cache with LFU (least frequently used) eviction policy. 
			* `SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory. 
			* `WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable. 
			* `NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.  

			Default value: `NONE`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].time-to-live`  
			Type: `java.lang.Integer`  
			Description: Time to live duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].max-idle`  
			Type: `java.lang.Integer`  
			Description: Defines max idle time duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied.  
			Default value: `0`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].cache-provider`  
			Type: `java.lang.String`  
			Description: Defines Cache provider used as local cache store.

			* `REDISSON` - uses Redisson own implementation.
			* `CAFFEINE` - uses Caffeine implementation.  

			Default value: `REDISSON`  

			**Setting name**: `redisson.local-caches-v2.[CACHE_NAME].store-сache-miss`  
			Type: `java.lang.Boolean`  
			Description: Defines whether to store a cache miss into the local cache.  
			Default value: `false`  

Code example:  

```java
@Singleton 
@CacheConfig("my-cache1") 
public class CarsService {

    @Cacheable
    public List<String> listAll() {
        // ...
    }
    
    @CachePut(parameters = {"type"}) 
    public List<String> addCar(String type, String description) {
        // ...
    }
    
    @CacheInvalidate(parameters = {"type"}) 
    public void removeCar(String type, String description) {
        // ...
    }    
}
```
