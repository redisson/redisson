/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.eviction;

import io.netty.util.concurrent.ScheduledFuture;
import org.redisson.api.RFuture;
import org.redisson.command.CommandAsyncExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class EvictionTask implements Runnable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    final Deque<Integer> sizeHistory = new LinkedList<Integer>();
    final int minDelay;
    final int maxDelay;
    final int keysLimit;
    
    int delay = 5;

    final CommandAsyncExecutor executor;

    ScheduledFuture<?> scheduledFuture;
    
    EvictionTask(CommandAsyncExecutor executor) {
        super();
        this.executor = executor;
        this.minDelay = executor.getConnectionManager().getCfg().getMinCleanUpDelay();
        this.maxDelay = executor.getConnectionManager().getCfg().getMaxCleanUpDelay();
        this.keysLimit = executor.getConnectionManager().getCfg().getCleanUpKeysAmount();
    }

    public void schedule() {
        scheduledFuture = executor.getConnectionManager().getGroup().schedule(this, delay, TimeUnit.SECONDS);
    }

    public ScheduledFuture<?> getScheduledFuture() {
        return scheduledFuture;
    }

    abstract RFuture<Integer> execute();
    
    abstract String getName();
    
    @Override
    public void run() {
        if (executor.getConnectionManager().isShuttingDown()) {
            return;
        }
        
        RFuture<Integer> future = execute();
        future.whenComplete((size, e) -> {
            if (e != null) {
                log.error("Unable to evict elements for '" + getName() + "'", e);
                schedule();
                return;
            }

            log.debug("{} elements evicted. Object name: {}", size, getName());
            
            if (size == -1) {
                schedule();
                return;
            }

            if (sizeHistory.size() == 2) {
                if (sizeHistory.peekFirst() > sizeHistory.peekLast()
                        && sizeHistory.peekLast() > size) {
                    delay = Math.min(maxDelay, (int) (delay*1.5));
                }

//                    if (sizeHistory.peekFirst() < sizeHistory.peekLast()
//                            && sizeHistory.peekLast() < size) {
//                        prevDelay = Math.max(minDelay, prevDelay/2);
//                    }

                if (sizeHistory.peekFirst().intValue() == sizeHistory.peekLast()
                        && sizeHistory.peekLast().intValue() == size) {
                    if (size >= keysLimit) {
                        delay = Math.max(minDelay, delay/4);
                    }
                    if (size == 0) {
                        delay = Math.min(maxDelay, (int) (delay*1.5));
                    }
                }

                sizeHistory.pollFirst();
            }

            sizeHistory.add(size);
            schedule();
        });
    }

}
