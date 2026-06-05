Multiple commands can be sent together in a single network round trip using the `RBatch` object, which reduces the overall execution time of a group of commands. In Valkey and Redis this technique is called [pipelining](https://redis.io/topics/pipelining).

A batch is configured through `BatchOptions`:

```java
BatchOptions options = BatchOptions.defaults()
    // execution mode:
    //   ExecutionMode.REDIS_READ_ATOMIC  - store the commands on the server and run them atomically as a single read
    //   ExecutionMode.REDIS_WRITE_ATOMIC - store the commands on the server and run them atomically as a single write
    //   ExecutionMode.IN_MEMORY          - buffer the commands on the Redisson side, then send them (default)
    //   ExecutionMode.IN_MEMORY_ATOMIC   - buffer the commands on the Redisson side, then run them atomically as a single command
    .executionMode(ExecutionMode.IN_MEMORY)

    // do not send replies back, saving network traffic for batches with many commands
    .skipResult()

    // wait for the writes to reach the given number of replicas (here: 2 replicas, 1 second timeout)
    .syncSlaves(2, 1, TimeUnit.SECONDS)

    // overall response timeout
    .responseTimeout(2, TimeUnit.SECONDS)

    // interval between attempts to resend the batch
    .retryInterval(2, TimeUnit.SECONDS)

    // number of attempts to resend the batch if it could not be sent due to network issues
    .retryAttempts(4);
```

The returned `BatchResult` holds the per-command results and replication information:

```java
// list of results, one per command, in execution order
List<?> responses = res.getResponses();
// number of replicas the writes were synchronized to during execution
int slaves = res.getSyncedSlaves();
```

Add commands to the batch by calling the asynchronous methods on its objects, then run the whole batch with `execute` (or `executeAsync`). Each command's result can be read either from the future or publisher it returns, or from the `BatchResult` responses list by index:

=== "Sync / Async"
    ```java
    RBatch batch = redisson.createBatch(BatchOptions.defaults());
    batch.getMap("test1").fastPutAsync("1", "2");
    batch.getMap("test2").fastPutAsync("2", "3");
    batch.getMap("test3").putAsync("2", "5");
    RFuture<Long> future = batch.getAtomicLong("counter").incrementAndGetAsync();
    batch.getAtomicLong("counter").incrementAndGetAsync();
    
    // read a single result from the returned future
    future.whenComplete((res, exception) -> {
        // ...
    });
    
    // run the batch, then read results by index
    BatchResult<?> res = batch.execute();
    // or
    RFuture<BatchResult<?>> resFuture = batch.executeAsync();
    
    List<?> list = res.getResponses();
    Long result = (Long) list.get(4);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RBatchReactive batch = redisson.createBatch(BatchOptions.defaults());
    batch.getMap("test1").fastPut("1", "2");
    batch.getMap("test2").fastPut("2", "3");
    batch.getMap("test3").put("2", "5");
    Mono<Long> commandMono = batch.getAtomicLong("counter").incrementAndGet();
    batch.getAtomicLong("counter").incrementAndGet();
    
    // read a single result from the returned publisher
    commandMono.doOnNext(res -> {
        // ...
    }).subscribe();
    
    // run the batch, then read results by index
    Mono<BatchResult<?>> resMono = batch.execute();
    resMono.doOnNext(res -> {
        List<?> list = res.getResponses();
        Long result = (Long) list.get(4);
    }).subscribe();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RBatchRx batch = redisson.createBatch(BatchOptions.defaults());
    batch.getMap("test1").fastPut("1", "2");
    batch.getMap("test2").fastPut("2", "3");
    batch.getMap("test3").put("2", "5");
    Single<Long> commandSingle = batch.getAtomicLong("counter").incrementAndGet();
    batch.getAtomicLong("counter").incrementAndGet();
    
    // read a single result from the returned publisher
    commandSingle.doOnSuccess(res -> {
        // ...
    }).subscribe();
    
    // run the batch, then read results by index
    Maybe<BatchResult<?>> resMaybe = batch.execute();
    resMaybe.doOnSuccess(res -> {
        List<?> list = res.getResponses();
        Long result = (Long) list.get(4);
    }).subscribe();
    ```

In a cluster, the batch is executed in a map/reduce fashion: Redisson groups the commands per node, sends the groups simultaneously, and merges the per-node results into a single response list.
