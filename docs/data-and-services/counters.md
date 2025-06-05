## Id generator
Java implementation of Valkey or Redis based Id generator [RIdGenerator](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RIdGenerator.html) generates unique numbers but not monotonically increased. At first request, batch of id numbers is allocated and cached on Java side till it's exhausted. This approach allows to generate ids faster than [RAtomicLong](#atomiclong).

Default allocation size is 5000. 
Default start value is 0.

Code examples:

=== "Sync"
	```
	RIdGenerator generator = redisson.getIdGenerator("generator");

	// Initialize with start value = 12 and allocation size = 20000
	generator.tryInit(12, 20000);

	long id = generator.nextId();
	```
=== "Async"
	```
	RIdGeneratorAsync generator = redisson.getIdGenerator("generator");

	// Initialize with start value = 12 and allocation size = 20000
	RFuture<Boolean> initFuture = generator.tryInitAsync(12, 20000);

	RFuture<Long> idFuture = generator.nextIdAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RIdGeneratorReactive generator = redisson.getIdGenerator("generator");

	// Initialize with start value = 12 and allocation size = 20000
	Mono<Boolean> initMono = generator.tryInit(12, 20000);

	Mono<Long> idMono = generator.nextId();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RIdGeneratorRx generator = redisson.getIdGenerator("generator");

	// Initialize with start value = 12 and allocation size = 20000
	Single<Boolean> initRx = generator.tryInit(12, 20000);

	Single<Long> idRx = generator.nextId();
	```

## AtomicLong
Java implementation of Valkey or Redis based [AtomicLong](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicLong.html) object provides API similar to [java.util.concurrent.atomic.AtomicLong](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicLong.html) object.

Code examples:

=== "Sync"
	```
	RAtomicLong atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	atomicLong.set(3);
	atomicLong.incrementAndGet();
	atomicLong.get();
	```
=== "Async"
	```
	RAtomicLongAsync atomicLong = redisson.getAtomicLong("myAtomicLong");

	RFuture<Void> setFuture = atomicLong.setAsync(3);
	RFuture<Long> igFuture = atomicLong.incrementAndGetAsync();
	RFuture<Long> getFuture = atomicLong.getAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicLongReactive atomicLong = redisson.getAtomicLong("myAtomicLong");

	Mono<Void> setMono = atomicLong.set(3);
	Mono<Long> igMono = atomicLong.incrementAndGet();
	Mono<Long> getMono = atomicLong.get();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicLongRx atomicLong = redisson.getAtomicLong("myAtomicLong");

	Completable setCompletable = atomicLong.set(3);
	Single<Long> igSingle = atomicLong.incrementAndGet();
	Single<Long> getSingle = atomicLong.get();
	```

## AtomicDouble
Java implementation of Valkey or Redis based [AtomicDouble](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicDouble.html) object. 

Code examples:

=== "Sync"
	```
	RAtomicDouble atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	atomicDouble.set(2.81);
	atomicDouble.addAndGet(4.11);
	atomicDouble.get();
	```
=== "Async"
	```
	RAtomicDoubleAsync atomicDouble = redisson.getAtomicDouble("myAtomicDouble");

	RFuture<Void> setFuture = atomicDouble.setAsync(2.81);
	RFuture<Double> agFuture = atomicDouble.addAndGetAsync(4.11);
	RFuture<Double> getFuture = atomicDouble.getAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicDoubleReactive atomicDouble = redisson.getAtomicDouble("myAtomicDouble");

	Mono<Void> setMono = atomicDouble.set(2.81);
	Mono<Double> agMono = atomicDouble.addAndGet(4.11);
	Mono<Double> getMono = atomicDouble.get();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicDoubleRx atomicDouble = redisson.getAtomicDouble("myAtomicDouble");

	Completable setCompletable = atomicDouble.set(2.81);
	Single<Double> agSingle = atomicDouble.addAndGet(4.11);
	Single<Double> getSingle = atomicDouble.get();
	```

## LongAdder
Java implementation of Valkey or Redis based [LongAdder](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLongAdder.html) object provides API similar to [java.util.concurrent.atomic.LongAdder](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/LongAdder.html) object.

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
Java implementation of Valkey or Redis based [DoubleAdder](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RDoubleAdder.html) object provides API similar to [java.util.concurrent.atomic.DoubleAdder](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/DoubleAdder.html) object.

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