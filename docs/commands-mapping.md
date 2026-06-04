# Commands mapping

This page maps each Valkey or Redis command to the Redisson object and methods that issue it. Commands are grouped by family; within each group the three API columns show the equivalent call on the synchronous/asynchronous API (`Redisson.create(config)`), the Reactive API (`redisson.reactive()`), and the RxJava3 API (`redisson.rxJava()`).

A dash (`-`) means the command has no direct equivalent on that API - for instance because it is set through `Config` rather than called at runtime, or is handled internally by Redisson.

## Strings

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| APPEND | RBinaryStream.<br/>getOutputStream().write() | - | - |
| DECR | RAtomicLong.<br/>decrementAndGet()<br/>decrementAndGetAsync() | RAtomicLongReactive.<br/>decrementAndGet() | RAtomicLongRx.<br/>decrementAndGet() |
| DECRBY | RAtomicLong.<br/>addAndGet()<br/>addAndGetAsync() | RAtomicLongReactive.<br/>addAndGet() | RAtomicLongRx.<br/>addAndGet() |
| GET | RBucket.<br/>get()<br/>getAsync()<br/><br/>RBinaryStream.<br/>get()<br/>getAsync() | RBucketReactive.<br/>get()<br/><br/>RBinaryStreamReactive.<br/>get() | RBucketRx.<br/>get()<br/><br/>RBinaryStreamRx.<br/>get() |
| GETEX | RBucket.<br/>getAndExpire()<br/>getAndExpireAsync()<br/>getAndClearExpire()<br/>getAndClearExpireAsync() | RBucketReactive.<br/>getAndExpire()<br/>getAndClearExpire() | RBucketRx.<br/>getAndExpire()<br/>getAndClearExpire() |
| GETRANGE | RBinaryStream.<br/>getChannel().read() | RBinaryStreamReactive.<br/>read() | RBinaryStreamRx.<br/>read() |
| GETSET | RBucket.<br/>getAndSet()<br/>getAndSetAsync()<br/><br/>RAtomicLong.<br/>getAndSet()<br/>getAndSetAsync()<br/><br/>RAtomicDouble.<br/>getAndSet()<br/>getAndSetAsync() | RBucketReactive.<br/>getAndSet()<br/><br/>RAtomicLongReactive.<br/>getAndSet()<br/><br/>RAtomicDoubleReactive.<br/>getAndSet() | RBucketRx.<br/>getAndSet()<br/><br/>RAtomicLongRx.<br/>getAndSet()<br/><br/>RAtomicDoubleRx.<br/>getAndSet() |
| INCR | RAtomicLong.<br/>incrementAndGet()<br/>incrementAndGetAsync() | RAtomicLongReactive.<br/>incrementAndGet() | RAtomicLongRx.<br/>incrementAndGet() |
| INCRBY | RAtomicLong.<br/>addAndGet()<br/>addAndGetAsync() | RAtomicLongReactive.<br/>addAndGet() | RAtomicLongRx.<br/>addAndGet() |
| INCRBYFLOAT | RAtomicDouble.<br/>addAndGet()<br/>addAndGetAsync() | RAtomicDoubleReactive.<br/>addAndGet() | RAtomicDoubleRx.<br/>addAndGet() |
| LCS | RBucket.<br/>findCommon()<br/>findCommonAsync()<br/>findCommonLength()<br/>findCommonLengthAsync() | RBucketReactive.<br/>findCommon()<br/>findCommonLength() | RBucketRx.<br/>findCommon()<br/>findCommonLength() |
| MGET | RBuckets.<br/>get()<br/>getAsync() | RBucketsReactive.<br/>get() | RBucketsRx.<br/>get() |
| MSET | RBuckets.<br/>set()<br/>setAsync() | RBucketsReactive.<br/>set() | RBucketsRx.<br/>set() |
| MSETNX | RBuckets.<br/>trySet()<br/>trySetAsync() | RBucketsReactive.<br/>trySet() | RBucketsRx.<br/>trySet() |
| PSETEX | RBucket.<br/>set()<br/>setAsync() | RBucketReactive.<br/>set() | RBucketRx.<br/>set() |
| SET | RBucket.<br/>set()<br/>setAsync() | RBucketReactive.<br/>set() | RBucketRx.<br/>set() |
| SETEX | RBucket.<br/>set()<br/>setAsync() | RBucketReactive.<br/>set() | RBucketRx.<br/>set() |
| SETNX | RBucket.<br/>setIfAbsent()<br/>setIfAbsentAsync() | RBucketReactive.<br/>setIfAbsent() | RBucketRx.<br/>setIfAbsent() |
| SETRANGE | RBinaryStream.<br/>getChannel().write() | RBinaryStreamReactive.<br/>write() | RBinaryStreamRx.<br/>write() |
| STRLEN | RBucket.<br/>size()<br/>sizeAsync() | RBucketReactive.<br/>size() | RBucketRx.<br/>size() |

## Bitmaps

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| BITCOUNT | RBitSet.<br/>cardinality()<br/>cardinalityAsync() | RBitSetReactive.<br/>cardinality() | RBitSetRx.<br/>cardinality() |
| BITFIELD | RBitSet.<br/>getByte()<br/>setByte()<br/>incrementAndGetByte()<br/><br/>getShort()<br/>setShort()<br/>incrementAndGetShort()<br/><br/>getInteger()<br/>setInteger()<br/>incrementAndGetInteger()<br/><br/>getLong()<br/>setLong()<br/>incrementAndGetLong() | RBitSetReactive.<br/>getByte()<br/>setByte()<br/>incrementAndGetByte()<br/><br/>getShort()<br/>setShort()<br/>incrementAndGetShort()<br/><br/>getInteger()<br/>setInteger()<br/>incrementAndGetInteger()<br/><br/>getLong()<br/>setLong()<br/>incrementAndGetLong() | RBitSetRx.<br/>getByte()<br/>setByte()<br/>incrementAndGetByte()<br/><br/>getShort()<br/>setShort()<br/>incrementAndGetShort()<br/><br/>getInteger()<br/>setInteger()<br/>incrementAndGetInteger()<br/><br/>getLong()<br/>setLong()<br/>incrementAndGetLong() |
| BITOP | RBitSet.<br/>or()<br/>and()<br/>xor()<br/>orAsync()<br/>andAsync()<br/>xorAsync() | RBitSetReactive.<br/>or()<br/>and()<br/>xor() | RBitSetRx.<br/>or()<br/>and()<br/>xor() |
| BITPOS | RBitSet.<br/>length()<br/>lengthAsync() | RBitSetReactive.<br/>length() | RBitSetRx.<br/>length() |
| GETBIT | RBitSet.<br/>get()<br/>getAsync() | RBitSetReactive.<br/>get() | RBitSetRx.<br/>get() |
| SETBIT | RBitSet.<br/>set()<br/>clear()<br/>setAsync()<br/>clearAsync() | RBitSetReactive.<br/>set()<br/>clear() | RBitSetRx.<br/>set()<br/>clear() |

## HyperLogLog

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| PFADD | RHyperLogLog.<br/>add()<br/>addAsync()<br/>addAll()<br/>addAllAsync() | RHyperLogLogReactive.<br/>add()<br/><br/>addAll() | RHyperLogLogRx.<br/>add()<br/><br/>addAll() |
| PFCOUNT | RHyperLogLog.<br/>count()<br/>countAsync()<br/>countWith()<br/>countWithAsync() | RHyperLogLogReactive.<br/>count()<br/>countWith() | RHyperLogLogRx.<br/>count()<br/>countWith() |
| PFMERGE | RHyperLogLog.<br/>mergeWith()<br/>mergeWithAsync() | RHyperLogLogReactive.<br/>mergeWith() | RHyperLogLogRx.<br/>mergeWith() |

