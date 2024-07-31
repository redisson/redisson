# Quarkus extension for Redis

Integrates Redisson with [Quarkus](https://quarkus.io/) framework. Implements [Quarkus Cache](https://quarkus.io/guides/cache).

<details>
    <summary><b>Native image with RemoteService</b>. Click to expand!</summary>
<br/>
To use RemoteService in native image add <b>dynamic-proxy.json</b> and <b>reflection-config.json</b> files in `quarkus.native.additional-build-args` setting.

```
-H:DynamicProxyConfigurationResources=dynamic-proxy.json,-H:ReflectionConfigurationFiles=reflection-config.json
```

dynamic-proxy.json:
```
[
    ["<Remote Service interface name>"]
]
```

reflection-config.json:
```
[
   {
     "name":"<Remote Service interface name>",
     "allDeclaredMethods":true
   }
]
``` 
</details>

## Cache usage

### 1. Add `redisson-quarkus-cache` dependency into your project:  

Maven  

```xml  
<dependency>
    <groupId>org.redisson</groupId>
    <!-- for Quarkus v3.x.x -->
    <artifactId>redisson-quarkus-30-cache</artifactId>
    <version>3.34.1</version>
</dependency>
```

Gradle

```groovy
// for Quarkus v3.x.x
compile 'org.redisson:redisson-quarkus-30-cache:3.34.1'
```

### 2. Add settings into `application.properties` file

Redisson provides various Cache implementations with multiple important features:  

**local cache** - so called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

**data partitioning** - although Map object is cluster compatible its content isn't scaled/partitioned across multiple Redis master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in Redis cluster.  

**`scripted` entry eviction** - allows to define `time to live` or `max idle time` parameters per map entry. Redis hash structure doesn't support eviction thus it's done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once by getMapCache() method execution per unique object name. So even if instance isn't used and has expired entries it should be get through getMapCache() method to start the eviction process. This leads to extra Redis calls and eviction task per unique map object name.

**`advanced` entry eviction** - allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task.

**`native` entry eviction** - allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task. Requires **Redis 7.4+**.

Below is the list of all Cache implementations:  

|`impementation`<br/>setting value | Local<br/>cache | Data<br/>partitioning | Entry<br/>eviction | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :-----------:| :----------:| :---------:|
|`standard`<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | **scripted** | ❌ |
|`native`<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | **native**| ❌ |
|`standard`<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | **scripted** | ✔️ |
|`native`<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | **native**| ✔️ |
|`v2`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | **advanced** | ✔️ |
|`localcache`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ❌ | **scripted** | ✔️ |
|`localcache_v2`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | **advanced** | ✔️ |
|`clustered`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | **scripted** | ✔️ |
|`clustered_localcache`<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | **scripted** | ✔️ |

#### 2.1. Cache settings

`expire-after-write` - setting defines time to live of the item stored in the cache. Default value is `0`.  
`expire-after-access` - setting defines time to live added to the item after read operation. Default value is `0`.  
`implementation` - setting defines the type of cache used. Default value is `standard`. 

Below is the cache configuration example.

```
quarkus.cache.type=redisson
quarkus.cache.redisson.implementation=standard

# Default configuration for all caches
quarkus.cache.redisson.expire-after-write=5s
quarkus.cache.redisson.expire-after-access=1s

# Configuration for `sampleCache` cache
quarkus.cache.redisson.sampleCache.expire-after-write=100s
quarkus.cache.redisson.sampleCache.expire-after-access=10s
```

#### 2.2. Local cache settings

`quarkus.cache.redisson.[CACHE_NAME].max-size` - max size of this cache. Superfluous elements are evicted using LRU algorithm. If 0 the cache is unbounded. Default value is `0`. 

`quarkus.cache.redisson.[CACHE_NAME].cache-size` - local cache size. If size is 0 then local cache is unbounded. Default value is `0`. 

`quarkus.cache.redisson.[CACHE_NAME].reconnection-strategy` - used to load missed updates during any connection failures to Redis. Default value is`CLEAR`. Since, local cache updates can't be executed in absence of connection to Redis. Available values: 
 * `CLEAR` - Clear local cache if map instance has been disconnected for a while.
 * `LOAD` - Store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise.
 * `NONE` - No reconnection handling

`redisson.cache.redisson.[CACHE_NAME].sync-strategy` - used to synchronize local cache changes. Default value is`INVALIDATE`. Available values: 
* `INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change.
* `UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change.
* `NONE` - No synchronizations on map changes.

`redisson.cache.redisson.[CACHE_NAME].eviction-policy` - defines local cache eviction policy. Default value is`NONE`. Available values:
* `LRU` - uses local cache with LRU (least recently used) eviction policy.
* `LFU` - uses local cache with LFU (least frequently used) eviction policy.
* `SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory.
* `WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable.
* `NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.

`redisson.cache.redisson.[CACHE_NAME].time-to-live` - time to live duration of each map entry in local cache. If value equals to 0 then timeout is not applied. Default value is `0`. 

`redisson.cache.redisson.[CACHE_NAME].max-idle` - defines max idle time duration of each map entry in local cache. If value equals to 0 then timeout is not applied. Default value is `0`. 

`redisson.cache.redisson.[CACHE_NAME].store-mode` - defines store mode of cache data. Default value is `LOCALCACHE_REDIS`. Available values:
* `LOCALCACHE` - store data in local cache only and use Redis only for data update/invalidation  
* `LOCALCACHE_REDIS` - store data in both Redis and local cache  

`redisson.cache.redisson.[CACHE_NAME].cache-provider` - defines Cache provider used as local cache store. Default value is `REDISSON`. Available values:
* `REDISSON` - uses Redisson own implementation
* `CAFFEINE` - uses Caffeine implementation

`redisson.cache.redisson.[CACHE_NAME].store-cache-miss` - defines whether to store a cache miss into the local cache. Default value is `false`.

Below is the local cache configuration example.

```
quarkus.cache.type=redisson
# possible values for localcache: localcache, localcache_v2, clustered_localcache
quarkus.cache.redisson.implementation=localcache

# Default configuration for all caches
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


## Redisson usage  

### 1. Add `redisson-quarkus` dependency into your project:  

Maven  

```xml  
<dependency>
    <groupId>org.redisson</groupId>
    <!-- for Quarkus v1.6.x - v1.13.x -->
    <artifactId>redisson-quarkus-16</artifactId>
    <!-- for Quarkus v2.x.x -->
    <artifactId>redisson-quarkus-20</artifactId>
    <!-- for Quarkus v3.x.x -->
    <artifactId>redisson-quarkus-30</artifactId>
    <version>3.34.1</version>
</dependency>
```

Gradle

```groovy
// for Quarkus v1.6.x - v1.13.x
compile 'org.redisson:redisson-quarkus-16:3.34.1'
// for Quarkus v2.x.x
compile 'org.redisson:redisson-quarkus-20:3.34.1'
// for Quarkus v3.x.x
compile 'org.redisson:redisson-quarkus-30:3.34.1'
```

### 2. Add settings into `application.properties` file
  
Config structure is a flat Redisson YAML configuration - 
[single mode](https://github.com/redisson/redisson/wiki/2.-Configuration#262-single-instance-yaml-config-format),
[replicated mode](https://github.com/redisson/redisson/wiki/2.-Configuration#252-replicated-yaml-config-format),
[cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration#242-cluster-yaml-config-format),
[sentinel mode](https://github.com/redisson/redisson/wiki/2.-Configuration#272-sentinel-yaml-config-format),
[proxy mode](https://github.com/redisson/redisson/wiki/2.-Configuration#292-proxy-mode-yaml-config-format)

NOTE: Setting names in camel case should be joined with hyphens (-).

Below is the configuration example for a single Redis node setup.
```
quarkus.redisson.single-server-config.address=redis://localhost:6379
quarkus.redisson.single-server-config.password=null
quarkus.redisson.threads=16
quarkus.redisson.netty-threads=32
```

Use `quarkus.redisson.file` setting to specify path to a config file.    
    
### 3. Use Redisson

```java
@Inject
RedissonClient redisson;
```

Upgrade to __[Redisson PRO](https://redisson.pro)__ with **advanced features**.
