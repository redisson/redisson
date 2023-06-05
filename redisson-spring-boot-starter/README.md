# Spring Boot Starter

Integrates Redisson with Spring Boot library. Depends on [Spring Data Redis](https://github.com/redisson/redisson/tree/master/redisson-spring-data#spring-data-redis-integration) module.

Supports Spring Boot 1.3.x - 3.0.x

## Usage

### 1. Add `redisson-spring-boot-starter` dependency into your project:

Maven

```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <artifactId>redisson-spring-boot-starter</artifactId>
         <version>3.22.0</version>
     </dependency>
```

Gradle

```groovy
     compile 'org.redisson:redisson-spring-boot-starter:3.22.0'
```


Downgrade `redisson-spring-data` module if necessary to support required Spring Boot version:

|redisson-spring-data<br/>module name|Spring Boot<br/>version|
|----------------------------|-------------------|
|redisson-spring-data-16     |1.3.y              |
|redisson-spring-data-17     |1.4.y              |
|redisson-spring-data-18     |1.5.y              |
|redisson-spring-data-2x     |2.x.y              |
|redisson-spring-data-3x     |3.x.y              |

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
    connectTimeout:
    clientName:
    cluster:
      nodes:
    sentinel:
      master:
      nodes:
```

Using Redisson config file ([single mode](https://github.com/redisson/redisson/wiki/2.-Configuration#262-single-instance-yaml-config-format),
[replicated mode](https://github.com/redisson/redisson/wiki/2.-Configuration#252-replicated-yaml-config-format),
[cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration#242-cluster-yaml-config-format),
[sentinel mode](https://github.com/redisson/redisson/wiki/2.-Configuration#272-sentinel-yaml-config-format),
[proxy mode](https://github.com/redisson/redisson/wiki/2.-Configuration#292-proxy-mode-yaml-config-format), [multi cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration/#2102-cluster-yaml-config-format))


```yaml
spring:
  redis:
   redisson: 
      file: classpath:redisson.yaml
```

Using Redisson settings ([single mode](https://github.com/redisson/redisson/wiki/2.-Configuration#262-single-instance-yaml-config-format),
[replicated mode](https://github.com/redisson/redisson/wiki/2.-Configuration#252-replicated-yaml-config-format),
[cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration#242-cluster-yaml-config-format),
[sentinel mode](https://github.com/redisson/redisson/wiki/2.-Configuration#272-sentinel-yaml-config-format),
[proxy mode](https://github.com/redisson/redisson/wiki/2.-Configuration#292-proxy-mode-yaml-config-format), [multi cluster mode](https://github.com/redisson/redisson/wiki/2.-Configuration/#210-multi-cluster-mode)):

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

### 3. Available Spring Beans:

- `RedissonClient`  
- `RedissonRxClient`  
- `RedissonReactiveClient`  
- `RedisTemplate`  
- `ReactiveRedisTemplate`  

Try __[Redisson PRO](https://redisson.pro)__ with **ultra-fast performance** and **support by SLA**.
