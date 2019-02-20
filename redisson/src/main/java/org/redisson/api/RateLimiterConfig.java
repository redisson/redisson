/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RateLimiterConfig {

    private RateType rateType;
    private Long rateInterval;
    private Long rate;

    public RateLimiterConfig(RateType rateType, Long rateInterval, Long rate) {
        super();
        this.rateType = rateType;
        this.rateInterval = rateInterval;
        this.rate = rate;
    }

    /**
     * Returns current rate type set through {@link RRateLimiter#trySetRate(RateType, long, long, RateIntervalUnit)} 
     * or {@link RRateLimiter#trySetRateAsync(RateType, long, long, RateIntervalUnit)} method.
     * 
     * @return rate type
     */
    public RateType getRateType() {
        return rateType;
    }
    
    /**
     * Returns current rate time interval value set through {@link RRateLimiter#trySetRate(RateType, long, long, RateIntervalUnit)} 
     * or {@link RRateLimiter#trySetRateAsync(RateType, long, long, RateIntervalUnit)} method.
     * 
     * @return rate time interval in milliseconds
     */
    public Long getRateInterval() {
        return rateInterval;
    }

    /**
     * Returns current rate value set through {@link RRateLimiter#trySetRate(RateType, long, long, RateIntervalUnit)} 
     * or {@link RRateLimiter#trySetRateAsync(RateType, long, long, RateIntervalUnit)} method.
     * 
     * @return rate
     */
    public Long getRate() {
        return rate;
    }

    
}
