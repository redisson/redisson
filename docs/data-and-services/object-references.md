It's possible to use a Redisson object inside another Redisson object in any combination. In this case a special reference object will be used and handled by Redisson.
Usage example:
```java
RMap<RSet<RList>, RList<RMap>> map = redisson.getMap("myMap");
RSet<RList> set = redisson.getSet("mySet");
RList<RMap> list = redisson.getList("myList");

map.put(set, list);
// With the help of the special reference object, we can even create a circular
// reference which is impossible to achieve if we were to serialize its content
set.add(list);
list.add(map);
```
As you may have noticed there is no need to re "save/persist" the map object after its elements have changed. Because it does not contain any value but merely a reference, this makes Redisson objects behaves much more like standard Java objects. In effect, making Redis or Valkey becomes part of JVM's memory rather than just a simple repository.

One Redis HASH, one Redis SET and one Redis LIST will be created in the example above.