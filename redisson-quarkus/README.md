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
    <version>3.32.0</version>
</dependency>
```

Gradle

```groovy
// for Quarkus v3.x.x
compile 'org.redisson:redisson-quarkus-30-cache:3.32.0'
```

### 2. Add settings into `application.properties` file

Redisson provides various Cache implementations with multiple important features:  

**local cache** - so called `near cache` used to speed up read operations and avoid network roundtrips. It caches Map entries on Redisson side and executes read operations up to **45x faster** in comparison with common implementation. Local cache instances with the same name connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

**data partitioning** - although Map object is cluster compatible its content isn't scaled/partitioned across multiple Redis master nodes in cluster. Data partitioning allows to scale available memory, read/write operations and entry eviction process for individual Map instance in Redis cluster.  

**scripted entry eviction** - allows to define `time to live` or `max idle time` parameters per map entry. Redis hash structure doesn't support eviction thus it's done on Redisson side through a custom scheduled task which removes expired entries using Lua script. Eviction task is started once by getMapCache() method execution per unique object name. So even if instance isn't used and has expired entries it should be get through getMapCache() method to start the eviction process. This leads to extra Redis calls and eviction task per unique map object name.

**advanced entry eviction** - allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task.

**native entry eviction** - allows to define `time to live` parameter per map entry. Doesn't use an entry eviction task. Requires **Redis 7.4+**.

Below is the list of all Cache implementations:  

|`impementation`<br/>setting value | Local<br/>cache | Data<br/>partitioning | Entry<br/>eviction | Ultra-fast<br/>read/write |
| ------------- | :-----------: | :-----------:| :----------:| :---------:|
|standard<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | ❌ | ❌ |
|native<br/><sub><i>open-source version</i></sub> | ❌ | ❌ | **native**| ❌ |
|standard<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | **scripted** | ✔️ |
|native<br/><sub><i>[Redisson PRO](https://redisson.pro) version</i></sub> | ❌ | ❌ | **native**| ✔️ |
|v2<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | **advanced** | ✔️ |
|localcache<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ❌ | **scripted** | ✔️ |
|localcache_v2<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | **advanced** | ✔️ |
|clustered<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ❌ | ✔️ | **scripted** | ✔️ |
|clustered_localcache<br/><sub><i>available only in [Redisson PRO](https://redisson.pro)</i></sub> | ✔️ | ✔️ | **scripted** | ✔️ |

`expire-after-write` setting defines time to live of the item stored in the cache  
`expire-after-access` setting defines time to live added to the item after read operation  
`implementation` setting defines the type of cache used

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
    <version>3.32.0</version>
</dependency>
```

Gradle

```groovy
// for Quarkus v1.6.x - v1.13.x
compile 'org.redisson:redisson-quarkus-16:3.32.0'
// for Quarkus v2.x.x
compile 'org.redisson:redisson-quarkus-20:3.32.0'
// for Quarkus v3.x.x
compile 'org.redisson:redisson-quarkus-30:3.32.0'
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
