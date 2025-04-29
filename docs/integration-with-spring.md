## Spring Boot Starter

Integrates Redisson with Spring Boot library. Depends on [Spring Data Redis](#spring-data-redis) module.

Supports Spring Boot 1.3.x - 3.4.x

### Usage

1. **Add `redisson-spring-boot-starter` dependency into your project:**

    <div class="grid cards" markdown>

    -   **Redisson PRO**

        Maven

        ```xml  
        <dependency>
           <groupId>pro.redisson</groupId>
           <artifactId>redisson-spring-boot-starter</artifactId>
           <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        compile 'pro.redisson:redisson-spring-boot-starter:xVERSIONx'
        ```

    -   **Community Edition**

        Maven

        ```xml  
        <dependency>
           <groupId>org.redisson</groupId>
           <artifactId>redisson-spring-boot-starter</artifactId>
           <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        compile 'org.redisson:redisson-spring-boot-starter:xVERSIONx'
        ```

    </div>

    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
    <br>
    <br>    

     `redisson-spring-boot-starter` depends on `redisson-spring-data` module compatible with the latest version of Spring Boot. Downgrade `redisson-spring-data` module if necessary to support previous Spring Boot versions:
    
     |redisson-spring-data<br/>module name|Spring Boot<br/>version|
     |----------------------------|-------------------|
     |redisson-spring-data-16     |1.3.y              |
     |redisson-spring-data-17     |1.4.y              |
     |redisson-spring-data-18     |1.5.y              |
     |redisson-spring-data-2x     |2.x.y              |
     |redisson-spring-data-3x     |3.x.y              |
    
     For Gradle, you can downgrade to `redisson-spring-data-27` this way:
    
     ```groovy
     implementation ("org.redisson:redisson-spring-boot-starter:xVERSIONx") {
        exclude group: 'org.redisson', module: 'redisson-spring-data-34'
     }
     implementation "org.redisson:redisson-spring-data-27:xVERSIONx"
     ```
    
     For Maven, you can downgrade to `redisson-spring-data-27` this way:
    
     ```xml
     <dependencies>
         <dependency>
             <groupId>org.redisson</groupId>
             <artifactId>redisson-spring-boot-starter</artifactId>
             <version>xVERSIONx</version>
             <exclusions>
                 <exclusion>
                     <groupId>org.redisson</groupId>
                     <artifactId>redisson-spring-data-34</artifactId>
                 </exclusion>
             </exclusions>
         </dependency>
    
         <dependency>
             <groupId>org.redisson</groupId>
             <artifactId>redisson-spring-data-27</artifactId>
             <version>xVERSIONx</version>
         </dependency>
     </dependencies>
     ```

2. **Add settings into `application.settings` file:**

     Using common Spring Boot 3.x+ settings:

     ```yaml
     spring:
       data:
         redis:
           database: 
           host:
           port:
           password:
           ssl: 
           timeout:
           connectTimeout:
           clientName:
           cluster:
             nodes:
           sentinel:
             master:
             nodes:
     ```

    Using common Spring Boot up to 2.7.x settings:

    ```yaml
    spring:
      redis:
        database: 
        host:
        port:
        password:
        ssl: 
        timeout:
        connectTimeout:
        clientName:
        cluster:
          nodes:
        sentinel:
          master:
          nodes:
    ```


    Using Redisson config file: 
    ([single mode](configuration.md/#single-yaml-config-format),
    [replicated mode](configuration.md/#replicated-yaml-config-format),
    [cluster mode](configuration.md/#cluster-yaml-config-format),
    [sentinel mode](configuration.md/#sentinel-yaml-config-format),
    [proxy mode](configuration.md/#proxy-mode-yaml-config-format),
    [multi cluster mode](configuration.md/#multi-cluster-yaml-config-format), 
    [multi sentinel mode](configuration.md/#multi-sentinel-yaml-config-format))


    ```yaml
    spring:
      redis:
       redisson: 
          file: classpath:redisson.yaml
    ```
    
    Using Redisson settings: 
    ([single mode](configuration.md/#single-settings),
    [replicated mode](configuration.md/#replicated-settings),
    [cluster mode](configuration.md/#cluster-settings),
    [sentinel mode](configuration.md/#sentinel-settings),
    [proxy mode](configuration.md/#proxy-mode-settings),
    [multi cluster mode](configuration.md/#multi-cluster-settings), 
    [multi sentinel mode](configuration.md/#multi-sentinel-settings))
    
    ```yaml
    spring:
      redis:
       redisson: 
          config: |
            clusterServersConfig:
              idleConnectionTimeout: 10000
              connectTimeout: 10000
              timeout: 3000
              retryAttempts: 3
              retryInterval: 1500
              failedSlaveReconnectionInterval: 3000
              failedSlaveCheckInterval: 60000
              password: null
              subscriptionsPerConnection: 5
              clientName: null
              loadBalancer: !<org.redisson.connection.balancer.RoundRobinLoadBalancer> {}
              subscriptionConnectionMinimumIdleSize: 1
              subscriptionConnectionPoolSize: 50
              slaveConnectionMinimumIdleSize: 24
              slaveConnectionPoolSize: 64
              masterConnectionMinimumIdleSize: 24
              masterConnectionPoolSize: 64
              readMode: "SLAVE"
              subscriptionMode: "SLAVE"
              nodeAddresses:
              - "redis://127.0.0.1:7004"
              - "redis://127.0.0.1:7001"
              - "redis://127.0.0.1:7000"
              scanInterval: 1000
              pingConnectionInterval: 0
              keepAlive: false
              tcpNoDelay: false
            threads: 16
            nettyThreads: 32
            codec: !<org.redisson.codec.Kryo5Codec> {}
            transportMode: "NIO"
    
    ```

3. **Available Spring Beans:**

     - `RedissonClient`  
     - `RedissonRxClient`  
     - `RedissonReactiveClient`  
     - `RedisTemplate`  
     - `ReactiveRedisTemplate`  
     - `ReactiveRedisOperations`  

### FAQ

**Q: How to replace Netty version brought by Spring Boot?**

You need to define netty version in properties section of your Maven project.

```xml
    <properties>
          <netty.version>4.1.107.Final</netty.version> 
    </properties>
```

**Q: How to disable Redisson?**

You may not have Redis or Valkey in some environments. In this case Redisson can be disabled:  

- Using Annotations  
Spring Boot 2.7+
```java
@SpringBootApplication
@EnableAutoConfiguration(exclude = {
    RedissonAutoConfigurationV2.class})
public class Application {
   
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```
Spring Boot up to 2.6
```java
@SpringBootApplication
@EnableAutoConfiguration(exclude = {
    RedissonAutoConfiguration.class})
public class Application {
   
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```
- Using application.yml file  
Spring Boot 2.7+
```yaml
spring:
  autoconfigure:
    exclude:
      - org.redisson.spring.starter.RedissonAutoConfigurationV2
```
Spring Boot up to 2.6
```yaml
spring:
  autoconfigure:
    exclude:
      - org.redisson.spring.starter.RedissonAutoConfiguration
```

{% include 'cache/Spring-cache.md' %}

## Spring Session
Please note that Redis or Valkey `notify-keyspace-events` setting should contain `Exg` letters to make Spring Session integration work.

Ensure you have Spring Session library in your classpath, add it if necessary:  

**Maven**

```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-core</artifactId>
    <version>3.4.1</version>
</dependency>

<dependency>
   <groupId>org.redisson</groupId>
   <artifactId>redisson-spring-data-34</artifactId>
   <version>xVERSIONx</version>
</dependency>
```

**Gradle**

```gradle
compile 'org.springframework.session:spring-session-core:3.4.1'

compile 'org.redisson:redisson-spring-data-34:xVERSIONx'
```

### Spring Http Session configuration

Add configuration class which extends `AbstractHttpSessionApplicationInitializer` class:
   ```java
   @Configuration
   @EnableRedisHttpSession
   public class SessionConfig extends AbstractHttpSessionApplicationInitializer { 

        @Bean
        public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
            return new RedissonConnectionFactory(redisson);
        }

        @Bean(destroyMethod = "shutdown")
        public RedissonClient redisson(@Value("classpath:/redisson.yaml") Resource configFile) throws IOException {
           Config config = Config.fromYAML(configFile.getInputStream());
           return Redisson.create(config);
        }

   }
   ```

### Spring WebFlux’s Session configuration

Add configuration class which extends `AbstractReactiveWebInitializer` class:
   ```java
   @Configuration
   @EnableRedisWebSession
   public class SessionConfig extends AbstractReactiveWebInitializer { 

        @Bean
        public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
            return new RedissonConnectionFactory(redisson);
        }

        @Bean(destroyMethod = "shutdown")
        public RedissonClient redisson(@Value("classpath:/redisson.yaml") Resource configFile) throws IOException {
           Config config = Config.fromYAML(configFile.getInputStream());
           return Redisson.create(config);
        }
   }
   ```

### Spring Boot configuration

1. Add Spring Session Data Redis library in classpath:  
    Maven:
    ```xml
    <dependency>
      <groupId>org.springframework.session</groupId>
      <artifactId>spring-session-data-redis</artifactId>
      <version>3.2.1</version>
    </dependency>
    ```
    Gradle:
    ```gradle
    compile 'org.springframework.session:spring-session-data-redis:3.4.1'  
    ```
2. Add Redisson Spring Data Redis library in classpath:  

    <div class="grid cards" markdown>

    -   **Redisson PRO**

        Maven

        ```xml  
        <dependency>
           <groupId>pro.redisson</groupId>
           <artifactId>redisson-spring-data-34</artifactId>
           <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        compile 'pro.redisson:redisson-spring-data-34:xVERSIONx'
        ```

    -   **Community Edition**

        Maven

        ```xml  
        <dependency>
           <groupId>org.redisson</groupId>
           <artifactId>redisson-spring-data-34</artifactId>
           <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        compile 'org.redisson:redisson-spring-data-34:xVERSIONx'
        ```

    </div>

    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
    <br>
    <br>    

3. Define follow properties in spring-boot settings  
    ```
    spring.session.store-type=redis
    spring.redis.redisson.file=classpath:redisson.yaml
    spring.session.timeout.seconds=900
    ```

## Spring Transaction Manager

Redisson provides implementation of both `org.springframework.transaction.PlatformTransactionManager` and `org.springframework.transaction.ReactiveTransactionManager` interfaces to participant in Spring transactions. See also [Transactions](Transactions.md) section.

### Spring Transaction Management

```java
@Configuration
@EnableTransactionManagement
public class RedissonTransactionContextConfig {
    
    @Bean
    public TransactionalBean transactionBean() {
        return new TransactionalBean();
    }
    
    @Bean
    public RedissonTransactionManager transactionManager(RedissonClient redisson) {
        return new RedissonTransactionManager(redisson);
    }
    
    @Bean(destroyMethod="shutdown")
    public RedissonClient redisson(@Value("classpath:/redisson.yaml") Resource configFile) throws IOException {
         Config config = Config.fromYAML(configFile.getInputStream());
        return Redisson.create(config);
    }
    
}


public class TransactionalBean {

    @Autowired
    private RedissonTransactionManager transactionManager;

    @Transactional
    public void commitData() {
        RTransaction transaction = transactionManager.getCurrentTransaction();
        RMap<String, String> map = transaction.getMap("test1");
        map.put("1", "2");
    }

}
```

### Reactive Spring Transaction Management

```java
@Configuration
@EnableTransactionManagement
public class RedissonReactiveTransactionContextConfig {
    
    @Bean
    public TransactionalBean transactionBean() {
        return new TransactionalBean();
    }
    
    @Bean
    public ReactiveRedissonTransactionManager transactionManager(RedissonReactiveClient redisson) {
        return new ReactiveRedissonTransactionManager(redisson);
    }
    
    @Bean(destroyMethod="shutdown")
    public RedissonReactiveClient redisson(@Value("classpath:/redisson.yaml") Resource configFile) throws IOException {
         Config config = Config.fromYAML(configFile.getInputStream());
        return Redisson.createReactive(config);
    }
    
}

public class TransactionalBean {

    @Autowired
    private ReactiveRedissonTransactionManager transactionManager;

    @Transactional
    public Mono<Void> commitData() {
        Mono<RTransactionReactive> transaction = transactionManager.getCurrentTransaction();
        return transaction.flatMap(t -> {
            RMapReactive<String, String> map = t.getMap("test1");
            return map.put("1", "2");
        }).then();
    }

}
```

## Spring Cloud Stream

_This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition._

Redisson implements Spring Cloud Stream integration based on the reliable Stream structure for message delivery. To use Redis or Valkey binder with Redisson you need to add [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream) Binder library in classpath:  

Maven:
```xml
<dependency>
    <groupId>pro.redisson</groupId>
    <artifactId>spring-cloud-stream-binder-redisson</artifactId>
    <version>xVERSIONx</version>
</dependency>
```
Gradle:
```gradle
compile 'pro.redisson:spring-cloud-stream-binder-redisson:xVERSIONx'  
```

Compatible with Spring versions below.

Spring Cloud Stream | Spring Cloud | Spring Boot
-- | -- | --
4.2.x | 2024.0.x | 3.4.x
4.1.x | 2023.0.x | 3.0.x - 3.3.x
4.0.x | 2022.0.x | 3.0.x - 3.3.x
3.2.x | 2021.0.x | 2.6.x, 2.7.x (Starting with 2021.0.3 of Spring Cloud)
3.1.x | 2020.0.x | 2.4.x, 2.5.x (Starting with 2020.0.3 of Spring Cloud)

### Receiving messages

Register the input binder (an event sink) for receiving messages as follows:

```java
@Bean
public Consumer<MyObject> receiveMessage() {
  return obj -> {
     // consume received object ...
  };
}
```

Define channel id in the configuration file `application.properties`. Example for `receiveMessage` bean defined above connected to `my-channel` channel:

```
spring.cloud.stream.bindings.receiveMessage-in-0.destination=my-channel
```

### Publishing messages

- Using an output binder

    Register the output binder (an event source) for publishing messages as follows:

    ```java
    @Bean
    public Supplier<MyObject> feedSupplier() {
        return () -> {
               // ...
               return new MyObject();
        };
    }
    ```



- Using org.springframework.cloud.stream.function.StreamBridge object

   ```java
   StreamBridge bridge;
   
   bridge.send("feedSupplier-out-0", new MyObject());
   ```



Define channel id in the configuration file `application.properties`. Example for `feedSupplier` bean defined above connected to `my-channel` channel:

```
spring.cloud.stream.bindings.feedSupplier-out-0.destination=my-channel
spring.cloud.stream.bindings.feedSupplier-out-0.producer.useNativeEncoding=true
```

## Spring Data Redis

Integrates Redisson with Spring Data Redis library. Implements Spring Data's `RedisConnectionFactory` and `ReactiveRedisConnectionFactory` interfaces and allows to interact with Redis or Valkey through `RedisTemplate`, `ReactiveRedisTemplate` or `ReactiveRedisOperations` object.

### Usage
1. Add `redisson-spring-data` dependency into your project:  

    <div class="grid cards" markdown>

    -   **Redisson PRO**

        Maven

        ```xml
        <dependency>
            <groupId>pro.redisson</groupId>
            <!-- for Spring Data Redis v.1.6.x -->
            <artifactId>redisson-spring-data-16</artifactId>
            <!-- for Spring Data Redis v.1.7.x -->
            <artifactId>redisson-spring-data-17</artifactId>
            <!-- for Spring Data Redis v.1.8.x -->
            <artifactId>redisson-spring-data-18</artifactId>
            <!-- for Spring Data Redis v.2.0.x -->
            <artifactId>redisson-spring-data-20</artifactId>
            <!-- for Spring Data Redis v.2.1.x -->
            <artifactId>redisson-spring-data-21</artifactId>
            <!-- for Spring Data Redis v.2.2.x -->
            <artifactId>redisson-spring-data-22</artifactId>
            <!-- for Spring Data Redis v.2.3.x -->
            <artifactId>redisson-spring-data-23</artifactId>
            <!-- for Spring Data Redis v.2.4.x -->
            <artifactId>redisson-spring-data-24</artifactId>
            <!-- for Spring Data Redis v.2.5.x -->
            <artifactId>redisson-spring-data-25</artifactId>
            <!-- for Spring Data Redis v.2.6.x -->
            <artifactId>redisson-spring-data-26</artifactId>
            <!-- for Spring Data Redis v.2.7.x -->
            <artifactId>redisson-spring-data-27</artifactId>
            <!-- for Spring Data Redis v.3.0.x -->
            <artifactId>redisson-spring-data-30</artifactId>
            <!-- for Spring Data Redis v.3.1.x -->
            <artifactId>redisson-spring-data-31</artifactId>
            <!-- for Spring Data Redis v.3.2.x -->
            <artifactId>redisson-spring-data-32</artifactId>
            <!-- for Spring Data Redis v.3.3.x -->
            <artifactId>redisson-spring-data-33</artifactId>
            <!-- for Spring Data Redis v.3.4.x -->
            <artifactId>redisson-spring-data-34</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Spring Data Redis v.1.6.x
        compile 'pro.redisson:redisson-spring-data-16:xVERSIONx'
        // for Spring Data Redis v.1.7.x
        compile 'pro.redisson:redisson-spring-data-17:xVERSIONx'
        // for Spring Data Redis v.1.8.x
        compile 'pro.redisson:redisson-spring-data-18:xVERSIONx'
        // for Spring Data Redis v.2.0.x
        compile 'pro.redisson:redisson-spring-data-20:xVERSIONx'
        // for Spring Data Redis v.2.1.x
        compile 'pro.redisson:redisson-spring-data-21:xVERSIONx'
        // for Spring Data Redis v.2.2.x
        compile 'pro.redisson:redisson-spring-data-22:xVERSIONx'
        // for Spring Data Redis v.2.3.x
        compile 'pro.redisson:redisson-spring-data-23:xVERSIONx'
        // for Spring Data Redis v.2.4.x
        compile 'pro.redisson:redisson-spring-data-24:xVERSIONx'
        // for Spring Data Redis v.2.5.x
        compile 'pro.redisson:redisson-spring-data-25:xVERSIONx'
        // for Spring Data Redis v.2.6.x
        compile 'pro.redisson:redisson-spring-data-26:xVERSIONx'
        // for Spring Data Redis v.2.7.x
        compile 'pro.redisson:redisson-spring-data-27:xVERSIONx'
        // for Spring Data Redis v.3.0.x
        compile 'pro.redisson:redisson-spring-data-30:xVERSIONx'
        // for Spring Data Redis v.3.1.x
        compile 'pro.redisson:redisson-spring-data-31:xVERSIONx'
        // for Spring Data Redis v.3.2.x
        compile 'pro.redisson:redisson-spring-data-32:xVERSIONx'
        // for Spring Data Redis v.3.3.x
        compile 'pro.redisson:redisson-spring-data-33:xVERSIONx'
        // for Spring Data Redis v.3.4.x
        compile 'pro.redisson:redisson-spring-data-34:xVERSIONx'
        ```

    -   **Community Edition**

        Maven

        ```xml
        <dependency>
            <groupId>org.redisson</groupId>
            <!-- for Spring Data Redis v.1.6.x -->
            <artifactId>redisson-spring-data-16</artifactId>
            <!-- for Spring Data Redis v.1.7.x -->
            <artifactId>redisson-spring-data-17</artifactId>
            <!-- for Spring Data Redis v.1.8.x -->
            <artifactId>redisson-spring-data-18</artifactId>
            <!-- for Spring Data Redis v.2.0.x -->
            <artifactId>redisson-spring-data-20</artifactId>
            <!-- for Spring Data Redis v.2.1.x -->
            <artifactId>redisson-spring-data-21</artifactId>
            <!-- for Spring Data Redis v.2.2.x -->
            <artifactId>redisson-spring-data-22</artifactId>
            <!-- for Spring Data Redis v.2.3.x -->
            <artifactId>redisson-spring-data-23</artifactId>
            <!-- for Spring Data Redis v.2.4.x -->
            <artifactId>redisson-spring-data-24</artifactId>
            <!-- for Spring Data Redis v.2.5.x -->
            <artifactId>redisson-spring-data-25</artifactId>
            <!-- for Spring Data Redis v.2.6.x -->
            <artifactId>redisson-spring-data-26</artifactId>
            <!-- for Spring Data Redis v.2.7.x -->
            <artifactId>redisson-spring-data-27</artifactId>
            <!-- for Spring Data Redis v.3.0.x -->
            <artifactId>redisson-spring-data-30</artifactId>
            <!-- for Spring Data Redis v.3.1.x -->
            <artifactId>redisson-spring-data-31</artifactId>
            <!-- for Spring Data Redis v.3.2.x -->
            <artifactId>redisson-spring-data-32</artifactId>
            <!-- for Spring Data Redis v.3.3.x -->
            <artifactId>redisson-spring-data-33</artifactId>
            <!-- for Spring Data Redis v.3.4.x -->
            <artifactId>redisson-spring-data-34</artifactId>
            <version>xVERSIONx</version>
        </dependency>
        ```

        Gradle

        ```groovy
        // for Spring Data Redis v.1.6.x
        compile 'org.redisson:redisson-spring-data-16:xVERSIONx'
        // for Spring Data Redis v.1.7.x
        compile 'org.redisson:redisson-spring-data-17:xVERSIONx'
        // for Spring Data Redis v.1.8.x
        compile 'org.redisson:redisson-spring-data-18:xVERSIONx'
        // for Spring Data Redis v.2.0.x
        compile 'org.redisson:redisson-spring-data-20:xVERSIONx'
        // for Spring Data Redis v.2.1.x
        compile 'org.redisson:redisson-spring-data-21:xVERSIONx'
        // for Spring Data Redis v.2.2.x
        compile 'org.redisson:redisson-spring-data-22:xVERSIONx'
        // for Spring Data Redis v.2.3.x
        compile 'org.redisson:redisson-spring-data-23:xVERSIONx'
        // for Spring Data Redis v.2.4.x
        compile 'org.redisson:redisson-spring-data-24:xVERSIONx'
        // for Spring Data Redis v.2.5.x
        compile 'org.redisson:redisson-spring-data-25:xVERSIONx'
        // for Spring Data Redis v.2.6.x
        compile 'org.redisson:redisson-spring-data-26:xVERSIONx'
        // for Spring Data Redis v.2.7.x
        compile 'org.redisson:redisson-spring-data-27:xVERSIONx'
        // for Spring Data Redis v.3.0.x
        compile 'org.redisson:redisson-spring-data-30:xVERSIONx'
        // for Spring Data Redis v.3.1.x
        compile 'org.redisson:redisson-spring-data-31:xVERSIONx'
        // for Spring Data Redis v.3.2.x
        compile 'org.redisson:redisson-spring-data-32:xVERSIONx'
        // for Spring Data Redis v.3.3.x
        compile 'org.redisson:redisson-spring-data-33:xVERSIONx'
        // for Spring Data Redis v.3.4.x
        compile 'org.redisson:redisson-spring-data-34:xVERSIONx'
        ```

    </div>

    [Redisson PRO vs. Community Edition ➜](https://redisson.pro/feature-comparison.html)
	<br>
    <br>    


2. Register `RedissonConnectionFactory` in Spring context:  
   ```java
   @Configuration
   public class RedissonSpringDataConfig {
   
      @Bean
      public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
          return new RedissonConnectionFactory(redisson);
      }
   
      @Bean(destroyMethod = "shutdown")
      public RedissonClient redisson(@Value("classpath:/redisson.yaml") Resource configFile) throws IOException {
          Config config = Config.fromYAML(configFile.getInputStream());
          return Redisson.create(config);
      }
   
   }
   ```