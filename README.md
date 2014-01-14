##Redisson - distributed and scalable Java data structures on top of Redis server. Advanced Redis client

Use familiar Java data structures with power of [Redis](http://redis.io).

Based on patched version of [lettuce](https://github.com/wg/lettuce) [Redis](http://redis.io) client and [Netty 4](http://netty.io) framework.  
JDK 1.6+ compatible  

Licensed under the Apache License 2.0.


Features
================================
* Distributed implementation of `java.util.List`  
* Distributed implementation of `java.util.Set`  
* Distributed implementation of `java.util.Queue`  
* Distributed implementation of `java.util.Map`  
* Distributed implementation of `java.util.concurrent.ConcurrentMap`  
* Distributed implementation of reentrant `java.util.concurrent.locks.Lock`  
* Distributed alternative to the `java.util.concurrent.atomic.AtomicLong`  
* Distributed alternative to the `java.util.concurrent.CountDownLatch`  
* Distributed publish/subscribe messaging via `org.redisson.core.RTopic` object  
* Supports OSGi  
* With over 70 unit tests  


Recent Releases
================================
####Please Note: trunk is current development branch.

####11-Jan-2014 - version 1.0.0 released
First stable release.


Usage examples
================================
####Simple config example
       
        // connects to Redis server 127.0.0.1:6379 by default
        Redisson redisson = Redisson.create();

        ...

        redisson.shutdown();

or with initialization by Config object

        Config config = new Config();
        config.setConnectionPoolSize(10);

        // Redisson will use load balance connections between listed servers
        config.addAddress("first.redisserver.com:8291");
        config.addAddress("second.redisserver.com:8291");
        config.addAddress("third.redisserver.com:8291");

        Redisson redisson = Redisson.create(config);

        ...

        redisson.shutdown();

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


