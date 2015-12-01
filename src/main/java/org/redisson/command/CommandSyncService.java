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
package org.redisson.command;

import java.net.InetSocketAddress;
import java.util.List;

import org.redisson.SyncOperation;
import org.redisson.client.RedisAskException;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.RedisLoadingException;
import org.redisson.client.RedisMovedException;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.NodeSource;
import org.redisson.connection.NodeSource.Redirect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

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
    public <T, R> R read(String key, RedisCommand<T> command, Object ... params) {
        return read(key, connectionManager.getCodec(), command, params);
    }

    @Override
    public <T, R> R read(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = readAsync(key, codec, command, params);
        return get(res);
    }

    @Override
    public <T, R> R read(InetSocketAddress client, String key, RedisCommand<T> command, Object ... params) {
        Future<R> res = readAsync(client, key, connectionManager.getCodec(), command, params);
        return get(res);
    }

    @Override
    public <T, R> R read(InetSocketAddress client, String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = readAsync(client, key, codec, command, params);
        return get(res);
    }

    @Override
    public <T, R> R evalRead(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalRead(key, connectionManager.getCodec(), evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> R evalRead(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Future<R> res = evalReadAsync(key, codec, evalCommandType, script, keys, params);
        return get(res);
    }

    @Override
    public <T, R> R evalWrite(String key, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        return evalWrite(key, connectionManager.getCodec(), evalCommandType, script, keys, params);
    }

    @Override
    public <T, R> R evalWrite(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        Future<R> res = evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        return get(res);
    }

    @Override
    public <T, R> R write(Integer slot, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(slot, codec, command, params);
        return get(res);
    }

    @Override
    public <R> R write(String key, Codec codec, SyncOperation<R> operation) {
        int slot = connectionManager.calcSlot(key);
        return sync(false, codec, new NodeSource(slot), operation, 0);
    }

    @Override
    public <R> R read(String key, Codec codec, SyncOperation<R> operation) {
        int slot = connectionManager.calcSlot(key);
        return sync(true, codec, new NodeSource(slot), operation, 0);
    }

    <R> R sync(boolean readOnlyMode, Codec codec, NodeSource source, SyncOperation<R> operation, int attempt) {
        if (!connectionManager.getShutdownLatch().acquire()) {
            throw new IllegalStateException("Redisson is shutdown");
        }

        try {
            Future<RedisConnection> connectionFuture;
            if (readOnlyMode) {
                connectionFuture = connectionManager.connectionReadOp(source, null);
            } else {
                connectionFuture = connectionManager.connectionWriteOp(source, null);
            }
            connectionFuture.syncUninterruptibly();

            RedisConnection connection = connectionFuture.getNow();

            try {
                return operation.execute(codec, connection);
            } catch (RedisMovedException e) {
                return sync(readOnlyMode, codec, new NodeSource(e.getSlot(), e.getAddr(), Redirect.MOVED), operation, attempt);
            } catch (RedisAskException e) {
                return sync(readOnlyMode, codec, new NodeSource(e.getSlot(), e.getAddr(), Redirect.ASK), operation, attempt);
            } catch (RedisLoadingException e) {
                return sync(readOnlyMode, codec, source, operation, attempt);
            } catch (RedisTimeoutException e) {
                if (attempt == connectionManager.getConfig().getRetryAttempts()) {
                    throw e;
                }
                attempt++;
                return sync(readOnlyMode, codec, source, operation, attempt);
            } finally {
                connectionManager.getShutdownLatch().release();
                if (readOnlyMode) {
                    connectionManager.releaseRead(source, connection);
                } else {
                    connectionManager.releaseWrite(source, connection);
                }
            }
        } catch (RedisException e) {
            if (attempt == connectionManager.getConfig().getRetryAttempts()) {
                throw e;
            }
            try {
                Thread.sleep(connectionManager.getConfig().getRetryInterval());
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
            attempt++;
            return sync(readOnlyMode, codec, source, operation, attempt);
        }
    }

    @Override
    public <T, R> R write(String key, Codec codec, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(key, codec, command, params);
        return get(res);
    }

    @Override
    public <T, R> R write(String key, RedisCommand<T> command, Object ... params) {
        Future<R> res = writeAsync(key, command, params);
        return get(res);
    }

}
