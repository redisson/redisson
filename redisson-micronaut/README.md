# Redis integration with Micronaut

Integrates Redisson with [Micronaut](https://micronaut.io/) framework.  

Supports Micronaut 2.0.x - 4.x.x

## Usage  

### 1. Add `redisson-micronaut` dependency into your project:  

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
    <version>3.36.0</version>
</dependency>
```

Gradle

```groovy
// for Micronaut v2.0.x - v2.5.x
compile 'org.redisson:redisson-micronaut-20:3.36.0'
// for Micronaut v3.x.x
compile 'org.redisson:redisson-micronaut-30:3.36.0'
// for Micronaut v4.x.x
compile 'org.redisson:redisson-micronaut-40:3.36.0'
```

### 2. Add settings into `application.yml` file

#### 2.1 Redisson settings

Config structure is a Redisson YAML configuration - 
[single mode](https://github.com/redisson/redisson/wiki/2.-Configuration#262-single-instance-yaml-config-format),
[replicated mode](https://github.com/redisson/redisson/wiki/2.-Configuration#252-replicated-yaml-config-format),
[cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration#242-cluster-yaml-config-format),
[sentinel mode](https://github.com/redisson/redisson/wiki/2.-Configuration#272-sentinel-yaml-config-format),
[proxy mode](https://github.com/redisson/redisson/wiki/2.-Configuration#292-proxy-mode-yaml-config-format)

NOTE: Setting names in camel case should be joined with hyphens (-).

Config example:
```yaml
redisson:
  single-server-config:
     address: "redis://127.0.0.1:6379"
  threads: 16
  netty-threads: 32
```

#### 2.2 Cache settings

Redisson provides various Cache implementations with multiple important features:  

**local cache** - so called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

**data partitioning** - although any Cache object is cluster compatible its content isn't scaled/partitioned across multiple Redis master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in Redis cluster.  

#### 1. Scripted eviction

Allows to define `time to live` or `max idle time` parameters per map entry. Redis hash structure doesn't support eviction thus it's done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](https://github.com/redisson/redisson/wiki/2.-Configuration#cleanupkeysamount) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](https://github.com/redisson/redisson/wiki/2.-Configuration#mincleanupdelay) and [maxCleanUpDelay](https://github.com/redisson/redisson/wiki/2.-Configuration#maxcleanupdelay). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

Available implementations:

|Setting prefix | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|redisson.caches.*<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|redisson.caches.*<br/><sub><i>[Redisson PRO](http://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|redisson.local-caches.*<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|redisson.clustered-caches.*<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|redisson.clustered-local-caches.*<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |

#### 2. Advanced eviction

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side.

Available implementations:

|Setting prefix | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|redisson.caches-v2.*<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|redisson.local-caches-v2.*<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ✔️ | ✔️ |

#### 3. Native eviction

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side.  
Requires **Redis 7.4+**.

Available implementations:

|Setting prefix | Local cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|redisson.caches-native.*<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|redisson.caches-native.*<br/><sub><i>[Redisson PRO](http://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|redisson.local-caches-native.*<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|redisson.clustered-caches-native.*<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |

<details>
    <summary><b>Map Cache</b> settings. Click to expand!</summary>
<br/>
    
| | |
|-|-|
|Setting name| `redisson.caches.[CACHE_NAME].max-size` |
|Type| `java.lang.Integer` |
|Description| Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.caches.[CACHE_NAME].codec` |
|Type| `java.lang.Class` |
|Description| Redis data codec applied to cache entries. |
|Default value| `Kryo5Codec` |

| | |
|-|-|
|Setting name| `redisson.caches.[CACHE_NAME].expire-after-write` |
|Type| `java.time.Duration` |
|Description| Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.caches.[CACHE_NAME].expire-after-access` |
|Type| `java.time.Duration` |
|Description| Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.caches.[CACHE_NAME].write-behind-batch-size` |
|Type| `java.lang.Integer` |
|Description| Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size. |
|Default value| `50` |

| | |
|-|-|
|Setting name| `redisson.caches.[CACHE_NAME].write-behind-delay` |
|Type| `java.time.Duration` |
|Description| Write behind tasks execution delay. All updates would be applied with lag not more than specified delay. |
|Default value| `1000ms` |

| | |
|-|-|
|Setting name| `redisson.caches.[CACHE_NAME].writer` |
|Type| `java.lang.Class` |
|Description| `MapWriter` object used for write-through operations |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.caches.[CACHE_NAME].write-mode` |
|Type| `java.lang.String` |
|Description| Write mode. Default is `WRITE_THROUGH` |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.caches.[CACHE_NAME].loader` |
|Type| `java.lang.Class` |
|Description| `MapLoader` object used to load entries during read-operations execution |
|Default value| `null` |


Config example:
```yaml
redisson:
  single-server-config:
    address: "redis://127.0.0.1:6379"
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
</details>

<details>
    <summary><b>Clustered Map Cache</b> settings. Click to expand!</summary>

<br/>

_This feature is available only in [Redisson PRO](https://redisson.pro)_    
    
| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].max-size` |
|Type| `java.lang.Integer` |
|Description| Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].codec` |
|Type| `java.lang.Class` |
|Description| Redis data codec applied to cache entries. |
|Default value| `Kryo5Codec` |

| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].expire-after-write` |
|Type| `java.time.Duration` |
|Description| Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].expire-after-access` |
|Type| `java.time.Duration` |
|Description| Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].write-behind-batch-size` |
|Type| `java.lang.Integer` |
|Description| Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size. |
|Default value| `50` |

| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].write-behind-delay` |
|Type| `java.time.Duration` |
|Description| Write behind tasks execution delay. All updates would be applied with lag not more than specified delay. |
|Default value| `1000ms` |

| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].writer` |
|Type| `java.lang.Class` |
|Description| `MapWriter` object used for write-through operations |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].write-mode` |
|Type| `java.lang.String` |
|Description| Write mode. Default is `WRITE_THROUGH` |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.clustered-caches.[CACHE_NAME].loader` |
|Type| `java.lang.Class` |
|Description| `MapLoader` object used to load entries during read-operations execution |
|Default value| `null` |

Config example:
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
</details>
    
<details>
    <summary><b>Clustered Local Cached Map Cache</b> settings. Click to expand!</summary>

<br/>
    
_This feature is available only in [Redisson PRO](https://redisson.pro)_
    
| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].max-size` |
|Type| `java.lang.Integer` |
|Description| Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].codec` |
|Type| `java.lang.Class` |
|Description| Redis data codec applied to cache entries. |
|Default value| `Kryo5Codec` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].expire-after-write` |
|Type| `java.time.Duration` |
|Description| Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].expire-after-access` |
|Type| `java.time.Duration` |
|Description| Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].write-behind-batch-size` |
|Type| `java.lang.Integer` |
|Description| Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size. |
|Default value| `50` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].write-behind-delay` |
|Type| `java.time.Duration` |
|Description| Write behind tasks execution delay. All updates would be applied with lag not more than specified delay. |
|Default value| `1000ms` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].writer` |
|Type| `java.lang.Class` |
|Description| `MapWriter` object used for write-through operations |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].write-mode` |
|Type| `java.lang.String` |
|Description| Write mode. Default is `WRITE_THROUGH` |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].loader` |
|Type| `java.lang.Class` |
|Description| `MapLoader` object used to load entries during read-operations execution |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.clustered-local-caches.[CACHE_NAME].cache-size` |
|Type| `java.lang.Integer` |
|Description| Local cache size. If size is <code>0</code> then local cache is unbounded. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.clustered-local-caches.[CACHE_NAME].reconnection-strategy` |
|Type| `java.lang.String` |
|Description| Used to load missed updates during any connection failures to Redis. Since, local cache updates can't be executed in absence of connection to Redis. <br/>`CLEAR` - Clear local cache if map instance has been disconnected for a while. <br/>`LOAD` - Store invalidated entry hash in invalidation log for 10 minutes.<br/>Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise. <br/>`NONE` - No reconnection handling|
|Default value| `NONE` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.clustered-local-caches.[CACHE_NAME].sync-strategy` |
|Type| `java.lang.String` |
|Description| Used to synchronize local cache changes. <br/>`INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change. <br/>`UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change. <br/>`NONE` - No synchronizations on map changes.|
|Default value| `NONE` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.clustered-local-caches.[CACHE_NAME].eviction-policy` |
|Type| `java.lang.String` |
|Description| Defines local cache eviction policy.<br/>`LRU` - uses local cache with LRU (least recently used) eviction policy. <br/>`LFU` - uses local cache with LFU (least frequently used) eviction policy. <br/>`SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory. <br/>`WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable. <br/>`NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working. |
|Default value| `NONE` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.clustered-local-caches.[CACHE_NAME].time-to-live` |
|Type| `java.lang.String` |
|Description| Time to live duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.clustered-local-caches.[CACHE_NAME].max-idle` |
|Type| `java.lang.String` |
|Description| Defines max idle time duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.clustered-local-caches.[CACHE_NAME].cache-provider` |
|Type| `java.lang.String` |
|Description| Defines Cache provider used as local cache store.<br/>`REDISSON` - uses Redisson own implementation.<br/>`CAFFEINE` - uses Caffeine implementation. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.clustered-local-caches.[CACHE_NAME].store-сache-miss` |
|Type| `java.lang.String` |
|Description| Defines whether to store a cache miss into the local cache. |
|Default value| `false` |



Config example:
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
</details>
    
<details>
    <summary><b>Local Cached Map Cache</b> settings. Click to expand!</summary>

<br/>

