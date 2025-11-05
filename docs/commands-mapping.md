Valkey or Redis command|Sync / Async API<br/><sub>Redisson.create(config)</sub>|Reactive API<br/><sub>redisson.reactive()</sub>|RxJava3 API<br/><sub>redisson.rxJava()</sub>|
| --- | --- | --- | --- |
AUTH | Config.<br/>setPassword()<br/>setUsername() | - | - |
APPEND | RBinaryStream.<br/>getOutputStream().write() | - | - |
BZPOPMAX|RScoredSortedSet.<br/>pollLast()<br/>pollLastAsync() | RScoredSortedSetReactive.<br/>pollLast()| RScoredSortedSetRx.<br/>pollLast() |
BZPOPMIN|RScoredSortedSet.<br/>pollFirst()<br/>pollFirstAsync() | RScoredSortedSetReactive.<br/>pollFirst() | RScoredSortedSetRx.<br/>pollFirst() |
BZMPOP|RScoredSortedSet.<br/>pollLast()<br/>pollLastAsync()<br/>pollLastFromAny()<br/>pollLastEntriesFromAny() | RScoredSortedSetReactive.<br/>pollLast()<br/>pollLastFromAny()<br/>pollLastEntriesFromAny()| RScoredSortedSetRx.<br/>pollLast()<br/>pollLastFromAny()<br/>pollLastEntriesFromAny() |
BITCOUNT|RBitSet.<br/>cardinality()<br/>cardinalityAsync() | RBitSetReactive.<br/>cardinality() | RBitSetRx.<br/>cardinality() |
BITOP|RBitSet.<br/>or()<br/>and()<br/>xor()<br/>orAsync()<br/>andAsync()<br/>xorAsync() | RBitSetReactive.<br/>or()<br/>and()<br/>xor() | RBitSetRx.<br/>or()<br/>and()<br/>xor() |
BITPOS|RBitSet.<br/>length()<br/>lengthAsync() | RBitSetReactive.<br/>length() | RBitSetRx.<br/>length() |
BITFIELD|RBitSet.<br/>getByte()<br/>setByte()<br/>incrementAndGetByte()<br/><br/>getShort()<br/>setShort()<br/>incrementAndGetShort()<br/><br/>getInteger()<br/>setInteger()<br/>incrementAndGetInteger()<br/><br/>getLong()<br/>setLong()<br/>incrementAndGetLong() | RBitSetReactive.<br/>getByte()<br/>setByte()<br/>incrementAndGetByte()<br/><br/>getShort()<br/>setShort()<br/>incrementAndGetShort()<br/><br/>getInteger()<br/>setInteger()<br/>incrementAndGetInteger()<br/><br/>getLong()<br/>setLong()<br/>incrementAndGetLong() | RBitSetRx.<br/>getByte()<br/>setByte()<br/>incrementAndGetByte()<br/><br/>getShort()<br/>setShort()<br/>incrementAndGetShort()<br/><br/>getInteger()<br/>setInteger()<br/>incrementAndGetInteger()<br/><br/>getLong()<br/>setLong()<br/>incrementAndGetLong() |
BLMPOP|RBlockingQueue.<br/>pollLastFromAny()<br/>pollFirstFromAny()<br/>pollLastFromAnyAsync()<br/>pollFirstFromAnyAsync() | RBlockingQueueReactive.<br/>pollLastFromAny()<br/>pollFirstFromAny() |RBlockingQueueRx.<br/>pollLastFromAny()<br/>pollFirstFromAny() |
BLPOP|RBlockingQueue.<br/>take()<br/>poll()<br/>pollFromAny()<br/>takeAsync()<br/>pollAsync()<br/>pollFromAnyAsync() | RBlockingQueueReactive.<br/>take()<br/>poll()<br/>pollFromAny() |RBlockingQueueRx.<br/>take()<br/>poll()<br/>pollFromAny() |
BLMOVE|RBlockingDeque.<br/>move()<br/>moveAsync()| RBlockingDequeReactive.<br/>move()|RBlockingDequeRx.<br/>move()|
BRPOP|RBlockingDeque.<br/>takeLast()<br/>takeLastAsync() | RBlockingDequeReactive.<br/>takeLast() | RBlockingDequeRx.<br/>takeLast() |
BRPOPLPUSH|RBlockingQueue.<br/>pollLastAndOfferFirstTo()<br/>pollLastAndOfferFirstToAsync() | RBlockingQueueReactive.<br/>pollLastAndOfferFirstTo()| RBlockingQueueRx.<br/>pollLastAndOfferFirstTo()|
CONFIG GET|RedisNode.<br/>getConfig()<br/>getConfigAsync() | - | - |
CONFIG SET|RedisNode.<br/>setConfig()<br/>setConfigAsync() | - | - |
COPY|RObject.<br/>copy()<br/>copyAsync() | RObjectReactive.<br/>copy() | RObjectRx.<br/>copy() |
CLIENT SETNAME|Config.setClientName() | - | - |
CLIENT REPLY|BatchOptions.skipResult() | - | - |
CLIENT TRACKING|RBucket<br/>.addListener(TrackingListener)<br/>RStream<br/>.addListener(TrackingListener)<br/>RScoredSortedSet<br/>.addListener(TrackingListener)<br/>RSet<br/>.addListener(TrackingListener)<br/>RMap<br/>.addListener(TrackingListener) | RBucketReactive<br/>.addListener(TrackingListener)<br/>RStreamReactive<br/>.addListener(TrackingListener)<br/>RScoredSortedSetReactive<br/>.addListener(TrackingListener)<br/>RSetReactive<br/>.addListener(TrackingListener)<br/>RMapReactive<br/>.addListener(TrackingListener) | RBucketRx<br/>.addListener(TrackingListener)<br/>RStreamRx<br/>.addListener(TrackingListener)<br/>RScoredSortedSetRx<br/>.addListener(TrackingListener)<br/>RSetRx<br/>.addListener(TrackingListener)<br/>RMapRx<br/>.addListener(TrackingListener) |
CLUSTER INFO| ClusterNode.info() | - | - |
CLUSTER KEYSLOT|RKeys.<br/>getSlot()<br/>getSlotAsync() | RKeysReactive.<br/>getSlot() | RKeysRx.<br/>getSlot() |
CLUSTER NODES|Used in ClusterConnectionManager |
DECRBY|RAtomicLong.<br/>addAndGet()<br/>addAndGetAsync() | RAtomicLongReactive.<br/>addAndGet() |RAtomicLongRx.<br/>addAndGet() |
DUMP|RObject.<br/>dump()<br/>dumpAsync()| RObjectReactive.<br/>dump()| RObjectRx.<br/>dump()|
DBSIZE|RKeys.<br/>count()<br/>countAsync()| RKeysReactive.<br/>count()| RKeysRx.count()|
DECR|RAtomicLong.<br/>decrementAndGet()<br/>decrementAndGetAsync()| RAtomicLongReactive.<br/>decrementAndGet()| RAtomicLongRx.<br/>decrementAndGet()|
DEL|RObject.<br/>delete()<br/>deleteAsync()<br/><br/>RKeys.<br/>delete()<br/>deleteAsync()| RObjectReactive.<br/>delete()<br/><br/>RKeysReactive.<br/>delete()|RObjectRx.<br/>delete()<br/><br/>RKeysRx.<br/>delete()|
STRLEN|RBucket.<br/>size()<br/>sizeAsync()| RBucketReactive.<br/>size()| RBucketRx.<br/>size()|
EVAL|RScript.<br/>eval()<br/>evalAsync()| RScriptReactive.<br/>eval()| RScriptRx.<br/>eval()|
EVALSHA|RScript.<br/>evalSha()<br/>evalShaAsync()| RScriptReactive.<br/>evalSha()| RScriptRx.<br/>evalSha()|
EXEC|RBatch.<br/>execute()<br/>executeAsync()| RBatchReactive.<br/>execute()| RBatchRx.<br/>execute()|
EXISTS|RObject.<br/>isExists()<br/>isExistsAsync()| RObjectReactive.<br/>isExists()| RObjectRx.<br/>isExists()|
FLUSHALL|RKeys.<br/>flushall()<br/>flushallAsync()| RKeysReactive.<br/>flushall()| RKeysRx.<br/>flushall()|
FLUSHDB|RKeys.<br/>flushdb()<br/>flushdbAsync()| RKeysReactive.<br/>flushdb()| RKeysRx.<br/>flushdb()|
GETRANGE|RBinaryStream.<br/>getChannel().read()|RBinaryStreamReactive.<br/>read()|RBinaryStreamRx.<br/>read()|
GEOADD|RGeo.<br/>add()<br/>addAsync()| RGeoReactive.<br/>add()| RGeoRx.<br/>add()|
GEODIST|RGeo.<br/>dist()<br/>distAsync()| RGeoReactive.<br/>dist()| RGeoRx.<br/>dist()|
GEOHASH|RGeo.<br/>hash()<br/>hashAsync()| RGeoReactive.<br/>hash()| RGeoRx.<br/>hash()|
GEOPOS|RGeo.<br/>pos()<br/>posAsync()| RGeoReactive.<br/>pos()| RGeoRx.<br/>pos()|
GEORADIUS|RGeo.<br/>radius()<br/>radiusAsync()<br/>radiusWithDistance()<br/>radiusWithDistanceAsync()<br/>radiusWithPosition()<br/>radiusWithPositionAsync()|RGeoReactive.<br/>radius()<br/>radiusWithDistance()<br/>radiusWithPosition()|RGeoRx.<br/>radius()<br/>radiusWithDistance()<br/>radiusWithPosition()|
GEORADIUSBYMEMBER | RGeo.<br/>radiusWithDistance()<br/>radiusWithDistanceAsync() | RGeoReactive.<br/>radiusWithDistance() | RGeoRx.<br/>radiusWithDistance()|
GEOSEARCH|RGeo.<br/>search()<br/>searchAsync()| RGeoReactive.<br/>search()| RGeoRx.<br/>search()|
GEOSEARCHSTORE|RGeo.<br/>storeSearchTo()<br/>storeSearchToAsync()| RGeoReactive.<br/>storeSearchTo()| RGeoRx.<br/>storeSearchTo()|
GET|RBucket.<br/>get()<br/>getAsync()<br/><br/>RBinaryStream.<br/>get()<br/>getAsync()| RBucketReactive.<br/>get()<br/><br/>RBinaryStreamReactive.<br/>get()| RBucketRx.<br/>get()<br/><br/>RBinaryStreamRx.<br/>get()|
GETEX|RBucket.<br/>getAndExpire()<br/>getAndExpireAsync()<br/>getAndClearExpire()<br/>getAndClearExpireAsync()| RBucketReactive.<br/>getAndExpire()<br/>getAndClearExpire()| RBucketRx.<br/>getAndExpire()<br/>getAndClearExpire()|
GETBIT|RBitSet.<br/>get()<br/>getAsync()| RBitSetReactive.<br/>get()| RBitSetRx.<br/>get() |
GETSET|RBucket.<br/>getAndSet()<br/>getAndSetAsync()<br/><br/>RAtomicLong.<br/>getAndSet()<br/>getAndSetAsync()<br/><br/>RAtomicDouble.<br/>getAndSet()<br/>getAndSetAsync()|RBucketReactive.<br/>getAndSet()<br/><br/>RAtomicLongReactive.<br/>getAndSet()<br/><br/>RAtomicDoubleReactive.<br/>getAndSet()|RBucketRx.<br/>getAndSet()<br/><br/>RAtomicLongRx.<br/>getAndSet()<br/><br/>RAtomicDoubleRx.<br/>getAndSet() |
LCS|RBucket.<br/>findCommon()<br/>findCommonAsync()<br/>findCommonLength()<br/>findCommonLengthAsync() | RBucketReactive.<br/>findCommon()<br/>findCommonLength() | RBucketRx.<br/>findCommon()<br/>findCommonLength() |
HDEL|RMap.<br/>fastRemove()<br/>fastRemoveAsync()| RMapReactive.<br/>fastRemove()| RMapRx.<br/>fastRemove()|
HEXISTS|RMap.<br/>containsKey()<br/>containsKeyAsync()| RMapReactive.<br/>containsKey()| RMapRx.<br/>containsKey()|
HGET|RMap.<br/>get()<br/>getAsync()|RMapReactive.<br/>get()|RMapRx.<br/>get()|
HSTRLEN|RMap.<br/>valueSize()<br/>valueSizeAsync()|RMapReactive.<br/>valueSize()| RMapRx.<br/>valueSize()|
HGETALL|RMap.<br/>readAllEntrySet()<br/>readAllEntrySetAsync()|RMapReactive.<br/>readAllEntrySet()| RMapRx.<br/>readAllEntrySet()|
HINCRBY|RMap.<br/>addAndGet()<br/>addAndGetAsync()| RMapReactive.<br/>addAndGet()| RMapRx.<br/>addAndGet()|
HINCRBYFLOAT|RMap.<br/>addAndGet()<br/>addAndGetAsync()| RMapReactive.<br/>addAndGet()| RMapRx.<br/>addAndGet()|
HKEYS|RMap.<br/>readAllKeySet()<br/>readAllKeySetAsync()| RMapReactive.<br/>readAllKeySet()| RMapRx.<br/>readAllKeySet()|
HLEN|RMap.<br/>size()<br/>sizeAsync()| RMapReactive.<br/>size()| RMapRx.<br/>size()|
HMGET|RMap.<br/>getAll()<br/>getAllAsync()| RMapReactive.<br/>getAll()| RMapRx.<br/>getAll()|
HMSET|RMap.<br/>putAll()<br/>putAllAsync()| RMapReactive.<br/>putAll()| RMapRx.<br/>putAll()|
HRANDFIELD|RMap.<br/>putAll()<br/>putAllAsync()| RMapReactive.<br/>putAll()| RMapRx.<br/>putAll()|
HSCAN|RMap.<br/>keySet().iterator()<br/>values().iterator()<br/>entrySet().iterator()|RMapReactive.<br/>keyIterator()<br/>valueIterator()<br/>entryIterator()|RMapRx.<br/>keyIterator()<br/>valueIterator()<br/>entryIterator()|
HSET|RMap.<br/>randomEntries()<br/>randomKeys()<br/>randomEntriesAsync()<br/>randomKeysAsync()| RMapReactive.<br/>randomEntries()<br/>randomKeys()| RMapRx.<br/>randomEntries()<br/>randomKeys()|
HSETNX|RMap.<br/>fastPutIfAbsent()<br/>fastPutIfAbsentAsync()| RMapReactive.<br/>fastPutIfAbsent()| RMapRx.<br/>fastPutIfAbsent()|
HVALS|RMap.<br/>readAllValues()<br/>readAllValuesAsync()| RMapReactive.<br/>readAllValues()| RMapRx.<br/>readAllValues()|
HPEXPIRE|RMapCacheNative.<br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()<br/>putAsync()<br/>fastPutAsync()<br/>putIfAbsentAsync()<br/>fastPutIfAbsentAsync()<br/>putAllAsync()<br/>expireEntryAsync()| RMapCacheNativeReactive.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()| RMapCacheNativeRx.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()|
HPEXPIREAT|RMapCacheNative.<br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()<br/>putAsync()<br/>fastPutAsync()<br/>putIfAbsentAsync()<br/>fastPutIfAbsentAsync()<br/>putAllAsync()<br/>expireEntryAsync()| RMapCacheNativeReactive.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()| RMapCacheNativeRx.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()|
HEXPIRE|RMapCacheNative.<br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()<br/>putAsync()<br/>fastPutAsync()<br/>putIfAbsentAsync()<br/>fastPutIfAbsentAsync()<br/>putAllAsync()<br/>expireEntryAsync()| RMapCacheNativeReactive.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()| RMapCacheNativeRx.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()|
HEXPIREAT|RMapCacheNative.<br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()<br/>putAsync()<br/>fastPutAsync()<br/>putIfAbsentAsync()<br/>fastPutIfAbsentAsync()<br/>putAllAsync()<br/>expireEntryAsync()| RMapCacheNativeReactive.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()| RMapCacheNativeRx.<br/><br/>put()<br/>fastPut()<br/>putIfAbsent()<br/>fastPutIfAbsent()<br/>putAll()<br/>expireEntry()|
HPERSIST|RMapCacheNative.<br/>clearExpire()<br/>clearExpireAsync()| RMapCacheNativeReactive.<br/>clearExpire()| RMapCacheNativeRx.<br/>clearExpire()|
HPTTL|RMapCacheNative.<br/>remainTimeToLive()<br/>remainTimeToLiveAsync()| RMapCacheNativeReactive.<br/>remainTimeToLive()| RMapCacheNativeRx.<br/>remainTimeToLive()|
INCR|RAtomicLong.<br/>incrementAndGet()<br/>incrementAndGetAsync()| RAtomicLongReactive.<br/>incrementAndGet()| RAtomicLongRx.<br/>incrementAndGet()|
INCRBY|RAtomicLong.<br/>addAndGet()<br/>addAndGetAsync()| RAtomicLongReactive.<br/>addAndGet()| RAtomicLongRx.<br/>addAndGet()|
INCRBYFLOAT|RAtomicDouble.<br/>addAndGet()<br/>addAndGetAsync()| RAtomicDoubleReactive.<br/>addAndGet()| RAtomicDoubleRx.<br/>addAndGet()|
JSON.ARRAPPEND|RJsonBucket.<br/>arrayAppend()<br/>arrayAppendAsync()| RJsonBucketReactive.<br/>arrayAppend()| RJsonBucketRx.<br/>arrayAppend()|
JSON.ARRINDEX|RJsonBucket.<br/>arrayIndex()<br/>arrayIndexAsync()| RJsonBucketReactive.<br/>arrayIndex()| RJsonBucketRx.<br/>arrayIndex()|
JSON.ARRINSERT|RJsonBucket.<br/>arrayInsert()<br/>arrayInsertAsync()| RJsonBucketReactive.<br/>arrayInsert()| RJsonBucketRx.<br/>arrayInsert()|
JSON.ARRLEN|RJsonBucket.<br/>arraySize()<br/>arraySizeAsync()| RJsonBucketReactive.<br/>arraySize()| RJsonBucketRx.<br/>arraySize()|
JSON.ARRPOP|RJsonBucket.<br/>arrayPollLast()<br/>arrayPollFirst()<br/>arrayPop()<br/>arrayPollLastAsync()<br/>arrayPollFirstAsync()<br/>arrayPopAsync()| RJsonBucketReactive.<br/>arrayPollLast()<br/>arrayPollFirst()<br/>arrayPop()| RJsonBucketRx.<br/>arrayPollLast()<br/>arrayPollFirst()<br/>arrayPop()|
JSON.ARRTRIM|RJsonBucket.<br/>arrayTrim()<br/>arrayTrimAsync()| RJsonBucketReactive.<br/>arrayTrim()| RJsonBucketRx.<br/>arrayTrim()|
JSON.CLEAR|RJsonBucket.<br/>clear()<br/>clearAsync()| RJsonBucketReactive.<br/>clear()| RJsonBucketRx.<br/>clear()|
JSON.GET|RJsonBucket.<br/>get()<br/>getAsync()| RJsonBucketReactive.<br/>get()| RJsonBucketRx.<br/>get()|
JSON.MERGE|RJsonBucket.<br/>merge()<br/>mergeAsync()| RJsonBucketReactive.<br/>merge()| RJsonBucketRx.<br/>merge()|
JSON.NUMINCRBY|RJsonBucket.<br/>incrementAndGet()<br/>incrementAndGetAsync()| RJsonBucketReactive.<br/>incrementAndGet()| RJsonBucketRx.<br/>incrementAndGet()|
JSON.OBJLEN|RJsonBucket.<br/>countKeys()<br/>countKeysAsync()| RJsonBucketReactive.<br/>countKeys()| RJsonBucketRx.<br/>countKeys()|
JSON.OBJKEYS|RJsonBucket.<br/>getKeys()<br/>getKeysAsync()| RJsonBucketReactive.<br/>getKeys()| RJsonBucketRx.<br/>getKeys()|
JSON.SET|RJsonBucket.<br/>set()<br/>setAsync()| RJsonBucketReactive.<br/>set()| RJsonBucketRx.<br/>set()|
JSON.STRAPPEND|RJsonBucket.<br/>stringAppend()<br/>stringAppendAsync()| RJsonBucketReactive.<br/>stringAppend()| RJsonBucketRx.<br/>stringAppend()|
JSON.STRLEN|RJsonBucket.<br/>stringSize()<br/>stringSizeAsync()| RJsonBucketReactive.<br/>stringSize()| RJsonBucketRx.<br/>stringSize()|
JSON.TOGGLE|RJsonBucket.<br/>toggle()<br/>toggleAsync()| RJsonBucketReactive.<br/>toggle()| RJsonBucketRx.<br/>toggle()|
JSON.TYPE|RJsonBucket.<br/>type()<br/>typeAsync()| RJsonBucketReactive.<br/>type()| RJsonBucketRx.<br/>type()|
KEYS|RKeys.<br/>getKeysByPattern()<br/>getKeysByPatternAsync()| RKeysReactive.<br/>getKeysByPattern()| RKeysRx.<br/>getKeysByPattern()|
LINDEX|RList.<br/>get()<br/>getAsync()| RListReactive.<br/>get()|RListRx.<br/>get()|
LLEN|RList.<br/>size()<br/>sizeAsync()| RListReactive.<br/>size()|RListRx.<br/>size()|
LMOVE|RDeque.<br/>move()<br/>moveAsync()| RDequeReactive.<br/>move()|RDequeRx.<br/>move()|
LPOP|RQueue.<br/>poll()<br/>pollAsync()| RQueueReactive.<br/>poll()|RQueueRx.<br/>poll()|
LPUSH|RDeque.<br/>addFirst()<br/>addFirstAsync()|RDequeReactive.<br/>addFirst()||RDequeRx.<br/>addFirst()|
LRANGE|RList.<br/>readAll()<br/>readAllAsync()|RListReactive.readAll()|RListRx.readAll()|
LPUSHX|RDeque.<br/>addFirstIfExists()<br/>addFirstIfExistsAsync()|RDequeReactive.<br/>addFirstIfExists()|RDequeRx.<br/>addFirstIfExists()|
LREM|RList.<br/>fastRemove()<br/>fastRemoveAsync()|RListReactive.<br/>fastRemove()|RListRx.<br/>fastRemove()|
LSET|RList.<br/>fastSet()<br/>fastSetAsync()|RListReactive.<br/>fastSet()|RListRx.<br/>fastSet()|
LTRIM|RList.<br/>trim()<br/>trimAsync()|RListReactive.<br/>trim()|RListRx.<br/>trim()|
LINSERT|RList.<br/>addBefore()<br/>addAfter()<br/>addBeforeAsync()<br/>addAfterAsync()|RListReactive.<br/>addBefore()<br/>addAfter()|RListRx.<br/>addBefore()<br/>addAfter()|
MULTI|RBatch.<br/>execute()<br/>executeAsync()|RBatchReactive.<br/>execute()|RBatchRx.<br/>execute()|
MGET|RBuckets.<br/>get()<br/>getAsync()|RBucketsReactive.<br/>get()|RBucketsRx.<br/>get()|
MSETNX|RBuckets.<br/>trySet()<br/>trySetAsync()|RBucketsReactive.<br/>trySet()|RBucketsRx.<br/>trySet()|
MIGRATE|RObject.<br/>migrate()<br/>migrateAsync()|RObjectReactive.<br/>migrate()|RObjectRx.<br/>migrate()|
MOVE|RObject.<br/>move()<br/>moveAsync()|RObjectReactive.<br/>move()|RObjectRx.<br/>move()|
MSET|RBuckets.<br/>set()<br/>setAsync()|RBucketsReactive.<br/>set()|RBucketsRx.<br/>set()|
PERSIST|RExpirable.<br/>clearExpire()<br/>clearExpireAsync()|RExpirableReactive.<br/>clearExpire()|RExpirableRx.<br/>clearExpire()|
PEXPIRE|RExpirable.<br/>expire()<br/>expireAsync()|RExpirableReactive.<br/>expire()|RExpirableRx.<br/>expire()|
PEXPIREAT|RExpirable.<br/>expireAt()<br/>expireAtAsync()|RExpirableReactive.<br/>expireAt()|RExpirableRx.<br/>expireAt()|
PEXPIRETIME|RExpirable.<br/>expireTime()<br/>expireTimeAsync()|RExpirableReactive.<br/>expireTime()|RExpirableRx.<br/>expireTime()|
PFADD|RHyperLogLog.<br/>add()<br/>addAsync()<br/>addAll()<br/>addAllAsync()|RHyperLogLogReactive.<br/>add()<br/><br/>addAll()|RHyperLogLogRx.<br/>add()<br/><br/>addAll()|
PFCOUNT|RHyperLogLog.<br/>count()<br/>countAsync()<br/>countWith()<br/>countWithAsync()|RHyperLogLogReactive.<br/>count()<br/>countWith()|RHyperLogLogRx.<br/>count()<br/>countWith()|
PFMERGE|RHyperLogLog.<br/>mergeWith()<br/>mergeWithAsync()|RHyperLogLogReactive.<br/>mergeWith()|RHyperLogLogRx.<br/>mergeWith()|
PING|Node.ping()<br/>NodesGroup.pingAll()| - | - |
PSUBSCRIBE|RPatternTopic.<br/>addListener()|RPatternTopicReactive.<br/>addListener()|RPatternTopicRx.<br/>addListener()|
PSETEX|RBucket.<br/>set()<br/>setAsync()|RBucketReactive.<br/>set()|RBucketRx.<br/>set()|
PTTL|RExpirable.<br/>remainTimeToLive()<br/>remainTimeToLiveAsync()|RExpirableReactive.<br/>remainTimeToLive()|RExpirableRx.<br/>remainTimeToLive()|
PUBLISH|RTopic.<br/>publish()|RTopicReactive.<br/>publish()|RTopicRx.<br/>publish()|
PUBSUB NUMSUB|RTopic.<br/>countSubscribers()<br/>countSubscribersAsync()|RTopicReactive.<br/>countSubscribers()|RTopicRx.<br/>countSubscribers()|
PUNSUBSCRIBE|RPatternTopic.<br/>removeListener()|RPatternTopicReactive.<br/>removeListener()|RPatternTopicRx.<br/>removeListener()|
RANDOMKEY|RKeys.<br/>randomKey()<br/>randomKeyAsync()|RKeysReactive.<br/>randomKey()|RKeysRx.<br/>randomKey()|
RESTORE|RObject.<br/>restore()<br/>restoreAsync()|RObjectReactive.<br/>restore()|RObjectRx.<br/>restore()|
RENAME|RObject.<br/>rename()<br/>renameAsync()|RObjectReactive.<br/>rename()|RObjectRx.<br/>rename()|
RPOP|RDeque.<br/>pollLast()<br/>removeLast()<br/>pollLastAsync()<br/>removeLastAsync()|RDequeReactive.<br/>pollLast()<br/>removeLast()|RDequeRx.<br/>pollLast()<br/>removeLast()|
RPOPLPUSH|RDeque.<br/>pollLastAndOfferFirstTo()<br/>pollLastAndOfferFirstToAsync()|RDequeReactive.<br/>pollLastAndOfferFirstTo()|RDequeRx.<br/>pollLastAndOfferFirstTo()|
RPUSH|RList.<br/>add()<br/>addAsync()|RListReactive.<br/>add()|RListRx.<br/>add()|
RPUSHX|RDeque.<br/>addLastIfExists()<br/>addLastIfExistsAsync()|RListReactive.<br/>addLastIfExists()|RListRx.<br/>addLastIfExists()|
SADD|RSet.<br/>add()<br/>addAsync()|RSetReactive.<br/>add()|RSetRx.<br/>add()|
SETRANGE|RBinaryStream.<br/>getChannel().write()|RBinaryStreamReactive.<br/>write()|RBinaryStreamRx.<br/>write()|
SCAN|RKeys.<br/>getKeys()|RKeysReactive.<br/>getKeys()|RKeysRx.<br/>getKeys()|
SCARD|RSet.<br/>size()<br/>sizeAsync()|RSetReactive.<br/>size()|RSetRx.<br/>size()|
SCRIPT EXISTS|RScript.<br/>scriptExists()<br/>scriptExistsAsync()|RScriptReactive.<br/>scriptExists()|RScriptRx.<br/>scriptExists()|
SCRIPT FLUSH|RScript.<br/>scriptFlush()<br/>scriptFlushAsync()|RScriptReactive.<br/>scriptFlush()|RScriptRx.<br/>scriptFlush()|
SCRIPT KILL|RScript.<br/>scriptKill()<br/>scriptKillAsync()|RScriptReactive.<br/>scriptKill()|RScriptRx.<br/>scriptKill()|
SCRIPT LOAD|RScript.<br/>scriptLoad()<br/>scriptLoadAsync()|RScriptReactive.<br/>scriptLoad()|RScriptRx.<br/>scriptLoad()|
SDIFFSTORE|RSet.<br/>diff()<br/>diffAsync()|RSetReactive.<br/>diff()|RSetRx.<br/>diff()|
SDIFF|RSet.<br/>readDiff()<br/>readDiffAsync()|RSetReactive.<br/>readDiff()|RSetRx.<br/>readDiff()|
SRANDMEMBER|RSet.<br/>random()<br/>randomAsync()|RSetReactive.<br/>random()|RSetRx.<br/>random()|
SELECT|Config.setDatabase()| - | - |
SET|RBucket.<br/>set()<br/>setAsync()|RBucketReactive.<br/>set()|RBucketRx.<br/>set()|
SETBIT|RBitSet.<br/>set()<br/>clear()<br/>setAsync()<br/>clearAsync()|RBitSetReactive.<br/>set()<br/>clear()|RBitSetRx.<br/>set()<br/>clear()|
SETEX|RBucket.<br/>set()<br/>setAsync()|RBucketReactive.<br/>set()|RBucketRx.<br/>set()|
SETNX|RBucket.<br/>setIfAbsent()<br/>setIfAbsentAsync()|RBucketReactive.<br/>setIfAbsent()|RBucketRx.<br/>setIfAbsent()|
SISMEMBER|RSet.<br/>contains()<br/>containsAsync()|RSetReactive.<br/>contains()|RSetRx.<br/>contains()|
SMISMEMBER|RSet.<br/>containsEach()<br/>containsEachAsync()|RSetReactive.<br/>containsEach()|RSetRx.<br/>containsEach()|
SINTERSTORE|RSet.<br/>intersection()<br/>intersectionAsync()|RSetReactive.<br/>intersection()|RSetRx.<br/>intersection()|
SINTER|RSet.<br/>readIntersection()<br/>readIntersectionAsync()|RSetReactive.<br/>readIntersection()|RSetRx.<br/>readIntersection()|
SMEMBERS|RSet.<br/>readAll()<br/>readAllAsync()|RSetReactive.<br/>readAll()|RSetRx.<br/>readAll()|
SMOVE|RSet.<br/>move()<br/>moveAsync()|RSetReactive.<br/>move()|RSetRx.<br/>move()|
SORT|RList.<br/>readSort()<br/>sortTo()<br/>readSortAsync()<br/>sortToAsync()|RListReactive.<br/>readSort()<br/>sortTo()|RListRx.<br/>readSort()<br/>sortTo()|
SPOP|RSet.<br/>removeRandom()<br/>removeRandomAsync()|RSetReactive.<br/>removeRandom()|RSetRx.<br/>removeRandom()|
SREM|RSet.<br/>remove()<br/>removeAsync()|RSetReactive.<br/>remove()|RSetRx.<br/>remove()|
SSCAN|RSet.<br/>iterator()|RSetReactive.<br/>iterator()|RSetRx.<br/>iterator()|
SUBSCRIBE|RTopic.<br/>addListener()|RTopicReactive.<br/>addListener()|RTopicRx.<br/>addListener()|
SUNION|RSet.<br/>readUnion()<br/>readUnionAsync()|RSetReactive.<br/>readUnion()|RSetRx.<br/>readUnion()|
SUNIONSTORE|RSet.<br/>union()<br/>unionAsync()|RSetReactive.<br/>union()|RSetRx.<br/>union()|
SWAPDB|RKeys.<br/>swapdb()<br/>swapdbAsync()|RKeysReactive.<br/>swapdb()|RKeysRx.<br/>swapdb()|
TTL|RExpirable.<br/>remainTimeToLive()<br/>remainTimeToLiveAsync()|RExpirableReactive.<br/>remainTimeToLive()|RExpirableRx.<br/>remainTimeToLive()|
TYPE|RKeys.<br/>getType()<br/>getTypeAsync()|RKeysReactive.<br/>getType()|RKeysRx.<br/>getType()|
TOUCH|RObject.<br/>touch()<br/>touchAsync()|RObjectReactive.<br/>touch()|RObjectRx.<br/>touch()|
UNSUBSCRIBE|RTopic.<br/>removeListener()|RTopicReactive.<br/>removeListener()|RTopicRx.<br/>removeListener()|
UNLINK|RObject.<br/>unlink()<br/>unlinkAsync()|RObjectReactive.<br/>unlink()|RObjectRx.<br/>unlink()|
WAIT|BatchOptions.<br/>sync()|BatchOptions.<br/>sync()|BatchOptions.<br/>sync()|
WAITAOF|BatchOptions.<br/>syncAOF()|BatchOptions.<br/>syncAOF()|BatchOptions.<br/>syncAOF()|
ZADD|RScoredSortedSet.<br/>add()<br/>addAsync()<br/>addAll()<br/>addAllAsync()|RScoredSortedSetReactive.<br/>add()<br/>addAll()|RScoredSortedSetRx.<br/>add()<br/>addAll()|
ZCARD|RScoredSortedSet.<br/>size()<br/>sizeAsync()|RScoredSortedSetReactive.<br/>size()|RScoredSortedSetRx.<br/>size()|
ZCOUNT|RScoredSortedSet.<br/>count()<br/>countAsync()|RScoredSortedSetReactive.<br/>count()|RScoredSortedSetRx.<br/>count()|
ZDIFF|RScoredSortedSet.<br/>readDiff()<br/>readDiffAsync()|RScoredSortedSetReactive.<br/>readDiff()|RScoredSortedSetRx.<br/>readDiff()|
ZDIFFSTORE|RScoredSortedSet.<br/>diff()<br/>diffAsync()|RScoredSortedSetReactive.<br/>diff()|RScoredSortedSetRx.<br/>diff()|
ZINCRBY|RScoredSortedSet.<br/>addScore()<br/>addScoreAsync()|RScoredSortedSetReactive.<br/>addScore()|RScoredSortedSetRx.<br/>addScore()|
ZINTER|RScoredSortedSet.<br/>readIntersection()<br/>readIntersectionAsync()|RScoredSortedSetReactive.<br/>readIntersection()|RScoredSortedSetRx.<br/>readIntersection()|
ZREMRANGEBYRANK|RScoredSortedSet.<br/>removeRangeByRank()<br/>removeRangeByRankAsync()|RScoredSortedSetReactive.<br/>removeRangeByRank()|RScoredSortedSetRx.<br/>removeRangeByRank()|
ZREVRANGEBYLEX|RLexSortedSet.<br/>rangeReversed()<br/>rangeReversedAsync()|RLexSortedSetReactive.<br/>rangeReversed()|RLexSortedSetSetRx.<br/>rangeReversed()|
ZLEXCOUNT|RLexSortedSet.<br/>lexCount()<br/>lexCountHead()<br/>lexCountTail()<br/>lexCountAsync()<br/>lexCountHeadAsync()<br/>lexCountTailAsync()|RLexSortedSetReactive.<br/>lexCount()<br/>lexCountHead()<br/>lexCountTail()|RLexSortedSetRx.<br/>lexCount()<br/>lexCountHead()<br/>lexCountTail()|
ZRANGE|RScoredSortedSet.<br/>valueRange()<br/>valueRangeAsync()|RScoredSortedSetReactive.<br/>valueRange()|RScoredSortedSetRx.<br/>valueRange()|
ZRANDMEMBER|RScoredSortedSet.<br/>random()<br/>randomAsync()|RScoredSortedSetReactive.<br/>random()|RScoredSortedSetRx.<br/>random()|
ZREVRANGE|RScoredSortedSet.<br/>valueRangeReversed()<br/>valueRangeReversedAsync()|RScoredSortedSetReactive.<br/>valueRangeReversed()|RScoredSortedSetRx.<br/>valueRangeReversed()|
ZUNION|RScoredSortedSet.<br/>readUnion()<br/>readUnionAsync()|RScoredSortedSetReactive.<br/>readUnion()|RScoredSortedSetRx.<br/>readUnion()|
ZUNIONSTORE|RScoredSortedSet.<br/>union()<br/>unionAsync()|RScoredSortedSetReactive.<br/>union()|RScoredSortedSetRx.<br/>union()|
ZINTERSTORE|RScoredSortedSet.<br/>intersection()<br/>intersectionAsync()|RScoredSortedSetReactive.<br/>intersection()|RScoredSortedSetRx.<br/>intersection()|
ZPOPMAX|RScoredSortedSet.<br/>pollLast()<br/>pollLastAsync()|RScoredSortedSetReactive.<br/>pollLast()|RScoredSortedSetRx.<br/>pollLast()|
ZPOPMIN|RScoredSortedSet.<br/>pollFirst()<br/>pollFirstAsync()|RScoredSortedSetReactive.<br/>pollFirst()|RScoredSortedSetRx.<br/>pollFirst()|
ZMPOP|RScoredSortedSet.<br/>pollFirstEntriesFromAny()<br/>pollFirstEntriesFromAnyAsync()<br/>pollLastFromAny()<br/>pollLastFromAnyAsync()<br/>pollFirstFromAny()<br/>pollFirstFromAnyAsync()|RScoredSortedSetReactive.<br/>pollFirstEntriesFromAny()<br/>pollLastFromAny()<br/>pollFirstFromAny()|RScoredSortedSetRx.<br/>pollFirstEntriesFromAny()<br/>pollLastFromAny()<br/>pollFirstFromAny()|
ZRANGEBYLEX|RLexSortedSet.<br/>range()<br/>rangeHead()<br/>rangeTail()<br/>rangeAsync()<br/>rangeHeadAsync()<br/>rangeTailAsync()|RLexSortedSetReactive.<br/>range()<br/>rangeHead()<br/>rangeTail()|RLexSortedSetRx.<br/>range()<br/>rangeHead()<br/>rangeTail()|
ZRANGEBYSCORE|RScoredSortedSet.<br/>valueRange()<br/>entryRange()<br/>valueRangeAsync()<br/>entryRangeAsync()|RScoredSortedSetReactive.<br/>valueRange()<br/>entryRange()|RScoredSortedSetRx.<br/>valueRange()<br/>entryRange()|
TIME|RedissonClient.<br/>getNodesGroup().<br/>getNode().time()<br/>getClusterNodesGroup().<br/>getNode().time()| - | - |
ZRANK|RScoredSortedSet.<br/>rank()<br/>rankAsync()|RScoredSortedSetReactive.<br/>rank()|RScoredSortedSetRx.<br/>rank()|
ZREM|RScoredSortedSet.<br/>remove()<br/>removeAll()<br/>removeAsync()<br/>removeAllAsync()|RScoredSortedSetReactive.<br/>remove()<br/>removeAll()|RScoredSortedSetRx.<br/>remove()<br/>removeAll()|
ZREMRANGEBYLEX|RLexSortedSet.<br/>removeRange()<br/>removeRangeHead()<br/>removeRangeTail()<br/>removeRangeAsync()<br/>removeRangeHeadAsync()<br/>removeRangeTailAsync()|RLexSortedSetReactive.<br/>removeRange()<br/>removeRangeHead()<br/>removeRangeTail()|RLexSortedSetRx.<br/>removeRange()<br/>removeRangeHead()<br/>removeRangeTail()|
ZREMRANGEBYSCORE|RScoredSortedSet.<br/>removeRangeByScore()<br/>removeRangeByScoreAsync()|RScoredSortedSetReactive.<br/>removeRangeByScore()|RScoredSortedSetRx.<br/>removeRangeByScore()|
ZREVRANGEBYSCORE|RScoredSortedSet.<br/>valueRangeReversed()<br/>entryRangeReversed()<br/>valueRangeReversedAsync()<br/>entryRangeReversedAsync()|RScoredSortedSetReactive.<br/>entryRangeReversed()<br/>valueRangeReversed()|RScoredSortedSetRx.<br/>entryRangeReversed()<br/>valueRangeReversed()|
ZREVRANK|RScoredSortedSet.<br/>revRank()<br/>revRankAsync()|RScoredSortedSetReactive.<br/>revRank()|RScoredSortedSetRx.<br/>revRank()|
ZSCORE|RScoredSortedSet.<br/>getScore()<br/>getScoreAsync()|RScoredSortedSetReactive.<br/>getScore()|RScoredSortedSetRx.<br/>getScore()|
ZMSCORE|RScoredSortedSet.<br/>getScore()<br/>getScoreAsync()|RScoredSortedSetReactive.<br/>getScore()|RScoredSortedSetRx.<br/>getScore()|
ZINTERCARD | RScoredSortedSet.<br/>countIntersection()<br/>countIntersectionAsync() | RScoredSortedSetReactive.<br/>countIntersection() | RScoredSortedSetRx.<br/>countIntersection() |
ZRANGESTORE | RScoredSortedSet.<br/>rangeTo()<br/>rangeToAsync()<br/>revRangeTo()<br/>revRangeToAsync() | RScoredSortedSetReactive.<br/>rangeTo()<br/>revRangeTo() | RScoredSortedSetRx.<br/>rangeTo()<br/>revRangeTo() |
XACK|RStream.<br/>ack()<br/>ackAsync()|RStreamReactive.<br/>ack()|RStreamRx.<br/>ack()|
XADD|RStream.<br/>add()<br/>addAsync()|RStreamReactive.<br/>add()|RStreamRx.<br/>add()|
XAUTOCLAIM|RStream.<br/>autoClaim()<br/>autoClaimAsync()|RStreamReactive.<br/>autoClaim()|RStreamRx.<br/>autoClaim()|
XCLAIM|RStream.<br/>claim()<br/>claimAsync()|RStreamReactive.<br/>claim()|RStreamRx.<br/>claim()|
XDEL|RStream.<br/>remove()<br/>removeAsync()|RStreamReactive.<br/>remove()|RStreamRx.<br/>remove()|
XGROUP|RStream.<br/>createGroup()<br/>removeGroup()<br/>updateGroup()<br/>createGroupAsync()<br/>removeGroupAsync()<br/>updateGroupAsync()|RStreamReactive.<br/>createGroup()<br/>removeGroup()<br/>updateGroup()|RStreamRx.<br/>createGroup()<br/>removeGroup()<br/>updateGroup()|
XINFO|RStream.<br/>getInfo()<br/>listGroups()<br/>listConsumers()<br/>getInfoAsync()<br/>listGroupsAsync()<br/>listConsumersAsync()|RStreamReactive.<br/>getInfo()<br/>listGroups()<br/>listConsumers()|RStreamRx.<br/>getInfo()<br/>listGroups()<br/>listConsumers()|
XLEN|RStream.<br/>size()<br/>sizeAsync()|RStreamReactive.<br/>size()|RStreamRx.<br/>size()|
XPENDING|RStream.<br/>listPending()<br/>listPendingAsync()|RStreamReactive.<br/>listPending()|RStreamRx.<br/>listPending()|
XRANGE|RStream.<br/>range()<br/>rangeAsync()|RStreamReactive.<br/>range()|RStreamRx.<br/>range()|
XREAD|RStream.<br/>read()<br/>readAsync()|RStreamReactive.<br/>read()|RStreamRx.<br/>read()|
XREADGROUP|RStream.<br/>readGroup()<br/>readGroupAsync()|RStreamReactive.<br/>readGroup()|RStreamRx.<br/>readGroup()|
XREVRANGE|RStream.<br/>rangeReversed()<br/>rangeReversedAsync()|RStreamReactive.<br/>rangeReversed()|RStreamRx.<br/>rangeReversed()|
XTRIM|RStream.<br/>trim()<br/>trimAsync()|RStreamReactive.<br/>trim()|RStreamRx.<br/>trim()|