## Hashes

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| HDEL | RMap.<br/>fastRemove()<br/>fastRemoveAsync() | RMapReactive.<br/>fastRemove() | RMapRx.<br/>fastRemove() |
| HEXISTS | RMap.<br/>containsKey()<br/>containsKeyAsync() | RMapReactive.<br/>containsKey() | RMapRx.<br/>containsKey() |
| HEXPIRE | RMapCacheNative.<br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()<br/>putAsync()<br/>fastPutAsync()<br/>putIfAbsentAsync()<br/>fastPutIfAbsentAsync()<br/>putAllAsync()<br/>expireEntryAsync() | RMapCacheNativeReactive.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry() | RMapCacheNativeRx.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry() |
| HEXPIREAT | RMapCacheNative.<br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()<br/>putAsync()<br/>fastPutAsync()<br/>putIfAbsentAsync()<br/>fastPutIfAbsentAsync()<br/>putAllAsync()<br/>expireEntryAsync() | RMapCacheNativeReactive.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry() | RMapCacheNativeRx.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry() |
| HGET | RMap.<br/>get()<br/>getAsync() | RMapReactive.<br/>get() | RMapRx.<br/>get() |
| HGETALL | RMap.<br/>readAllEntrySet()<br/>readAllEntrySetAsync() | RMapReactive.<br/>readAllEntrySet() | RMapRx.<br/>readAllEntrySet() |
| HINCRBY | RMap.<br/>addAndGet()<br/>addAndGetAsync() | RMapReactive.<br/>addAndGet() | RMapRx.<br/>addAndGet() |
| HINCRBYFLOAT | RMap.<br/>addAndGet()<br/>addAndGetAsync() | RMapReactive.<br/>addAndGet() | RMapRx.<br/>addAndGet() |
| HKEYS | RMap.<br/>readAllKeySet()<br/>readAllKeySetAsync() | RMapReactive.<br/>readAllKeySet() | RMapRx.<br/>readAllKeySet() |
| HLEN | RMap.<br/>size()<br/>sizeAsync() | RMapReactive.<br/>size() | RMapRx.<br/>size() |
| HMGET | RMap.<br/>getAll()<br/>getAllAsync() | RMapReactive.<br/>getAll() | RMapRx.<br/>getAll() |
| HMSET | RMap.<br/>putAll()<br/>putAllAsync() | RMapReactive.<br/>putAll() | RMapRx.<br/>putAll() |
| HPERSIST | RMapCacheNative.<br/>clearExpire()<br/>clearExpireAsync() | RMapCacheNativeReactive.<br/>clearExpire() | RMapCacheNativeRx.<br/>clearExpire() |
| HPEXPIRE | RMapCacheNative.<br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()<br/>putAsync()<br/>fastPutAsync()<br/>putIfAbsentAsync()<br/>fastPutIfAbsentAsync()<br/>putAllAsync()<br/>expireEntryAsync() | RMapCacheNativeReactive.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry() | RMapCacheNativeRx.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry() |
| HPEXPIREAT | RMapCacheNative.<br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()<br/>putAsync()<br/>fastPutAsync()<br/>putIfAbsentAsync()<br/>fastPutIfAbsentAsync()<br/>putAllAsync()<br/>expireEntryAsync() | RMapCacheNativeReactive.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry() | RMapCacheNativeRx.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry() |
| HPTTL | RMapCacheNative.<br/>remainTimeToLive()<br/>remainTimeToLiveAsync() | RMapCacheNativeReactive.<br/>remainTimeToLive() | RMapCacheNativeRx.<br/>remainTimeToLive() |
| HRANDFIELD | RMap.<br/>putAll()<br/>putAllAsync() | RMapReactive.<br/>putAll() | RMapRx.<br/>putAll() |
| HSCAN | RMap.<br/>keySet().iterator()<br/>values().iterator()<br/>entrySet().iterator() | RMapReactive.<br/>keyIterator()<br/>valueIterator()<br/>entryIterator() | RMapRx.<br/>keyIterator()<br/>valueIterator()<br/>entryIterator() |
| HSET | RMap.<br/>randomEntries()<br/>randomKeys()<br/>randomEntriesAsync()<br/>randomKeysAsync() | RMapReactive.<br/>randomEntries()<br/>randomKeys() | RMapRx.<br/>randomEntries()<br/>randomKeys() |
| HSETNX | RMap.<br/>fastPutIfAbsent()<br/>fastPutIfAbsentAsync() | RMapReactive.<br/>fastPutIfAbsent() | RMapRx.<br/>fastPutIfAbsent() |
| HSTRLEN | RMap.<br/>valueSize()<br/>valueSizeAsync() | RMapReactive.<br/>valueSize() | RMapRx.<br/>valueSize() |
| HVALS | RMap.<br/>readAllValues()<br/>readAllValuesAsync() | RMapReactive.<br/>readAllValues() | RMapRx.<br/>readAllValues() |

## Lists & blocking queues

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| BLMOVE | RBlockingDeque.<br/>move()<br/>moveAsync() | RBlockingDequeReactive.<br/>move() | RBlockingDequeRx.<br/>move() |
| BLMPOP | RBlockingQueue.<br/>pollLastFromAny()<br/>pollFirstFromAny()<br/>pollLastFromAnyAsync()<br/>pollFirstFromAnyAsync() | RBlockingQueueReactive.<br/>pollLastFromAny()<br/>pollFirstFromAny() | RBlockingQueueRx.<br/>pollLastFromAny()<br/>pollFirstFromAny() |
| BLPOP | RBlockingQueue.<br/>take()<br/>poll()<br/>pollFromAny()<br/>takeAsync()<br/>pollAsync()<br/>pollFromAnyAsync() | RBlockingQueueReactive.<br/>take()<br/>poll()<br/>pollFromAny() | RBlockingQueueRx.<br/>take()<br/>poll()<br/>pollFromAny() |
| BRPOP | RBlockingDeque.<br/>takeLast()<br/>takeLastAsync() | RBlockingDequeReactive.<br/>takeLast() | RBlockingDequeRx.<br/>takeLast() |
| BRPOPLPUSH | RBlockingQueue.<br/>pollLastAndOfferFirstTo()<br/>pollLastAndOfferFirstToAsync() | RBlockingQueueReactive.<br/>pollLastAndOfferFirstTo() | RBlockingQueueRx.<br/>pollLastAndOfferFirstTo() |
| LINDEX | RList.<br/>get()<br/>getAsync() | RListReactive.<br/>get() | RListRx.<br/>get() |
| LINSERT | RList.<br/>addBefore()<br/>addAfter()<br/>addBeforeAsync()<br/>addAfterAsync() | RListReactive.<br/>addBefore()<br/>addAfter() | RListRx.<br/>addBefore()<br/>addAfter() |
| LLEN | RList.<br/>size()<br/>sizeAsync() | RListReactive.<br/>size() | RListRx.<br/>size() |
| LMOVE | RDeque.<br/>move()<br/>moveAsync() | RDequeReactive.<br/>move() | RDequeRx.<br/>move() |
| LPOP | RQueue.<br/>poll()<br/>pollAsync() | RQueueReactive.<br/>poll() | RQueueRx.<br/>poll() |
| LPUSH | RDeque.<br/>addFirst()<br/>addFirstAsync() | RDequeReactive.<br/>addFirst() | RDequeRx.<br/>addFirst() |
| LPUSHX | RDeque.<br/>addFirstIfExists()<br/>addFirstIfExistsAsync() | RDequeReactive.<br/>addFirstIfExists() | RDequeRx.<br/>addFirstIfExists() |
| LRANGE | RList.<br/>readAll()<br/>readAllAsync() | RListReactive.readAll() | RListRx.readAll() |
| LREM | RList.<br/>fastRemove()<br/>fastRemoveAsync() | RListReactive.<br/>fastRemove() | RListRx.<br/>fastRemove() |
| LSET | RList.<br/>fastSet()<br/>fastSetAsync() | RListReactive.<br/>fastSet() | RListRx.<br/>fastSet() |
| LTRIM | RList.<br/>trim()<br/>trimAsync() | RListReactive.<br/>trim() | RListRx.<br/>trim() |
| RPOP | RDeque.<br/>pollLast()<br/>removeLast()<br/>pollLastAsync()<br/>removeLastAsync() | RDequeReactive.<br/>pollLast()<br/>removeLast() | RDequeRx.<br/>pollLast()<br/>removeLast() |
| RPOPLPUSH | RDeque.<br/>pollLastAndOfferFirstTo()<br/>pollLastAndOfferFirstToAsync() | RDequeReactive.<br/>pollLastAndOfferFirstTo() | RDequeRx.<br/>pollLastAndOfferFirstTo() |
| RPUSH | RList.<br/>add()<br/>addAsync() | RListReactive.<br/>add() | RListRx.<br/>add() |
| RPUSHX | RDeque.<br/>addLastIfExists()<br/>addLastIfExistsAsync() | RListReactive.<br/>addLastIfExists() | RListRx.<br/>addLastIfExists() |

