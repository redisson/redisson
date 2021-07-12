/**
 * Copyright (c) 2013-2021 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.client.protocol;

import org.redisson.api.FastAutoClaimResult;
import org.redisson.api.RType;
import org.redisson.api.StreamInfo;
import org.redisson.api.StreamMessageId;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.convertor.*;
import org.redisson.client.protocol.decoder.*;
import org.redisson.client.protocol.pubsub.PubSubStatusDecoder;
import org.redisson.cluster.ClusterNodeInfo;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public interface RedisCommands {

    RedisStrictCommand<Void> DEBUG = new RedisStrictCommand<Void>("DEBUG");
    
    RedisStrictCommand<Long> GEOADD = new RedisStrictCommand<Long>("GEOADD");
    RedisStrictCommand<Boolean> GEOADD_BOOLEAN = new RedisStrictCommand<>("GEOADD", new BooleanReplayConvertor());
    RedisCommand<Double> GEODIST = new RedisCommand<Double>("GEODIST", new DoubleReplayConvertor());
    RedisCommand<List<Object>> GEORADIUS_RO = new RedisCommand<List<Object>>("GEORADIUS_RO", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> GEORADIUSBYMEMBER_RO = new RedisCommand<List<Object>>("GEORADIUSBYMEMBER_RO", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> GEOSEARCH = new RedisCommand<>("GEOSEARCH", new ObjectListReplayDecoder<>());
    RedisCommand<Object> GEORADIUS_STORE = new RedisCommand<Object>("GEORADIUS", new Long2MultiDecoder());
    RedisCommand<Object> GEORADIUSBYMEMBER_STORE = new RedisCommand<Object>("GEORADIUSBYMEMBER", new Long2MultiDecoder());
    RedisCommand<Object> GEOSEARCHSTORE_STORE = new RedisCommand<Object>("GEOSEARCHSTORE", new Long2MultiDecoder());

    RedisStrictCommand<Integer> KEYSLOT = new RedisStrictCommand<Integer>("CLUSTER", "KEYSLOT", new IntegerReplayConvertor());
    RedisStrictCommand<RType> TYPE = new RedisStrictCommand<RType>("TYPE", new TypeConvertor());

    RedisStrictCommand<Object> BITFIELD_LONG = new RedisStrictCommand<>("BITFIELD", null,
                                                    new ListFirstObjectDecoder(), new LongReplayConvertor());
    RedisStrictCommand<Object> BITFIELD_INT = new RedisStrictCommand<>("BITFIELD", null,
                                                    new ListFirstObjectDecoder(), new IntegerReplayConvertor(0));
    RedisStrictCommand<Object> BITFIELD_BYTE = new RedisStrictCommand<>("BITFIELD", null,
                                                    new ListFirstObjectDecoder(), new ByteReplayConvertor());
    RedisStrictCommand<Object> BITFIELD_SHORT = new RedisStrictCommand<>("BITFIELD", null,
                                                    new ListFirstObjectDecoder(), new ShortReplayConvertor());

    RedisStrictCommand<Boolean> GETBIT = new RedisStrictCommand<Boolean>("GETBIT", new BooleanReplayConvertor());
    RedisStrictCommand<Long> BITS_SIZE = new RedisStrictCommand<Long>("STRLEN", new BitsSizeReplayConvertor());
    RedisStrictCommand<Long> STRLEN = new RedisStrictCommand<Long>("STRLEN");
    RedisStrictCommand<Long> BITCOUNT = new RedisStrictCommand<Long>("BITCOUNT");
    RedisStrictCommand<Integer> BITPOS = new RedisStrictCommand<Integer>("BITPOS", new IntegerReplayConvertor());
    RedisStrictCommand<Void> SETBIT_VOID = new RedisStrictCommand<Void>("SETBIT", new VoidReplayConvertor());
    RedisStrictCommand<Boolean> SETBIT = new RedisStrictCommand<Boolean>("SETBIT", new BooleanReplayConvertor());
    RedisStrictCommand<Void> BITOP = new RedisStrictCommand<Void>("BITOP", new VoidReplayConvertor());

    RedisStrictCommand<Integer> WAIT = new RedisStrictCommand<Integer>("WAIT", new IntegerReplayConvertor());
    RedisStrictCommand<Void> CLIENT_REPLY = new RedisStrictCommand<Void>("CLIENT", "REPLY", new VoidReplayConvertor());
    RedisStrictCommand<Void> ASKING = new RedisStrictCommand<Void>("ASKING", new VoidReplayConvertor());
    RedisStrictCommand<Void> READONLY = new RedisStrictCommand<Void>("READONLY", new VoidReplayConvertor());

    RedisCommand<Map<Object, Object>> ZRANDMEMBER_ENTRIES = new RedisCommand<>("ZRANDMEMBER", new ScoredSortedSetRandomMapDecoder());
    RedisCommand<Set<Object>> ZRANDMEMBER = new RedisCommand<>("ZRANDMEMBER", new ObjectSetReplayDecoder<>());
    RedisCommand<Object> ZRANDMEMBER_SINGLE = new RedisCommand<>("ZRANDMEMBER");
    RedisStrictCommand<List<Object>> ZDIFF = new RedisStrictCommand<>("ZDIFF", new ObjectListReplayDecoder<>());
    RedisCommand<List<Object>> ZUNION = new RedisCommand<>("ZUNION", new ObjectListReplayDecoder<>());
    RedisCommand<List<Object>> ZINTER = new RedisCommand<>("ZINTER", new ObjectListReplayDecoder<>());
    RedisStrictCommand<Integer> ZDIFFSTORE_INT = new RedisStrictCommand<Integer>("ZDIFFSTORE", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> ZUNIONSTORE_INT = new RedisStrictCommand<Integer>("ZUNIONSTORE", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> ZINTERSTORE_INT = new RedisStrictCommand<Integer>("ZINTERSTORE", new IntegerReplayConvertor());
    RedisCommand<Boolean> ZADD_BOOL = new RedisCommand<Boolean>("ZADD", new BooleanAmountReplayConvertor());
    RedisCommand<Boolean> ZADD_NX_BOOL = new RedisCommand<Boolean>("ZADD", new BooleanAmountReplayConvertor());
    RedisCommand<Boolean> ZADD_BOOL_RAW = new RedisCommand<Boolean>("ZADD", new BooleanAmountReplayConvertor());
    RedisCommand<Boolean> ZADD_RAW = new RedisCommand<Boolean>("ZADD");
    RedisStrictCommand<Integer> ZADD_INT = new RedisStrictCommand<Integer>("ZADD", new IntegerReplayConvertor());
    RedisCommand<Long> ZADD = new RedisCommand<Long>("ZADD");
    RedisStrictCommand<Long> ZREM_LONG = new RedisStrictCommand<Long>("ZREM");
    RedisCommand<Boolean> ZREM = new RedisCommand<Boolean>("ZREM", new BooleanAmountReplayConvertor());
    RedisStrictCommand<Integer> ZCARD_INT = new RedisStrictCommand<Integer>("ZCARD", new IntegerReplayConvertor());
    RedisStrictCommand<Long> ZCARD = new RedisStrictCommand<Long>("ZCARD");
    RedisStrictCommand<Integer> ZCOUNT = new RedisStrictCommand<Integer>("ZCOUNT", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> ZLEXCOUNT = new RedisStrictCommand<Integer>("ZLEXCOUNT", new IntegerReplayConvertor());
    RedisCommand<Boolean> ZSCORE_CONTAINS = new RedisCommand<Boolean>("ZSCORE", new BooleanNotNullReplayConvertor());
    RedisStrictCommand<Double> ZSCORE = new RedisStrictCommand<Double>("ZSCORE", new DoubleReplayConvertor());
    RedisStrictCommand<Long> ZRANK = new RedisStrictCommand<Long>("ZRANK");
    RedisCommand<Integer> ZRANK_INT = new RedisCommand<Integer>("ZRANK", new IntegerReplayConvertor());
    RedisStrictCommand<Long> ZREVRANK = new RedisStrictCommand<Long>("ZREVRANK");
    RedisCommand<Integer> ZREVRANK_INT = new RedisCommand<Integer>("ZREVRANK", new IntegerReplayConvertor());
    RedisCommand<Object> ZRANGE_SINGLE = new RedisCommand<Object>("ZRANGE", new ListFirstObjectDecoder());
    RedisStrictCommand<Double> ZRANGE_SINGLE_SCORE = new RedisStrictCommand<Double>("ZRANGE", new ObjectFirstScoreReplayDecoder());
    RedisCommand<List<Object>> ZRANGE = new RedisCommand<List<Object>>("ZRANGE", new ObjectListReplayDecoder<Object>());
    RedisCommand<Integer> ZRANGESTORE = new RedisCommand<>("ZRANGESTORE", new IntegerReplayConvertor());
    RedisCommand<List<Object>> ZPOPMIN = new RedisCommand<List<Object>>("ZPOPMIN", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> ZPOPMAX = new RedisCommand<List<Object>>("ZPOPMAX", new ObjectListReplayDecoder<Object>());
    RedisStrictCommand<Integer> ZREMRANGEBYRANK = new RedisStrictCommand<Integer>("ZREMRANGEBYRANK", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> ZREMRANGEBYSCORE = new RedisStrictCommand<Integer>("ZREMRANGEBYSCORE", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> ZREMRANGEBYLEX = new RedisStrictCommand<Integer>("ZREMRANGEBYLEX", new IntegerReplayConvertor());
    RedisCommand<List<Object>> ZRANGEBYLEX = new RedisCommand<List<Object>>("ZRANGEBYLEX", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> ZREVRANGEBYLEX = new RedisCommand<List<Object>>("ZREVRANGEBYLEX", new ObjectListReplayDecoder<Object>());
    RedisCommand<Set<Object>> ZRANGEBYSCORE = new RedisCommand<Set<Object>>("ZRANGEBYSCORE", new ObjectSetReplayDecoder<Object>());
    RedisCommand<List<Object>> ZRANGEBYSCORE_LIST = new RedisCommand<List<Object>>("ZRANGEBYSCORE", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> ZREVRANGE = new RedisCommand<List<Object>>("ZREVRANGE", new ObjectListReplayDecoder<Object>());
    RedisCommand<Set<Object>> ZREVRANGEBYSCORE = new RedisCommand<Set<Object>>("ZREVRANGEBYSCORE", new ObjectSetReplayDecoder<Object>());
    RedisCommand<List<ScoredEntry<Object>>> ZREVRANGE_ENTRY = new RedisCommand<List<ScoredEntry<Object>>>("ZREVRANGE", new ScoredSortedSetReplayDecoder<Object>());
    RedisCommand<List<ScoredEntry<Object>>> ZREVRANGEBYSCORE_ENTRY = new RedisCommand<List<ScoredEntry<Object>>>("ZREVRANGEBYSCORE", new ScoredSortedSetReplayDecoder<Object>());
    RedisCommand<List<ScoredEntry<Object>>> ZRANGE_ENTRY = new RedisCommand<List<ScoredEntry<Object>>>("ZRANGE", new ScoredSortedSetReplayDecoder<Object>());
    RedisCommand<List<ScoredEntry<Object>>> ZRANGEBYSCORE_ENTRY = new RedisCommand<List<ScoredEntry<Object>>>("ZRANGEBYSCORE", new ScoredSortedSetReplayDecoder<Object>());
    RedisCommand<ListScanResult<Object>> ZSCAN = new RedisCommand<ListScanResult<Object>>("ZSCAN", new ListMultiDecoder2(new ScoredSortedSetScanReplayDecoder(), new ScoredSortedSetScanDecoder<Object>()));
    RedisStrictCommand<Double> ZINCRBY = new RedisStrictCommand<Double>("ZINCRBY", new DoubleNullSafeReplayConvertor());

    RedisCommand<ListScanResult<String>> SCAN = new RedisCommand<ListScanResult<String>>("SCAN", new ListMultiDecoder2(new ListScanResultReplayDecoder(), new ObjectListReplayDecoder<String>()));
    RedisStrictCommand<String> RANDOM_KEY = new RedisStrictCommand<String>("RANDOMKEY");
    RedisCommand<String> PING = new RedisCommand<String>("PING");
    RedisStrictCommand<Boolean> PING_BOOL = new RedisStrictCommand<Boolean>("PING", new BooleanNotNullReplayConvertor());

    RedisStrictCommand<Void> SHUTDOWN = new RedisStrictCommand<Void>("SHUTDOWN", new VoidReplayConvertor());
    RedisStrictCommand<Void> UNWATCH = new RedisStrictCommand<Void>("UNWATCH", new VoidReplayConvertor());
    RedisStrictCommand<Void> WATCH = new RedisStrictCommand<Void>("WATCH", new VoidReplayConvertor());
    RedisStrictCommand<Void> MULTI = new RedisStrictCommand<Void>("MULTI", new VoidReplayConvertor());
    RedisStrictCommand<Void> DISCARD = new RedisStrictCommand<Void>("DISCARD", new VoidReplayConvertor());
    RedisCommand<List<Object>> EXEC = new RedisCommand<List<Object>>("EXEC", new ObjectListReplayDecoder<Object>());

    RedisCommand<Boolean> SADD_BOOL = new RedisCommand<Boolean>("SADD", new BooleanAmountReplayConvertor());
    RedisCommand<Integer> SADD = new RedisCommand<Integer>("SADD", new IntegerReplayConvertor());
    RedisCommand<Set<Object>> SPOP = new RedisCommand<Set<Object>>("SPOP", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Object> SPOP_SINGLE = new RedisCommand<Object>("SPOP");
    RedisCommand<Boolean> SADD_SINGLE = new RedisCommand<Boolean>("SADD", new BooleanReplayConvertor());
    RedisCommand<Boolean> SREM_SINGLE = new RedisCommand<Boolean>("SREM", new BooleanAmountReplayConvertor());
    RedisCommand<Boolean> SMOVE = new RedisCommand<Boolean>("SMOVE", new BooleanReplayConvertor());
    RedisCommand<Set<Object>> SMEMBERS = new RedisCommand<Set<Object>>("SMEMBERS", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Set<Object>> SRANDMEMBER = new RedisCommand<Set<Object>>("SRANDMEMBER", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Object> SRANDMEMBER_SINGLE = new RedisCommand<Object>("SRANDMEMBER");
    RedisCommand<ListScanResult<Object>> SSCAN = new RedisCommand<ListScanResult<Object>>("SSCAN", new ListMultiDecoder2(new ListScanResultReplayDecoder(), new ObjectListReplayDecoder()));
    RedisCommand<ListScanResult<Object>> EVAL_SSCAN = new RedisCommand<ListScanResult<Object>>("EVAL", new ListMultiDecoder2(new ListScanResultReplayDecoder(), new ObjectListReplayDecoder<Object>()));
    RedisCommand<ListScanResult<Object>> EVAL_ZSCAN = new RedisCommand<ListScanResult<Object>>("EVAL", new ListMultiDecoder2(new ListScanResultReplayDecoder(), new ObjectListReplayDecoder<Object>()));
    RedisCommand<Boolean> SISMEMBER = new RedisCommand<Boolean>("SISMEMBER", new BooleanReplayConvertor());
    RedisStrictCommand<Integer> SCARD_INT = new RedisStrictCommand<Integer>("SCARD", new IntegerReplayConvertor());
    RedisStrictCommand<Long> SCARD = new RedisStrictCommand<Long>("SCARD");
    RedisStrictCommand<Integer> SUNIONSTORE_INT = new RedisStrictCommand<Integer>("SUNIONSTORE", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> SDIFFSTORE_INT = new RedisStrictCommand<Integer>("SDIFFSTORE", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> SINTERSTORE_INT = new RedisStrictCommand<Integer>("SINTERSTORE", new IntegerReplayConvertor());
    RedisStrictCommand<Long> SUNIONSTORE = new RedisStrictCommand<Long>("SUNIONSTORE");
    RedisStrictCommand<Long> SINTERSTORE = new RedisStrictCommand<Long>("SINTERSTORE");
    RedisStrictCommand<Long> SDIFFSTORE = new RedisStrictCommand<Long>("SDIFFSTORE");
    RedisCommand<Set<Object>> SUNION = new RedisCommand<Set<Object>>("SUNION", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Set<Object>> SDIFF = new RedisCommand<Set<Object>>("SDIFF", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Set<Object>> SINTER = new RedisCommand<Set<Object>>("SINTER", new ObjectSetReplayDecoder<Object>());

    RedisStrictCommand<Long> LPOS = new RedisStrictCommand<>("LPOS");
    RedisCommand<Void> LSET = new RedisCommand<Void>("LSET", new VoidReplayConvertor());
    RedisCommand<Object> LPOP = new RedisCommand<Object>("LPOP");
    RedisCommand<Boolean> LREM_SINGLE = new RedisCommand<Boolean>("LREM", new BooleanReplayConvertor());
    RedisCommand<Object> LINDEX = new RedisCommand<Object>("LINDEX");
    RedisCommand<Object> LMOVE = new RedisCommand<Object>("LMOVE");
    RedisCommand<Integer> LINSERT_INT = new RedisCommand<Integer>("LINSERT", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> LLEN_INT = new RedisStrictCommand<Integer>("LLEN", new IntegerReplayConvertor());
    RedisStrictCommand<Void> LTRIM = new RedisStrictCommand<Void>("LTRIM", new VoidReplayConvertor());

    RedisStrictCommand<Boolean> PEXPIRE = new RedisStrictCommand<Boolean>("PEXPIRE", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> PEXPIREAT = new RedisStrictCommand<Boolean>("PEXPIREAT", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> PERSIST = new RedisStrictCommand<Boolean>("PERSIST", new BooleanReplayConvertor());
    RedisStrictCommand<Long> PTTL = new RedisStrictCommand<Long>("PTTL");

    RedisCommand<Object> RPOPLPUSH = new RedisCommand<Object>("RPOPLPUSH");
    RedisCommand<Object> BRPOPLPUSH = new RedisCommand<Object>("BRPOPLPUSH");
    RedisCommand<List<Object>> BLPOP = new RedisCommand<List<Object>>("BLPOP", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> BRPOP = new RedisCommand<List<Object>>("BRPOP", new ObjectListReplayDecoder<Object>());
    RedisCommand<Object> BLPOP_VALUE = new RedisCommand<Object>("BLPOP", new ListObjectDecoder<Object>(1));
    RedisCommand<Object> BLMOVE = new RedisCommand<Object>("BLMOVE");
    RedisCommand<Object> BRPOP_VALUE = new RedisCommand<Object>("BRPOP", new ListObjectDecoder<Object>(1));
    RedisCommand<Object> BZPOPMIN_VALUE = new RedisCommand<Object>("BZPOPMIN", new ScoredSortedSetPolledObjectDecoder());
    RedisCommand<Object> BZPOPMAX_VALUE = new RedisCommand<Object>("BZPOPMAX", new ScoredSortedSetPolledObjectDecoder());

    Set<String> BLOCKING_COMMAND_NAMES = new HashSet<String>(
            Arrays.asList(BRPOPLPUSH.getName(), BZPOPMIN_VALUE.getName(), BZPOPMAX_VALUE.getName(),
                    BLPOP.getName(), BRPOP.getName(), BLMOVE.getName()));

    RedisCommand<Boolean> PFADD = new RedisCommand<Boolean>("PFADD", new BooleanReplayConvertor());
    RedisStrictCommand<Long> PFCOUNT = new RedisStrictCommand<Long>("PFCOUNT");
    RedisStrictCommand<Void> PFMERGE = new RedisStrictCommand<Void>("PFMERGE", new VoidReplayConvertor());

    RedisCommand<List<Object>> SORT_LIST = new RedisCommand<List<Object>>("SORT", new ObjectListReplayDecoder<Object>());
    RedisCommand<Set<Object>> SORT_SET = new RedisCommand<Set<Object>>("SORT", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Integer> SORT_TO = new RedisCommand<Integer>("SORT", new IntegerReplayConvertor());
    
    RedisCommand<Object> RPOP = new RedisCommand<Object>("RPOP");
    RedisCommand<Integer> LPUSH = new RedisCommand<Integer>("LPUSH", new IntegerReplayConvertor());
    RedisCommand<Integer> LPUSHX = new RedisCommand<Integer>("LPUSHX", new IntegerReplayConvertor());
    RedisCommand<Boolean> LPUSH_BOOLEAN = new RedisCommand<Boolean>("LPUSH", new TrueReplayConvertor());
    RedisStrictCommand<Void> LPUSH_VOID = new RedisStrictCommand<Void>("LPUSH", new VoidReplayConvertor());
    RedisCommand<List<Object>> LRANGE = new RedisCommand<List<Object>>("LRANGE", new ObjectListReplayDecoder<Object>());
    RedisCommand<Set<Object>> LRANGE_SET = new RedisCommand<Set<Object>>("LRANGE", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Integer> RPUSH = new RedisCommand<Integer>("RPUSH", new IntegerReplayConvertor());
    RedisCommand<Integer> RPUSHX = new RedisCommand<Integer>("RPUSHX", new IntegerReplayConvertor());
    RedisCommand<Boolean> RPUSH_BOOLEAN = new RedisCommand<Boolean>("RPUSH", new TrueReplayConvertor());
    RedisCommand<Void> RPUSH_VOID = new RedisCommand<Void>("RPUSH", new VoidReplayConvertor());

    RedisStrictCommand<String> SCRIPT_LOAD = new RedisStrictCommand<String>("SCRIPT", "LOAD", new ObjectDecoder(new StringDataDecoder()));
    RedisStrictCommand<Boolean> SCRIPT_KILL = new RedisStrictCommand<Boolean>("SCRIPT", "KILL", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> SCRIPT_FLUSH = new RedisStrictCommand<Boolean>("SCRIPT", "FLUSH", new BooleanReplayConvertor());
    RedisStrictCommand<List<Boolean>> SCRIPT_EXISTS = new RedisStrictCommand<List<Boolean>>("SCRIPT", "EXISTS", new ObjectListReplayDecoder<Boolean>(), new BooleanReplayConvertor());

    RedisStrictCommand<Boolean> EVAL_BOOLEAN_AMOUNT = new RedisStrictCommand<Boolean>("EVAL", new BooleanAmountReplayConvertor());
    RedisStrictCommand<Boolean> EVAL_BOOLEAN = new RedisStrictCommand<Boolean>("EVAL", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> EVAL_BOOLEAN_SAFE = new RedisStrictCommand<Boolean>("EVAL", new BooleanNullSafeReplayConvertor());
    RedisStrictCommand<Boolean> EVAL_NULL_BOOLEAN = new RedisStrictCommand<Boolean>("EVAL", new BooleanNullReplayConvertor());
    RedisStrictCommand<String> EVAL_STRING = new RedisStrictCommand("EVAL",
            new ObjectDecoder(new StringReplayDecoder()));
    RedisStrictCommand<String> EVAL_STRING_DATA = new RedisStrictCommand("EVAL",
            new ObjectDecoder(new StringDataDecoder()));
    RedisStrictCommand<Integer> EVAL_INTEGER = new RedisStrictCommand<Integer>("EVAL", new IntegerReplayConvertor());
    RedisStrictCommand<Double> EVAL_DOUBLE = new RedisStrictCommand<Double>("EVAL", new DoubleNullSafeReplayConvertor());
    RedisStrictCommand<Long> EVAL_LONG = new RedisStrictCommand<Long>("EVAL");
    RedisStrictCommand<Long> EVAL_LONG_SAFE = new RedisStrictCommand<Long>("EVAL", new LongReplayConvertor());
    RedisStrictCommand<Void> EVAL_VOID = new RedisStrictCommand<Void>("EVAL", new VoidReplayConvertor());
    RedisCommand<Object> EVAL_FIRST_LIST = new RedisCommand<Object>("EVAL", new ListFirstObjectDecoder());
    RedisCommand<List<Object>> EVAL_LIST = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> EVAL_LIST_REVERSE = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<>(true));
    RedisCommand<List<Integer>> EVAL_INT_LIST = new RedisCommand("EVAL", new ObjectListReplayDecoder<Integer>(), new IntegerReplayConvertor());
    RedisCommand<Set<Object>> EVAL_SET = new RedisCommand<Set<Object>>("EVAL", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Object> EVAL_OBJECT = new RedisCommand<Object>("EVAL");
    RedisCommand<Object> EVAL_MAP_VALUE = new RedisCommand<Object>("EVAL", new MapValueDecoder());
    RedisCommand<Set<Entry<Object, Object>>> EVAL_MAP_ENTRY = new RedisCommand<Set<Entry<Object, Object>>>("EVAL",
            new ObjectMapEntryReplayDecoder());
    RedisCommand<Map<Object, Object>> EVAL_MAP = new RedisCommand<Map<Object, Object>>("EVAL",
            new ObjectMapReplayDecoder());
    RedisCommand<List<Object>> EVAL_MAP_VALUE_LIST = new RedisCommand<List<Object>>("EVAL",
            new MapValueDecoder(new ObjectListReplayDecoder<>()));
    RedisCommand<Set<Object>> EVAL_MAP_VALUE_SET = new RedisCommand<Set<Object>>("EVAL",
            new MapValueDecoder(new ObjectSetReplayDecoder<>()));
    RedisCommand<Set<Object>> EVAL_MAP_KEY_SET = new RedisCommand<Set<Object>>("EVAL",
            new MapValueDecoder(new ObjectSetReplayDecoder<>()));

    RedisStrictCommand<Long> INCR = new RedisStrictCommand<Long>("INCR");
    RedisStrictCommand<Long> INCRBY = new RedisStrictCommand<Long>("INCRBY");
    RedisStrictCommand<Double> INCRBYFLOAT = new RedisStrictCommand<Double>("INCRBYFLOAT", new DoubleNullSafeReplayConvertor());
    RedisStrictCommand<Long> DECR = new RedisStrictCommand<Long>("DECR");

    RedisStrictCommand<Void> AUTH = new RedisStrictCommand<Void>("AUTH", new VoidReplayConvertor());
    RedisStrictCommand<Void> SELECT = new RedisStrictCommand<Void>("SELECT", new VoidReplayConvertor());
    RedisStrictCommand<Void> CLIENT_SETNAME = new RedisStrictCommand<Void>("CLIENT", "SETNAME", new VoidReplayConvertor());
    RedisStrictCommand<String> CLIENT_GETNAME = new RedisStrictCommand<String>("CLIENT", "GETNAME", new ObjectDecoder(new StringDataDecoder()));
    RedisStrictCommand<Void> FLUSHDB = new RedisStrictCommand<Void>("FLUSHDB", new VoidReplayConvertor());
    RedisStrictCommand<Void> SWAPDB = new RedisStrictCommand<Void>("SWAPDB", new VoidReplayConvertor());
    RedisStrictCommand<Void> FLUSHALL = new RedisStrictCommand<Void>("FLUSHALL", new VoidReplayConvertor());

    RedisStrictCommand<Void> SAVE = new RedisStrictCommand<Void>("SAVE", new VoidReplayConvertor());
    RedisStrictCommand<Long> LASTSAVE = new RedisStrictCommand<Long>("LASTSAVE");
    RedisStrictCommand<Void> BGSAVE = new RedisStrictCommand<Void>("BGSAVE", new VoidReplayConvertor());
    RedisStrictCommand<Void> BGREWRITEAOF = new RedisStrictCommand<Void>("BGREWRITEAOF", new VoidReplayConvertor());
    
    RedisStrictCommand<Void> FLUSHDB_ASYNC = new RedisStrictCommand<Void>("FLUSHDB", "ASYNC", new VoidReplayConvertor());
    RedisStrictCommand<Void> FLUSHALL_ASYNC = new RedisStrictCommand<Void>("FLUSHALL", "ASYNC", new VoidReplayConvertor());

    RedisStrictCommand<List<String>> KEYS = new RedisStrictCommand<List<String>>("KEYS", new StringListReplayDecoder());
    RedisCommand<List<Object>> MGET = new RedisCommand<List<Object>>("MGET", new ObjectListReplayDecoder<Object>());
    RedisStrictCommand<Void> MSET = new RedisStrictCommand<Void>("MSET", new VoidReplayConvertor());
    RedisStrictCommand<Boolean> MSETNX = new RedisStrictCommand<Boolean>("MSETNX", new BooleanReplayConvertor());

    RedisStrictCommand<Boolean> HSETNX = new RedisStrictCommand<Boolean>("HSETNX", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> HSET = new RedisStrictCommand<Boolean>("HSET", new BooleanReplayConvertor());
    RedisStrictCommand<Void> HSET_VOID = new RedisStrictCommand<Void>("HSET", new VoidReplayConvertor());
    RedisCommand<MapScanResult<Object, Object>> HSCAN =
            new RedisCommand<MapScanResult<Object, Object>>("HSCAN",
                        new ListMultiDecoder2(new MapScanResultReplayDecoder(),
                                new ObjectMapReplayDecoder()));
    RedisCommand<Map<Object, Object>> HRANDFIELD = new RedisCommand<>("HRANDFIELD",
                        new ObjectMapReplayDecoder(), new EmptyMapConvertor());
    RedisCommand<Set<Object>> HRANDFIELD_KEYS = new RedisCommand<>("HRANDFIELD",
                        new MapKeyDecoder(new ObjectSetReplayDecoder<>()), new EmptySetConvertor());
    RedisCommand<Map<Object, Object>> HGETALL = new RedisCommand<Map<Object, Object>>("HGETALL",
                        new ObjectMapReplayDecoder());
    RedisCommand<Set<Entry<Object, Object>>> HGETALL_ENTRY = new RedisCommand<Set<Entry<Object, Object>>>("HGETALL",
                        new ObjectMapEntryReplayDecoder());
    RedisCommand<List<Object>> HVALS = new RedisCommand<List<Object>>("HVALS",
                        new MapValueDecoder(new ObjectListReplayDecoder<>()));
    RedisCommand<Boolean> HEXISTS = new RedisCommand<Boolean>("HEXISTS", new BooleanReplayConvertor());
    RedisStrictCommand<Integer> HLEN = new RedisStrictCommand<Integer>("HLEN", new IntegerReplayConvertor());
    RedisCommand<Integer> HSTRLEN = new RedisCommand<Integer>("HSTRLEN", new IntegerReplayConvertor());
    RedisStrictCommand<Long> HLEN_LONG = new RedisStrictCommand<Long>("HLEN");
    RedisCommand<Set<Object>> HKEYS = new RedisCommand<Set<Object>>("HKEYS",
                        new MapKeyDecoder(new ObjectSetReplayDecoder()));
    RedisCommand<Void> HMSET = new RedisCommand<Void>("HMSET", new VoidReplayConvertor());
    RedisCommand<Object> HGET = new RedisCommand<Object>("HGET", new MapValueDecoder());
    RedisCommand<Long> HDEL = new RedisStrictCommand<Long>("HDEL");

    RedisStrictCommand<Long> DEL = new RedisStrictCommand<Long>("DEL");
    RedisStrictCommand<Long> DBSIZE = new RedisStrictCommand<Long>("DBSIZE");
    RedisStrictCommand<Boolean> DEL_BOOL = new RedisStrictCommand<Boolean>("DEL", new BooleanNullSafeReplayConvertor());
    RedisStrictCommand<Boolean> DEL_OBJECTS = new RedisStrictCommand<Boolean>("DEL", new BooleanAmountReplayConvertor());
    RedisStrictCommand<Void> DEL_VOID = new RedisStrictCommand<Void>("DEL", new VoidReplayConvertor());
    
    RedisStrictCommand<Long> UNLINK = new RedisStrictCommand<Long>("UNLINK");
    RedisStrictCommand<Boolean> UNLINK_BOOL = new RedisStrictCommand<Boolean>("UNLINK", new BooleanNullSafeReplayConvertor());

    RedisCommand<Object> DUMP = new RedisCommand<Object>("DUMP");
    RedisStrictCommand<Void> RESTORE = new RedisStrictCommand<Void>("RESTORE", new VoidReplayConvertor());
    
    RedisCommand<Object> GET = new RedisCommand<Object>("GET");
    RedisCommand<Object> GETRANGE = new RedisCommand<Object>("GETRANGE");
    RedisCommand<Long> SETRANGE = new RedisCommand<Long>("SETRANGE");
    RedisStrictCommand<Long> GET_LONG = new RedisStrictCommand<Long>("GET", new LongReplayConvertor());
    RedisStrictCommand<Integer> GET_INTEGER = new RedisStrictCommand<Integer>("GET", new IntegerReplayConvertor(0));
    RedisStrictCommand<Double> GET_DOUBLE = new RedisStrictCommand<Double>("GET", new DoubleNullSafeReplayConvertor());
    RedisCommand<Object> GETSET = new RedisCommand<Object>("GETSET");
    RedisCommand<Long> GETSET_LONG = new RedisCommand<>("GETSET", new LongReplayConvertor());
    RedisCommand<Double> GETSET_DOUBLE = new RedisCommand<>("GETSET", new DoubleReplayConvertor(0D));
    RedisCommand<Void> SET = new RedisCommand<Void>("SET", new VoidReplayConvertor());
    RedisCommand<Void> APPEND = new RedisCommand<Void>("APPEND", new VoidReplayConvertor());
    RedisCommand<Boolean> SET_BOOLEAN = new RedisCommand<Boolean>("SET", new BooleanNotNullReplayConvertor());
    RedisCommand<Boolean> SETNX = new RedisCommand<Boolean>("SETNX", new BooleanReplayConvertor());
    RedisCommand<Void> PSETEX = new RedisCommand<Void>("PSETEX", new VoidReplayConvertor());

    RedisCommand<Map<StreamMessageId, Map<Object, Object>>> XRANGE = new RedisCommand<>("XRANGE",
            new ListMultiDecoder2(
                    new ObjectMapReplayDecoder2(),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder())));

    RedisCommand<Map<StreamMessageId, Map<Object, Object>>> XREVRANGE =
                new RedisCommand<>("XREVRANGE", XRANGE.getReplayMultiDecoder());
    
    RedisCommand<Map<String, Map<StreamMessageId, Map<Object, Object>>>> XREAD = new RedisCommand<>("XREAD",
            new ListMultiDecoder2(
                    new StreamResultDecoder(false),
                    new ObjectDecoder(StringCodec.INSTANCE.getValueDecoder()),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder())));
            
    RedisCommand<Map<String, Map<StreamMessageId, Map<Object, Object>>>> XREAD_BLOCKING = new RedisCommand<>("XREAD", XREAD.getReplayMultiDecoder());

    RedisCommand<Map<StreamMessageId, Map<Object, Object>>> XREAD_SINGLE = new RedisCommand<>("XREAD",
            new ListMultiDecoder2(
                    new StreamResultDecoder(true),
                    new ObjectDecoder(StringCodec.INSTANCE.getValueDecoder()),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder())));
    
    RedisCommand<Map<StreamMessageId, Map<Object, Object>>> XREAD_BLOCKING_SINGLE = 
                new RedisCommand<>("XREAD", XREAD_SINGLE.getReplayMultiDecoder());

    RedisCommand<Map<String, Map<StreamMessageId, Map<Object, Object>>>> XREADGROUP =
            new RedisCommand<>("XREADGROUP", XREAD.getReplayMultiDecoder());

    RedisCommand<Map<String, Map<StreamMessageId, Map<Object, Object>>>> XREADGROUP_BLOCKING = 
                new RedisCommand<>("XREADGROUP", XREADGROUP.getReplayMultiDecoder());
            
    RedisCommand<Map<StreamMessageId, Map<Object, Object>>> XREADGROUP_SINGLE = new RedisCommand<>("XREADGROUP",
            new ListMultiDecoder2(
                    new StreamResultDecoder(true),
                    new ObjectDecoder(StringCodec.INSTANCE.getValueDecoder()),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder())));

    RedisCommand<StreamInfo<Object, Object>> XINFO_GROUPS = new RedisCommand<>("XINFO", "GROUPS",
            new ListMultiDecoder2(new ObjectListReplayDecoder(), new StreamGroupInfoDecoder()));

    RedisCommand<StreamInfo<Object, Object>> XINFO_CONSUMERS = new RedisCommand<>("XINFO", "CONSUMERS",
            new ListMultiDecoder2(new ObjectListReplayDecoder(), new StreamConsumerInfoDecoder()));

    RedisCommand<Object> XCLAIM_IDS = new RedisCommand<>("XCLAIM", new ObjectDecoder(new StreamIdDecoder()));
    
    RedisCommand<Map<StreamMessageId, Map<Object, Object>>> XCLAIM = new RedisCommand<>("XCLAIM",
            new ListMultiDecoder2(
                    new ObjectMapReplayDecoder2(),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder())));

    RedisCommand<FastAutoClaimResult> XAUTOCLAIM_IDS = new RedisCommand<>("XAUTOCLAIM",
            new ListMultiDecoder2(new FastAutoClaimDecoder(), new ObjectListReplayDecoder(false, new StreamIdDecoder())));

    RedisCommand<Map<StreamMessageId, Map<Object, Object>>> XAUTOCLAIM = new RedisCommand<>("XAUTOCLAIM",
            new ListMultiDecoder2(
                    new AutoClaimDecoder(),
                    new ObjectMapReplayDecoder2(),
                    new ObjectDecoder(new StreamIdDecoder()),
                    new MapEntriesDecoder(new StreamObjectMapReplayDecoder())));

    RedisCommand<Map<StreamMessageId, Map<Object, Object>>> XREADGROUP_BLOCKING_SINGLE = new RedisCommand<>("XREADGROUP",
            XREADGROUP_SINGLE.getReplayMultiDecoder());

    Set<RedisCommand> BLOCKING_COMMANDS = new HashSet<RedisCommand>(Arrays.asList(
            XREAD_BLOCKING_SINGLE, XREAD_BLOCKING, XREADGROUP_BLOCKING_SINGLE, XREADGROUP_BLOCKING));
    
    RedisStrictCommand<StreamMessageId> XADD = new RedisStrictCommand<StreamMessageId>("XADD", new StreamIdConvertor());
    RedisStrictCommand<Void> XGROUP = new RedisStrictCommand<Void>("XGROUP", new VoidReplayConvertor());
    RedisStrictCommand<Long> XGROUP_LONG = new RedisStrictCommand<Long>("XGROUP");
    RedisStrictCommand<Void> XADD_VOID = new RedisStrictCommand<Void>("XADD", new VoidReplayConvertor());
    RedisStrictCommand<Long> XLEN = new RedisStrictCommand<Long>("XLEN");
    RedisStrictCommand<Long> XACK = new RedisStrictCommand<Long>("XACK");
    RedisStrictCommand<Long> XDEL = new RedisStrictCommand<Long>("XDEL");
    RedisStrictCommand<Long> XTRIM = new RedisStrictCommand<Long>("XTRIM");
    RedisCommand<Object> XPENDING = new RedisCommand<Object>("XPENDING", 
            new ListMultiDecoder2(new PendingResultDecoder(), new ObjectListReplayDecoder(), new ObjectListReplayDecoder()));
    RedisCommand<Object> XPENDING_ENTRIES = new RedisCommand<Object>("XPENDING", 
            new PendingEntryDecoder());
    
    RedisStrictCommand<Long> TOUCH_LONG = new RedisStrictCommand<Long>("TOUCH", new LongReplayConvertor());
    RedisStrictCommand<Boolean> TOUCH = new RedisStrictCommand<Boolean>("TOUCH", new BooleanReplayConvertor());
    RedisStrictCommand<Long> EXISTS_LONG = new RedisStrictCommand<Long>("EXISTS", new LongReplayConvertor());
    RedisStrictCommand<Boolean> EXISTS = new RedisStrictCommand<Boolean>("EXISTS", new BooleanAmountReplayConvertor());
    RedisStrictCommand<Boolean> NOT_EXISTS = new RedisStrictCommand<Boolean>("EXISTS", new BooleanNumberReplayConvertor(1L));

    RedisStrictCommand<Long> OBJECT_IDLETIME = new RedisStrictCommand<Long>("OBJECT", "IDLETIME", new LongReplayConvertor());
    RedisStrictCommand<Long> MEMORY_USAGE = new RedisStrictCommand<Long>("MEMORY", "USAGE", new LongReplayConvertor());
    RedisStrictCommand<Map<String, String>> MEMORY_STATS = new RedisStrictCommand<>("MEMORY", "STATS", new StringMapReplayDecoder());
    RedisStrictCommand<Boolean> RENAMENX = new RedisStrictCommand<Boolean>("RENAMENX", new BooleanReplayConvertor());
    RedisStrictCommand<Void> RENAME = new RedisStrictCommand<Void>("RENAME", new VoidReplayConvertor());
    RedisStrictCommand<Boolean> MOVE = new RedisStrictCommand<Boolean>("MOVE", new BooleanReplayConvertor());
    RedisStrictCommand<Void> MIGRATE = new RedisStrictCommand<Void>("MIGRATE", new VoidReplayConvertor());
    RedisStrictCommand<Void> QUIT = new RedisStrictCommand<Void>("QUIT", new VoidReplayConvertor());

    RedisStrictCommand<Long> PUBLISH = new RedisStrictCommand<Long>("PUBLISH");
    RedisCommand<Long> PUBSUB_NUMSUB = new RedisCommand<Long>("PUBSUB", "NUMSUB", new ListObjectDecoder<Long>(1));
    RedisCommand<List<String>> PUBSUB_CHANNELS = new RedisStrictCommand<>("PUBSUB", "CHANNELS", new StringListReplayDecoder());

    RedisCommand<Object> SUBSCRIBE = new RedisCommand<Object>("SUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> UNSUBSCRIBE = new RedisCommand<Object>("UNSUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> PSUBSCRIBE = new RedisCommand<Object>("PSUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> PUNSUBSCRIBE = new RedisCommand<Object>("PUNSUBSCRIBE", new PubSubStatusDecoder());

    Set<String> PUBSUB_COMMANDS = new HashSet<String>(
            Arrays.asList(PSUBSCRIBE.getName(), SUBSCRIBE.getName(), PUNSUBSCRIBE.getName(), UNSUBSCRIBE.getName()));

    Set<String> SCAN_COMMANDS = new HashSet<String>(
            Arrays.asList(HSCAN.getName(), SCAN.getName(), ZSCAN.getName(), SSCAN.getName()));

    RedisStrictCommand<List<ClusterNodeInfo>> CLUSTER_NODES = new RedisStrictCommand<List<ClusterNodeInfo>>("CLUSTER", "NODES",
            new ObjectDecoder(new ClusterNodesDecoder(false)));
    RedisStrictCommand<List<ClusterNodeInfo>> CLUSTER_NODES_SSL = new RedisStrictCommand<List<ClusterNodeInfo>>("CLUSTER", "NODES",
            new ObjectDecoder(new ClusterNodesDecoder(true)));
    RedisStrictCommand<Long> TIME_LONG = new RedisStrictCommand<Long>("TIME", new TimeLongObjectDecoder());
    RedisStrictCommand<Time> TIME = new RedisStrictCommand<Time>("TIME", new TimeObjectDecoder());
    RedisStrictCommand<Map<String, String>> CLUSTER_INFO = new RedisStrictCommand<Map<String, String>>("CLUSTER", "INFO", new StringMapDataDecoder());

    RedisStrictCommand<Void> SENTINEL_FAILOVER = new RedisStrictCommand<Void>("SENTINEL", "FAILOVER", new VoidReplayConvertor());
    RedisStrictCommand<Void> SENTINEL_REMOVE = new RedisStrictCommand<Void>("SENTINEL", "REMOVE", new VoidReplayConvertor());
    RedisStrictCommand<Void> SENTINEL_MONITOR = new RedisStrictCommand<Void>("SENTINEL", "MONITOR", new VoidReplayConvertor());
    
    RedisStrictCommand<InetSocketAddress> SENTINEL_GET_MASTER_ADDR_BY_NAME = new RedisStrictCommand<>("SENTINEL", "GET-MASTER-ADDR-BY-NAME",
            new InetSocketAddressDecoder());
    RedisCommand<List<Map<String, String>>> SENTINEL_MASTERS = new RedisCommand<List<Map<String, String>>>("SENTINEL", "MASTERS",
            new ListMultiDecoder2(new ListResultReplayDecoder(), new ObjectMapReplayDecoder()));
    RedisCommand<Map<String, String>> SENTINEL_MASTER = new RedisCommand("SENTINEL", "MASTER", new ObjectMapReplayDecoder());
    RedisCommand<List<Map<String, String>>> SENTINEL_SLAVES = new RedisCommand<List<Map<String, String>>>("SENTINEL", "SLAVES",
            new ListMultiDecoder2(new ListResultReplayDecoder(), new ObjectMapReplayDecoder()));
    RedisCommand<List<Map<String, String>>> SENTINEL_SENTINELS = new RedisCommand<List<Map<String, String>>>("SENTINEL", "SENTINELS",
            new ListMultiDecoder2(new ListResultReplayDecoder(), new ObjectMapReplayDecoder()));

    RedisStrictCommand<String> CLUSTER_MYID = new RedisStrictCommand<String>("CLUSTER", "MYID");
    RedisStrictCommand<Void> CLUSTER_ADDSLOTS = new RedisStrictCommand<Void>("CLUSTER", "ADDSLOTS");
    RedisStrictCommand<Void> CLUSTER_REPLICATE = new RedisStrictCommand<Void>("CLUSTER", "REPLICATE");
    RedisStrictCommand<Void> CLUSTER_FORGET = new RedisStrictCommand<Void>("CLUSTER", "FORGET");
    RedisCommand<Object> CLUSTER_SLOTS = new RedisCommand<Object>("CLUSTER", "SLOTS", new SlotsDecoder());
    RedisStrictCommand<Void> CLUSTER_RESET = new RedisStrictCommand<Void>("CLUSTER", "RESET");
    RedisStrictCommand<Void> CLUSTER_DELSLOTS = new RedisStrictCommand<Void>("CLUSTER", "DELSLOTS");
    RedisStrictCommand<Void> CLUSTER_FLUSHSLOTS = new RedisStrictCommand<Void>("CLUSTER", "FLUSHSLOTS");
    RedisStrictCommand<Long> CLUSTER_COUNTFAILUREREPORTS = new RedisStrictCommand<Long>("CLUSTER", "COUNT-FAILURE-REPORTS");
    RedisStrictCommand<Long> CLUSTER_COUNTKEYSINSLOT = new RedisStrictCommand<Long>("CLUSTER", "COUNTKEYSINSLOT");
    RedisStrictCommand<List<String>> CLUSTER_GETKEYSINSLOT = new RedisStrictCommand<List<String>>("CLUSTER", "GETKEYSINSLOT", new StringListReplayDecoder());
    RedisStrictCommand<Void> CLUSTER_SETSLOT = new RedisStrictCommand<Void>("CLUSTER", "SETSLOT");
    RedisStrictCommand<Void> CLUSTER_MEET = new RedisStrictCommand<Void>("CLUSTER", "MEET");
    
    RedisStrictCommand<List<String>> CONFIG_GET = new RedisStrictCommand<List<String>>("CONFIG", "GET", new StringListReplayDecoder());
    RedisStrictCommand<Map<String, String>> CONFIG_GET_MAP = new RedisStrictCommand<>("CONFIG", "GET", new ObjectMapReplayDecoder());
    RedisStrictCommand<Void> CONFIG_SET = new RedisStrictCommand<Void>("CONFIG", "SET", new VoidReplayConvertor());
    RedisStrictCommand<Void> CONFIG_RESETSTAT = new RedisStrictCommand<Void>("CONFIG", "RESETSTAT", new VoidReplayConvertor());
    RedisStrictCommand<List<String>> CLIENT_LIST = new RedisStrictCommand<List<String>>("CLIENT", "LIST", new StringToListConvertor());
    
    RedisStrictCommand<Map<String, String>> INFO_ALL = new RedisStrictCommand<Map<String, String>>("INFO", "ALL", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_DEFAULT = new RedisStrictCommand<Map<String, String>>("INFO", "DEFAULT", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_SERVER = new RedisStrictCommand<Map<String, String>>("INFO", "SERVER", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_CLIENTS = new RedisStrictCommand<Map<String, String>>("INFO", "CLIENTS", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_MEMORY = new RedisStrictCommand<Map<String, String>>("INFO", "MEMORY", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_PERSISTENCE = new RedisStrictCommand<Map<String, String>>("INFO", "PERSISTENCE", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_STATS = new RedisStrictCommand<Map<String, String>>("INFO", "STATS", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_REPLICATION = new RedisStrictCommand<Map<String, String>>("INFO", "REPLICATION", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_CPU = new RedisStrictCommand<Map<String, String>>("INFO", "CPU", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_COMMANDSTATS = new RedisStrictCommand<Map<String, String>>("INFO", "COMMANDSTATS", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_CLUSTER = new RedisStrictCommand<Map<String, String>>("INFO", "CLUSTER", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_KEYSPACE = new RedisStrictCommand<Map<String, String>>("INFO", "KEYSPACE", new StringMapDataDecoder());
}
