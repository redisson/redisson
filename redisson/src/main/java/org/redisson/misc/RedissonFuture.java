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
package org.redisson.misc;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.redisson.api.RFuture;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T>
 */
public class RedissonFuture<T> implements RFuture<T> {

    private final Future<T> future;

    public RedissonFuture(Future<T> future) {
        super();
        this.future = future;
    }
    
    public Future<T> getInnerFuture() {
        return future;
    }

    public boolean isSuccess() {
        return future.isSuccess();
    }

    public boolean isCancellable() {
        return future.isCancellable();
    }

    public Throwable cause() {
        return future.cause();
    }

    public Future<T> addListener(GenericFutureListener<? extends Future<? super T>> listener) {
        return future.addListener(listener);
    }

    public Future<T> addListeners(GenericFutureListener<? extends Future<? super T>>... listeners) {
        return future.addListeners(listeners);
    }

    public Future<T> removeListener(GenericFutureListener<? extends Future<? super T>> listener) {
        return future.removeListener(listener);
    }

    public Future<T> removeListeners(GenericFutureListener<? extends Future<? super T>>... listeners) {
        return future.removeListeners(listeners);
    }

    public Future<T> sync() throws InterruptedException {
        return future.sync();
    }

    public Future<T> syncUninterruptibly() {
        return future.syncUninterruptibly();
    }

    public Future<T> await() throws InterruptedException {
        return future.await();
    }

    public Future<T> awaitUninterruptibly() {
        return future.awaitUninterruptibly();
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return future.await(timeout, unit);
    }

    public boolean isCancelled() {
        return future.isCancelled();
    }

    public boolean isDone() {
        return future.isDone();
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return future.await(timeoutMillis);
    }

    public T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return future.awaitUninterruptibly(timeout, unit);
    }

    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    public boolean awaitUninterruptibly(long timeoutMillis) {
        return future.awaitUninterruptibly(timeoutMillis);
    }

    public T getNow() {
        return future.getNow();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }
    
    
    
}
