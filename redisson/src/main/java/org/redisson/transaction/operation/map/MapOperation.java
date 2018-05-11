/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.transaction.operation.map;

import org.redisson.RedissonMap;
import org.redisson.RedissonMapCache;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.operation.TransactionalOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class MapOperation extends TransactionalOperation {

    Object key;
    Object value;
    Object oldValue;
    RMap<?, ?> map;
    
    public MapOperation() {
    }
    
    public MapOperation(RMap<?, ?> map, Object key, Object value) {
        this(map, key, value, null);
    }
    
    public MapOperation(RMap<?, ?> map, Object key, Object value, Object oldValue) {
        super(map.getName(), map.getCodec());
        this.map = map;
        this.key = key;
        this.value = value;
        this.oldValue = oldValue;
    }

    public Object getKey() {
        return key;
    }
    
    public RMap<?, ?> getMap() {
        return map;
    }
    
    @Override
    public final void commit(CommandAsyncExecutor commandExecutor) {
        RMap<Object, Object> map = getMap(commandExecutor);
        commit(map);
        map.getLock(key).unlockAsync();
    }

    protected RMap<Object, Object> getMap(CommandAsyncExecutor commandExecutor) {
        if (map instanceof RMapCache) {
            return new RedissonMapCache<Object, Object>(codec, null, commandExecutor, name, null, null);
        }
        return new RedissonMap<Object, Object>(codec, commandExecutor, name, null, null);
    }
    
    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        RMap<Object, Object> map = getMap(commandExecutor);
        map.getLock(key).unlockAsync();
    }

    protected abstract void commit(RMap<Object, Object> map);

    public Object getValue() {
        return value;
    }
    
    public Object getOldValue() {
        return oldValue;
    }
}
