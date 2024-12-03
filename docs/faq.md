**Q: What is the cause of RedisTimeoutException?**

**A** : There are multiple reasons: 

1. All netty threads are busy, leading to delays in both response decoding and sending commands.
2. All connections are busy.
3. Redis or Valkey server is busy and takes too long to respond the request.
4. Java application is busy.
5. Blocking invocation in async/reactive/rx listeners or subscribeOnElements methods.
6. Server CPU throttling. For GCP hosting try to add `--no-cpu-throttling` to CloudRun container which connects via Redisson to the Redis or Valkey instance.
7. Unstable network with TCP packet drops. 
8. Redis or Valkey vendor limits concurrent connections amount.

First try to set follow values for `nettyThreads` setting: 32, 64, 128, 256 this allows Redisson to get a free netty thread to decode a response or send a command. Next, try to increase `retryInterval` and/or `timeout` to a reasonable value so that a command can still gracefully fail without having the end user wait forever. At the last step, try to increase `connection pool` setting so that Redisson can stand a better chance in getting a free connection. 

Complex commands such as `keys`, `hmget` and big loops in Lua scripts are more likely to see it than other commands. It is important to understand an operation can still timeout despite the absence of it from the Redis or Valkey slowlog. Slowlogs only record the time a command is been processed by the Redis or Valkey event loop and not anything before or after. Network issue may also cause this exception when a response is still in-flight. 

**Q: When do I need to shut down a Redisson instance, at the end of each request or the end of the life of a thread?**

**A** : Redisson instance requires manual shutdown only if you want to stop using all of its features. It is a common pattern that Redisson starts and stops along with the application. Since it is completely thread safe, you may treat a Redisson instance as a singleton. The shutdown sequence will disconnect all the active connections held in each connection pool, and it will clean up certain types of Redisson objects require a manual destroy action upon disposal, it will then stop the event loops. Please be advised, the entire shutdown process is not instant.

**Q: In MapCache/SetCache/SpringCache/JCache, I have set an expiry time to an entry, why is it still in Redis or Valkey when it should be disappeared?**

**A** : The first and foremost thing you need to understand is entry expiry feature is not supported by Redis or Valkey. This is one of Redisson’s own creation. Which means it requires Redisson to work. You can’t expect a correct behaviour using other clients such as redis-cli or even jedis.

Redisson employs both active approach and passive approach, just like Redis or Valkey server, to ensure an element is evicted when its time is due. There is are scheduled eviction tasks that runs periodically to remove the expired elements from the collection, Redisson also checks the expiry information when an element is accessed: if it has expired, it will be removed and a null value will be returned.

So if you saw the stale value in redis-cli, please do not panic, it will be removed when the scheduled task catches up or when you next request it through Redisson.

**Q: How can I perform Pipelining/Transaction through Redisson?**

**A** : The fact is pipelining and transaction are dealt with virtually in the same way by the `RBatch` object in Redisson. This is the design decision we took based on the analysis of the characteristics of both techniques. From the client side point of view, through both techniques, you are expected to issue a series of commands consecutively, and more importantly you will only expect to see the results after the last command is executed.

There are only subtle differences between the two techniques lies within the `RBatch` API. There is an `atomic()` method you should use before issuing batch commands if you want to execute a series of commands in a single transaction. There is no other usage difference.

And yes, if this answers your other question, transactional commands are always dispatched in a single pipeline.

**Q: Is Redisson thread safe? Can I share an instance of it between different threads?**

**A** : The Redisson instance itself and all the objects it provides are thread safe. APIs exposed by those objects are just operation handles. None of the these objects keep any states that would break the thread safety local to the instance. Most of those objects that do not “contain” any data local to the JVM with exception to LocalCached instances for obvious reasons. And here is a piece of pro advice: You can even share a RObject to multiple machines by publish it over a RTopic.

**Q: Can I use different encoder/decoders for different tasks?**

**A** : A different codec to the default one can be supplied when creating a `RObject` instance:
```java
RMap<String, String> map = redisson.getMap("myMap", new MyCodec());
```
