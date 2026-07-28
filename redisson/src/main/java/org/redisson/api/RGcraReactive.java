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

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive interface for Redis based GCRA object.
 * <p>
 * Requires <b>Redis 8.8.0 or higher.</b>
 *
 * @author Su Ko
 *
 */
public interface RGcraReactive extends RExpirableReactive {

    /**
     * Sets the rate configuration only if it hasn't been set before.
     *
     * @param maxBurst maximum burst size
     * @param tokensPerPeriod token replenishment rate per period
     * @param period replenishment period
     * @return {@code true} if the rate was set, or {@code false} if it was already set before
     */
    Mono<Boolean> trySetRate(long maxBurst, long tokensPerPeriod, Duration period);

    /**
     * Sets the rate configuration overwriting the previous value and resetting the consumed tokens.
     *
     * @param maxBurst maximum burst size
     * @param tokensPerPeriod token replenishment rate per period
     * @param period replenishment period
     * @return void
     */
    Mono<Void> setRate(long maxBurst, long tokensPerPeriod, Duration period);

    /**
     * Returns the rate configuration set through
     * {@link #trySetRate(long, long, Duration)} or {@link #setRate(long, long, Duration)} method.
     *
     * @return rate configuration or {@code null} if the rate wasn't set
     */
    Mono<GcraConfig> getConfig();

    /**
     * Applies the GCRA algorithm with a single token request
     * using the rate configuration set through
     * {@link #trySetRate(long, long, Duration)} or {@link #setRate(long, long, Duration)} method.
     *
     * @return GCRA result
     */
    Mono<GcraResult> tryAcquire();

    /**
     * Applies the GCRA algorithm with a custom token request size
     * using the rate configuration set through
     * {@link #trySetRate(long, long, Duration)} or {@link #setRate(long, long, Duration)} method.
     *
     * @param tokens requested token amount
     * @return GCRA result
     */
    Mono<GcraResult> tryAcquire(long tokens);

    /**
     * Applies the GCRA algorithm with a single token request.
     *
     * @param maxBurst maximum burst size
     * @param tokensPerPeriod token replenishment rate per period
     * @param period replenishment period
     * @return GCRA result
     * @deprecated use {@link #trySetRate(long, long, Duration)} with {@link #tryAcquire()} instead
     */
    @Deprecated
    Mono<GcraResult> tryAcquire(long maxBurst, long tokensPerPeriod, Duration period);

    /**
     * Applies the GCRA algorithm with a custom token request size.
     *
     * @param maxBurst maximum burst size
     * @param tokensPerPeriod token replenishment rate per period
     * @param period replenishment period
     * @param tokens requested token amount
     * @return GCRA result
     * @deprecated use {@link #trySetRate(long, long, Duration)} with {@link #tryAcquire(long)} instead
     */
    @Deprecated
    Mono<GcraResult> tryAcquire(long maxBurst, long tokensPerPeriod, Duration period, long tokens);

}
