## Common methods

Every Redisson object supports a set of generic operations that don't depend on its data type - reading the key it is stored under, copying or renaming it, expiring it, dumping and restoring its binary state, and reacting to changes through listeners. These map onto the generic key commands of Valkey and Redis (such as `COPY`, `RENAME`, `UNLINK`, `DUMP`/`RESTORE`, and `EXPIRE`) and come from two interfaces that every object implements: [RObject](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RObject.html) for the type-independent operations and [RExpirable](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RExpirable.html) for expiration (`RExpirable` extends `RObject`).

Operations that work across the whole key space - iterating, counting, and bulk-deleting keys - are not tied to a single object and are exposed through the [RKeys](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RKeys.html) interface, obtained from `redisson.getKeys()`.

Each of these interfaces has asynchronous, reactive, and RxJava3 counterparts (`RObjectAsync`/`RExpirableAsync`/`RKeysAsync` and the matching `Reactive` and `Rx` variants) whose methods return `RFuture`, `Mono`, and `Single`/`Completable` respectively.

### Object name

The name of a Redisson object is the key under which its data is stored in Valkey or Redis. Two objects created with the same name and compatible type share the same underlying data, so the name is how an object is addressed across clients and JVMs. `getName` returns it.

```java
RMap<String, String> map = redisson.getMap("mymap");

map.getName(); // = mymap
```

### Common operations

These operations come from the `RObject` interface and behave the same way for every object type. `copy` duplicates the object under a new key and returns `false` if that key already exists, while `copyAndReplace` overwrites any existing destination. `delete` removes the object and reports whether a key was actually deleted; `unlink` does the same but reclaims the memory asynchronously on the server, so it returns sooner for large objects. `rename` changes the key, overwriting the destination if present, whereas `renamenx` renames only when the destination is free. `dump` serializes the object to a portable byte array and `restore` recreates it from that array - useful for backups or moving data between instances.

=== "Sync"
    ```java
    RObject object = ...

    boolean copied = object.copy("myNewCopy");     // false if the destination already exists
    object.copyAndReplace("myNewCopy");            // overwrites the destination

    boolean deleted = object.delete();
    object.unlink();                               // non-blocking deletion on the server

    object.rename("myNewName");                    // overwrites the destination
    boolean renamed = object.renamenx("myNewName"); // rename only if the destination is free

    byte[] state = object.dump();
    object.restore(state);
    ```
=== "Async"
    ```java
    RObjectAsync object = ...

    RFuture<Boolean> copyFuture = object.copyAsync("myNewCopy");
    RFuture<Boolean> copyReplaceFuture = object.copyAndReplaceAsync("myNewCopy");

    RFuture<Boolean> deleteFuture = object.deleteAsync();
    RFuture<Boolean> unlinkFuture = object.unlinkAsync();

    RFuture<Void> renameFuture = object.renameAsync("myNewName");
    RFuture<Boolean> renamenxFuture = object.renamenxAsync("myNewName");

    RFuture<byte[]> dumpFuture = object.dumpAsync();
    RFuture<Void> restoreFuture = object.restoreAsync(state); // state: byte[] from a previous dump
    ```
=== "Reactive"
    ```java
    RObjectReactive object = ...

    Mono<Boolean> copyMono = object.copy("myNewCopy");
    Mono<Boolean> copyReplaceMono = object.copyAndReplace("myNewCopy");

    Mono<Boolean> deleteMono = object.delete();
    Mono<Boolean> unlinkMono = object.unlink();

    Mono<Void> renameMono = object.rename("myNewName");
    Mono<Boolean> renamenxMono = object.renamenx("myNewName");

    Mono<byte[]> dumpMono = object.dump();
    Mono<Void> restoreMono = object.restore(state); // state: byte[] from a previous dump
    ```
=== "RxJava3"
    ```java
    RObjectRx object = ...

    Single<Boolean> copyRx = object.copy("myNewCopy");
    Single<Boolean> copyReplaceRx = object.copyAndReplace("myNewCopy");

    Single<Boolean> deleteRx = object.delete();
    Single<Boolean> unlinkRx = object.unlink();

    Completable renameRx = object.rename("myNewName");
    Single<Boolean> renamenxRx = object.renamenx("myNewName");

    Single<byte[]> dumpRx = object.dump();
    Completable restoreRx = object.restore(state); // state: byte[] from a previous dump
    ```

### Expiration

The `RExpirable` interface controls when an object's key is automatically deleted. `expire` sets a time to live, given either as a relative `Duration` or an absolute `Instant`. The conditional variants apply it only under a condition - `expireIfNotSet` when no expiration exists yet, and `expireIfGreater`/`expireIfLess` only when the new time extends or shortens the current one. `clearExpire` removes the expiration so the object becomes persistent again. `remainTimeToLive` reports the milliseconds left (or `-1` when there is no expiration), and `getExpireTime` reports the absolute expiration as a unix timestamp in milliseconds.

=== "Sync"
    ```java
    RExpirable object = ...

    object.expire(Duration.ofMinutes(10));            // relative time to live
    object.expire(Instant.now().plusSeconds(60));     // absolute expiration time

    object.expireIfNotSet(Duration.ofMinutes(5));     // only if no expiration is set yet
    object.expireIfGreater(Duration.ofMinutes(30));   // only if it extends the current expiration

    object.clearExpire();                             // make the object persistent again

    long ttl = object.remainTimeToLive(); // ms left, or -1 if there is no expiration
    long at = object.getExpireTime();      // expiration time as a unix timestamp in ms
    ```
