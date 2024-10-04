### Synchronous and Asynchronous API

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
Avoid to use blocking methods in future listeners. Listeners executed by netty-threads and delays in listeners may cause errors in Redis or Valkey request/response processing. Use follow methods to execute blocking methods in listeners:

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

### Reactive API

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

### RxJava API

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

### Retry policy

Redisson implements auto-retry policy per operation. Retry policy is controlled by [retryAttempts](configuration.md) and [retryInterval](configuration.md) settings. These settings are applied to each Redisson object. [timeout](configuration.md) setting is applied when the Redis or Valkey command was successfully sent. 

Settings above can be overridden per Redisson object instance. These settings apply to each method of a given Redisson object instance.

Here is an example with `RBucket` object:
```java
RedissonClient client = Redisson.create(config);
RBucket<MyObject> bucket = client.getBucket('myObject');

// sync way
bucket.get();
// async way
RFuture<MyObject> result = bucket.getAsync();

// instance with overridden retryInterval and timeout parameters
RBucket<MyObject> bucket = client.getBucket(PlainOptions.name('myObject')
                                                        .timeout(Duration.ofSeconds(3))
                                                        .retryInterval(Duration.ofSeconds(5)));

```