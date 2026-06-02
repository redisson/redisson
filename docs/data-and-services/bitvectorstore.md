## BitVector Store

*This feature is available only in [Redisson PRO](https://redisson.pro/feature-comparison.html) edition.*

Java implementation of Valkey or Redis based [RBitVectorStore](https://static.javadoc.io/org.redisson/redisson/latest/org/redisson/api/RBitVectorStore.html) object is a distributed store of 64-bit vectors mapped by keys, with fast server-side filtering by bitmask. Two distinct keys may map to vectors with identical values; they remain distinct entries. Maximum number of stored vectors is limited to `4 294 967 295`. This object is thread-safe.

Requires Redis 7.0.0+ or any Valkey version.

Supported match predicates (where `v` is a stored vector):

- `matchAll(mask)` — every bit set in `mask` is set in `v`
- `matchAny(mask)` — at least one bit set in `mask` is set in `v`
- `matchNone(mask)` — no bit set in `mask` is set in `v`
- `matchExact(mask, target)` — `(v & mask) == target`

Code examples:

=== "Sync"
	```
	RBitVectorStore<String> store = redisson.getBitVectorStore("flags");

	store.put("user-1", 0b011001L);
	store.put("user-2", 0b100101L);
	store.put("user-3", 0b101000L);
	
	Long vector = store.get("user-1");
	
	long count = store.countMatchAny(0b011000L);
	Iterable<String> ids = store.matchExact(
	    MatchExactArgs.mask(0b101001L).target(0b100001L));
	```

=== "Async"
	```
	RBitVectorStoreAsync<String> store = redisson.getBitVectorStore("flags");

	RFuture<Long> putFuture = store.putAsync("user-1", 0b011001L);
	RFuture<Long> getFuture = store.getAsync("user-1");
	
	RFuture<Long> countFuture = store.countMatchAnyAsync(0b011000L);
	RFuture<Iterable<String>> matchFuture = store.matchExactAsync(
	    MatchExactArgs.mask(0b101001L).target(0b100001L));
	```

=== "Reactive"
	```
	RedissonReactiveClient redisson = redissonClient.reactive();
	RBitVectorStoreReactive<String> store = redisson.getBitVectorStore("flags");

	Mono<Long> putMono = store.put("user-1", 0b011001L);
	Mono<Long> getMono = store.get("user-1");
	
	Mono<Long> countMono = store.countMatchAny(0b011000L);
	Flux<String> matchFlux = store.matchExact(
	    MatchExactArgs.mask(0b101001L).target(0b100001L));
	```

=== "RxJava3"
	```
	RedissonRxClient redisson = redissonClient.rxJava();
	RBitVectorStoreRx<String> store = redisson.getBitVectorStore("flags");

	Maybe<Long> putRx = store.put("user-1", 0b011001L);
	Maybe<Long> getRx = store.get("user-1");
	
	Single<Long> countRx = store.countMatchAny(0b011000L);
	Flowable<String> matchRx = store.matchExact(
	    MatchExactArgs.mask(0b101001L).target(0b100001L));
	```

Tuning parameters for result iteration (batch size and server-side state TTL) are controlled via `MatchArgs` and `MatchExactArgs`:

```java
Iterable<String> ids = store.matchAny(
    MatchArgs.mask(0b101001L)
             .chunkSize(2048)
             .chunkFetchTTL(Duration.ofMinutes(2)));
```

The returned `Iterable` is single-pass and walks the result server-side in batches; abandoning iteration without consuming it fully relies on `chunkFetchTTL` to reclaim server-side state.

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

