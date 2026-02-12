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
package org.redisson.misc;

import org.redisson.api.AsyncIterator;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 *
 * @author seakider
 *
 */
public class CompositeAsyncIterator<T> implements AsyncIterator<T> {
    private final Iterator<AsyncIterator<T>> iterator;
    private AsyncIterator<T> currentAsyncIterator;
    private final int limit;
    private int counter;
    
    public CompositeAsyncIterator(List<AsyncIterator<T>> asyncIterators, int limit) {
        this.iterator = asyncIterators.iterator();
        this.limit = limit;
    }
    
    @Override
    public CompletionStage<Boolean> hasNext() {
        if (limit > 0 && limit <= counter) {
            return CompletableFuture.completedFuture(false);
        }
        while (currentAsyncIterator == null && iterator.hasNext()) {
            currentAsyncIterator = iterator.next();
        }
        if (currentAsyncIterator == null) {
            return CompletableFuture.completedFuture(false);
            
        }
        CompletionStage<Boolean> main = currentAsyncIterator.hasNext();
        return main.thenCompose(v -> {
            if (v) {
                return CompletableFuture.completedFuture(true);
            } else {
                currentAsyncIterator = null;
                return hasNext();
            }
        });
    }
    
    @Override
    public CompletionStage<T> next() {
        CompletableFuture<T> result = new CompletableFuture<>();
        hasNext().thenAccept(v1 -> {
            if (!v1) {
                result.completeExceptionally(new NoSuchElementException());
                return;
            }
            currentAsyncIterator.next().whenComplete((v2, e2) -> {
                if (e2 != null) {
                    result.completeExceptionally(new CompletionException(e2));
                    return;
                }
                result.complete(v2);
                counter++;
            });
        });
        return result;
    }
}
