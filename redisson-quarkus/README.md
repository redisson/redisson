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

`expire-after-write` setting defines time to live of the item stored in the cache  
`expire-after-access` setting defines time to live added to the item after read operation  

```
quarkus.cache.type=redisson

# Default configuration
quarkus.cache.redisson.expire-after-write=5s
quarkus.cache.redisson.expire-after-access=1s

# Configuration for `sampleCache`
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
