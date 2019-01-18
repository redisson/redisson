/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.command.CommandAsyncExecutor;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class EvictionTask implements Runnable {

    final Deque<Integer> sizeHistory = new LinkedList<Integer>();
    final int minDelay = 5;
    final int maxDelay = 30*60;
    final int keysLimit = 100;
    
    int delay = 5;

    final CommandAsyncExecutor executor;
    
    EvictionTask(CommandAsyncExecutor executor) {
        super();
        this.executor = executor;
    }

    public void schedule() {
        executor.getConnectionManager().getGroup().schedule(this, delay, TimeUnit.SECONDS);
    }

    abstract RFuture<Integer> execute();
    
    @Override
    public void run() {
        if (executor.getConnectionManager().isShuttingDown()) {
            return;
        }
        
        RFuture<Integer> future = execute();
        future.addListener(new FutureListener<Integer>() {
            @Override
            public void operationComplete(Future<Integer> future) throws Exception {
                if (!future.isSuccess()) {
                    schedule();
                    return;
                }

                Integer size = future.getNow();
                if (size == -1) {
                    schedule();
                    return;
                }

                if (sizeHistory.size() == 2) {
                    if (sizeHistory.peekFirst() > sizeHistory.peekLast()
                            && sizeHistory.peekLast() > size) {
                        delay = Math.min(maxDelay, (int)(delay*1.5));
                    }

//                    if (sizeHistory.peekFirst() < sizeHistory.peekLast()
//                            && sizeHistory.peekLast() < size) {
//                        prevDelay = Math.max(minDelay, prevDelay/2);
//                    }

                    if (sizeHistory.peekFirst().intValue() == sizeHistory.peekLast()
                            && sizeHistory.peekLast().intValue() == size) {
                        if (size == keysLimit) {
                            delay = Math.max(minDelay, delay/4);
                        }
                        if (size == 0) {
                            delay = Math.min(maxDelay, (int)(delay*1.5));
                        }
                    }

                    sizeHistory.pollFirst();
                }

                sizeHistory.add(size);
                schedule();
            }
        });
    }

}
