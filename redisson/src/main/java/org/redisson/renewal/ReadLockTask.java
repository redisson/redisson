/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
import org.redisson.misc.AsyncChunkProcessor;
import org.redisson.misc.AsyncChunkProcessor.ChunkExecution;

import java.util.*;
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
        return AsyncChunkProcessor.processAll(iter, chunkSize, this::buildChunk);
    }

    private ChunkExecution<List<String>> buildChunk(Iterator<String> iter, int chunkSize) {
        Map<String, Set<Long>> name2threadIds = new HashMap<>();
        List<Object> args = new ArrayList<>();
        args.add(internalLockLeaseTime);

        List<String> keys = new ArrayList<>(chunkSize);
        List<Object> keysArgs = new ArrayList<>(chunkSize);

        // Build chunk, skipping invalid entries
        while (iter.hasNext() && keys.size() < chunkSize) {
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
            if (keyPrefix == null) {
                continue;
            }

            keys.add(key);
            keysArgs.add(key);
            keysArgs.add(keyPrefix);

            Map<Long, String> snapshot = new LinkedHashMap<>(entry.threadId2lockName);
            List<String> lockNames = new ArrayList<>(snapshot.values());
            args.add(lockNames.size());
            args.addAll(lockNames);

            Set<Long> threadIds = new HashSet<>(snapshot.keySet());
            name2threadIds.put(key, threadIds);
        }

        // No valid entries found - signal completion
        if (keys.isEmpty()) {
            return null;
        }

        String firstName = keys.get(0);

        CompletionStage<List<String>> f = trackFailure(executor.syncedEval(firstName, LongCodec.INSTANCE,
                new RedisCommand<>("EVAL", new ContainsDecoder<>(keys)),
          "local result = {} " +
                "local argIdx = 2 " +
                "for i = 1, #KEYS, 2 do " +
                    "local anyAlive = false; " +
                    "local lockNamesCount = tonumber(ARGV[argIdx]); " +
                    "argIdx = argIdx + 1; " +
                    "for k = 1, lockNamesCount do " +
                        "local counter = redis.call('hget', KEYS[i], ARGV[argIdx]); " +
                        "if (counter ~= false) then " +
                            "anyAlive = true; " +
                            "for c=counter, 1, -1 do " +
                                "redis.call('pexpire', KEYS[i+1] .. ':' .. ARGV[argIdx] .. ':rwlock_timeout:' .. c, ARGV[1]); " +
                            "end; " +
                        "end; " +
                        "argIdx = argIdx + 1; " +
                    "end; " +
                    "if (anyAlive) then " +
                        "redis.call('pexpire', KEYS[i], ARGV[1]); " +
                        "table.insert(result, 1); " +
                    "else " +
                        "table.insert(result, 0); " +
                    "end; " +
                "end; " +
                "return result;",
                keysArgs,
                args.toArray()), name2threadIds);

        return new ChunkExecution<>(f, existingNames -> {
            keys.removeAll(existingNames);
            for (String key : keys) {
                Set<Long> threadIds = name2threadIds.get(key);
                if (threadIds != null) {
                    for (Long threadId : threadIds) {
                        cancelExpirationRenewal(key, threadId);
                    }
                }
            }
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
