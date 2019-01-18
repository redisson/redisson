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
package org.redisson;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.command.CommandAsyncExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <R> return type
 */
public class MapWriteBehindListener<R> implements FutureListener<R> {

    private static final Logger log = LoggerFactory.getLogger(MapWriteBehindListener.class);
    
    private final AtomicInteger writeBehindCurrentThreads;
    private final Queue<Runnable> writeBehindTasks;
    private final int threadsAmount;
    private final MapWriterTask<R> task;
    private final CommandAsyncExecutor commandExecutor;
    
    public MapWriteBehindListener(CommandAsyncExecutor commandExecutor, MapWriterTask<R> task, AtomicInteger writeBehindCurrentThreads, Queue<Runnable> writeBehindTasks, int threadsAmount) {
        super();
        this.threadsAmount = threadsAmount;
        this.commandExecutor = commandExecutor;
        this.task = task;
        this.writeBehindCurrentThreads = writeBehindCurrentThreads;
        this.writeBehindTasks = writeBehindTasks;
    }

    @Override
    public void operationComplete(Future<R> future) throws Exception {
        if (future.isSuccess() && task.condition(future)) {
            enqueueRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        task.execute();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            });
        }
    }

    private void enqueueRunnable(Runnable runnable) {
        if (runnable != null) {
            writeBehindTasks.add(runnable);
        }
        
        if (writeBehindCurrentThreads.incrementAndGet() <= threadsAmount) {
            commandExecutor.getConnectionManager().getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            Runnable runnable = writeBehindTasks.poll();
                            if (runnable != null) {
                                runnable.run();
                            } else {
                                break;
                            }
                        }
                    } finally {
                        if (writeBehindCurrentThreads.decrementAndGet() == 0 && !writeBehindTasks.isEmpty()) {
                            enqueueRunnable(null);
                        }
                    }
                }
            });
        } else {
            writeBehindCurrentThreads.decrementAndGet();
        }
    }

    
}
