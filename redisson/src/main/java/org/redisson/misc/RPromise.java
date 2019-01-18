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
package org.redisson.misc;

import org.redisson.api.RFuture;

import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public interface RPromise<T> extends RFuture<T> {

    /**
     * Marks this future as a success and notifies all
     * listeners.
     * 
     * @param result object
     * @return {@code true} if and only if successfully marked this future as
     *         a success. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean trySuccess(T result);

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     * 
     * @param cause object
     * @return {@code true} if and only if successfully marked this future as
     *         a failure. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean tryFailure(Throwable cause);

    /**
     * Make this future impossible to cancel.
     *
     * @return {@code true} if and only if successfully marked this future as uncancellable or it is already done
     *         without being cancelled.  {@code false} if this future has been cancelled already.
     */
    boolean setUncancellable();

    @Override
    RPromise<T> addListener(FutureListener<? super T> listener);

    @Override
    RPromise<T> addListeners(FutureListener<? super T>... listeners);

    @Override
    RPromise<T> removeListener(FutureListener<? super T> listener);

    @Override
    RPromise<T> removeListeners(FutureListener<? super T>... listeners);

    @Override
    RPromise<T> await() throws InterruptedException;

    @Override
    RPromise<T> awaitUninterruptibly();

    @Override
    RPromise<T> sync() throws InterruptedException;

    @Override
    RPromise<T> syncUninterruptibly();

    boolean hasListeners();
    
}
