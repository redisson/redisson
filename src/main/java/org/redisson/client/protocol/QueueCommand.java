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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface QueueCommand {

    Set<String> PUBSUB_COMMANDS = new HashSet<String>(Arrays.asList("PSUBSCRIBE", "SUBSCRIBE", "PUNSUBSCRIBE", "UNSUBSCRIBE"));
    
    Set<String> TIMEOUTLESS_COMMANDS = new HashSet<String>(Arrays.asList(RedisCommands.BLPOP_VALUE.getName(),
            RedisCommands.BRPOP_VALUE.getName(), RedisCommands.BRPOPLPUSH.getName()));

    List<CommandData<Object, Object>> getPubSubOperations();

    boolean tryFailure(Throwable cause);

}