## Sets

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| SADD | RSet.<br/>add()<br/>addAsync() | RSetReactive.<br/>add() | RSetRx.<br/>add() |
| SCARD | RSet.<br/>size()<br/>sizeAsync() | RSetReactive.<br/>size() | RSetRx.<br/>size() |
| SDIFF | RSet.<br/>readDiff()<br/>readDiffAsync() | RSetReactive.<br/>readDiff() | RSetRx.<br/>readDiff() |
| SDIFFSTORE | RSet.<br/>diff()<br/>diffAsync() | RSetReactive.<br/>diff() | RSetRx.<br/>diff() |
| SINTER | RSet.<br/>readIntersection()<br/>readIntersectionAsync() | RSetReactive.<br/>readIntersection() | RSetRx.<br/>readIntersection() |
| SINTERCARD | RSet.<br/>countIntersection()<br/>countIntersectionAsync() | RSetReactive.<br/>countIntersection() | RSetRx.<br/>countIntersection() |
| SINTERSTORE | RSet.<br/>intersection()<br/>intersectionAsync() | RSetReactive.<br/>intersection() | RSetRx.<br/>intersection() |
| SISMEMBER | RSet.<br/>contains()<br/>containsAsync() | RSetReactive.<br/>contains() | RSetRx.<br/>contains() |
| SMEMBERS | RSet.<br/>readAll()<br/>readAllAsync() | RSetReactive.<br/>readAll() | RSetRx.<br/>readAll() |
| SMISMEMBER | RSet.<br/>containsEach()<br/>containsEachAsync() | RSetReactive.<br/>containsEach() | RSetRx.<br/>containsEach() |
| SMOVE | RSet.<br/>move()<br/>moveAsync() | RSetReactive.<br/>move() | RSetRx.<br/>move() |
| SPOP | RSet.<br/>removeRandom()<br/>removeRandomAsync() | RSetReactive.<br/>removeRandom() | RSetRx.<br/>removeRandom() |
| SRANDMEMBER | RSet.<br/>random()<br/>randomAsync() | RSetReactive.<br/>random() | RSetRx.<br/>random() |
| SREM | RSet.<br/>remove()<br/>removeAsync() | RSetReactive.<br/>remove() | RSetRx.<br/>remove() |
| SSCAN | RSet.<br/>iterator() | RSetReactive.<br/>iterator() | RSetRx.<br/>iterator() |
| SUNION | RSet.<br/>readUnion()<br/>readUnionAsync() | RSetReactive.<br/>readUnion() | RSetRx.<br/>readUnion() |
| SUNIONSTORE | RSet.<br/>union()<br/>unionAsync() | RSetReactive.<br/>union() | RSetRx.<br/>union() |

