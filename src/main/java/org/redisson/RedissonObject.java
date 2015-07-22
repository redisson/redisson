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
package org.redisson;

import org.redisson.client.protocol.RedisCommands;
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

    final CommandExecutor commandExecutor;
    private final String name;

    public RedissonObject(CommandExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
    }

    protected <V> V get(Future<V> future) {
        return commandExecutor.get(future);
    }

    protected <V> Promise<V> newPromise() {
        return commandExecutor.getConnectionManager().getGroup().next().<V>newPromise();
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
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_SINGLE, getName());
    }

}
