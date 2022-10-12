/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.api;

import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;

/**
 * Function result type.
 *
 * @author Nikita Koksharov
 *
 */
public enum FunctionResult {

    /**
     * Result is a value of Boolean type
     */
    BOOLEAN(RedisCommands.FCALL_BOOLEAN_SAFE),

    /**
     * Result is a value of Long type
     */
    LONG(RedisCommands.FCALL_LONG),

    /**
     * Result is a value of List type
     */
    LIST(RedisCommands.FCALL_LIST),

    /**
     * Result is a value of plain String type
     */
    STRING(RedisCommands.FCALL_STRING),

    /**
     * Result is a value of user defined type
     */
    VALUE(RedisCommands.FCALL_OBJECT),

    /**
     * Result is a value of Map Value type. Codec.getMapValueDecoder() and Codec.getMapValueEncoder()
     * methods are used for data deserialization or serialization.
     */
    MAPVALUE(RedisCommands.FCALL_MAP_VALUE),

    /**
     * Result is a value of List type, which consists of objects of Map Value type.
     * Codec.getMapValueDecoder() and Codec.getMapValueEncoder()
     * methods are used for data deserialization or serialization.
     */
    MAPVALUELIST(RedisCommands.FCALL_MAP_VALUE_LIST);

    private final RedisCommand<?> command;

    FunctionResult(RedisCommand<?> command) {
        this.command = command;
    }

    public RedisCommand<?> getCommand() {
        return command;
    }

}
