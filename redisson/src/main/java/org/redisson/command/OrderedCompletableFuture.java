/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.command;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class OrderedCompletableFuture<V> extends CompletableFuture<V> {

    final Queue<BiConsumer<? super V, ? super Throwable>> actions = new ConcurrentLinkedQueue<>();

    final CompletableFuture<V> parentFuture;

    public OrderedCompletableFuture(CompletableFuture<V> parentFuture) {
        this.parentFuture = parentFuture;
        parentFuture.whenComplete((r, e) -> {
            invokeActions(r, e);
        });
    }

    void invokeActions(V r, Throwable e) {
        while (true) {
            BiConsumer<? super V, ? super Throwable> action = actions.poll();
            if (action != null) {
                action.accept(r, e);
            } else {
                break;
            }
        }
    }

    void invokeActions() {
        try {
            V r = parentFuture.getNow(null);
            invokeActions(r, null);
        } catch (CompletionException e) {
            invokeActions(null, e.getCause());
        }
    }

    @Override
    public CompletableFuture<V> whenComplete(BiConsumer<? super V, ? super Throwable> action) {
        actions.add(action);
        if (parentFuture.isDone()) {
            invokeActions();
        }
        return this;
    }

    @Override
    public V getNow(V valueIfAbsent) {
        return parentFuture.getNow(valueIfAbsent);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return parentFuture.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean complete(V value) {
        return parentFuture.complete(value);
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        return parentFuture.completeExceptionally(ex);
    }

    @Override
    public boolean isDone() {
        return parentFuture.isDone();
    }

    @Override
    public boolean isCompletedExceptionally() {
        return parentFuture.isCompletedExceptionally();
    }

    @Override
    public boolean isCancelled() {
        return parentFuture.isCancelled();
    }

    @Override
    public V join() {
        return parentFuture.join();
    }
}
