## Id generator
Java implementation of Valkey or Redis based id generator [RIdGenerator](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RIdGenerator.html) produces unique numbers that are not monotonically increasing.

It owes its speed to batch allocation. The first call reserves a block of numbers from Valkey or Redis in a single operation and caches it on the Java side; later calls are then served from that block locally, with no network round trip, until it is exhausted - at which point the next block is reserved. Because each client hands out numbers from its own reserved block concurrently, the ids are globally unique but interleave out of order rather than increasing monotonically. This makes `nextId` far faster than [RAtomicLong](#atomiclong), which performs a round trip per number and stays strictly sequential - the trade-off being ordering, and that a client which stops before using its whole block leaves those numbers unissued, so the sequence can jump forward.

Code examples:

=== "Sync"
	```
	RIdGenerator generator = redisson.getIdGenerator("generator");
	
	// initialize once with start value = 12 and block size = 20000
	generator.tryInit(12, 20000);
	
	// unique number, served from the local block without a round trip
	long id = generator.nextId();
	```
=== "Async"
	```
	RIdGeneratorAsync generator = redisson.getIdGenerator("generator");
	
	RFuture<Boolean> initFuture = generator.tryInitAsync(12, 20000);
	RFuture<Long> idFuture = generator.nextIdAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RIdGeneratorReactive generator = redisson.getIdGenerator("generator");
	
	Mono<Boolean> initMono = generator.tryInit(12, 20000);
	Mono<Long> idMono = generator.nextId();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RIdGeneratorRx generator = redisson.getIdGenerator("generator");
	
	Single<Boolean> initRx = generator.tryInit(12, 20000);
	Single<Long> idRx = generator.nextId();
	```

**Initialization**

`tryInit` sets the start value and the block size, returning `true` if it initialized the generator and `false` if it was already initialized. Since it takes effect only once, it is safe to call from every node at startup - the first call wins and the rest are no-ops. If `nextId` is called without an explicit `tryInit`, the generator is created with the default start value of 0 and block size of 5000.

=== "Sync"
	```
	RIdGenerator generator = redisson.getIdGenerator("generator");
	
	// true on the node that initializes it, false on the others
	boolean initialized = generator.tryInit(12, 20000);
	```
=== "Async"
	```
	RIdGeneratorAsync generator = redisson.getIdGenerator("generator");
	
	RFuture<Boolean> initialized = generator.tryInitAsync(12, 20000);
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RIdGeneratorReactive generator = redisson.getIdGenerator("generator");
	
	Mono<Boolean> initialized = generator.tryInit(12, 20000);
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RIdGeneratorRx generator = redisson.getIdGenerator("generator");
	
	Single<Boolean> initialized = generator.tryInit(12, 20000);
	```

**Choosing the allocation size**

The allocation size sets how many numbers each client reserves per round trip, and is the main tuning knob. A larger block amortises the network cost over more numbers and raises throughput, but a client that restarts discards the unused tail of its current block, so the sequence can jump forward by up to that many numbers. A smaller block keeps those jumps small at the cost of more frequent round trips. Where gaps are unwelcome and strict ordering is required, [RAtomicLong](#atomiclong) is the better fit.

### Use Cases

RIdGenerator hands out unique `Long` numbers cheaply and without per-id coordination, which fits anywhere a system needs identifiers but not order: primary keys generated in the application, high-volume tags for events and jobs, and correlation ids across services. Where strictly increasing ids are required, [RAtomicLong](#atomiclong) is the alternative.

**Application-Generated Primary Keys**

Generating an entity's key in the application - rather than relying on a database sequence or auto-increment column - means the id is known before the row is written, so it can be returned immediately, used to set foreign keys, or batched, without a database round trip per insert. `nextId` supplies a unique key that is safe to use across all instances.

=== "Sync"
	```
	RIdGenerator ids = redisson.getIdGenerator("orders:id");
	ids.tryInit(1, 50000);   // optional: start at 1 with a 50000 block
	
	Order order = new Order();
	order.setId(ids.nextId());   // key known before the row is persisted
	repository.save(order);
	```
=== "Async"
	```
	RIdGeneratorAsync ids = redisson.getIdGenerator("orders:id");
	
	RFuture<Long> id = ids.nextIdAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RIdGeneratorReactive ids = redisson.getIdGenerator("orders:id");
	
	Mono<Long> id = ids.nextId();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RIdGeneratorRx ids = redisson.getIdGenerator("orders:id");
	
	Single<Long> id = ids.nextId();
	```

**High-Throughput Identifiers for Events and Jobs**

Stamping events, log records, messages, or background jobs with a unique id at high volume is where batch allocation pays off: most `nextId` calls are served from the local block with no network round trip, so the generator sustains high rates. Non-monotonic order and occasional gaps are irrelevant when the id is only an opaque handle.

=== "Sync"
	```
	RIdGenerator ids = redisson.getIdGenerator("events:id");
	
	for (Event event : batch) {
	    event.setId(ids.nextId());   // mostly served locally, no round trip
	}
	```
=== "Async"
	```
	RIdGeneratorAsync ids = redisson.getIdGenerator("events:id");
	
	RFuture<Long> id = ids.nextIdAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RIdGeneratorReactive ids = redisson.getIdGenerator("events:id");
	
	Mono<Long> id = ids.nextId();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RIdGeneratorRx ids = redisson.getIdGenerator("events:id");
	
	Single<Long> id = ids.nextId();
	```

**Correlation and Trace IDs Across Services**

Assigning a unique id to each request or transaction so logs and spans can be correlated across services needs uniqueness, not ordering. Because ids come from per-client blocks, there is no single hot key that every request must contend on - the bottleneck a strictly sequential [RAtomicLong](#atomiclong) would introduce.

=== "Sync"
	```
	RIdGenerator ids = redisson.getIdGenerator("trace:id");
	
	String correlationId = "req-" + ids.nextId();
	MDC.put("correlationId", correlationId);
	```
=== "Async"
	```
	RIdGeneratorAsync ids = redisson.getIdGenerator("trace:id");
	
	RFuture<Long> id = ids.nextIdAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RIdGeneratorReactive ids = redisson.getIdGenerator("trace:id");
	
	Mono<Long> id = ids.nextId();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RIdGeneratorRx ids = redisson.getIdGenerator("trace:id");
	
	Single<Long> id = ids.nextId();
	```

## AtomicLong
Java implementation of Valkey or Redis based [AtomicLong](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicLong.html) object provides API similar to [java.util.concurrent.atomic.AtomicLong](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicLong.html) object.

It holds a single 64-bit value shared by all clients. Every operation runs as an atomic command on Valkey or Redis and returns the up-to-date result, so reads and conditional updates stay strongly consistent. Where `RLongAdder` trades read cost for very fast writes, each operation here is a network round trip - which makes `RAtomicLong` the better choice when a value is read about as often as it is written, or when an operation needs the post-update result or a compare-and-set.

Code examples:

=== "Sync"
	```
	RAtomicLong atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	atomicLong.set(3);
	long value = atomicLong.get();
	long previous = atomicLong.getAndSet(10);
	```
=== "Async"
	```
	RAtomicLongAsync atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	RFuture<Void> setFuture = atomicLong.setAsync(3);
	RFuture<Long> getFuture = atomicLong.getAsync();
	RFuture<Long> getAndSetFuture = atomicLong.getAndSetAsync(10);
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicLongReactive atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	Mono<Void> setMono = atomicLong.set(3);
	Mono<Long> getMono = atomicLong.get();
	Mono<Long> getAndSetMono = atomicLong.getAndSet(10);
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicLongRx atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	Completable setRx = atomicLong.set(3);
	Single<Long> getRx = atomicLong.get();
	Single<Long> getAndSetRx = atomicLong.getAndSet(10);
	```

**Increment, decrement, and add**

Each step operation has two forms: `incrementAndGet`, `decrementAndGet`, and `addAndGet` apply the change and return the new value, while `getAndIncrement`, `getAndDecrement`, and `getAndAdd` return the value as it was before the change.

=== "Sync"
	```
	RAtomicLong atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	long updated = atomicLong.incrementAndGet();   // value after the change
	long previous = atomicLong.getAndAdd(5);       // value before the change
	long current = atomicLong.decrementAndGet();
	```
=== "Async"
	```
	RAtomicLongAsync atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	RFuture<Long> updatedFuture = atomicLong.incrementAndGetAsync();
	RFuture<Long> previousFuture = atomicLong.getAndAddAsync(5);
	RFuture<Long> currentFuture = atomicLong.decrementAndGetAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicLongReactive atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	Mono<Long> updatedMono = atomicLong.incrementAndGet();
	Mono<Long> previousMono = atomicLong.getAndAdd(5);
	Mono<Long> currentMono = atomicLong.decrementAndGet();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicLongRx atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	Single<Long> updatedRx = atomicLong.incrementAndGet();
	Single<Long> previousRx = atomicLong.getAndAdd(5);
	Single<Long> currentRx = atomicLong.decrementAndGet();
	```

**Compare-and-set and atomic swap**

`compareAndSet` updates the value only if it still equals the expected value and returns whether it did - the building block for lock-free retry loops. `getAndSet` replaces the value and returns the previous one, and `getAndDelete` reads the value and removes the object in a single step.

=== "Sync"
	```
	RAtomicLong atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	boolean updated = atomicLong.compareAndSet(3, 10);   // set to 10 only if still 3
	long previous = atomicLong.getAndSet(20);
	long last = atomicLong.getAndDelete();
	```
=== "Async"
	```
	RAtomicLongAsync atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	RFuture<Boolean> updatedFuture = atomicLong.compareAndSetAsync(3, 10);
	RFuture<Long> previousFuture = atomicLong.getAndSetAsync(20);
	RFuture<Long> lastFuture = atomicLong.getAndDeleteAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicLongReactive atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	Mono<Boolean> updatedMono = atomicLong.compareAndSet(3, 10);
	Mono<Long> previousMono = atomicLong.getAndSet(20);
	Mono<Long> lastMono = atomicLong.getAndDelete();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicLongRx atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	Single<Boolean> updatedRx = atomicLong.compareAndSet(3, 10);
	Single<Long> previousRx = atomicLong.getAndSet(20);
	Single<Long> lastRx = atomicLong.getAndDelete();
	```

**Conditional update by comparison**

`setIfGreater` writes the new value only when the current value is greater than the given comparison value, and `setIfLess` only when it is less. Both return whether the update happened, which makes them a convenient way to clamp a value to a bound in one atomic step.

=== "Sync"
	```
	RAtomicLong atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	// clamp to a ceiling: replace the value only if it is greater than 100
	boolean cappedHigh = atomicLong.setIfGreater(100, 100);
	
	// clamp to a floor: replace the value only if it is less than 0
	boolean cappedLow = atomicLong.setIfLess(0, 0);
	```
=== "Async"
	```
	RAtomicLongAsync atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	RFuture<Boolean> cappedHighFuture = atomicLong.setIfGreaterAsync(100, 100);
	RFuture<Boolean> cappedLowFuture = atomicLong.setIfLessAsync(0, 0);
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicLongReactive atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	Mono<Boolean> cappedHighMono = atomicLong.setIfGreater(100, 100);
	Mono<Boolean> cappedLowMono = atomicLong.setIfLess(0, 0);
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicLongRx atomicLong = redisson.getAtomicLong("myAtomicLong");
	
	Single<Boolean> cappedHighRx = atomicLong.setIfGreater(100, 100);
	Single<Boolean> cappedLowRx = atomicLong.setIfLess(0, 0);
	```

### Use Cases

RAtomicLong is the right choice when a single shared integer must stay exact and immediately consistent across instances - every read and update is an atomic operation on the server. That suits sequence numbers, live up-and-down counters whose current value is read directly, and quota or guard logic built on compare-and-set.

**Sequence and Ticket Numbers**

`incrementAndGet` hands out a unique, monotonically increasing number on every call with no coordination between instances - useful for order or ticket numbers, batch ids, or any sequence where each consumer must receive a distinct value. Where the batched id generator above trades strict ordering for throughput, this returns each number in order.

=== "Sync"
	```
	RAtomicLong sequence = redisson.getAtomicLong("orders:sequence");
	
	// each call returns the next number, unique across all instances
	long orderNumber = sequence.incrementAndGet();
	```
=== "Async"
	```
	RAtomicLongAsync sequence = redisson.getAtomicLong("orders:sequence");
	
	RFuture<Long> orderNumber = sequence.incrementAndGetAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicLongReactive sequence = redisson.getAtomicLong("orders:sequence");
	
	Mono<Long> orderNumber = sequence.incrementAndGet();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicLongRx sequence = redisson.getAtomicLong("orders:sequence");
	
	Single<Long> orderNumber = sequence.incrementAndGet();
	```

**Live Counters Read in Real Time**

A value that moves up and down and is read directly - active sessions, in-flight requests, open connections - is incremented and decremented atomically, with `get` returning the exact current value at any moment. This is where `RAtomicLong` is preferred over `RLongAdder`, whose total is only cheap to read occasionally.

=== "Sync"
	```
	RAtomicLong active = redisson.getAtomicLong("sessions:active");
	
	active.incrementAndGet();   // on connect
	active.decrementAndGet();   // on disconnect
	
	long current = active.get();   // exact live value
	```
=== "Async"
	```
	RAtomicLongAsync active = redisson.getAtomicLong("sessions:active");
	
	RFuture<Long> afterConnect = active.incrementAndGetAsync();
	RFuture<Long> current = active.getAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicLongReactive active = redisson.getAtomicLong("sessions:active");
	
	Mono<Long> afterConnect = active.incrementAndGet();
	Mono<Long> current = active.get();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicLongRx active = redisson.getAtomicLong("sessions:active");
	
	Single<Long> afterConnect = active.incrementAndGet();
	Single<Long> current = active.get();
	```

**Quotas and Lock-Free Guards**

`compareAndSet` gives lock-free coordination: a worker claims a one-time action by moving the value from an expected state to a new one only if no one else has, retrying if it changed. `setIfGreater` caps a counter at a quota in a single step.

=== "Sync"
	```
	RAtomicLong state = redisson.getAtomicLong("job:42:state");
	
	// claim the job: only one worker moves it from 0 (pending) to 1 (running)
	boolean claimed = state.compareAndSet(0, 1);
	
	// keep a usage counter from exceeding its quota of 1000
	RAtomicLong used = redisson.getAtomicLong("quota:user:7");
	boolean capped = used.setIfGreater(1000, 1000);
	```
=== "Async"
	```
	RAtomicLongAsync state = redisson.getAtomicLong("job:42:state");
	
	RFuture<Boolean> claimed = state.compareAndSetAsync(0, 1);
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicLongReactive state = redisson.getAtomicLong("job:42:state");
	
	Mono<Boolean> claimed = state.compareAndSet(0, 1);
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicLongRx state = redisson.getAtomicLong("job:42:state");
	
	Single<Boolean> claimed = state.compareAndSet(0, 1);
	```

## AtomicDouble
Java implementation of Valkey or Redis based [AtomicDouble](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RAtomicDouble.html) object. It provides the same atomic operations as [AtomicLong](#atomiclong) for `double` values.

As with `RAtomicLong`, the value is shared by all clients and every operation runs atomically on Valkey or Redis and returns the up-to-date result, so it stays strongly consistent. Each operation is a network round trip, so it suits values read about as often as written, or cases that need the post-update result or a compare-and-set - whereas `RDoubleAdder` instead optimises for very fast writes.

Code examples:

=== "Sync"
	```
	RAtomicDouble atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	atomicDouble.set(2.81);
	double value = atomicDouble.get();
	double previous = atomicDouble.getAndSet(10.5);
	```
=== "Async"
	```
	RAtomicDoubleAsync atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	RFuture<Void> setFuture = atomicDouble.setAsync(2.81);
	RFuture<Double> getFuture = atomicDouble.getAsync();
	RFuture<Double> getAndSetFuture = atomicDouble.getAndSetAsync(10.5);
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicDoubleReactive atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	Mono<Void> setMono = atomicDouble.set(2.81);
	Mono<Double> getMono = atomicDouble.get();
	Mono<Double> getAndSetMono = atomicDouble.getAndSet(10.5);
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicDoubleRx atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	Completable setRx = atomicDouble.set(2.81);
	Single<Double> getRx = atomicDouble.get();
	Single<Double> getAndSetRx = atomicDouble.getAndSet(10.5);
	```

**Increment, decrement, and add**

Like `RAtomicLong`, the `...AndGet` operations return the updated value and the `getAnd...` operations return the value from before the change.

=== "Sync"
	```
	RAtomicDouble atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	double updated = atomicDouble.incrementAndGet();   // value after the change
	double previous = atomicDouble.getAndAdd(5.5);     // value before the change
	double current = atomicDouble.decrementAndGet();
	```
=== "Async"
	```
	RAtomicDoubleAsync atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	RFuture<Double> updatedFuture = atomicDouble.incrementAndGetAsync();
	RFuture<Double> previousFuture = atomicDouble.getAndAddAsync(5.5);
	RFuture<Double> currentFuture = atomicDouble.decrementAndGetAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicDoubleReactive atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	Mono<Double> updatedMono = atomicDouble.incrementAndGet();
	Mono<Double> previousMono = atomicDouble.getAndAdd(5.5);
	Mono<Double> currentMono = atomicDouble.decrementAndGet();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicDoubleRx atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	Single<Double> updatedRx = atomicDouble.incrementAndGet();
	Single<Double> previousRx = atomicDouble.getAndAdd(5.5);
	Single<Double> currentRx = atomicDouble.decrementAndGet();
	```

**Compare-and-set and atomic swap**

`compareAndSet` performs a lock-free conditional update, `getAndSet` swaps in a new value and returns the previous one, and `getAndDelete` reads and removes the value in a single step.

=== "Sync"
	```
	RAtomicDouble atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	boolean updated = atomicDouble.compareAndSet(2.81, 10.5);   // set only if still 2.81
	double previous = atomicDouble.getAndSet(20.5);
	double last = atomicDouble.getAndDelete();
	```
=== "Async"
	```
	RAtomicDoubleAsync atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	RFuture<Boolean> updatedFuture = atomicDouble.compareAndSetAsync(2.81, 10.5);
	RFuture<Double> previousFuture = atomicDouble.getAndSetAsync(20.5);
	RFuture<Double> lastFuture = atomicDouble.getAndDeleteAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicDoubleReactive atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	Mono<Boolean> updatedMono = atomicDouble.compareAndSet(2.81, 10.5);
	Mono<Double> previousMono = atomicDouble.getAndSet(20.5);
	Mono<Double> lastMono = atomicDouble.getAndDelete();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicDoubleRx atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	Single<Boolean> updatedRx = atomicDouble.compareAndSet(2.81, 10.5);
	Single<Double> previousRx = atomicDouble.getAndSet(20.5);
	Single<Double> lastRx = atomicDouble.getAndDelete();
	```

**Conditional update by comparison**

`setIfGreater` and `setIfLess` write the new value only when the current value is respectively greater or less than the given bound - a one-step way to clamp a `double` to a ceiling or floor.

=== "Sync"
	```
	RAtomicDouble atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	// clamp to a ceiling: replace the value only if it is greater than 100.0
	boolean cappedHigh = atomicDouble.setIfGreater(100.0, 100.0);
	
	// clamp to a floor: replace the value only if it is less than 0.0
	boolean cappedLow = atomicDouble.setIfLess(0.0, 0.0);
	```
=== "Async"
	```
	RAtomicDoubleAsync atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	RFuture<Boolean> cappedHighFuture = atomicDouble.setIfGreaterAsync(100.0, 100.0);
	RFuture<Boolean> cappedLowFuture = atomicDouble.setIfLessAsync(0.0, 0.0);
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicDoubleReactive atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	Mono<Boolean> cappedHighMono = atomicDouble.setIfGreater(100.0, 100.0);
	Mono<Boolean> cappedLowMono = atomicDouble.setIfLess(0.0, 0.0);
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicDoubleRx atomicDouble = redisson.getAtomicDouble("myAtomicDouble");
	
	Single<Boolean> cappedHighRx = atomicDouble.setIfGreater(100.0, 100.0);
	Single<Boolean> cappedLowRx = atomicDouble.setIfLess(0.0, 0.0);
	```

### Use Cases

RAtomicDouble suits a single shared fractional value that must stay exact and immediately consistent - a balance, a live measurement, an accruing total - where each read reflects every update so far and adjustments are applied atomically.

**Live Balances and Measurements**

A running balance or current reading checked on every access - an account balance, a wallet, a live load factor or price - is read with `get` and adjusted with `addAndGet`, so a credit or debit and the resulting balance are a single atomic step. Where `RDoubleAdder` is read only occasionally, here every read is exact and current.

=== "Sync"
	```
	RAtomicDouble balance = redisson.getAtomicDouble("account:7:balance");
	
	double afterCredit = balance.addAndGet(50.0);     // apply a credit, get new balance
	double afterDebit = balance.addAndGet(-19.99);    // apply a debit
	
	double current = balance.get();
	```
=== "Async"
	```
	RAtomicDoubleAsync balance = redisson.getAtomicDouble("account:7:balance");
	
	RFuture<Double> afterCredit = balance.addAndGetAsync(50.0);
	RFuture<Double> current = balance.getAsync();
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicDoubleReactive balance = redisson.getAtomicDouble("account:7:balance");
	
	Mono<Double> afterCredit = balance.addAndGet(50.0);
	Mono<Double> current = balance.get();
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicDoubleRx balance = redisson.getAtomicDouble("account:7:balance");
	
	Single<Double> afterCredit = balance.addAndGet(50.0);
	Single<Double> current = balance.get();
	```

**Atomic Adjustments and Swaps**

`compareAndSet` applies a change only if the value has not moved since it was read - safe for updating a shared price or rate without a lock - and `getAndSet` swaps in a fresh reading while returning the previous one, for instance when rotating a sampled value.

=== "Sync"
	```
	RAtomicDouble price = redisson.getAtomicDouble("product:9:price");
	
	// apply a new price only if it is still the one we read
	boolean changed = price.compareAndSet(19.99, 17.99);
	
	// replace with a fresh reading and keep the previous one
	double previous = price.getAndSet(21.50);
	```
=== "Async"
	```
	RAtomicDoubleAsync price = redisson.getAtomicDouble("product:9:price");
	
	RFuture<Boolean> changed = price.compareAndSetAsync(19.99, 17.99);
	RFuture<Double> previous = price.getAndSetAsync(21.50);
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicDoubleReactive price = redisson.getAtomicDouble("product:9:price");
	
	Mono<Boolean> changed = price.compareAndSet(19.99, 17.99);
	Mono<Double> previous = price.getAndSet(21.50);
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicDoubleRx price = redisson.getAtomicDouble("product:9:price");
	
	Single<Boolean> changed = price.compareAndSet(19.99, 17.99);
	Single<Double> previous = price.getAndSet(21.50);
	```

**Clamping to a Ceiling or Floor**

`setIfGreater` and `setIfLess` hold a value within bounds in one atomic step - capping a computed score, a load factor, or an accrued amount at a maximum, or lifting it to a minimum - without a read-then-write race.

=== "Sync"
	```
	RAtomicDouble load = redisson.getAtomicDouble("node:load-factor");
	
	// cap the load factor at 1.0
	boolean capped = load.setIfGreater(1.0, 1.0);
	
	// keep it from going negative
	boolean floored = load.setIfLess(0.0, 0.0);
	```
=== "Async"
	```
	RAtomicDoubleAsync load = redisson.getAtomicDouble("node:load-factor");
	
	RFuture<Boolean> capped = load.setIfGreaterAsync(1.0, 1.0);
	RFuture<Boolean> floored = load.setIfLessAsync(0.0, 0.0);
	```
=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RAtomicDoubleReactive load = redisson.getAtomicDouble("node:load-factor");
	
	Mono<Boolean> capped = load.setIfGreater(1.0, 1.0);
	Mono<Boolean> floored = load.setIfLess(0.0, 0.0);
	```
=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RAtomicDoubleRx load = redisson.getAtomicDouble("node:load-factor");
	
	Single<Boolean> capped = load.setIfGreater(1.0, 1.0);
	Single<Boolean> floored = load.setIfLess(0.0, 0.0);
	```

## LongAdder
Java implementation of Valkey or Redis based [LongAdder](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLongAdder.html) object provides API similar to [java.util.concurrent.atomic.LongAdder](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/LongAdder.html) object.

Each client keeps its own internal `LongAdder` and updates it in memory, so `add`, `increment`, and `decrement` complete locally without a network round trip per call - up to __12000x__ faster than a comparable `AtomicLong` under contention. The cost of that speed is paid on reads: `sum` coordinates with every `RLongAdder` instance to accumulate their local values into a single total, and `reset` clears them all. That division - very cheap writes, a heavier and less frequent read - is what makes it a fit for distributed metric counters updated constantly across many nodes and read only occasionally.

Code examples:

=== "Sync"
	```
	RLongAdder atomicLong = redisson.getLongAdder("myLongAdder");
	
	atomicLong.add(12);
	atomicLong.increment();
	atomicLong.decrement();
	
	long sum = atomicLong.sum();   // accumulated across all instances
	atomicLong.reset();            // cleared on all instances
	```
=== "Async"
	```
	RLongAdder atomicLong = redisson.getLongAdder("myLongAdder");
	
	// only sum and reset coordinate across instances, so only they have async forms
	RFuture<Long> sumFuture = atomicLong.sumAsync();
	RFuture<Void> resetFuture = atomicLong.resetAsync();
	```

**Bounded coordination with a timeout**

Because `sum` and `reset` gather responses from every instance over the network, their asynchronous variants accept an optional timeout that bounds how long to wait for those responses before completing.

=== "Async"
	```
	RLongAdder atomicLong = redisson.getLongAdder("myLongAdder");
	
	RFuture<Long> sumFuture = atomicLong.sumAsync(3, TimeUnit.SECONDS);
	RFuture<Void> resetFuture = atomicLong.resetAsync(3, TimeUnit.SECONDS);
	```

**Lifecycle**

The object keeps state on the client side, so call `destroy` once it is no longer used to release it. This is not necessary if Redisson itself is shutting down.

```java
RLongAdder atomicLong = ...
atomicLong.destroy();
```

### Use Cases

LongAdder accumulates a 64-bit total on the client side and flushes it to Valkey or Redis, so increments and decrements stay contention-free and run far faster than `AtomicLong` under load - the trade-off being that `sum()` is the comparatively rare read. That makes it a fit for write-heavy counters spread across many instances.

**High-Throughput Event Counting**

Counting discrete events - requests served, messages processed, cache hits, errors - across a fleet of instances is dominated by increments, with the total read only occasionally. `increment` and `add` are cheap and lock-free on each node, while `sum` accumulates the cluster-wide total when a metrics scrape or dashboard needs it.

```java
RLongAdder requests = redisson.getLongAdder("metrics:requests");

// hot path on every node - contention-free
requests.increment();
requests.add(batchSize);

// read the cluster-wide total occasionally, e.g. on a metrics scrape
long total = requests.sum();
```

**Windowed Counters with Reset**

For per-interval volume or rate counters, the adder accumulates over a window and is then read and cleared together: `sum` captures the window's total and `reset` returns the counter to zero for the next interval. Call `destroy` once the counter is no longer used.

```java
RLongAdder perWindow = redisson.getLongAdder("metrics:requests:window");

perWindow.add(observed);

// at the end of the interval: capture the total, then start the next window
long windowTotal = perWindow.sum();
perWindow.reset();

// release client-side resources when the counter is retired
perWindow.destroy();
```

## DoubleAdder
Java implementation of Valkey or Redis based [DoubleAdder](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RDoubleAdder.html) object provides API similar to [java.util.concurrent.atomic.DoubleAdder](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/DoubleAdder.html) object.

Each client keeps its own internal `DoubleAdder` and updates it in memory, so `add`, `increment`, and `decrement` complete locally without a network round trip per call - up to __12000x__ faster than a comparable `AtomicDouble` under contention. As with `RLongAdder`, the cost is paid on reads: `sum` coordinates with every `RDoubleAdder` instance to accumulate their local values, and `reset` clears them all. That makes it a fit for distributed metric counters that accumulate fractional amounts across many nodes and are read only occasionally.

Code examples:

=== "Sync"
	```
	RDoubleAdder atomicDouble = redisson.getDoubleAdder("myDoubleAdder");
	
	atomicDouble.add(12.5);
	atomicDouble.increment();
	atomicDouble.decrement();
	
	double sum = atomicDouble.sum();   // accumulated across all instances
	atomicDouble.reset();              // cleared on all instances
	```
=== "Async"
	```
	RDoubleAdder atomicDouble = redisson.getDoubleAdder("myDoubleAdder");
	
	// only sum and reset coordinate across instances, so only they have async forms
	RFuture<Double> sumFuture = atomicDouble.sumAsync();
	RFuture<Void> resetFuture = atomicDouble.resetAsync();
	```

**Bounded coordination with a timeout**

Like `RLongAdder`, the asynchronous `sum` and `reset` accept an optional timeout that bounds how long to wait for all instances to respond before completing.

=== "Async"
	```
	RDoubleAdder atomicDouble = redisson.getDoubleAdder("myDoubleAdder");
	
	RFuture<Double> sumFuture = atomicDouble.sumAsync(3, TimeUnit.SECONDS);
	RFuture<Void> resetFuture = atomicDouble.resetAsync(3, TimeUnit.SECONDS);
	```

**Lifecycle**

As with `RLongAdder`, call `destroy` once the counter is no longer used to release its client-side state. This is not necessary if Redisson itself is shutting down.

```java
RDoubleAdder atomicDouble = ...
atomicDouble.destroy();
```

### Use Cases

DoubleAdder is the floating-point counterpart: it accumulates a `double` total on the client side with the same contention-free, high-throughput add and decrement, reading the aggregate with `sum`. It fits write-heavy accumulation of fractional quantities across instances.

**Aggregating Fractional Quantities**

Summing non-integer measurements at a high rate - request latencies in seconds, bytes-per-second samples, sensor readings - across many instances suits a double accumulator. `add` records each measurement without contention, and `sum` reads the running total for reporting.

```java
RDoubleAdder latency = redisson.getDoubleAdder("metrics:latency-seconds");

// record each observation, contention-free on every node
latency.add(requestSeconds);

// running total across all instances, read when reporting
double totalSeconds = latency.sum();
```

**Running Monetary or Measurement Totals**

Accumulating decimal amounts - revenue booked, cost accrued, kilograms processed - over a reporting window uses the same pattern: `add` each amount, then `sum` and `reset` to close the window and begin the next. Call `destroy` when the accumulator is retired.

```java
RDoubleAdder revenue = redisson.getDoubleAdder("metrics:revenue");

revenue.add(orderAmount);

// close the reporting window: read the total, then reset for the next
double windowTotal = revenue.sum();
revenue.reset();

revenue.destroy();
```