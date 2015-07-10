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

import org.redisson.client.protocol.decoder.BooleanReplayDecoder;
import org.redisson.client.protocol.decoder.StringDataDecoder;
import org.redisson.client.protocol.decoder.StringListObjectReplayDecoder;
import org.redisson.client.protocol.decoder.StringListReplayDecoder;
import org.redisson.client.protocol.decoder.StringMapReplayDecoder;
import org.redisson.client.protocol.decoder.StringReplayDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;

public interface RedisCommands {

    RedisStrictCommand<Boolean> EVAL_BOOLEAN = new RedisStrictCommand<Boolean>("EVAL", new BooleanReplayConvertor());
    RedisStrictCommand<Long> EVAL_INTEGER = new RedisStrictCommand<Long>("EVAL");

    RedisStrictCommand<Long> INCR = new RedisStrictCommand<Long>("INCR");
    RedisStrictCommand<Long> INCRBY = new RedisStrictCommand<Long>("INCRBY");
    RedisStrictCommand<Long> DECR = new RedisStrictCommand<Long>("DECR");

    RedisStrictCommand<String> AUTH = new RedisStrictCommand<String>("AUTH", new StringReplayDecoder());
    RedisStrictCommand<String> SELECT = new RedisStrictCommand<String>("SELECT", new StringReplayDecoder());
    RedisStrictCommand<String> CLIENT_SETNAME = new RedisStrictCommand<String>("CLIENT", "SETNAME", new StringReplayDecoder());
    RedisStrictCommand<String> CLIENT_GETNAME = new RedisStrictCommand<String>("CLIENT", "GETNAME", new StringDataDecoder());
    RedisStrictCommand<String> FLUSHDB = new RedisStrictCommand<String>("FLUSHDB", new StringReplayDecoder());

    RedisStrictCommand<List<String>> KEYS = new RedisStrictCommand<List<String>>("KEYS", new StringListReplayDecoder());

    RedisCommand<String> HMSET = new RedisCommand<String>("HMSET", new StringReplayDecoder(), 2, 3);
    RedisCommand<Object> HMGET = new RedisCommand<Object>("HMGET", new StringListObjectReplayDecoder(), 2, 3);

    RedisStrictCommand<Boolean> DEL_ONE = new RedisStrictCommand<Boolean>("DEL", new BooleanReplayConvertor());

    RedisCommand<Object> GET = new RedisCommand<Object>("GET");
    RedisCommand<String> SET = new RedisCommand<String>("SET", new StringReplayDecoder(), 1);
    RedisCommand<String> SETEX = new RedisCommand<String>("SETEX", new StringReplayDecoder(), 2);
    RedisStrictCommand<Boolean> EXISTS = new RedisStrictCommand<Boolean>("EXISTS", new BooleanReplayConvertor());

    RedisStrictCommand<Boolean> RENAMENX = new RedisStrictCommand<Boolean>("RENAMENX", new BooleanReplayConvertor());
    RedisStrictCommand<Boolean> RENAME = new RedisStrictCommand<Boolean>("RENAME", new BooleanReplayDecoder());

    RedisCommand<Long> PUBLISH = new RedisCommand<Long>("PUBLISH", 1);

    RedisStrictCommand<PubSubStatusMessage> SUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("SUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> UNSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("UNSUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> PSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("PSUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> PUNSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("PUNSUBSCRIBE", new PubSubStatusDecoder());

    RedisStrictCommand<String> CLUSTER_NODES = new RedisStrictCommand<String>("CLUSTER", "NODES", new StringDataDecoder());

    RedisStrictCommand<List<String>> SENTINEL_GET_MASTER_ADDR_BY_NAME = new RedisStrictCommand<List<String>>("SENTINEL", "GET-MASTER-ADDR-BY-NAME", new StringListReplayDecoder());
    RedisStrictCommand<List<Map<String, String>>> SENTINEL_SLAVES = new RedisStrictCommand<List<Map<String, String>>>("SENTINEL", "SLAVES", new StringMapReplayDecoder());

}
