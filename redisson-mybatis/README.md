# Redis based MyBatis Cache implementation

Implements [MyBatis Cache](https://mybatis.org/mybatis-3/sqlmap-xml.html#cache) provider based on Redis.  

Compatible with MyBatis 3.0.0+

Redisson provides various MyBatis Cache implementations including those with features below:

**local cache** - so called `near cache`, which is useful for use cases when Hibernate Cache used mostly for read operations and/or network roundtrips are undesirable. It caches Map entries on Redisson side and executes read operations up to **5x faster** in comparison with common implementation. All local caches with the same name connected to the same pub/sub channel which is used for messaging between them. In particular to send entity update or entity invalidate event.

**data partitioning** - data partitioning in cluster mode. It allows to scale available memory, read/write operations and entry eviction process for individual Hibernate Cache instance in Redis cluster.

Below is the list of all available implementations with local cache and/or data partitioning support:

|Class name | Local cache <br/> support<br/>(up to 45x faster) | Data partitioning <br/> support |
| ------------- | ------------- | ------------|
|RedissonCache<br/>&nbsp; | No | No |
|RedissonLocalCachedCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro) edition</i></sub>  | **Yes** | No |
|RedissonClusteredCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro) edition</i></sub> | No | **Yes** |
|RedissonClusteredLocalCachedCache<br/><sub><i>available only in [Redisson PRO](http://redisson.pro) edition</i></sub> | **Yes** | **Yes** |

## Hibernate Cache Usage

### 1. Add `redisson-mybatis` dependency into your project:

Maven

```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <artifactId>redisson-mybatis</artifactId>
         <version>3.11.7</version>
     </dependency>
```

Gradle

```groovy
     compile 'org.redisson:redisson-mybatis:3.11.7'
```

### 2. Specify MyBatis cache settings

Redisson allows to define follow settings per Cache instance:

`timeToLive` - defines time to live per cache entry

`maxIdleTime` - defines max idle time per cache entry

`maxSize` - defines max size of entries amount stored in Redis

`localCacheEvictionPolicy` - local cache eviction policy. `LFU`, `LRU`, `SOFT`, `WEAK` and `NONE` eviction policies are available.

`localCacheSize` - local cache size. If size is `0` then local cache is unbounded.

`localCacheTimeToLive` - time to live in milliseconds for each map entry in local cache. If value equals to `0` then timeout is not applied.

`localCacheMaxIdleTime` - max idle time in milliseconds for each map entry in local cache. If value equals to `0` then timeout is not applied.

`localCacheSyncStrategy` - local cache sync strategy. `INVALIDATE`, `UPDATE` and `NONE` eviction policies are available.

`redissonConfig` - defines path to redisson config in YAML format

Examples:

```xml
<cache type="org.redisson.hibernate.RedissonCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<!-- or -->
<cache type="org.redisson.hibernate.RedissonLocalCachedCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<!-- or -->
<cache type="org.redisson.hibernate.RedissonClusteredCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>

<!-- or -->
<cache type="org.redisson.hibernate.RedissonClusteredLocalCachedCache">
  <property name="timeToLive" value="200000"/>
  <property name="maxIdleTime" value="100000"/>
  <property name="maxSize" value="100000"/>
  <property name="redissonConfig" value="redisson.yaml"/>
</cache>
```
