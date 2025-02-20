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

import io.netty.util.Timeout;
import org.redisson.api.RFuture;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RStream;
import org.redisson.api.StreamMessageId;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonReliableTopic extends RedissonExpirable implements RReliableTopic {

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
    private final String subscriberId;
    private volatile RFuture<Map<StreamMessageId, Map<String, Object>>> readFuture;
    private volatile Timeout timeoutTask;
    private final RStream<String, Object> stream;
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final String timeoutName;

    RedissonReliableTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name, String subscriberId) {
        super(codec, commandExecutor, name);
        stream = new RedissonStream<>(new CompositeCodec(StringCodec.INSTANCE, codec), commandExecutor, name);
        if (subscriberId == null) {
            subscriberId = getServiceManager().generateId();
        }
        this.subscriberId = subscriberId;
        this.timeoutName = getTimeout(getRawName());
    }

    RedissonReliableTopic(CommandAsyncExecutor commandExecutor, String name, String subscriberId) {
        this(commandExecutor.getServiceManager().getCfg().getCodec(), commandExecutor, name, subscriberId);
    }

    private String getTimeout(String name) {
        return suffixName(name, "timeout");
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
        return stream.sizeAsync();
    }

    @Override
    public int countListeners() {
        return listeners.size();
    }

    @Override
    public RFuture<Long> publishAsync(Object message) {
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "redis.call('xadd', KEYS[1], '*', 'm', ARGV[1]); "
                    + "local v = redis.call('xinfo', 'groups', KEYS[1]); "
                    + "return #v;",
                Arrays.asList(getRawName()),
                encode(message));
    }

    @Override
    public <M> RFuture<String> addListenerAsync(Class<M> type, MessageListener<M> listener) {
        String id = getServiceManager().generateId();
        listeners.put(id, new Entry(type, listener));

        if (!subscribed.compareAndSet(false, true)) {
            return new CompletableFutureWrapper<>(id);
        }

        RFuture<Void> addFuture = commandExecutor.evalWriteNoRetryAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                          "redis.call('zadd', KEYS[2], ARGV[3], ARGV[2]);" +
                                "redis.call('xgroup', 'create', KEYS[1], ARGV[2], ARGV[1], 'MKSTREAM'); ",
                Arrays.asList(getRawName(), timeoutName),
        StreamMessageId.ALL, subscriberId, System.currentTimeMillis() + getServiceManager().getCfg().getReliableTopicWatchdogTimeout());

        CompletionStage<String> f = addFuture.thenApply(r -> {
            renewExpiration();

            poll(subscriberId);
            return id;
        });

        return new CompletableFutureWrapper<>(f);
    }

    private void poll(String id) {
        RFuture<Map<StreamMessageId, Map<String, Object>>> f = stream.pendingRangeAsync(id, StreamMessageId.MIN, StreamMessageId.MAX, 100);
        CompletionStage<Map<StreamMessageId, Map<String, Object>>> ff = f.thenCompose(r -> {
            if (!subscribed.get()) {
                return CompletableFuture.completedFuture(r);
            }

            if (r.isEmpty()) {
                readFuture = stream.readGroupAsync(id, "consumer",
                                        StreamReadGroupArgs.neverDelivered().timeout(Duration.ofSeconds(0)));
                return readFuture;
            }
            return CompletableFuture.completedFuture(r);
        });

        ff.whenComplete((res, ex) -> {
            if (ex != null) {
                if (getServiceManager().isShuttingDown(ex)) {
                    return;
                }

                if (ex.getCause() != null
                        && ex.getCause().getMessage().contains("NOGROUP")) {
                    return;
                }

                log.error("Unable to poll a new element. Subscriber id: {}", id, ex.getCause());

                getServiceManager().newTimeout(task -> {
                    if (getServiceManager().isShuttingDown()) {
                        return;
                    }

                    poll(id);
                }, 1, TimeUnit.SECONDS);
                return;
            }

            CompletableFuture<Void> done = new CompletableFuture<>();
            if (!listeners.isEmpty()) {
                getServiceManager().getExecutor().execute(() -> {
                    for (Map.Entry<StreamMessageId, Map<String, Object>> entry : res.entrySet()) {
                        Object m = entry.getValue().get("m");
                        listeners.values().forEach(e -> {
                            if (e.getType().isInstance(m)) {
                                ((MessageListener<Object>) e.getListener()).onMessage(getRawName(), m);
                                stream.ack(id, entry.getKey());
                            }
                        });
                    }
                    done.complete(null);
                });
            } else {
                done.complete(null);
            }

            done.thenAccept(r -> {
                long time = System.currentTimeMillis();
                RFuture<Boolean> updateFuture = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                                "local expired = redis.call('zrangebyscore', KEYS[2], 0, tonumber(ARGV[2]) - 1); "
                                + "for i, v in ipairs(expired) do "
                                    + "redis.call('xgroup', 'destroy', KEYS[1], v); "
                                + "end; "
                                + "local r = redis.call('zscore', KEYS[2], ARGV[1]); "

                                + "local score = 92233720368547758;"
                                + "local groups = redis.call('xinfo', 'groups', KEYS[1]); " +
                                  "for i, v in ipairs(groups) do "
                                     + "local id1, id2 = string.match(v[8], '(.*)%-(.*)'); "
                                     + "score = math.min(tonumber(id1), score); "
                                + "end; " +

                                  "score = tostring(score) .. '-0';"
                                + "local range = redis.call('xrange', KEYS[1], score, '+'); "
                                + "if #range == 0 or (#range == 1 and range[1][1] == score) then "
                                    + "redis.call('xtrim', KEYS[1], 'maxlen', 0); "
                                + "else "
                                    + "redis.call('xtrim', KEYS[1], 'maxlen', #range); "
                                + "end;"
                                + "return r ~= false; ",
                        Arrays.asList(getRawName(), timeoutName),
                        id, time);

                updateFuture.whenComplete((re, exc) -> {
                    if (exc != null) {
                        if (getServiceManager().isShuttingDown(exc)) {
                            return;
                        }
                        log.error("Unable to update subscriber status", exc);
                        return;
                    }

                    if (!re || !subscribed.get()) {
                        return;
                    }

                    poll(id);
                });
            });

        });
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), timeoutName);
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        return super.sizeInMemoryAsync(Arrays.asList(getRawName(), timeoutName));
    }

    @Override
    public RFuture<Boolean> copyAsync(List<Object> keys, int database, boolean replace) {
        String newName = (String) keys.get(1);
        List<Object> kks = Arrays.asList(getRawName(), timeoutName,
                newName, getTimeout(newName));
        return super.copyAsync(kks, database, replace);
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
    public RFuture<Void> removeListenerAsync(String... listenerIds) {
        listeners.keySet().removeAll(Arrays.asList(listenerIds));

        if (listeners.isEmpty()) {
            return removeSubscriber();
        }
        return new CompletableFutureWrapper<>((Void) null);
    }

    private RFuture<Void> removeSubscriber() {
        subscribed.set(false);
        readFuture.cancel(false);
        timeoutTask.cancel();

        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "redis.call('xgroup', 'destroy', KEYS[1], ARGV[1]); "
                      + "redis.call('zrem', KEYS[2], ARGV[1]); ",
                Arrays.asList(getRawName(), timeoutName),
                subscriberId);
    }

    @Override
    public int countSubscribers() {
        return get(countSubscribersAsync());
    }

    @Override
    public RFuture<Integer> countSubscribersAsync() {
        return commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                        "local v = redis.call('xinfo', 'groups', KEYS[1]); " +
                              "return #v;",
                Arrays.asList(getRawName()));
    }

    private void renewExpiration() {
        timeoutTask = getServiceManager().newTimeout(t -> {
            if (!subscribed.get() || getServiceManager().isShuttingDown()) {
                return;
            }

            RFuture<Boolean> future = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "if redis.call('zscore', KEYS[1], ARGV[2]) == false then "
                         + "return 0; "
                      + "end; "
                      + "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2]); "
                      + "return 1; ",
                Arrays.asList(timeoutName),
                System.currentTimeMillis() + getServiceManager().getCfg().getReliableTopicWatchdogTimeout(), subscriberId);
            future.whenComplete((res, e) -> {
                if (e != null) {
                    log.error("Can't update reliable topic {} expiration time", getRawName(), e);
                    return;
                }

                if (res) {
                    // reschedule itself
                    renewExpiration();
                }
            });
        }, getServiceManager().getCfg().getReliableTopicWatchdogTimeout() / 3, TimeUnit.MILLISECONDS);
    }


}
