## Spring Boot

For Spring Boot usage please refer to [Spring Boot](integration-with-spring.md/#spring-boot-starter) article.

## Micronaut

Redisson integrates with [Micronaut](https://micronaut.io/) framework. It implements [Micronaut Cache](cache-api-implementations.md/#micronaut-cache) and [Micronaut Session](#session).  

Supports Micronaut 2.0.x - 5.x.x

### Usage  

1. **Add `redisson-micronaut` dependency into your project:**

    <div class="grid cards" markdown>

    -   **Community Edition**

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
            <!-- for Micronaut v5.x.x -->
            <artifactId>redisson-micronaut-50</artifactId>
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
        // for Micronaut v5.x.x
        compile 'org.redisson:redisson-micronaut-50:xVERSIONx'
        ```


    -   **Redisson PRO**

        Maven

        ```xml
        <dependency>
            <groupId>pro.redisson</groupId>
            <!-- for Micronaut v2.0.x - v2.5.x -->
            <artifactId>redisson-micronaut-20</artifactId>
            <!-- for Micronaut v3.x.x -->
            <artifactId>redisson-micronaut-30</artifactId>
            <!-- for Micronaut v4.x.x -->
            <artifactId>redisson-micronaut-40</artifactId>
            <!-- for Micronaut v5.x.x -->
            <artifactId>redisson-micronaut-50</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Micronaut v2.0.x - v2.5.x
        compile 'pro.redisson:redisson-micronaut-20:xVERSIONx'
        // for Micronaut v3.x.x
        compile 'pro.redisson:redisson-micronaut-30:xVERSIONx'
        // for Micronaut v4.x.x
        compile 'pro.redisson:redisson-micronaut-40:xVERSIONx'
        // for Micronaut v5.x.x
        compile 'pro.redisson:redisson-micronaut-50:xVERSIONx'
        ```

        [License key configuration](configuration.md/#license-key-configuration)

    </div>
    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
    <br>
    <br>

2. **Add settings into `application.yml` file**

    Config structure is a Redisson YAML configuration - 
    [single mode](configuration.md/#single-yaml-config-format),
    [replicated mode](configuration.md/#replicated-yaml-config-format),
    [cluster mode](configuration.md/#cluster-yaml-config-format),
    [sentinel mode](configuration.md/#sentinel-yaml-config-format),
    [proxy mode](configuration.md/#proxy-mode-yaml-config-format),
    [multi cluster mode](configuration.md/#multi-cluster-yaml-config-format), 
    [multi sentinel mode](configuration.md/#multi-sentinel-yaml-config-format)

    !!! note

        Setting names in camel case should be joined with hyphens (-).

    Config example:
    ```yaml
    redisson:
      single-server-config:
         address: "redis://127.0.0.1:6379"
      threads: 16
      netty-threads: 32
    ```

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

1. **Add `redisson-quarkus` dependency into your project:**  

    <div class="grid cards" markdown>

    -   **Community Edition**

        Maven

        ```xml  
        <dependency>
            <groupId>org.redisson</groupId>
            <!-- for Quarkus v1.6.x - v1.13.x -->
            <artifactId>redisson-quarkus-16</artifactId>
            <!-- for Quarkus v2.x.x -->
            <artifactId>redisson-quarkus-20</artifactId>
            <!-- for Quarkus v3.x.x - v3.29.x -->
            <artifactId>redisson-quarkus-30</artifactId>
            <!-- for Quarkus v3.30.x+ -->
            <artifactId>redisson-quarkus-33</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Quarkus v1.6.x - v1.13.x
        compile 'org.redisson:redisson-quarkus-16:xVERSIONx'
        // for Quarkus v2.x.x
        compile 'org.redisson:redisson-quarkus-20:xVERSIONx'
        // for Quarkus v3.x.x - v3.29.x
        compile 'pro.redisson:redisson-quarkus-30:xVERSIONx'
        // for Quarkus v3.30.x+
        compile 'pro.redisson:redisson-quarkus-33:xVERSIONx'
        ```


    -   **Redisson PRO**

        Maven

        ```xml  
        <dependency>
            <groupId>pro.redisson</groupId>
            <!-- for Quarkus v1.6.x - v1.13.x -->
            <artifactId>redisson-quarkus-16</artifactId>
            <!-- for Quarkus v2.x.x -->
            <artifactId>redisson-quarkus-20</artifactId>
            <!-- for Quarkus v3.x.x - v3.29.x -->
            <artifactId>redisson-quarkus-30</artifactId>
            <!-- for Quarkus v3.30.x+ -->
            <artifactId>redisson-quarkus-33</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Quarkus v1.6.x - v1.13.x
        compile 'pro.redisson:redisson-quarkus-16:xVERSIONx'
        // for Quarkus v2.x.x
        compile 'pro.redisson:redisson-quarkus-20:xVERSIONx'
        // for Quarkus v3.x.x - v3.29.x
        compile 'pro.redisson:redisson-quarkus-30:xVERSIONx'
        // for Quarkus v3.30.x+
        compile 'pro.redisson:redisson-quarkus-33:xVERSIONx'
        ```

        [License key configuration](configuration.md/#license-key-configuration)

    </div>
    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
    <br>
    <br>

2. **Add settings into `application.properties` file**  
  
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
    
3. **Use Redisson**  

    ```java
    @Inject
    RedissonClient redisson;
    ```

### Cache 

For Quarkus Cache usage please refer to [Quarkus Cache](cache-api-implementations.md/#quarkus-cache) article.

## Helidon

Redisson implements [Helidon](https://helidon.io/) CDI extension for Redis. For Helidon 27 it provides Redisson instances through Helidon service registry.  

Supports Helidon 1.4.x - 4.x.x and 27.x.x  

### Usage  

1. **Add `redisson-helidon` dependency into your project:**  

    <div class="grid cards" markdown>

    -   **Community Edition**

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
            <!-- for Helidon v27.x.x -->
            <artifactId>redisson-helidon-270</artifactId>
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
        // for Helidon v27.x.x
        compile 'org.redisson:redisson-helidon-270:xVERSIONx'
        ```


    -   **Redisson PRO**

        Maven

        ```xml  
        <dependency>
            <groupId>pro.redisson</groupId>
            <!-- for Helidon v1.4.x - v2.5.x -->
            <artifactId>redisson-helidon-20</artifactId>
            <!-- for Helidon v3.x.x -->
            <artifactId>redisson-helidon-30</artifactId>
            <!-- for Helidon v4.x.x -->
            <artifactId>redisson-helidon-40</artifactId>
            <!-- for Helidon v27.x.x -->
            <artifactId>redisson-helidon-270</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Helidon v1.4.x - v2.5.x
        compile 'pro.redisson:redisson-helidon-20:xVERSIONx'
        // for Helidon v3.x.x
        compile 'pro.redisson:redisson-helidon-30:xVERSIONx'
        // for Helidon v4.x.x
        compile 'pro.redisson:redisson-helidon-40:xVERSIONx'
        // for Helidon v27.x.x
        compile 'pro.redisson:redisson-helidon-270:xVERSIONx'
        ```

        [License key configuration](configuration.md/#license-key-configuration)

    </div>
    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
    <br>
    <br>

2. **Add settings into `META-INF/microprofile-config.properties` file**  

    For Helidon v27.x.x add them into `application.yaml` file instead.

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

    The same configuration in `application.yaml` file for Helidon v27.x.x:
    ```yaml
    org.redisson.Redisson:
      simple:
        singleServerConfig:
          address: "redis://127.0.0.1:6379"
          connectionPoolSize: 64
        threads: 16
        nettyThreads: 32
    ```

3. **Use Redisson**  

    Helidon v1.4.x - v4.x.x
    ```java
    @Inject
    @Named("simple")
    private RedissonClient redisson;
    ```

    Helidon v27.x.x
    ```java
    @Service.Singleton
    class MyService {

        private final RedissonClient redisson;

        @Service.Inject
        MyService(@Service.Named("simple") RedissonClient redisson) {
            this.redisson = redisson;
        }

    }
    ```

    Applications without dependency injection can get the instance through `Services.getNamed(RedissonClient.class, "simple")`.

	For injection without @Named or @Service.Named annotation use instance name - `default`. 

## Eclipse JNoSQL

Redisson implements [Eclipse JNoSQL](https://www.jnosql.org/) key-value driver. Eclipse JNoSQL implements [Jakarta NoSQL](https://jakarta.ee/specifications/nosql/) and [Jakarta Data](https://jakarta.ee/specifications/data/) specifications.

Supports Eclipse JNoSQL 1.1.x - 1.2.x  

### Usage  

1. **Add `redisson-jnosql` dependency into your project:**  

    <div class="grid cards" markdown>

    -   **Community Edition**

        Maven

        ```xml  
        <dependency>
            <groupId>org.redisson</groupId>
            <!-- for Eclipse JNoSQL v1.1.x - v1.2.x -->
            <artifactId>redisson-jnosql-11</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Eclipse JNoSQL v1.1.x - v1.2.x
        compile 'org.redisson:redisson-jnosql-11:xVERSIONx'
        ```


    -   **Redisson PRO**

        Maven

        ```xml  
        <dependency>
            <groupId>pro.redisson</groupId>
            <!-- for Eclipse JNoSQL v1.1.x - v1.2.x -->
            <artifactId>redisson-jnosql-11</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Eclipse JNoSQL v1.1.x - v1.2.x
        compile 'pro.redisson:redisson-jnosql-11:xVERSIONx'
        ```

        [License key configuration](configuration.md/#license-key-configuration)

    </div>
    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
    <br>
    <br>

    Eclipse JNoSQL dependencies aren't added transitively, so the application defines Eclipse JNoSQL version. Add `org.eclipse.jnosql.mapping:jnosql-mapping-key-value` and `org.eclipse.jnosql.databases:jnosql-database-commons` of the same version and a Jakarta JSON Binding implementation, unless the runtime provides it.

2. **Add settings into `META-INF/microprofile-config.properties` file**  

    Config structure is a flat Redisson YAML configuration with `jnosql.redisson.` prefix - 
    [single mode](configuration.md/#single-yaml-config-format),
    [replicated mode](configuration.md/#replicated-yaml-config-format),
    [cluster mode](configuration.md/#cluster-yaml-config-format),
    [sentinel mode](configuration.md/#sentinel-yaml-config-format),
    [proxy mode](configuration.md/#proxy-mode-yaml-config-format),
    [multi cluster mode](configuration.md/#multi-cluster-yaml-config-format), 
    [multi sentinel mode](configuration.md/#multi-sentinel-yaml-config-format)

    Below is the configuration example for `books` bucket.
    ```
    jnosql.keyvalue.database=books
    jnosql.redisson.singleServerConfig.address=redis://127.0.0.1:6379
    jnosql.redisson.singleServerConfig.connectionPoolSize=64
    jnosql.redisson.threads=16
    jnosql.redisson.nettyThreads=32
    ```

    Redisson YAML configuration can be defined in a file instead. File path or classpath resource with `classpath:` prefix are supported.
    ```
    jnosql.keyvalue.database=books
    jnosql.redisson.file=classpath:redisson.yaml
    ```

    `jnosql.keyvalue.provider=org.redisson.jnosql.RedissonKeyValueConfiguration` setting is required only if other Eclipse JNoSQL key-value drivers are present in classpath.

3. **Use Eclipse JNoSQL**  

    ```java
    @Inject
    private KeyValueTemplate template;

    template.put(book);
    Optional<Book> result = template.get("978-0441013593", Book.class);
    ```

    Jakarta Data repository of key-value database extends `CrudRepository`:
    ```java
    @Repository
    public interface BookRepository extends CrudRepository<Book, String> {
    }

    @Inject
    private BookRepository repository;

    repository.save(book);
    Optional<Book> result = repository.findById("978-0441013593");
    ```

    Each value is stored as JSON under `bucketName:key` name with millisecond precision of time to live. Collections returned by `BucketManagerFactory` are stored under the bucket name. String elements are stored as is, other elements as JSON. This is the same data layout as Eclipse JNoSQL Redis driver uses.

    Without dependency injection `RedissonKeyValueConfiguration` creates `BucketManagerFactory` from Eclipse JNoSQL `Settings`, Redisson `Config` or existing `RedissonClient` instance.
    ```java
    RedissonBucketManagerFactory factory = new RedissonKeyValueConfiguration().apply(redisson);
    BucketManager manager = factory.apply("books");
    ```

    Redisson instance created by the factory is shut down when the factory is closed or when the last `BucketManager` created by the factory is closed. Redisson instance passed to the factory isn't shut down.

    With dependency injection Eclipse JNoSQL creates one factory for `KeyValueTemplate` and repositories and another one for injected `BucketManagerFactory` and collections. Each of them has its own Redisson instance.
