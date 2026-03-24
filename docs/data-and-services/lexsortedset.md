## LexSortedSet

Valkey or Redis based distributed [LexSortedSet](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RLexSortedSet.html) object for Java stores `String` elements only and implements `java.util.Set<String>` interface. Unlike regular sets, LexSortedSet maintains elements in lexicographical (alphabetical dictionary) order automatically. This makes it ideal for use cases requiring sorted string data such as autocomplete systems, alphabetical indexes, and dictionary-style lookups.

This object is thread-safe. Set size is limited to `4 294 967 295` elements. Valkey or Redis uses serialized state to check value uniqueness instead of value's `hashCode()`/`equals()` methods.

### Lexicographical Ordering

LexSortedSet stores all elements in lexicographical order based on byte-by-byte comparison of their string values. Elements are automatically sorted when added, with no additional configuration required. This ordering follows standard dictionary sorting rules where, for example, "apple" comes before "banana", and "a1" comes before "a2".

=== "Sync"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    // Add elements - automatically sorted lexicographically
    set.add("banana");
    set.add("apple");
    set.add("cherry");
    set.add("apricot");
    
    // Elements are stored in order: apple, apricot, banana, cherry
    
    // Get the first (lexicographically smallest) element
    String first = set.first();  // returns "apple"
    
    // Get the last (lexicographically largest) element
    String last = set.last();    // returns "cherry"
    
    // Poll (retrieve and remove) first element
    String polledFirst = set.pollFirst();  // returns "apple"
    
    // Poll (retrieve and remove) last element
    String polledLast = set.pollLast();    // returns "cherry"
    
    // Get count of all elements
    int count = set.count("a", true, "z", true);
    
    // Check element existence
    boolean exists = set.contains("banana");
    
    // Remove element
    boolean removed = set.remove("banana");
    ```
=== "Async"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    // Add elements asynchronously
    RFuture<Boolean> f1 = set.addAsync("banana");
    RFuture<Boolean> f2 = set.addAsync("apple");
    RFuture<Boolean> f3 = set.addAsync("cherry");
    RFuture<Boolean> f4 = set.addAsync("apricot");
    
    // Get the first (lexicographically smallest) element
    RFuture<String> firstFuture = set.firstAsync();
    
    // Get the last (lexicographically largest) element
    RFuture<String> lastFuture = set.lastAsync();
    
    // Poll (retrieve and remove) first element
    RFuture<String> polledFirstFuture = set.pollFirstAsync();
    
    // Poll (retrieve and remove) last element
    RFuture<String> polledLastFuture = set.pollLastAsync();
    
    // Get count of all elements in range
    RFuture<Integer> countFuture = set.countAsync("a", true, "z", true);
    
    // Check element existence
    RFuture<Boolean> existsFuture = set.containsAsync("banana");
    
    // Remove element
    RFuture<Boolean> removedFuture = set.removeAsync("banana");
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RLexSortedSetReactive set = redisson.getLexSortedSet("myLexSet");
    
    // Add elements reactively
    Mono<Boolean> m1 = set.add("banana");
    Mono<Boolean> m2 = set.add("apple");
    Mono<Boolean> m3 = set.add("cherry");
    Mono<Boolean> m4 = set.add("apricot");
    
    // Get the first (lexicographically smallest) element
    Mono<String> firstMono = set.first();
    
    // Get the last (lexicographically largest) element
    Mono<String> lastMono = set.last();
    
    // Poll (retrieve and remove) first element
    Mono<String> polledFirstMono = set.pollFirst();
    
    // Poll (retrieve and remove) last element
    Mono<String> polledLastMono = set.pollLast();
    
    // Get count of all elements in range
    Mono<Integer> countMono = set.count("a", true, "z", true);
    
    // Check element existence
    Mono<Boolean> existsMono = set.contains("banana");
    
    // Remove element
    Mono<Boolean> removedMono = set.remove("banana");
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RLexSortedSetRx set = redisson.getLexSortedSet("myLexSet");
    
    // Add elements using RxJava
    Single<Boolean> s1 = set.add("banana");
    Single<Boolean> s2 = set.add("apple");
    Single<Boolean> s3 = set.add("cherry");
    Single<Boolean> s4 = set.add("apricot");
    
    // Get the first (lexicographically smallest) element
    Maybe<String> firstMaybe = set.first();
    
    // Get the last (lexicographically largest) element
    Maybe<String> lastMaybe = set.last();
    
    // Poll (retrieve and remove) first element
    Maybe<String> polledFirstMaybe = set.pollFirst();
    
    // Poll (retrieve and remove) last element
    Maybe<String> polledLastMaybe = set.pollLast();
    
    // Get count of all elements in range
    Single<Integer> countSingle = set.count("a", true, "z", true);
    
    // Check element existence
    Single<Boolean> existsSingle = set.contains("banana");
    
    // Remove element
    Single<Boolean> removedSingle = set.remove("banana");
    ```

