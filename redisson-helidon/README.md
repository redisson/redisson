# Helidon CDI extension for Redis

Integrates Redisson with [Helidon](https://helidon.io/) framework.  

Supports Helidon 1.4.x - 4.x.x  

## Usage  

### 1. Add `redisson-helidon` dependency into your project:  

Maven  

```xml  
<dependency>
    <groupId>org.redisson</groupId>
    <!-- for Helidon v1.4.x - v2.5.x -->
    <artifactId>redisson-helidon-20</artifactId>
    <!-- for Helidon v3.x.x -->
    <artifactId>redisson-helidon-30</artifactId>
    <!-- for Helidon v4.x.x -->
    <artifactId>redisson-helidon-40</artifactId>
    <version>3.35.0</version>
</dependency>
```

Gradle

```groovy
// for Helidon v1.4.x - v2.5.x
compile 'org.redisson:redisson-helidon-20:3.35.0'
// for Helidon v3.x.x
compile 'org.redisson:redisson-helidon-30:3.35.0'
// for Helidon v4.x.x
compile 'org.redisson:redisson-helidon-40:3.35.0'
```

### 2. Add settings into `META-INF/microprofile-config.properties` file

Config structure is a flat Redisson YAML configuration - 
[single mode](https://github.com/redisson/redisson/wiki/2.-Configuration#262-single-instance-yaml-config-format),
[replicated mode](https://github.com/redisson/redisson/wiki/2.-Configuration#252-replicated-yaml-config-format),
[cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration#242-cluster-yaml-config-format),
[sentinel mode](https://github.com/redisson/redisson/wiki/2.-Configuration#272-sentinel-yaml-config-format),
[proxy mode](https://github.com/redisson/redisson/wiki/2.-Configuration#292-proxy-mode-yaml-config-format),
[multi cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration/#2102-cluster-yaml-config-format)

Below is the configuration example for Redisson instance named `simple`.
```
org.redisson.Redisson.simple.singleServerConfig.address=redis://127.0.0.1:6379
org.redisson.Redisson.simple.singleServerConfig.connectionPoolSize=64
org.redisson.Redisson.simple.threads=16
org.redisson.Redisson.simple.nettyThreads=32
```

### 3. Use Redisson

```java
@Inject
@Named("simple")
private RedissonClient redisson;
```

For injection without @Named annotation use instance name - `default`. 

Upgrade to __[Redisson PRO](https://redisson.pro)__ with **advanced features**.
