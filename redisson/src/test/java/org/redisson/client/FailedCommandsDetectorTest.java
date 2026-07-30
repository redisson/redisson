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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FailedCommandsDetectorTest {

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
    void removesFailuresOutsideTheCheckInterval() {
        TestFailedCommandsDetector detector = new TestFailedCommandsDetector(1000, 2);
        detector.onCommandFailed(new RedisTimeoutException("expired"));

        detector.advanceTime(1001);
        detector.onCommandFailed(new RedisTimeoutException("current"));

        assertThat(detector.isNodeFailed()).isFalse();
    }

    @Test
    void thresholdCanOnlyBeConsumedOnceConcurrently() throws Exception {
        TestFailedCommandsDetector detector = new TestFailedCommandsDetector(1000, 1);
        detector.onCommandFailed(new RedisTimeoutException("failed"));

        int callers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return detector.isNodeFailed();
                }));
            }
            ready.await();
            start.countDown();

            int failedTransitions = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    failedTransitions++;
                }
            }
            assertThat(failedTransitions).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class TestFailedCommandsDetector extends FailedCommandsDetector {

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
    }
}
