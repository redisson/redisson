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

public class PromiseDelegator<T> implements RPromise<T> {

    private final RPromise<T> promise;
    
    public PromiseDelegator(RPromise<T> promise) {
        super();
        this.promise = promise;
    }
    
    public RPromise<T> getInnerPromise() {
        return promise;
    }

    @Override
    public boolean isSuccess() {
        return promise.isSuccess();
    }

    @Override
    public boolean trySuccess(T result) {
        return promise.trySuccess(result);
    }

    @Override
    public Throwable cause() {
        return promise.cause();
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return promise.tryFailure(cause);
    }

    @Override
    public boolean setUncancellable() {
        return promise.setUncancellable();
    }

    @Override
    public RPromise<T> addListener(FutureListener<? super T> listener) {
        return promise.addListener(listener);
    }

    @Override
    public RPromise<T> addListeners(FutureListener<? super T>... listeners) {
        return promise.addListeners(listeners);
    }

    @Override
    public RPromise<T> removeListener(FutureListener<? super T> listener) {
        return promise.removeListener(listener);
    }

    @Override
    public RPromise<T> removeListeners(FutureListener<? super T>... listeners) {
        return promise.removeListeners(listeners);
    }

    @Override
    public RPromise<T> await() throws InterruptedException {
        return promise.await();
    }

    @Override
    public RPromise<T> awaitUninterruptibly() {
        return promise.awaitUninterruptibly();
    }

    @Override
    public RPromise<T> sync() throws InterruptedException {
        return promise.sync();
    }

    @Override
    public RPromise<T> syncUninterruptibly() {
        return promise.syncUninterruptibly();
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return promise.await(timeout, unit);
    }

    @Override
    public boolean isCancelled() {
        return promise.isCancelled();
    }

    @Override
    public boolean isDone() {
        return promise.isDone();
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return promise.await(timeoutMillis);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return promise.get();
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return promise.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return promise.get(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return promise.awaitUninterruptibly(timeoutMillis);
    }

    @Override
    public T getNow() {
        return promise.getNow();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return promise.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean hasListeners() {
        return promise.hasListeners();
    }
    
    
}
