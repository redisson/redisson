/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.util.concurrent.Future;

/**
 * RedLock locking algorithm implementation for multiple locks. 
 * It manages all locks as one.
 * 
 * @see <a href="http://redis.io/topics/distlock">http://redis.io/topics/distlock</a>
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonRedLock extends RedissonMultiLock {

    /**
     * Creates instance with multiple {@link RLock} objects.
     * Each RLock object could be created by own Redisson instance.
     *
     * @param locks
     */
    public RedissonRedLock(RLock... locks) {
        super(locks);
    }
    
    protected boolean sync(Map<RLock, Future<Boolean>> tryLockFutures) {
        Queue<RLock> lockedLocks = new ConcurrentLinkedQueue<RLock>();
        RuntimeException latestException = null;
        for (Entry<RLock, Future<Boolean>> entry : tryLockFutures.entrySet()) {
            try {
                if (entry.getValue().syncUninterruptibly().getNow()) {
                    lockedLocks.add(entry.getKey());
                }
            } catch (RuntimeException e) {
                latestException = e;
            }
        }
        
        if (lockedLocks.size() < minLocksAmount(locks)) {
            unlock();
            lockedLocks.clear();
            if (latestException != null) {
                throw latestException;
            }
            return false;
        }
        
        return true;
    }

    public void unlock() {
        List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>(locks.size());

        for (RLock lock : locks) {
            futures.add(lock.forceUnlockAsync());
        }

        for (Future<Boolean> future : futures) {
            future.awaitUninterruptibly();
        }
    }
    
    protected int minLocksAmount(final List<RLock> locks) {
        return locks.size()/2 + 1;
    }

    @Override
    protected boolean isLockFailed(Future<Boolean> future) {
        return false;
    }
    
    @Override
    protected boolean isAllLocksAcquired(AtomicReference<RLock> lockedLockHolder, AtomicReference<Throwable> failed, Queue<RLock> lockedLocks) {
        return (lockedLockHolder.get() == null && failed.get() == null) || lockedLocks.size() >= minLocksAmount(locks);
    }
    
}
