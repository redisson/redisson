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
package org.redisson.api;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Redis based implementation of {@link java.util.concurrent.ScheduledExecutorService}
 * 
 * @author Nikita Koksharov
 *
 */
public interface RScheduledExecutorServiceAsync extends RExecutorServiceAsync {

    /**
     * Creates in async mode and executes a one-shot action that becomes enabled
     * after the given delay.
     *
     * @param task the task to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @return RScheduledFuture with listeners support
     */
    RScheduledFuture<?> scheduleAsync(Runnable task, long delay, TimeUnit unit);
    
    /**
     * Creates in async mode and executes a ScheduledFuture that becomes enabled after the
     * given delay.
     *
     * @param task the function to execute
     * @param delay the time from now to delay execution
     * @param unit the time unit of the delay parameter
     * @param <V> the type of the callable's result
     * @return RScheduledFuture with listeners support
     */
    <V> RScheduledFuture<V> scheduleAsync(Callable<V> task, long delay, TimeUnit unit);
    
    /**
     * Creates in async mode and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the given
     * period.
     * If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.  If any execution of this task
     * takes longer than its period, then subsequent executions
     * may start late, but will not concurrently execute.
     *
     * @param task the task to execute
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions
     * @param unit the time unit of the initialDelay and period parameters
     * @return RScheduledFuture with listeners support
     */
    RScheduledFuture<?> scheduleAtFixedRateAsync(Runnable task, long initialDelay, long period, TimeUnit unit);

    /**
     * Creates in async mode and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the
     * given delay between the termination of one execution and the
     * commencement of the next.  If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.
     *
     * @param task the task to execute
     * @param initialDelay the time to delay first execution
     * @param delay the delay between the termination of one
     * execution and the commencement of the next
     * @param unit the time unit of the initialDelay and delay parameters
     * @return RScheduledFuture with listeners support
     */
    RScheduledFuture<?> scheduleWithFixedDelayAsync(Runnable task, long initialDelay, long delay, TimeUnit unit);

    /**
     * Creates in async mode and executes a periodic action with cron schedule object.
     * If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.  If any execution of this task
     * takes longer than its period, then subsequent executions
     * may start late, but will not concurrently execute.
     *
     * @param task the task to execute
     * @param cronSchedule cron schedule object
     * @return RScheduledFuture with listeners support
     */
    RScheduledFuture<?> scheduleAsync(Runnable task, CronSchedule cronSchedule);
}
