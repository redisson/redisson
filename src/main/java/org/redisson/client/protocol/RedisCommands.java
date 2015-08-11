/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.convertor.BooleanAmountReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.client.protocol.convertor.KeyValueConvertor;
import org.redisson.client.protocol.convertor.TrueReplayConvertor;
import org.redisson.client.protocol.convertor.VoidReplayConvertor;
import org.redisson.client.protocol.decoder.KeyValueObjectDecoder;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ListScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.MapScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.NestedMultiDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectSetReplayDecoder;
import org.redisson.client.protocol.decoder.StringDataDecoder;
import org.redisson.client.protocol.decoder.StringListReplayDecoder;
import org.redisson.client.protocol.decoder.StringMapDataDecoder;
import org.redisson.client.protocol.decoder.StringMapReplayDecoder;
import org.redisson.client.protocol.decoder.StringReplayDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusDecoder;

public interface RedisCommands {

    RedisCommand<ListScanResult<String>> SCAN = new RedisCommand<ListScanResult<String>>("SCAN", new NestedMultiDecoder(new ObjectListReplayDecoder<String>(), new ListScanResultReplayDecoder()), ValueType.OBJECT);
    RedisStrictCommand<String> RANDOM_KEY = new RedisStrictCommand<String>("RANDOMKEY", new StringDataDecoder());
    RedisStrictCommand<String> PING = new RedisStrictCommand<String>("PING");

    RedisStrictCommand<Void> UNWATCH = new RedisStrictCommand<Void>("UNWATCH", new VoidReplayConvertor());
    RedisStrictCommand<Void> WATCH = new RedisStrictCommand<Void>("WATCH", new VoidReplayConvertor());
    RedisStrictCommand<Void> MULTI = new RedisStrictCommand<Void>("MULTI", new VoidReplayConvertor());
    RedisCommand<List<Object>> EXEC = new RedisCommand<List<Object>>("EXEC", new ObjectListReplayDecoder<Object>());

    RedisCommand<Long> SREM = new RedisCommand<Long>("SREM", 2, ValueType.OBJECTS);
    RedisCommand<Boolean> SADD = new RedisCommand<Boolean>("SADD", new BooleanAmountReplayConvertor(), 2, ValueType.OBJECTS);
    RedisCommand<Object> SPOP_SINGLE = new RedisCommand<Object>("SPOP");
    RedisCommand<Boolean> SADD_SINGLE = new RedisCommand<Boolean>("SADD", new BooleanReplayConvertor(), 2);
    RedisCommand<Boolean> SREM_SINGLE = new RedisCommand<Boolean>("SREM", new BooleanReplayConvertor(), 2);
    RedisCommand<List<Object>> SMEMBERS = new RedisCommand<List<Object>>("SMEMBERS", new ObjectListReplayDecoder<Object>());
    RedisCommand<ListScanResult<Object>> SSCAN = new RedisCommand<ListScanResult<Object>>("SSCAN", new NestedMultiDecoder(new ObjectListReplayDecoder<Object>(), new ListScanResultReplayDecoder()), ValueType.OBJECT);
    RedisCommand<Boolean> SISMEMBER = new RedisCommand<Boolean>("SISMEMBER", new BooleanReplayConvertor(), 2);
    RedisStrictCommand<Integer> SCARD = new RedisStrictCommand<Integer>("SCARD", new IntegerReplayConvertor());

    RedisCommand<Void> LSET = new RedisCommand<Void>("LSET", new VoidReplayConvertor(), 3);
    RedisCommand<Object> LPOP = new RedisCommand<Object>("LPOP");
    RedisCommand<Boolean> LREM_SINGLE = new RedisCommand<Boolean>("LREM", new BooleanReplayConvertor(), 3);
    RedisCommand<Long> LREM = new RedisCommand<Long>("LREM", 3);
    RedisCommand<Object> LINDEX = new RedisCommand<Object>("LINDEX");
    RedisCommand<Object> LINSERT = new RedisCommand<Object>("LINSERT", 3, ValueType.OBJECTS);
    RedisStrictCommand<Integer> LLEN = new RedisStrictCommand<Integer>("LLEN", new IntegerReplayConvertor());
    RedisStrictCommand<Boolean> LTRIM = new RedisStrictCommand<Boolean>("LTRIM", new BooleanReplayConvertor());

