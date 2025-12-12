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
public class ReadLockTask extends LockTask {

    public ReadLockTask(long internalLockLeaseTime, CommandAsyncExecutor executor, int chunkSize) {
        super(internalLockLeaseTime, executor, chunkSize);
    }

    @Override
    CompletionStage<Void> renew(Iterator<String> iter, int chunkSize) {
        if (!iter.hasNext()) {
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Long> name2lockName = new HashMap<>();
        List<Object> args = new ArrayList<>();
        args.add(internalLockLeaseTime);

        List<Object> keys = new ArrayList<>(chunkSize);
        List<Object> keysArgs = new ArrayList<>(chunkSize);
        while (iter.hasNext()) {
            String key = iter.next();

            ReadLockEntry entry = (ReadLockEntry) name2entry.get(key);
            if (entry == null) {
                continue;
            }

            Long threadId = entry.getFirstThreadId();
            if (threadId == null) {
                continue;
            }

            String keyPrefix = entry.getKeyPrefix(threadId);
            String lockName = entry.getLockName(threadId);

            if (keyPrefix == null || lockName == null) {
                continue;
            }

            keys.add(key);
            keysArgs.add(key);
            keysArgs.add(keyPrefix);
            args.add(lockName);
            name2lockName.put(key, threadId);

            if (keys.size() == chunkSize) {
                break;
            }
        }

        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String firstName = keys.get(0).toString();

        CompletionStage<List<Object>> f = executor.syncedEval(firstName, LongCodec.INSTANCE,
                new RedisCommand<>("EVAL", new ContainsDecoder<>(keys)),
          "local result = {} " +
                "local j = 1 " +
                "for i = 1, #KEYS, 2 do " +
                    "j = j + 1; " +
                    "local counter = redis.call('hget', KEYS[i], ARGV[j]); " +
                    "if (counter ~= false) then " +
                        "redis.call('pexpire', KEYS[i], ARGV[1]); " +

                        "if (redis.call('hlen', KEYS[i]) > 1) then " +
                            "local keys = redis.call('hkeys', KEYS[i]); " +
                            "for n, key in ipairs(keys) do " +
                                "counter = tonumber(redis.call('hget', KEYS[i], key)); " +
                                "if type(counter) == 'number' then " +
                                    "for c=counter, 1, -1 do " +
                                        "redis.call('pexpire', KEYS[i+1] .. ':' .. key .. ':rwlock_timeout:' .. c, ARGV[1]); " +
                                    "end; " +
                                "end; " +
                            "end; " +
                        "end; " +
                        "table.insert(result, 1); " +
                    "else " +
                        "table.insert(result, 0); " +
                    "end; " +
                "end; " +
                "return result;",
                keysArgs,
                args.toArray());

        return f.thenCompose(existingNames -> {
            keys.removeAll(existingNames);
            for (Object k : keys) {
                String key = k.toString();
                cancelExpirationRenewal(key, name2lockName.get(key));
            }
            return renew(iter, chunkSize);
        });
    }

    public void add(String rawName, String lockName, long threadId, String keyPrefix) {
        addSlotName(rawName);

        ReadLockEntry entry = new ReadLockEntry();
        entry.addThreadId(threadId, lockName, keyPrefix);

        ReadLockEntry oldEntry = (ReadLockEntry) name2entry.putIfAbsent(rawName, entry);
        if (oldEntry != null) {
            oldEntry.addThreadId(threadId, lockName, keyPrefix);
        } else {
            if (tryRun()) {
                schedule();
            }
        }
    }

}