### Range Operations

LexSortedSet provides powerful range query capabilities for retrieving elements within specified lexicographical boundaries. Range operations support both inclusive and exclusive bounds, allowing precise control over which elements are included in the results. These operations are highly efficient as they leverage Valkey or Redis's native sorted set commands.

**Retrieving Elements by Range**

=== "Sync"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    set.add("apple");
    set.add("apricot");
    set.add("banana");
    set.add("blueberry");
    set.add("cherry");
    set.add("cranberry");
    
    // Get elements from start up to specified value (inclusive)
    // Returns: apple, apricot, banana
    Collection<String> head = set.rangeHead("banana", true);
    
    // Get elements from start up to specified value (exclusive)
    // Returns: apple, apricot
    Collection<String> headExclusive = set.rangeHead("banana", false);
    
    // Get elements from specified value to end (inclusive)
    // Returns: cherry, cranberry
    Collection<String> tail = set.rangeTail("cherry", true);
    
    // Get elements from specified value to end (exclusive)
    // Returns: cranberry
    Collection<String> tailExclusive = set.rangeTail("cherry", false);
    
    // Get elements between two values (both inclusive)
    // Returns: apricot, banana, blueberry
    Collection<String> range = set.range("apricot", true, "blueberry", true);
    
    // Get elements between two values (start inclusive, end exclusive)
    // Returns: apricot, banana
    Collection<String> rangeMixed = set.range("apricot", true, "blueberry", false);
    
    // Get elements in reverse lexicographical order
    Collection<String> rangeReversed = set.rangeReversed("apricot", true, "cherry", true);
    Collection<String> headReversed = set.rangeHeadReversed("cherry", true);
    Collection<String> tailReversed = set.rangeTailReversed("banana", true);
    ```
=== "Async"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    // Add elements
    set.addAsync("apple");
    set.addAsync("apricot");
    set.addAsync("banana");
    set.addAsync("blueberry");
    set.addAsync("cherry");
    set.addAsync("cranberry");
    
    // Get elements from start up to specified value (inclusive)
    RFuture<Collection<String>> headFuture = set.rangeHeadAsync("banana", true);
    
    // Get elements from start up to specified value (exclusive)
    RFuture<Collection<String>> headExclusiveFuture = set.rangeHeadAsync("banana", false);
    
    // Get elements from specified value to end (inclusive)
    RFuture<Collection<String>> tailFuture = set.rangeTailAsync("cherry", true);
    
    // Get elements from specified value to end (exclusive)
    RFuture<Collection<String>> tailExclusiveFuture = set.rangeTailAsync("cherry", false);
    
    // Get elements between two values (both inclusive)
    RFuture<Collection<String>> rangeFuture = set.rangeAsync("apricot", true, "blueberry", true);
    
    // Get elements between two values (start inclusive, end exclusive)
    RFuture<Collection<String>> rangeMixedFuture = set.rangeAsync("apricot", true, "blueberry", false);
    
    // Get elements in reverse lexicographical order
    RFuture<Collection<String>> rangeReversedFuture = set.rangeReversedAsync("apricot", true, "cherry", true);
    RFuture<Collection<String>> headReversedFuture = set.rangeHeadReversedAsync("cherry", true);
    RFuture<Collection<String>> tailReversedFuture = set.rangeTailReversedAsync("banana", true);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RLexSortedSetReactive set = redisson.getLexSortedSet("myLexSet");
    
    // Add elements
    set.add("apple").subscribe();
    set.add("apricot").subscribe();
    set.add("banana").subscribe();
    set.add("blueberry").subscribe();
    set.add("cherry").subscribe();
    set.add("cranberry").subscribe();
    
    // Get elements from start up to specified value (inclusive)
    Mono<Collection<String>> headMono = set.rangeHead("banana", true);
    
    // Get elements from start up to specified value (exclusive)
    Mono<Collection<String>> headExclusiveMono = set.rangeHead("banana", false);
    
    // Get elements from specified value to end (inclusive)
    Mono<Collection<String>> tailMono = set.rangeTail("cherry", true);
    
    // Get elements from specified value to end (exclusive)
    Mono<Collection<String>> tailExclusiveMono = set.rangeTail("cherry", false);
    
    // Get elements between two values (both inclusive)
    Mono<Collection<String>> rangeMono = set.range("apricot", true, "blueberry", true);
    
    // Get elements between two values (start inclusive, end exclusive)
    Mono<Collection<String>> rangeMixedMono = set.range("apricot", true, "blueberry", false);
    
    // Get elements in reverse lexicographical order
    Mono<Collection<String>> rangeReversedMono = set.rangeReversed("apricot", true, "cherry", true);
    Mono<Collection<String>> headReversedMono = set.rangeHeadReversed("cherry", true);
    Mono<Collection<String>> tailReversedMono = set.rangeTailReversed("banana", true);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RLexSortedSetRx set = redisson.getLexSortedSet("myLexSet");
    
    // Add elements
    set.add("apple").subscribe();
    set.add("apricot").subscribe();
    set.add("banana").subscribe();
    set.add("blueberry").subscribe();
    set.add("cherry").subscribe();
    set.add("cranberry").subscribe();
    
    // Get elements from start up to specified value (inclusive)
    Single<Collection<String>> headSingle = set.rangeHead("banana", true);
    
    // Get elements from start up to specified value (exclusive)
    Single<Collection<String>> headExclusiveSingle = set.rangeHead("banana", false);
    
    // Get elements from specified value to end (inclusive)
    Single<Collection<String>> tailSingle = set.rangeTail("cherry", true);
    
    // Get elements from specified value to end (exclusive)
    Single<Collection<String>> tailExclusiveSingle = set.rangeTail("cherry", false);
    
    // Get elements between two values (both inclusive)
    Single<Collection<String>> rangeSingle = set.range("apricot", true, "blueberry", true);
    
    // Get elements between two values (start inclusive, end exclusive)
    Single<Collection<String>> rangeMixedSingle = set.range("apricot", true, "blueberry", false);
    
    // Get elements in reverse lexicographical order
    Single<Collection<String>> rangeReversedSingle = set.rangeReversed("apricot", true, "cherry", true);
    Single<Collection<String>> headReversedSingle = set.rangeHeadReversed("cherry", true);
    Single<Collection<String>> tailReversedSingle = set.rangeTailReversed("banana", true);
    ```

