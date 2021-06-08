# Helidon CDI extension for Redis

Integrates Redisson with [Helidon](https://helidon.io/) framework.  

Supports Helidon 1.4.x - 2.3.x  

## Usage  

### 1. Add `redisson-helidon` dependency into your project:  

Maven  

```xml  
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-helidon</artifactId>
    <version>3.16.0</version>
</dependency>
```

Gradle

```groovy
compile 'org.redisson:redisson-helidon:3.16.0'
```

### 2. Add settings into `META-INF/microprofile-config.properties` file

Config structure is a flat Redisson YAML configuration - 
[single mode](https://github.com/redisson/redisson/wiki/2.-Configuration#262-single-instance-yaml-config-format),
[replicated mode](https://github.com/redisson/redisson/wiki/2.-Configuration#252-replicated-yaml-config-format),
[cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration#242-cluster-yaml-config-format),
[sentinel mode](https://github.com/redisson/redisson/wiki/2.-Configuration#272-sentinel-yaml-config-format),
[proxy mode](https://github.com/redisson/redisson/wiki/2.-Configuration#292-proxy-mode-yaml-config-format)

Below is the configuration for Redisson instance named `simple`.
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

Consider __[Redisson PRO](https://redisson.pro)__ version for **ultra-fast performance** and **support by SLA**.
