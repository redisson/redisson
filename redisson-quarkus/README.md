# Quarkus extension for Redis

Integrates Redisson with [Quarkus](https://quarkus.io/) framework.  

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

## Usage  

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
    <version>3.22.0</version>
</dependency>
```

Gradle

```groovy
// for Quarkus v1.6.x - v1.13.x
compile 'org.redisson:redisson-quarkus-16:3.22.0'
// for Quarkus v2.x.x
compile 'org.redisson:redisson-quarkus-20:3.22.0'
// for Quarkus v3.x.x
compile 'org.redisson:redisson-quarkus-30:3.22.0'
```

### 2. Add settings into `application.properties` file
  
Config structure is a flat Redisson YAML configuration - 
[single mode](https://github.com/redisson/redisson/wiki/2.-Configuration#262-single-instance-yaml-config-format),
[replicated mode](https://github.com/redisson/redisson/wiki/2.-Configuration#252-replicated-yaml-config-format),
[cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration#242-cluster-yaml-config-format),
[sentinel mode](https://github.com/redisson/redisson/wiki/2.-Configuration#272-sentinel-yaml-config-format),
[proxy mode](https://github.com/redisson/redisson/wiki/2.-Configuration#292-proxy-mode-yaml-config-format)

NOTE: Setting names in camel case should be joined with hyphens (-).

Below is configuration example for Redisson in single mode.
```
quarkus.redisson.single-server-config.address=redis://localhost:6379
quarkus.redisson.single-server-config.password=null
quarkus.redisson.threads=16
quarkus.redisson.netty-threads=32
```

Use `quarkus.redisson.file` setting to specify path to config file.    
    
### 3. Use Redisson

```java
@Inject
RedissonClient redisson;
```

Try __[Redisson PRO](https://redisson.pro)__ with **ultra-fast performance** and **support by SLA**.
