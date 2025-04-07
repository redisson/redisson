1. Add dependency

    <div class="grid cards" markdown>

    -   **Redisson PRO**

        Maven

        ```xml
        <dependency>
           <groupId>pro.redisson</groupId>
           <artifactId>redisson</artifactId>
           <version>xVERSIONx</version>
        </dependency>  
        ```

        Gradle

        ```groovy
        compile 'pro.redisson:redisson:xVERSIONx'
        ```

    -   **Community Edition**

        Maven

        ```xml
        <dependency>
           <groupId>org.redisson</groupId>
           <artifactId>redisson</artifactId>
           <version>xVERSIONx</version>
        </dependency>  
        ```

        Gradle

        ```groovy
        compile 'org.redisson:redisson:xVERSIONx'
        ```

    </div>

    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
    <br>
    <br>    

2. Start development

    * Create config object.  
    Use one of supported modes: ([single mode](configuration.md/#single-mode),
    [replicated mode](configuration.md/#replicated-mode),
    [cluster mode](configuration.md/#cluster-mode),
    [sentinel mode](configuration.md/#sentinel-mode),
    [proxy mode](configuration.md/#proxy-mode),
    [multi cluster mode](configuration.md/#multi-cluster-mode), 
    [multi sentinel mode](configuration.md/#multi-sentinel-mode))
       ```java
       Config config = new Config();
       config.useClusterServers()
             // use "redis://" for Redis connection
             // use "valkey://" for Valkey connection
             // use "valkeys://" for Valkey SSL connection
             // use "rediss://" for Redis SSL connection
             .addNodeAddress("redis://127.0.0.1:7181");

       // or read config from file
       config = Config.fromYAML(new File("config-file.yaml")); 
       ```

    * Create Redisson instance.
       ```java
       // Sync and Async API
       RedissonClient redisson = Redisson.create(config);
       
       // Reactive API
       RedissonReactiveClient redissonReactive = redisson.reactive();
       
       // RxJava3 API
       RedissonRxClient redissonRx = redisson.rxJava();
       ```

    * Get Redis or Valkey based object or service.
       ```java
       // java.util.concurrent.ConcurrentMap
       
       RMap<MyKey, MyValue> map = redisson.getMap("myMap");
       
       RMapReactive<MyKey, MyValue> mapReactive = redissonReactive.getMap("myMap");
       
       RMapRx<MyKey, MyValue> mapRx = redissonRx.getMap("myMap");
       
       // client side caching
       
       RLocalCachedMap<MyKey, MyValue> map = redisson.getLocalCachedMap(LocalCachedMapOptions.<MyKey, MyValue>name("myMap"));


       // java.util.concurrent.locks.Lock
    
       RLock lock = redisson.getLock("myLock");
    
       RLockReactive lockReactive = redissonReactive.getLock("myLock");
    
       RLockRx lockRx = redissonRx.getLock("myLock");


       // java.util.concurrent.ExecutorService
    
       RExecutorService executor = redisson.getExecutorService("myExecutorService");


       // over 50 Redis or Valkey based Java objects and services ...
       ```

More code examples can be found [here](https://github.com/redisson/redisson-examples).