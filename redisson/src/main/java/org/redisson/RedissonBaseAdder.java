/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
import org.redisson.api.RFuture;
import org.redisson.api.RSemaphore;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class RedissonBaseAdder<T extends Number> extends RedissonExpirable {

    private static final Logger log = LoggerFactory.getLogger(RedissonBaseAdder.class);
    
    private static final String CLEAR_MSG = "0";
    private static final String SUM_MSG = "1";

    private final RedissonClient redisson;
    private final RTopic topic;
    private final int listenerId;
    
    public RedissonBaseAdder(CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(connectionManager, name);
        
        topic = redisson.getTopic(suffixName(getRawName(), "topic"), StringCodec.INSTANCE);
        this.redisson = redisson;
        listenerId = topic.addListener(String.class, (channel, msg) -> {
            String[] parts = msg.split(":");
            String id = parts[1];

            RSemaphore semaphore = getSemaphore(id);
            if (parts[0].equals(SUM_MSG)) {
                RFuture<T> addAndGetFuture = addAndGetAsync(id);
                addAndGetFuture.whenComplete((res, e) -> {
                    if (e != null) {
                        log.error("Can't increase sum", e);
                        return;
                    }

                    semaphore.releaseAsync().whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.error("Can't release semaphore", ex);
                        }
                    });
                });
            }

            if (parts[0].equals(CLEAR_MSG)) {
                doReset();
                semaphore.releaseAsync().whenComplete((res, e) -> {
                    if (e != null) {
                        log.error("Can't release semaphore", e);
                    }
                });
            }
        });
    }

    protected abstract void doReset();

    private String generateId() {
        byte[] id = new byte[16];
        ThreadLocalRandom.current().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

    public void reset() {
        get(resetAsync());
    }
    
    public void reset(long timeout, TimeUnit timeUnit) {
        get(resetAsync(timeout, timeUnit));
    }
    
    public RFuture<T> sumAsync() {
        String id = generateId();
        RFuture<Long> future = topic.publishAsync(SUM_MSG + ":" + id);
        RSemaphore semaphore = getSemaphore(id);

        CompletionStage<T> f = future.thenCompose(r -> semaphore.acquireAsync(r.intValue()))
                                        .thenCompose(r -> getAndDeleteAsync(id))
                                        .thenCompose(r -> semaphore.deleteAsync().thenApply(res -> r));
        return new CompletableFutureWrapper<>(f);
    }

    private RSemaphore getSemaphore(String id) {
        return redisson.getSemaphore(suffixName(getRawName(), id + ":semaphore"));
    }

    protected String getCounterName(String id) {
        return suffixName(getRawName(), id + ":counter");
    }

    public RFuture<T> sumAsync(long timeout, TimeUnit timeUnit) {
        String id = generateId();
        RFuture<Long> future = topic.publishAsync(SUM_MSG + ":" + id);
        RSemaphore semaphore = getSemaphore(id);
        CompletionStage<T> f = future.thenCompose(r -> tryAcquire(semaphore, timeout, timeUnit, r.intValue()))
                                    .thenCompose(r -> getAndDeleteAsync(id))
                                    .thenCompose(r -> semaphore.deleteAsync().thenApply(res -> r));
        return new CompletableFutureWrapper<>(f);
    }

    protected CompletionStage<Void> tryAcquire(RSemaphore semaphore, long timeout, TimeUnit timeUnit, int value) {
        return semaphore.tryAcquireAsync(value, timeout, timeUnit).handle((res, e) -> {
            if (e != null) {
                throw new CompletionException(e);
            }
            
            if (res) {
                return null;
            }
            throw new CompletionException(new TimeoutException());
        });
    }

    public RFuture<Void> resetAsync() {
        String id = generateId();
        RFuture<Long> future = topic.publishAsync(CLEAR_MSG + ":" + id);
        RSemaphore semaphore = getSemaphore(id);
        CompletionStage<Void> f = future.thenCompose(r -> semaphore.acquireAsync(r.intValue()))
                                        .thenCompose(r -> semaphore.deleteAsync().thenApply(res -> null));
        return new CompletableFutureWrapper<>(f);
    }
    
    public RFuture<Void> resetAsync(long timeout, TimeUnit timeUnit) {
        String id = generateId();
        RFuture<Long> future = topic.publishAsync(CLEAR_MSG + ":" + id);
        RSemaphore semaphore = getSemaphore(id);
        CompletionStage<Void> f = future.thenCompose(r -> tryAcquire(semaphore, timeout, timeUnit, r.intValue()))
                                        .thenCompose(r -> semaphore.deleteAsync().thenApply(res -> null));
        return new CompletableFutureWrapper<>(f);
    }

    public void destroy() {
        topic.removeListener(listenerId);
    }

    protected abstract RFuture<T> addAndGetAsync(String id);

    protected abstract RFuture<T> getAndDeleteAsync(String id);

}
