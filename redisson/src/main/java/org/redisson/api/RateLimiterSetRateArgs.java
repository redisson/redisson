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
package org.redisson.api;

import java.time.Duration;

/**
 * Arguments object for {@link RRateLimiter#setRate(RateLimiterSetRateArgs)} methods.
 */
public interface RateLimiterSetRateArgs {

    RateType getMode();

    long getRate();

    Duration getRateInterval();

    Duration getKeepAliveTime();

    /**
     * If {@code true} then state is preserved, otherwise state is cleared.
     * <p>
     * Default is {@code false} to match legacy {@code setRate(...)} behavior.
     *
     * @return keep state flag
     */
    boolean isKeepState();

    /**
     * Defines time to live of rate limiter keys.
     *
     * @param keepAliveTime maximum time that limiter will wait for a new acquisition before deletion
     * @return arguments object
     */
    RateLimiterSetRateArgs keepAliveTime(Duration keepAliveTime);

    /**
     * Clears current state (available permits and used permits history).
     *
     * @return arguments object
     */
    RateLimiterSetRateArgs clearState();

    /**
     * Keeps current state (available permits and used permits history).
     *
     * @return arguments object
     */
    RateLimiterSetRateArgs keepState();

    /**
     * Default args implementation.
     */
    final class Params implements RateLimiterSetRateArgs {

        private final RateType mode;
        private final long rate;
        private final Duration rateInterval;
        private Duration keepAliveTime = Duration.ZERO;
        private boolean keepState;

        public Params(RateType mode, long rate, Duration rateInterval) {
            this.mode = mode;
            this.rate = rate;
            this.rateInterval = rateInterval;
        }

        @Override
        public RateLimiterSetRateArgs keepAliveTime(Duration keepAliveTime) {
            this.keepAliveTime = keepAliveTime;
            return this;
        }

        @Override
        public RateLimiterSetRateArgs clearState() {
            this.keepState = false;
            return this;
        }

        @Override
        public RateLimiterSetRateArgs keepState() {
            this.keepState = true;
            return this;
        }

        @Override
        public RateType getMode() {
            return mode;
        }

        @Override
        public long getRate() {
            return rate;
        }

        @Override
        public Duration getRateInterval() {
            return rateInterval;
        }

        @Override
        public Duration getKeepAliveTime() {
            return keepAliveTime;
        }

        @Override
        public boolean isKeepState() {
            return keepState;
        }
    }

    /**
     * Creates args object with required parameters.
     *
     * @param mode rate mode
     * @param rate rate
     * @param rateInterval rate time interval
     * @return arguments object
     */
    static RateLimiterSetRateArgs of(RateType mode, long rate, Duration rateInterval) {
        return new Params(mode, rate, rateInterval);
    }
}

