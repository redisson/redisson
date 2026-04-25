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
package org.redisson;

import org.redisson.api.GcraResult;
import org.redisson.api.RFuture;
import org.redisson.api.RGcra;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Su Ko
 *
 */
public final class RedissonGcra extends RedissonExpirable implements RGcra {

    public RedissonGcra(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public GcraResult tryAcquire(long maxBurst, long tokensPerPeriod, Duration period) {
        return get(tryAcquireAsync(maxBurst, tokensPerPeriod, period));
    }

    @Override
    public RFuture<GcraResult> tryAcquireAsync(long maxBurst, long tokensPerPeriod, Duration period) {
        return tryAcquireAsync(maxBurst, tokensPerPeriod, period, 1);
    }

    @Override
    public GcraResult tryAcquire(long maxBurst, long tokensPerPeriod, Duration period, long tokens) {
        return get(tryAcquireAsync(maxBurst, tokensPerPeriod, period, tokens));
    }

    @Override
    public RFuture<GcraResult> tryAcquireAsync(long maxBurst, long tokensPerPeriod, Duration period, long tokens) {
        validate(maxBurst, tokensPerPeriod, period, tokens);

        List<Object> params = new ArrayList<>(6);
        params.add(name);
        params.add(maxBurst);
        params.add(tokensPerPeriod);
        params.add(toSeconds(period));
        if (tokens != 1) {
            params.add("TOKENS");
            params.add(tokens);
        }

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.GCRA, params.toArray());
    }

    static void validate(long maxBurst, long tokensPerPeriod, Duration period, long tokens) {
        if (maxBurst < 0) {
            throw new IllegalArgumentException("maxBurst can't be negative");
        }
        if (tokensPerPeriod <= 0) {
            throw new IllegalArgumentException("tokensPerPeriod must be positive");
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period must be positive");
        }
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive");
        }
    }

    static String toSeconds(Duration period) {
        return BigDecimal.valueOf(period.getSeconds())
                .add(BigDecimal.valueOf(period.getNano(), 9))
                .stripTrailingZeros()
                .toPlainString();
    }
}
