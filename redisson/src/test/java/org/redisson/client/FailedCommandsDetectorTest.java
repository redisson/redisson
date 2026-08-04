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
package org.redisson.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailedCommandsDetectorTest {

    private static final int CONCURRENT_CALLERS = 8;
    private static final RedisTimeoutException FAILURE = new RedisTimeoutException("failed");

    @Test
    void countsFailuresWithTheSameTimestamp() {
        TestFailedCommandsDetector detector = new TestFailedCommandsDetector(1000, 3);

        detector.onCommandFailed(new RedisTimeoutException("first"));
        detector.onCommandFailed(new RedisTimeoutException("second"));
        detector.onCommandFailed(new RedisTimeoutException("third"));

        assertThat(detector.isNodeFailed()).isTrue();
        assertThat(detector.isNodeFailed()).isFalse();
    }

    @Test
    void retainsFailuresAtTheCheckIntervalBoundary() {
        TestFailedCommandsDetector detector = new TestFailedCommandsDetector(1000, 1);
        detector.onCommandFailed(FAILURE);

        detector.advanceTime(1000);

        assertThat(detector.isNodeFailed()).isTrue();
    }

    @Test
    void removesFailuresOutsideTheCheckIntervalAndKeepsCountConsistent() {
        TestFailedCommandsDetector detector = new TestFailedCommandsDetector(1000, 2);
        detector.onCommandFailed(new RedisTimeoutException("expired"));

        detector.advanceTime(1001);
        detector.onCommandFailed(new RedisTimeoutException("current"));

        assertThat(detector.isNodeFailed()).isFalse();

        detector.onCommandFailed(new RedisTimeoutException("second-current"));

        assertThat(detector.isNodeFailed()).isTrue();
        assertThat(detector.isNodeFailed()).isFalse();
    }

    @Test
    void removesExpiredFailuresInsertedAfterCurrentFailures() {
        TestFailedCommandsDetector detector = new TestFailedCommandsDetector(1000, 2);
        detector.setTime(1001);
        detector.onCommandFailed(new RedisTimeoutException("current"));
        detector.setTime(0);
        detector.onCommandFailed(new RedisTimeoutException("expired"));

        detector.setTime(1001);

        assertThat(detector.isNodeFailed()).isFalse();

        detector.onCommandFailed(new RedisTimeoutException("second-current"));

        assertThat(detector.isNodeFailed()).isTrue();
    }

    @Test
    void commandFailuresCanBeRecordedConcurrently() throws Exception {
        BlockingTimeDetector detector = new BlockingTimeDetector(1000, 2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        detector.blockNextTimeRead();
        try {
            Future<?> first = executor.submit(() -> detector.onCommandFailed(FAILURE));
            detector.awaitBlockedTimeRead();

            Future<?> second = executor.submit(() -> detector.onCommandFailed(FAILURE));
            second.get(5, TimeUnit.SECONDS);

            detector.releaseTimeRead();
            first.get(5, TimeUnit.SECONDS);

            assertThat(detector.isNodeFailed()).isTrue();
        } finally {
            detector.releaseTimeRead();
            executor.shutdownNow();
        }
    }

    @Test
    void thresholdCanOnlyBeConsumedOnceConcurrently() throws Exception {
        TestFailedCommandsDetector detector = new TestFailedCommandsDetector(1000, 1);
        detector.onCommandFailed(FAILURE);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS);
        try {
            List<Boolean> results = runConcurrently(
                    executor, CONCURRENT_CALLERS, detector::isNodeFailed);

            assertThat(results.stream().filter(Boolean::booleanValue).count()).isOne();
            assertThat(detector.isNodeFailed()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failureWaitingForThresholdResetIsKeptForTheNextTransition() throws Exception {
        BlockingTimeDetector detector = new BlockingTimeDetector(1000, 1);
        detector.onCommandFailed(FAILURE);
        detector.blockNextTimeRead();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> transition = executor.submit(detector::isNodeFailed);
            detector.awaitBlockedTimeRead();

            CountDownLatch writerStarted = new CountDownLatch(1);
            Future<?> nextFailure = executor.submit(() -> {
                writerStarted.countDown();
                detector.onCommandFailed(FAILURE);
            });
            assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> nextFailure.get(1, TimeUnit.SECONDS))
                    .isInstanceOf(TimeoutException.class);

            detector.releaseTimeRead();

            assertThat(transition.get(5, TimeUnit.SECONDS)).isTrue();
            nextFailure.get(5, TimeUnit.SECONDS);
            assertThat(detector.isNodeFailed()).isTrue();
            assertThat(detector.isNodeFailed()).isFalse();
        } finally {
            detector.releaseTimeRead();
            executor.shutdownNow();
        }
    }

    private static <T> List<T> runConcurrently(
            ExecutorService executor, int callers, Callable<T> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        return results;
    }

    private static class TestFailedCommandsDetector extends FailedCommandsDetector {

        private final AtomicLong time = new AtomicLong();

        private TestFailedCommandsDetector(long checkInterval, int failedCommandsLimit) {
            super(checkInterval, failedCommandsLimit);
        }

        @Override
        protected long getCurrentTime() {
            return time.get();
        }

        private void advanceTime(long delta) {
            time.addAndGet(delta);
        }

        private void setTime(long value) {
            time.set(value);
        }
    }

    private static final class BlockingTimeDetector extends TestFailedCommandsDetector {

        private final AtomicBoolean blockNextTimeRead = new AtomicBoolean();
        private volatile CountDownLatch blockedTimeRead;
        private volatile CountDownLatch releaseTimeRead;

        private BlockingTimeDetector(long checkInterval, int failedCommandsLimit) {
            super(checkInterval, failedCommandsLimit);
        }

        private void blockNextTimeRead() {
            blockedTimeRead = new CountDownLatch(1);
            releaseTimeRead = new CountDownLatch(1);
            blockNextTimeRead.set(true);
        }

        private void awaitBlockedTimeRead() throws InterruptedException {
            assertThat(blockedTimeRead.await(5, TimeUnit.SECONDS)).isTrue();
        }

        private void releaseTimeRead() {
            CountDownLatch release = releaseTimeRead;
            if (release != null) {
                release.countDown();
            }
        }

        @Override
        protected long getCurrentTime() {
            if (blockNextTimeRead.compareAndSet(true, false)) {
                blockedTimeRead.countDown();
                try {
                    if (!releaseTimeRead.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release time read");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return super.getCurrentTime();
        }
    }

}
