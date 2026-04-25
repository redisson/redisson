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
 * Async interface for Redis based GCRA object.
 * <p>
 * Requires <b>Redis 8.8.0 or higher.</b>
 *
 * @author Su Ko
 *
 */
public interface RGcraAsync extends RExpirableAsync {

    /**
     * Applies the GCRA algorithm with a single token request.
     *
     * @param maxBurst maximum burst size
     * @param tokensPerPeriod token replenishment rate per period
     * @param period replenishment period
     * @return GCRA result
     */
    RFuture<GcraResult> tryAcquireAsync(long maxBurst, long tokensPerPeriod, Duration period);

    /**
     * Applies the GCRA algorithm with a custom token request size.
     *
     * @param maxBurst maximum burst size
     * @param tokensPerPeriod token replenishment rate per period
     * @param period replenishment period
     * @param tokens requested token amount
     * @return GCRA result
     */
    RFuture<GcraResult> tryAcquireAsync(long maxBurst, long tokensPerPeriod, Duration period, long tokens);

}
