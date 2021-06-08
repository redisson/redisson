# Spring Data Redis integration

Integrates Redisson with Spring Data Redis library. Implements Spring Data's `RedisConnectionFactory` and `ReactiveRedisConnectionFactory` interfaces and allows to interact with Redis through `RedisTemplate` or `ReactiveRedisTemplate` object.

Supports Spring Data Redis 1.6.x - 2.4.x

## Usage

### 1. Add `redisson-spring-data` dependency into your project:

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
         <version>3.15.6</version>
     </dependency>
```

Gradle

```groovy
     // for Spring Data Redis v.1.6.x
     compile 'org.redisson:redisson-spring-data-16:3.15.6'
     // for Spring Data Redis v.1.7.x
     compile 'org.redisson:redisson-spring-data-17:3.15.6'
     // for Spring Data Redis v.1.8.x
     compile 'org.redisson:redisson-spring-data-18:3.15.6'
     // for Spring Data Redis v.2.0.x
     compile 'org.redisson:redisson-spring-data-20:3.15.6'
     // for Spring Data Redis v.2.1.x
     compile 'org.redisson:redisson-spring-data-21:3.15.6'
     // for Spring Data Redis v.2.2.x
     compile 'org.redisson:redisson-spring-data-22:3.15.6'
     // for Spring Data Redis v.2.3.x
     compile 'org.redisson:redisson-spring-data-23:3.15.6'
     // for Spring Data Redis v.2.4.x
     compile 'org.redisson:redisson-spring-data-24:3.15.6'
```

### 2. Register `RedissonConnectionFactory` in Spring context

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
Consider __[Redisson PRO](https://redisson.pro)__ version for **ultra-fast performance** and **support by SLA**.
