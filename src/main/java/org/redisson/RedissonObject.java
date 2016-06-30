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
package org.redisson;

import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RObject;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * Base Redisson object
 *
 * @author Nikita Koksharov
 *
 */
abstract class RedissonObject implements RObject {

    final CommandAsyncExecutor commandExecutor;
    private final String name;
    final Codec codec;

    public RedissonObject(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this.codec = codec;
        this.name = name;
        this.commandExecutor = commandExecutor;
    }

    public RedissonObject(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    protected boolean await(Future<?> future, long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        return commandExecutor.await(future, timeout, timeoutUnit);
    }
    
    protected <V> V get(Future<V> future) {
        return commandExecutor.get(future);
    }

    protected <V> Promise<V> newPromise() {
        return commandExecutor.getConnectionManager().newPromise();
    }

    protected <V> Future<V> newSucceededFuture(V result) {
        return commandExecutor.getConnectionManager().newSucceededFuture(result);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void rename(String newName) {
        get(renameAsync(newName));
    }

    @Override
    public Future<Void> renameAsync(String newName) {
        return commandExecutor.writeAsync(getName(), RedisCommands.RENAME, getName(), newName);
    }

    @Override
    public void migrate(String host, int port, int database) {
        get(migrateAsync(host, port, database));
    }

    @Override
    public Future<Void> migrateAsync(String host, int port, int database) {
        return commandExecutor.writeAsync(getName(), RedisCommands.MIGRATE, host, port, getName(), database);
    }

    @Override
    public boolean move(int database) {
        return get(moveAsync(database));
    }

    @Override
    public Future<Boolean> moveAsync(int database) {
        return commandExecutor.writeAsync(getName(), RedisCommands.MOVE, getName(), database);
    }

    @Override
    public boolean renamenx(String newName) {
        return get(renamenxAsync(newName));
    }

    @Override
    public Future<Boolean> renamenxAsync(String newName) {
        return commandExecutor.writeAsync(getName(), RedisCommands.RENAMENX, getName(), newName);
    }

    @Override
    public boolean delete() {
        return get(deleteAsync());
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_BOOL, getName());
    }

    @Override
    public boolean isExists() {
        return get(isExistsAsync());
    }

    @Override
    public Future<Boolean> isExistsAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.EXISTS, getName());
    }

}
