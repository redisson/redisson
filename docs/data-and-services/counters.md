## Id generator
Redis or Valkey based Java Id generator [RIdGenerator](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RIdGenerator.html) generates unique numbers but not monotonically increased. At first request, batch of id numbers is allocated and cached on Java side till it's exhausted. This approach allows to generate ids faster than [RAtomicLong](#atomiclong).

Default allocation size is 5000. 
Default start value is 0.

Code example:
```java
RIdGenerator generator = redisson.getIdGenerator("generator");

// Initialize with start value = 12 and allocation size = 20000
generator.tryInit(12, 20000);

long id = generator.nextId();
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RIdGeneratorAsync.html) interface** usage:
```java
RIdGeneratorAsync generator = redisson.getIdGenerator("generator");

// Initialize with start value = 12 and allocation size = 20000
RFuture<Boolean> initFuture = generator.tryInitAsync(12, 20000);

RFuture<Long> idFuture = generator.nextIdAsync();
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RIdGeneratorReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RIdGeneratorReactive generator = redisson.getIdGenerator("generator");

// Initialize with start value = 12 and allocation size = 20000
Mono<Boolean> initMono = generator.tryInit(12, 20000);

Mono<Long> idMono = generator.nextId();
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RIdGeneratorRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RIdGeneratorRx generator = redisson.getIdGenerator("generator");

// Initialize with start value = 12 and allocation size = 20000
Single<Boolean> initRx = generator.tryInit(12, 20000);

Single<Long> idRx = generator.nextId();
```

## AtomicLong
Java implementation of Redis or Valkey based [AtomicLong](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicLong.html) object provides API similar to [java.util.concurrent.atomic.AtomicLong](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicLong.html) object.

Code example:
```java
RAtomicLong atomicLong = redisson.getAtomicLong("myAtomicLong");
atomicLong.set(3);
atomicLong.incrementAndGet();
atomicLong.get();
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicLongAsync.html) interface** usage:
```java
RAtomicLongAsync atomicLong = redisson.getAtomicLong("myAtomicLong");

RFuture<Void> setFuture = atomicLong.setAsync(3);
RFuture<Long> igFuture = atomicLong.incrementAndGetAsync();
RFuture<Long> getFuture = atomicLong.getAsync();
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicLongReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RAtomicLongReactive atomicLong = redisson.getAtomicLong("myAtomicLong");

Mono<Void> setMono = atomicLong.set(3);
Mono<Long> igMono = atomicLong.incrementAndGet();
RFuture<Long> getMono = atomicLong.getAsync();
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicLongRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RAtomicLongRx atomicLong = redisson.getAtomicLong("myAtomicLong");

Completable setMono = atomicLong.set(3);
Single<Long> igMono = atomicLong.incrementAndGet();
Single<Long> getMono = atomicLong.getAsync();
```

## AtomicDouble
Java implementation of Redis or Valkey based [AtomicDouble](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicDouble.html) object. 

Code example:
```java
RAtomicDouble atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
atomicDouble.set(2.81);
atomicDouble.addAndGet(4.11);
atomicDouble.get();
```

Code example of **[Async](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicDoubleAsync.html) interface** usage:
```java
RAtomicDoubleAsync atomicDouble = redisson.getAtomicDouble("myAtomicDouble");

RFuture<Void> setFuture = atomicDouble.setAsync(2.81);
RFuture<Double> agFuture = atomicDouble.addAndGetAsync(4.11);
RFuture<Double> getFuture = atomicDouble.getAsync();
```

Code example of **[Reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicDoubleReactive.html) interface** usage:
```java
RedissonReactiveClient redisson = redissonClient.reactive();
RAtomicDoubleReactive atomicDouble = redisson.getAtomicDouble("myAtomicDouble");

Mono<Void> setMono = atomicDouble.set(2.81);
Mono<Double> agMono = atomicDouble.addAndGet(4.11);
Mono<Double> getMono = atomicDouble.get();
```

Code example of **[RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicDoubleRx.html) interface** usage:
```java
RedissonRxClient redisson = redissonClient.rxJava();
RAtomicDoubleRx atomicDouble = redisson.getAtomicDouble("myAtomicDouble");

Completable setMono = atomicDouble.set(2.81);
Single<Double> igMono = atomicDouble.addAndGet(4.11);
Single<Double> getMono = atomicDouble.get();
```

## LongAdder
Java implementation of Redis or Valkey based [LongAdder](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLongAdder.html) object provides API similar to [java.util.concurrent.atomic.LongAdder](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/LongAdder.html) object.

It maintains internal LongAdder object on client side and provides superior performance for both increment and decrement operations. Up to __12000x__ faster than similar `AtomicLong` object. Suitable for distributed metric objects.

Code example:
```java
RLongAdder atomicLong = redisson.getLongAdder("myLongAdder");
atomicLong.add(12);
atomicLong.increment();
atomicLong.decrement();
atomicLong.sum();
```

Object should be destroyed if it's not used anymore, but it's not necessary to call destroy method if Redisson goes shutdown.
```java
RLongAdder atomicLong = ...
atomicLong.destroy();
```

## DoubleAdder
Java implementation of Redis or Valkey based [DoubleAdder](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RDoubleAdder.html) object provides API similar to [java.util.concurrent.atomic.DoubleAdder](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/DoubleAdder.html) object.

It maintains internal DoubleAdder object on client side and provides superior performance for both increment and decrement operations. Up to __12000x__ faster than similar `AtomicDouble` object. Suitable for distributed metric objects.

Code example:
```java
RLongDouble atomicDouble = redisson.getLongDouble("myLongDouble");
atomicDouble.add(12);
atomicDouble.increment();
atomicDouble.decrement();
atomicDouble.sum();
```

Object should be destroyed if it's not used anymore, but it's not necessary to call destroy method if Redisson goes shutdown.
```java
RLongDouble atomicDouble = ...
atomicDouble.destroy();
```