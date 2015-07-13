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

import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.decoder.BooleanStatusReplayDecoder;
import org.redisson.client.protocol.decoder.KeyValueObjectDecoder;
import org.redisson.client.protocol.decoder.MapScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.client.protocol.decoder.StringDataDecoder;
import org.redisson.client.protocol.decoder.StringListReplayDecoder;
import org.redisson.client.protocol.decoder.StringMapReplayDecoder;
import org.redisson.client.protocol.decoder.StringReplayDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;

import com.lambdaworks.redis.output.MapScanResult;

public interface RedisCommands {

    RedisCommand<Object> LPOP = new RedisCommand<Object>("LPOP");
    RedisCommand<Object> LINDEX = new RedisCommand<Object>("LINDEX");
    RedisStrictCommand<Long> LLEN = new RedisStrictCommand<Long>("LLEN");

    RedisStrictCommand<Boolean> EXPIRE = new RedisStrictCommand<Boolean>("EXPIRE", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> EXPIREAT = new RedisStrictCommand<Boolean>("EXPIREAT", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> PERSIST = new RedisStrictCommand<Boolean>("PERSIST", new BooleanReplayConvertor());
    RedisStrictCommand<Long> TTL = new RedisStrictCommand<Long>("TTL");

    RedisCommand<Object> BRPOPLPUSH = new RedisCommand<Object>("BRPOPLPUSH");
    RedisCommand<Object> BLPOP = new RedisCommand<Object>("BLPOP", new KeyValueObjectDecoder());

    RedisCommand<Long> RPUSH = new RedisCommand<Long>("RPUSH");

    RedisStrictCommand<Boolean> EVAL_BOOLEAN = new RedisStrictCommand<Boolean>("EVAL", new BooleanReplayConvertor());
    RedisStrictCommand<Long> EVAL_INTEGER = new RedisStrictCommand<Long>("EVAL");
    RedisCommand<List<Object>> EVAL_LIST = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder());
    RedisCommand<Object> EVAL_OBJECT = new RedisCommand<Object>("EVAL");
    RedisCommand<Object> EVAL_MAP_VALUE = new RedisCommand<Object>("EVAL", 4, ValueType.MAP, ValueType.MAP_VALUE);

    RedisStrictCommand<Long> INCR = new RedisStrictCommand<Long>("INCR");
    RedisStrictCommand<Long> INCRBY = new RedisStrictCommand<Long>("INCRBY");
    RedisStrictCommand<Long> DECR = new RedisStrictCommand<Long>("DECR");

    RedisStrictCommand<String> AUTH = new RedisStrictCommand<String>("AUTH", new StringReplayDecoder());
    RedisStrictCommand<String> SELECT = new RedisStrictCommand<String>("SELECT", new StringReplayDecoder());
    RedisStrictCommand<String> CLIENT_SETNAME = new RedisStrictCommand<String>("CLIENT", "SETNAME", new StringReplayDecoder());
    RedisStrictCommand<String> CLIENT_GETNAME = new RedisStrictCommand<String>("CLIENT", "GETNAME", new StringDataDecoder());
    RedisStrictCommand<String> FLUSHDB = new RedisStrictCommand<String>("FLUSHDB", new StringReplayDecoder());

    RedisStrictCommand<List<String>> KEYS = new RedisStrictCommand<List<String>>("KEYS", new StringListReplayDecoder());

    RedisStrictCommand<String> HINCRBYFLOAT = new RedisStrictCommand<String>("HINCRBYFLOAT");
    RedisCommand<MapScanResult<Object, Object>> HSCAN = new RedisCommand<MapScanResult<Object, Object>>("HSCAN", new MapScanResultReplayDecoder(), ValueType.MAP);
    RedisCommand<Map<Object, Object>> HGETALL = new RedisCommand<Map<Object, Object>>("HGETALL", new ObjectMapReplayDecoder(), ValueType.MAP);
    RedisCommand<List<Object>> HVALS = new RedisCommand<List<Object>>("HVALS", new ObjectListReplayDecoder(), ValueType.MAP_VALUE);
    RedisCommand<Boolean> HEXISTS = new RedisCommand<Boolean>("HEXISTS", new BooleanReplayConvertor(), 2, ValueType.MAP_KEY);
    RedisStrictCommand<Long> HLEN = new RedisStrictCommand<Long>("HLEN");
    RedisCommand<List<Object>> HKEYS = new RedisCommand<List<Object>>("HKEYS", new ObjectListReplayDecoder(), ValueType.MAP_KEY);
    RedisCommand<String> HMSET = new RedisCommand<String>("HMSET", new StringReplayDecoder(), 1, ValueType.MAP);
    RedisCommand<List<Object>> HMGET = new RedisCommand<List<Object>>("HMGET", new ObjectListReplayDecoder(), 2, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    RedisCommand<Object> HGET = new RedisCommand<Object>("HGET", 2, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    RedisCommand<Long> HDEL = new RedisStrictCommand<Long>("HDEL", 2, ValueType.MAP_KEY);

    RedisStrictCommand<Boolean> DEL_BOOLEAN = new RedisStrictCommand<Boolean>("DEL", new BooleanReplayConvertor());

    RedisCommand<Object> GET = new RedisCommand<Object>("GET");
    RedisCommand<String> SET = new RedisCommand<String>("SET", new StringReplayDecoder(), 2);
    RedisCommand<String> SETEX = new RedisCommand<String>("SETEX", new StringReplayDecoder(), 2);
    RedisStrictCommand<Boolean> EXISTS = new RedisStrictCommand<Boolean>("EXISTS", new BooleanReplayConvertor());

    RedisStrictCommand<Boolean> RENAMENX = new RedisStrictCommand<Boolean>("RENAMENX", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> RENAME = new RedisStrictCommand<Boolean>("RENAME", new BooleanStatusReplayDecoder());

    RedisCommand<Long> PUBLISH = new RedisCommand<Long>("PUBLISH", 1);

    RedisStrictCommand<PubSubStatusMessage> SUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("SUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> UNSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("UNSUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> PSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("PSUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> PUNSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("PUNSUBSCRIBE", new PubSubStatusDecoder());

    RedisStrictCommand<String> CLUSTER_NODES = new RedisStrictCommand<String>("CLUSTER", "NODES", new StringDataDecoder());

    RedisStrictCommand<List<String>> SENTINEL_GET_MASTER_ADDR_BY_NAME = new RedisStrictCommand<List<String>>("SENTINEL", "GET-MASTER-ADDR-BY-NAME", new StringListReplayDecoder());
    RedisStrictCommand<List<Map<String, String>>> SENTINEL_SLAVES = new RedisStrictCommand<List<Map<String, String>>>("SENTINEL", "SLAVES", new StringMapReplayDecoder());

}
