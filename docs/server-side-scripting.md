## Lua scripting

Redisson's `RScript` object runs a Lua script on the Valkey or Redis side, where it executes atomically. A script runs in one of two modes:

* `Mode.READ_ONLY` - executed as a read operation
* `Mode.READ_WRITE` - executed as a write operation

The result is decoded according to a `ReturnType`:

* `ReturnType.BOOLEAN` - boolean type
* `ReturnType.LONG` - long type
* `ReturnType.LIST` - list of the user-defined type
* `ReturnType.STRING` - plain string type
* `ReturnType.VALUE` - the user-defined type
* `ReturnType.MAPVALUE` - map value type, decoded with the codec's map-value encoder/decoder
* `ReturnType.MAPVALUELIST` - list of the map value type

`eval` runs a script directly; the `keys` and `values` arguments are exposed inside the script as `KEYS` and `ARGV`. For repeated execution, `scriptLoad` caches the script on every master node and returns its SHA digest, which `evalSha` then runs without resending the body.

=== "Sync"
    ```java
    RBucket<String> bucket = redisson.getBucket("foo");
    bucket.set("bar");
    
    RScript script = redisson.getScript(StringCodec.INSTANCE);
    
    // run a script directly
    String value = script.eval(RScript.Mode.READ_ONLY,
            "return redis.call('get', KEYS[1])",
            RScript.ReturnType.VALUE,
            Collections.singletonList("foo"));
    
    // cache once, then run by SHA digest
    String sha = script.scriptLoad("return redis.call('get', KEYS[1])");
    String cached = script.evalSha(RScript.Mode.READ_ONLY,
            sha,
            RScript.ReturnType.VALUE,
            Collections.singletonList("foo"));
    ```
=== "Async"
    ```java
    RScript script = redisson.getScript(StringCodec.INSTANCE);
    
    RFuture<String> valueFuture = script.evalAsync(RScript.Mode.READ_ONLY,
            "return redis.call('get', KEYS[1])",
            RScript.ReturnType.VALUE,
            Collections.singletonList("foo"));
    
    RFuture<String> shaFuture = script.scriptLoadAsync("return redis.call('get', KEYS[1])");
    RFuture<String> cachedFuture = script.evalShaAsync(RScript.Mode.READ_ONLY,
            "282297a0228f48cd3fc6a55de6316f31422f5d17",
            RScript.ReturnType.VALUE,
            Collections.singletonList("foo"));
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RScriptReactive script = redisson.getScript(StringCodec.INSTANCE);
    
    Mono<String> value = script.eval(RScript.Mode.READ_ONLY,
            "return redis.call('get', KEYS[1])",
            RScript.ReturnType.VALUE,
            Collections.singletonList("foo"));
    
    Mono<String> sha = script.scriptLoad("return redis.call('get', KEYS[1])");
    Mono<String> cached = script.evalSha(RScript.Mode.READ_ONLY,
            "282297a0228f48cd3fc6a55de6316f31422f5d17",
            RScript.ReturnType.VALUE,
            Collections.singletonList("foo"));
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RScriptRx script = redisson.getScript(StringCodec.INSTANCE);
    
    Maybe<String> value = script.eval(RScript.Mode.READ_ONLY,
            "return redis.call('get', KEYS[1])",
            RScript.ReturnType.VALUE,
            Collections.singletonList("foo"));
    
    Single<String> sha = script.scriptLoad("return redis.call('get', KEYS[1])");
    Maybe<String> cached = script.evalSha(RScript.Mode.READ_ONLY,
            "282297a0228f48cd3fc6a55de6316f31422f5d17",
            RScript.ReturnType.VALUE,
            Collections.singletonList("foo"));
    ```

## Functions

Redisson's `RFunction` object runs a [Redis function](https://redis.io/docs/latest/develop/programmability/functions-intro/) - a named routine registered inside a library that, like a script, executes atomically on the server. A function runs in one of two modes:

