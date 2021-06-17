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
import io.netty.util.Timeout;
import org.redisson.api.RFuture;
import org.redisson.api.RReliableTopic;
import org.redisson.api.StreamMessageId;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonReliableTopic extends RedissonExpirable implements RReliableTopic {

    private static final Logger log = LoggerFactory.getLogger(RedissonReliableTopic.class);

    private static class Entry {

        private final Class<?> type;
        private final MessageListener<?> listener;

        Entry(Class<?> type, MessageListener<?> listener) {
            this.type = type;
            this.listener = listener;
        }

        public Class<?> getType() {
            return type;
        }

        public MessageListener<?> getListener() {
            return listener;
        }
    }

    private final Map<String, Entry> listeners = new ConcurrentHashMap<>();
    private final AtomicReference<String> subscriberId = new AtomicReference<>();
    private volatile RFuture<Map<StreamMessageId, Map<String, Object>>> readFuture;
    private volatile Timeout timeoutTask;

    public RedissonReliableTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    public RedissonReliableTopic(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    private String getSubscribersName() {
        return suffixName(getRawName(), "subscribers");
    }

    private String getMapName() {
        return suffixName(getRawName(), "map");
    }

    private String getCounter() {
        return suffixName(getRawName(), "counter");
    }

    private String getTimeout() {
        return suffixName(getRawName(), "timeout");
    }

    @Override
    public long publish(Object message) {
        return get(publishAsync(message));
    }

    @Override
    public <M> String addListener(Class<M> type, MessageListener<M> listener) {
        return get(addListenerAsync(type, listener));
    }

    @Override
    public void removeListener(String... listenerIds) {
        get(removeListenerAsync(listenerIds));
    }

    @Override
    public void removeAllListeners() {
        get(removeAllListenersAsync());
    }

    public RFuture<Void> removeAllListenersAsync() {
        listeners.clear();
        return removeSubscriber();
    }

    @Override
    public long size() {
        return get(sizeAsync());
    }

    public RFuture<Long> sizeAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.XLEN, getRawName());
    }

    @Override
    public int countListeners() {
        return listeners.size();
    }

    @Override
    public RFuture<Long> publishAsync(Object message) {
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "redis.call('xadd', KEYS[1], '*', 'm', ARGV[1]); "
                        + "return redis.call('zcard', KEYS[2]); ",
                Arrays.asList(getRawName(), getSubscribersName()), encode(message));
    }

    protected String generateId() {
        byte[] id = new byte[16];
        ThreadLocalRandom.current().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

    @Override
    public <M> RFuture<String> addListenerAsync(Class<M> type, MessageListener<M> listener) {
        String id = generateId();
        listeners.put(id, new Entry(type, listener));

        if (subscriberId.get() != null) {
            return RedissonPromise.newSucceededFuture(id);
        }

        if (subscriberId.compareAndSet(null, id)) {
            renewExpiration();

            StreamMessageId startId = new StreamMessageId(System.currentTimeMillis(), 0);

            RPromise<String> promise = new RedissonPromise<>();
            RFuture<Void> addFuture = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                    "local value = redis.call('incr', KEYS[3]); "
                            + "redis.call('zadd', KEYS[4], ARGV[3], ARGV[2]); "
                            + "redis.call('zadd', KEYS[1], value, ARGV[2]); "
                            + "redis.call('hset', KEYS[2], ARGV[2], ARGV[1]); ",
                    Arrays.asList(getSubscribersName(), getMapName(), getCounter(), getTimeout()),
                    startId, id, System.currentTimeMillis() + commandExecutor.getConnectionManager().getCfg().getReliableTopicWatchdogTimeout());
            addFuture.onComplete((r, e) -> {
                if (e != null) {
                    promise.tryFailure(e);
                    return;
                }

                poll(id, startId);
                promise.trySuccess(id);
            });

            return promise;
        }

        return RedissonPromise.newSucceededFuture(id);
    }

    private void poll(String id, StreamMessageId startId) {
        readFuture = commandExecutor.readAsync(getRawName(), new CompositeCodec(StringCodec.INSTANCE, codec),
                RedisCommands.XREAD_BLOCKING_SINGLE, "BLOCK", 0, "STREAMS", getRawName(), startId);
        readFuture.onComplete((res, ex) -> {
            if (readFuture.isCancelled()) {
                return;
            }
            if (ex != null) {
                if (ex instanceof RedissonShutdownException) {
                    return;
                }

                poll(id, startId);
                return;
            }

            commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                res.values().forEach(entry -> {
                    Object m = entry.get("m");
                    listeners.values().forEach(e -> {
                        if (e.getType().isInstance(m)) {
                            ((MessageListener<Object>) e.getListener()).onMessage(getRawName(), m);
                        }
                    });
                });
            });

            if (listeners.isEmpty()) {
                return;
            }

            StreamMessageId lastId = res.keySet().stream().skip(res.size() - 1).findFirst().get();
            long time = System.currentTimeMillis();
            RFuture<Boolean> updateFuture = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "local r = redis.call('zscore', KEYS[2], ARGV[2]); "
                            + "if r ~= false then "
                                + "local value = redis.call('incr', KEYS[4]); "
                                + "redis.call('zadd', KEYS[2], value, ARGV[2]); "
                                + "redis.call('hset', KEYS[3], ARGV[2], ARGV[1]); "
                            + "end; "

                            + "local t = redis.call('zrange', KEYS[5], 0, 0, 'WITHSCORES'); "
                            + "if tonumber(t[2]) < tonumber(ARGV[3]) then "
                                + "redis.call('hdel', KEYS[3], t[1]); "
                                + "redis.call('zrem', KEYS[2], t[1]); "
                                + "redis.call('zrem', KEYS[5], t[1]); "
                            + "end; "

                            + "local v = redis.call('zrange', KEYS[2], 0, 0); "
                            + "local score = redis.call('hget', KEYS[3], v[1]); "
                            + "local range = redis.call('xrange', KEYS[1], score, '+'); "
                            + "if #range == 0 then "
                                + "redis.call('del', KEYS[1]); "
                            + "elseif #range == 1 and range[1][1] == score then "
                                + "redis.call('del', KEYS[1]); "
                            + "else "
                                + "redis.call('xtrim', KEYS[1], 'maxlen', #range); "
                            + "end;"
                            + "return r ~= false; ",
                    Arrays.asList(getRawName(), getSubscribersName(), getMapName(), getCounter(), getTimeout()),
                    lastId, id, time);
            updateFuture.onComplete((re, exc) -> {
                if (exc != null) {
                    if (exc instanceof RedissonShutdownException) {
                        return;
                    }
                    log.error("Unable to update subscriber status", exc);
                    return;
                }

                if (!re || listeners.isEmpty()) {
                    return;
                }

                poll(id, lastId);
            });

        });
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), getSubscribersName(), getMapName(), getCounter(), getTimeout());
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        return super.sizeInMemoryAsync(Arrays.asList(getRawName(), getSubscribersName(), getMapName(), getCounter(), getTimeout()));
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return expireAsync(timeToLive, timeUnit, getRawName(), getSubscribersName(), getMapName(), getCounter(), getTimeout());
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String... keys) {
        return super.expireAtAsync(timestamp, getRawName(), getSubscribersName(), getMapName(), getCounter(), getTimeout());
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), getSubscribersName(), getMapName(), getCounter(), getTimeout());
    }

    @Override
    public RFuture<Void> removeListenerAsync(String... listenerIds) {
        listeners.keySet().removeAll(Arrays.asList(listenerIds));

        if (listeners.isEmpty()) {
            return removeSubscriber();
        }
        return RedissonPromise.newSucceededFuture(null);
    }

    private RFuture<Void> removeSubscriber() {
        readFuture.cancel(false);
        timeoutTask.cancel();

        String id = subscriberId.getAndSet(null);
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "redis.call('zrem', KEYS[3], ARGV[1]); "
                      + "redis.call('zrem', KEYS[1], ARGV[1]); "
                      + "redis.call('hdel', KEYS[2], ARGV[1]); ",
                Arrays.asList(getSubscribersName(), getMapName(), getTimeout()),
                id);
    }

    @Override
    public int countSubscribers() {
        return get(countSubscribersAsync());
    }

    @Override
    public RFuture<Integer> countSubscribersAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.ZCARD_INT, getSubscribersName());
    }

    private void renewExpiration() {
        timeoutTask = commandExecutor.getConnectionManager().newTimeout(t -> {
            RFuture<Boolean> future = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                   "if redis.call('zscore', KEYS[1], ARGV[2]) == false then "
                         + "return 0; "
                      + "end; "
                      + "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2]); "
                      + "return 1; ",
                Arrays.asList(getTimeout()),
                System.currentTimeMillis() + commandExecutor.getConnectionManager().getCfg().getReliableTopicWatchdogTimeout(), subscriberId.get());
            future.onComplete((res, e) -> {
                if (e != null) {
                    log.error("Can't update reliable topic " + getRawName() + " expiration time", e);
                    return;
                }

                if (res) {
                    // reschedule itself
                    renewExpiration();
                }
            });
        }, commandExecutor.getConnectionManager().getCfg().getReliableTopicWatchdogTimeout() / 3, TimeUnit.MILLISECONDS);
    }


}
