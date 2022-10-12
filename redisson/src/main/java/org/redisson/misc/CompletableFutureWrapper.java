/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 *
 * @author Nikita Koksharov
 * @param <V> value type
 */
public class CompletableFutureWrapper<V> implements RFuture<V> {

    private final CompletableFuture<V> future;
    private CompletableFuture<V> lastFuture;

    public CompletableFutureWrapper(V value) {
        this(CompletableFuture.completedFuture(value));
    }

    public CompletableFutureWrapper(Throwable ex) {
        this(new CompletableFuture<>());
        this.future.completeExceptionally(ex);
    }

    public CompletableFutureWrapper(CompletionStage<V> stage) {
        this.future = stage.toCompletableFuture();
        this.lastFuture = future;
    }

    public CompletableFutureWrapper(CompletableFuture<V> future) {
        this.future = future;
        this.lastFuture = future;
    }

    @Override
    public <U> CompletionStage<U> thenApply(Function<? super V, ? extends U> fn) {
        return future.thenApply(fn);
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(Function<? super V, ? extends U> fn) {
        return future.thenApplyAsync(fn);
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(Function<? super V, ? extends U> fn, Executor executor) {
        return future.thenApplyAsync(fn, executor);
    }

    @Override
    public CompletionStage<Void> thenAccept(Consumer<? super V> action) {
        return future.thenAccept(action);
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super V> action) {
        return future.thenAcceptAsync(action);
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(Consumer<? super V> action, Executor executor) {
        return future.thenAcceptAsync(action, executor);
    }

    @Override
    public CompletionStage<Void> thenRun(Runnable action) {
        return future.thenRun(action);
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action) {
        return future.thenRunAsync(action);
    }

    @Override
    public CompletionStage<Void> thenRunAsync(Runnable action, Executor executor) {
        return future.thenRunAsync(action, executor);
    }

    @Override
    public <U, V1> CompletionStage<V1> thenCombine(CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn) {
        return future.thenCombine(other, fn);
    }

    @Override
    public <U, V1> CompletionStage<V1> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn) {
        return future.thenCombineAsync(other, fn);
    }

    @Override
    public <U, V1> CompletionStage<V1> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super V, ? super U, ? extends V1> fn, Executor executor) {
        return future.thenCombineAsync(other, fn, executor);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action) {
        return future.thenAcceptBoth(other, action);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action) {
        return future.thenAcceptBothAsync(other, action);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super V, ? super U> action, Executor executor) {
        return future.thenAcceptBothAsync(other, action, executor);
    }

    @Override
    public CompletionStage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return future.runAfterBoth(other, action);
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return future.runAfterBothAsync(other, action);
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return future.runAfterBothAsync(other, action, executor);
    }

    @Override
    public <U> CompletionStage<U> applyToEither(CompletionStage<? extends V> other, Function<? super V, U> fn) {
        return future.applyToEither(other, fn);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends V> other, Function<? super V, U> fn) {
        return future.applyToEitherAsync(other, fn);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends V> other, Function<? super V, U> fn, Executor executor) {
        return future.applyToEitherAsync(other, fn, executor);
    }

    @Override
    public CompletionStage<Void> acceptEither(CompletionStage<? extends V> other, Consumer<? super V> action) {
        return future.acceptEither(other, action);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends V> other, Consumer<? super V> action) {
        return future.acceptEitherAsync(other, action);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends V> other, Consumer<? super V> action, Executor executor) {
        return future.acceptEitherAsync(other, action, executor);
    }

    @Override
    public CompletionStage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return future.runAfterEither(other, action);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return future.runAfterEitherAsync(other, action);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return future.runAfterEitherAsync(other, action, executor);
    }

    @Override
    public <U> CompletionStage<U> thenCompose(Function<? super V, ? extends CompletionStage<U>> fn) {
        return future.thenCompose(fn);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super V, ? extends CompletionStage<U>> fn) {
        return future.thenComposeAsync(fn);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(Function<? super V, ? extends CompletionStage<U>> fn, Executor executor) {
        return future.thenComposeAsync(fn, executor);
    }

    @Override
    public <U> CompletionStage<U> handle(BiFunction<? super V, Throwable, ? extends U> fn) {
        return future.handle(fn);
    }

    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super V, Throwable, ? extends U> fn) {
        return future.handleAsync(fn);
    }

    @Override
    public <U> CompletionStage<U> handleAsync(BiFunction<? super V, Throwable, ? extends U> fn, Executor executor) {
        return future.handleAsync(fn, executor);
    }

    @Override
    public CompletionStage<V> whenComplete(BiConsumer<? super V, ? super Throwable> action) {
        return future.whenComplete(action);
    }

    @Override
    public CompletionStage<V> whenCompleteAsync(BiConsumer<? super V, ? super Throwable> action) {
        return future.whenCompleteAsync(action);
    }

    @Override
    public CompletionStage<V> whenCompleteAsync(BiConsumer<? super V, ? super Throwable> action, Executor executor) {
        return future.whenCompleteAsync(action, executor);
    }

    @Override
    public CompletionStage<V> exceptionally(Function<Throwable, ? extends V> fn) {
        return future.exceptionally(fn);
    }

    @Override
    public CompletableFuture<V> toCompletableFuture() {
        return future;
    }

    public V getNow(V valueIfAbsent) {
        return future.getNow(valueIfAbsent);
    }

    public boolean complete(V value) {
        return future.complete(value);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    @Override
    public boolean isSuccess() {
        return future.isDone() && !future.isCompletedExceptionally();
    }

    @Override
    public Throwable cause() {
        if (future.isDone()) {
            try {
                future.getNow(null);
            } catch (CompletionException e) {
                return e.getCause();
            } catch (CancellationException e) {
                return e;
            }
        }
        return null;
    }

    @Override
    public V getNow() {
        return future.getNow(null);
    }

    @Override
    public V join() {
        return future.join();
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            future.get(timeout, unit);
        } catch (ExecutionException e) {
            // skip
        } catch (TimeoutException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public RFuture<V> sync() throws InterruptedException {
        try {
            future.get();
            return this;
        } catch (ExecutionException e) {
            throw (RuntimeException) e.getCause();
        }
    }

    @Override
    public RFuture<V> syncUninterruptibly() {
        try {
            future.join();
            return this;
        } catch (CompletionException e) {
            throw (RuntimeException) e.getCause();
        }
    }

    @Override
    public RFuture<V> await() throws InterruptedException {
        try {
            future.get();
        } catch (ExecutionException e) {
            // skip
        }
        return this;
    }

    @Override
    public RFuture<V> awaitUninterruptibly() {
        try {
            future.join();
        } catch (Exception e) {
            // skip
        }
        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            future.get(timeout, unit);
        } catch (ExecutionException | TimeoutException e) {
            // skip
        } catch (InterruptedException e) {
            awaitUninterruptibly(timeout, unit);
        }
        return future.isDone();
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return awaitUninterruptibly(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onComplete(BiConsumer<? super V, ? super Throwable> action) {
        lastFuture = lastFuture.whenComplete(action);
    }

}
