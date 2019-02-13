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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public class PromiseDelegator<T> implements RPromise<T> {

    private final RPromise<T> promise;
    
    public PromiseDelegator(RPromise<T> promise) {
        super();
        this.promise = promise;
    }
    
    public RPromise<T> getInnerPromise() {
        return promise;
    }

    public T join() {
        return promise.join();
    }

    public boolean isSuccess() {
        return promise.isSuccess();
    }

    public boolean trySuccess(T result) {
        return promise.trySuccess(result);
    }

    public Throwable cause() {
        return promise.cause();
    }

    public T getNow() {
        return promise.getNow();
    }

    public boolean tryFailure(Throwable cause) {
        return promise.tryFailure(cause);
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return promise.await(timeout, unit);
    }

    public boolean setUncancellable() {
        return promise.setUncancellable();
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return promise.await(timeoutMillis);
    }

    public RPromise<T> await() throws InterruptedException {
        return promise.await();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return promise.cancel(mayInterruptIfRunning);
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

    public boolean isCancelled() {
        return promise.isCancelled();
    }

    public boolean isDone() {
        return promise.isDone();
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

    public <U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn) {
        return promise.thenApply(fn);
    }

    public boolean awaitUninterruptibly(long timeoutMillis) {
        return promise.awaitUninterruptibly(timeoutMillis);
    }

    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return promise.thenApplyAsync(fn);
    }

    public <U> CompletionStage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return promise.thenApplyAsync(fn, executor);
    }

    public CompletionStage<Void> thenAccept(Consumer<? super T> action) {
        return promise.thenAccept(action);
    }

    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
        return promise.thenAcceptAsync(action);
    }

    @Override
    public boolean hasListeners() {
        return promise.hasListeners();
    }
    
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return promise.thenAcceptAsync(action, executor);
    }

    public CompletionStage<Void> thenRun(Runnable action) {
        return promise.thenRun(action);
    }

    public CompletionStage<Void> thenRunAsync(Runnable action) {
        return promise.thenRunAsync(action);
    }

    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return promise.thenRunAsync(action, executor);
    }

    public <U, V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return promise.thenCombine(other, fn);
    }

    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn) {
        return promise.thenCombineAsync(other, fn);
    }

    public <U, V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return promise.thenCombineAsync(other, fn, executor);
    }

    public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return promise.thenAcceptBoth(other, action);
    }

    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action) {
        return promise.thenAcceptBothAsync(other, action);
    }

    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
            BiConsumer<? super T, ? super U> action, Executor executor) {
        return promise.thenAcceptBothAsync(other, action, executor);
    }

    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return promise.runAfterBoth(other, action);
    }

    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return promise.runAfterBothAsync(other, action);
    }

    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return promise.runAfterBothAsync(other, action, executor);
    }

    public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return promise.applyToEither(other, fn);
    }

    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return promise.applyToEitherAsync(other, fn);
    }

    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn,
            Executor executor) {
        return promise.applyToEitherAsync(other, fn, executor);
    }

    public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return promise.acceptEither(other, action);
    }

    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return promise.acceptEitherAsync(other, action);
    }

    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action,
            Executor executor) {
        return promise.acceptEitherAsync(other, action, executor);
    }

    public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return promise.runAfterEither(other, action);
    }

    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return promise.runAfterEitherAsync(other, action);
    }

    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return promise.runAfterEitherAsync(other, action, executor);
    }

    public <U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return promise.thenCompose(fn);
    }

    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return promise.thenComposeAsync(fn);
    }

    public <U> CompletionStage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn,
            Executor executor) {
        return promise.thenComposeAsync(fn, executor);
    }

    public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return promise.exceptionally(fn);
    }

    public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return promise.whenComplete(action);
    }

    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return promise.whenCompleteAsync(action);
    }

    public CompletionStage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return promise.whenCompleteAsync(action, executor);
    }

    public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return promise.handle(fn);
    }

    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return promise.handleAsync(fn);
    }

    public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return promise.handleAsync(fn, executor);
    }

    public CompletableFuture<T> toCompletableFuture() {
        return promise.toCompletableFuture();
    }

    @Override
    public void onComplete(BiConsumer<? super T, ? super Throwable> action) {
        promise.onComplete(action);
    }

}
