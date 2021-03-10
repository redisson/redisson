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

import org.redisson.RedissonFairLock;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import java.util.Arrays;

/**
 * @author wuqian30624
 */
public class FairLockClientRefreshThread extends AbstractFairLockClientThread{
    private static String threadName = "fairLock-refresh";
    private long ttl = RedissonFairLock.DEFAULT_THREAD_WAIT_TIME;

    public FairLockClientRefreshThread(FairLockCache fairLockCache,
                                       CommandAsyncExecutor commandExecutor, long ttl){
        super(fairLockCache, commandExecutor, threadName);
        this.ttl = ttl;
    }

    @Override
    protected void executeCommands(String fairLock) {
        evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "redis.call('set', KEYS[1], 1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]);",
                Arrays.<Object>asList(fairLock),
                ttl);
    }

}
