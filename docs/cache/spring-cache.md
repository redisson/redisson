## Spring Cache

Redisson provides various Spring Cache implementations. Each Cache instance has two important parameters: `ttl` and `maxIdleTime`. Data is stored infinitely if these settings are not defined or equal to `0`.  

Config example:
```java
    @Configuration
    @ComponentScan
    @EnableCaching
    public static class Application {

        @Bean(destroyMethod="shutdown")
        RedissonClient redisson() throws IOException {
            Config config = new Config();
            config.useClusterServers()
                  .addNodeAddress("redis://127.0.0.1:7004", "redis://127.0.0.1:7001");
            return Redisson.create(config);
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) {
            Map<String, CacheConfig> config = new HashMap<String, CacheConfig>();

            // create "testMap" cache with ttl = 24 minutes and maxIdleTime = 12 minutes
            config.put("testMap", new CacheConfig(24*60*1000, 12*60*1000));
            return new RedissonSpringCacheManager(redissonClient, config);
        }

    }
```

Cache configuration can be read from YAML configuration files:

```java
    @Configuration
    @ComponentScan
    @EnableCaching
    public static class Application {

        @Bean(destroyMethod="shutdown")
        RedissonClient redisson(@Value("classpath:/redisson.yaml") Resource configFile) throws IOException {
            Config config = Config.fromYAML(configFile.getInputStream());
            return Redisson.create(config);
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) throws IOException {
            return new RedissonSpringCacheManager(redissonClient, "classpath:/cache-config.yaml");
        }

    }
```

### Eviction, local cache and data partitioning
Redisson provides various Spring Cache managers with two important features:  

**local cache** - so called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

**data partitioning** - although Map object is cluster compatible its content isn't scaled/partitioned across multiple Redis or Valkey master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in cluster.  

**Scripted eviction**

Allows to define `time to live` or `max idle time` parameters per map entry. Eviction is done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis or Valkey calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](../configuration.md) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](../configuration.md) and [maxCleanUpDelay](../configuration.md). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

Available implementations:

|Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonSpringCacheManager<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|RedissonSpringCacheManager<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|RedissonSpringLocalCachedCacheManager<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|RedissonClusteredSpringCacheManager<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|RedissonClusteredSpringLocalCachedCacheManager<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |

**Advanced eviction**

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis or Valkey side.

Available implementations:

|Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonSpringCacheV2Manager<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|RedissonSpringLocalCachedCacheV2Manager<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub>  | ✔️ | ✔️ | ✔️ |

**Native eviction**

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side.  
Requires **Redis 7.4+**.

Available implementations:

|Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonSpringCacheNativeManager<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|RedissonSpringCacheNativeManager<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|RedissonSpringLocalCachedCacheNativeManager<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|RedissonClusteredSpringCacheNativeManager<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |

**Local cache**

Follow options object can be supplied during local cached managers initialization:
```java
LocalCachedMapOptions options = LocalCachedMapOptions.defaults()

// Defines whether to store a cache miss into the local cache.
// Default value is false.
.storeCacheMiss(false);

// Defines store mode of cache data.
// Follow options are available:
// LOCALCACHE - store data in local cache only.
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

 // time to live for each map entry in local cache
.timeToLive(10000)
 // or
.timeToLive(10, TimeUnit.SECONDS)

 // max idle time for each map entry in local cache
.maxIdle(10000)
 // or
.maxIdle(10, TimeUnit.SECONDS);
```

Each Spring Cache instance has two important parameters: `ttl` and `maxIdleTime` and stores data infinitely if they are not defined or equal to `0`.

Complete config example:
```java
@Configuration
@ComponentScan
@EnableCaching
public static class Application {

    @Bean(destroyMethod="shutdown")
    RedissonClient redisson() throws IOException {
        Config config = new Config();
        config.useClusterServers()
              .addNodeAddress("redis://127.0.0.1:7004", "redis://127.0.0.1:7001");
        return Redisson.create(config);
    }

    @Bean
    CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> config = new HashMap<String, CacheConfig>();

        // define local cache settings for "testMap" cache.
        // ttl = 48 minutes and maxIdleTime = 24 minutes for local cache entries
        LocalCachedMapOptions options = LocalCachedMapOptions.defaults()
            .evictionPolicy(EvictionPolicy.LFU)
            .timeToLive(48, TimeUnit.MINUTES)
            .maxIdle(24, TimeUnit.MINUTES);
            .cacheSize(1000);
 
        // create "testMap" Redis or Valkey cache with ttl = 24 minutes and maxIdleTime = 12 minutes
        LocalCachedCacheConfig cfg = new LocalCachedCacheConfig(24*60*1000, 12*60*1000, options);
        // Max size of map stored in Redis
        cfg.setMaxSize(2000);
        config.put("testMap", cfg);

        return new RedissonSpringLocalCachedCacheManager(redissonClient, config);
        // or 
        return new RedissonSpringLocalCachedCacheNativeManager(redissonClient, config);
        // or 
        return new RedissonSpringLocalCachedCacheV2Manager(redissonClient, config);
        // or 
        return new RedissonClusteredSpringLocalCachedCacheManager(redissonClient, config);
    }

}
```

Cache configuration could be read from YAML configuration files:

```java
    @Configuration
    @ComponentScan
    @EnableCaching
    public static class Application {

        @Bean(destroyMethod="shutdown")
        RedissonClient redisson(@Value("classpath:/redisson.yaml") Resource configFile) throws IOException {
            Config config = Config.fromYAML(configFile.getInputStream());
            return Redisson.create(config);
        }

        @Bean
        CacheManager cacheManager(RedissonClient redissonClient) throws IOException {
            return new RedissonSpringLocalCachedCacheManager(redissonClient, "classpath:/cache-config.yaml");
        }

    }
```

### YAML config format
Below is the configuration of Spring Cache with name `testMap` in YAML format:

```yaml
---
testMap:
  ttl: 1440000
  maxIdleTime: 720000
  localCacheOptions:
    invalidationPolicy: "ON_CHANGE"
    evictionPolicy: "NONE"
    cacheSize: 0
    timeToLiveInMillis: 0
    maxIdleInMillis: 0
```

_Please note: `localCacheOptions` settings are available for `org.redisson.spring.cache.RedissonSpringLocalCachedCacheManager` and `org.redisson.spring.cache.RedissonSpringClusteredLocalCachedCacheManager` classes only._
