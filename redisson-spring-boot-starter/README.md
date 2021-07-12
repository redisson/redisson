# Spring Boot Starter

Integrates Redisson with Spring Boot library. Depends on [Spring Data Redis](https://github.com/redisson/redisson/tree/master/redisson-spring-data#spring-data-redis-integration) module.

Supports Spring Boot 1.3.x - 2.5.x

## Usage

### 1. Add `redisson-spring-boot-starter` dependency into your project:

Maven

```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <artifactId>redisson-spring-boot-starter</artifactId>
         <version>3.16.0</version>
     </dependency>
```

Gradle

```groovy
     compile 'org.redisson:redisson-spring-boot-starter:3.16.0'
```


Downgrade `redisson-spring-data` module if necessary to support required Spring Boot version:

|redisson-spring-data<br/>module name|Spring Boot<br/>version|
|----------------------------|-------------------|
|redisson-spring-data-16     |1.3.x              |
|redisson-spring-data-17     |1.4.x              |
|redisson-spring-data-18     |1.5.x              |
|redisson-spring-data-20     |2.0.x              |
|redisson-spring-data-21     |2.1.x              |
|redisson-spring-data-22     |2.2.x              |
|redisson-spring-data-23     |2.3.x              |
|redisson-spring-data-24     |2.4.x              |
|redisson-spring-data-25     |2.5.x              |

### 2. Add settings into `application.settings` file

Using common spring boot settings:

```yaml
spring:
  redis:
    database: 
    host:
    port:
    password:
    ssl: 
    timeout:
    cluster:
      nodes:
    sentinel:
      master:
      nodes:
```

Using Redisson settings:

```yaml
spring:
  redis:
   redisson: 
      file: classpath:redisson.yaml
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
        codec: !<org.redisson.codec.MarshallingCodec> {}
        transportMode: "NIO"

```

### 3. Available Spring Beans:

- `RedissonClient`  
- `RedissonRxClient`  
- `RedissonReactiveClient`  
- `RedisTemplate`  
- `ReactiveRedisTemplate`  

Consider __[Redisson PRO](https://redisson.pro)__ version for **ultra-fast performance** and **support by SLA**.
