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

import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class PromiseDelegator<T> implements RPromise<T> {

    private final RPromise<T> promise;
    
    public PromiseDelegator(RPromise<T> promise) {
        super();
        this.promise = promise;
    }
    
    public RPromise<T> getInnerPromise() {
        return promise;
    }

    public RPromise<T> setSuccess(T result) {
        return promise.setSuccess(result);
    }

    public boolean isSuccess() {
        return promise.isSuccess();
    }

    public boolean trySuccess(T result) {
        return promise.trySuccess(result);
    }

    public boolean isCancellable() {
        return promise.isCancellable();
    }

    public Throwable cause() {
        return promise.cause();
    }

    public Promise<T> setFailure(Throwable cause) {
        return promise.setFailure(cause);
    }

    public boolean tryFailure(Throwable cause) {
        return promise.tryFailure(cause);
    }

    public boolean setUncancellable() {
        return promise.setUncancellable();
    }

    public RPromise<T> addListener(FutureListener<? super T> listener) {
        return promise.addListener(listener);
    }

    public RPromise<T> addListeners(FutureListener<? super T>... listeners) {
        return promise.addListeners(listeners);
    }

    public RPromise<T> removeListener(FutureListener<? super T> listener) {
        return promise.removeListener(listener);
    }

    public RPromise<T> removeListeners(FutureListener<? super T>... listeners) {
        return promise.removeListeners(listeners);
    }

    public RPromise<T> await() throws InterruptedException {
        return promise.await();
    }

    public RPromise<T> awaitUninterruptibly() {
        return promise.awaitUninterruptibly();
    }

    public RPromise<T> sync() throws InterruptedException {
        return promise.sync();
    }

    public RPromise<T> syncUninterruptibly() {
        return promise.syncUninterruptibly();
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return promise.await(timeout, unit);
    }

    public boolean isCancelled() {
        return promise.isCancelled();
    }

    public boolean isDone() {
        return promise.isDone();
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return promise.await(timeoutMillis);
    }

    public T get() throws InterruptedException, ExecutionException {
        return promise.get();
    }

    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return promise.awaitUninterruptibly(timeout, unit);
    }

    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return promise.get(timeout, unit);
    }

    public boolean awaitUninterruptibly(long timeoutMillis) {
        return promise.awaitUninterruptibly(timeoutMillis);
    }

    public T getNow() {
        return promise.getNow();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return promise.cancel(mayInterruptIfRunning);
    }
    
    
}
