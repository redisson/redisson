/**
 * Copyright 2016 Nikita Koksharov
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.convertor.BitSetReplayConvertor;
import org.redisson.client.protocol.convertor.BitsSizeReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanAmountReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanNotNullReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanNullReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanNumberReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.DoubleReplayConvertor;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.client.protocol.convertor.KeyValueConvertor;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.client.protocol.convertor.TrueReplayConvertor;
import org.redisson.client.protocol.convertor.TypeConvertor;
import org.redisson.client.protocol.convertor.VoidReplayConvertor;
import org.redisson.client.protocol.decoder.ClusterNodesDecoder;
import org.redisson.client.protocol.decoder.FlatNestedMultiDecoder;
import org.redisson.client.protocol.decoder.KeyValueObjectDecoder;
import org.redisson.client.protocol.decoder.ListResultReplayDecoder;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ListScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.MapScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.NestedMultiDecoder;
import org.redisson.client.protocol.decoder.ObjectFirstResultReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectMapEntryReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectSetReplayDecoder;
import org.redisson.client.protocol.decoder.ScoredSortedSetReplayDecoder;
import org.redisson.client.protocol.decoder.ScoredSortedSetScanDecoder;
import org.redisson.client.protocol.decoder.ScoredSortedSetScanReplayDecoder;
import org.redisson.client.protocol.decoder.SlotsDecoder;
import org.redisson.client.protocol.decoder.StringDataDecoder;
import org.redisson.client.protocol.decoder.StringListReplayDecoder;
import org.redisson.client.protocol.decoder.StringMapDataDecoder;
import org.redisson.client.protocol.decoder.StringReplayDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusDecoder;
import org.redisson.cluster.ClusterNodeInfo;
import org.redisson.core.RType;

public interface RedisCommands {

    RedisStrictCommand<Long> GEOADD = new RedisStrictCommand<Long>("GEOADD", 4);
    RedisStrictCommand<Long> GEOADD_ENTRIES = new RedisStrictCommand<Long>("GEOADD", 2, ValueType.OBJECTS);
    RedisCommand<Double> GEODIST = new RedisCommand<Double>("GEODIST", new DoubleReplayConvertor(), 2, Arrays.asList(ValueType.OBJECT, ValueType.OBJECT, ValueType.STRING));
    RedisCommand<List<Object>> GEORADIUS = new RedisCommand<List<Object>>("GEORADIUS", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> GEORADIUSBYMEMBER = new RedisCommand<List<Object>>("GEORADIUSBYMEMBER", new ObjectListReplayDecoder<Object>(), 2);
    
    RedisStrictCommand<Integer> KEYSLOT = new RedisStrictCommand<Integer>("CLUSTER", "KEYSLOT", new IntegerReplayConvertor());
    RedisStrictCommand<RType> TYPE = new RedisStrictCommand<RType>("TYPE", new TypeConvertor());

    RedisStrictCommand<Boolean> GETBIT = new RedisStrictCommand<Boolean>("GETBIT", new BooleanReplayConvertor());
    RedisStrictCommand<Integer> BITS_SIZE = new RedisStrictCommand<Integer>("STRLEN", new BitsSizeReplayConvertor());
    RedisStrictCommand<Integer> STRLEN = new RedisStrictCommand<Integer>("STRLEN", new IntegerReplayConvertor());
    RedisStrictCommand<Long> BITCOUNT = new RedisStrictCommand<Long>("BITCOUNT");
    RedisStrictCommand<Integer> BITPOS = new RedisStrictCommand<Integer>("BITPOS", new IntegerReplayConvertor());
    RedisStrictCommand<Void> SETBIT_VOID = new RedisStrictCommand<Void>("SETBIT", new VoidReplayConvertor());
    RedisStrictCommand<Boolean> SETBIT = new RedisStrictCommand<Boolean>("SETBIT", new BitSetReplayConvertor());
    RedisStrictCommand<Void> BITOP = new RedisStrictCommand<Void>("BITOP", new VoidReplayConvertor());

    RedisStrictCommand<Void> ASKING = new RedisStrictCommand<Void>("ASKING", new VoidReplayConvertor());
    RedisStrictCommand<Void> READONLY = new RedisStrictCommand<Void>("READONLY", new VoidReplayConvertor());

    RedisCommand<Boolean> ZADD_BOOL = new RedisCommand<Boolean>("ZADD", new BooleanAmountReplayConvertor(), 3);
    RedisCommand<Boolean> ZADD_NX_BOOL = new RedisCommand<Boolean>("ZADD", new BooleanAmountReplayConvertor(), 4);
    RedisCommand<Boolean> ZADD_BOOL_RAW = new RedisCommand<Boolean>("ZADD", new BooleanAmountReplayConvertor());
    RedisCommand<Boolean> ZADD_RAW = new RedisCommand<Boolean>("ZADD");
    RedisCommand<Long> ZADD = new RedisCommand<Long>("ZADD");
    RedisCommand<Boolean> ZREM = new RedisCommand<Boolean>("ZREM", new BooleanAmountReplayConvertor(), 2, ValueType.OBJECTS);
    RedisStrictCommand<Integer> ZCARD_INT = new RedisStrictCommand<Integer>("ZCARD", new IntegerReplayConvertor());
    RedisStrictCommand<Long> ZCARD = new RedisStrictCommand<Long>("ZCARD");
    RedisStrictCommand<Long> ZCOUNT = new RedisStrictCommand<Long>("ZCOUNT");
    RedisStrictCommand<Integer> ZLEXCOUNT = new RedisStrictCommand<Integer>("ZLEXCOUNT", new IntegerReplayConvertor());
    RedisCommand<Boolean> ZSCORE_CONTAINS = new RedisCommand<Boolean>("ZSCORE", new BooleanNotNullReplayConvertor(), 2);
    RedisStrictCommand<Double> ZSCORE = new RedisStrictCommand<Double>("ZSCORE", new DoubleReplayConvertor(), 2);
    RedisCommand<Integer> ZRANK_INT = new RedisCommand<Integer>("ZRANK", new IntegerReplayConvertor(), 2);
    RedisCommand<Integer> ZREVRANK_INT = new RedisCommand<Integer>("ZREVRANK", new IntegerReplayConvertor(), 2);
    RedisStrictCommand<Long> ZRANK = new RedisStrictCommand<Long>("ZRANK", 2);
    RedisCommand<Object> ZRANGE_SINGLE = new RedisCommand<Object>("ZRANGE", new ObjectFirstResultReplayDecoder<Object>());
    RedisCommand<List<Object>> ZRANGE = new RedisCommand<List<Object>>("ZRANGE", new ObjectListReplayDecoder<Object>());
    RedisStrictCommand<Integer> ZREMRANGEBYRANK = new RedisStrictCommand<Integer>("ZREMRANGEBYRANK", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> ZREMRANGEBYSCORE = new RedisStrictCommand<Integer>("ZREMRANGEBYSCORE", new IntegerReplayConvertor());
    RedisStrictCommand<Integer> ZREMRANGEBYLEX = new RedisStrictCommand<Integer>("ZREMRANGEBYLEX", new IntegerReplayConvertor());
    RedisCommand<List<Object>> ZRANGEBYLEX = new RedisCommand<List<Object>>("ZRANGEBYLEX", new ObjectListReplayDecoder<Object>());
    RedisCommand<Set<Object>> ZRANGEBYSCORE = new RedisCommand<Set<Object>>("ZRANGEBYSCORE", new ObjectSetReplayDecoder<Object>());
    RedisCommand<List<Object>> ZRANGEBYSCORE_LIST = new RedisCommand<List<Object>>("ZRANGEBYSCORE", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<Object>> ZREVRANGEBYSCORE = new RedisCommand<List<Object>>("ZREVRANGEBYSCORE", new ObjectListReplayDecoder<Object>());
    RedisCommand<List<ScoredEntry<Object>>> ZREVRANGEBYSCORE_ENTRY = new RedisCommand<List<ScoredEntry<Object>>>("ZREVRANGEBYSCORE", new ScoredSortedSetReplayDecoder<Object>());
    RedisCommand<List<ScoredEntry<Object>>> ZRANGE_ENTRY = new RedisCommand<List<ScoredEntry<Object>>>("ZRANGE", new ScoredSortedSetReplayDecoder<Object>());
    RedisCommand<List<ScoredEntry<Object>>> ZRANGEBYSCORE_ENTRY = new RedisCommand<List<ScoredEntry<Object>>>("ZRANGEBYSCORE", new ScoredSortedSetReplayDecoder<Object>());
    RedisCommand<ListScanResult<Object>> ZSCAN = new RedisCommand<ListScanResult<Object>>("ZSCAN", new NestedMultiDecoder(new ScoredSortedSetScanDecoder<Object>(), new ScoredSortedSetScanReplayDecoder()), ValueType.OBJECT);
    RedisStrictCommand<Double> ZINCRBY = new RedisStrictCommand<Double>("ZINCRBY", new DoubleReplayConvertor(), 4);

    RedisCommand<ListScanResult<String>> SCAN = new RedisCommand<ListScanResult<String>>("SCAN", new NestedMultiDecoder(new ObjectListReplayDecoder<String>(), new ListScanResultReplayDecoder()), ValueType.OBJECT);
    RedisStrictCommand<String> RANDOM_KEY = new RedisStrictCommand<String>("RANDOMKEY", new StringDataDecoder());
    RedisStrictCommand<String> PING = new RedisStrictCommand<String>("PING");

    RedisStrictCommand<Void> UNWATCH = new RedisStrictCommand<Void>("UNWATCH", new VoidReplayConvertor());
    RedisStrictCommand<Void> WATCH = new RedisStrictCommand<Void>("WATCH", new VoidReplayConvertor());
    RedisStrictCommand<Void> MULTI = new RedisStrictCommand<Void>("MULTI", new VoidReplayConvertor());
    RedisCommand<List<Object>> EXEC = new RedisCommand<List<Object>>("EXEC", new ObjectListReplayDecoder<Object>());

    RedisCommand<Boolean> SADD_BOOL = new RedisCommand<Boolean>("SADD", new BooleanAmountReplayConvertor(), 2, ValueType.OBJECTS);
    RedisStrictCommand<Long> SADD = new RedisStrictCommand<Long>("SADD", 2, ValueType.OBJECTS);
    RedisCommand<Object> SPOP_SINGLE = new RedisCommand<Object>("SPOP");
    RedisCommand<Boolean> SADD_SINGLE = new RedisCommand<Boolean>("SADD", new BooleanReplayConvertor(), 2);
    RedisCommand<Boolean> SREM_SINGLE = new RedisCommand<Boolean>("SREM", new BooleanAmountReplayConvertor(), 2, ValueType.OBJECTS);
    RedisCommand<Boolean> SMOVE = new RedisCommand<Boolean>("SMOVE", new BooleanReplayConvertor(), 3);
    RedisCommand<Set<Object>> SMEMBERS = new RedisCommand<Set<Object>>("SMEMBERS", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Object> SRANDMEMBER_SINGLE = new RedisCommand<Object>("SRANDMEMBER");
    RedisCommand<ListScanResult<Object>> SSCAN = new RedisCommand<ListScanResult<Object>>("SSCAN", new NestedMultiDecoder(new ObjectListReplayDecoder<Object>(), new ListScanResultReplayDecoder()), ValueType.OBJECT);
    RedisCommand<ListScanResult<Object>> EVAL_SSCAN = new RedisCommand<ListScanResult<Object>>("EVAL", new NestedMultiDecoder(new ObjectListReplayDecoder<Object>(), new ListScanResultReplayDecoder()), ValueType.OBJECT);
    RedisCommand<ListScanResult<Object>> EVAL_ZSCAN = new RedisCommand<ListScanResult<Object>>("EVAL", new NestedMultiDecoder(new ObjectListReplayDecoder<Object>(), new ListScanResultReplayDecoder()), ValueType.OBJECT);
    RedisCommand<Boolean> SISMEMBER = new RedisCommand<Boolean>("SISMEMBER", new BooleanReplayConvertor(), 2);
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

    RedisCommand<Void> LSET = new RedisCommand<Void>("LSET", new VoidReplayConvertor(), 3);
    RedisCommand<Object> LPOP = new RedisCommand<Object>("LPOP");
    RedisCommand<Boolean> LREM_SINGLE = new RedisCommand<Boolean>("LREM", new BooleanReplayConvertor(), 3);
    RedisStrictCommand<Long> LREM = new RedisStrictCommand<Long>("LREM", 3);
    RedisCommand<Object> LINDEX = new RedisCommand<Object>("LINDEX");
    RedisCommand<Object> LINSERT = new RedisCommand<Object>("LINSERT", 3, ValueType.OBJECTS);
    RedisCommand<Integer> LINSERT_INT = new RedisCommand<Integer>("LINSERT", new IntegerReplayConvertor(), 3, ValueType.OBJECTS);
    RedisStrictCommand<Integer> LLEN_INT = new RedisStrictCommand<Integer>("LLEN", new IntegerReplayConvertor());
    RedisStrictCommand<Long> LLEN = new RedisStrictCommand<Long>("LLEN");
    RedisStrictCommand<Void> LTRIM = new RedisStrictCommand<Void>("LTRIM", new VoidReplayConvertor());

    RedisStrictCommand<Boolean> PEXPIRE = new RedisStrictCommand<Boolean>("PEXPIRE", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> PEXPIREAT = new RedisStrictCommand<Boolean>("PEXPIREAT", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> PERSIST = new RedisStrictCommand<Boolean>("PERSIST", new BooleanReplayConvertor());
    RedisStrictCommand<Long> PTTL = new RedisStrictCommand<Long>("PTTL");

    RedisCommand<Object> RPOPLPUSH = new RedisCommand<Object>("RPOPLPUSH");
    RedisCommand<Object> BRPOPLPUSH = new RedisCommand<Object>("BRPOPLPUSH");
    RedisCommand<Object> BLPOP_VALUE = new RedisCommand<Object>("BLPOP", new KeyValueObjectDecoder(), new KeyValueConvertor());
    RedisCommand<Object> BRPOP_VALUE = new RedisCommand<Object>("BRPOP", new KeyValueObjectDecoder(), new KeyValueConvertor());

    RedisCommand<Boolean> PFADD = new RedisCommand<Boolean>("PFADD", new BooleanReplayConvertor(), 2);
    RedisStrictCommand<Long> PFCOUNT = new RedisStrictCommand<Long>("PFCOUNT");
    RedisStrictCommand<Void> PFMERGE = new RedisStrictCommand<Void>("PFMERGE", new VoidReplayConvertor());

    RedisStrictCommand<Long> RPOP = new RedisStrictCommand<Long>("RPOP");
    RedisStrictCommand<Long> LPUSH = new RedisStrictCommand<Long>("LPUSH", 2, ValueType.OBJECTS);
    RedisCommand<Boolean> LPUSH_BOOLEAN = new RedisCommand<Boolean>("LPUSH", new TrueReplayConvertor(), 2, ValueType.OBJECTS);
    RedisStrictCommand<Void> LPUSH_VOID = new RedisStrictCommand<Void>("LPUSH", new VoidReplayConvertor(), 2);
    RedisCommand<List<Object>> LRANGE = new RedisCommand<List<Object>>("LRANGE", new ObjectListReplayDecoder<Object>());
    RedisCommand<Long> RPUSH = new RedisCommand<Long>("RPUSH", 2, ValueType.OBJECTS);
    RedisCommand<Boolean> RPUSH_BOOLEAN = new RedisCommand<Boolean>("RPUSH", new TrueReplayConvertor(), 2, ValueType.OBJECTS);

    RedisStrictCommand<String> SCRIPT_LOAD = new RedisStrictCommand<String>("SCRIPT", "LOAD", new StringDataDecoder());
    RedisStrictCommand<Boolean> SCRIPT_KILL = new RedisStrictCommand<Boolean>("SCRIPT", "KILL", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> SCRIPT_FLUSH = new RedisStrictCommand<Boolean>("SCRIPT", "FLUSH", new BooleanReplayConvertor());
    RedisStrictCommand<List<Boolean>> SCRIPT_EXISTS = new RedisStrictCommand<List<Boolean>>("SCRIPT", "EXISTS", new ObjectListReplayDecoder<Boolean>(), new BooleanReplayConvertor());

    RedisStrictCommand<Boolean> EVAL_BOOLEAN_AMOUNT = new RedisStrictCommand<Boolean>("EVAL", new BooleanAmountReplayConvertor());
    RedisStrictCommand<Boolean> EVAL_BOOLEAN = new RedisStrictCommand<Boolean>("EVAL", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> EVAL_NULL_BOOLEAN = new RedisStrictCommand<Boolean>("EVAL", new BooleanNullReplayConvertor());
    RedisCommand<Boolean> EVAL_BOOLEAN_WITH_VALUES = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4, ValueType.OBJECTS);
    RedisStrictCommand<String> EVAL_STRING = new RedisStrictCommand<String>("EVAL", new StringReplayDecoder());
    RedisStrictCommand<Integer> EVAL_INTEGER = new RedisStrictCommand<Integer>("EVAL", new IntegerReplayConvertor());
    RedisStrictCommand<Long> EVAL_LONG = new RedisStrictCommand<Long>("EVAL");
    RedisStrictCommand<Void> EVAL_VOID = new RedisStrictCommand<Void>("EVAL", new VoidReplayConvertor());
    RedisCommand<List<Object>> EVAL_LIST = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<Object>());
    RedisCommand<Set<Object>> EVAL_SET = new RedisCommand<Set<Object>>("EVAL", new ObjectSetReplayDecoder<Object>());
    RedisCommand<Object> EVAL_OBJECT = new RedisCommand<Object>("EVAL");
    RedisCommand<Object> EVAL_MAP_VALUE = new RedisCommand<Object>("EVAL", ValueType.MAP_VALUE);
    RedisCommand<Set<Entry<Object, Object>>> EVAL_MAP_ENTRY = new RedisCommand<Set<Entry<Object, Object>>>("EVAL", new ObjectMapEntryReplayDecoder(), ValueType.MAP);
    RedisCommand<List<Object>> EVAL_MAP_VALUE_LIST = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<Object>(), ValueType.MAP_VALUE);

    RedisStrictCommand<Long> INCR = new RedisStrictCommand<Long>("INCR");
    RedisStrictCommand<Long> INCRBY = new RedisStrictCommand<Long>("INCRBY");
    RedisStrictCommand<Double> INCRBYFLOAT = new RedisStrictCommand<Double>("INCRBYFLOAT", new DoubleReplayConvertor());
    RedisStrictCommand<Long> DECR = new RedisStrictCommand<Long>("DECR");

    RedisStrictCommand<Void> AUTH = new RedisStrictCommand<Void>("AUTH", new VoidReplayConvertor());
    RedisStrictCommand<Void> SELECT = new RedisStrictCommand<Void>("SELECT", new VoidReplayConvertor());
    RedisStrictCommand<Boolean> CLIENT_SETNAME = new RedisStrictCommand<Boolean>("CLIENT", "SETNAME", new BooleanReplayConvertor());
    RedisStrictCommand<String> CLIENT_GETNAME = new RedisStrictCommand<String>("CLIENT", "GETNAME", new StringDataDecoder());
    RedisStrictCommand<Void> FLUSHDB = new RedisStrictCommand<Void>("FLUSHDB", new VoidReplayConvertor());
    RedisStrictCommand<Void> FLUSHALL = new RedisStrictCommand<Void>("FLUSHALL", new VoidReplayConvertor());

    RedisStrictCommand<List<String>> KEYS = new RedisStrictCommand<List<String>>("KEYS", new StringListReplayDecoder());
    RedisCommand<List<Object>> MGET = new RedisCommand<List<Object>>("MGET", new ObjectListReplayDecoder<Object>());
    RedisStrictCommand<Void> MSET = new RedisStrictCommand<Void>("MSET", new VoidReplayConvertor());
    RedisStrictCommand<Boolean> MSETNX = new RedisStrictCommand<Boolean>("MSETNX", new BooleanReplayConvertor());

    RedisCommand<Boolean> HSETNX = new RedisCommand<Boolean>("HSETNX", new BooleanReplayConvertor(), 2, ValueType.MAP);
    RedisCommand<Boolean> HSET = new RedisCommand<Boolean>("HSET", new BooleanReplayConvertor(), 2, ValueType.MAP);
    RedisCommand<MapScanResult<Object, Object>> HSCAN = new RedisCommand<MapScanResult<Object, Object>>("HSCAN", new NestedMultiDecoder(new ObjectMapReplayDecoder(), new MapScanResultReplayDecoder()), ValueType.MAP);
    RedisCommand<Map<Object, Object>> HGETALL = new RedisCommand<Map<Object, Object>>("HGETALL", new ObjectMapReplayDecoder(), ValueType.MAP);
    RedisCommand<Set<Entry<Object, Object>>> HGETALL_ENTRY = new RedisCommand<Set<Entry<Object, Object>>>("HGETALL", new ObjectMapEntryReplayDecoder(), ValueType.MAP);
    RedisCommand<List<Object>> HVALS = new RedisCommand<List<Object>>("HVALS", new ObjectListReplayDecoder<Object>(), ValueType.MAP_VALUE);
    RedisCommand<Boolean> HEXISTS = new RedisCommand<Boolean>("HEXISTS", new BooleanReplayConvertor(), 2, ValueType.MAP_KEY);
    RedisStrictCommand<Integer> HLEN = new RedisStrictCommand<Integer>("HLEN", new IntegerReplayConvertor());
    RedisStrictCommand<Long> HLEN_LONG = new RedisStrictCommand<Long>("HLEN");
    RedisCommand<Set<Object>> HKEYS = new RedisCommand<Set<Object>>("HKEYS", new ObjectSetReplayDecoder(), ValueType.MAP_KEY);
    RedisCommand<Void> HMSET = new RedisCommand<Void>("HMSET", new VoidReplayConvertor(), 2, ValueType.MAP);
    RedisCommand<List<Object>> HMGET = new RedisCommand<List<Object>>("HMGET", new ObjectListReplayDecoder<Object>(), 2, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    RedisCommand<Object> HGET = new RedisCommand<Object>("HGET", 2, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    RedisCommand<Long> HDEL = new RedisStrictCommand<Long>("HDEL", 2, ValueType.MAP_KEY);

    RedisStrictCommand<Long> DEL = new RedisStrictCommand<Long>("DEL");
    RedisStrictCommand<Long> DBSIZE = new RedisStrictCommand<Long>("DBSIZE");
    RedisStrictCommand<Boolean> DEL_BOOL = new RedisStrictCommand<Boolean>("DEL", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> DEL_OBJECTS = new RedisStrictCommand<Boolean>("DEL", new BooleanAmountReplayConvertor());
    RedisStrictCommand<Void> DEL_VOID = new RedisStrictCommand<Void>("DEL", new VoidReplayConvertor());

    RedisCommand<Object> GET = new RedisCommand<Object>("GET");
    RedisStrictCommand<Long> GET_LONG = new RedisStrictCommand<Long>("GET", new LongReplayConvertor());
    RedisCommand<Object> GETSET = new RedisCommand<Object>("GETSET", 2);
    RedisCommand<Void> SET = new RedisCommand<Void>("SET", new VoidReplayConvertor(), 2);
    RedisCommand<Boolean> SETPXNX = new RedisCommand<Boolean>("SET", new BooleanNotNullReplayConvertor(), 2);
    RedisCommand<Boolean> SETNX = new RedisCommand<Boolean>("SETNX", new BooleanReplayConvertor(), 2);
    RedisCommand<Void> SETEX = new RedisCommand<Void>("SETEX", new VoidReplayConvertor(), 3);
    RedisStrictCommand<Boolean> EXISTS = new RedisStrictCommand<Boolean>("EXISTS", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> NOT_EXISTS = new RedisStrictCommand<Boolean>("EXISTS", new BooleanNumberReplayConvertor(1L));

    RedisStrictCommand<Boolean> RENAMENX = new RedisStrictCommand<Boolean>("RENAMENX", new BooleanReplayConvertor());
    RedisStrictCommand<Void> RENAME = new RedisStrictCommand<Void>("RENAME", new VoidReplayConvertor());
    RedisStrictCommand<Boolean> MOVE = new RedisStrictCommand<Boolean>("MOVE", new BooleanReplayConvertor());
    RedisStrictCommand<Void> MIGRATE = new RedisStrictCommand<Void>("MIGRATE", new VoidReplayConvertor());

    RedisStrictCommand<Long> PUBLISH = new RedisStrictCommand<Long>("PUBLISH", 2);

    RedisCommand<Object> SUBSCRIBE = new RedisCommand<Object>("SUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> UNSUBSCRIBE = new RedisCommand<Object>("UNSUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> PSUBSCRIBE = new RedisCommand<Object>("PSUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> PUNSUBSCRIBE = new RedisCommand<Object>("PUNSUBSCRIBE", new PubSubStatusDecoder());

    RedisStrictCommand<List<ClusterNodeInfo>> CLUSTER_NODES = new RedisStrictCommand<List<ClusterNodeInfo>>("CLUSTER", "NODES", new ClusterNodesDecoder());
    RedisStrictCommand<Map<String, String>> CLUSTER_INFO = new RedisStrictCommand<Map<String, String>>("CLUSTER", "INFO", new StringMapDataDecoder());

    RedisStrictCommand<List<String>> SENTINEL_GET_MASTER_ADDR_BY_NAME = new RedisStrictCommand<List<String>>("SENTINEL", "GET-MASTER-ADDR-BY-NAME", new StringListReplayDecoder());
    RedisCommand<List<Map<String, String>>> SENTINEL_SLAVES = new RedisCommand<List<Map<String, String>>>("SENTINEL", "SLAVES",
            new NestedMultiDecoder(new ObjectMapReplayDecoder(), new ListResultReplayDecoder(), true), ValueType.OBJECT
            );

    RedisStrictCommand<Void> CLUSTER_ADDSLOTS = new RedisStrictCommand<Void>("CLUSTER", "ADDSLOTS");
    RedisStrictCommand<Void> CLUSTER_REPLICATE = new RedisStrictCommand<Void>("CLUSTER", "REPLICATE");
    RedisStrictCommand<Void> CLUSTER_FORGET = new RedisStrictCommand<Void>("CLUSTER", "FORGET");
    RedisCommand<Object> CLUSTER_SLOTS = new RedisCommand<Object>("CLUSTER", "SLOTS", new SlotsDecoder(), ValueType.OBJECT);
    RedisStrictCommand<Void> CLUSTER_RESET = new RedisStrictCommand<Void>("CLUSTER", "RESET");
    RedisStrictCommand<List<String>> CLUSTER_GETKEYSINSLOT = new RedisStrictCommand<List<String>>("CLUSTER", "GETKEYSINSLOT", new StringListReplayDecoder());
    RedisStrictCommand<Void> CLUSTER_SETSLOT = new RedisStrictCommand<Void>("CLUSTER", "SETSLOT");
    RedisStrictCommand<Void> CLUSTER_MEET = new RedisStrictCommand<Void>("CLUSTER", "MEET");
    RedisStrictCommand<Map<String, String>> INFO_KEYSPACE = new RedisStrictCommand<Map<String, String>>("INFO", "KEYSPACE", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_CLUSTER = new RedisStrictCommand<Map<String, String>>("INFO", "CLUSTER", new StringMapDataDecoder());
    RedisStrictCommand<String> INFO_REPLICATION = new RedisStrictCommand<String>("INFO", "replication", new StringDataDecoder());
    RedisStrictCommand<Map<String, String>> INFO_PERSISTENCE = new RedisStrictCommand<Map<String, String>>("INFO", "persistence", new StringMapDataDecoder());
    RedisStrictCommand<Map<String, String>> SERVER_INFO = new RedisStrictCommand<Map<String, String>>("INFO", "SERVER", new StringMapDataDecoder());
}
