package org.redisson.config;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DelayStrategyOverflowTest {

    private static final Duration DEFAULT_BASE_DELAY = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofMinutes(30);

    static Stream<Arguments> delayStrategyProvider() {
        BiFunction<Duration, Duration, DelayStrategy> equalJitter = EqualJitterDelay::new;
        BiFunction<Duration, Duration, DelayStrategy> fullJitter = FullJitterDelay::new;
        return Stream.of(
                Arguments.of(Named.of("EqualJitterDelay", equalJitter)),
                Arguments.of(Named.of("FullJitterDelay", fullJitter))
        );
    }

    static Stream<Arguments> delayStrategyWithAttemptsProvider() {
        BiFunction<Duration, Duration, DelayStrategy> equalJitter = EqualJitterDelay::new;
        BiFunction<Duration, Duration, DelayStrategy> fullJitter = FullJitterDelay::new;
        int[] attempts = {50, 53, 54, 57, 60, 62, 63, 100, 1000, Integer.MAX_VALUE};

        return Stream.of(
                new Object[]{equalJitter, "EqualJitterDelay"},
                new Object[]{fullJitter, "FullJitterDelay"}
        ).flatMap(pair -> {
            BiFunction<Duration, Duration, DelayStrategy> factory =
                    (BiFunction<Duration, Duration, DelayStrategy>) pair[0];
            String name = (String) pair[1];
            return Arrays.stream(attempts).mapToObj(attempt ->
                    Arguments.of(Named.of(name, factory), attempt));
        });
    }

    static Stream<Arguments> delayStrategyWithConfigProvider() {
        BiFunction<Duration, Duration, DelayStrategy> equalJitter = EqualJitterDelay::new;
        BiFunction<Duration, Duration, DelayStrategy> fullJitter = FullJitterDelay::new;
        long[][] configs = {
                {100L, 600000L},    // 100ms base, 10 min max
                {1000L, 1800000L},  // 1s base, 30 min max
                {5000L, 3600000L},  // 5s base, 1 hour max
                {10000L, 7200000L}  // 10s base, 2 hour max
        };

        return Stream.of(
                new Object[]{equalJitter, "EqualJitterDelay"},
                new Object[]{fullJitter, "FullJitterDelay"}
        ).flatMap(pair -> {
            BiFunction<Duration, Duration, DelayStrategy> factory =
                    (BiFunction<Duration, Duration, DelayStrategy>) pair[0];
            String name = (String) pair[1];
            return Stream.of(configs).map(config ->
                    Arguments.of(Named.of(name, factory), config[0], config[1]));
        });
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testDelayNeverExceedsMaxDelay(BiFunction<Duration, Duration, DelayStrategy> factory) {
        Duration maxDelay = Duration.ofMinutes(10);
        DelayStrategy strategy = factory.apply(Duration.ofSeconds(1), maxDelay);

        for (int attempt = 0; attempt < 100; attempt++) {
            Duration delay = strategy.calcDelay(attempt);
            assertTrue(delay.toMillis() >= 0,
                    "Delay should be non-negative at attempt " + attempt + ", got: " + delay.toMillis());
            assertTrue(delay.toMillis() <= maxDelay.toMillis(),
                    "Delay should not exceed maxDelay at attempt " + attempt + ", got: " + delay.toMillis());
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testDelayNeverBecomesNegative(BiFunction<Duration, Duration, DelayStrategy> factory) {
        DelayStrategy strategy = factory.apply(Duration.ofMillis(1000), Duration.ofHours(1));

        for (int attempt = 0; attempt < 100; attempt++) {
            Duration delay = strategy.calcDelay(attempt);
            assertTrue(delay.toMillis() >= 0,
                    "Delay must be non-negative at attempt " + attempt + ", got: " + delay.toMillis());
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    // Reproduces the scenario from GitHub issue #6891
    void testOverflowAtAttempt57(BiFunction<Duration, Duration, DelayStrategy> factory) {
        Duration maxDelay = Duration.ofMinutes(30);
        DelayStrategy strategy = factory.apply(Duration.ofMillis(1000), maxDelay);

        assertDoesNotThrow(() -> {
            Duration delay = strategy.calcDelay(57);
            assertTrue(delay.toMillis() >= 0, "Delay should be non-negative");
            assertTrue(delay.toMillis() <= maxDelay.toMillis(), "Delay should not exceed max");
        }, "calcDelay should not throw at attempt 57");
    }

    @ParameterizedTest
    @MethodSource("delayStrategyWithAttemptsProvider")
    void testHighAttemptValuesDoNotCauseOverflow(
            BiFunction<Duration, Duration, DelayStrategy> factory, int attempt) {
        Duration maxDelay = Duration.ofMinutes(10);
        DelayStrategy strategy = factory.apply(Duration.ofSeconds(1), maxDelay);

        assertDoesNotThrow(() -> {
            Duration delay = strategy.calcDelay(attempt);
            assertTrue(delay.toMillis() >= 0,
                    "Delay should be non-negative at attempt " + attempt);
            assertTrue(delay.toMillis() <= maxDelay.toMillis(),
                    "Delay should not exceed maxDelay at attempt " + attempt);
        });
    }

    @ParameterizedTest
    @MethodSource("delayStrategyWithConfigProvider")
    void testVariousBaseDelayConfigurations(
            BiFunction<Duration, Duration, DelayStrategy> factory, long baseMs, long maxMs) {
        Duration baseDelay = Duration.ofMillis(baseMs);
        Duration maxDelay = Duration.ofMillis(maxMs);
        DelayStrategy strategy = factory.apply(baseDelay, maxDelay);

        for (int attempt = 0; attempt < 100; attempt++) {
            Duration delay = strategy.calcDelay(attempt);
            assertTrue(delay.toMillis() >= 0,
                    String.format("Delay should be non-negative (base=%d, max=%d, attempt=%d), got: %d",
                            baseMs, maxMs, attempt, delay.toMillis()));
            assertTrue(delay.toMillis() <= maxDelay.toMillis(),
                    String.format("Delay should not exceed max (base=%d, max=%d, attempt=%d), got: %d",
                            baseMs, maxMs, attempt, delay.toMillis()));
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testAverageDelayDoesNotCollapseAfterOverflow(
            BiFunction<Duration, Duration, DelayStrategy> factory) {
        Duration maxDelay = Duration.ofSeconds(60);
        DelayStrategy strategy = factory.apply(Duration.ofSeconds(1), maxDelay);

        // After reaching max, average delay should remain significant
        // If overflow occurs, it collapses to ~0-1ms
        long minimumExpectedAverage = 1000; // At least 1-second average

        for (int attempt = 20; attempt < 100; attempt++) {
            long sum = 0;
            int samples = 200;

            for (int i = 0; i < samples; i++) {
                sum += strategy.calcDelay(attempt).toMillis();
            }

            long average = sum / samples;
            assertTrue(average >= minimumExpectedAverage,
                    "Average delay collapsed at attempt " + attempt +
                            ", expected at least " + minimumExpectedAverage + "ms, got: " + average + "ms");
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testMultiplicationOverflowScenario(BiFunction<Duration, Duration, DelayStrategy> factory) {
        // Large maxDelay that won't cap the exponential growth early
        Duration baseDelay = Duration.ofMillis(1000);
        Duration maxDelay = Duration.ofDays(365);
        DelayStrategy strategy = factory.apply(baseDelay, maxDelay);

        // At attempt 54: 1000 * 2^54 overflows Long.MAX_VALUE
        int overflowAttempt = 54;

        long sum = 0;
        int samples = 100;
        for (int i = 0; i < samples; i++) {
            Duration delay = strategy.calcDelay(overflowAttempt);
            assertTrue(delay.toMillis() >= 0, "Delay should be non-negative");
            assertTrue(delay.toMillis() <= maxDelay.toMillis(), "Delay should not exceed maxDelay");
            sum += delay.toMillis();
        }

        long average = sum / samples;
        assertTrue(average > 100,
                "Delay collapsed to near zero at attempt " + overflowAttempt +
                        " (overflow detected), average: " + average + "ms");
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testConsistentBehaviorAcrossOverflowBoundary(
            BiFunction<Duration, Duration, DelayStrategy> factory) {
        Duration maxDelay = Duration.ofMinutes(30);
        DelayStrategy strategy = factory.apply(Duration.ofMillis(1000), maxDelay);

        int[] attempts = {50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60};
        long[] averages = new long[attempts.length];
        int samples = 200;

        for (int i = 0; i < attempts.length; i++) {
            long sum = 0;
            for (int j = 0; j < samples; j++) {
                sum += strategy.calcDelay(attempts[i]).toMillis();
            }
            averages[i] = sum / samples;
        }

        // All averages should be significant (not collapsed to ~0-1ms)
        long minimumExpectedAverage = 10000; // At least 10 seconds

        for (int i = 0; i < attempts.length; i++) {
            assertTrue(averages[i] >= minimumExpectedAverage,
                    "Average at attempt " + attempts[i] + " dropped significantly: " + averages[i] +
                            "ms (expected at least " + minimumExpectedAverage + "ms). Possible overflow bug.");
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testEdgeCaseNegativeOverflowDetection(BiFunction<Duration, Duration, DelayStrategy> factory) {
        long baseMs = 1000;
        int attempt = 54;

        // Manually check if overflow occurs
        long shifted = 1L << Math.min(attempt, 62);
        long product = baseMs * shifted;

        if (product < 0) {
            System.out.println("INFO: Overflow detected at attempt " + attempt);
            System.out.println("  1L << " + attempt + " = " + shifted);
            System.out.println("  " + baseMs + " * " + shifted + " = " + product + " (overflowed!)");

            Duration maxDelay = Duration.ofMinutes(30);
            DelayStrategy strategy = factory.apply(Duration.ofMillis(baseMs), maxDelay);

            long sum = 0;
            int samples = 100;
            for (int i = 0; i < samples; i++) {
                sum += strategy.calcDelay(attempt).toMillis();
            }
            long average = sum / samples;

            assertTrue(average > 1,
                    "Overflow caused delay to collapse to ~0-1ms - this is the bug! Average: " + average);
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testExponentialGrowthBeforeReachingMax(BiFunction<Duration, Duration, DelayStrategy> factory) {
        Duration baseDelay = Duration.ofMillis(100);
        Duration maxDelay = Duration.ofMinutes(10);
        DelayStrategy strategy = factory.apply(baseDelay, maxDelay);

        // Verify exponential growth pattern for early attempts
        for (int attempt = 0; attempt < 10; attempt++) {
            long expectedMaxDelay = Math.min(
                    baseDelay.toMillis() * (1L << attempt),
                    maxDelay.toMillis()
            );

            for (int i = 0; i < 50; i++) {
                Duration delay = strategy.calcDelay(attempt);
                assertTrue(delay.toMillis() <= expectedMaxDelay,
                        "At attempt " + attempt + ", delay should be <= " + expectedMaxDelay +
                                ", got: " + delay.toMillis());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testZeroAttempt(BiFunction<Duration, Duration, DelayStrategy> factory) {
        Duration baseDelay = Duration.ofMillis(1000);
        Duration maxDelay = Duration.ofMinutes(10);
        DelayStrategy strategy = factory.apply(baseDelay, maxDelay);

        for (int i = 0; i < 50; i++) {
            Duration delay = strategy.calcDelay(0);
            assertTrue(delay.toMillis() >= 0, "Delay should be non-negative");
            assertTrue(delay.toMillis() <= baseDelay.toMillis(),
                    "At attempt 0, delay should be <= baseDelay");
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testNegativeAttemptHandling(BiFunction<Duration, Duration, DelayStrategy> factory) {
        DelayStrategy strategy = factory.apply(DEFAULT_BASE_DELAY, DEFAULT_MAX_DELAY);

        // Negative attempts should not crash or produce invalid results
        assertDoesNotThrow(() -> {
            Duration delay = strategy.calcDelay(-1);
            assertTrue(delay.toMillis() >= 0, "Delay should be non-negative for negative attempt");
        });
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testSmallBaseDelayWithLargeAttempt(BiFunction<Duration, Duration, DelayStrategy> factory) {
        // Small base delay should still overflow eventually
        Duration baseDelay = Duration.ofMillis(1);
        Duration maxDelay = Duration.ofHours(24);
        DelayStrategy strategy = factory.apply(baseDelay, maxDelay);

        // 1ms * 2^63 would overflow, but should be capped at maxDelay
        for (int attempt = 60; attempt < 70; attempt++) {
            Duration delay = strategy.calcDelay(attempt);
            assertTrue(delay.toMillis() >= 0,
                    "Delay should be non-negative at attempt " + attempt);
            assertTrue(delay.toMillis() <= maxDelay.toMillis(),
                    "Delay should not exceed maxDelay at attempt " + attempt);
        }
    }

    @ParameterizedTest
    @MethodSource("delayStrategyProvider")
    void testLargeBaseDelayOverflowsEarlier(BiFunction<Duration, Duration, DelayStrategy> factory) {
        // Larger base delay causes overflow at lower attempt numbers
        Duration baseDelay = Duration.ofSeconds(10); // 10000ms
        Duration maxDelay = Duration.ofDays(1);
        DelayStrategy strategy = factory.apply(baseDelay, maxDelay);

        // 10000ms * 2^50 should overflow
        long sum = 0;
        int samples = 100;
        int attempt = 50;

        for (int i = 0; i < samples; i++) {
            Duration delay = strategy.calcDelay(attempt);
            assertTrue(delay.toMillis() >= 0, "Delay should be non-negative");
            assertTrue(delay.toMillis() <= maxDelay.toMillis(), "Delay should not exceed maxDelay");
            sum += delay.toMillis();
        }

        long average = sum / samples;
        assertTrue(average > 100,
                "Delay collapsed at attempt " + attempt + " with large base delay, average: " + average + "ms");
    }
}