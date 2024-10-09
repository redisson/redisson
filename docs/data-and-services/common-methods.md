**1. Object name**

Name of Redisson object stored as a key in Redis or Valkey.

Example:
```java
RMap map = redisson.getMap("mymap");

map.getName(); // = mymap
```

**2. Common methods**

Each Redisson object implements [RObject](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RObject.html) and [RExpirable](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RExpirable.html) interfaces.  

Below are the most commonly used methods.

```java
RObject object = ...

// Copy methods

object.copy("myNewCopy");

object.copyAndReplace("myNewCopy");

// Delete methods

object.delete();

object.unlink(); // works faster because it's executed  on the database side in a different thread

// Rename methods

object.rename("myNewName");

object.renamenx("myNewName"); // rename only if the new key doesn't exist

// Dump and restore methods

byte[] state = object.dump();
object.restore(state);
```

**3. Listeners per Redisson object instance**

Listeners can be attached per Redisson object instance. Base listeners are `ExpiredObjectListener` and `DeletedObjectListener`. Redisson objects may support specific listeners. Like [RScoredSortedSet](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RScoredSortedSet.html#addListener(org.redisson.api.ObjectListener)), [RStream](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RStream.html#addListener(org.redisson.api.ObjectListener)) and others.

```java
RObject object = ...

// listening to expired events
object.addListener((ExpiredObjectListener) name -> {

   //...

});

// listening to delete events
object.addListener((DeletedObjectListener) name -> {

   //...

});
```

**4. Operations over all Redisson object instances**

Operations over all objects are exposed by [RKeys](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RKeys.html) interface. 

Usage example:
```java
RKeys keys = redisson.getKeys();

// Keys iteration

Iterable<String> allKeys = keys.getKeys();

Iterable<String> foundedKeys = keys.getKeys(KeysScanOptions.defaults().pattern("key*"));

String randomKey = keys.randomKey();

long keysAmount = keys.count();

long keysAmount = keys.countExists("obj1", "obj2", "obj3"); // amount of existing keys

// Delete methods

long delKeys = keys.delete("obj1", "obj2", "obj3");

long delKeys = keys.deleteByPattern("test?");

long delKeys = keys.unlink("obj1", "obj2", "obj3"); // works faster because it's executed  on the database side in a different thread

long delKeys = keys.unlinkByPattern("test?"); // works faster because it's executed  on the database side in a different thread

keys.flushall(); // Delete all keys of all existing databases

keys.flushallParallel(); // Delete all keys of all existing databases in background without blocking server

keys.flushdb(); // Delete all keys of currently selected database
```
	
**5. Global listeners**

Global listeners are attached to all Redisson object instances. 

Available listeners: 

- [TrackingListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/listener/TrackingListener.html), 
- [SetObjectListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/listener/SetObjectListener.html), 
- [NewObjectListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/listener/NewObjectListener.html), 
- [ExpiredObjectListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/ExpiredObjectListener.html), 
- [DeletedObjectListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/DeletedObjectListener.html), 
- [FlushListener](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/listener/FlushListener.html)

Usage example:

```java
RKeys keys = redisson.getKeys();
int id = keys.addListener((NewObjectListener) name -> {

   //...

});

int id = keys.addListener((DeletedObjectListener) name -> {

   //...

});


// Flush listener is executed on flushall/flushdb commands execution.

int id = keys.addListener((FlushListener) address -> {

   //...

});

keys.removeListener(id);
```