package org.redisson.misc;

import org.junit.jupiter.api.Test;
import org.redisson.misc.AsyncChunkProcessor.ChunkExecution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncChunkProcessorTest {

    @Test
    void testNoStackOverflowWithManyChunks() {
        // Create iterator with 100,000 elements
        List<String> elements = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            elements.add("element-" + i);
        }
        Iterator<String> iter = elements.iterator();

        AtomicInteger processedCount = new AtomicInteger(0);
        int chunkSize = 100;

        CompletionStage<Void> result = AsyncChunkProcessor.processAll(iter, chunkSize, (it, size) -> {
            List<String> chunk = new ArrayList<>();
            while (it.hasNext() && chunk.size() < size) {
                chunk.add(it.next());
            }

            if (chunk.isEmpty()) {
                return null;
            }

            // Simulate synchronous completion (worst case for stack growth)
            CompletableFuture<Integer> future = CompletableFuture.completedFuture(chunk.size());

            return new ChunkExecution<>(future, processedCount::addAndGet);
        });

        // Should not throw StackOverflowError
        assertDoesNotThrow(() -> result.toCompletableFuture().join());
        assertEquals(100_000, processedCount.get());
    }

    @Test
    void testExceptionUnwrapping() {
        Iterator<String> iter = List.of("a").iterator();
        RuntimeException originalException = new RuntimeException("test error");

        CompletionStage<Void> result = AsyncChunkProcessor.processAll(iter, 1, (it, size) -> {
            if (!it.hasNext()) {
                return null;
            }
            it.next();

            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(originalException);

            return new ChunkExecution<>(future, r -> {});
        });

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> result.toCompletableFuture().get());

        // Should be unwrapped, not wrapped in CompletionException
        assertSame(originalException, ex.getCause());
    }

    @Test
    void testEmptyIterator() {
        Iterator<String> iter = Collections.emptyIterator();
        AtomicInteger callCount = new AtomicInteger(0);

        CompletionStage<Void> result = AsyncChunkProcessor.processAll(iter, 10, (it, size) -> {
            callCount.incrementAndGet();
            if (!it.hasNext()) {
                return null;
            }
            return new ChunkExecution<>(CompletableFuture.completedFuture(null), r -> {});
        });

        assertDoesNotThrow(() -> result.toCompletableFuture().join());
        assertEquals(1, callCount.get()); // Called once, returned null immediately
    }

    @Test
    void testAsyncCompletion() throws Exception {
        List<String> elements = List.of("a", "b", "c");
        Iterator<String> iter = elements.iterator();
        AtomicInteger processedCount = new AtomicInteger(0);

        CompletionStage<Void> result = AsyncChunkProcessor.processAll(iter, 1, (it, size) -> {
            if (!it.hasNext()) {
                return null;
            }
            it.next();

            // Simulate async completion
            CompletableFuture<Integer> future = new CompletableFuture<>();
            new Thread(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                future.complete(1);
            }).start();

            return new ChunkExecution<>(future, processedCount::addAndGet);
        });

        result.toCompletableFuture().get();
        assertEquals(3, processedCount.get());
    }

    @Test
    void testMixedSyncAsyncCompletion() throws Exception {
        List<String> elements = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            elements.add("element-" + i);
        }
        Iterator<String> iter = elements.iterator();
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger index = new AtomicInteger(0);

        CompletionStage<Void> result = AsyncChunkProcessor.processAll(iter, 1, (it, size) -> {
            if (!it.hasNext()) {
                return null;
            }
            it.next();
            int currentIndex = index.getAndIncrement();

            CompletableFuture<Integer> future;
            if (currentIndex % 10 == 0) {
                // Every 10th element completes async
                future = new CompletableFuture<>();
                CompletableFuture<Integer> f = future;
                new Thread(() -> f.complete(1)).start();
            } else {
                // Others complete sync
                future = CompletableFuture.completedFuture(1);
            }

            return new ChunkExecution<>(future, processedCount::addAndGet);
        });

        result.toCompletableFuture().get();
        assertEquals(100, processedCount.get());
    }
}