**Counting Elements in Range**

=== "Sync"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    set.add("apple");
    set.add("apricot");
    set.add("banana");
    set.add("blueberry");
    set.add("cherry");
    
    // Count elements from start up to specified value (inclusive)
    int headCount = set.countHead("banana", true);  // returns 3
    
    // Count elements from start up to specified value (exclusive)
    int headCountExclusive = set.countHead("banana", false);  // returns 2
    
    // Count elements from specified value to end (inclusive)
    int tailCount = set.countTail("banana", true);  // returns 3
    
    // Count elements from specified value to end (exclusive)
    int tailCountExclusive = set.countTail("banana", false);  // returns 2
    
    // Count elements between two values (both inclusive)
    int rangeCount = set.count("apricot", true, "cherry", true);  // returns 4
    
    // Count elements between two values (start inclusive, end exclusive)
    int rangeCountMixed = set.count("apricot", true, "cherry", false);  // returns 3
    ```
=== "Async"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    // Count elements from start up to specified value (inclusive)
    RFuture<Integer> headCountFuture = set.countHeadAsync("banana", true);
    
    // Count elements from start up to specified value (exclusive)
    RFuture<Integer> headCountExclusiveFuture = set.countHeadAsync("banana", false);
    
    // Count elements from specified value to end (inclusive)
    RFuture<Integer> tailCountFuture = set.countTailAsync("banana", true);
    
    // Count elements from specified value to end (exclusive)
    RFuture<Integer> tailCountExclusiveFuture = set.countTailAsync("banana", false);
    
    // Count elements between two values (both inclusive)
    RFuture<Integer> rangeCountFuture = set.countAsync("apricot", true, "cherry", true);
    
    // Count elements between two values (start inclusive, end exclusive)
    RFuture<Integer> rangeCountMixedFuture = set.countAsync("apricot", true, "cherry", false);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RLexSortedSetReactive set = redisson.getLexSortedSet("myLexSet");
    
    // Count elements from start up to specified value (inclusive)
    Mono<Integer> headCountMono = set.countHead("banana", true);
    
    // Count elements from start up to specified value (exclusive)
    Mono<Integer> headCountExclusiveMono = set.countHead("banana", false);
    
    // Count elements from specified value to end (inclusive)
    Mono<Integer> tailCountMono = set.countTail("banana", true);
    
    // Count elements from specified value to end (exclusive)
    Mono<Integer> tailCountExclusiveMono = set.countTail("banana", false);
    
    // Count elements between two values (both inclusive)
    Mono<Integer> rangeCountMono = set.count("apricot", true, "cherry", true);
    
    // Count elements between two values (start inclusive, end exclusive)
    Mono<Integer> rangeCountMixedMono = set.count("apricot", true, "cherry", false);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RLexSortedSetRx set = redisson.getLexSortedSet("myLexSet");
    
    // Count elements from start up to specified value (inclusive)
    Single<Integer> headCountSingle = set.countHead("banana", true);
    
    // Count elements from start up to specified value (exclusive)
    Single<Integer> headCountExclusiveSingle = set.countHead("banana", false);
    
    // Count elements from specified value to end (inclusive)
    Single<Integer> tailCountSingle = set.countTail("banana", true);
    
    // Count elements from specified value to end (exclusive)
    Single<Integer> tailCountExclusiveSingle = set.countTail("banana", false);
    
    // Count elements between two values (both inclusive)
    Single<Integer> rangeCountSingle = set.count("apricot", true, "cherry", true);
    
    // Count elements between two values (start inclusive, end exclusive)
    Single<Integer> rangeCountMixedSingle = set.count("apricot", true, "cherry", false);
    ```