## Sorted sets

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| BZMPOP | RScoredSortedSet.<br/>pollLast()<br/>pollLastAsync()<br/>pollLastFromAny()<br/>pollLastEntriesFromAny() | RScoredSortedSetReactive.<br/>pollLast()<br/>pollLastFromAny()<br/>pollLastEntriesFromAny() | RScoredSortedSetRx.<br/>pollLast()<br/>pollLastFromAny()<br/>pollLastEntriesFromAny() |
| BZPOPMAX | RScoredSortedSet.<br/>pollLast()<br/>pollLastAsync() | RScoredSortedSetReactive.<br/>pollLast() | RScoredSortedSetRx.<br/>pollLast() |
| BZPOPMIN | RScoredSortedSet.<br/>pollFirst()<br/>pollFirstAsync() | RScoredSortedSetReactive.<br/>pollFirst() | RScoredSortedSetRx.<br/>pollFirst() |
| ZADD | RScoredSortedSet.<br/>add()<br/>addAsync()<br/>addAll()<br/>addAllAsync() | RScoredSortedSetReactive.<br/>add()<br/>addAll() | RScoredSortedSetRx.<br/>add()<br/>addAll() |
| ZCARD | RScoredSortedSet.<br/>size()<br/>sizeAsync() | RScoredSortedSetReactive.<br/>size() | RScoredSortedSetRx.<br/>size() |
| ZCOUNT | RScoredSortedSet.<br/>count()<br/>countAsync() | RScoredSortedSetReactive.<br/>count() | RScoredSortedSetRx.<br/>count() |
| ZDIFF | RScoredSortedSet.<br/>readDiff()<br/>readDiffAsync() | RScoredSortedSetReactive.<br/>readDiff() | RScoredSortedSetRx.<br/>readDiff() |
| ZDIFFSTORE | RScoredSortedSet.<br/>diff()<br/>diffAsync() | RScoredSortedSetReactive.<br/>diff() | RScoredSortedSetRx.<br/>diff() |
| ZINCRBY | RScoredSortedSet.<br/>addScore()<br/>addScoreAsync() | RScoredSortedSetReactive.<br/>addScore() | RScoredSortedSetRx.<br/>addScore() |
| ZINTER | RScoredSortedSet.<br/>readIntersection()<br/>readIntersectionAsync() | RScoredSortedSetReactive.<br/>readIntersection() | RScoredSortedSetRx.<br/>readIntersection() |
| ZINTERCARD | RScoredSortedSet.<br/>countIntersection()<br/>countIntersectionAsync() | RScoredSortedSetReactive.<br/>countIntersection() | RScoredSortedSetRx.<br/>countIntersection() |
| ZINTERSTORE | RScoredSortedSet.<br/>intersection()<br/>intersectionAsync() | RScoredSortedSetReactive.<br/>intersection() | RScoredSortedSetRx.<br/>intersection() |
| ZLEXCOUNT | RLexSortedSet.<br/>lexCount()<br/>lexCountHead()<br/>lexCountTail()<br/>lexCountAsync()<br/>lexCountHeadAsync()<br/>lexCountTailAsync() | RLexSortedSetReactive.<br/>lexCount()<br/>lexCountHead()<br/>lexCountTail() | RLexSortedSetRx.<br/>lexCount()<br/>lexCountHead()<br/>lexCountTail() |
| ZMPOP | RScoredSortedSet.<br/>pollFirstEntriesFromAny()<br/>pollFirstEntriesFromAnyAsync()<br/>pollLastFromAny()<br/>pollLastFromAnyAsync()<br/>pollFirstFromAny()<br/>pollFirstFromAnyAsync() | RScoredSortedSetReactive.<br/>pollFirstEntriesFromAny()<br/>pollLastFromAny()<br/>pollFirstFromAny() | RScoredSortedSetRx.<br/>pollFirstEntriesFromAny()<br/>pollLastFromAny()<br/>pollFirstFromAny() |
| ZMSCORE | RScoredSortedSet.<br/>getScore()<br/>getScoreAsync() | RScoredSortedSetReactive.<br/>getScore() | RScoredSortedSetRx.<br/>getScore() |
| ZPOPMAX | RScoredSortedSet.<br/>pollLast()<br/>pollLastAsync() | RScoredSortedSetReactive.<br/>pollLast() | RScoredSortedSetRx.<br/>pollLast() |
| ZPOPMIN | RScoredSortedSet.<br/>pollFirst()<br/>pollFirstAsync() | RScoredSortedSetReactive.<br/>pollFirst() | RScoredSortedSetRx.<br/>pollFirst() |
| ZRANDMEMBER | RScoredSortedSet.<br/>random()<br/>randomAsync() | RScoredSortedSetReactive.<br/>random() | RScoredSortedSetRx.<br/>random() |
| ZRANGE | RScoredSortedSet.<br/>valueRange()<br/>valueRangeAsync() | RScoredSortedSetReactive.<br/>valueRange() | RScoredSortedSetRx.<br/>valueRange() |
| ZRANGEBYLEX | RLexSortedSet.<br/>range()<br/>rangeHead()<br/>rangeTail()<br/>rangeAsync()<br/>rangeHeadAsync()<br/>rangeTailAsync() | RLexSortedSetReactive.<br/>range()<br/>rangeHead()<br/>rangeTail() | RLexSortedSetRx.<br/>range()<br/>rangeHead()<br/>rangeTail() |
| ZRANGEBYSCORE | RScoredSortedSet.<br/>valueRange()<br/>entryRange()<br/>valueRangeAsync()<br/>entryRangeAsync() | RScoredSortedSetReactive.<br/>valueRange()<br/>entryRange() | RScoredSortedSetRx.<br/>valueRange()<br/>entryRange() |
| ZRANGESTORE | RScoredSortedSet.<br/>rangeTo()<br/>rangeToAsync()<br/>revRangeTo()<br/>revRangeToAsync() | RScoredSortedSetReactive.<br/>rangeTo()<br/>revRangeTo() | RScoredSortedSetRx.<br/>rangeTo()<br/>revRangeTo() |
| ZRANK | RScoredSortedSet.<br/>rank()<br/>rankAsync() | RScoredSortedSetReactive.<br/>rank() | RScoredSortedSetRx.<br/>rank() |
| ZREM | RScoredSortedSet.<br/>remove()<br/>removeAll()<br/>removeAsync()<br/>removeAllAsync() | RScoredSortedSetReactive.<br/>remove()<br/>removeAll() | RScoredSortedSetRx.<br/>remove()<br/>removeAll() |
| ZREMRANGEBYLEX | RLexSortedSet.<br/>removeRange()<br/>removeRangeHead()<br/>removeRangeTail()<br/>removeRangeAsync()<br/>removeRangeHeadAsync()<br/>removeRangeTailAsync() | RLexSortedSetReactive.<br/>removeRange()<br/>removeRangeHead()<br/>removeRangeTail() | RLexSortedSetRx.<br/>removeRange()<br/>removeRangeHead()<br/>removeRangeTail() |
| ZREMRANGEBYRANK | RScoredSortedSet.<br/>removeRangeByRank()<br/>removeRangeByRankAsync() | RScoredSortedSetReactive.<br/>removeRangeByRank() | RScoredSortedSetRx.<br/>removeRangeByRank() |
| ZREMRANGEBYSCORE | RScoredSortedSet.<br/>removeRangeByScore()<br/>removeRangeByScoreAsync() | RScoredSortedSetReactive.<br/>removeRangeByScore() | RScoredSortedSetRx.<br/>removeRangeByScore() |
| ZREVRANGE | RScoredSortedSet.<br/>valueRangeReversed()<br/>valueRangeReversedAsync() | RScoredSortedSetReactive.<br/>valueRangeReversed() | RScoredSortedSetRx.<br/>valueRangeReversed() |
| ZREVRANGEBYLEX | RLexSortedSet.<br/>rangeReversed()<br/>rangeReversedAsync() | RLexSortedSetReactive.<br/>rangeReversed() | RLexSortedSetSetRx.<br/>rangeReversed() |
| ZREVRANGEBYSCORE | RScoredSortedSet.<br/>valueRangeReversed()<br/>entryRangeReversed()<br/>valueRangeReversedAsync()<br/>entryRangeReversedAsync() | RScoredSortedSetReactive.<br/>entryRangeReversed()<br/>valueRangeReversed() | RScoredSortedSetRx.<br/>entryRangeReversed()<br/>valueRangeReversed() |
| ZREVRANK | RScoredSortedSet.<br/>revRank()<br/>revRankAsync() | RScoredSortedSetReactive.<br/>revRank() | RScoredSortedSetRx.<br/>revRank() |
| ZSCORE | RScoredSortedSet.<br/>getScore()<br/>getScoreAsync() | RScoredSortedSetReactive.<br/>getScore() | RScoredSortedSetRx.<br/>getScore() |
| ZUNION | RScoredSortedSet.<br/>readUnion()<br/>readUnionAsync() | RScoredSortedSetReactive.<br/>readUnion() | RScoredSortedSetRx.<br/>readUnion() |
| ZUNIONSTORE | RScoredSortedSet.<br/>union()<br/>unionAsync() | RScoredSortedSetReactive.<br/>union() | RScoredSortedSetRx.<br/>union() |

