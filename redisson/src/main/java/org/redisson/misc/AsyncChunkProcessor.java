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

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Utility for processing chunks from an iterator asynchronously without stack overflow.
 * Uses trampoline pattern to prevent StackOverflowError with large iterators.
 *
 * @author Konstantin Subbotin
 */
public final class AsyncChunkProcessor {

    private AsyncChunkProcessor() {
    }

    /**
     * Represents a chunk execution: the async operation and its success handler.
     * Return null from chunk handler to signal iteration complete.
     *
     * @param <R> the result type of the async operation
     */
    public static final class ChunkExecution<R> {
        private final CompletionStage<R> future;
        private final Consumer<R> onSuccess;

        public ChunkExecution(CompletionStage<R> future, Consumer<R> onSuccess) {
            this.future = future;
            this.onSuccess = onSuccess;
        }

        public CompletionStage<R> future() {
            return future;
        }

        public Consumer<R> onSuccess() {
            return onSuccess;
        }
    }

    /**
     * Processes chunks from the iterator until exhausted or handler returns null.
     * Stack-safe: handles both synchronous and asynchronous completions without stack growth.
     *
     * @param <R> the result type of each chunk operation
     * @param iter the iterator to process
     * @param chunkSize the chunk size hint passed to handler
     * @param chunkHandler builds and executes a chunk, returns null when done
     * @return a CompletionStage that completes when all chunks have been processed
     */
    public static <R> CompletionStage<Void> processAll(
            Iterator<String> iter,
            int chunkSize,
            BiFunction<Iterator<String>, Integer, ChunkExecution<R>> chunkHandler) {

        CompletableFuture<Void> result = new CompletableFuture<>();
        processNext(iter, chunkSize, chunkHandler, result);
        return result;
    }

    private static <R> void processNext(
            Iterator<String> iter,
            int chunkSize,
            BiFunction<Iterator<String>, Integer, ChunkExecution<R>> chunkHandler,
            CompletableFuture<Void> result) {

        // Loop handles synchronous completions without stack growth
        while (true) {
            ChunkExecution<R> execution = chunkHandler.apply(iter, chunkSize);

            // Null signals completion
            if (execution == null) {
                result.complete(null);
                return;
            }

            CompletableFuture<R> cf = execution.future().toCompletableFuture();

            // Synchronous completion: process in loop (no stack growth)
            if (cf.isDone()) {
                if (cf.isCompletedExceptionally()) {
                    propagateException(cf, result);
                    return;
                }
                execution.onSuccess().accept(cf.join());
                continue; // Next chunk in same stack frame
            }

            // Async: register callback and return (breaks stack chain)
            cf.whenComplete((r, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(unwrap(ex));
                    return;
                }
                execution.onSuccess().accept(r);
                processNext(iter, chunkSize, chunkHandler, result); // Trampoline
            });
            return;
        }
    }

    private static void propagateException(CompletableFuture<?> cf, CompletableFuture<Void> result) {
        try {
            cf.join();
        } catch (CompletionException e) {
            result.completeExceptionally(unwrap(e));
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
    }

    private static Throwable unwrap(Throwable ex) {
        if (ex instanceof CompletionException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
    }
}
