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

import org.redisson.api.GcraConfig;
import org.redisson.api.GcraResult;
import org.redisson.api.RFuture;
import org.redisson.api.RGcra;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.GcraResultDecoder;
import org.redisson.client.protocol.decoder.MapEntriesDecoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.command.CommandAsyncExecutor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Su Ko
 *
 */
public final class RedissonGcra extends RedissonExpirable implements RGcra {

    private static final RedisCommand<GcraResult> EVAL_GCRA = new RedisCommand<>("EVAL", new GcraResultDecoder());

    private static final RedisCommand HGETALL = new RedisCommand("HGETALL",
            new MapEntriesDecoder((MultiDecoder<GcraConfig>) (parts, state) -> {
                if (parts.isEmpty()) {
                    return null;
                }
                Map<String, String> map = new HashMap<>(parts.size() / 2);
                for (int i = 1; i < parts.size(); i += 2) {
                    map.put(parts.get(i - 1).toString(), parts.get(i).toString());
                }
                long maxBurst = Long.parseLong(map.get("maxBurst"));
                long tokensPerPeriod = Long.parseLong(map.get("rate"));
                Duration period = fromSeconds(map.get("period"));
                return new GcraConfig(maxBurst, tokensPerPeriod, period);
            }));

    public RedissonGcra(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    String getConfigName() {
        return suffixName(getRawName(), "config");
    }

    @Override
    public boolean trySetRate(long maxBurst, long tokensPerPeriod, Duration period) {
        return get(trySetRateAsync(maxBurst, tokensPerPeriod, period));
    }

    @Override
    public RFuture<Boolean> trySetRateAsync(long maxBurst, long tokensPerPeriod, Duration period) {
        validate(maxBurst, tokensPerPeriod, period);

        return commandExecutor.evalWriteNoRetryAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "redis.call('hsetnx', KEYS[1], 'maxBurst', ARGV[1]);"
                + "redis.call('hsetnx', KEYS[1], 'rate', ARGV[2]);"
                + "return redis.call('hsetnx', KEYS[1], 'period', ARGV[3]);",
                Collections.singletonList(getConfigName()),
                maxBurst, tokensPerPeriod, toSeconds(period));
    }

    @Override
    public void setRate(long maxBurst, long tokensPerPeriod, Duration period) {
        get(setRateAsync(maxBurst, tokensPerPeriod, period));
    }

    @Override
    public RFuture<Void> setRateAsync(long maxBurst, long tokensPerPeriod, Duration period) {
        validate(maxBurst, tokensPerPeriod, period);

        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                  "redis.call('hset', KEYS[1], 'maxBurst', ARGV[1], 'rate', ARGV[2], 'period', ARGV[3]);"
                + "redis.call('del', KEYS[2]);",
                Arrays.asList(getConfigName(), getRawName()),
                maxBurst, tokensPerPeriod, toSeconds(period));
    }

    @Override
    public GcraConfig getConfig() {
        return get(getConfigAsync());
    }

    @Override
    public RFuture<GcraConfig> getConfigAsync() {
        return commandExecutor.readAsync(getConfigName(), StringCodec.INSTANCE, HGETALL, getConfigName());
    }

    @Override
    public GcraResult tryAcquire() {
        return get(tryAcquireAsync());
    }

    @Override
    public RFuture<GcraResult> tryAcquireAsync() {
        return tryAcquireAsync(1);
    }

    @Override
    public GcraResult tryAcquire(long tokens) {
        return get(tryAcquireAsync(tokens));
    }

    @Override
    public RFuture<GcraResult> tryAcquireAsync(long tokens) {
        validateTokens(tokens);

        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, EVAL_GCRA,
                  "local maxBurst = redis.call('hget', KEYS[1], 'maxBurst');"
                + "local rate = redis.call('hget', KEYS[1], 'rate');"
                + "local period = redis.call('hget', KEYS[1], 'period');"
                + "assert(maxBurst ~= false and rate ~= false and period ~= false, 'GCRA rate is not set');"
                + "if tonumber(ARGV[1]) == 1 then "
                    + "return redis.call('GCRA', KEYS[2], maxBurst, rate, period);"
                + "end;"
                + "return redis.call('GCRA', KEYS[2], maxBurst, rate, period, 'TOKENS', ARGV[1]);",
                Arrays.asList(getConfigName(), getRawName()),
                tokens);
    }

    @Override
    @Deprecated
    public GcraResult tryAcquire(long maxBurst, long tokensPerPeriod, Duration period) {
        return get(tryAcquireAsync(maxBurst, tokensPerPeriod, period));
    }

    @Override
    @Deprecated
    public RFuture<GcraResult> tryAcquireAsync(long maxBurst, long tokensPerPeriod, Duration period) {
        return tryAcquireAsync(maxBurst, tokensPerPeriod, period, 1);
    }

    @Override
    @Deprecated
    public GcraResult tryAcquire(long maxBurst, long tokensPerPeriod, Duration period, long tokens) {
        return get(tryAcquireAsync(maxBurst, tokensPerPeriod, period, tokens));
    }

    @Override
    @Deprecated
    public RFuture<GcraResult> tryAcquireAsync(long maxBurst, long tokensPerPeriod, Duration period, long tokens) {
        validate(maxBurst, tokensPerPeriod, period);
        validateTokens(tokens);

        List<Object> params = new ArrayList<>(6);
        params.add(getRawName());
        params.add(maxBurst);
        params.add(tokensPerPeriod);
        params.add(toSeconds(period));
        if (tokens != 1) {
            params.add("TOKENS");
            params.add(tokens);
        }

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.GCRA, params.toArray());
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), getConfigName());
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, param, getRawName(), getConfigName());
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), getConfigName());
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), getConfigName());
    }

    static void validate(long maxBurst, long tokensPerPeriod, Duration period) {
        if (maxBurst < 0) {
            throw new IllegalArgumentException("maxBurst can't be negative");
        }
        if (tokensPerPeriod <= 0) {
            throw new IllegalArgumentException("tokensPerPeriod must be positive");
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period must be positive");
        }
    }

    static void validateTokens(long tokens) {
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

    static Duration fromSeconds(String seconds) {
        return Duration.ofNanos(new BigDecimal(seconds).movePointRight(9).longValueExact());
    }
}
