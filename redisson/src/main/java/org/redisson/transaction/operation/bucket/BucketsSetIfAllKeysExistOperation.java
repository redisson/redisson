/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson.transaction.operation.bucket;

import org.redisson.RedissonBuckets;
import org.redisson.api.RBuckets;
import org.redisson.api.RLock;
import org.redisson.api.SetParams;
import org.redisson.api.SetArgs;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.RedissonTransactionalLock;
import org.redisson.transaction.operation.TransactionalOperation;

/**
 *
 * @author seakider
 *
 */
public class BucketsSetIfAllKeysExistOperation extends TransactionalOperation {

    private String transactionId;
    private SetArgs setArgs;

    public BucketsSetIfAllKeysExistOperation(Codec codec, SetArgs setArgs, String transactionId) {
        super(null, codec);
        this.setArgs = setArgs;
        this.transactionId = transactionId;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RBuckets bucket = new RedissonBuckets(codec, commandExecutor);
        bucket.setIfAllKeysExistAsync(setArgs);

        unlock(commandExecutor);
    }

    protected void unlock(CommandAsyncExecutor commandExecutor) {
        SetParams pps = (SetParams) setArgs;
        for (String key : pps.getEntries().keySet()) {
            RLock lock = new RedissonTransactionalLock(commandExecutor, getLockName(key), transactionId);
            lock.unlockAsync();
        }
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        unlock(commandExecutor);
    }

    public SetArgs getSetArgs() {
        return setArgs;
    }

    private String getLockName(String name) {
        return name + ":transaction_lock";
    }
}
