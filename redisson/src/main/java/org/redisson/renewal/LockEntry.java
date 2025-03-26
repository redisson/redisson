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

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class LockEntry {

    final Queue<Long> threadsQueue = new ConcurrentLinkedQueue<>();
    final Map<Long, Integer> threadId2counter = new ConcurrentHashMap<>();
    final Map<Long, String> threadId2lockName = new ConcurrentHashMap<>();

    LockEntry() {
        super();
    }

    public String getLockName(long threadId) {
        return threadId2lockName.get(threadId);
    }

    public void addThreadId(long threadId, String lockName) {
        threadId2counter.compute(threadId, (t, counter) -> {
            counter = Optional.ofNullable(counter).orElse(0);
            counter++;
            threadsQueue.add(threadId);
            return counter;
        });
        threadId2lockName.putIfAbsent(threadId, lockName);
    }

    public boolean hasNoThreads() {
        return threadsQueue.isEmpty();
    }

    public Long getFirstThreadId() {
        return threadsQueue.peek();
    }

    public void removeThreadId(long threadId) {
        threadId2counter.computeIfPresent(threadId, (t, counter) -> {
            counter--;
            if (counter == 0) {
                threadsQueue.removeIf(v-> v == threadId);
                threadId2lockName.remove(threadId);
                return null;
            }
            return counter;
        });
    }

}
