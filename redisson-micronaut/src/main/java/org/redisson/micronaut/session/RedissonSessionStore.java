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
package org.redisson.micronaut.session;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.session.InMemorySessionStore;
import io.micronaut.session.SessionIdGenerator;
import io.micronaut.session.SessionSettings;
import io.micronaut.session.SessionStore;
import io.micronaut.session.event.SessionCreatedEvent;
import io.micronaut.session.event.SessionDeletedEvent;
import io.micronaut.session.event.SessionExpiredEvent;
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Singleton
@Primary
@Requires(property = RedissonSessionStore.ENABLED, value = StringUtils.TRUE)
@Replaces(InMemorySessionStore.class)
public class RedissonSessionStore implements SessionStore<RedissonSession>, PatternMessageListener<String>, MessageListener<String> {

    public static final String ENABLED = SessionSettings.HTTP + ".redisson.enabled";

    private static final String SESSION_PREFIX = "redisson:session:";

    private MessageListener<AttributeMessage> messageListener;

    private final String nodeId = UUID.randomUUID().toString();

    private static final Logger LOG  = LoggerFactory.getLogger(RedissonSessionStore.class);

    private RPatternTopic deletedTopic;
    private RPatternTopic expiredTopic;
    private RTopic createdTopic;
    private RedissonClient redisson;

    private final SessionIdGenerator sessionIdGenerator;
    private RedissonHttpSessionConfiguration sessionConfiguration;
    private final ApplicationEventPublisher eventPublisher;

    public RedissonSessionStore(
            RedissonClient redisson,
            SessionIdGenerator sessionIdGenerator,
            RedissonHttpSessionConfiguration sessionConfiguration,
            ApplicationEventPublisher eventPublisher) {

        this.sessionIdGenerator = sessionIdGenerator;
        this.sessionConfiguration = sessionConfiguration;
        this.eventPublisher = eventPublisher;
        this.redisson = redisson;

        deletedTopic = redisson.getPatternTopic("__keyevent@*:del", StringCodec.INSTANCE);
        expiredTopic = redisson.getPatternTopic("__keyevent@*:expired", StringCodec.INSTANCE);
        createdTopic = redisson.getTopic(getEventsChannelPrefix(), StringCodec.INSTANCE);

        deletedTopic.addListener(String.class, this);
        expiredTopic.addListener(String.class, this);
        createdTopic.addListener(String.class, this);

        if (sessionConfiguration.isBroadcastSessionUpdates()) {
            RTopic updatesTopic = getTopic();
            messageListener = new MessageListener<AttributeMessage>() {

                @Override
                public void onMessage(CharSequence channel, AttributeMessage msg) {
                    if (msg.getNodeId().equals(nodeId)) {
                        return;
                    }

                    findSession(msg.getSessionId()).thenAccept(s -> {
                        if (s.isPresent()) {
                            return;
                        }

                        try {
                            RedissonSession session = s.get();
                            if (msg instanceof AttributeRemoveMessage) {
                                for (CharSequence name : ((AttributeRemoveMessage)msg).getNames()) {
                                    session.superRemove(name);
                                }
                            }

                            if (msg instanceof AttributesClearMessage) {
                                deleteSession(session.getId());
                            }

                            if (msg instanceof AttributesPutAllMessage) {
                                AttributesPutAllMessage m = (AttributesPutAllMessage) msg;
                                Map<CharSequence, Object> attrs = m.getAttrs(getCodec().getMapValueDecoder());
                                session.load(attrs);
                            }

                            if (msg instanceof AttributeUpdateMessage) {
                                AttributeUpdateMessage m = (AttributeUpdateMessage)msg;
                                session.superPut(m.getName(), m.getValue(getCodec().getMapValueDecoder()));
                            }
                        } catch (Exception e) {
                            LOG.error("Unable to handle topic message", e);
                        }
                    });
                }
            };

            updatesTopic.addListener(AttributeMessage.class, messageListener);
        }

    }

    String getEventsChannelPrefix() {
        return sessionConfiguration.getKeyPrefix() + "sessions:created:";
    }

    String getExpiredKeyPrefix() {
        return sessionConfiguration.getKeyPrefix() + "sessions:expires:";
    }

