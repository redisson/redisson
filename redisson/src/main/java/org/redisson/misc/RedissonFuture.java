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
import io.netty.util.concurrent.FutureListener;

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

    public RFuture<T> addListener(FutureListener<? super T> listener) {
        future.addListener(listener);
        return this;
    }

    public RFuture<T> addListeners(FutureListener<? super T>... listeners) {
        future.addListeners(listeners);
        return this;
    }

    public RFuture<T> removeListener(FutureListener<? super T> listener) {
        future.removeListener(listener);
        return this;
    }

    public RFuture<T> removeListeners(FutureListener<? super T>... listeners) {
        future.removeListeners(listeners);
        return this;
    }

    public RFuture<T> sync() throws InterruptedException {
        future.sync();
        return this;
    }

    public RFuture<T> syncUninterruptibly() {
        future.syncUninterruptibly();
        return this;
    }

    public RFuture<T> await() throws InterruptedException {
        future.await();
        return this;
    }

    public RFuture<T> awaitUninterruptibly() {
        future.awaitUninterruptibly();
        return this;
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
