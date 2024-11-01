## Remote service
Redisson provides Java Remote Services to execute remote procedure call using Redis or Valkey. Remote interface could have any type of method parameters and result object. Redis or Valkey is used to store method request and corresponding execution result.

The RemoteService provides two types of [RRemoteService](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RRemoteService.html) instances:

* __Server side instance__ - executes remote method (worker instance).
Example:
```java
RRemoteService remoteService = redisson.getRemoteService();
SomeServiceImpl someServiceImpl = new SomeServiceImpl();

// register remote service before any remote invocation
// can handle only 1 invocation concurrently
remoteService.register(SomeServiceInterface.class, someServiceImpl);

// register remote service able to handle up to 12 invocations concurrently
remoteService.register(SomeServiceInterface.class, someServiceImpl, 12);
```
* __Client side instance__ - invokes remote method.
Example:
```java
RRemoteService remoteService = redisson.getRemoteService();
SomeServiceInterface service = remoteService.get(SomeServiceInterface.class);

String result = service.doSomeStuff(1L, "secondParam", new AnyParam());
```

Client and server side instances shall be using the same remote interface and backed by redisson instances created using the same server connection configuration. Client and server side instances could be run in same JVM. There are no limits to the amount of client and/or server instances.
(Note: While redisson does not enforce any limits, [limitations from Redis or Valkey still apply](https://redis.io/topics/faq#what-is-the-maximum-number-of-keys-a-single-redis-instance-can-hold-and-what-the-max-number-of-elements-in-a-hash-list-set-sorted-set).)

Remote invocations executes in __parallel mode__ if __1+__ workers are available.

<img src="https://redisson.org/remoteService.png" width="470">

The total number of parallel executors is calculated as such:
`T` = `R` * `N`

`T` - total available parallel executors
`R` - Redisson server side instance amount
`N` - executors amount defined during service registration

Commands exceeding this number will be queued for the next available executor.

Remote invocations executes in __sequential mode__ if only __1__ workers are available. Only one command can be handled concurrently in this case and the rest of commands will be queued.

<img src="https://redisson.org/remoteService2.png" height="470">

### Message flow
RemoteService creates two queues per invocation. One queue for request (being listened by server side instance) and another one is for ack-response and result-response (being listened by client side instance). Ack-response used to determine if method executor has got a request. If it doesn't during ack timeout then `RemoteServiceAckTimeoutException` will be thrown.

 Below is depicted a message flow for each remote invocation.

<img src="https://redisson.org/remoteService3.png" height="561">

### Fire-and-forget and ack-response modes
RemoteService options for each remote invocation could be defined via [RemoteInvocationOptions](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RemoteInvocationOptions.html) object. Such options allow to change timeouts and skip ack-response and/or result-response. Examples:

```java
// 1 second ack timeout and 30 seconds execution timeout
RemoteInvocationOptions options = RemoteInvocationOptions.defaults();

// no ack but 30 seconds execution timeout
RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noAck();

// 1 second ack timeout then forget the result
RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noResult();

// 1 minute ack timeout then forget about the result
RemoteInvocationOptions options = RemoteInvocationOptions.defaults().expectAckWithin(1, TimeUnit.MINUTES).noResult();

// no ack and forget about the result (fire and forget)
RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noAck().noResult();

RRemoteService remoteService = redisson.getRemoteService();
YourService service = remoteService.get(YourService.class, options);
```
### Asynchronous, Reactive and RxJava3 calls
Remote method could be executed using Async, Reactive and RxJava3 Api. 

**Asynchronous Remote interface**. Interface should be annotated with [@RRemoteAsync](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/annotation/RRemoteAsync.html). Method signatures match with the methods in remote interface and return [RFuture](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RFuture.html) object.

**Reactive Remote interface**. Interface should be annotated with [@RRemoteReactive](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/annotation/RRemoteReactive.html). Method signatures match with the methods in remote interface and return `reactor.core.publisher.Mono` object. 

**RxJava3 Remote interface**. Interface should be annotated with [@RRemoteRx](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/annotation/RRemoteRx.html). Method signatures match with the methods in remote interface and return one of the following object: `io.reactivex.Completable`, `io.reactivex.Single`, `io.reactivex.Maybe`. 

It's not necessary to list all methods, only those which are needed. Below is an example of Remote Service interface:

```java
public interface RemoteInterface {

    Long someMethod1(Long param1, String param2);

    void someMethod2(MyObject param);

    MyObject someMethod3();

}
```

**Asynchronous Remote interface** and method call example:
```java
@RRemoteAsync(RemoteInterface.class)
public interface RemoteInterfaceAsync {

    RFuture<Long> someMethod1(Long param1, String param2);

    RFuture<Void> someMethod2(MyObject param);

}

RedissonClient redisson = Redisson.create(config);

RRemoteService remoteService = redisson.getRemoteService();
RemoteInterfaceAsync asyncService = remoteService.get(RemoteInterfaceAsync.class);

asyncService.someMethod1(1L, "myparam");
```

**Reactive Remote interface** and method call example:
```java
@RRemoteReactive(RemoteInterface.class)
public interface RemoteInterfaceReactive {

    Mono<Long> someMethod1(Long param1, String param2);

    Mono<Void> someMethod2(MyObject param);

}

RedissonReactiveClient redisson = Redisson.createReactive(config);

RRemoteService remoteService = redisson.getRemoteService();
RemoteInterfaceReactive reactiveService = remoteService.get(RemoteInterfaceReactive.class);

reactiveService.someMethod1(1L, "myparam");
```

**RxJava3 Remote interface** and method call example:
```java
@RRemoteRx(RemoteInterface.class)
public interface RemoteInterfaceRx {

    Single<Long> someMethod1(Long param1, String param2);

    Completable someMethod2(MyObject param);

}

RedissonRxClient redisson = Redisson.createRx(config);

RRemoteService remoteService = redisson.getRemoteService();
RemoteInterfaceReactive rxService = remoteService.get(RemoteInterfaceRx.class);

rxService.someMethod1(1L, "myparam");
```

### Asynchronous, Reactive and RxJava3 call cancellation
Remote service provides ability to cancel invocation in any stages of its execution. There are three stages:

1. Remote invocation request in queue
2. Remote invocation request received by remote service but not lunched and Ack-response hasn't send yet
3. Remote invocation execution in progress

To handle third stage you need to check for `Thread.currentThread().isInterrupted()` status in your Remote service code. Here is an example:

```java
public interface MyRemoteInterface {

    Long myBusyMethod(Long param1, String param2);

}

@RRemoteAsync(MyRemoteInterface.class)
public interface MyRemoteInterfaceAsync {

    RFuture<Long> myBusyMethod(Long param1, String param2);

}

@RRemoteReactive(MyRemoteInterface.class)
public interface MyRemoteInterfaceReactive {

    Mono<Long> myBusyMethod(Long param1, String param2);

}

@RRemoteRx(MyRemoteInterface.class)
public interface MyRemoteInterfaceRx {

    Single<Long> myBusyMethod(Long param1, String param2);

}


// remote service implementation
public class MyRemoteServiceImpl implements MyRemoteInterface {

   public Long myBusyMethod(Long param1, String param2) {
       for (long i = 0; i < Long.MAX_VALUE; i++) {
           iterations.incrementAndGet();
           if (Thread.currentThread().isInterrupted()) {
                System.out.println("interrupted! " + i);
                return;
           }
       }
   }

}

RRemoteService remoteService = redisson.getRemoteService();
ExecutorService executor = Executors.newFixedThreadPool(5);
// register remote service using separate
// ExecutorService used to execute remote invocation
MyRemoteInterface serviceImpl = new MyRemoteServiceImpl();
remoteService.register(MyRemoteInterface.class, serviceImpl, 5, executor);

// call Asynchronous method
MyRemoteInterfaceAsync asyncService = remoteService.get(MyRemoteInterfaceAsync.class);
RFuture<Long> future = asyncService.myBusyMethod(1L, "someparam");
// cancel invocation
future.cancel(true);

// call Reactive method
MyRemoteInterfaceReactive reactiveService = remoteService.get(MyRemoteInterfaceReactive.class);
Mono<Long> mono = reactiveService.myBusyMethod(1L, "someparam");
Disposable disp = mono.doOnSubscribe(s -> s.request(1)).subscribe();
// cancel invocation
disp.dispose();

// call RxJava3 method
MyRemoteInterfaceRx asyncService = remoteService.get(MyRemoteInterfaceRx.class);
Single<Long> single = asyncService.myBusyMethod(1L, "someparam");
Disposable disp = single.subscribe();
// cancel invocation
disp.dispose();
```
## Live Object service

### Introduction
A **Live Object** can be understood as an enhanced version of standard Java object, of which an instance reference can be shared not only between threads in a single JVM, but can also be shared between different JVMs across different machines.Wikipedia discribes it as:
> Live distributed object (also abbreviated as live object) refers to a running instance of a distributed multi-party (or peer-to-peer) protocol, viewed from the object-oriented perspective, as an entity that has a distinct identity, may encapsulate internal state and threads of execution, and that exhibits a well-defined externally visible behavior.

Redisson Live Object (RLO) realised this idea by mapping all the fields inside a Java class to a HASH through a runtime-constructed proxy class. All the getters/setters methods of each field are translated to hget/hset commands operated on the HASH, making it accessable to/from any clients connected to the same Redis or Valkey server. Getters/setters/constructors can't be generated by byte-code tools like Lombok. Additional methods should use getters and not fields. As we all know, the field values of an object represent its state; having them stored in a remote repository, redis, makes it a distributed object. This object is a Redisson Live Object.

By using RLO, sharing an object between applications and/or servers is the same as sharing one in a standalone application. This removes the need for serialization and deserialization, and at the same time reduces the complexity of the programming model: Changes made to one field is (almost^) immediately accessable to other processes, applications and servers.

Since the Redis or Valkey server is a single-threaded application, all field access to the live object is automatically executed in atomic fashion: a value will not be changed when you are reading it.

With RLO, you can treat the Redis or Valkey server as a shared Heap space for all connected JVMs.

### Usage
Redisson provides different [annotations](#annotations) for Live Object. `@RId` and `@REntity` annotation are required to create and use Live Object.

```java
@REntity
public class MyObject {

    @RId
    private String id;
    @RIndex
    private String value;
    private transient SimpleObject simpleObject;
    private MyObject parent;

    public MyObject(String id) {
        this.id = id;
    }

    public MyObject() {
    }

    // getters and setters

}
```

Please note: fields marked with `transient` keyword aren't stored in Redis or Valkey.

To start use it you should use one of methods below:

`RLiveObjectService.attach()` - attaches object to Redis or Valkey. Discard all the field values already in the detached instance  
`RLiveObjectService.merge()` - overrides current object state in Redis or Valkey with the given object state  
`RLiveObjectService.persist()` - stores only new object  

Code example:

```java
RLiveObjectService service = redisson.getLiveObjectService();
MyLiveObject myObject = new MyLiveObject();
myObject1.setId("1");
// current state of myObject is now persisted and attached to Redis or Valkey
myObject = service.persist(myObject);

MyLiveObject myObject = new MyLiveObject("1");
// current state of myObject is now cleared and attached to Redis or Valkey
myObject = service.attach(myObject);

MyLiveObject myObject = new MyLiveObject();
myObject.setId("1");
// current state of myObject is now merged to already existed object and attached to Redis
myObject = service.merge(myObject);
myObject.setValue("somevalue");

// get Live Object by Id
MyLiveObject myObject = service.get(MyLiveObject.class, "1");

// find Live Objects by value field
Collection<MyLiveObject> myObjects = service.find(MyLiveObject.class, Conditions.in("value", "somevalue", "somevalue2"));

Collection<MyLiveObject> myObjects = service.find(MyLiveObject.class, Conditions.and(Conditions.in("value", "somevalue", "somevalue2"), Conditions.eq("secondfield", "test")));
```
"parent" field has a link to another instances of Live Object of the same type, but the type could be different. Redisson stores this link into Redis or Valkey as a reference object and not the whole object state, so you continue to work with reference object as Live Object.
```java
//Redisson Live Object behavior:
MyObject myObject = service.get(MyObject.class, "1");
MyObject myParentObject = service.get(MyObject.class, "2");
myObject.setValue(myParentObject);
```

Field types in the RLO can be almost anything, from Java util classes to collection/map types and of course your own custom objects, as long as it can be encoded and decoded by a supplied codec. More details about the codec can be found in the [Advanced Usage](#advanced-usage) section.

In order to keep RLOs behaving as closely to standard Java objects as possible, Redisson automatically converts the following standard Java field types to its counter types supported by Redisson `RObject`.

Standard Java Class | Converted Redisson Class
------------ | ------------
SortedSet.class | RedissonSortedSet.class
Set.class | RedissonSet.class
ConcurrentMap.class | RedissonMap.class
Map.class | RedissonMap.class
BlockingDeque.class | RedissonBlockingDeque.class
Deque.class | RedissonDeque.class
BlockingQueue.class | RedissonBlockingQueue.class
Queue.class | RedissonQueue.class
List.class | RedissonList.class

The conversion prefers the one nearer to the top of the table if a field type matches more than one entries. i.e. `LinkedList` implements `Deque`, `List`, `Queue`, it will be converted to a `RedissonDeque` because of this.

Instances of these Redisson classes retains their states/values/entries in Redis or Valkey too, changes to them are directly reflected into database without keeping values in local VM.

### Search by Object properties

Redisson provides comprehensive search engine for RLO objects. To make a property participate in search it should be annotated with `@RIndex` annotation.  

!!! note "The open-source version of the search engine is not optimized!" 
    Use [Redisson PRO](https://redisson.pro) for **ultra-fast search engine**, **low JVM memory consumption during search process** and **search index partitiong in cluster**.

Usage example:

```java
@REntity
public class MyObject {

    @RId
    private String id;

    @RIndex
    private String field1;

    @RIndex
    private Integer field2;
    
    @RIndex
    private Long field3;
}
```

Different type of search conditions are available:

`Conditions.and()` - **AND** condition for collection of nested conditions  
`Conditions.eq()`  - **EQUALS** condition which restricts property to defined value  
`Conditions.or()`  - **OR** condition for collection of nested conditions  
`Conditions.in()`  - **IN** condition which restricts property to set of defined values  
`Conditions.gt()`  - **GREATER THAN** condition which restricts property to defined value  
`Conditions.ge()`  - **GREATER THAN ON EQUAL** condition which restricts property to defined value  
`Conditions.lt()`  - **LESS THAN** condition which restricts property to defined value  
`Conditions.le()`  - **LESS THAN ON EQUAL** condition which restricts property to defined value  

Once object stored in Redis or Valkey we can run search:

```java
RLiveObjectService liveObjectService = redisson.getLiveObjectService();

liveObjectService.persist(new MyObject());

// find all objects where field1 = value and field2 < 12 or field1 = value2 and field2 > 23 or field3 in [1, 2]

Collection<MyObject> objects = liveObjectService.find(MyObject.class, 
            Conditions.or(Conditions.and(Conditions.eq("field1", "value"), Conditions.lt("field2", 12)), 
                          Conditions.and(Conditions.eq("field1", "value2"), Conditions.gt("field2", 23)), 
                          Conditions.in("field3", 1L, 2L));
```

Search index expires after `expireXXX()` method call only if the Redis or Valkey `notify-keyspace-events` setting contains the letters `Kx`.

### Local Cache

_This feature is available only in [Redisson PRO](https://redisson.pro) edition._

RLO objects can be cached in local cache on the Redisson side.

**local cache** - so called near cache used to speed up read operations and avoid network roundtrips. It caches JSON Store entries on Redisson side and executes read operations up to **45x faster** in comparison with regular implementation. Local cached instances with the same name are connected to the same pub/sub channel. This channel is used for exchanging of update/invalidate events between all instances. Local cache store doesn't use `hashCode()`/`equals()` methods of key object, instead it uses hash of serialized state.

To make an RLO entity stored in local cache it should be annotated with [@RCache](#annotations) annotation. 

Usage example:

```java
@REntity
@RCache(cacheSize = 10, evictionPolicy = EvictionPolicy.LRU)
public class MyObject {

    @RId
    private String id;

    private String name;

    private Integer counter;
    
}


RLiveObjectService service = redisson.getLiveObjectService();

MyObject obj = new MyObject();
obj.setId("1");
obj.setName("Simple name");

// current state of myObject is now persisted and attached to Redis or Valkey
myObject = service.persist(obj);

// loaded from local cache
String n = myObject.getName();
```


### Advanced Usage
As described before, RLO classes are proxy classes which can be fabricated when needed and then get cached in a `RedissonClient` instance against its original class. This process can be a bit slow and it is recommended to pre-register all the Redisson Live Object classes via `RedissonLiveObjectService` for any kind of delay-sensitive applications. The service can also be used to unregister a class if it is no longer needed. And of course it can be used to check if the class has already been registered.

```java
RLiveObjectService service = redisson.getLiveObjectService();
service.registerClass(MyClass.class);
service.unregisterClass(MyClass.class);
Boolean registered = service.isClassRegistered(MyClass.class);
```

### Annotations

**@REntity**  
Applied to a class. The behaviour of each type of RLO can be customised through properties of the `@REntity` annotation. You can specify each of those properties to gain fine control over its behaviour:

* `namingScheme` - You can specify a naming scheme which tells Redisson how to assign key names for each instance of this class. It is used to create a reference to an existing Redisson Live Object and materialising a new one in redis. It defaults to use Redisson provided `DefaultNamingScheme`.
* `codec` - You can tell Redisson which `Codec` class you want to use for the RLO. Redisson will use an instance pool to locate the instance based on the class type. It defaults to `JsonJacksonCodec` provided by Redisson.
* `fieldTransformation` - You can also specify a field transformation mode for the RLO. As mentioned before, in order to keep everything as close to standard Java as possible, Redisson will automatically transform fields with commonly-used Java util classes to Redisson compatible classes. This uses `ANNOTATION_BASED` as the default value. You can set it to `IMPLEMENTATION_BASED` which will skip the transformation.

**@RCache**  
Applied to a class. Allows to store in local cache each instance of the defined class. Annotation settings:  

* `storeCacheMiss` - defines whether to store a cache miss into the local cache. Default value is `false`.
* `storeMode` - defines store mode of cache data. Default value is `LOCALCACHE_REDIS`. Follow options are available:  
    * `LOCALCACHE` - store data in local cache only and use Redis or Valkey only for data update/invalidation.  
    * `LOCALCACHE_REDIS` - store data in both Redis or Valkey and local cache.
* `cacheProvider` - defines Cache provider used as local cache store. Default value is `REDISSON`. Follow options are available:  
    * `REDISSON` - uses Redisson own implementation
    * `CAFFEINE` - uses Caffeine implementation
* `evictionPolicy` - defines local cache eviction policy. Default value is `NONE`. Follow options are available:
    * `LFU` - counts how often an item was requested. Those that are used least often are discarded first.
    * `LRU` - discards the least recently used items first
    * `SOFT` - uses soft references, entries are removed by GC
    * `WEAK` - uses weak references, entries are removed by GC
    * `NONE` - no eviction
* `cacheSize` - local cache size. If cache size is `0` then local cache is unbounded. If size is `-1` then local cache is always empty and doesn't store data. Default value is `0`.
* `reconnectionStrategy` - defines strategy for load missed local cache updates after connection failure. Default value is `NONE`. Follow options are available:
    * `CLEAR` - clear local cache if map instance has been disconnected for a while.
	* `NONE` - no reconnection handling
* `syncStrategy` - defines local cache synchronization strategy. Default value is `INVALIDATE`. Follow options are available:
    * `INVALIDATE` - Default. Invalidate cache entry across all RLocalCachedJsonStore instances on map entry change
    * `UPDATE` - Insert/update cache entry across all RLocalCachedJsonStore instances on map entry change
    * `NONE` - No synchronizations on map changes
* `timeToLive` - defines time to live for each entry in local cache
* `maxIdle` - defines max idle time for each entry in local cache
* `useTopicPattern` - defines whether to use a global topic pattern listener that applies to all local cache instances belonging to the same Redisson instance. Default value is `true`.

**@RId**  
Applied to a field. Defines `primary key` field of this class. The value of this field is used to create a reference to existing RLO. The field with this annotation is the only field that has its value also kept in the local VM. You can only have one `RId` annotation per class.

You can supply a `generator` strategy to the `@RId` annotation if you want the value of this field to be programatically generated. The default generator is `null`.

**@RIndex**  
Applied to a field. Specifies that the field is used in search index. Allows to execute search query based on that field through `RLiveObjectService.find` method.

**@RObjectField**  
Applied to a field. Allows to specify `namingScheme` and/or `codec` different from what is specified in `@REntity`.

**@RCascade**  
Applied to a field. Specifies that the defined cascade types are applied to the object/objects contained in Live Object field.
Different cascade types are available:

* `RCascadeType.ALL` - Includes all cascade types  
* `RCascadeType.PERSIST` - Cascade persist operation during `RLiveObjectService.persist()` method invocation  
* `RCascadeType.DETACH` - Cascade detach operation during `RLiveObjectService.detach()` method invocation  
* `RCascadeType.MERGE` - Cascade merge operation during `RLiveObjectService.merge()` method invocation  
* `RCascadeType.DELETE` - Cascade delete operation during `RLiveObjectService.delete()` method invocation. 

## Executor service

### Overview
Redis or Valkey based [RExecutorService](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RExecutorService.html) object implements of [ExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) interface to run [Callable](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Callable.html), [Runnable](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html) and Lambda tasks. Task and result objects are serialized using defined codec and stored in two request/response Redis or Valkey queues. Redisson instance and task id can be injected in task object. Task id has size 128-bits and globally unique. This allows to process Redis or Valkey data and execute distributed computations in fast and efficient way.

### Workers

Worker runs task submitted through [RExecutorService](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RExecutorService.html) interface. Worker should be registered with `WorkerOptions` settings object. Each worker polls a task from a head of Redis or Valkey queue in order it requested it. Polled tasks are executed with ExecutorService. Global default ExecutorService from Redisson configuration is used if ExecutorService in `WorkerOptions` isn't defined.

Consider to use [Redisson node](./12.-Standalone-node) if you need standalone jar which register and executes workers.

WorkerOptions exposes follow settings:
```java
    WorkerOptions options = WorkerOptions.defaults()
    
    // Defines workers amount used to execute tasks.
    // Default is 1.
    .workers(2)

    // Defines Spring BeanFactory instance to execute tasks 
    // with Spring's '@Autowired', '@Value' or JSR-330's '@Inject' annotation.
    .beanFactory(beanFactory)

    // Defines custom ExecutorService to execute tasks
    // Config.executor is used by default.
    .executorService()

    // Defines task timeout since task execution start moment
    .taskTimeout(60, TimeUnit.SECONDS)

    // add listener which is invoked on task successed event
    .addListener(new TaskSuccessListener() {
         public void onSucceeded(String taskId, T result) {
             // ...
         }
    })

    // add listener which is invoked on task failed event
    .addListener(new TaskFailureListener() {
         public void onFailed(String taskId, Throwable exception) {
             // ...
         }
    })

    // add listener which is invoked on task started event
    .addListener(new TaskStartedListener() {
         public void onStarted(String taskId) {
             // ...
         }
    })

    // add listener which is invoked on task finished event
    .addListener(new TaskFinishedListener() {
         public void onFinished(String taskId) {
             // ...
         }
    });
```

Code example of worker registration with options defined above:
```java
RExecutorService executor = redisson.getExecutorService("myExecutor");
executor.registerWorkers(options);
```

### Tasks
Redisson node doesn't require presence of task classes in classpath. Task classes are loaded automatically by Redisson node ClassLoader. Thus each new task class doesn't require restart of Redisson node.

Example with `Callable` task:
```java
public class CallableTask implements Callable<Long> {

    @RInject
    private RedissonClient redissonClient;

    @RInject
    private String taskId;

    @Override
    public Long call() throws Exception {
        RMap<String, Integer> map = redissonClient.getMap("myMap");
        Long result = 0;
        for (Integer value : map.values()) {
            result += value;
        }
        return result;
    }

}
```

Example with `Runnable` task:
```java
public class RunnableTask implements Runnable {

    @RInject
    private RedissonClient redissonClient;

    @RInject
    private String taskId;

    private long param;

    public RunnableTask() {
    }

    public RunnableTask(long param) {
        this.param = param;
    }

    @Override
    public void run() {
        RAtomicLong atomic = redissonClient.getAtomicLong("myAtomic");
        atomic.addAndGet(param);
    }

}
```

Follow options could be supplied during ExecutorService aquisition:

```java
ExecutorOptions options = ExecutorOptions.defaults()

// Defines task retry interval at the end of which task is executed again.
// ExecutorService worker re-schedule task execution retry every 5 seconds.
//
// Set 0 to disable.
//
// Default is 5 minutes
options.taskRetryInterval(10, TimeUnit.MINUTES);
```

```java
RExecutorService executorService = redisson.getExecutorService("myExecutor", options);
executorService.submit(new RunnableTask(123));

RExecutorService executorService = redisson.getExecutorService("myExecutor", options);
Future<Long> future = executorService.submit(new CallableTask());
Long result = future.get();
```

Example with Lambda task:
```java
RExecutorService executorService = redisson.getExecutorService("myExecutor", options);
Future<Long> future = executorService.submit((Callable & Serializable)() -> {
      System.out.println("task has been executed!");
});
Long result = future.get();
```

Each Redisson node has ready to use RedissonClient which could be injected using `@RInject` annotation.

### Tasks with Spring beans
Redisson allows to inject not only RedissonClient and task id using `@RInject` annotation but also supports Spring's `@Autowire`, `@Value` and JSR-330's `@Inject` annotations. Injected Spring Bean service interface should implement Serializable.

Redisson uses Spring's BeanFactory object for injection. It should be defined in Redisson Node [configuration](../standalone-node.md#settings).

Example with `Callable` task:
```java
public class CallableTask implements Callable<Integer> {

    @Autowired
    private MySpringService service;

    @Value("myProperty")
    private String prop;

    @RInject
    private RedissonClient redissonClient;

    @Override
    public Integer call() throws Exception {
        RMap<String, Integer> map = redissonClient.getMap(prop);

        Integer result = service.someMethod();
        map.put("test", result);
        return result;
    }

}
```

```java
RExecutorService executorService = redisson.getExecutorService("myExecutor", options);
Future<Integer> future = executorService.submit(new CallableTask());
Integer result = future.get();
```

### Task execution cancellation
It's easy to cancel any submitted task via `RFuture.cancel()` or `RExecutorService.cancelTask()` method. To handle case then task execution is already in progress you need to check Thread status for interruption with `Thread.currentThread().isInterrupted()` invocation:
```java
public class CallableTask implements Callable<Long> {

    @RInject
    private RedissonClient redissonClient;

    @Override
    public Long call() throws Exception {
        RMap<String, Integer> map = redissonClient.getMap("myMap");
        Long result = 0;
        // map contains many entries
        for (Integer value : map.values()) {
           if (Thread.currentThread().isInterrupted()) {
                // task has been canceled
                return null;
           }
           result += value;
        }
        return result;
    }

}

RExecutorService executorService = redisson.getExecutorService("myExecutor");

// submit tasks synchronously for asynchronous execution.
RExecutorFuture<Long> future = executorService.submit(new CallableTask());

// submit tasks asynchronously for asynchronous execution.
RExecutorFuture<Long> future = executorService.submitAsync(new CallableTask());

// cancel future
future.cancel(true);

// cancel by taskId
executorService.cancelTask(future.getTaskId());
```
## Scheduled executor service

### Overview
Redis or Valkey based [RScheduledExecutorService](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RScheduledExecutorService.html) implements [ScheduledExecutorService](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledExecutorService.html) interface to schedule [Callable](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Callable.html), [Runnable](https://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html) and Lambda tasks. Scheduled task is a job which needs to be execute in the future at a particular time one or more times. Task and result objects are serialized and stored in request/response Redis or Valkey queues. Redisson instance and task id can be injected into task object. Task id has size 128-bits and globally unique. This allows to process Redis or Valkey data and execute distributed computations in fast and efficient way.

### Workers

Worker runs task submitted through [RScheduledExecutorService](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RScheduledExecutorService.html) interface. Worker should be registered with `WorkerOptions` settings object. Each worker polls a task from head of Redis or Valkey queue in order it requested it. Polled tasks are executed with ExecutorService. Global default ExecutorService from Redisson configuration is used if ExecutorService in `WorkerOptions` isn't defined.

Consider to use [Redisson node](./12.-Standalone-node) if you need standalone jar which register and executes workers.

WorkerOptions exposes follow settings:
```java
    WorkerOptions options = WorkerOptions.defaults()
    
    // Defines workers amount used to execute tasks.
    // Default is 1.
    .workers(2)

    // Defines Spring BeanFactory instance to execute tasks 
    // with Spring's '@Autowired', '@Value' or JSR-330's '@Inject' annotation.
    .beanFactory(beanFactory)

    // Defines custom ExecutorService to execute tasks
    // Config.executor is used by default.
    .executorService()

    // Defines task timeout since task execution start moment
    .taskTimeout(60, TimeUnit.SECONDS)

    // add listener which is invoked on task successed event
    .addListener(new TaskSuccessListener() {
         public void onSucceeded(String taskId, T result) {
             // ...
         }
    })

    // add listener which is invoked on task failed event
    .addListener(new TaskFailureListener() {
         public void onFailed(String taskId, Throwable exception) {
             // ...
         }
    })

    // add listener which is invoked on task started event
    .addListener(new TaskStartedListener() {
         public void onStarted(String taskId) {
             // ...
         }
    })

    // add listener which is invoked on task finished event
    .addListener(new TaskFinishedListener() {
         public void onFinished(String taskId) {
             // ...
         }
    });
```

Code example of worker registration with options defined above:
```java
RScheduledExecutorService executor = redisson.getExecutorService("myExecutor");
executor.registerWorkers(options);
```


### Scheduling a task
Redisson node doesn't require presence of task classes in classpath. Task classes are loaded automatically by Redisson node ClassLoader. Thus each new task class doesn't require restart of Redisson node.

Example with `Callable` task:
```java
public class CallableTask implements Callable<Long> {

    @RInject
    private RedissonClient redissonClient;

    @Override
    public Long call() throws Exception {
        RMap<String, Integer> map = redissonClient.getMap("myMap");
        Long result = 0;
        for (Integer value : map.values()) {
            result += value;
        }
        return result;
    }

}
```
Follow options could be supplied during ExecutorService aquisition:

```java
ExecutorOptions options = ExecutorOptions.defaults()

// Defines task retry interval at the end of which task is executed again.
// ExecutorService worker re-schedule task execution retry every 5 seconds.
//
// Set 0 to disable.
//
// Default is 5 minutes
options.taskRetryInterval(10, TimeUnit.MINUTES);
```

```java
RScheduledExecutorService executorService = redisson.getExecutorService("myExecutor");
ScheduledFuture<Long> future = executorService.schedule(new CallableTask(), 10, TimeUnit.MINUTES);
Long result = future.get();
```

Example with Lambda task:
```java
RScheduledExecutorService executorService = redisson.getExecutorService("myExecutor", options);
ScheduledFuture<Long> future = executorService.schedule((Callable & Serializable)() -> {
      System.out.println("task has been executed!");
}, 10, TimeUnit.MINUTES);
Long result = future.get();
```


Example with `Runnable` task:
```java
public class RunnableTask implements Runnable {

    @RInject
    private RedissonClient redissonClient;

    private long param;

    public RunnableTask() {
    }

    public RunnableTask(long param) {
        this.param= param;
    }

    @Override
    public void run() {
        RAtomicLong atomic = redissonClient.getAtomicLong("myAtomic");
        atomic.addAndGet(param);
    }

}

RScheduledExecutorService executorService = redisson.getExecutorService("myExecutor");
ScheduledFuture<?> future1 = executorService.schedule(new RunnableTask(123), 10, TimeUnit.HOURS);
// ...
ScheduledFuture<?> future2 = executorService.scheduleAtFixedRate(new RunnableTask(123), 10, 25, TimeUnit.HOURS);
// ...
ScheduledFuture<?> future3 = executorService.scheduleWithFixedDelay(new RunnableTask(123), 5, 10, TimeUnit.HOURS);
```

### Scheduling a task with Spring beans
Redisson allows to inject not only RedissonClient using `@RInject` annotation but also supports Spring's `@Autowire`, `@Value` and JSR-330's `@Inject` annotations. Injected Spring Bean service interface should implement Serializable.

Redisson uses Spring's BeanFactory object for injection. It should be defined in Redisson Node [configuration](https://github.com/redisson/redisson/wiki/12.-Standalone-node#beanfactory).

Example with `Callable` task:
```java
public class RunnableTask implements Runnable {

    @Autowired
    private MySpringService service;

    @Value("myProperty")
    private String prop;

    @RInject
    private RedissonClient redissonClient;

    private long param;

    public RunnableTask() {
    }

    public RunnableTask(long param) {
        this.param = param;
    }

    @Override
    public void run() {
        RMap<String, Integer> map = redissonClient.getMap(prop);

        Integer result = service.someMethod();
        map.put("test", result);
        return result;
    }

}
```

```java
RScheduledExecutorService executorService = redisson.getExecutorService("myExecutor");
ScheduledFuture<?> future1 = executorService.schedule(new RunnableTask(123), 10, TimeUnit.HOURS);
// ...
ScheduledFuture<?> future2 = executorService.scheduleAtFixedRate(new RunnableTask(123), 10, 25, TimeUnit.HOURS);
// ...
ScheduledFuture<?> future3 = executorService.scheduleWithFixedDelay(new RunnableTask(123), 5, 10, TimeUnit.HOURS);
```

### Scheduling a task with cron expression
Tasks scheduler service allows to define more complex schedule with cron expressions. Which fully compatible with [Quartz cron format](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html).

Example:
```java
RScheduledExecutorService executorService = redisson.getExecutorService("myExecutor");
executorService.schedule(new RunnableTask(), CronSchedule.of("10 0/5 * * * ?"));
// ...
executorService.schedule(new RunnableTask(), CronSchedule.dailyAtHourAndMinute(10, 5));
// ...
executorService.schedule(new RunnableTask(), CronSchedule.weeklyOnDayAndHourAndMinute(12, 4, Calendar.MONDAY, Calendar.FRIDAY));
```
### Task scheduling cancellation
Scheduled executor service provides two ways to cancel scheduled task via `RScheduledFuture.cancel()` or `RScheduledExecutorService.cancelTask()` method. To handle case then task execution is already in progress you need to check Thread status for interruption with `Thread.currentThread().isInterrupted()` invocation:
```java
public class RunnableTask implements Callable<Long> {

    @RInject
    private RedissonClient redissonClient;

    @Override
    public Long call() throws Exception {
        RMap<String, Integer> map = redissonClient.getMap("myMap");
        Long result = 0;
        // map contains many entries
        for (Integer value : map.values()) {
           if (Thread.currentThread().isInterrupted()) {
                // task has been canceled
                return null;
           }
           result += value;
        }
        return result;
    }

}

RScheduledExecutorService executorService = redisson.getExecutorService("myExecutor");

// submit tasks synchronously for asynchronous execution.
RScheduledFuture<Long> future = executorService.schedule(new RunnableTask(), CronSchedule.dailyAtHourAndMinute(10, 5));

// submit tasks asynchronously for asynchronous execution.
RScheduledFuture<Long> future = executorService.scheduleAsync(new RunnableTask(), CronSchedule.dailyAtHourAndMinute(10, 5));

// cancel future
future.cancel(true);

// cancel by taskId
executorService.cancelTask(future.getTaskId());
```

## MapReduce service

### Overview

Redisson provides MapReduce programming model to process large amount of data stored in Redis. Based on ideas from different implementations and [Google's MapReduce publication](https://research.google.com/archive/mapreduce.html). Supported by different objects: `RMap`, `RMapCache`, `RLocalCachedMap`, `RClusteredMap`, `RClusteredMapCache`, `RClusteredLocalCachedMap`, `RClusteredSet`, `RClusteredSetCache`, `RSet`, `RSetCache`, `RList`, `RSortedSet`, `RScoredSortedSet`, `RQueue`, `RBlockingQueue`, `RDeque`, `RBlockingDeque`, `RPriorityQueue` and `RPriorityDeque`.

MapReduce model based on few objects: `RMapper`/`RCollectionMapper`, `RReducer` and `RCollator`.

All tasks for _map_ and _reduce_ phases are executed across [Redisson Nodes](https://github.com/redisson/redisson/wiki/12.-Standalone-node). 

For `RLocalCachedMap`, `RClusteredMap`, `RClusteredMapCache`, `RClusteredLocalCachedMap`, `RClusteredSet`, `RClusteredSetCache` objects _Map phase_ is splitted to multiple sub-tasks and executes in parallel. For other objects _Map phase_ is executed in single sub-task. _Reduce phase_ is always splitted to multiple sub-tasks and executes in parallel. Sub-tasks amount equals to total amount of registered MapReduce workers.

**1. `RMapper` applied only for Map objects and transforms each Map's entry to intermediate key/value pair.**
```java
public interface RMapper<KIn, VIn, KOut, VOut> extends Serializable {

    void map(KIn key, VIn value, RCollector<KOut, VOut> collector);
    
}
```

**2. `RCollectionMapper ` applied only for Collection objects and transforms each Collection's entry to intermediate key/value pair.**
```java
public interface RCollectionMapper<VIn, KOut, VOut> extends Serializable {

    void map(VIn value, RCollector<KOut, VOut> collector);
    
}
```

**3. `RReducer` reduces a list of intermediate key/value pairs.**
```java
public interface RReducer<K, V> extends Serializable {

    V reduce(K reducedKey, Iterator<V> values);
    
}
```
**4. `RCollator` converts reduced result to a single object.**

```java
public interface RCollator<K, V, R> extends Serializable {

    R collate(Map<K, V> resultMap);
    
}
```

Each type of task has ability to inject RedissonClient using `@RInject` annotation like this:
```java
    public class WordMapper implements RMapper<String, String, String, Integer> {

        @RInject
        private RedissonClient redissonClient;

        @Override
        public void map(String key, String value, RCollector<String, Integer> collector) {

            // ...

            redissonClient.getAtomicLong("mapInvocations").incrementAndGet();
        }
        
    }

```

By default MapReduce time execution is not limited, but timeout could be defined if required:
```java
             = list.<String, Integer>mapReduce()
                   .mapper(new WordMapper())
                   .reducer(new WordReducer())
                   .timeout(60, TimeUnit.MINUTES);
```

### Map example
MapReduce is available for map type objects: `RMap`, `RMapCache` and `RLocalCachedMap`.

Below is word count example using MapReduce:

1. Mapper object applied to each Map entry and split value by space to separate words.  
```java
    public class WordMapper implements RMapper<String, String, String, Integer> {

        @Override
        public void map(String key, String value, RCollector<String, Integer> collector) {
            String[] words = value.split("[^a-zA-Z]");
            for (String word : words) {
                collector.emit(word, 1);
            }
        }
        
    }
```

2. Reducer object calculates the total sum for each word.  
```java    
    public class WordReducer implements RReducer<String, Integer> {

        @Override
        public Integer reduce(String reducedKey, Iterator<Integer> iter) {
            int sum = 0;
            while (iter.hasNext()) {
               Integer i = (Integer) iter.next();
               sum += i;
            }
            return sum;
        }
        
    }
```

3. Collator object counts total amount of words.
```java
    public class WordCollator implements RCollator<String, Integer, Integer> {

        @Override
        public Integer collate(Map<String, Integer> resultMap) {
            int result = 0;
            for (Integer count : resultMap.values()) {
                result += count;
            }
            return result;
        }
        
    }
```

4. Here is how to run all together:
```java
    RMap<String, String> map = redisson.getMap("wordsMap");
    map.put("line1", "Alice was beginning to get very tired"); 
    map.put("line2", "of sitting by her sister on the bank and");
    map.put("line3", "of having nothing to do once or twice she");
    map.put("line4", "had peeped into the book her sister was reading");
    map.put("line5", "but it had no pictures or conversations in it");
    map.put("line6", "and what is the use of a book");
    map.put("line7", "thought Alice without pictures or conversation");

    RMapReduce<String, String, String, Integer> mapReduce
             = map.<String, Integer>mapReduce()
                  .mapper(new WordMapper())
                  .reducer(new WordReducer());

    // count occurrences of words
    Map<String, Integer> mapToNumber = mapReduce.execute();

    // count total words amount
    Integer totalWordsAmount = mapReduce.execute(new WordCollator());
```


### Collection example
MapReduce is available for collection type objects: `RSet`, `RSetCache`, `RList`, `RSortedSet`, `RScoredSortedSet`, `RQueue`, `RBlockingQueue`, `RDeque`, `RBlockingDeque`, `RPriorityQueue` and `RPriorityDeque`.

Below is word count example using MapReduce:

```java
    public class WordMapper implements RCollectionMapper<String, String, Integer> {

        @Override
        public void map(String value, RCollector<String, Integer> collector) {
            String[] words = value.split("[^a-zA-Z]");
            for (String word : words) {
                collector.emit(word, 1);
            }
        }
        
    }
```
```java    
    public class WordReducer implements RReducer<String, Integer> {

        @Override
        public Integer reduce(String reducedKey, Iterator<Integer> iter) {
            int sum = 0;
            while (iter.hasNext()) {
               Integer i = (Integer) iter.next();
               sum += i;
            }
            return sum;
        }
        
    }
```
```java
    public class WordCollator implements RCollator<String, Integer, Integer> {

        @Override
        public Integer collate(Map<String, Integer> resultMap) {
            int result = 0;
            for (Integer count : resultMap.values()) {
                result += count;
            }
            return result;
        }
        
    }
```
```java
    RList<String> list = redisson.getList("myList");
    list.add("Alice was beginning to get very tired"); 
    list.add("of sitting by her sister on the bank and");
    list.add("of having nothing to do once or twice she");
    list.add("had peeped into the book her sister was reading");
    list.add("but it had no pictures or conversations in it");
    list.add("and what is the use of a book");
    list.add("thought Alice without pictures or conversation");

    RCollectionMapReduce<String, String, Integer> mapReduce
             = list.<String, Integer>mapReduce()
                   .mapper(new WordMapper())
                   .reducer(new WordReducer());

    // count occurrences of words
    Map<String, Integer> mapToNumber = mapReduce.execute();

    // count total words amount
    Integer totalWordsAmount = mapReduce.execute(new WordCollator());
```

## RediSearch service

Redisson provides RediSearch integration. Supports field indexing of [RMap](Distributed-collections.md/#map), [RJSONStore](Distributed-collections.md/#json-store) and [RJSONBucket](Distributed-objects.md/#json-object-holder) objects, query execution and aggregation.

It has [Async](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RSearchAsync.html), [Reactive](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RSearchReactive.html) and [RxJava3](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RSearchRx.html) interfaces.

### Query execution

Code example for RMap object:
```java
RMap<String, SimpleObject> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
m.put("v1", new SimpleObject("name1"));
m.put("v2", new SimpleObject("name2"));
RMap<String, SimpleObject> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
m2.put("v1", new SimpleObject("name3"));
m2.put("v2", new SimpleObject("name4"));

RSearch s = redisson.getSearch();

// creates an index for documents with prefix "doc"
s.createIndex("idx", IndexOptions.defaults()
                                 .on(IndexType.HASH)
                                 .prefix(Arrays.asList("doc:")),
                                         FieldIndex.text("v1"),
                                         FieldIndex.text("v2"));

SearchResult r = s.search("idx", "*", QueryOptions.defaults()
                                                  .returnAttributes(new ReturnAttribute("v1"), new ReturnAttribute("v2")));

SearchResult r = s.search("idx", "*", QueryOptions.defaults()
                                                  .filters(QueryFilter.geo("field")
                                                                      .from(1, 1)
                                                                      .radius(10, GeoUnit.FEET)));

```

Code example for JSON object:
```java
    public class TestClass {

        private List<Integer> arr;
        private String value;

        public TestClass() {
        }

        public TestClass(List<Integer> arr, String value) {
            this.arr = arr;
            this.value = value;
        }

        public List<Integer> getArr() {
            return arr;
        }

        public TestClass setArr(List<Integer> arr) {
            this.arr = arr;
            return this;
        }

        public String getValue() {
            return value;
        }

        public TestClass setValue(String value) {
            this.value = value;
            return this;
        }
    }


RJsonBucket<TestClass> b = redisson.getJsonBucket("doc:1", new JacksonCodec<>(TestClass.class));
b.set(new TestClass(Arrays.asList(1, 2, 3), "hello"));

RSearch s = redisson.getSearch(StringCodec.INSTANCE);
// creates an index for documents with prefix "doc"
s.createIndex("idx", IndexOptions.defaults()
                                  .on(IndexType.JSON)
                                  .prefix(Arrays.asList("doc:")),
                                      FieldIndex.numeric("$..arr").as("arr"),
                                      FieldIndex.text("$..value").as("val"));

SearchResult r = s.search("idx", "*", QueryOptions.defaults()
                                                  .returnAttributes(new ReturnAttribute("arr"), new ReturnAttribute("val")));
// total amount of found documents
long total = r.getTotal();
// found documents
List<Document> docs = r.getDocuments();

for (Document doc: docs) {
   String id = doc.getId();
   Map<String, Object> attrs = doc.getAttributes();
}
```

### Aggregation

Code example for Map object:

```java
RMap<String, SimpleObject> m = redisson.getMap("doc:1", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
m.put("v1", new SimpleObject("name1"));
m.put("v2", new SimpleObject("name2"));
RMap<String, SimpleObject> m2 = redisson.getMap("doc:2", new CompositeCodec(StringCodec.INSTANCE, redisson.getConfig().getCodec()));
m2.put("v1", new SimpleObject("name3"));
m2.put("v2", new SimpleObject("name4"));

RSearch s = redisson.getSearch();
// creates an index for documents with prefix "doc"
s.createIndex("idx", IndexOptions.defaults()
                                    .on(IndexType.HASH)
                                    .prefix(Arrays.asList("doc:")),
                                      FieldIndex.text("v1"),
                                      FieldIndex.text("v2"));

AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                .load("v1", "v2"));
// total amount of attributes
long total = r.getTotal();
// list of attributes mapped by attribute name
List<Map<String, Object>> attrs = r.getAttributes();
```

Code example for JSON object:

```java
    public class TestClass {

        private List<Integer> arr;
        private String value;

        public TestClass() {
        }

        public TestClass(List<Integer> arr, String value) {
            this.arr = arr;
            this.value = value;
        }

        public List<Integer> getArr() {
            return arr;
        }

        public TestClass setArr(List<Integer> arr) {
            this.arr = arr;
            return this;
        }

        public String getValue() {
            return value;
        }

        public TestClass setValue(String value) {
            this.value = value;
            return this;
        }
    }

RJsonBucket<TestClass> b = redisson.getJsonBucket("doc:1", new JacksonCodec<>(TestClass.class));
// stores object in JSON format
b.set(new TestClass(Arrays.asList(1, 2, 3), "hello"));

RSearch s = redisson.getSearch(StringCodec.INSTANCE);
// creates an index for documents with prefix "doc"
s.createIndex("idx", IndexOptions.defaults()
                                 .on(IndexType.JSON)
                                 .prefix(Arrays.asList("doc:")),
                             FieldIndex.numeric("$..arr").as("arr"),
                             FieldIndex.text("$..value").as("val"));

AggregationResult r = s.aggregate("idx", "*", AggregationOptions.defaults()
                                                                .load("arr", "val"));
// total amount of attributes
long total = r.getTotal();
// list of attributes mapped by attribute name
List<Map<String, Object>> attrs = r.getAttributes();

```

### Spellcheck

Code example.

```java
RSearch s = redisson.getSearch();

s.createIndex("idx", IndexOptions.defaults()
                                 .on(IndexType.HASH)
                                 .prefix(Arrays.asList("doc:")),
                             FieldIndex.text("t1"),
                             FieldIndex.text("t2"));

s.addDict("name", "hockey", "stik");

Map<String, Map<String, Double>> res = s.spellcheck("idx", "Hocke sti", SpellcheckOptions.defaults()
                                                                                         .includedTerms("name"));

// returns misspelled terms and their score - "hockey", 0
Map<String, Double> m = res.get("hocke");

// returns misspelled terms and their score - "stik", 0
Map<String, Double> m = res.get("sti");
```