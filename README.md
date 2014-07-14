##Redisson - distributed and scalable Java data structures on top of Redis server. Advanced Redis client

Use familiar Java data structures with power of [Redis](http://redis.io).

Based on patched version of [lettuce](https://github.com/wg/lettuce) [Redis](http://redis.io) client and [Netty 4](http://netty.io) framework.  
Redis 2.4+ and JDK 1.6+ compatible  

Licensed under the Apache License 2.0.


Features
================================
* Sentinel servers mode: 
    1. automatic master and slave servers discovery
    2. automatic new master server discovery
    3. automatic new slave servers discovery
    4. automatic slave servers offline/online discovery
    5. read data using slave servers, write data using master server
* Master with Slave servers mode: read data using slave servers, write data using master server
* Single server mode: read and write data using single server
* Distributed implementation of `java.util.List`  
* Distributed implementation of `java.util.Set`  
* Distributed implementation of `java.util.SortedSet`  
* Distributed implementation of `java.util.Queue`  
* Distributed implementation of `java.util.Deque`  
* Distributed implementation of `java.util.Map`  
* Distributed implementation of `java.util.concurrent.ConcurrentMap`  
* Distributed implementation of reentrant `java.util.concurrent.locks.Lock`  
* Distributed alternative to the `java.util.concurrent.atomic.AtomicLong`  
* Distributed alternative to the `java.util.concurrent.CountDownLatch`  
* Distributed publish/subscribe messaging via `org.redisson.core.RTopic`  
* Distributed HyperLogLog via `org.redisson.core.RHyperLogLog`  
* Thread-safe implementation
* Supports OSGi  
* With over 110 unit tests  


Recent Releases
================================
####Please Note: trunk is current development branch.

####13-Jul-2014 - version 1.1.3 released
Improvement - RedissonCountDownLatch optimization  
Improvement - RedissonLock optimization  
Fixed - RedissonLock thread-safety  
Fixed - master/slave auth using Sentinel servers  
Fixed - slave down handling using Sentinel servers  

####03-Jul-2014 - version 1.1.2 released
Improvement - RedissonSet.iterator implemented with sscan  
Improvement - RedissonSortedSet.iterator optimization  
Feature - RSortedSet.removeAsync, RSortedSet.addAsync, RSet.removeAsync, RSet.addAsync methods added  
Feature - slave up/down detection in Sentinel servers connection mode  
Feature - new-slave automatic discovery in Sentinel servers connection mode  

####17-June-2014 - version 1.1.1 released
Feature - sentinel servers support  
Fixed - connection leak in `RTopic`  
Fixed - setted password not used in single server connection  

####07-June-2014 - version 1.1.0 released
Feature - master/slave connection management  
Feature - simple set/get object support via `org.redisson.core.RBucket`  
Feature - hyperloglog support via `org.redisson.core.RHyperLogLog`  
Feature - new methods `getAsync`, `putAsync` and `removeAsync` added to `org.redisson.core.RMap`  
Feature - new method `publishAsync` added to `org.redisson.core.RTopic`  
Feature - [Kryo](https://github.com/EsotericSoftware/kryo) codec added (thanks to mathieucarbou)  
__Breaking api change__ - `org.redisson.Config` model changed  
Fixed - score calucaltion algorithm used in `RSortedSet`.  
Fixed - `RMap.put` & `RMap.remove` result consistency fixed.  
Fixed - `RTopic.publish` now returns the number of clients that received the message  
Fixed - reconnection handling (thanks to renzihui)  
Improvement - `org.redisson.core.RTopic` now use lazy apporach for subscribe/unsubscribe  

####04-May-2014 - version 1.0.4 released
Feature - distributed implementation of `java.util.Deque`  
Feature - some objects implements `org.redisson.core.RExpirable`  
Fixed - JsonJacksonCodec lazy init  

####26-Mar-2014 - version 1.0.3 released
Fixed - RedissonAtomicLong state format  
Fixed - Long serialization in JsonJacksonCodec  

####05-Feb-2014 - version 1.0.2 released
Feature - distributed implementation of `java.util.SortedSet`  
Fixed - OSGi compability  

####17-Jan-2014 - version 1.0.1 released
Improvement - forceUnlock, isLocked, isHeldByCurrentThread and getHoldCount methods added to RLock  
Feature - connection load balancer to use multiple Redis servers  
Feature - published in maven central repo  

####11-Jan-2014 - version 1.0.0 released
First stable release.

### Maven 

Include the following to your dependency list:

    <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson</artifactId>
       <version>1.1.1</version>
    </dependency>

Usage examples
================================
####Simple config example
       
        // connects to Redis server 127.0.0.1:6379 by default
        Redisson redisson = Redisson.create();

        ...

        redisson.shutdown();

or with initialization by Config object for single server connection

        Config config = new Config();
        config.useSingleServer()
              .setAddress("127.0.0.1:6379")
              .setConnectionPoolSize(10);

        Redisson redisson = Redisson.create(config);

or master/slave servers connection

        Config config = new Config();
        config.useMasterSlaveConnection()
              .setMasterAddress("127.0.0.1:6379")
              .addSlaveAddress("127.0.0.1:6389", "127.0.0.1:6332", "127.0.0.1:6419")
              .addSlaveAddress("127.0.0.1:6399");

        Redisson redisson = Redisson.create(config);

for sentinel servers connection

        Config config = new Config();
        config.useSentinelConnection()
              .setMasterName("mymaster")
              .addSentinelAddress("127.0.0.1:26389", "127.0.0.1:26379")
              .addSentinelAddress("127.0.0.1:26319";

        Redisson redisson = Redisson.create(config);


####Distributed Map example

        Redisson redisson = Redisson.create();

        ConcurrentMap<String, SomeObject> map = redisson.getMap("anyMap");
        map.put("123", new SomeObject());
        map.putIfAbsent("323", new SomeObject());
        map.remove("123");

        ...

        redisson.shutdown();

####Distributed Set example

        Redisson redisson = Redisson.create();

        Set<SomeObject> set = redisson.getSet("anySet");
        set.add(new SomeObject());
        set.remove(new SomeObject());

        ...

        redisson.shutdown();

####Distributed List example

        Redisson redisson = Redisson.create();

        List<SomeObject> list = redisson.getList("anyList");
        list.add(new SomeObject());
        list.get(0);
        list.remove(new SomeObject());

        ...

        redisson.shutdown();

####Distributed Queue example

        Redisson redisson = Redisson.create();

        Queue<SomeObject> queue = redisson.getQueue("anyQueue");
        queue.add(new SomeObject());
        queue.peek();
        queue.pool();

        ...

        redisson.shutdown();

####Distributed Lock example

        Redisson redisson = Redisson.create();

        Lock lock = redisson.getLock("anyLock");
        lock.lock();
        lock.unlock();

        // same as

        redisson.getLock("anyLock").lock();

        ...

        redisson.getLock("anyLock").unlock();

        ...

        redisson.shutdown();

####Distributed AtomicLong example

        Redisson redisson = Redisson.create();

        RAtomicLong atomicLong = redisson.getAtomicLong("anyAtomicLong");
        atomicLong.set(3);
        atomicLong.incrementAndGet();
        atomicLong.get();

        ...

        redisson.shutdown();


####Distributed CountDownLatch example

        Redisson redisson = Redisson.create();

        RCountDownLatch latch = redisson.getCountDownLatch("anyCountDownLatch");
        latch.trySetCount(1);
        latch.await();

        // in other thread or other JVM

        RCountDownLatch latch = redisson.getCountDownLatch("anyCountDownLatch");
        latch.countDown();

        ...

        redisson.shutdown();


####Distributed Topic example

        Redisson redisson = Redisson.create();

        RTopic<SomeObject> topic = redisson.getTopic("anyTopic");
        topic.addListener(new MessageListener<SomeObject>() {

             public void onMessage(SomeObject message) {
                ...
             }
        });

        // in other thread or other JVM

        RTopic<SomeObject> topic = redisson.getTopic("anyTopic");
        topic.publish(new SomeObject());


        ...

        redisson.shutdown();


