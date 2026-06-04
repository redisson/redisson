## BitVector Store

*This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition.*

Java implementation of Valkey or Redis based [RBitVectorStore](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitVectorStore.html) object is a distributed store of 64-bit vectors mapped by keys, with fast server-side filtering by bitmask. Each entry packs up to 64 independent boolean attributes into a single `long`, and queries select entries by testing those bits - all evaluated on the server, so a query never transfers the whole data set to the client. Two distinct keys may map to identical vector values yet remain separate entries. The number of stored vectors is limited to `4 294 967 295`, and the object is thread-safe.

Beyond plain `put` and `get`, a vector can be modified atomically a few bits at a time - setting, clearing, flipping, or replacing masked bits, with read-before/after and compare-and-set variants - so concurrent clients can update disjoint attributes of the same entry without overwriting each other. Stored vectors are queried with four bitmask predicates and either counted or iterated.

Requires Redis 7.0.0+ or any Valkey version.

Supported match predicates (where `v` is a stored vector):

- `matchAll(mask)` — every bit set in `mask` is set in `v`
- `matchAny(mask)` — at least one bit set in `mask` is set in `v`
- `matchNone(mask)` — no bit set in `mask` is set in `v`
- `matchExact(mask, target)` — `(v & mask) == target`

It has synchronous, [asynchronous](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitVectorStoreAsync.html), [reactive](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitVectorStoreReactive.html), and [RxJava3](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitVectorStoreRx.html) interfaces. The async, reactive, and RxJava3 methods mirror the synchronous ones, returning `RFuture`, `Mono`, and `Single`/`Maybe` for single values; the streaming results of `ids` and the `match*` queries are returned as an `AsyncIterator`, `Flux`, and `Flowable` respectively.

Code examples:

=== "Sync"
	```
	RBitVectorStore<String> store = redisson.getBitVectorStore("flags");
	
	store.put("user-1", 0b011001L);
	store.put("user-2", 0b100101L);
	store.put("user-3", 0b101000L);
	
	Long vector = store.get("user-1");
	
	// keys whose vectors have every bit of the mask set
	Iterable<String> ids = store.matchAll(MatchArgs.mask(0b001000L));
	
	// count vectors with at least one of the mask's bits set
	long count = store.countMatchAny(0b011000L);
	```

=== "Async"
	```
	RBitVectorStoreAsync<String> store = redisson.getBitVectorStore("flags");
	
	RFuture<Long> putFuture = store.putAsync("user-1", 0b011001L);
	RFuture<Long> getFuture = store.getAsync("user-1");
	
	AsyncIterator<String> matchIterator = store.matchAllAsync(MatchArgs.mask(0b001000L));
	RFuture<Long> countFuture = store.countMatchAnyAsync(0b011000L);
	```

=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RBitVectorStoreReactive<String> store = redisson.getBitVectorStore("flags");
	
	Mono<Long> putMono = store.put("user-1", 0b011001L);
	Mono<Long> getMono = store.get("user-1");
	
	Flux<String> matchFlux = store.matchAll(MatchArgs.mask(0b001000L));
	Mono<Long> countMono = store.countMatchAny(0b011000L);
	```

=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RBitVectorStoreRx<String> store = redisson.getBitVectorStore("flags");
	
	Maybe<Long> putRx = store.put("user-1", 0b011001L);
	Maybe<Long> getRx = store.get("user-1");
	
	Flowable<String> matchRx = store.matchAll(MatchArgs.mask(0b001000L));
	Single<Long> countRx = store.countMatchAny(0b011000L);
	```

### Storing, reading, and removing vectors

`put` stores a 64-bit vector under a key and returns the previous value, or `null` if the key was new. `get` returns the stored vector, or `null` when the key is absent. `contains` tests for presence, `remove` deletes an entry and reports whether one existed, and `size` returns the number of stored vectors.

```java
RBitVectorStore<String> store = redisson.getBitVectorStore("flags");

Long previous = store.put("user-1", 0b011001L); // null if "user-1" was new
Long vector = store.get("user-1");              // null if absent
boolean present = store.contains("user-1");
boolean removed = store.remove("user-1");
long total = store.size();
```

### Bulk operations

Bulk variants apply the same operation to many keys in a single round-trip. `put(Map)` stores many entries at once, `get(Set)` returns the vectors of the present keys (absent keys are omitted from the map rather than mapped to `null`), and `remove(Set)` deletes many keys and returns how many existed. `setBits(Set, mask)` ORs a mask into every key that is already present, skipping absent ones, and returns how many were updated.