## Geospatial

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| GEOADD | RGeo.<br/>add()<br/>addAsync() | RGeoReactive.<br/>add() | RGeoRx.<br/>add() |
| GEODIST | RGeo.<br/>dist()<br/>distAsync() | RGeoReactive.<br/>dist() | RGeoRx.<br/>dist() |
| GEOHASH | RGeo.<br/>hash()<br/>hashAsync() | RGeoReactive.<br/>hash() | RGeoRx.<br/>hash() |
| GEOPOS | RGeo.<br/>pos()<br/>posAsync() | RGeoReactive.<br/>pos() | RGeoRx.<br/>pos() |
| GEORADIUS | RGeo.<br/>radius()<br/>radiusAsync()<br/>radiusWithDistance()<br/>radiusWithDistanceAsync()<br/>radiusWithPosition()<br/>radiusWithPositionAsync() | RGeoReactive.<br/>radius()<br/>radiusWithDistance()<br/>radiusWithPosition() | RGeoRx.<br/>radius()<br/>radiusWithDistance()<br/>radiusWithPosition() |
| GEORADIUSBYMEMBER | RGeo.<br/>radiusWithDistance()<br/>radiusWithDistanceAsync() | RGeoReactive.<br/>radiusWithDistance() | RGeoRx.<br/>radiusWithDistance() |
| GEOSEARCH | RGeo.<br/>search()<br/>searchAsync() | RGeoReactive.<br/>search() | RGeoRx.<br/>search() |
| GEOSEARCHSTORE | RGeo.<br/>storeSearchTo()<br/>storeSearchToAsync() | RGeoReactive.<br/>storeSearchTo() | RGeoRx.<br/>storeSearchTo() |

## Streams

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| XACK | RStream.<br/>ack()<br/>ackAsync() | RStreamReactive.<br/>ack() | RStreamRx.<br/>ack() |
| XADD | RStream.<br/>add()<br/>addAsync() | RStreamReactive.<br/>add() | RStreamRx.<br/>add() |
| XAUTOCLAIM | RStream.<br/>autoClaim()<br/>autoClaimAsync() | RStreamReactive.<br/>autoClaim() | RStreamRx.<br/>autoClaim() |
| XCLAIM | RStream.<br/>claim()<br/>claimAsync() | RStreamReactive.<br/>claim() | RStreamRx.<br/>claim() |
| XDEL | RStream.<br/>remove()<br/>removeAsync() | RStreamReactive.<br/>remove() | RStreamRx.<br/>remove() |
| XGROUP | RStream.<br/>createGroup()<br/>removeGroup()<br/>updateGroup()<br/>createGroupAsync()<br/>removeGroupAsync()<br/>updateGroupAsync() | RStreamReactive.<br/>createGroup()<br/>removeGroup()<br/>updateGroup() | RStreamRx.<br/>createGroup()<br/>removeGroup()<br/>updateGroup() |
| XINFO | RStream.<br/>getInfo()<br/>listGroups()<br/>listConsumers()<br/>getInfoAsync()<br/>listGroupsAsync()<br/>listConsumersAsync() | RStreamReactive.<br/>getInfo()<br/>listGroups()<br/>listConsumers() | RStreamRx.<br/>getInfo()<br/>listGroups()<br/>listConsumers() |
| XLEN | RStream.<br/>size()<br/>sizeAsync() | RStreamReactive.<br/>size() | RStreamRx.<br/>size() |
| XNACK | RStream.<br/>nack()<br/>nackAsync() | RStreamReactive.<br/>nack() | RStreamRx.<br/>nack() |
| XPENDING | RStream.<br/>listPending()<br/>listPendingAsync() | RStreamReactive.<br/>listPending() | RStreamRx.<br/>listPending() |
| XRANGE | RStream.<br/>range()<br/>rangeAsync() | RStreamReactive.<br/>range() | RStreamRx.<br/>range() |
| XREAD | RStream.<br/>read()<br/>readAsync() | RStreamReactive.<br/>read() | RStreamRx.<br/>read() |
| XREADGROUP | RStream.<br/>readGroup()<br/>readGroupAsync() | RStreamReactive.<br/>readGroup() | RStreamRx.<br/>readGroup() |
| XREVRANGE | RStream.<br/>rangeReversed()<br/>rangeReversedAsync() | RStreamReactive.<br/>rangeReversed() | RStreamRx.<br/>rangeReversed() |
| XTRIM | RStream.<br/>trim()<br/>trimAsync() | RStreamReactive.<br/>trim() | RStreamRx.<br/>trim() |

## Probabilistic filters

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| BF.ADD | RBloomFilterNative.<br/>add()<br/>addAsync() | RBloomFilterNativeReactive.<br/>add() | RBloomFilterNativeRx.<br/>add() |
| BF.CARD | RBloomFilterNative.<br/>count()<br/>countAsync() | RBloomFilterNativeReactive.<br/>count() | RBloomFilterNativeRx.<br/>count() |
| BF.EXISTS | RBloomFilterNative.<br/>exists()<br/>existsAsync() | RBloomFilterNativeReactive.<br/>exists() | RBloomFilterNativeRx.<br/>exists() |
| BF.INFO | RBloomFilterNative.<br/>getInfo()<br/>getInfoAsync() | RBloomFilterNativeReactive.<br/>getInfo() | RBloomFilterNativeRx.<br/>getInfo() |
| BF.LOADCHUNK | RBloomFilterNative.<br/>loadChunk()<br/>loadChunkAsync() | RBloomFilterNativeReactive.<br/>loadChunk() | RBloomFilterNativeRx.<br/>loadChunk() |
| BF.RESERVE | RBloomFilterNative.<br/>init()<br/>initAsync() | RBloomFilterNativeReactive.<br/>init() | RBloomFilterNativeRx.<br/>init() |
| BF.SCANDUMP | RBloomFilterNative.<br/>scanDump()<br/>scanDumpAsync() | RBloomFilterNativeReactive.<br/>scanDump() | RBloomFilterNativeRx.<br/>scanDump() |
| CF.ADD | RCuckooFilter.<br/>add()<br/>addAsync() | RCuckooFilterReactive.<br/>add() | RCuckooFilterRx.<br/>add() |
| CF.ADDNX | RCuckooFilter.<br/>addIfAbsent()<br/>addIfAbsentAsync() | RCuckooFilterReactive.<br/>addIfAbsent() | RCuckooFilterRx.<br/>addIfAbsent() |
| CF.COUNT | RCuckooFilter.<br/>count()<br/>countAsync() | RCuckooFilterReactive.<br/>count() | RCuckooFilterRx.<br/>count() |
| CF.DEL | RCuckooFilter.<br/>remove()<br/>removeAsync() | RCuckooFilterReactive.<br/>remove() | RCuckooFilterRx.<br/>remove() |
| CF.EXISTS | RCuckooFilter.<br/>exists()<br/>existsAsync() | RCuckooFilterReactive.<br/>exists() | RCuckooFilterRx.<br/>exists() |
| CF.INFO | RCuckooFilter.<br/>getInfo()<br/>getInfoAsync() | RCuckooFilterReactive.<br/>getInfo() | RCuckooFilterRx.<br/>getInfo() |
| CF.RESERVE | RCuckooFilter.<br/>init()<br/>initAsync() | RCuckooFilterReactive.<br/>init() | RCuckooFilterRx.<br/>init() |