=== "Async"
    ```java
    RExpirableAsync object = ...

    RFuture<Boolean> expireFuture = object.expireAsync(Duration.ofMinutes(10));
    RFuture<Boolean> expireAtFuture = object.expireAsync(Instant.now().plusSeconds(60));
    RFuture<Boolean> clearFuture = object.clearExpireAsync();
    RFuture<Long> ttlFuture = object.remainTimeToLiveAsync();
    RFuture<Long> expireTimeFuture = object.getExpireTimeAsync();
    ```
=== "Reactive"
    ```java
    RExpirableReactive object = ...

    Mono<Boolean> expireMono = object.expire(Duration.ofMinutes(10));
    Mono<Boolean> expireAtMono = object.expire(Instant.now().plusSeconds(60));
    Mono<Boolean> clearMono = object.clearExpire();
    Mono<Long> ttlMono = object.remainTimeToLive();
    Mono<Long> expireTimeMono = object.getExpireTime();
    ```
=== "RxJava3"
    ```java
    RExpirableRx object = ...

    Single<Boolean> expireRx = object.expire(Duration.ofMinutes(10));
    Single<Boolean> expireAtRx = object.expire(Instant.now().plusSeconds(60));
    Single<Boolean> clearRx = object.clearExpire();
    Single<Long> ttlRx = object.remainTimeToLive();
    Single<Long> expireTimeRx = object.getExpireTime();
    ```

### Listeners per Redisson object instance

Listeners attached to a specific object instance are notified when that object changes. The base listeners are `ExpiredObjectListener` and `DeletedObjectListener`, fired when the key expires or is deleted; many types add their own, such as [RScoredSortedSet](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSet.html#addListener(org.redisson.api.ObjectListener)), [RStream](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RStream.html#addListener(org.redisson.api.ObjectListener)), and others. These callbacks are delivered through Valkey or Redis keyspace notifications, so the server's `notify-keyspace-events` must be configured for the relevant events, otherwise the listeners never fire. `addListener` returns an id that `removeListener` uses to detach the listener.

```java
RObject object = ...

// listen for expired events
int expireListenerId = object.addListener((ExpiredObjectListener) name -> {

   //...

});

// listen for delete events
int deleteListenerId = object.addListener((DeletedObjectListener) name -> {

   //...

});

// stop listening
object.removeListener(expireListenerId);
```

### Operations over all keys

The [RKeys](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RKeys.html) interface works across the whole key space rather than a single object. `getKeys` iterates keys lazily using the server's `SCAN` cursor, so it never loads the entire key set into memory at once; `KeysScanOptions` narrows the scan by `pattern`, `limit`, key `type`, and chunk size. `randomKey` returns one key at random, `count` estimates the total number of keys, and `countExists` reports how many of the given names currently exist. Keys are removed with `delete` (or `deleteByPattern` for a glob pattern) and with the non-blocking `unlink`/`unlinkByPattern`, which free the memory on a background thread. `flushdb` clears the current database and `flushall` clears every database, each with a `*Parallel` variant that runs in the background without blocking the server.

```java
RKeys keys = redisson.getKeys();

// Keys iteration

Iterable<String> allKeys = keys.getKeys();

Iterable<String> foundKeys = keys.getKeys(KeysScanOptions.defaults().pattern("key*"));

String randomKey = keys.randomKey();

long keysAmount = keys.count();

long existingAmount = keys.countExists("obj1", "obj2", "obj3"); // amount of existing keys

// Delete methods

long delKeys = keys.delete("obj1", "obj2", "obj3");

long delByPattern = keys.deleteByPattern("test?");

long unlinkKeys = keys.unlink("obj1", "obj2", "obj3"); // works faster: runs on the database side in a separate thread

long unlinkByPattern = keys.unlinkByPattern("test?"); // works faster: runs on the database side in a separate thread

keys.flushall();          // delete all keys of all databases
keys.flushallParallel();  // delete all keys of all databases in the background, without blocking the server

keys.flushdb();           // delete all keys of the currently selected database
keys.flushdbParallel();   // delete all keys of the currently selected database in the background
```

### Global listeners

Global listeners are registered on the `RKeys` interface and fire for every object rather than one instance, covering the full lifecycle of a key:

- [NewObjectListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/listener/NewObjectListener.html) - a key is created
- [SetObjectListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/listener/SetObjectListener.html) - a value is set on a key
- [ExpiredObjectListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/ExpiredObjectListener.html) - a key expires
- [DeletedObjectListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/DeletedObjectListener.html) - a key is deleted
- [FlushListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/listener/FlushListener.html) - `flushall` or `flushdb` is executed
- [TrackingListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/listener/TrackingListener.html) - server-assisted client-side cache invalidation

Most of these events are delivered through keyspace notifications and need `notify-keyspace-events` enabled on the server; `TrackingListener` instead relies on server-assisted client-side caching. As with per-instance listeners, `addListener` returns an id passed to `removeListener`.

```java
RKeys keys = redisson.getKeys();
int id = keys.addListener((NewObjectListener) name -> {

   //...

});

int id = keys.addListener((DeletedObjectListener) name -> {

   //...

});

// Flush listener is executed on flushall/flushdb command execution.
int id = keys.addListener((FlushListener) address -> {

   //...

});

keys.removeListener(id);
```