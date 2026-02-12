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
package org.redisson.mapreduce;

import org.redisson.api.RExecutorService;
import org.redisson.api.RFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SubTasksExecutor {

    private final List<CompletableFuture<?>> futures = new ArrayList<>();
    private final RExecutorService executor;
    private final long startTime;
    private final long timeout;

    public SubTasksExecutor(RExecutorService executor, long startTime, long timeout) {
        this.executor = executor;
        this.startTime = startTime;
        this.timeout = timeout;
    }
    
    public void submit(Runnable runnable) {
        RFuture<?> future = executor.submitAsync(runnable);
        futures.add(future.toCompletableFuture());
    }
    
    private void cancel(List<CompletableFuture<?>> futures) {
        for (CompletableFuture<?> future : futures) {
            future.cancel(true);
        }
    }
    
    private boolean isTimeoutExpired(long timeSpent) {
        return timeSpent > timeout && timeout > 0;
    }
    
    public boolean await() throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            cancel(futures);
            return false;
        }
        
        long timeSpent = System.currentTimeMillis() - startTime;
        if (isTimeoutExpired(timeSpent)) {
            cancel(futures);
            throw new MapReduceTimeoutException();
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            if (timeout > 0) {
                try {
                    future.get(timeout - timeSpent, TimeUnit.MILLISECONDS);
                } catch (ExecutionException e) {
                    // skip
                } catch (TimeoutException e) {
                    cancel(futures);
                    throw new MapReduceTimeoutException();
                }
            } else if (timeout == 0) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    throw (Exception) e.getCause();
                }
            }
        } catch (InterruptedException e) {
            cancel(futures);
            return false;
        }
        return true;
    }
    
}