## JSON

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| JSON.ARRAPPEND | RJsonBucket.<br/>arrayAppend()<br/>arrayAppendAsync() | RJsonBucketReactive.<br/>arrayAppend() | RJsonBucketRx.<br/>arrayAppend() |
| JSON.ARRINDEX | RJsonBucket.<br/>arrayIndex()<br/>arrayIndexAsync() | RJsonBucketReactive.<br/>arrayIndex() | RJsonBucketRx.<br/>arrayIndex() |
| JSON.ARRINSERT | RJsonBucket.<br/>arrayInsert()<br/>arrayInsertAsync() | RJsonBucketReactive.<br/>arrayInsert() | RJsonBucketRx.<br/>arrayInsert() |
| JSON.ARRLEN | RJsonBucket.<br/>arraySize()<br/>arraySizeAsync() | RJsonBucketReactive.<br/>arraySize() | RJsonBucketRx.<br/>arraySize() |
| JSON.ARRPOP | RJsonBucket.<br/>arrayPollLast()<br/>arrayPollFirst()<br/>arrayPop()<br/>arrayPollLastAsync()<br/>arrayPollFirstAsync()<br/>arrayPopAsync() | RJsonBucketReactive.<br/>arrayPollLast()<br/>arrayPollFirst()<br/>arrayPop() | RJsonBucketRx.<br/>arrayPollLast()<br/>arrayPollFirst()<br/>arrayPop() |
| JSON.ARRTRIM | RJsonBucket.<br/>arrayTrim()<br/>arrayTrimAsync() | RJsonBucketReactive.<br/>arrayTrim() | RJsonBucketRx.<br/>arrayTrim() |
| JSON.CLEAR | RJsonBucket.<br/>clear()<br/>clearAsync() | RJsonBucketReactive.<br/>clear() | RJsonBucketRx.<br/>clear() |
| JSON.DEL | RJsonBucket.<br/>delete()<br/>deleteAsync() | RJsonBucketReactive.<br/>delete() | RJsonBucketRx.<br/>delete() |
| JSON.GET | RJsonBucket.<br/>get()<br/>getAsync() | RJsonBucketReactive.<br/>get() | RJsonBucketRx.<br/>get() |
| JSON.MERGE | RJsonBucket.<br/>merge()<br/>mergeAsync() | RJsonBucketReactive.<br/>merge() | RJsonBucketRx.<br/>merge() |
| JSON.MSET | RJsonBuckets.<br/>set()<br/>setAsync() | RJsonBucketsReactive.<br/>set() | RJsonBucketsRx.<br/>set() |
| JSON.NUMINCRBY | RJsonBucket.<br/>incrementAndGet()<br/>incrementAndGetAsync() | RJsonBucketReactive.<br/>incrementAndGet() | RJsonBucketRx.<br/>incrementAndGet() |
| JSON.OBJKEYS | RJsonBucket.<br/>getKeys()<br/>getKeysAsync() | RJsonBucketReactive.<br/>getKeys() | RJsonBucketRx.<br/>getKeys() |
| JSON.OBJLEN | RJsonBucket.<br/>countKeys()<br/>countKeysAsync() | RJsonBucketReactive.<br/>countKeys() | RJsonBucketRx.<br/>countKeys() |
| JSON.SET | RJsonBucket.<br/>set()<br/>setAsync() | RJsonBucketReactive.<br/>set() | RJsonBucketRx.<br/>set() |
| JSON.STRAPPEND | RJsonBucket.<br/>stringAppend()<br/>stringAppendAsync() | RJsonBucketReactive.<br/>stringAppend() | RJsonBucketRx.<br/>stringAppend() |
| JSON.STRLEN | RJsonBucket.<br/>stringSize()<br/>stringSizeAsync() | RJsonBucketReactive.<br/>stringSize() | RJsonBucketRx.<br/>stringSize() |
| JSON.TOGGLE | RJsonBucket.<br/>toggle()<br/>toggleAsync() | RJsonBucketReactive.<br/>toggle() | RJsonBucketRx.<br/>toggle() |
| JSON.TYPE | RJsonBucket.<br/>type()<br/>typeAsync() | RJsonBucketReactive.<br/>type() | RJsonBucketRx.<br/>type() |

## Search & query

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| FT._LIST | RSearch.<br/>getIndexes()<br/>getIndexesAsync() | RSearchReactive.<br/>getIndexes() | RSearchRx.<br/>getIndexes() |
| FT.AGGREGATE | RSearch.<br/>aggregate()<br/>aggregateAsync() | RSearchReactive.<br/>aggregate() | RSearchRx.<br/>aggregate() |
| FT.ALIASADD | RSearch.<br/>addAlias()<br/>addAliasAsync() | RSearchReactive.<br/>addAlias() | RSearchRx.<br/>addAlias() |
| FT.ALIASDEL | RSearch.<br/>delAlias()<br/>delAliasAsync() | RSearchReactive.<br/>delAlias() | RSearchRx.<br/>delAlias() |
| FT.ALIASUPDATE | RSearch.<br/>updateAlias()<br/>updateAliasAsync() | RSearchReactive.<br/>updateAlias() | RSearchRx.<br/>updateAlias() |
| FT.ALTER | RSearch.<br/>alter()<br/>alterAsync() | RSearchReactive.<br/>alter() | RSearchRx.<br/>alter() |
| FT.CONFIG GET | RSearch.<br/>getConfig()<br/>getConfigAsync() | RSearchReactive.<br/>getConfig() | RSearchRx.<br/>getConfig() |
| FT.CONFIG SET | RSearch.<br/>setConfig()<br/>setConfigAsync() | RSearchReactive.<br/>setConfig() | RSearchRx.<br/>setConfig() |
| FT.CREATE | RSearch.<br/>createIndex()<br/>createIndexAsync() | RSearchReactive.<br/>createIndex() | RSearchRx.<br/>createIndex() |
| FT.CURSOR DEL | RSearch.<br/>delCursor()<br/>delCursorAsync() | RSearchReactive.<br/>delCursor() | RSearchRx.<br/>delCursor() |
| FT.DICTADD | RSearch.<br/>addDict()<br/>addDictAsync() | RSearchReactive.<br/>addDict() | RSearchRx.<br/>addDict() |
| FT.DICTDEL | RSearch.<br/>delDict()<br/>delDictAsync() | RSearchReactive.<br/>delDict() | RSearchRx.<br/>delDict() |
| FT.DICTDUMP | RSearch.<br/>dumpDict()<br/>dumpDictAsync() | RSearchReactive.<br/>dumpDict() | RSearchRx.<br/>dumpDict() |
| FT.DROPINDEX | RSearch.<br/>dropIndex()<br/>dropIndexAndDocuments()<br/>dropIndexAsync()<br/>dropIndexAndDocumentsAsync() | RSearchReactive.<br/>dropIndex()<br/>dropIndexAndDocuments() | RSearchRx.<br/>dropIndex()<br/>dropIndexAndDocuments() |
| FT.HYBRID | RSearch.<br/>hybridSearch()<br/>hybridSearchAsync() | RSearchReactive.<br/>hybridSearch() | RSearchRx.<br/>hybridSearch() |
| FT.INFO | RSearch.<br/>info()<br/>infoAsync() | RSearchReactive.<br/>info() | RSearchRx.<br/>info() |
| FT.SEARCH | RSearch.<br/>search()<br/>searchAsync() | RSearchReactive.<br/>search() | RSearchRx.<br/>search() |
| FT.SPELLCHECK | RSearch.<br/>spellcheck()<br/>spellcheckAsync() | RSearchReactive.<br/>spellcheck() | RSearchRx.<br/>spellcheck() |
| FT.SYNDUMP | RSearch.<br/>dumpSynonyms()<br/>dumpSynonymsAsync() | RSearchReactive.<br/>dumpSynonyms() | RSearchRx.<br/>dumpSynonyms() |
| FT.SYNUPDATE | RSearch.<br/>updateSynonyms()<br/>updateSynonymsAsync() | RSearchReactive.<br/>updateSynonyms() | RSearchRx.<br/>updateSynonyms() |

