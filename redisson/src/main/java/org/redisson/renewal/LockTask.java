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
package org.redisson.renewal;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.decoder.ContainsDecoder;
import org.redisson.command.CommandAsyncExecutor;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LockTask extends RenewalTask {

    public LockTask(long internalLockLeaseTime,
                    CommandAsyncExecutor executor, int chunkSize) {
        super(internalLockLeaseTime, executor, chunkSize);
    }

    @Override
    CompletionStage<Void> renew(Iterator<String> iter, int chunkSize) {
        if (!iter.hasNext()) {
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Long> name2threadId = new HashMap<>(chunkSize);
        List<Object> args = new ArrayList<>(chunkSize + 1);
        args.add(internalLockLeaseTime);

        List<String> keys = new ArrayList<>(chunkSize);
        while (iter.hasNext()) {
            String key = iter.next();

            LockEntry entry = name2entry.get(key);
            if (entry == null) {
                continue;
            }
            Long threadId = entry.getFirstThreadId();
            if (threadId == null) {
                continue;
            }

            keys.add(key);
            args.add(entry.getLockName(threadId));
            name2threadId.put(key, threadId);

            if (keys.size() == chunkSize) {
                break;
            }
        }

        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String firstName = keys.get(0);

        CompletionStage<List<String>> f = executor.syncedEval(firstName, LongCodec.INSTANCE,
                new RedisCommand<>("EVAL", new ContainsDecoder<>(keys)),
                  "local result = {} " +
                        "for i = 1, #KEYS, 1 do " +
                            "if (redis.call('hexists', KEYS[i], ARGV[i + 1]) == 1) then " +
                                "redis.call('pexpire', KEYS[i], ARGV[1]); " +
                                "table.insert(result, 1); " +
                            "else " +
                                "table.insert(result, 0); " +
                            "end; " +
                        "end; " +
                        "return result;",
                new ArrayList<>(keys),
                args.toArray());

        return f.thenCompose(existingNames -> {
            keys.removeAll(existingNames);
            for (String key : keys) {
                cancelExpirationRenewal(key, name2threadId.get(key));
            }
            return renew(iter, chunkSize);
        });
    }

    public void add(String rawName, String lockName, long threadId) {
        LockEntry entry = new LockEntry();
        entry.addThreadId(threadId, lockName);

        add(rawName, lockName, threadId, entry);
    }

}
