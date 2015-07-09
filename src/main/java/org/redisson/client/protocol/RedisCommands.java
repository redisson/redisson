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

import org.redisson.client.protocol.pubsub.PubSubStatusDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;

public interface RedisCommands {

    RedisStrictCommand<String> AUTH = new RedisStrictCommand<String>("AUTH", new StringReplayDecoder());
    RedisStrictCommand<String> SELECT = new RedisStrictCommand<String>("SELECT", new StringReplayDecoder());
    RedisStrictCommand<String> CLIENT_SETNAME = new RedisStrictCommand<String>("CLIENT", "SETNAME", new StringReplayDecoder());
    RedisStrictCommand<String> CLIENT_GETNAME = new RedisStrictCommand<String>("CLIENT", "GETNAME", new StringDataDecoder());

    RedisCommand<Object> GET = new RedisCommand<Object>("GET");
    RedisCommand<String> SET = new RedisCommand<String>("SET", new StringReplayDecoder(), 1);
    RedisCommand<String> SETEX = new RedisCommand<String>("SETEX", new StringReplayDecoder(), 2);
    RedisStrictCommand<Boolean> EXISTS = new RedisStrictCommand<Boolean>("EXISTS", new BooleanReplayConvertor());

    RedisCommand<Long> PUBLISH = new RedisCommand<Long>("PUBLISH", 1);

    RedisStrictCommand<PubSubStatusMessage> SUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("SUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> UNSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("UNSUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> PSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("PSUBSCRIBE", new PubSubStatusDecoder());
    RedisStrictCommand<PubSubStatusMessage> PUNSUBSCRIBE = new RedisStrictCommand<PubSubStatusMessage>("PUNSUBSCRIBE", new PubSubStatusDecoder());

}
