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
import org.redisson.client.protocol.RedisCommands;
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
public class FastMultilockTask extends LockTask {

    public FastMultilockTask(long internalLockLeaseTime, CommandAsyncExecutor executor) {
        super(internalLockLeaseTime, executor, 1);
    }

    @Override
    CompletionStage<Void> renew(Iterator<String> iter, int chunkSize) {
        return AsyncChunkProcessor.processAll(iter, chunkSize, this::buildChunk);
    }

    private ChunkExecution<Boolean> buildChunk(Iterator<String> iter, int chunkSize) {
        Map<String, Long> name2lockName = new HashMap<>();
        List<Object> args = new ArrayList<>();
        args.add(internalLockLeaseTime);
        args.add(System.currentTimeMillis());

        List<String> keys = new ArrayList<>(chunkSize);

        // Build chunk, skipping invalid entries
        while (iter.hasNext() && keys.size() < chunkSize) {
            String key = iter.next();

            FastMultilockEntry entry = (FastMultilockEntry) name2entry.get(key);
            if (entry == null) {
                continue;
            }

            Long threadId = entry.getFirstThreadId();
            if (threadId == null) {
                continue;
            }

            keys.add(key);
            args.add(entry.getLockName(threadId));
            args.addAll(entry.getFields());
            name2lockName.put(key, threadId);
        }

        // No valid entries found - signal completion
        if (keys.isEmpty()) {
            return null;
        }

        String firstName = keys.get(0);

        CompletionStage<Boolean> f = executor.syncedEval(firstName, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "local leaseTime = tonumber(ARGV[1]);" +
                        "local currentTime = tonumber(ARGV[2]);" +
                        "local currentThread = ARGV[3];" +
                        "if (redis.call('exists',KEYS[1]) > 0) then" +
                        "   local newExpireTime = leaseTime + currentTime;" +
                        "   for i=4, #ARGV, 1 do " +
                        "       local lockThread = redis.call('hget', KEYS[1], ARGV[i]);" +
                        "       if(lockThread ~= false and lockThread == currentThread) then " +
                        "           local expireFieldName = ARGV[i]..':'..lockThread..':expire_time';" +
                        "           local expireTime = redis.call('hget', KEYS[1], expireFieldName);" +
                        "           if(tonumber(expireTime) < newExpireTime) then " +
                        "               redis.call('hset', KEYS[1],expireFieldName, newExpireTime);" +
                        "           end;" +
                        "       else" +
                        "           return 0;" +
                        "       end;" +
                        "   end; " +
                        "   local expireTime = redis.call('pttl',KEYS[1]);" +
                        "   if(tonumber(expireTime) < tonumber(leaseTime)) then " +
                        "       redis.call('pexpire',KEYS[1], leaseTime);" +
                        "   end;" +
                        "   return 1;" +
                        "end;" +
                        "return 0;",
                Collections.singletonList(firstName),
                args.toArray());

        return new ChunkExecution<>(f, exists -> {
            if (!exists) {
                cancelExpirationRenewal(firstName, name2lockName.get(firstName));
            }
        });
    }

    public void add(String rawName, String lockName, long threadId, Collection<String> fields) {
        FastMultilockEntry entry = new FastMultilockEntry(fields);
        entry.addThreadId(threadId, lockName);

        add(rawName, lockName, threadId, entry);
    }

}