    RedisStrictCommand<Boolean> EXPIRE = new RedisStrictCommand<Boolean>("EXPIRE", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> EXPIREAT = new RedisStrictCommand<Boolean>("EXPIREAT", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> PERSIST = new RedisStrictCommand<Boolean>("PERSIST", new BooleanReplayConvertor());
    RedisStrictCommand<Long> TTL = new RedisStrictCommand<Long>("TTL");

    RedisCommand<Object> RPOPLPUSH = new RedisCommand<Object>("RPOPLPUSH");
    RedisCommand<Object> BRPOPLPUSH = new RedisCommand<Object>("BRPOPLPUSH");
    RedisCommand<Object> BLPOP = new RedisCommand<Object>("BLPOP", new KeyValueObjectDecoder());
    RedisCommand<Object> BLPOP_VALUE = new RedisCommand<Object>("BLPOP", new KeyValueObjectDecoder(), new KeyValueConvertor());

    RedisCommand<Boolean> PFADD = new RedisCommand<Boolean>("PFADD", new BooleanReplayConvertor(), 2);
    RedisCommand<Long> PFCOUNT = new RedisCommand<Long>("PFCOUNT");
    RedisStrictCommand<Void> PFMERGE = new RedisStrictCommand<Void>("PFMERGE", new VoidReplayConvertor());

    RedisCommand<Long> RPOP = new RedisCommand<Long>("RPOP");
    RedisCommand<Long> LPUSH = new RedisCommand<Long>("LPUSH");
    RedisCommand<List<Object>> LRANGE = new RedisCommand<List<Object>>("LRANGE", new ObjectListReplayDecoder<Object>());
    RedisCommand<Long> RPUSH = new RedisCommand<Long>("RPUSH", 2, ValueType.OBJECTS);
    RedisCommand<Boolean> RPUSH_BOOLEAN = new RedisCommand<Boolean>("RPUSH", new TrueReplayConvertor(), 2, ValueType.OBJECTS);

    RedisStrictCommand<String> SCRIPT_LOAD = new RedisStrictCommand<String>("SCRIPT", "LOAD", new StringDataDecoder());
    RedisStrictCommand<Boolean> SCRIPT_KILL = new RedisStrictCommand<Boolean>("SCRIPT", "KILL", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> SCRIPT_FLUSH = new RedisStrictCommand<Boolean>("SCRIPT", "FLUSH", new BooleanReplayConvertor());
    RedisStrictCommand<List<Boolean>> SCRIPT_EXISTS = new RedisStrictCommand<List<Boolean>>("SCRIPT", "EXISTS", new ObjectListReplayDecoder<Boolean>(), new BooleanReplayConvertor());

    RedisStrictCommand<Boolean> EVAL_BOOLEAN = new RedisStrictCommand<Boolean>("EVAL", new BooleanReplayConvertor());
    RedisStrictCommand<String> EVAL_STRING = new RedisStrictCommand<String>("EVAL", new StringReplayDecoder());
    RedisStrictCommand<Long> EVAL_INTEGER = new RedisStrictCommand<Long>("EVAL");
    RedisCommand<List<Object>> EVAL_LIST = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<Object>());
    RedisCommand<Object> EVAL_OBJECT = new RedisCommand<Object>("EVAL");
    RedisCommand<Object> EVAL_MAP_VALUE = new RedisCommand<Object>("EVAL", ValueType.MAP_VALUE);
    RedisCommand<List<Object>> EVAL_MAP_VALUE_LIST = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<Object>(), ValueType.MAP_VALUE);

    RedisStrictCommand<Long> INCR = new RedisStrictCommand<Long>("INCR");
    RedisStrictCommand<Long> INCRBY = new RedisStrictCommand<Long>("INCRBY");
    RedisStrictCommand<Long> DECR = new RedisStrictCommand<Long>("DECR");

    RedisStrictCommand<String> AUTH = new RedisStrictCommand<String>("AUTH", new StringReplayDecoder());
    RedisStrictCommand<String> SELECT = new RedisStrictCommand<String>("SELECT", new StringReplayDecoder());
    RedisStrictCommand<Boolean> CLIENT_SETNAME = new RedisStrictCommand<Boolean>("CLIENT", "SETNAME", new BooleanReplayConvertor());
    RedisStrictCommand<String> CLIENT_GETNAME = new RedisStrictCommand<String>("CLIENT", "GETNAME", new StringDataDecoder());
    RedisStrictCommand<Void> FLUSHDB = new RedisStrictCommand<Void>("FLUSHDB", new VoidReplayConvertor());
    RedisStrictCommand<Void> FLUSHALL = new RedisStrictCommand<Void>("FLUSHALL", new VoidReplayConvertor());

    RedisStrictCommand<List<String>> KEYS = new RedisStrictCommand<List<String>>("KEYS", new StringListReplayDecoder());

    RedisCommand<Boolean> HSET = new RedisCommand<Boolean>("HSET", new BooleanReplayConvertor(), 2, ValueType.MAP);
    RedisStrictCommand<String> HINCRBYFLOAT = new RedisStrictCommand<String>("HINCRBYFLOAT");
    RedisCommand<MapScanResult<Object, Object>> HSCAN = new RedisCommand<MapScanResult<Object, Object>>("HSCAN", new NestedMultiDecoder(new ObjectMapReplayDecoder(), new MapScanResultReplayDecoder()), ValueType.MAP);
    RedisCommand<Map<Object, Object>> HGETALL = new RedisCommand<Map<Object, Object>>("HGETALL", new ObjectMapReplayDecoder(), ValueType.MAP);
    RedisCommand<List<Object>> HVALS = new RedisCommand<List<Object>>("HVALS", new ObjectListReplayDecoder<Object>(), ValueType.MAP_VALUE);
    RedisCommand<Boolean> HEXISTS = new RedisCommand<Boolean>("HEXISTS", new BooleanReplayConvertor(), 2, ValueType.MAP_KEY);
    RedisStrictCommand<Integer> HLEN = new RedisStrictCommand<Integer>("HLEN", new IntegerReplayConvertor());
    RedisCommand<Set<Object>> HKEYS = new RedisCommand<Set<Object>>("HKEYS", new ObjectSetReplayDecoder(), ValueType.MAP_KEY);
    RedisCommand<String> HMSET = new RedisCommand<String>("HMSET", new StringReplayDecoder(), 1, ValueType.MAP);
    RedisCommand<List<Object>> HMGET = new RedisCommand<List<Object>>("HMGET", new ObjectListReplayDecoder<Object>(), 2, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    RedisCommand<Object> HGET = new RedisCommand<Object>("HGET", 2, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    RedisCommand<Long> HDEL = new RedisStrictCommand<Long>("HDEL", 2, ValueType.MAP_KEY);

    RedisStrictCommand<Long> DEL = new RedisStrictCommand<Long>("DEL");
    RedisStrictCommand<Boolean> DEL_SINGLE = new RedisStrictCommand<Boolean>("DEL", new BooleanReplayConvertor());

    RedisCommand<Object> GET = new RedisCommand<Object>("GET");
    RedisCommand<Void> SET = new RedisCommand<Void>("SET", new VoidReplayConvertor(), 2);
    RedisCommand<Boolean> SETNX = new RedisCommand<Boolean>("SETNX", new BooleanReplayConvertor(), 2);
    RedisCommand<Void> SETEX = new RedisCommand<Void>("SETEX", new VoidReplayConvertor(), 3);
    RedisStrictCommand<Boolean> EXISTS = new RedisStrictCommand<Boolean>("EXISTS", new BooleanReplayConvertor());

    RedisStrictCommand<Boolean> RENAMENX = new RedisStrictCommand<Boolean>("RENAMENX", new BooleanReplayConvertor());
    RedisStrictCommand<Void> RENAME = new RedisStrictCommand<Void>("RENAME", new VoidReplayConvertor());
    RedisStrictCommand<Boolean> MOVE = new RedisStrictCommand<Boolean>("MOVE", new BooleanReplayConvertor());
    RedisStrictCommand<Void> MIGRATE = new RedisStrictCommand<Void>("MIGRATE", new VoidReplayConvertor());

    RedisCommand<Long> PUBLISH = new RedisCommand<Long>("PUBLISH", 2);

    RedisCommand<Object> SUBSCRIBE = new RedisCommand<Object>("SUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> UNSUBSCRIBE = new RedisCommand<Object>("UNSUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> PSUBSCRIBE = new RedisCommand<Object>("PSUBSCRIBE", new PubSubStatusDecoder());
    RedisCommand<Object> PUNSUBSCRIBE = new RedisCommand<Object>("PUNSUBSCRIBE", new PubSubStatusDecoder());

    RedisStrictCommand<String> CLUSTER_NODES = new RedisStrictCommand<String>("CLUSTER", "NODES", new StringDataDecoder());
    RedisStrictCommand<Map<String, String>> CLUSTER_INFO = new RedisStrictCommand<Map<String, String>>("CLUSTER", "INFO", new StringMapDataDecoder());

    RedisStrictCommand<List<String>> SENTINEL_GET_MASTER_ADDR_BY_NAME = new RedisStrictCommand<List<String>>("SENTINEL", "GET-MASTER-ADDR-BY-NAME", new StringListReplayDecoder());
    RedisStrictCommand<List<Map<String, String>>> SENTINEL_SLAVES = new RedisStrictCommand<List<Map<String, String>>>("SENTINEL", "SLAVES", new StringMapReplayDecoder());

}
