# Redis based MyBatis Cache implementation

Implements [MyBatis Cache](https://mybatis.org/mybatis-3/sqlmap-xml.html#cache) based on Redis.  

Compatible with MyBatis 3.0.0+

Redisson provides various MyBatis Cache implementations with features below:

**local cache** - so called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

**data partitioning** - although Map object is cluster compatible its content isn't scaled/partitioned across multiple Redis master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in Redis cluster.  

#### 1. Scripted eviction

Allows to define `time to live` or `max idle time` parameters per entry. Redis hash structure doesn't support eviction thus it's done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once per unique object name at the moment of getting Map instance. If instance isn't used and has expired entries it should be get again to start the eviction process. This leads to extra Redis calls and eviction task per unique map object name. 

Entries are cleaned time to time by `org.redisson.eviction.EvictionScheduler`. By default, it removes 100 expired entries at a time. This can be changed through [cleanUpKeysAmount](https://github.com/redisson/redisson/wiki/2.-Configuration#cleanupkeysamount) setting. Task launch time tuned automatically and depends on expired entries amount deleted in previous time and varies between 5 second to 30 minutes by default. This time interval can be changed through [minCleanUpDelay](https://github.com/redisson/redisson/wiki/2.-Configuration#mincleanupdelay) and [maxCleanUpDelay](https://github.com/redisson/redisson/wiki/2.-Configuration#maxcleanupdelay). For example, if clean task deletes 100 entries each time it will be executed every 5 seconds (minimum execution delay). But if current expired entries amount is lower than previous one then execution delay will be increased by 1.5 times and decreased otherwise.

Available implementations:

|Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonCache<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|RedissonCache<br/><sub><i>[Redisson PRO](http://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|RedissonLocalCachedCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ❌ |  ✔️ |
|RedissonClusteredCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ |  ✔️ |
|RedissonClusteredLocalCachedCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |

#### 2. Advanced eviction

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side.

Available implementations:

|Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonCacheV2<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|RedissonLocalCachedCacheV2<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ✔️ | ✔️ |

#### 3. Native eviction

Allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task, entries are cleaned on Redis side.  
Requires **Redis 7.4+**.

Available implementations:

|Class name | Local<br/>cache | Data<br/>partitioning | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonCacheNative<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|RedissonCacheNative<br/><sub><i>[Redisson PRO](http://redisson.pro) version</i></sub> | ❌ | ❌ |  ✔️ |
|RedissonLocalCachedCacheNative<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|RedissonClusteredCacheNative<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ |  ✔️ |

## MyBatis Cache Usage

### 1. Add `redisson-mybatis` dependency into your project:

Maven

```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <artifactId>redisson-mybatis</artifactId>
         <version>3.34.1</version>
     </dependency>
```

Gradle

```groovy
     compile 'org.redisson:redisson-mybatis:3.34.1'
```

### 2. Specify MyBatis cache settings

Redisson allows to define follow settings per Cache instance:

`timeToLive` - defines time to live per cache entry

`maxIdleTime` - defines max idle time per cache entry

`maxSize` - defines max size of entries amount stored in Redis

`localCacheProvider` - cache provider used as local cache store. `REDISSON` and `CAFFEINE` providers are available. Default value: `REDISSON`

`localCacheEvictionPolicy` - local cache eviction policy. `LFU`, `LRU`, `SOFT`, `WEAK` and `NONE` eviction policies are available.

`localCacheSize` - local cache size. If size is `0` then local cache is unbounded.

`localCacheTimeToLive` - time to live in milliseconds for each map entry in local cache. If value equals to `0` then timeout is not applied.

`localCacheMaxIdleTime` - max idle time in milliseconds for each map entry in local cache. If value equals to `0` then timeout is not applied.

`localCacheSyncStrategy` - local cache sync strategy. `INVALIDATE`, `UPDATE` and `NONE` eviction policies are available.

`redissonConfig` - defines path to redisson config in YAML format

Example cache definitions:

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
