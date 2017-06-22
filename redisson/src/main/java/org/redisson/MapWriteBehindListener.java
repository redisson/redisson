/**
 * Copyright 2016 Nikita Koksharov
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

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
    
    private static final AtomicBoolean sent = new AtomicBoolean();
    private static final Queue<Runnable> operations = new ConcurrentLinkedQueue<Runnable>();
    
    private final MapWriterTask<R> task;
    private final CommandAsyncExecutor commandExecutor;
    
    public MapWriteBehindListener(CommandAsyncExecutor commandExecutor, MapWriterTask<R> task) {
        super();
        this.commandExecutor = commandExecutor;
        this.task = task;
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
            operations.add(runnable);
        }
        
        if (sent.compareAndSet(false, true)) {
            commandExecutor.getConnectionManager().getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            Runnable runnable = operations.poll();
                            if (runnable != null) {
                                runnable.run();
                            } else {
                                break;
                            }
                        }
                    } finally {
                        sent.set(false);
                        if (!operations.isEmpty()) {
                            enqueueRunnable(null);
                        }
                    }
                }
            });
        }
    }

    
}
