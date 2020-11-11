/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
package org.redisson.executor;

import org.redisson.RedissonLock;
import org.redisson.command.CommandAsyncExecutor;

/**
 * @author wuqian30624
 */
public abstract class AbstractFairLockClientThread extends RedissonLock implements Runnable {
    protected String threadName;
    protected FairLockCache fairLockCache;

    public AbstractFairLockClientThread(FairLockCache fairLockCache,
                                 CommandAsyncExecutor commandExecutor, String threadName){
        super(commandExecutor, threadName);
        this.fairLockCache = fairLockCache;
        this.threadName = threadName;
    }

    @Override
    public String getName() {
        return threadName;
    }

    @Override
    public void run() {
        if (null != fairLockCache && fairLockCache.getRegisteredLocks().size() > 0) {
            for (String fairLockName:fairLockCache.getRegisteredLocks()) {
                try {
                    executeCommands(fairLockName);
                }catch (Exception e){
                    // failure during executing commands
                }
            }
        }
    }

    /**
     * Execute redis command
     * @param fairLock
     */
    protected abstract void executeCommands(String fairLock);
}
