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

import java.lang.reflect.Field;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.RFuture;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type of object
 */
public class RedissonPromise<T> extends CompletableFuture<T> implements RPromise<T> {

    private volatile boolean uncancellable;
    
    private final int SUCCESS = 1;
    private final int FAILED = 2;
    private final int CANCELED = 3;    
    
    private static final Field listenersField;
    
    static {
        try {
            listenersField = DefaultPromise.class.getDeclaredField("listeners");
            listenersField.setAccessible(true);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final Promise<T> promise = ImmediateEventExecutor.INSTANCE.newPromise();
    private final AtomicInteger status = new AtomicInteger();
    
    public RedissonPromise() {
    }
    
    public static <V> RFuture<V> newFailedFuture(Throwable cause) {
        RedissonPromise<V> future = new RedissonPromise<V>();
        future.tryFailure(cause);
        return future;
    }
    
    public static <V> RFuture<V> newSucceededFuture(V result) {
        RedissonPromise<V> future = new RedissonPromise<V>();
        future.trySuccess(result);
        return future;
    }
    
    @Override
    public boolean isSuccess() {
        return isDone() && !isCompletedExceptionally();
    }

    @Override
    public synchronized boolean trySuccess(T result) {
        if (status.compareAndSet(0, SUCCESS)) {
            complete(result);
            promise.trySuccess(result);
            return true;
        }
        return false;
    }

    @Override
    public Throwable cause() {
        try {
            getNow(null);
        } catch (CancellationException e) {
            return e;
        } catch (CompletionException e) {
            return e.getCause();
        }
        return null;
    }

    @Override
    public synchronized boolean tryFailure(Throwable cause) {
        if (status.compareAndSet(0, FAILED)) {
            completeExceptionally(cause);
            promise.tryFailure(cause);
            return true;
        }
        return false;
    }

    @Override
    public boolean setUncancellable() {
        if (!isDone()) {
            uncancellable = true;
        }
        return uncancellable;
    }

    @Override
    public RPromise<T> addListener(FutureListener<? super T> listener) {
        promise.addListener(listener);
        return this;
    }

    @Override
    public RPromise<T> addListeners(FutureListener<? super T>... listeners) {
        promise.addListeners(listeners);
        return this;
    }

    @Override
    public RPromise<T> removeListener(FutureListener<? super T> listener) {
        promise.removeListener(listener);
        return this;
    }

    @Override
    public RPromise<T> removeListeners(FutureListener<? super T>... listeners) {
        promise.removeListeners(listeners);
        return this;
    }

    @Override
    public RPromise<T> await() throws InterruptedException {
        try {
            get();
        } catch (ExecutionException | CancellationException e) {
            // skip
        }
        return this;
    }

    @Override
    public RPromise<T> awaitUninterruptibly() {
        try {
            return await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return this;
    }

    @Override
    public RPromise<T> sync() throws InterruptedException {
        try {
            get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CancellationException) {
                throw (CancellationException)e.getCause();
            }
            PlatformDependent.throwException(e.getCause());
        }
        return this;
    }

    @Override
    public RPromise<T> syncUninterruptibly() {
        try {
            join();
        } catch (CompletionException e) {
            PlatformDependent.throwException(e.getCause());
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            get(timeout, unit);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof CancellationException) {
                throw (CancellationException)e.getCause();
            }
            throw new CompletionException(e.getCause());
        } catch (TimeoutException e) {
            return false;
        }
        return isDone();
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return awaitUninterruptibly(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public T getNow() {
        try {
            return getNow(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (uncancellable) {
            return false;
        }
        if (status.compareAndSet(0, CANCELED)) {
            promise.cancel(mayInterruptIfRunning);
            return super.cancel(mayInterruptIfRunning);
        }
        return false;
    }
    
    @Override
    public boolean hasListeners() {
        try {
            return listenersField.get(promise) != null || getNumberOfDependents() > 0;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "RedissonPromise [promise=" + promise + "]";
    }

}