_This feature is available only in [Redisson PRO](https://redisson.pro)_    
    
| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].max-size` |
|Type| `java.lang.Integer` |
|Description| Max size of this cache. Superfluous elements are evicted using LRU algorithm. If <code>0</code> the cache is unbounded. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].codec` |
|Type| `java.lang.Class` |
|Description| Redis data codec applied to cache entries. |
|Default value| `Kryo5Codec` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].expire-after-write` |
|Type| `java.time.Duration` |
|Description| Cache entry time to live duration applied after each write operation. Disabled if value is <code>0</code>. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].expire-after-access` |
|Type| `java.time.Duration` |
|Description| Cache entry time to live duration applied after each read operation. Disabled if value is <code>0</code>. |
|Default value| `0` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].write-behind-batch-size` |
|Type| `java.lang.Integer` |
|Description| Write behind tasks batch size. During `MapWriter` methods execution all updates accumulated into a batch of specified size. |
|Default value| `50` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].write-behind-delay` |
|Type| `java.time.Duration` |
|Description| Write behind tasks execution delay. All updates would be applied with lag not more than specified delay. |
|Default value| `1000ms` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].writer` |
|Type| `java.lang.Class` |
|Description| `MapWriter` object used for write-through operations |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].write-mode` |
|Type| `java.lang.String` |
|Description| Write mode. Default is `WRITE_THROUGH` |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].loader` |
|Type| `java.lang.Class` |
|Description| `MapLoader` object used to load entries during read-operations execution |
|Default value| `null` |

| | |
|-|-|
|Setting name| `redisson.local-caches.[CACHE_NAME].cache-size` |
|Type| `java.lang.Integer` |
|Description| Local cache size. If size is <code>0</code> then local cache is unbounded. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.local-caches.[CACHE_NAME].reconnection-strategy` |
|Type| `java.lang.String` |
|Description| Used to load missed updates during any connection failures to Redis. Since, local cache updates can't be executed in absence of connection to Redis. <br/>`CLEAR` - Clear local cache if map instance has been disconnected for a while. <br/>`LOAD` - Store invalidated entry hash in invalidation log for 10 minutes.<br/>Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise. <br/>`NONE` - No reconnection handling|
|Default value| `NONE` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.local-caches.[CACHE_NAME].sync-strategy` |
|Type| `java.lang.String` |
|Description| Used to synchronize local cache changes. <br/>`INVALIDATE` - Invalidate cache entry across all LocalCachedMap instances on map entry change. <br/>`UPDATE` - Insert/update cache entry across all LocalCachedMap instances on map entry change. <br/>`NONE` - No synchronizations on map changes.|
|Default value| `NONE` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.local-caches.[CACHE_NAME].eviction-policy` |
|Type| `java.lang.String` |
|Description| Defines local cache eviction policy. <br/>`LRU` - uses local cache with LRU (least recently used) eviction policy. <br/>`LFU` - uses local cache with LFU (least frequently used) eviction policy. <br/>`SOFT` - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory. <br/>`WEAK` - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable. <br/>`NONE` - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working. |
|Default value| `NONE` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.local-caches.[CACHE_NAME].time-to-live` |
|Type| `java.time.Duration` |
|Description| Time to live duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.local-caches.[CACHE_NAME].max-idle` |
|Type| `java.time.Duration` |
|Description| Defines max idle time duration of each map entry in local cache. If value equals to <code>0</code> then timeout is not applied. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.local-caches.[CACHE_NAME].cache-provider` |
|Type| `java.lang.String` |
|Description| Defines Cache provider used as local cache store.<br/>`REDISSON` - uses Redisson own implementation.<br/>`CAFFEINE` - uses Caffeine implementation. |
|Default value| `0` |

| | |
|-|-|
|Setting&nbsp;name| `redisson.local-caches.[CACHE_NAME].store-сache-miss` |
|Type| `java.lang.Boolean` |
|Description| Defines whether to store a cache miss into the local cache. |
|Default value| `false` |



Config example:
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
</details>
    
#### 2.3 Session settings

[Session](https://docs.micronaut.io/latest/api/io/micronaut/session/Session.html) store implementation.
Additional settings to [HttpSessionConfiguration](https://docs.micronaut.io/2.5.4/api/io/micronaut/session/http/HttpSessionConfiguration.html) object:

|Setting name|Type|Description|
|------------|----|-----------|
|micronaut.session.http.redisson.enabled|java.lang.Boolean|Enables Session store|
|micronaut.session.http.redisson.key-prefix|java.lang.Integer|Defines string prefix applied to all objects stored in Redis.|
|micronaut.session.http.redisson.codec|java.lang.Class|Redis data codec applied to cache entries. Default is Kryo5Codec codec.|
|micronaut.session.http.redisson.update-mode|java.lang.String|Defines session attributes update mode.<br/>`WRITE_BEHIND` - session changes stored asynchronously.<br/>`AFTER_REQUEST` - session changes stored only on `SessionStore#save(Session)` method invocation. Default value.|
|micronaut.session.http.redisson.broadcastSessionUpdates|java.lang.Boolean|Defines broadcasting of session updates across all micronaut services.|


Config example:

```yaml
micronaut:
    session:
        http:
            redisson:
                enabled: true
                update-mode: "WRITE_BEHIND"
                broadcast-session-updates: false
```
### 3 Use Redisson

#### 3.1 Redisson instance

```java
@Inject
private RedissonClient redisson;
```

#### 3.2 Cache

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
