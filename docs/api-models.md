## Synchronous and Asynchronous API

Redisson instances are fully thread-safe. 

Synchronous and Asynchronous API could be reached via [RedissonClient](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RedissonClient.html) interface.  

Most Redisson objects extend asynchronous interface with asynchronous methods which mirrors synchronous methods. Like below:
```java
// RAtomicLong extends RAtomicLongAsync
RAtomicLong obj = client.getAtomicLong("myLong");
obj.compareAndSet(1, 401);

RAtomicLongAsync objAsync = client.getAtomicLong("myLong");
RFuture<Boolean> future = objAsync.compareAndSetAsync(1, 401);
```
Asynchronous methods return [RFuture](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RFuture.html) object which extends [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html) interface.

```java
future.whenComplete((res, exception) -> {

    // handle both result and exception

});


// or
future.thenAccept(res -> {

    // handle result

}).exceptionally(exception -> {

    // handle exception

});

```

!!! note
    Avoid using blocking methods in RFuture listeners. Listeners executed by netty-threads and delays in listeners may cause errors in Redis or Valkey request/response processing. 

Use the following methods to execute blocking methods in listeners:

```java
future.whenCompleteAsync((res, exception) -> {

    // handle both result and exception

}, executor);


// or
future.thenAcceptAsync(res -> {

    // handle result

}, executor).exceptionallyAsync(exception -> {

    // handle exception

}, executor);
```

## Reactive API

Reactive API could be reached via [RedissonReactiveClient](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RedissonReactiveClient.html) interface.

Redisson's implementation based on [Project Reactor](https://projectreactor.io).  

Usage example:  

```java
RedissonReactiveClient client = redissonClient.reactive();

RAtomicLongReactive atomicLong = client.getAtomicLong("myLong");
Mono<Boolean> cs = longObject.compareAndSet(10, 91);
Mono<Long> get = longObject.get();

get.doOnSuccess(res -> {
   // ...
}).subscribe();
```

## RxJava API

RxJava API could be reached via [RedissonRxClient](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RedissonRxClient.html) interface.

Redisson's implementation based on [RxJava3](https://github.com/ReactiveX/RxJava).  

Usage example:  

```java
RedissonRxClient client = redissonClient.rxJava();

RAtomicLongRx atomicLong = client.getAtomicLong("myLong");
Single<Boolean> cs = longObject.compareAndSet(10, 91);
Single<Long> get = longObject.get();

get.doOnSuccess(res -> {
   // ...
}).subscribe();
```

## Command Execution Reliability

When building applications that use Valkey or Redis, ensuring reliable command execution is crucial for maintaining data consistency and application stability. Redisson handles command failures, network interruptions, and connection issues through configurable retry and reconnection strategies.

Redisson implements a multi-layered approach to command execution reliability that addresses different types of failures:

- **Command-Level Retries**: When a Valkey or Redis command fails due to network issues or temporary server unavailability, Redisson can automatically retry the operation based on configured parameters.

- **Connection-Level Recovery**: When connections are broken or become unavailable, Redisson handles reconnection attempts to restore connectivity to Valkey or Redis servers. Applied to any type of Valkey and Redis nodes.

- **Server-Level Recovery**: Redisson allows to implement a custom logic via `failedSlaveNodeDetector` to mark secondary node as failed. Redisson handles reconnection attempts to restore connectivity to failed Valkey or Redis servers.

- **Timeout Management**: Commands that exceed specified time limits are handled gracefully with configurable timeout policies.

### Retry Settings

**timeout**

Defines Valkey or Redis server response timeout. Starts to countdown when a command was successfully sent. The default value is `3000` milliseconds.

**retryAttempts**  

Defines the maximum number of retry attempts for failed commands. The default value is `4` attempts. This parameter determines how many times Redisson will attempt to execute a command throwing an exception.

**retryDelay**  

Defines the delay strategy for a new attempt to send a command. The default value is `EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))`. This creates a delay between consecutive retry attempts to avoid overwhelming the Valkey or Redis server.

Available implementations:

- `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
- `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
- `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
- `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

Settings above can be overridden per Redisson object instance. These settings apply to each method of a given Redisson object instance.

Below is the example with `RBucket` object:
```java
Config config = new Config();
config.useSingleServer()
        .setRetryAttempts(2)
        .setRetryDelay(new EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2)))
        .setTimeout(5000)
        .setAddress("redis://127.0.0.1:6789");


RedissonClient client = Redisson.create(config);

// instance uses global retryInterval and timeout parameters
RBucket<MyObject> bucket = client.getBucket('myObject');

// instance with overridden retryInterval and timeout parameters
RBucket<MyObject> bucket = client.getBucket(PlainOptions.name('myObject')
                                                        .timeout(Duration.ofSeconds(3))
                                                        .retryDelay(new EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2)));
```

### Reconnection Settings

**reconnectionDelay**

Defines the delay strategy for a new attempt to reconnect a broken connection. The default value is `EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))`.

Available implementations:

- `org.redisson.config.DecorrelatedJitterDelay` - Decorrelated jitter strategy that increases delay exponentially while introducing randomness influenced by the previous backoff duration.
- `org.redisson.config.EqualJitterDelay` - Equal jitter strategy that introduces moderate randomness while maintaining some stability of delay value.
- `org.redisson.config.FullJitterDelay` - Full jitter strategy that applies complete randomization to the exponential backoff delay.
- `org.redisson.config.ConstantDelay` - A constant delay strategy that returns the same delay duration for every retry attempt.

### Failed Nodes Detection Settings

**failedSlaveReconnectionInterval**

Once the defined retry interval has elapsed, Redisson attempts to connect to the failed Redis node reported by the `failedSlaveNodeDetector`.

**failedSlaveNodeDetector**

Defines the failed Valkey or Redis secondary node detector object which implements failed node detection logic via `org.redisson.client.FailedNodeDetector` interface. The default value is `org.redisson.client.FailedConnectionDetector`

Available implementations:

- `org.redisson.client.FailedConnectionDetector` - marks the Redis or Valkey node as failed if it has ongoing connection errors in the defined checkInterval interval (in milliseconds). Default is 180000 milliseconds.
- `org.redisson.client.FailedCommandsDetector` - marks the Redis or Valkey node as failed if it has certain amount of command execution errors defined by failedCommandsLimit in the defined checkInterval interval (in milliseconds).
- `org.redisson.client.FailedCommandsTimeoutDetector` - marks the Redis or Valkey node as failed if it has a certain amount of command execution timeout errors defined by failedCommandsLimit in the defined checkInterval interval in milliseconds.