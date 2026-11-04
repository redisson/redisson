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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Watchdog for the threads that wait in a fair lock queue.
 * <p>
 * {@link LockTask} keeps the holder of a fair lock alive. It extends the lock key while
 * the process of the holder runs. Thus a crashed holder loses the lock after
 * {@code lockWatchdogTimeout}. A queued waiter had no such signal. Its queue entry
 * stayed until a deadline computed from the {@code waitTime} of the caller. Thus a
 * crashed waiter blocked the queue for that time, and forever for an unbounded wait.
 *
 * @author Nikita Koksharov
 *
 */
public class FairLockWaiterTask extends RenewalTask {

    public FairLockWaiterTask(long internalLockLeaseTime, CommandAsyncExecutor executor, int chunkSize) {
        super(internalLockLeaseTime, executor, chunkSize);
    }

    @Override
    CompletionStage<Void> renew(Iterator<String> iter, int chunkSize) {
        return AsyncChunkProcessor.processAll(iter, chunkSize, this::buildChunk);
    }

    private ChunkExecution<Void> buildChunk(Iterator<String> iter, int chunkSize) {
        List<Object> args = new ArrayList<>();

        long deadline = System.currentTimeMillis() + internalLockLeaseTime;
        args.add(deadline);
        // The turn estimate is kept this far beyond the deadline. The reason: when the
        // thread ahead acquires the lock, it subtracts its own wait from the estimate, and
        // that wait is capped at fairLockWaitTimeout. The setting is deprecated. Only clients
        // of older Redisson versions read the estimates, so this task reads the deprecated
        // setting on purpose, until the estimates are removed.
        @SuppressWarnings("deprecation")
        long estimateMargin = executor.getServiceManager().getCfg().getFairLockWaitTimeout();
        args.add(deadline + estimateMargin);

        List<Object> keyPairs = new ArrayList<>(2 * chunkSize);

        while (iter.hasNext() && keyPairs.size() < 2 * chunkSize) {
            String name = iter.next();

            FairLockWaiterEntry entry = (FairLockWaiterEntry) name2entry.get(name);
            if (entry == null) {
                continue;
            }

            Map<Long, String> snapshot = new LinkedHashMap<>(entry.threadId2lockName);
            if (snapshot.isEmpty()) {
                continue;
            }

            keyPairs.add(entry.getAliveSetName());
            keyPairs.add(entry.getTimeoutSetName());

            args.add(snapshot.size());
            args.addAll(snapshot.values());
        }

        if (keyPairs.isEmpty()) {
            return null;
        }

        String firstName = keyPairs.get(0).toString();

        CompletionStage<Void> f = executor.syncedEval(firstName, LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                        "local argIdx = 3 " +
                        "for i = 1, #KEYS, 2 do " +
                            "local count = tonumber(ARGV[argIdx]); " +
                            "argIdx = argIdx + 1; " +
                            "for k = 1, count do " +
                                "if (redis.call('zscore', KEYS[i], ARGV[argIdx]) ~= false) then " +
                                    "redis.call('zadd', KEYS[i], ARGV[1], ARGV[argIdx]); " +
                                    "local estimate = redis.call('zscore', KEYS[i + 1], ARGV[argIdx]); " +
                                    "if (estimate ~= false and tonumber(estimate) < tonumber(ARGV[2])) then " +
                                        "redis.call('zadd', KEYS[i + 1], ARGV[2], ARGV[argIdx]); " +
                                    "end; " +
                                "end; " +
                                "argIdx = argIdx + 1; " +
                            "end; " +
                        "end; ",
                keyPairs,
                args.toArray());

        return new ChunkExecution<>(f, unused -> {
        });
    }

    public void add(String rawName, String aliveSetName, String timeoutSetName, String lockName, long threadId) {
        FairLockWaiterEntry entry = new FairLockWaiterEntry(aliveSetName, timeoutSetName);
        entry.addThreadId(threadId, lockName);

        add(rawName, lockName, threadId, entry);
    }

}
