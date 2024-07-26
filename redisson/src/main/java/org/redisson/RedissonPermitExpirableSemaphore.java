/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBufUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.api.RFuture;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.pubsub.SemaphorePubSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonPermitExpirableSemaphore extends RedissonExpirable implements RPermitExpirableSemaphore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonPermitExpirableSemaphore.class);

    private final String channelName;
    private final SemaphorePubSub semaphorePubSub;

    private final String timeoutName;
    
    private final long nonExpirableTimeout = 922337203685477L;

    public RedissonPermitExpirableSemaphore(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.timeoutName = suffixName(getRawName(), "timeout");
        this.semaphorePubSub = commandExecutor.getConnectionManager().getSubscribeService().getSemaphorePubSub();
        this.channelName = prefixName("redisson_sc", getRawName());
    }

    @Override
    public String acquire() throws InterruptedException {
        return acquire(-1, TimeUnit.MILLISECONDS);
    }

    @Override
    public List<String> acquire(int permits) throws InterruptedException {
        return acquire(permits, -1, TimeUnit.MILLISECONDS);
    }

    @Override
    public String acquire(long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        List<String> ids = acquire(1, leaseTime, timeUnit);
        return getFirstOrNull(ids);
    }
    
    @Override
    public List<String> acquire(int permits, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        List<String> ids = tryAcquire(permits, leaseTime, timeUnit);
        if (!ids.isEmpty() && !hasOnlyNearestTimeout(ids)) {
            return ids;
        }

        CompletableFuture<RedissonLockEntry> future = subscribe();
        semaphorePubSub.timeout(future);
        RedissonLockEntry entry = commandExecutor.getInterrupted(future);
        try {
            while (true) {
                Long nearestTimeout;
                ids = tryAcquire(permits, leaseTime, timeUnit);
                if (ids.isEmpty()) {
                    nearestTimeout = null;
                } else if (hasOnlyNearestTimeout(ids)) {
                    nearestTimeout = Long.parseLong(ids.get(0).substring(1)) - System.currentTimeMillis();
                } else {
                    return ids;
                }
                
                if (nearestTimeout != null) {
                    entry.getLatch().tryAcquire(permits, nearestTimeout, TimeUnit.MILLISECONDS);
                } else {
                    entry.getLatch().acquire(permits);
                }
            }
        } finally {
            unsubscribe(entry);
        }
//        return get(acquireAsync(permits, leaseTime, timeUnit));
    }
    
    @Override
    public RFuture<String> acquireAsync() {
        CompletionStage<String> future = acquireAsync(1)
                .thenApply(RedissonPermitExpirableSemaphore::getFirstOrNull);
        return new CompletableFutureWrapper<>(future);
    }
    
    @Override
    public RFuture<List<String>> acquireAsync(int permits) {
        return acquireAsync(permits, -1, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public RFuture<String> acquireAsync(long leaseTime, TimeUnit timeUnit) {
        CompletionStage<String> future = acquireAsync(1, leaseTime, timeUnit)
                .thenApply(RedissonPermitExpirableSemaphore::getFirstOrNull);

        return new CompletableFutureWrapper<>(future);
    }
    
    @Override
    public RFuture<List<String>> acquireAsync(int permits, long leaseTime, TimeUnit timeUnit) {
        long timeoutDate = calcTimeout(leaseTime, timeUnit);
        RFuture<List<String>> tryAcquireFuture = tryAcquireAsync(permits, timeoutDate);
        CompletionStage<List<String>> f = tryAcquireFuture.thenCompose(ids -> {
            if (!ids.isEmpty() && !hasOnlyNearestTimeout(ids)) {
                return CompletableFuture.completedFuture(ids);
            }

            CompletableFuture<RedissonLockEntry> subscribeFuture = subscribe();
            semaphorePubSub.timeout(subscribeFuture);
            return subscribeFuture.thenCompose(res -> acquireAsync(permits, res, leaseTime, timeUnit));
        });
        f.whenComplete((r, e) -> {
            if (f.toCompletableFuture().isCancelled()) {
                tryAcquireFuture.whenComplete((ids, ex) -> {
                    if (!ids.isEmpty() && !hasOnlyNearestTimeout(ids)) {
                        releaseAsync(ids);
                    }
                });
            }
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    private void tryAcquireAsync(AtomicLong time, int permits, RedissonLockEntry entry, CompletableFuture<List<String>> result, long leaseTime, TimeUnit timeUnit) {
        if (result.isDone()) {
            unsubscribe(entry);
            return;
        }
        
        if (time.get() <= 0) {
            unsubscribe(entry);
            result.complete(Collections.emptyList());
            return;
        }
        
        long timeoutDate = calcTimeout(leaseTime, timeUnit);
        long curr = System.currentTimeMillis();
        RFuture<List<String>> tryAcquireFuture = tryAcquireAsync(permits, timeoutDate);
        tryAcquireFuture.whenComplete((ids, e) -> {
            if (e != null) {
                unsubscribe(entry);
                result.completeExceptionally(e);
                return;
            }

            Long nearestTimeout;
            if (ids.isEmpty()) {
                nearestTimeout = null;
            } else if (hasOnlyNearestTimeout(ids)) {
                nearestTimeout = Long.parseLong(ids.get(0).substring(1)) - System.currentTimeMillis();
            } else {
                unsubscribe(entry);
                if (!result.complete(ids)) {
                    releaseAsync(ids);
                }
                return;
            }

            long el = System.currentTimeMillis() - curr;
            time.addAndGet(-el);
            
            if (time.get() <= 0) {
                unsubscribe(entry);
                result.complete(Collections.emptyList());
                return;
            }

            // waiting for message
            long current = System.currentTimeMillis();
            if (entry.getLatch().tryAcquire()) {
                tryAcquireAsync(time, permits, entry, result, leaseTime, timeUnit);
            } else {
                AtomicReference<Timeout> waitTimeoutFutureRef = new AtomicReference<>();

                Timeout scheduledFuture;
                if (nearestTimeout != null) {
                    scheduledFuture = getServiceManager().newTimeout(new TimerTask() {
                        @Override
                        public void run(Timeout timeout) throws Exception {
                            if (waitTimeoutFutureRef.get() != null && !waitTimeoutFutureRef.get().cancel()) {
                                return;
                            }
                            
                            long elapsed = System.currentTimeMillis() - current;
                            time.addAndGet(-elapsed);

                            tryAcquireAsync(time, permits, entry, result, leaseTime, timeUnit);
                        }
                    }, nearestTimeout, TimeUnit.MILLISECONDS);
                } else {
                    scheduledFuture = null;
                }

                Runnable listener = () -> {
                    if (waitTimeoutFutureRef.get() != null && !waitTimeoutFutureRef.get().cancel()) {
                        entry.getLatch().release();
                        return;
                    }
                    if (scheduledFuture != null && !scheduledFuture.cancel()) {
                        entry.getLatch().release();
                        return;
                    }
                    
                    long elapsed = System.currentTimeMillis() - current;
                    time.addAndGet(-elapsed);

                    tryAcquireAsync(time, permits, entry, result, leaseTime, timeUnit);
                };
                entry.addListener(listener);

                long t = time.get();
                Timeout waitTimeoutFuture = getServiceManager().newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        if (scheduledFuture != null && !scheduledFuture.cancel()) {
                            return;
                        }

                        if (entry.removeListener(listener)) {
                            long elapsed = System.currentTimeMillis() - current;
                            time.addAndGet(-elapsed);
                            
                            tryAcquireAsync(time, permits, entry, result, leaseTime, timeUnit);
                        }
                    }
                }, t, TimeUnit.MILLISECONDS);
                waitTimeoutFutureRef.set(waitTimeoutFuture);
            }
        });
        
    }

    private CompletableFuture<List<String>> acquireAsync(int permits, RedissonLockEntry entry, long leaseTime, TimeUnit timeUnit) {
        long timeoutDate = calcTimeout(leaseTime, timeUnit);
        CompletableFuture<List<String>> tryAcquireFuture = tryAcquireAsync(permits, timeoutDate).toCompletableFuture();
        return tryAcquireFuture.whenComplete((p, e) -> {
            if (e != null) {
                unsubscribe(entry);
            }
        }).thenCompose(ids -> {
            Long nearestTimeout;
            if (ids.isEmpty()) {
                nearestTimeout = null;
            } else if (hasOnlyNearestTimeout(ids)) {
                nearestTimeout = Long.parseLong(ids.get(0).substring(1)) - System.currentTimeMillis();
            } else {
                unsubscribe(entry);
                return CompletableFuture.completedFuture(ids);
            }

            if (entry.getLatch().tryAcquire(permits)) {
                return acquireAsync(permits, entry, leaseTime, timeUnit);
            }

            CompletableFuture<List<String>> res = new CompletableFuture<>();
            Timeout scheduledFuture;
            if (nearestTimeout != null) {
                scheduledFuture = getServiceManager().newTimeout(timeout -> {
                    CompletableFuture<List<String>> r = acquireAsync(permits, entry, leaseTime, timeUnit);
                    commandExecutor.transfer(r, res);
                }, nearestTimeout, TimeUnit.MILLISECONDS);
            } else {
                scheduledFuture = null;
            }

            Runnable listener = () -> {
                if (scheduledFuture != null && !scheduledFuture.cancel()) {
                    entry.getLatch().release();
                    return;
                }
                CompletableFuture<List<String>> r = acquireAsync(permits, entry, leaseTime, timeUnit);
                commandExecutor.transfer(r, res);
            };
            entry.addListener(listener);
            return res;
        });
    }

    @Override
    public String tryAcquire() {
        List<String> ids = tryAcquire(1);
        return getFirstOrNull(ids);
    }

    @Override
    public List<String> tryAcquire(int permits) {
        List<String> ids = tryAcquire(permits, -1, TimeUnit.MILLISECONDS);
        if (hasOnlyNearestTimeout(ids)) {
            return Collections.emptyList();
        }
        return ids;
    }

    private List<String> tryAcquire(int permits, long leaseTime, TimeUnit timeUnit) {
        long timeoutDate = calcTimeout(leaseTime, timeUnit);
        return get(tryAcquireAsync(permits, timeoutDate));
    }

    private long calcTimeout(long leaseTime, TimeUnit timeUnit) {
        if (leaseTime != -1) {
            return System.currentTimeMillis() + timeUnit.toMillis(leaseTime);
        }
        return nonExpirableTimeout;
    }

    @Override
    public RFuture<String> tryAcquireAsync() {
        CompletionStage<String> future = tryAcquireAsync(1)
                .thenApply(RedissonPermitExpirableSemaphore::getFirstOrNull);
        return new CompletableFutureWrapper<>(future);
    }

    @Override
    public RFuture<List<String>> tryAcquireAsync(int permits) {
        CompletableFuture<List<String>> future = tryAcquireAsync(permits, nonExpirableTimeout).toCompletableFuture()
                .thenApply(ids -> {
                    if (hasOnlyNearestTimeout(ids)) {
                        return null;
                    }
                    return ids;
                });
        future.whenComplete((ids, e) -> {
            if (future.isCancelled() && !ids.isEmpty() && !hasOnlyNearestTimeout(ids)) {
                releaseAsync(ids);
            }
        });

        return new CompletableFutureWrapper<>(future);
    }

    private RFuture<List<String>> tryAcquireAsync(int permits, long timeoutDate) {
        if (permits < 0) {
            throw new IllegalArgumentException("Permits amount can't be negative");
        }
        List<String> ids = new ArrayList<>(permits);
        for (int i = 0; i < permits; i++) {
            ids.add(getServiceManager().generateId());
        }

        return getServiceManager().execute(() -> {
            RFuture<List<String>> future = tryAcquireAsync(ids, timeoutDate);
            return commandExecutor.handleNoSync(future, () -> releaseAsync(ids));
        });
    }

    private RFuture<List<String>> tryAcquireAsync(List<String> ids, long timeoutDate) {
        List<Object> params = new ArrayList<>();
        params.add(ids.size());
        params.add(timeoutDate);
        params.add(System.currentTimeMillis());
        params.add(nonExpirableTimeout);
        params.add(getSubscribeService().getPublishCommand());

        for (String permitId: ids) {
            params.add(ByteBufUtil.decodeHexDump(permitId));
        }
        
        CompletionStage<List<String>> future = commandExecutor.syncedEval(getRawName(), ByteArrayCodec.INSTANCE, RedisCommands.EVAL_STRING,
                  "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[3], 'limit', 0, ARGV[1]); " +
                  "if #expiredIds > 0 then " +
                      "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                      "local value = redis.call('incrby', KEYS[1], #expiredIds); " +
                      "if tonumber(value) > 0 then " +
                          "redis.call(ARGV[5], KEYS[3], value); " +
                      "end;" +
                  "end; " +
                  "local value = redis.call('get', KEYS[1]); " +
                  "if (value ~= false and tonumber(value) >= tonumber(ARGV[1])) then " +
                      "redis.call('decrby', KEYS[1], ARGV[1]); " +
                      "for i = 6, #ARGV, 1 do " +
                          "redis.call('zadd', KEYS[2], ARGV[2], ARGV[i]); " +
                      "end; " +

                      "local ttl = redis.call('pttl', KEYS[1]); " +
                      "if ttl > 0 then " +
                          "redis.call('pexpire', KEYS[2], ttl); " +
                      "end; " +

                      "return 'OK'; " +
                  "end; " +
                  "local v = redis.call('zrange', KEYS[2], 0, 0, 'WITHSCORES'); " +
                  "if v[1] ~= nil and v[2] ~= ARGV[4] then " +
                      "return ':' .. tostring(v[2]); " +
                  "end " +
                  "return nil;",
                  Arrays.asList(getRawName(), timeoutName, channelName),
                  params.toArray()
        ).thenApply(result -> {
            if (result == null) {
                return Collections.emptyList();
            }
            if (result.equals("OK")) {
                return ids;
            }
            return Collections.singletonList(result);
        });
        return new CompletableFutureWrapper<>(future);
    }

    @Override
    public RFuture<String> tryAcquireAsync(long waitTime, TimeUnit unit) {
        CompletionStage<String> future = tryAcquireAsync(1, waitTime, -1, unit)
                .thenApply(RedissonPermitExpirableSemaphore::getFirstOrNull);
        return new CompletableFutureWrapper<>(future);
    }
    
    @Override
    public String tryAcquire(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        List<String> ids = tryAcquire(1, waitTime, leaseTime, unit);
        return getFirstOrNull(ids);
    }
    
    @Override
    public RFuture<String> tryAcquireAsync(long waitTime, long leaseTime, TimeUnit unit) {
        CompletionStage<String> future = tryAcquireAsync(1, waitTime, leaseTime, unit)
                .thenApply(RedissonPermitExpirableSemaphore::getFirstOrNull);
        return new CompletableFutureWrapper<>(future);
    }
    
    @Override
    public List<String> tryAcquire(int permits, long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        long current = System.currentTimeMillis();

        List<String> ids = tryAcquire(permits, leaseTime, unit);
        if (!ids.isEmpty() && !hasOnlyNearestTimeout(ids)) {
            return ids;
        }

        time -= System.currentTimeMillis() - current;
        if (time <= 0) {
            return Collections.emptyList();
        }
        
        current = System.currentTimeMillis();
        CompletableFuture<RedissonLockEntry> future = subscribe();
        RedissonLockEntry entry;
        try {
            entry = future.get(time, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            return Collections.emptyList();
        } catch (TimeoutException e) {
            return Collections.emptyList();
        }

        try {
            time -= System.currentTimeMillis() - current;
            if (time <= 0) {
                return Collections.emptyList();
            }
            
            while (true) {
                current = System.currentTimeMillis();
                Long nearestTimeout;
                ids = tryAcquire(permits, leaseTime, unit);
                if (ids.isEmpty()) {
                    nearestTimeout = null;
                } else if (hasOnlyNearestTimeout(ids)) {
                    nearestTimeout = Long.parseLong(ids.get(0).substring(1)) - System.currentTimeMillis();
                } else {
                    return ids;
                }
                
                time -= System.currentTimeMillis() - current;
                if (time <= 0) {
                    return Collections.emptyList();
                }

                // waiting for message
                current = System.currentTimeMillis();

                if (nearestTimeout != null) {
                    entry.getLatch().tryAcquire(permits, Math.min(time, nearestTimeout), TimeUnit.MILLISECONDS);
                } else {
                    entry.getLatch().tryAcquire(permits, time, TimeUnit.MILLISECONDS);
                }
                
                long elapsed = System.currentTimeMillis() - current;
                time -= elapsed;
                if (time <= 0) {
                    return Collections.emptyList();
                }
            }
        } finally {
            unsubscribe(entry);
        }
//        return get(tryAcquireAsync(permits, waitTime, leaseTime, unit));
    }

    @Override
    public RFuture<List<String>> tryAcquireAsync(int permits, long waitTime, long leaseTime, TimeUnit timeUnit) {
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        AtomicLong time = new AtomicLong(timeUnit.toMillis(waitTime));
        long curr = System.currentTimeMillis();
        long timeoutDate = calcTimeout(leaseTime, timeUnit);
        tryAcquireAsync(permits, timeoutDate).whenComplete((ids, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            if (!ids.isEmpty() && !hasOnlyNearestTimeout(ids)) {
                if (!result.complete(ids)) {
                    releaseAsync(ids);
                }
                return;
            }
            
            long el = System.currentTimeMillis() - curr;
            time.addAndGet(-el);
            
            if (time.get() <= 0) {
                result.complete(Collections.emptyList());
                return;
            }
            
            long current = System.currentTimeMillis();
            AtomicReference<Timeout> futureRef = new AtomicReference<>();
            CompletableFuture<RedissonLockEntry> subscribeFuture = subscribe();
            subscribeFuture.whenComplete((r, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                    return;
                }
                
                if (futureRef.get() != null) {
                    futureRef.get().cancel();
                }

                long elapsed = System.currentTimeMillis() - current;
                time.addAndGet(-elapsed);
                
                tryAcquireAsync(time, permits, r, result, leaseTime, timeUnit);
            });
            
            if (!subscribeFuture.isDone()) {
                Timeout scheduledFuture = getServiceManager().newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        if (!subscribeFuture.isDone()) {
                            result.complete(Collections.emptyList());
                        }
                    }
                }, time.get(), TimeUnit.MILLISECONDS);
                futureRef.set(scheduledFuture);
            }
        });
        
        return new CompletableFutureWrapper<>(result);
    }

    private CompletableFuture<RedissonLockEntry> subscribe() {
        return semaphorePubSub.subscribe(getRawName(), channelName);
    }

    private void unsubscribe(RedissonLockEntry entry) {
        semaphorePubSub.unsubscribe(entry, getRawName(), channelName);
    }

    @Override
    public String tryAcquire(long waitTime, TimeUnit unit) throws InterruptedException {
        List<String> ids = tryAcquire(1, waitTime, -1, unit);
        if (ids.isEmpty() || hasOnlyNearestTimeout(ids)) {
            return null;
        }
        return ids.get(0);
    }

    @Override
    public void release(String permitId) {
        get(releaseAsync(permitId));
    }

    @Override
    public void release(List<String> permitsIds) {
        get(releaseAsync(permitsIds));
    }

    @Override
    public boolean tryRelease(String permitId) {
        return get(tryReleaseAsync(permitId));
    }

    @Override
    public int tryRelease(List<String> permitsIds) {
        return get(tryReleaseAsync(permitsIds));
    }

    @Override
    public RFuture<Boolean> tryReleaseAsync(String permitId) {
        if (permitId == null) {
            throw new IllegalArgumentException("permitId can't be null");
        }

        byte[] id = ByteBufUtil.decodeHexDump(permitId);
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local expire = redis.call('zscore', KEYS[3], ARGV[1]);" +
                        "local removed = redis.call('zrem', KEYS[3], ARGV[1]);" +
                        "if tonumber(removed) ~= 1 then " +
                            "return 0;" +
                        "end;" +
                        "local value = redis.call('incrby', KEYS[1], ARGV[2]); " +
                        "redis.call(ARGV[4], KEYS[2], value); " +
                        "if tonumber(expire) <= tonumber(ARGV[3]) then " +
                            "return 0;" +
                        "end;" +
                        "return 1;",
                Arrays.asList(getRawName(), channelName, timeoutName),
                id, 1, System.currentTimeMillis(), getSubscribeService().getPublishCommand());
    }
    
    @Override
    public RFuture<Integer> tryReleaseAsync(List<String> permitsIds) {
        if (permitsIds == null || permitsIds.isEmpty()) {
            throw new IllegalArgumentException("permitIds can't be null or empty");
        }

        List<Object> params = new ArrayList<>(permitsIds.size() + 3);
        params.add(permitsIds.size());
        params.add(System.currentTimeMillis());
        params.add(getSubscribeService().getPublishCommand());
        
        for (String permitId : permitsIds) {
            params.add(ByteBufUtil.decodeHexDump(permitId));
        }

        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local expiredIds = redis.call('zrangebyscore', KEYS[3], 0, ARGV[2], 'limit', 0, -1); " +
                "if #expiredIds > 0 then " +
                    "redis.call('zrem', KEYS[3], unpack(expiredIds)); " +
                    "local value = redis.call('incrby', KEYS[1], #expiredIds); " +
                    "if tonumber(value) > 0 then " +
                        "redis.call(ARGV[3], KEYS[2], value); " +
                    "end; " +
                "end; " +
                "local keys = {}; " +
                "for i = 4, #ARGV, 1 do " +
                    "table.insert(keys, ARGV[i]); " +
                "end; " +
                "local removed = redis.call('zrem', KEYS[3], unpack(keys)); " +
                "if tonumber(removed) == 0 then " +
                    "return 0;" +
                "end; " +
                "redis.call('incrby', KEYS[1], removed); " +
                "redis.call(ARGV[3], KEYS[2], removed); " +
                "return removed;",
                Arrays.asList(getRawName(), channelName, timeoutName),
                params.toArray());
    }
    
    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getRawName(), timeoutName);
        return super.sizeInMemoryAsync(keys);
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), timeoutName);
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), timeoutName);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, param, getRawName(), timeoutName);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), timeoutName);
    }

    @Override
    public RFuture<Void> releaseAsync(String permitId) {
        CompletionStage<Void> f = tryReleaseAsync(permitId).handle((res, e) -> {
            if (e != null) {
                throw new CompletionException(e);
            }

            if (res) {
                return null;
            }
            throw new CompletionException(new IllegalArgumentException("Permit with id " + permitId + " has already been released or doesn't exist"));
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Void> releaseAsync(List<String> permitsIds) {
        CompletionStage<Void> f = tryReleaseAsync(permitsIds).handle((res, e) -> {
            if (e != null) {
                throw new CompletionException(e);
            }

            if (res == permitsIds.size()) {
                return null;
            }
            throw new CompletionException(new IllegalArgumentException("Permits with ids " + permitsIds + " have already been released or don't exist"));
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public int availablePermits() {
        return get(availablePermitsAsync());
    }

    @Override
    public int getPermits() {
        return get(getPermitsAsync());
    }

    @Override
    public int acquiredPermits() {
        return get(acquiredPermitsAsync());
    }
    
    @Override
    public RFuture<Integer> availablePermitsAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, -1); " +
                "if #expiredIds > 0 then " +
                    "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                    "local value = redis.call('incrby', KEYS[1], #expiredIds); " + 
                    "if tonumber(value) > 0 then " +
                        "redis.call(ARGV[2], KEYS[3], value); " +
                    "end;" + 
                    "return value; " +
                "end; " +
                "local ret = redis.call('get', KEYS[1]); " + 
                "return ret == false and 0 or ret;",
                Arrays.<Object>asList(getRawName(), timeoutName, channelName),
                System.currentTimeMillis(), getSubscribeService().getPublishCommand());
    }

    @Override
    public RFuture<Integer> getPermitsAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, -1); " +
                "if #expiredIds > 0 then " +
                    "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                    "local value = redis.call('incrby', KEYS[1], #expiredIds); " +
                    "if tonumber(value) > 0 then " +
                        "redis.call(ARGV[2], KEYS[3], value); " +
                    "end;" +
                "end; " +
                "local available = redis.call('get', KEYS[1]); " +
                "if available == false then " +
                    "return 0 " +
                "end;" +
                "local acquired = redis.call('zcount', KEYS[2], 0, '+inf'); " +
                "if acquired == false then " +
                    "return tonumber(available) " +
                "end;" +
                "return tonumber(available) + acquired;",
                Arrays.<Object>asList(getRawName(), timeoutName, channelName),
                System.currentTimeMillis(), getSubscribeService().getPublishCommand());
    }

    @Override
    public RFuture<Integer> acquiredPermitsAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, -1); " +
                "if #expiredIds > 0 then " +
                    "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                    "local value = redis.call('incrby', KEYS[1], #expiredIds); " +
                    "if tonumber(value) > 0 then " +
                        "redis.call(ARGV[2], KEYS[3], value); " +
                    "end;" +
                "end; " +
                "local acquired = redis.call('zcount', KEYS[2], 0, '+inf'); " +
                "return acquired == false and 0 or acquired;",
                Arrays.<Object>asList(getRawName(), timeoutName, channelName),
                System.currentTimeMillis(), getSubscribeService().getPublishCommand());
    }

    @Override
    public boolean trySetPermits(int permits) {
        return get(trySetPermitsAsync(permits));
    }

    @Override
    public void setPermits(int permits) {
        get(setPermitsAsync(permits));
    }

    @Override
    public RFuture<Void> setPermitsAsync(int permits) {
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local available = redis.call('get', KEYS[1]); " +
                "if (available == false) then " +
                    "redis.call('set', KEYS[1], ARGV[1]); " +
                    "redis.call(ARGV[2], KEYS[2], ARGV[1]); " +
                    "return;" +
                "end;" +
                "local acquired = redis.call('zcount', KEYS[3], 0, '+inf'); " +
                "local maximum = (acquired == false and 0 or acquired) + tonumber(available); " +
                "if (maximum == tonumber(ARGV[1])) then " +
                    "return;" +
                "end;" +
                "redis.call('incrby', KEYS[1], tonumber(ARGV[1]) - maximum); " +
                "redis.call(ARGV[2], KEYS[2], ARGV[1]);",
                Arrays.<Object>asList(getRawName(), channelName, timeoutName),
                permits, getSubscribeService().getPublishCommand());
    }

    @Override
    public RFuture<Boolean> trySetPermitsAsync(int permits) {
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false) then "
                    + "redis.call('set', KEYS[1], ARGV[1]); "
                    + "redis.call(ARGV[2], KEYS[2], ARGV[1]); "
                    + "return 1;"
                + "end;"
                + "return 0;",
                Arrays.<Object>asList(getRawName(), channelName),
                permits, getSubscribeService().getPublishCommand());
    }

    @Override
    public void addPermits(int permits) {
        get(addPermitsAsync(permits));
    }
    
    @Override
    public RFuture<Void> addPermitsAsync(int permits) {
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false) then "
                  + "value = 0;"
              + "end;"
              + "redis.call('set', KEYS[1], tonumber(value) + tonumber(ARGV[1])); "
              + "if tonumber(ARGV[1]) > 0 then "
                  + "redis.call(ARGV[2], KEYS[2], ARGV[1]); "
              + "end;",
                Arrays.asList(getRawName(), channelName), permits, getSubscribeService().getPublishCommand());
    }

    @Override
    public RFuture<Boolean> updateLeaseTimeAsync(String permitId, long leaseTime, TimeUnit unit) {
        long timeoutDate = calcTimeout(leaseTime, unit);
        byte[] id = ByteBufUtil.decodeHexDump(permitId);
        return commandExecutor.syncedEvalWithRetry(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local expiredIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[3], 'limit', 0, -1); " +
                "if #expiredIds > 0 then " +
                    "redis.call('zrem', KEYS[2], unpack(expiredIds)); " +
                    "local value = redis.call('incrby', KEYS[1], #expiredIds); " + 
                    "if tonumber(value) > 0 then " +
                        "redis.call(ARGV[4], KEYS[3], value); " +
                    "end;" + 
                "end; " +

                  "local value = redis.call('zscore', KEYS[2], ARGV[1]); " +
                  "if (value ~= false) then "
                    + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[1]); "
                    + "return 1;"
                + "end;"
                + "return 0;",
                Arrays.asList(getRawName(), timeoutName, channelName),
                id, timeoutDate, System.currentTimeMillis(), getSubscribeService().getPublishCommand());
    }

    @Override
    public boolean updateLeaseTime(String permitId, long leaseTime, TimeUnit unit) {
        return get(updateLeaseTimeAsync(permitId, leaseTime, unit));
    }

    private static boolean hasOnlyNearestTimeout(List<String> ids) {
        return ids.size() == 1 && ids.get(0).startsWith(":");
    }

    private static String getFirstOrNull(List<String> ids) {
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }
    
}