```java
store.put(Map.of("user-1", 0b011001L, "user-2", 0b100101L));

Map<String, Long> vectors = store.get(Set.of("user-1", "user-2", "user-3"));

long removed = store.remove(Set.of("user-1", "user-2"));

long updated = store.setBits(Set.of("user-3", "user-4"), 0b000010L); // present keys only
```

### Atomic mask updates

These operations change selected bits of an entry atomically, leaving the rest untouched, and create the entry (treating the old value as `0`) when the key is absent. `setBits` turns masked bits on, `clearBits` turns them off, `flipBits` toggles them, and `replaceBits` overwrites the masked bits with the corresponding bits of a value. `updateAndGet` combines a set and a clear in one step - `(old & ~clearMask) | setMask`, where `setMask` wins on any overlap - and returns the new value, while `getAndUpdate` performs the same change but returns the previous value.

```java
RBitVectorStore<String> store = redisson.getBitVectorStore("flags");

long afterSet = store.setBits("user-1", 0b000110L);     // turn masked bits on
long afterClear = store.clearBits("user-1", 0b000010L); // turn masked bits off
long afterFlip = store.flipBits("user-1", 0b001000L);   // toggle masked bits

// overwrite only the masked bits with those of the value
long afterReplace = store.replaceBits("user-1", 0b001100L, 0b000100L);

// set some bits and clear others in one atomic step (set wins on overlap)
long updated = store.updateAndGet("user-1", 0b000001L, 0b010000L);
Long previous = store.getAndUpdate("user-1", 0b000001L, 0b010000L);
```

### Single-bit operations

For one attribute at a time, the single-bit operations address a bit position in `[0, 63]`; a position outside that range throws `IndexOutOfBoundsException`. `getBit` reads a bit (or `null` if the key is absent); `setBit`, `clearBit`, and `flipBit` change one bit and return the new vector; and `getAndSetBit`/`getAndFlipBit` return the full vector as it was before the change. As with the mask operations, an absent key is created on write.

```java
Boolean bit = store.getBit("user-1", 2); // null if "user-1" is absent

long afterSet = store.setBit("user-1", 2);          // set bit 2 to 1
long afterValue = store.setBit("user-1", 3, false); // set bit 3 to a given value
long afterClear = store.clearBit("user-1", 2);
long afterFlip = store.flipBit("user-1", 4);

long before = store.getAndSetBit("user-1", 5); // returns the vector before the change
```

### Conditional storage and updates

These operations apply only when a precondition holds. `putIfAbsent` stores a vector only if the key is new, returning the existing value otherwise (the `Map.putIfAbsent` convention); `putIfExists` stores only if the key already exists. `updateIfExists` applies a set/clear mask update like `updateAndGet` but skips absent keys instead of creating them. `compareAndSet` swaps the whole vector only if it currently equals an expected value, and `compareAndSetBits` performs that compare-and-swap restricted to the bits selected by a mask.

```java
Long existing = store.putIfAbsent("user-1", 0b000001L);  // null if stored, else current value
Long prevIfExists = store.putIfExists("user-1", 0b000010L); // null if "user-1" was absent

Long updated = store.updateIfExists("user-1", 0b000100L, 0b001000L); // null if absent

boolean swapped = store.compareAndSet("user-1", 0b000001L, 0b000011L);

// compare and swap only within the masked bits
boolean swappedBits = store.compareAndSetBits("user-1", 0b001100L, 0b000100L, 0b001000L);
```

### Counting matches

Each predicate has a counting form that returns how many stored vectors satisfy it, evaluated entirely on the server. `countMatchAll(mask)` counts vectors that have every bit of `mask` set, `countMatchAny(mask)` those with at least one, `countMatchNone(mask)` those with none, and `countMatchExact(mask, target)` those whose masked bits equal `target`. A `mask` of `0` makes the all, none, and exact predicates trivially true (returning `size()`) and the any predicate trivially false (returning `0`).

```java
long all = store.countMatchAll(0b000110L);
long any = store.countMatchAny(0b011000L);
long none = store.countMatchNone(0b000001L);
long exact = store.countMatchExact(0b101001L, 0b100001L);
```

### Iterating matches and keys

The same predicates have iterating forms that return the matching keys: `matchAll`, `matchAny`, and `matchNone` take a `MatchArgs` built from a mask, while `matchExact` takes a `MatchExactArgs` built from a mask and a target. `ids` iterates every stored key. The returned `Iterable` is single-pass - its iterator may be taken only once - and walks the result server-side in batches, so it is safe for stores of any size and reflects a non-strict snapshot. Two parameters tune the walk: `chunkSize` controls how many keys are fetched per round-trip, and `chunkFetchTTL` bounds how long the server-side cursor state is kept, so an iterator abandoned before it is fully consumed is still cleaned up.

