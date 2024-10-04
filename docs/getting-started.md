**1. Add dependency**

``` python
import tensorflow as tf
```

Maven  

```xml
<dependency>
   <groupId>org.redisson</groupId>
   <artifactId>redisson</artifactId>
   <version>xVERSIONx</version>
</dependency>  
```

Gradle  

```java
compile 'org.redisson:redisson:xVERSIONx'  
```

SBT  

```java
libraryDependencies += "org.redisson" % "redisson" % "xVERSIONx"
```

**2. Start development**

```java
// 1. Create config object
Config config = new Config();
config.useClusterServers()
       // use "rediss://" for SSL connection
      .addNodeAddress("redis://127.0.0.1:7181");

// or read config from file
config = Config.fromYAML(new File("config-file.yaml")); 
```

```java
// 2. Create Redisson instance

// Sync and Async API
RedissonClient redisson = Redisson.create(config);

// Reactive API
RedissonReactiveClient redissonReactive = redisson.reactive();

// RxJava3 API
RedissonRxClient redissonRx = redisson.rxJava();
```

```java
// 3. Get Redis or Valkey based implementation of java.util.concurrent.ConcurrentMap
RMap<MyKey, MyValue> map = redisson.getMap("myMap");

RMapReactive<MyKey, MyValue> mapReactive = redissonReactive.getMap("myMap");

RMapRx<MyKey, MyValue> mapRx = redissonRx.getMap("myMap");
```

```java
// 4. Get Redis or Valkey based implementation of java.util.concurrent.locks.Lock
RLock lock = redisson.getLock("myLock");

RLockReactive lockReactive = redissonReactive.getLock("myLock");

RLockRx lockRx = redissonRx.getLock("myLock");
```

```java
// 4. Get Redis or Valkey based implementation of java.util.concurrent.ExecutorService
RExecutorService executor = redisson.getExecutorService("myExecutorService");

// over 50 Redis or Valkey based Java objects and services ...

```

Upgrade to __[Redisson PRO](https://redisson.pro)__ with **advanced features**.