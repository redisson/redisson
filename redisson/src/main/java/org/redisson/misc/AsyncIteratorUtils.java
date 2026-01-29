/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Utility for iterating over elements asynchronously without stack overflow.
 * Uses trampoline pattern to prevent StackOverflowError with large iterators.
 *
 * @author Konstantin Subbotin
 */
public final class AsyncIteratorUtils {

    private AsyncIteratorUtils() {
    }

    /**
     * Processes each element from the iterator sequentially using the provided async function.
     * Stack-safe: handles both synchronous and asynchronous completions without stack growth.
     *
     * @param <T> the type of elements in the iterator
     * @param iter the iterator to process
     * @param processor the async function to apply to each element
     * @return a CompletionStage that completes when all elements have been processed
     */
    public static <T> CompletionStage<Void> forEachAsync(Iterator<T> iter,
                                                          Function<T, CompletionStage<Void>> processor) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        processNext(iter, processor, result);
        return result;
    }

    private static <T> void processNext(Iterator<T> iter,
                                         Function<T, CompletionStage<Void>> processor,
                                         CompletableFuture<Void> result) {
        // Loop handles synchronous completions without stack growth
        while (true) {
            if (!iter.hasNext()) {
                result.complete(null);
                return;
            }

            T element = iter.next();
            CompletionStage<Void> stage = processor.apply(element);
            CompletableFuture<Void> cf = stage.toCompletableFuture();

            // Synchronous completion: process in loop (no stack growth)
            if (cf.isDone()) {
                if (cf.isCompletedExceptionally()) {
                    try {
                        cf.join();
                    } catch (CompletionException e) {
                        Throwable cause = e.getCause();
                        if (cause == null) {
                            cause = e;
                        }
                        result.completeExceptionally(cause);
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                    return;
                }
                continue; // Next element in same stack frame
            }

            // Async: register callback and return (breaks stack chain)
            cf.whenComplete((r, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(unwrap(ex));
                    return;
                }
                processNext(iter, processor, result); // Trampoline
            });
            return;
        }
    }

    private static Throwable unwrap(Throwable ex) {
        if (ex instanceof CompletionException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
    }
}
