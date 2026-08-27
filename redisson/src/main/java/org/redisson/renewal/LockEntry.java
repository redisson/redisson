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

import org.redisson.misc.Tuple;

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
    final Map<Long, Tuple<String, String>> threadId2owner = new ConcurrentHashMap<>();

    LockEntry() {
        super();
    }

    public String getLockName(long threadId) {
        Tuple<String, String> owner = threadId2owner.get(threadId);
        if (owner == null) {
            return null;
        }
        return owner.getT1();
    }

    public String getThreadName(long threadId) {
        Tuple<String, String> owner = threadId2owner.get(threadId);
        if (owner == null) {
            return String.valueOf(threadId);
        }
        return owner.getT2();
    }

    public void addThreadId(long threadId, String lockName, String threadName) {
        threadId2counter.compute(threadId, (t, counter) -> {
            counter = Optional.ofNullable(counter).orElse(0);
            counter++;
            threadsQueue.add(threadId);
            return counter;
        });
        threadId2owner.putIfAbsent(threadId, new Tuple<>(lockName, threadName));
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
                threadId2owner.remove(threadId);
                return null;
            }
            return counter;
        });
    }

}