```java
// iterate keys whose vectors contain every bit of the mask
for (String id : store.matchAll(MatchArgs.mask(0b000110L))) {
    // ...
}

// exact match on the selected bits
Iterable<String> exact = store.matchExact(
    MatchExactArgs.mask(0b101001L).target(0b100001L));

// tune batch size and server-side cursor TTL
Iterable<String> tuned = store.matchAny(
    MatchArgs.mask(0b101001L)
             .chunkSize(2048)
             .chunkFetchTTL(Duration.ofMinutes(2)));

// iterate all stored keys
for (String id : store.ids()) {
    // ...
}
```

### Use Cases

Bit Vector Store is well-suited to applications that filter many records by combinations of discrete boolean attributes — feature flags, permissions, categorical tags, audience segments, and similar low-cardinality dimensions. Up to 64 such attributes can be packed into a single vector per key, and queries combine them with bitmask predicates evaluated server-side.

**Feature Flags and Permissions**

Each user is associated with a vector whose bits represent capabilities (admin, premium, beta access, export rights, ...). Queries find users matching a required permission set without scanning every user — typically expressed with `matchAll`, which returns keys whose vectors have every required bit set.

```java
// Bit layout: 0=login, 1=premium, 2=admin, 3=beta, 4=export, 5=billing
long PREMIUM = 1L << 1;
long ADMIN   = 1L << 2;
long EXPORT  = 1L << 4;

RBitVectorStore<String> permissions = redisson.getBitVectorStore("user-permissions");
permissions.put("alice", PREMIUM | ADMIN | EXPORT);
permissions.put("bob",   PREMIUM | EXPORT);
permissions.put("carol", PREMIUM);

// Find all users who can both export AND have admin rights
Iterable<String> exporters = permissions.matchAll(
    MatchArgs.mask(ADMIN | EXPORT));

// Count premium users
long premiumCount = permissions.countMatchAll(PREMIUM);
```

**Categorical Tag Filtering**

Each item carries a vector of category bits (dietary tags, content categories, product attributes). Catalog and search queries filter on tag combinations: `matchAny` returns items matching at least one selected tag (broad discovery); `matchExact` returns items matching a specific combination on the selected bits.

```java
// Bit layout: 0=vegan, 1=gluten-free, 2=organic, 3=kosher, 4=halal, 5=nut-free
long VEGAN       = 1L << 0;
long GLUTEN_FREE = 1L << 1;
long NUT_FREE    = 1L << 5;

RBitVectorStore<String> products = redisson.getBitVectorStore("product-tags");
products.put("sku-100", VEGAN | GLUTEN_FREE);
products.put("sku-101", GLUTEN_FREE | NUT_FREE);
products.put("sku-102", VEGAN | GLUTEN_FREE | NUT_FREE);

// Items with at least one of the selected dietary tags
Iterable<String> any = products.matchAny(
    MatchArgs.mask(VEGAN | NUT_FREE));

// Items that are gluten-free AND nut-free, but NOT vegan
Iterable<String> exact = products.matchExact(
    MatchExactArgs.mask(VEGAN | GLUTEN_FREE | NUT_FREE)
                  .target(GLUTEN_FREE | NUT_FREE));
```

**Audience Segments and Experiment Cohorts**

Each user is associated with a vector whose bits represent membership in marketing segments, A/B experiment buckets, or behavioral cohorts. Analytical queries answer "find users in cohort X but not cohort Y" — a question naturally expressed with `matchExact` by selecting both bits in the mask and pinning their required values in the target.

```java
// Bit layout: 0=newsletter, 1=cart-abandoned, 2=high-value, 3=experiment-A, 4=experiment-B
long CART_ABANDONED = 1L << 1;
long HIGH_VALUE     = 1L << 2;
long EXPERIMENT_A   = 1L << 3;
long EXPERIMENT_B   = 1L << 4;

RBitVectorStore<Long> segments = redisson.getBitVectorStore("user-segments");
segments.put(1001L, HIGH_VALUE | EXPERIMENT_A);
segments.put(1002L, CART_ABANDONED | EXPERIMENT_B);
segments.put(1003L, HIGH_VALUE | CART_ABANDONED | EXPERIMENT_A);

// High-value users in experiment A but NOT experiment B — targeting candidates
Iterable<Long> targets = segments.matchExact(
    MatchExactArgs.mask(HIGH_VALUE | EXPERIMENT_A | EXPERIMENT_B)
                  .target(HIGH_VALUE | EXPERIMENT_A));

// How many users abandoned a cart and are NOT in any experiment
long retargeting = segments.countMatchExact(
    CART_ABANDONED | EXPERIMENT_A | EXPERIMENT_B,
    CART_ABANDONED);
```