* `FunctionMode.READ` - executed as a read operation
* `FunctionMode.WRITE` - executed as a write operation

The result is decoded according to a `FunctionResult`:

* `FunctionResult.BOOLEAN` - boolean type
* `FunctionResult.LONG` - long type
* `FunctionResult.LIST` - list of the user-defined type
* `FunctionResult.STRING` - plain string type
* `FunctionResult.VALUE` - the user-defined type
* `FunctionResult.MAPVALUE` - map value type, decoded with the codec's map-value encoder/decoder
* `FunctionResult.MAPVALUELIST` - list of the map value type

Register a library with `load`, then invoke one of its functions with `call`. The `keys` and `values` arguments arrive as the function's `keys` and `args` parameters.

=== "Sync"
    ```java
    RFunction function = redisson.getFunction();
    
    // register a library that defines a function
    function.load("mylib",
            "redis.register_function('myfun', function(keys, args) return args[1] end)");
    
    // invoke the function
    String value = function.call(FunctionMode.READ, "myfun",
            FunctionResult.STRING, Collections.emptyList(), "test");
    ```
=== "Async"
    ```java
    RFunction function = redisson.getFunction();
    
    RFuture<Void> loadFuture = function.loadAsync("mylib",
            "redis.register_function('myfun', function(keys, args) return args[1] end)");
    
    RFuture<String> valueFuture = function.callAsync(FunctionMode.READ, "myfun",
            FunctionResult.STRING, Collections.emptyList(), "test");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RFunctionReactive function = redisson.getFunction();
    
    Mono<Void> load = function.load("mylib",
            "redis.register_function('myfun', function(keys, args) return args[1] end)");
    
    Mono<String> value = function.call(FunctionMode.READ, "myfun",
            FunctionResult.STRING, Collections.emptyList(), "test");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RFunctionRx function = redisson.getFunction();
    
    Completable load = function.load("mylib",
            "redis.register_function('myfun', function(keys, args) return args[1] end)");
    
    Maybe<String> value = function.call(FunctionMode.READ, "myfun",
            FunctionResult.STRING, Collections.emptyList(), "test");
    ```

### Managing libraries

`RFunction` also manages the libraries themselves: `loadAndReplace` overwrites an existing library, `list` returns the registered libraries as `FunctionLibrary` objects, `delete` removes a single library, and `flush` removes them all. In addition, `dump` and `restore` serialize and reload the full function state, `stats` reports the running engine, and `kill` stops a function that is stuck.

=== "Sync"
    ```java
    RFunction function = redisson.getFunction();
    
    // overwrite an existing library
    function.loadAndReplace("mylib",
            "redis.register_function('myfun', function(keys, args) return args[1] end)");
    
    // inspect registered libraries
    List<FunctionLibrary> libraries = function.list();
    
    // remove a single library, or all of them
    function.delete("mylib");
    function.flush();
    ```
=== "Async"
    ```java
    RFunction function = redisson.getFunction();
    
    RFuture<Void> replaceFuture = function.loadAndReplaceAsync("mylib",
            "redis.register_function('myfun', function(keys, args) return args[1] end)");
    RFuture<List<FunctionLibrary>> librariesFuture = function.listAsync();
    RFuture<Void> deleteFuture = function.deleteAsync("mylib");
    RFuture<Void> flushFuture = function.flushAsync();
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RFunctionReactive function = redisson.getFunction();
    
    Mono<Void> replace = function.loadAndReplace("mylib",
            "redis.register_function('myfun', function(keys, args) return args[1] end)");
    Mono<List<FunctionLibrary>> libraries = function.list();
    Mono<Void> delete = function.delete("mylib");
    Mono<Void> flush = function.flush();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RFunctionRx function = redisson.getFunction();
    
    Completable replace = function.loadAndReplace("mylib",
            "redis.register_function('myfun', function(keys, args) return args[1] end)");
    Single<List<FunctionLibrary>> libraries = function.list();
    Completable delete = function.delete("mylib");
    Completable flush = function.flush();
    ```
