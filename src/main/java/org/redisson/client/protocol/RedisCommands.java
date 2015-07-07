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

import org.redisson.client.handler.RedisData;

import io.netty.util.concurrent.Future;

public interface RedisCommands {

    RedisStringCommand AUTH = new RedisStringCommand("AUTH", new StringReplayDecoder());
    RedisStringCommand SELECT = new RedisStringCommand("SELECT", new StringReplayDecoder());
    RedisStringCommand CLIENT_SETNAME = new RedisStringCommand("CLIENT", "SETNAME", new StringReplayDecoder(), 1);
    RedisStringCommand CLIENT_GETNAME = new RedisStringCommand("CLIENT", "GETNAME");

    RedisCommand<Object> GET = new RedisCommand<Object>("GET");
    RedisCommand<String> SET = new RedisCommand<String>("SET", new StringReplayDecoder(), 1);
    RedisCommand<String> SETEX = new RedisCommand<String>("SETEX", new StringReplayDecoder(), 2);
    RedisCommand<Boolean> EXISTS = new RedisCommand<Boolean>("EXISTS", new BooleanReplayDecoder(), 1);



    String sync(RedisStringCommand command, Object ... params);

    Future<String> async(RedisStringCommand command, Object ... params);

    <T, R> R sync(Codec encoder, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> async(Codec encoder, RedisCommand<T> command, Object ... params);

    <T, R> void send(RedisData<T, R> data);

}
