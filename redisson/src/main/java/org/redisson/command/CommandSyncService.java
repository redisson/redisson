/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.command;

import java.util.List;

import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CommandSyncService extends CommandAsyncService implements CommandExecutor {

    final Logger log = LoggerFactory.getLogger(getClass());

    public CommandSyncService(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    @Override
    public <T, R> R read(String key, RedisCommand<T> command, Object... params) {
        return read(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> R read(String key, Codec codec, RedisCommand<T> command, Object... params) {
        RFuture<R> res = readAsync(key, codec, command, params);
        return get(res);
    }

    @Override
    public <T, R> R evalRead(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        return evalRead(key, connectionManager.getCodec(), evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> R evalRead(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        RFuture<R> res = evalReadAsync(key, codec, evalCommandType, script, keys, params);
        return get(res);
    }

    @Override
    public <T, R> R evalWrite(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        return evalWrite(key, connectionManager.getCodec(), evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> R evalWrite(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        RFuture<R> res = evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        return get(res);
    }

}