## Vector sets

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| VADD | RVectorSet.<br/>add()<br/>addAsync() | RVectorSetReactive.<br/>add() | RVectorSetRx.<br/>add() |
| VCARD | RVectorSet.<br/>size()<br/>sizeAsync() | RVectorSetReactive.<br/>size() | RVectorSetRx.<br/>size() |
| VDIM | RVectorSet.<br/>dimensions()<br/>dimensionsAsync() | RVectorSetReactive.<br/>dimensions() | RVectorSetRx.<br/>dimensions() |
| VEMB | RVectorSet.<br/>getVector()<br/>getRawVector()<br/>getVectorAsync()<br/>getRawVectorAsync() | RVectorSetReactive.<br/>getVector()<br/>getRawVector() | RVectorSetRx.<br/>getVector()<br/>getRawVector() |
| VGETATTR | RVectorSet.<br/>getAttributes()<br/>getAttributesAsync() | RVectorSetReactive.<br/>getAttributes() | RVectorSetRx.<br/>getAttributes() |
| VINFO | RVectorSet.<br/>getInfo()<br/>getInfoAsync() | RVectorSetReactive.<br/>getInfo() | RVectorSetRx.<br/>getInfo() |
| VLINKS | RVectorSet.<br/>getNeighbors()<br/>getNeighborEntries()<br/>getNeighborsAsync()<br/>getNeighborEntriesAsync() | RVectorSetReactive.<br/>getNeighbors()<br/>getNeighborEntries() | RVectorSetRx.<br/>getNeighbors()<br/>getNeighborEntries() |
| VRANDMEMBER | RVectorSet.<br/>random()<br/>randomAsync() | RVectorSetReactive.<br/>random() | RVectorSetRx.<br/>random() |
| VREM | RVectorSet.<br/>remove()<br/>removeAsync() | RVectorSetReactive.<br/>remove() | RVectorSetRx.<br/>remove() |
| VSETATTR | RVectorSet.<br/>setAttributes()<br/>setAttributesAsync() | RVectorSetReactive.<br/>setAttributes() | RVectorSetRx.<br/>setAttributes() |
| VSIM | RVectorSet.<br/>getSimilar()<br/>getSimilarEntries()<br/>getSimilarAsync()<br/>getSimilarEntriesAsync() | RVectorSetReactive.<br/>getSimilar()<br/>getSimilarEntries() | RVectorSetRx.<br/>getSimilar()<br/>getSimilarEntries() |

## Pub/Sub

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| PSUBSCRIBE | RPatternTopic.<br/>addListener() | RPatternTopicReactive.<br/>addListener() | RPatternTopicRx.<br/>addListener() |
| PUBLISH | RTopic.<br/>publish() | RTopicReactive.<br/>publish() | RTopicRx.<br/>publish() |
| PUBSUB NUMSUB | RTopic.<br/>countSubscribers()<br/>countSubscribersAsync() | RTopicReactive.<br/>countSubscribers() | RTopicRx.<br/>countSubscribers() |
| PUBSUB SHARDNUMSUB | RShardedTopic.<br/>countSubscribers()<br/>countSubscribersAsync() | RShardedTopicReactive.<br/>countSubscribers() | RShardedTopicRx.<br/>countSubscribers() |
| PUNSUBSCRIBE | RPatternTopic.<br/>removeListener() | RPatternTopicReactive.<br/>removeListener() | RPatternTopicRx.<br/>removeListener() |
| SPUBLISH | RShardedTopic.<br/>publish()<br/>publishAsync() | RShardedTopicReactive.<br/>publish() | RShardedTopicRx.<br/>publish() |
| SSUBSCRIBE | RShardedTopic.<br/>addListener() | RShardedTopicReactive.<br/>addListener() | RShardedTopicRx.<br/>addListener() |
| SUBSCRIBE | RTopic.<br/>addListener() | RTopicReactive.<br/>addListener() | RTopicRx.<br/>addListener() |
| SUNSUBSCRIBE | RShardedTopic.<br/>removeListener()<br/>removeAllListeners() | RShardedTopicReactive.<br/>removeListener() | RShardedTopicRx.<br/>removeListener() |
| UNSUBSCRIBE | RTopic.<br/>removeListener() | RTopicReactive.<br/>removeListener() | RTopicRx.<br/>removeListener() |

## Scripting & functions

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| EVAL | RScript.<br/>eval()<br/>evalAsync() | RScriptReactive.<br/>eval() | RScriptRx.<br/>eval() |
| EVALSHA | RScript.<br/>evalSha()<br/>evalShaAsync() | RScriptReactive.<br/>evalSha() | RScriptRx.<br/>evalSha() |
| FCALL | RFunction.<br/>call()<br/>callAsync() | RFunctionReactive.<br/>call() | RFunctionRx.<br/>call() |
| FUNCTION DELETE | RFunction.<br/>delete()<br/>deleteAsync() | RFunctionReactive.<br/>delete() | RFunctionRx.<br/>delete() |
| FUNCTION DUMP | RFunction.<br/>dump()<br/>dumpAsync() | RFunctionReactive.<br/>dump() | RFunctionRx.<br/>dump() |
| FUNCTION FLUSH | RFunction.<br/>flush()<br/>flushAsync() | RFunctionReactive.<br/>flush() | RFunctionRx.<br/>flush() |
| FUNCTION KILL | RFunction.<br/>kill()<br/>killAsync() | RFunctionReactive.<br/>kill() | RFunctionRx.<br/>kill() |
| FUNCTION LIST | RFunction.<br/>list()<br/>listAsync() | RFunctionReactive.<br/>list() | RFunctionRx.<br/>list() |
| FUNCTION LOAD | RFunction.<br/>load()<br/>loadAndReplace()<br/>loadAsync()<br/>loadAndReplaceAsync() | RFunctionReactive.<br/>load()<br/>loadAndReplace() | RFunctionRx.<br/>load()<br/>loadAndReplace() |
| FUNCTION RESTORE | RFunction.<br/>restore()<br/>restoreAndReplace()<br/>restoreAfterFlush()<br/>restoreAsync() | RFunctionReactive.<br/>restore()<br/>restoreAndReplace()<br/>restoreAfterFlush() | RFunctionRx.<br/>restore()<br/>restoreAndReplace()<br/>restoreAfterFlush() |
| FUNCTION STATS | RFunction.<br/>stats()<br/>statsAsync() | RFunctionReactive.<br/>stats() | RFunctionRx.<br/>stats() |
| SCRIPT EXISTS | RScript.<br/>scriptExists()<br/>scriptExistsAsync() | RScriptReactive.<br/>scriptExists() | RScriptRx.<br/>scriptExists() |
| SCRIPT FLUSH | RScript.<br/>scriptFlush()<br/>scriptFlushAsync() | RScriptReactive.<br/>scriptFlush() | RScriptRx.<br/>scriptFlush() |
| SCRIPT KILL | RScript.<br/>scriptKill()<br/>scriptKillAsync() | RScriptReactive.<br/>scriptKill() | RScriptRx.<br/>scriptKill() |
| SCRIPT LOAD | RScript.<br/>scriptLoad()<br/>scriptLoadAsync() | RScriptReactive.<br/>scriptLoad() | RScriptRx.<br/>scriptLoad() |

## Transactions

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| EXEC | RBatch.<br/>execute()<br/>executeAsync() | RBatchReactive.<br/>execute() | RBatchRx.<br/>execute() |
| MULTI | RBatch.<br/>execute()<br/>executeAsync() | RBatchReactive.<br/>execute() | RBatchRx.<br/>execute() |

