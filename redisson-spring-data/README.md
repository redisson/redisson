Spring Data Redis integration
===

Integrates Redisson with Spring Data Redis library. Provides ability to work with Redis through `RedisTemplate` object.

Supports Spring Data Redis 1.6.x, 1.7.x, 1.8.x, 2.0.x

<sub>Please consider __[Redisson PRO](https://redisson.pro)__ version for advanced features and support by SLA.</sub>

Usage
===

### 1.  Add `redisson-spring-data` dependency into your project:

1. __For JDK 1.8+__  

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
         <version>3.9.1</version>
     </dependency>
     ```
     Gradle

     ```java
     // for Spring Data Redis v.1.6.x
     compile 'org.redisson:redisson-spring-data-16:3.9.1'
     // for Spring Data Redis v.1.7.x
     compile 'org.redisson:redisson-spring-data-17:3.9.1'
     // for Spring Data Redis v.1.8.x
     compile 'org.redisson:redisson-spring-data-18:3.9.1'
     // for Spring Data Redis v.2.0.x
     compile 'org.redisson:redisson-spring-data-20:3.9.1'
     ```  

2. __For JDK 1.6+__  

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
         <version>2.14.1</version>
     </dependency>
     ```
     Gradle

     ```java
     // for Spring Data Redis v.1.6.x
     compile 'org.redisson:redisson-spring-data-16:2.14.1'
     // for Spring Data Redis v.1.7.x
     compile 'org.redisson:redisson-spring-data-17:2.14.1'
     // for Spring Data Redis v.1.8.x
     compile 'org.redisson:redisson-spring-data-18:2.14.1'
     // for Spring Data Redis v.2.0.x
     compile 'org.redisson:redisson-spring-data-20:2.14.1'
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