**Removing Elements by Range**

=== "Sync"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    set.add("apple");
    set.add("apricot");
    set.add("banana");
    set.add("blueberry");
    set.add("cherry");
    set.add("cranberry");
    
    // Remove elements from start up to specified value (inclusive)
    int removedHead = set.removeRangeHead("apricot", true);  // removes apple, apricot
    
    // Remove elements from start up to specified value (exclusive)
    int removedHeadExclusive = set.removeRangeHead("banana", false);  // removes elements before banana
    
    // Remove elements from specified value to end (inclusive)
    int removedTail = set.removeRangeTail("cherry", true);  // removes cherry, cranberry
    
    // Remove elements from specified value to end (exclusive)
    int removedTailExclusive = set.removeRangeTail("cherry", false);  // removes elements after cherry
    
    // Remove elements between two values (both inclusive)
    int removedRange = set.removeRange("banana", true, "cherry", true);
    
    // Remove elements between two values (start inclusive, end exclusive)
    int removedRangeMixed = set.removeRange("banana", true, "cherry", false);
    ```
=== "Async"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    // Remove elements from start up to specified value (inclusive)
    RFuture<Integer> removedHeadFuture = set.removeRangeHeadAsync("apricot", true);
    
    // Remove elements from start up to specified value (exclusive)
    RFuture<Integer> removedHeadExclusiveFuture = set.removeRangeHeadAsync("banana", false);
    
    // Remove elements from specified value to end (inclusive)
    RFuture<Integer> removedTailFuture = set.removeRangeTailAsync("cherry", true);
    
    // Remove elements from specified value to end (exclusive)
    RFuture<Integer> removedTailExclusiveFuture = set.removeRangeTailAsync("cherry", false);
    
    // Remove elements between two values (both inclusive)
    RFuture<Integer> removedRangeFuture = set.removeRangeAsync("banana", true, "cherry", true);
    
    // Remove elements between two values (start inclusive, end exclusive)
    RFuture<Integer> removedRangeMixedFuture = set.removeRangeAsync("banana", true, "cherry", false);
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RLexSortedSetReactive set = redisson.getLexSortedSet("myLexSet");
    
    // Remove elements from start up to specified value (inclusive)
    Mono<Integer> removedHeadMono = set.removeRangeHead("apricot", true);
    
    // Remove elements from start up to specified value (exclusive)
    Mono<Integer> removedHeadExclusiveMono = set.removeRangeHead("banana", false);
    
    // Remove elements from specified value to end (inclusive)
    Mono<Integer> removedTailMono = set.removeRangeTail("cherry", true);
    
    // Remove elements from specified value to end (exclusive)
    Mono<Integer> removedTailExclusiveMono = set.removeRangeTail("cherry", false);
    
    // Remove elements between two values (both inclusive)
    Mono<Integer> removedRangeMono = set.removeRange("banana", true, "cherry", true);
    
    // Remove elements between two values (start inclusive, end exclusive)
    Mono<Integer> removedRangeMixedMono = set.removeRange("banana", true, "cherry", false);
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RLexSortedSetRx set = redisson.getLexSortedSet("myLexSet");
    
    // Remove elements from start up to specified value (inclusive)
    Single<Integer> removedHeadSingle = set.removeRangeHead("apricot", true);
    
    // Remove elements from start up to specified value (exclusive)
    Single<Integer> removedHeadExclusiveSingle = set.removeRangeHead("banana", false);
    
    // Remove elements from specified value to end (inclusive)
    Single<Integer> removedTailSingle = set.removeRangeTail("cherry", true);
    
    // Remove elements from specified value to end (exclusive)
    Single<Integer> removedTailExclusiveSingle = set.removeRangeTail("cherry", false);
    
    // Remove elements between two values (both inclusive)
    Single<Integer> removedRangeSingle = set.removeRange("banana", true, "cherry", true);
    
    // Remove elements between two values (start inclusive, end exclusive)
    Single<Integer> removedRangeMixedSingle = set.removeRange("banana", true, "cherry", false);
    ```