## Generic / keys

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| COPY | RObject.<br/>copy()<br/>copyAsync() | RObjectReactive.<br/>copy() | RObjectRx.<br/>copy() |
| DEL | RObject.<br/>delete()<br/>deleteAsync()<br/><br/>RKeys.<br/>delete()<br/>deleteAsync() | RObjectReactive.<br/>delete()<br/><br/>RKeysReactive.<br/>delete() | RObjectRx.<br/>delete()<br/><br/>RKeysRx.<br/>delete() |
| DUMP | RObject.<br/>dump()<br/>dumpAsync() | RObjectReactive.<br/>dump() | RObjectRx.<br/>dump() |
| EXISTS | RObject.<br/>isExists()<br/>isExistsAsync() | RObjectReactive.<br/>isExists() | RObjectRx.<br/>isExists() |
| KEYS | RKeys.<br/>getKeysByPattern()<br/>getKeysByPatternAsync() | RKeysReactive.<br/>getKeysByPattern() | RKeysRx.<br/>getKeysByPattern() |
| MIGRATE | RObject.<br/>migrate()<br/>migrateAsync() | RObjectReactive.<br/>migrate() | RObjectRx.<br/>migrate() |
| MOVE | RObject.<br/>move()<br/>moveAsync() | RObjectReactive.<br/>move() | RObjectRx.<br/>move() |
| PERSIST | RExpirable.<br/>clearExpire()<br/>clearExpireAsync() | RExpirableReactive.<br/>clearExpire() | RExpirableRx.<br/>clearExpire() |
| PEXPIRE | RExpirable.<br/>expire()<br/>expireAsync() | RExpirableReactive.<br/>expire() | RExpirableRx.<br/>expire() |
| PEXPIREAT | RExpirable.<br/>expireAt()<br/>expireAtAsync() | RExpirableReactive.<br/>expireAt() | RExpirableRx.<br/>expireAt() |
| PEXPIRETIME | RExpirable.<br/>expireTime()<br/>expireTimeAsync() | RExpirableReactive.<br/>expireTime() | RExpirableRx.<br/>expireTime() |
| PTTL | RExpirable.<br/>remainTimeToLive()<br/>remainTimeToLiveAsync() | RExpirableReactive.<br/>remainTimeToLive() | RExpirableRx.<br/>remainTimeToLive() |
| RANDOMKEY | RKeys.<br/>randomKey()<br/>randomKeyAsync() | RKeysReactive.<br/>randomKey() | RKeysRx.<br/>randomKey() |
| RENAME | RObject.<br/>rename()<br/>renameAsync() | RObjectReactive.<br/>rename() | RObjectRx.<br/>rename() |
| RENAMENX | RObject.<br/>renamenx()<br/>renamenxAsync() | RObjectReactive.<br/>renamenx() | RObjectRx.<br/>renamenx() |
| RESTORE | RObject.<br/>restore()<br/>restoreAsync() | RObjectReactive.<br/>restore() | RObjectRx.<br/>restore() |
| SCAN | RKeys.<br/>getKeys() | RKeysReactive.<br/>getKeys() | RKeysRx.<br/>getKeys() |
| SORT | RList.<br/>readSort()<br/>sortTo()<br/>readSortAsync()<br/>sortToAsync() | RListReactive.<br/>readSort()<br/>sortTo() | RListRx.<br/>readSort()<br/>sortTo() |
| TOUCH | RObject.<br/>touch()<br/>touchAsync() | RObjectReactive.<br/>touch() | RObjectRx.<br/>touch() |
| TTL | RExpirable.<br/>remainTimeToLive()<br/>remainTimeToLiveAsync() | RExpirableReactive.<br/>remainTimeToLive() | RExpirableRx.<br/>remainTimeToLive() |
| TYPE | RKeys.<br/>getType()<br/>getTypeAsync() | RKeysReactive.<br/>getType() | RKeysRx.<br/>getType() |
| UNLINK | RObject.<br/>unlink()<br/>unlinkAsync() | RObjectReactive.<br/>unlink() | RObjectRx.<br/>unlink() |

## Connection & server

| Valkey or Redis command | Sync / Async API<br/><sub>Redisson.create(config)</sub> | Reactive API<br/><sub>redisson.reactive()</sub> | RxJava3 API<br/><sub>redisson.rxJava()</sub> |
| --- | --- | --- | --- |
| AUTH | Config.<br/>setPassword()<br/>setUsername() | - | - |
| CLIENT REPLY | BatchOptions.skipResult() | - | - |
| CLIENT SETNAME | Config.setClientName() | - | - |
| CLIENT TRACKING | RBucket<br/>.addListener(TrackingListener)<br/>RStream<br/>.addListener(TrackingListener)<br/>RScoredSortedSet<br/>.addListener(TrackingListener)<br/>RSet<br/>.addListener(TrackingListener)<br/>RMap<br/>.addListener(TrackingListener) | RBucketReactive<br/>.addListener(TrackingListener)<br/>RStreamReactive<br/>.addListener(TrackingListener)<br/>RScoredSortedSetReactive<br/>.addListener(TrackingListener)<br/>RSetReactive<br/>.addListener(TrackingListener)<br/>RMapReactive<br/>.addListener(TrackingListener) | RBucketRx<br/>.addListener(TrackingListener)<br/>RStreamRx<br/>.addListener(TrackingListener)<br/>RScoredSortedSetRx<br/>.addListener(TrackingListener)<br/>RSetRx<br/>.addListener(TrackingListener)<br/>RMapRx<br/>.addListener(TrackingListener) |
| CLUSTER INFO | ClusterNode.info() | - | - |
| CLUSTER KEYSLOT | RKeys.<br/>getSlot()<br/>getSlotAsync() | RKeysReactive.<br/>getSlot() | RKeysRx.<br/>getSlot() |
| CLUSTER NODES | Used in ClusterConnectionManager |  |  |
| CONFIG GET | RedisNode.<br/>getConfig()<br/>getConfigAsync() | - | - |
| CONFIG SET | RedisNode.<br/>setConfig()<br/>setConfigAsync() | - | - |
| DBSIZE | RKeys.<br/>count()<br/>countAsync() | RKeysReactive.<br/>count() | RKeysRx.count() |
| FLUSHALL | RKeys.<br/>flushall()<br/>flushallAsync() | RKeysReactive.<br/>flushall() | RKeysRx.<br/>flushall() |
| FLUSHDB | RKeys.<br/>flushdb()<br/>flushdbAsync() | RKeysReactive.<br/>flushdb() | RKeysRx.<br/>flushdb() |
| PING | Node.ping()<br/>NodesGroup.pingAll() | - | - |
| SELECT | Config.setDatabase() | - | - |
| SWAPDB | RKeys.<br/>swapdb()<br/>swapdbAsync() | RKeysReactive.<br/>swapdb() | RKeysRx.<br/>swapdb() |
| TIME | RedissonClient.<br/>getNodesGroup().<br/>getNode().time()<br/>getClusterNodesGroup().<br/>getNode().time() | - | - |
| WAIT | BatchOptions.<br/>sync() | BatchOptions.<br/>sync() | BatchOptions.<br/>sync() |
| WAITAOF | BatchOptions.<br/>syncAOF() | BatchOptions.<br/>syncAOF() | BatchOptions.<br/>syncAOF() |
