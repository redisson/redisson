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
package org.redisson.api;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuration for Lock object.
 *
 * @author Danila Varatyntsev
 */
public class LockOptions {

    /**
     * Factory for {@linkplain BackOffPolicy} class.
     */
    public interface BackOff {
        BackOffPolicy create();
    }

    /**
     * Generator of sleep period values for {@linkplain org.redisson.RedissonSpinLock} back off
     */
    public interface BackOffPolicy {

        /**
         * Generates and returns next sleep period
         *
         * @return next sleep period
         */
        long getNextSleepPeriod();
    }

    /**
     * Back off algorithm, where sleep period starts with {@linkplain #initialDelay}, each time increases
     * {@linkplain #multiplier} times but doesn't exceed {@linkplain #maxDelay}
     */
    public static class ExponentialBackOff implements BackOff {
        private long maxDelay = 128;
        private long initialDelay = 1;
        private int multiplier = 2;

        @Override
        public BackOffPolicy create() {
            return new ExponentialBackOffPolicy(initialDelay, maxDelay, multiplier);
        }

        /**
         * Sets max back off delay.
         * <p>
         * Default is <code>128</code>
         *
         * @param maxDelay - max sleep period. Has to be positive
         * @return ExponentialBackOffOptions instance
         */
        public ExponentialBackOff maxDelay(long maxDelay) {
            if (maxDelay <= 0) {
                throw new IllegalArgumentException("maxDelay should be positive");
            }
            this.maxDelay = maxDelay;
            return this;
        }

        public long getMaxDelay() {
            return maxDelay;
        }

        /**
         * Sets initial back off delay.
         * <p>
         * Default is <code>1</code>
         *
         * @param initialDelay - initial sleep period. Has to be positive
         * @return ExponentialBackOffOptions instance
         */
        public ExponentialBackOff initialDelay(long initialDelay) {
            if (initialDelay <= 0) {
                throw new IllegalArgumentException("initialDelay should be positive");
            }
            this.initialDelay = initialDelay;
            return this;
        }

        public long getInitialDelay() {
            return initialDelay;
        }

        /**
         * Sets back off delay multiplier.
         * <p>
         * Default is <code>2</code>
         *
         * @param multiplier - sleep period multiplier. Has to be positive
         * @return ExponentialBackOffOptions instance
         */
        public ExponentialBackOff multiplier(int multiplier) {
            if (multiplier <= 0) {
                throw new IllegalArgumentException("multiplier should be positive");
            }
            this.multiplier = multiplier;
            return this;
        }

        public int getMultiplier() {
            return multiplier;
        }
    }

    /**
     * Back off algorithm, where sleep period time increases exponentially. To prevent
     */
    private static final class ExponentialBackOffPolicy implements BackOffPolicy {

        private final long maxDelay;
        private final int multiplier;
        private int fails;
        private long nextSleep;

        private ExponentialBackOffPolicy(long initialDelay, long maxDelay, int multiplier) {
            this.nextSleep = initialDelay;
            this.maxDelay = maxDelay;
            this.multiplier = multiplier;
        }

        @Override
        public long getNextSleepPeriod() {
            if (nextSleep == maxDelay) {
                return maxDelay;
            }
            long result = nextSleep;
            nextSleep = nextSleep * multiplier + ThreadLocalRandom.current().nextInt(++fails);
            nextSleep = Math.min(maxDelay, nextSleep);
            return result;
        }
    }

    /**
     * Back off algorithm, where sleep period is constant and is defined by {@linkplain #delay}.
     * To reduce possible negative effects of many threads simultaneously sending requests, a small random value is
     * added to all sleep periods.
     */
    public static class ConstantBackOff implements BackOff {
        private long delay = 64;

        @Override
        public BackOffPolicy create() {
            return new ConstantBackOffPolicy(delay);
        }

        /**
         * Sets back off delay value.
         * <p>
         * Default is <code>64</code>
         *
         * @param delay - sleep period value. Has to be positive
         * @return ConstantBackOffOptions instance
         */
        public ConstantBackOff delay(long delay) {
            if (delay <= 0) {
                throw new IllegalArgumentException("delay should be positive");
            }
            this.delay = delay;
            return this;
        }

        public long getDelay() {
            return delay;
        }
    }

    /**
     * Back off policy, where sleep period is constant and is defined by {@linkplain #delay}
     */
    private static final class ConstantBackOffPolicy implements BackOffPolicy {

        private final long delay;

        private ConstantBackOffPolicy(long delay) {
            this.delay = delay;
        }

        @Override
        public long getNextSleepPeriod() {
            return delay;
        }
    }

    /**
     * Creates a new instance of ExponentialBackOffOptions with default options.
     *
     * @return BackOffOptions instance
     */
    public static BackOff defaults() {
        return new ExponentialBackOff();
    }
}
