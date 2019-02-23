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
package org.redisson.mapreduce;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.redisson.api.RExecutorService;
import org.redisson.api.RFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SubTasksExecutor {

    public static class LatchListener implements BiConsumer<Object, Throwable> {

        private CountDownLatch latch;
        
        public LatchListener() {
        }
        
        public LatchListener(CountDownLatch latch) {
            super();
            this.latch = latch;
        }

        @Override
        public void accept(Object t, Throwable u) {
            latch.countDown();
        }
        
    }
    
    private final List<RFuture<?>> futures = new ArrayList<RFuture<?>>();
    private final CountDownLatch latch;
    private final RExecutorService executor;
    private final long startTime;
    private final long timeout;

    public SubTasksExecutor(RExecutorService executor, int workersAmount, long startTime, long timeout) {
        this.executor = executor;
        this.latch = new CountDownLatch(workersAmount);
        this.startTime = startTime;
        this.timeout = timeout;
    }
    
    public void submit(Runnable runnable) {
        RFuture<?> future = executor.submitAsync(runnable);
        future.onComplete(new LatchListener(latch));
        futures.add(future);
    }
    
    private void cancel(List<RFuture<?>> futures) {
        for (RFuture<?> future : futures) {
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
        try {
            if (timeout > 0 && !latch.await(timeout - timeSpent, TimeUnit.MILLISECONDS)) {
                cancel(futures);
                throw new MapReduceTimeoutException();
            }
            if (timeout == 0) {
                latch.await();
            }
        } catch (InterruptedException e) {
            cancel(futures);
            return false;
        }
        for (RFuture<?> rFuture : futures) {
            if (!rFuture.isSuccess()) {
                throw (Exception) rFuture.cause();
            }
        }
        return true;
    }
    
}
