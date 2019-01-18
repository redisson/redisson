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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.netty.util.concurrent.FutureListener;

/**
 * Represents the result of an asynchronous computation
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 */
public interface RFuture<V> extends java.util.concurrent.Future<V>, CompletionStage<V> {

    /**
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully.
     * 
     * @return {@code true} if future was completed successfully
     */
    boolean isSuccess();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     *
     * @return the cause of the failure.
     *         {@code null} if succeeded or this future is not
     *         completed yet.
     */
    Throwable cause();
    
    /**
     * Return the result without blocking. If the future is not done yet this will return {@code null}.
     *
     * As it is possible that a {@code null} value is used to mark the future as successful you also need to check
     * if the future is really done with {@link #isDone()} and not relay on the returned {@code null} value.
     * 
     * @return object
     */
    V getNow();
    
    /**
     * Returns the result value when complete, or throws an
     * (unchecked) exception if completed exceptionally. To better
     * conform with the use of common functional forms, if a
     * computation involved in the completion of this
     * CompletableFuture threw an exception.
     *
     * @return the result value
     */
    V join();
    
    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @param timeout - wait timeout
     * @param unit - time unit
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @param timeoutMillis - timeout value
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;
    
    /**
     * Use methods from {@link CompletionStage} interface
     * 
     * @param listener - listener for future object
     * @return Future object
     */
    @Deprecated
    RFuture<V> addListener(FutureListener<? super V> listener);

    /**
     * Use methods from {@link CompletionStage} interface
     * 
     * @param listeners - listeners for future object
     * @return Future object
     */
    @Deprecated
    RFuture<V> addListeners(FutureListener<? super V>... listeners);

    @Deprecated
    RFuture<V> removeListener(FutureListener<? super V> listener);

    @Deprecated
    RFuture<V> removeListeners(FutureListener<? super V>... listeners);

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     * @return Future object
     */
    RFuture<V> sync() throws InterruptedException;

    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     * 
     * @return Future object
     */
    RFuture<V> syncUninterruptibly();

    /**
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     * @return Future object
     */
    RFuture<V> await() throws InterruptedException;

    /**
     * Waits for this future to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     * 
     * @return Future object
     */
    RFuture<V> awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @param timeout - timeout value
     * @param unit - timeout unit value
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     * 
     * @param timeoutMillis - timeout value
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    
}
