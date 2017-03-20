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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.api.RObject;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;

/**
 * Base Redisson object
 *
 * @author Nikita Koksharov
 *
 */
public abstract class RedissonObject implements RObject {

    protected final CommandAsyncExecutor commandExecutor;
    private final String name;
    protected final Codec codec;

    public RedissonObject(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this.codec = codec;
        this.name = name;
        this.commandExecutor = commandExecutor;
    }

    public RedissonObject(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    protected boolean await(RFuture<?> future, long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        return commandExecutor.await(future, timeout, timeoutUnit);
    }
    
    protected String prefixName(String prefix, String name) {
        if (name.contains("{")) {
            return prefix + ":" + name;
        }
        return prefix + ":{" + name + "}";
    }
    
    protected String suffixName(String name, String suffix) {
        if (name.contains("{")) {
            return name + ":" + suffix;
        }
        return "{" + name + "}:" + suffix;
    }

    protected <V> V get(RFuture<V> future) {
        return commandExecutor.get(future);
    }

    protected <V> RPromise<V> newPromise() {
        return commandExecutor.getConnectionManager().newPromise();
    }

    protected <V> RFuture<V> newSucceededFuture(V result) {
        return commandExecutor.getConnectionManager().newSucceededFuture(result);
    }

    @Override
    public String getName() {
        return name;
    }
    
    protected String getName(Object o) {
        return getName();
    }

    @Override
    public void rename(String newName) {
        get(renameAsync(newName));
    }

    @Override
    public RFuture<Void> renameAsync(String newName) {
        return commandExecutor.writeAsync(getName(), RedisCommands.RENAME, getName(), newName);
    }

    @Override
    public void migrate(String host, int port, int database) {
        get(migrateAsync(host, port, database));
    }

    @Override
    public RFuture<Void> migrateAsync(String host, int port, int database) {
        return commandExecutor.writeAsync(getName(), RedisCommands.MIGRATE, host, port, getName(), database);
    }

    @Override
    public boolean move(int database) {
        return get(moveAsync(database));
    }

    @Override
    public RFuture<Boolean> moveAsync(int database) {
        return commandExecutor.writeAsync(getName(), RedisCommands.MOVE, getName(), database);
    }

    @Override
    public boolean renamenx(String newName) {
        return get(renamenxAsync(newName));
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String newName) {
        return commandExecutor.writeAsync(getName(), RedisCommands.RENAMENX, getName(), newName);
    }

    @Override
    public boolean delete() {
        return get(deleteAsync());
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_BOOL, getName());
    }

    @Override
    public boolean touch() {
        return get(touchAsync());
    }

    @Override
    public RFuture<Boolean> touchAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.TOUCH, getName());
    }
    
    @Override
    public boolean isExists() {
        return get(isExistsAsync());
    }

    @Override
    public RFuture<Boolean> isExistsAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.EXISTS, getName());
    }

    @Override
    public Codec getCodec() {
        return codec;
    }
    
    protected byte[] encode(Object value) {
        try {
            return codec.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    protected byte[] encodeMapKey(Object value) {
        try {
            return codec.getMapKeyEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected byte[] encodeMapValue(Object value) {
        try {
            return codec.getMapValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
