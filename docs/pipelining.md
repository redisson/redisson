Multiple commands can be sent in a batch using `RBatch` object in a single network call. Command batches allows to reduce the overall execution time of a group of commands. In Redis or Valkey this approach called [Pipelining](https://redis.io/topics/pipelining). 
Follow options could be supplied during object creation:
```java
BatchOptions options = BatchOptions.defaults()
// Sets execution mode
//
// ExecutionMode.REDIS_READ_ATOMIC - Store batched invocations in Redis and execute them atomically as a single command
//
// ExecutionMode.REDIS_WRITE_ATOMIC - Store batched invocations in Redis and execute them atomically as a single command
//
// ExecutionMode.IN_MEMORY - Store batched invocations in memory on Redisson side and execute them on Redis. Default mode
//
// ExecutionMode.IN_MEMORY_ATOMIC - Store batched invocations on Redisson side and executes them atomically on Redis or Valkey as a single command
.executionMode(ExecutionMode.IN_MEMORY)

// Inform Redis or Valkey not to send reply back to client. This allows to save network traffic for commands with batch with big
.skipResult()

// Synchronize write operations execution across defined amount of Redis or Valkey slave nodes
//
// sync with 2 slaves with 1 second for timeout
.syncSlaves(2, 1, TimeUnit.SECONDS)

// Response timeout
.responseTimeout(2, TimeUnit.SECONDS)

// Retry interval for each attempt to send Redis or Valkey commands batch
.retryInterval(2, TimeUnit.SECONDS);

// Attempts amount to re-send Redis or Valkey commands batch if it wasn't sent due to network delays or other issues
.retryAttempts(4);
```

Result Batch object contains follow data:
```java
// list of result objects per command in batch
List<?> responses = res.getResponses();
// amount of successfully synchronized slaves during batch execution
int slaves = res.getSyncedSlaves();
```
Code example for **Sync / Async** mode:
```java
RBatch batch = redisson.createBatch(BatchOptions.defaults());
batch.getMap("test1").fastPutAsync("1", "2");
batch.getMap("test2").fastPutAsync("2", "3");
batch.getMap("test3").putAsync("2", "5");
RFuture<Long> future = batch.getAtomicLong("counter").incrementAndGetAsync();
batch.getAtomicLong("counter").incrementAndGetAsync();

// result could be acquired through RFuture object returned by batched method
// or 
// through result list by corresponding index
future.whenComplete((res, exception) -> {
    // ...
});

BatchResult res = batch.execute();
// or
Future<BatchResult> resFuture = batch.executeAsync();

List<?> list = res.getResponses();
Long result = list.get(4);
```

Code example of **[Reactive](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTransactionReactive.html) interface** usage:
```java
RBatchReactive batch = redisson.createBatch(BatchOptions.defaults());
batch.getMap("test1").fastPut("1", "2");
batch.getMap("test2").fastPut("2", "3");
batch.getMap("test3").put("2", "5");
Mono<Long> commandMono = batch.getAtomicLongAsync("counter").incrementAndGet();
batch.getAtomicLongAsync("counter").incrementAndGet();

// result could be acquired through Reactive object returned by batched method
// or 
// through result list by corresponding index
commandMono.doOnNext(res -> {
   // ...
}).subscribe();

Mono<BatchResult> resMono = batch.execute();
resMono.doOnNext(res -> {
   List<?> list = res.getResponses();
   Long result = list.get(4);

   // ...
}).subscribe();
```

Code example of **[RxJava3](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTransactionRx.html) interface** usage:
```java
RBatchRx batch = redisson.createBatch(BatchOptions.defaults());
batch.getMap("test1").fastPut("1", "2");
batch.getMap("test2").fastPut("2", "3");
batch.getMap("test3").put("2", "5");
Single<Long> commandSingle = batch.getAtomicLongAsync("counter").incrementAndGet();
batch.getAtomicLongAsync("counter").incrementAndGet();

// result could be acquired through RxJava3 object returned by batched method
// or 
// through result list by corresponding index
commandSingle.doOnSuccess(res -> {
   // ...
}).subscribe();

Mono<BatchResult> resSingle = batch.execute();
resSingle.doOnSuccess(res -> {
   List<?> list = res.getResponses();
   Long result = list.get(4);

   // ...
}).subscribe();
```

In cluster environment batch executed in map\reduce way. It aggregates commands for each node and sends them simultaneously, then result got from each node added to common result list.
