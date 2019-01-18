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
package org.redisson.rx;

import org.redisson.RedissonReadWriteLock;
import org.redisson.api.RLockRx;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RReadWriteLockRx;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReadWriteLockRx implements RReadWriteLockRx {

    private final RReadWriteLock instance;
    private final CommandRxExecutor commandExecutor;
    
    public RedissonReadWriteLockRx(CommandRxExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.instance = new RedissonReadWriteLock(commandExecutor, name);
    }

    @Override
    public RLockRx readLock() {
        return RxProxyBuilder.create(commandExecutor, instance.readLock(), RLockRx.class);
    }

    @Override
    public RLockRx writeLock() {
        return RxProxyBuilder.create(commandExecutor, instance.writeLock(), RLockRx.class);
    }

    
}
