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

public interface RedisCommands {

    RedisCommand<String> AUTH = new RedisCommand<String>("AUTH", new StringReplayDecoder());
    RedisCommand<String> SELECT = new RedisCommand<String>("SELECT", new StringReplayDecoder());
    RedisCommand<String> CLIENT_SETNAME = new RedisCommand<String>("CLIENT", "SETNAME", new StringReplayDecoder(), 1);
    RedisCommand<String> CLIENT_GETNAME = new RedisCommand<String>("CLIENT", "GETNAME");

    RedisCommand<Object> GET = new RedisCommand<Object>("GET");
    RedisCommand<String> SET = new RedisCommand<String>("SET", new StringReplayDecoder(), 1);

}
