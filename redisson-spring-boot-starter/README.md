Spring Boot Starter
===

Integrates Redisson with Spring Boot library.

Supports Spring Data Redis 1.3.x, 1.4.x, 1.5.x, 2.0.x

Usage
===

### 1.  Add `redisson-spring-boot-starter` dependency into your project:

1. __For JDK 1.8+__  

     Maven
     ```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <artifactId>redisson-spring-boot-starter</artifactId>
         <version>3.8.0</version>
     </dependency>
     ```
     Gradle

     ```java
     compile 'org.redisson:redisson-spring-boot-starter:3.8.0'
     ```  

2. __For JDK 1.6+__  

     Maven
     ```xml
     <dependency>
         <groupId>org.redisson</groupId>
         <artifactId>redisson-spring-boot-starter</artifactId>
         <version>2.13.0</version>
     </dependency>
     ```
     Gradle

     ```java
     compile 'org.redisson:redisson-spring-boot-starter:2.13.0'
     ```  


### 2. Get access to Redisson through spring bean with `RedissonClient` interface
