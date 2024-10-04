## LUA Scripting
Redisson provides `RScript` object to execute Lua script. It has atomicity property and used to process data on Redis or Valkey side. Script could be executed in two modes:

* `Mode.READ_ONLY` scripts are executed as read operation
* `Mode.READ_WRITE` scripts are executed as write operation

One of the follow types returned as a script result object:

* `ReturnType.BOOLEAN` - Boolean type.
* `ReturnType.INTEGER` - Integer type.
* `ReturnType.MULTI` - List type of user defined type.
* `ReturnType.STATUS` - Lua String type.
* `ReturnType.VALUE` - User defined type.
* `ReturnType.MAPVALUE` - Map value type.
* `ReturnType.MAPVALUELIST` - List of Map value type.

Code example:

```java
RBucket<String> bucket = redisson.getBucket("foo");
bucket.set("bar");

RScript script = redisson.getScript(StringCodec.INSTANCE);

String r = script.eval(Mode.READ_ONLY,
                       "return redis.call('get', 'foo')", 
                       RScript.ReturnType.VALUE);

// execute the same script stored in Redis or Valkey lua script cache

// load lua script into Redis or Valkey cache to all master instances
String res = script.scriptLoad("return redis.call('get', 'foo')");
// res == 282297a0228f48cd3fc6a55de6316f31422f5d17

// call lua script by sha digest
Future<Object> r1 = redisson.getScript().evalShaAsync(Mode.READ_ONLY,
   "282297a0228f48cd3fc6a55de6316f31422f5d17",
   RScript.ReturnType.VALUE, Collections.emptyList());
```

## Functions 
Redisson provides `RFunction` object to execute [Functions](https://redis.io/topics/functions-intro). It has atomicity property and used to process data on Redis or Valkey side. Function can be executed in two modes:

* `Mode.READ` executes function as read operation
* `Mode.WRITE` executes function as write operation

One of the follow types returned as a script result object:

* `ReturnType.BOOLEAN` - Boolean type
* `ReturnType.LONG` - Long type
* `ReturnType.LIST` - List type of user defined type.
* `ReturnType.STRING` - plain String type
* `ReturnType.VALUE` - user defined type 
* `ReturnType.MAPVALUE` - Map Value type. Codec.getMapValueDecoder() and Codec.getMapValueEncoder() methods are used for data deserialization or serialization
* `ReturnType.MAPVALUELIST` - List type, which consists of objects of Map Value type. Codec.getMapValueDecoder() and Codec.getMapValueEncoder() methods are used for data deserialization or serialization

Code example:

```java
RFunction f = redisson.getFunction();

// load function
f.load("lib", "redis.register_function('myfun', function(keys, args) return args[1] end)");

// execute function
String value = f.call(RFunction.Mode.READ, "myfun", RFunction.ReturnType.STRING, Collections.emptyList(), "test");
```

Code example of <b>Async interface</b> usage:

```java
RFunction f = redisson.getFunction();

// load function
RFuture<Void> loadFuture = f.loadAsync("lib", "redis.register_function('myfun', function(keys, args) return args[1] end)");

// execute function
RFuture<String> valueFuture = f.callAsync(RFunction.Mode.READ, "myfun", RFunction.ReturnType.STRING, Collections.emptyList(), "test");
```

Code example of <b>Reactive interface</b> usage:

```java
RedissonReactiveClient redisson = redissonClient.reactive();
RFunctionReactive f = redisson.getFunction();

// load function
Mono<Void> loadMono = f.load("lib", "redis.register_function('myfun', function(keys, args) return args[1] end)");

// execute function
Mono<String> valueMono = f.callAsync(RFunction.Mode.READ, "myfun", RFunction.ReturnType.STRING, Collections.emptyList(), "test");
```

Code example of <b>RxJava3 interface</b> usage:

```java
RedissonRxClient redisson = redissonClient.rxJava();
RFunctionRx f = redisson.getFunction();

// load function
Completable loadMono = f.load("lib", "redis.register_function('myfun', function(keys, args) return args[1] end)");

// execute function
Maybe<String> valueMono = f.callAsync(RFunction.Mode.READ, "myfun", RFunction.ReturnType.STRING, Collections.emptyList(), "test");
```


