## Spring Boot

For Spring Boot usage please refer to [Spring Boot](integration-with-spring.md/#spring-boot-starter) article.

## Micronaut

Redisson integrates with [Micronaut](https://micronaut.io/) framework. It implements [Micronaut Cache](cache-api-implementations.md/#micronaut-cache) and [Micronaut Session](#session).  

Supports Micronaut 2.0.x - 4.x.x

### Usage  

**1. Add `redisson-micronaut` dependency into your project:**

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
    <version>xVERSIONx</version>
</dependency>
```

Gradle

```groovy
// for Micronaut v2.0.x - v2.5.x
compile 'org.redisson:redisson-micronaut-20:xVERSIONx'
// for Micronaut v3.x.x
compile 'org.redisson:redisson-micronaut-30:xVERSIONx'
// for Micronaut v4.x.x
compile 'org.redisson:redisson-micronaut-40:xVERSIONx'
```

**2. Add settings into `application.yml` file**

Config structure is a Redisson YAML configuration - 
[single mode](configuration.md/#single-yaml-config-format),
[replicated mode](configuration.md/#replicated-yaml-config-format),
[cluster mode](configuration.md/#cluster-yaml-config-format),
[sentinel mode](configuration.md/#sentinel-yaml-config-format),
[proxy mode](configuration.md/#proxy-mode-yaml-config-format),
[multi cluster mode](configuration.md/#multi-cluster-yaml-config-format), 
[multi sentinel mode](configuration.md/#multi-sentinel-yaml-config-format)

NOTE: Setting names in camel case should be joined with hyphens (-).

Config example:
```yaml
redisson:
  single-server-config:
     address: "redis://127.0.0.1:6379"
  threads: 16
  netty-threads: 32
```

[Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)

### Cache 

For Micronaut Cache usage please refer to [Micronaut Cache](cache-api-implementations.md/#micronaut-cache) article.

### Session

Redisson provides Micronanut [Session](https://docs.micronaut.io/latest/api/io/micronaut/session/Session.html) store implementation. 
Extra settings to [HttpSessionConfiguration](https://docs.micronaut.io/2.5.4/api/io/micronaut/session/http/HttpSessionConfiguration.html) object:

|Setting name|Type|Description|
|------------|----|-----------|
|micronaut.session.http.redisson.enabled|java.lang.Boolean|Enables Session store|
|micronaut.session.http.redisson.key-prefix|java.lang.Integer|Defines string prefix applied to all objects stored in Redis.|
|micronaut.session.http.redisson.codec|java.lang.Class|Data codec applied to cache entries. Default is Kryo5Codec codec.|
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

## Quarkus

Redisson integrates with [Quarkus](https://quarkus.io/) framework and implements [Quarkus Cache](cache-api-implementations.md/#quarkus-cache).  

Supports Quarkus 1.6.x - 3.x.x

??? note "Native image with RemoteService. Click to expand"
    To use RemoteService in native image add **dynamic-proxy.json** and **reflection-config.json** files in `quarkus.native.additional-build-args` setting.

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


### Usage  

**1. Add `redisson-quarkus` dependency into your project:**  

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
    <version>xVERSIONx</version>
</dependency>
```

Gradle  

```groovy
// for Quarkus v1.6.x - v1.13.x
compile 'org.redisson:redisson-quarkus-16:xVERSIONx'
// for Quarkus v2.x.x
compile 'org.redisson:redisson-quarkus-20:xVERSIONx'
// for Quarkus v3.x.x
compile 'org.redisson:redisson-quarkus-30:xVERSIONx'
```

**2. Add settings into `application.properties` file**  
  
Config structure is a flat Redisson YAML configuration - 
[single mode](configuration.md/#single-yaml-config-format),
[replicated mode](configuration.md/#replicated-yaml-config-format),
[cluster mode](configuration.md/#cluster-yaml-config-format),
[sentinel mode](configuration.md/#sentinel-yaml-config-format),
[proxy mode](configuration.md/#proxy-mode-yaml-config-format),
[multi cluster mode](configuration.md/#multi-cluster-yaml-config-format), 
[multi sentinel mode](configuration.md/#multi-sentinel-yaml-config-format)

NOTE: Setting names in camel case should be joined with hyphens (-).

Below is the configuration example for a single Redis or Valkey node setup.
```
quarkus.redisson.single-server-config.address=redis://localhost:6379
quarkus.redisson.single-server-config.password=null
quarkus.redisson.threads=16
quarkus.redisson.netty-threads=32
```

Use `quarkus.redisson.file` setting to specify path to a config file.    
    
**3. Use Redisson**  

```java
@Inject
RedissonClient redisson;
```

Upgrade to __[Redisson PRO](https://redisson.pro/feature-comparison.html)__ with **advanced features**.

### Cache 

For Quarkus Cache usage please refer to [Quarkus Cache](cache-api-implementations.md/#quarkus-cache) article.

## Helidon

Redisson implements [Helidon](https://helidon.io/) CDI extension for Redis.  

Supports Helidon 1.4.x - 4.x.x  

### Usage  

**1. Add `redisson-helidon` dependency into your project:**  

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
    <version>xVERSIONx</version>
</dependency>
```

Gradle  

```groovy
// for Helidon v1.4.x - v2.5.x
compile 'org.redisson:redisson-helidon-20:xVERSIONx'
// for Helidon v3.x.x
compile 'org.redisson:redisson-helidon-30:xVERSIONx'
// for Helidon v4.x.x
compile 'org.redisson:redisson-helidon-40:xVERSIONx'
```

**2. Add settings into `META-INF/microprofile-config.properties` file**  

Config structure is a flat Redisson YAML configuration - 
[single mode](configuration.md/#single-yaml-config-format),
[replicated mode](configuration.md/#replicated-yaml-config-format),
[cluster mode](configuration.md/#cluster-yaml-config-format),
[sentinel mode](configuration.md/#sentinel-yaml-config-format),
[proxy mode](configuration.md/#proxy-mode-yaml-config-format),
[multi cluster mode](configuration.md/#multi-cluster-yaml-config-format), 
[multi sentinel mode](configuration.md/#multi-sentinel-yaml-config-format)

Below is the configuration example for Redisson instance named `simple`.
```
org.redisson.Redisson.simple.singleServerConfig.address=redis://127.0.0.1:6379
org.redisson.Redisson.simple.singleServerConfig.connectionPoolSize=64
org.redisson.Redisson.simple.threads=16
org.redisson.Redisson.simple.nettyThreads=32
```

**3. Use Redisson**  

```java
@Inject
@Named("simple")
private RedissonClient redisson;
```

For injection without @Named annotation use instance name - `default`. 

[Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
