# Redis based MyBatis Cache implementation

Implements [MyBatis Cache](https://mybatis.org/mybatis-3/sqlmap-xml.html#cache) based on Redis.  

Compatible with MyBatis 3.0.0+

Redisson provides various MyBatis Cache implementations including those with features below:

**local cache** - so called `near cache`, which is useful for use cases when MyBatis Cache used mostly for read operations and/or network roundtrips are undesirable. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. All local caches with the same name connected to the same pub/sub channel which is used for messaging between them. In particular to send entity update or entity invalidate event.

**data partitioning** - allows to scale available memory, read/write operations and entry eviction process for individual MyBatis Cache instance in Redis cluster.

Below is the list of all available implementations with local cache and/or data partitioning support:

|Class name | Local cache | Data<br/>partitioning | Ultra-fast read/write |
| ------------- | :-----------: | :----------:| :----------:|
|RedissonCache<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ |
|RedissonCache<br/><sub><i>[Redisson PRO](http://redisson.pro) version</i></sub> | ❌ | ❌ | ✔️ |
|RedissonLocalCachedCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub>  | ✔️ | ❌ | ✔️ |
|RedissonClusteredCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ❌ | ✔️ | ✔️ |
|RedissonClusteredLocalCachedCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro)</i></sub> | ✔️ | ✔️ | ✔️ |

## MyBatis Cache Usage

### 1. Add `redisson-mybatis` dependency into your project:

Maven

```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <artifactId>redisson-mybatis</artifactId>
         <version>3.15.6</version>
     </dependency>
```

Gradle

```groovy
     compile 'org.redisson:redisson-mybatis:3.15.6'
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

Examples:

```xml
<cache type="org.redisson.mybatis.RedissonCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<!-- or -->
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

<!-- or -->
<cache type="org.redisson.mybatis.RedissonClusteredCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<!-- or -->
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