    @Override
    public RedissonSession newSession() {
        return new RedissonSession(this, sessionIdGenerator.generateId(),
                sessionConfiguration.getUpdateMode(), sessionConfiguration.getMaxInactiveInterval());
    }

    @Override
    public CompletableFuture<Optional<RedissonSession>> findSession(String id) {
        return loadSession(id, false);
    }

    @Override
    public CompletableFuture<Boolean> deleteSession(String id) {
        return loadSession(id, false).thenCompose(optional -> {
            return optional.map(s -> {
                return s.delete().thenApply(r -> {
                    return true;
                });
            }).orElse(CompletableFuture.completedFuture(false));
        }).toCompletableFuture();
    }

    @Override
    public CompletableFuture<RedissonSession> save(RedissonSession session) {
        CompletableFuture<RedissonSession> f = session.save();
        return f.thenCompose(v -> {
            if (session.isNew()) {
                return createdTopic.publishAsync(v.getId()).thenApply(val -> v);
            }
            return CompletableFuture.completedFuture(session);
        });
    }

    @Override
    public void onMessage(CharSequence pattern, CharSequence channel, String body) {
        if (deletedTopic.getPatternNames().contains(pattern.toString())) {
            if (!body.contains(SESSION_PREFIX +"notification:")) {
                return;
            }

            String id = body.split(SESSION_PREFIX +"notification:")[1];
            loadSession(id, true).whenComplete((r, e) -> {
                r.ifPresent(v -> {
                    eventPublisher.publishEvent(new SessionDeletedEvent(v));
                });
            });
        } else if (expiredTopic.getPatternNames().contains(pattern.toString())) {
            if (!body.contains(SESSION_PREFIX +"notification:")) {
                return;
            }

            String id = body.split(SESSION_PREFIX +"notification:")[1];
            loadSession(id, true).whenComplete((r, e) -> {
                r.ifPresent(v -> {
                    eventPublisher.publishEvent(new SessionExpiredEvent(v));
                });
            });
        }
    }

    private CompletableFuture<Optional<RedissonSession>> loadSession(String id, boolean useExpired) {
        RMap<CharSequence, Object> map = getMap(id);

        return map.readAllMapAsync().thenApply(data -> {
            if (data.isEmpty()) {
                return Optional.<RedissonSession>empty();
            }
            RedissonSession session = new RedissonSession(this, id,
                    sessionConfiguration.getUpdateMode());
            session.load(data);
            if (useExpired || !session.isExpired()) {
                return Optional.of(session);
            }
            return Optional.<RedissonSession>empty();
        }).toCompletableFuture();
    }

    @Override
    public void onMessage(CharSequence channel, String id) {
        loadSession(id, true).whenComplete((r, e) -> {
            r.ifPresent(v -> {
                eventPublisher.publishEvent(new SessionCreatedEvent(v));
            });
        });
    }

    public RTopic getTopic() {
        String keyPrefix = sessionConfiguration.getKeyPrefix();
        String separator = keyPrefix == null || keyPrefix.isEmpty() ? "" : ":";
        final String name = keyPrefix + separator + "redisson:session_updates";
        return redisson.getTopic(name);
    }

    public String getNodeId() {
        return nodeId;
    }

    public RBatch createBatch() {
        return redisson.createBatch();
    }

    private Codec getCodec() {
        return Optional.ofNullable(sessionConfiguration.getCodec()).orElse(redisson.getConfig().getCodec());
    }

    public RMap<CharSequence, Object> getMap(String sessionId) {
        String keyPrefix = sessionConfiguration.getKeyPrefix();
        String separator = keyPrefix == null || keyPrefix.isEmpty() ? "" : ":";
        String name = keyPrefix + separator + SESSION_PREFIX + sessionId;
        return redisson.getMap(name, new CompositeCodec(StringCodec.INSTANCE, getCodec(), getCodec()));
    }

    public RBucket<Integer> getNotificationBucket(String sessionId) {
        String keyPrefix = sessionConfiguration.getKeyPrefix();
        String separator = keyPrefix == null || keyPrefix.isEmpty() ? "" : ":";
        String name = keyPrefix + separator + SESSION_PREFIX +"notification:" + sessionId;
        return redisson.getBucket(name, IntegerCodec.INSTANCE);
    }

}