### Listeners

Redisson allows binding listeners per `RLexSortedSet` object to receive notifications on set modifications. This requires the `notify-keyspace-events` setting to be enabled on Valkey or Redis side.

| Listener class name | Event description | Valkey or Redis `notify-keyspace-events` value |
| --- | --- | --- |
| org.redisson.api.listener.TrackingListener | Element created/removed/updated after read operation | - |
| org.redisson.api.listener.ScoredSortedSetAddListener | Element added | Ez |
| org.redisson.api.listener.ScoredSortedSetRemoveListener | Element removed | Ez |
| org.redisson.api.ExpiredObjectListener | `RLexSortedSet` object expired | Ex |
| org.redisson.api.DeletedObjectListener | `RLexSortedSet` object deleted | Eg |

=== "Sync"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    // Listener for element additions
    int addListenerId = set.addListener(new ScoredSortedSetAddListener() {
        @Override
        public void onAdd(String name) {
            System.out.println("Element added to set: " + name);
        }
    });
    
    // Listener for element removals
    int removeListenerId = set.addListener(new ScoredSortedSetRemoveListener() {
        @Override
        public void onRemove(String name) {
            System.out.println("Element removed from set: " + name);
        }
    });
    
    // Listener for set expiration
    int expireListenerId = set.addListener(new ExpiredObjectListener() {
        @Override
        public void onExpired(String name) {
            System.out.println("Set expired: " + name);
        }
    });
    
    // Listener for set deletion
    int deleteListenerId = set.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            System.out.println("Set deleted: " + name);
        }
    });
    
    // Listener for tracking changes after read operations
    int trackingListenerId = set.addListener(new TrackingListener() {
        @Override
        public void onChange(String name) {
            System.out.println("Set changed after read: " + name);
        }
    });
    
    // Remove listeners when no longer needed
    set.removeListener(addListenerId);
    set.removeListener(removeListenerId);
    set.removeListener(expireListenerId);
    set.removeListener(deleteListenerId);
    set.removeListener(trackingListenerId);
    ```
=== "Async"
    ```java
    RLexSortedSet set = redisson.getLexSortedSet("myLexSet");
    
    // Add listener for element additions
    RFuture<Integer> addListenerFuture = set.addListenerAsync(new ScoredSortedSetAddListener() {
        @Override
        public void onAdd(String name) {
            System.out.println("Element added to set: " + name);
        }
    });
    
    // Add listener for element removals
    RFuture<Integer> removeListenerFuture = set.addListenerAsync(new ScoredSortedSetRemoveListener() {
        @Override
        public void onRemove(String name) {
            System.out.println("Element removed from set: " + name);
        }
    });
    
    // Add listener for set expiration
    RFuture<Integer> expireListenerFuture = set.addListenerAsync(new ExpiredObjectListener() {
        @Override
        public void onExpired(String name) {
            System.out.println("Set expired: " + name);
        }
    });
    
    // Add listener for set deletion
    RFuture<Integer> deleteListenerFuture = set.addListenerAsync(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            System.out.println("Set deleted: " + name);
        }
    });
    
    // Remove listeners
    addListenerFuture.thenAccept(listenerId -> {
        set.removeListenerAsync(listenerId);
    });
    ```
=== "Reactive"
    ```java
    RedissonReactiveClient redisson = redissonClient.reactive();
    RLexSortedSetReactive set = redisson.getLexSortedSet("myLexSet");
    
    // Add listener for element additions
    Mono<Integer> addListenerMono = set.addListener(new ScoredSortedSetAddListener() {
        @Override
        public void onAdd(String name) {
            System.out.println("Element added to set: " + name);
        }
    });
    
    // Add listener for element removals
    Mono<Integer> removeListenerMono = set.addListener(new ScoredSortedSetRemoveListener() {
        @Override
        public void onRemove(String name) {
            System.out.println("Element removed from set: " + name);
        }
    });
    
    // Add listener for set expiration
    Mono<Integer> expireListenerMono = set.addListener(new ExpiredObjectListener() {
        @Override
        public void onExpired(String name) {
            System.out.println("Set expired: " + name);
        }
    });
    
    // Add listener for set deletion
    Mono<Integer> deleteListenerMono = set.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            System.out.println("Set deleted: " + name);
        }
    });
    
    // Remove listener
    addListenerMono
        .flatMap(listenerId -> set.removeListener(listenerId))
        .subscribe();
    ```
=== "RxJava3"
    ```java
    RedissonRxClient redisson = redissonClient.rxJava();
    RLexSortedSetRx set = redisson.getLexSortedSet("myLexSet");
    
    // Add listener for element additions
    Single<Integer> addListenerSingle = set.addListener(new ScoredSortedSetAddListener() {
        @Override
        public void onAdd(String name) {
            System.out.println("Element added to set: " + name);
        }
    });
    
    // Add listener for element removals
    Single<Integer> removeListenerSingle = set.addListener(new ScoredSortedSetRemoveListener() {
        @Override
        public void onRemove(String name) {
            System.out.println("Element removed from set: " + name);
        }
    });
    
    // Add listener for set expiration
    Single<Integer> expireListenerSingle = set.addListener(new ExpiredObjectListener() {
        @Override
        public void onExpired(String name) {
            System.out.println("Set expired: " + name);
        }
    });
    
    // Add listener for set deletion
    Single<Integer> deleteListenerSingle = set.addListener(new DeletedObjectListener() {
        @Override
        public void onDeleted(String name) {
            System.out.println("Set deleted: " + name);
        }
    });
    
    // Remove listener
    addListenerSingle
        .flatMapCompletable(listenerId -> set.removeListener(listenerId))
        .subscribe();
    ```