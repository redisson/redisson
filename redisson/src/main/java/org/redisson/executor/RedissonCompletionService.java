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
package org.redisson.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.api.RScheduledExecutorService;

/**
 * A {@link CompletionService} that uses a supplied {@link Executor}
 * to execute tasks.  This class arranges that submitted tasks are,
 * upon completion, placed on a queue accessible using {@code take}.
 * The class is lightweight enough to be suitable for transient use
 * when processing groups of tasks.
 *  
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonCompletionService<V> implements CompletionService<V> {

    protected final RScheduledExecutorService executorService;

    protected final BlockingQueue<RFuture<V>> completionQueue;
    
    public RedissonCompletionService(RScheduledExecutorService executorService) {
        this(executorService, null);
    }

    public RedissonCompletionService(RScheduledExecutorService executorService, BlockingQueue<RFuture<V>> completionQueue) {
        if (executorService == null) {
            throw new NullPointerException("executorService can't be null");
        }
        
        this.executorService = executorService;
        if (completionQueue == null) {
            completionQueue = new LinkedBlockingQueue<RFuture<V>>();
        }
        
        this.completionQueue = completionQueue;
    }

    @Override
    public Future<V> submit(Callable<V> task) {
        if (task == null) {
            throw new NullPointerException("taks can't be null");
        }
        
        RFuture<V> f = executorService.submit(task);
        f.onComplete((res, e) -> {
            completionQueue.add(f);
        });
        return f;
    }

    @Override
    public Future<V> submit(Runnable task, V result) {
        if (task == null) {
            throw new NullPointerException("taks can't be null");
        }
        
        RFuture<V> f = executorService.submit(task, result);
        f.onComplete((res, e) -> {
            completionQueue.add(f);
        });
        return f;
    }

    @Override
    public Future<V> take() throws InterruptedException {
        return completionQueue.take();
    }

    @Override
    public Future<V> poll() {
        return completionQueue.poll();
    }

    @Override
    public Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return completionQueue.poll(timeout, unit);
    }

}
