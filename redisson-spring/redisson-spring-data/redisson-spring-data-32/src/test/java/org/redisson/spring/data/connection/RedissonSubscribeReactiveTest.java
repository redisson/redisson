package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSubscribeReactiveTest extends BaseConnectionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static long pubsubNumpat() {
        return redisson.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_ONLY,
                "return redis.call('pubsub', 'numpat')",
                RScript.ReturnType.LONG);
    }

    private static long pubsubNumsub(String channel) {
        return redisson.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_ONLY,
                "return redis.call('pubsub', 'numsub', KEYS[1])[2]",
                RScript.ReturnType.LONG,
                Collections.singletonList(channel));
    }

    private static ByteBuffer buf(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Healthy control: explicit {@code pUnsubscribe(ByteBuffer...)} after {@code pSubscribe}
     * emits {@code PUNSUBSCRIBE}, clears {@code getPatterns()}, and restores {@code PUBSUB NUMPAT}
     * to baseline while the client connection remains open.
     */
    @Test
    public void testExplicitPUnsubscribe() {
        RedissonClient client = createInstance();
        RedissonConnectionFactory factory = new RedissonConnectionFactory(client);
        ReactiveRedisConnection connection = null;
        try {
            connection = factory.getReactiveConnection();
            ReactiveSubscription subscription = connection.pubSubCommands().createSubscription().block(TIMEOUT);
            ByteBuffer wildcard = buf("rp:explicit:*");
            ByteBuffer literal = buf("rp:explicit:literal");

            long baselineNumpat = pubsubNumpat();
            subscription.pSubscribe(wildcard, literal).block(TIMEOUT);
            subscription.receive().subscribe();

            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat + 2);
            });

            subscription.pUnsubscribe(wildcard, literal).block(TIMEOUT);

            assertThat(subscription.getPatterns()).isEmpty();
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat);
            });
        } finally {
            if (connection != null) {
                connection.closeLater().block(TIMEOUT);
            }
            client.shutdown();
        }
    }

    /**
     * Regression for no-arg {@code pUnsubscribe()} after {@code pSubscribe} with wildcard and literal patterns:
     * failed on the unfixed implementation (delegated to {@code unsubscribe(...)}, emitted {@code UNSUBSCRIBE},
     * left {@code getPatterns()} non-empty and {@code PUBSUB NUMPAT} elevated while open).
     * Verifies fixed behavior: clears patterns via {@code PUNSUBSCRIBE} to restore {@code PUBSUB NUMPAT}
     * while preserving an unrelated ordinary channel subscription ({@code PUBSUB NUMSUB} and delivery).
     */
    @Test
    public void testPUnsubscribeAll() {
        RedissonClient client = createInstance();
        RedissonConnectionFactory factory = new RedissonConnectionFactory(client);
        ReactiveRedisConnection connection = null;
        ReactiveSubscription subscription = null;
        String keptChannel = "rc:keep";
        try {
            connection = factory.getReactiveConnection();
            subscription = connection.pubSubCommands().createSubscription().block(TIMEOUT);
            long baselineNumpat = pubsubNumpat();
            long baselineNumsub = pubsubNumsub(keptChannel);

            Queue<String> channelMessages = new ConcurrentLinkedQueue<>();
            subscription.subscribe(buf(keptChannel)).block(TIMEOUT);
            subscription.pSubscribe(buf("rp:all:*"), buf("rp:all:literal")).block(TIMEOUT);
            subscription.receive().subscribe(msg -> {
                if (!(msg instanceof ReactiveSubscription.PatternMessage)) {
                    channelMessages.add(StandardCharsets.UTF_8.decode(msg.getMessage().duplicate()).toString());
                }
            });

            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat + 2);
                assertThat(pubsubNumsub(keptChannel)).isEqualTo(baselineNumsub + 1);
            });

            subscription.pUnsubscribe().block(TIMEOUT);

            assertThat(subscription.getPatterns()).isEmpty();
            assertThat(subscription.getChannels()).hasSize(1);
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat);
                assertThat(pubsubNumsub(keptChannel)).isEqualTo(baselineNumsub + 1);
            });

            connection.pubSubCommands().publish(buf(keptChannel), buf("still-alive")).block(TIMEOUT);
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(channelMessages).contains("still-alive");
            });

            subscription.unsubscribe().block(TIMEOUT);
            assertThat(subscription.getChannels()).isEmpty();
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumsub(keptChannel)).isEqualTo(baselineNumsub);
            });
        } finally {
            if (subscription != null) {
                try {
                    subscription.unsubscribe().block(Duration.ofSeconds(2));
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.closeLater().block(TIMEOUT);
            }
            client.shutdown();
        }
    }

    /**
     * Regression for {@code cancel()} with both an ordinary channel and pattern subscriptions:
     * failed on the unfixed implementation (cleaned the ordinary channel via {@code UNSUBSCRIBE}
     * while leaving patterns active via {@code pUnsubscribe()} -&gt; {@code unsubscribe(...)}).
     * Verifies fixed behavior: clears both {@code getChannels()} and {@code getPatterns()} and restores
     * both {@code PUBSUB NUMSUB} and {@code PUBSUB NUMPAT} to baseline while open.
     */
    @Test
    public void testCancelWithChannelAndPatterns() {
        RedissonClient client = createInstance();
        RedissonConnectionFactory factory = new RedissonConnectionFactory(client);
        ReactiveRedisConnection connection = null;
        String channel = "rc:cancel";
        try {
            connection = factory.getReactiveConnection();
            ReactiveSubscription subscription = connection.pubSubCommands().createSubscription().block(TIMEOUT);
            long baselineNumpat = pubsubNumpat();
            long baselineNumsub = pubsubNumsub(channel);

            subscription.subscribe(buf(channel)).block(TIMEOUT);
            subscription.pSubscribe(buf("rp:cancel:*"), buf("rp:cancel:literal")).block(TIMEOUT);
            subscription.receive().subscribe();

            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat + 2);
                assertThat(pubsubNumsub(channel)).isEqualTo(baselineNumsub + 1);
            });

            subscription.cancel().block(TIMEOUT);

            assertThat(subscription.getChannels()).isEmpty();
            assertThat(subscription.getPatterns()).isEmpty();
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumsub(channel)).isEqualTo(baselineNumsub);
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat);
            });
        } finally {
            if (connection != null) {
                connection.closeLater().block(TIMEOUT);
            }
            client.shutdown();
        }
    }

    /**
     * Regression for {@code ReactiveRedisMessageListenerContainer.receive(PatternTopic...)} Flux disposal:
     * failed on the unfixed implementation ({@code dispose()} triggers {@code cancel()} -&gt; no-arg {@code pUnsubscribe()},
     * emitting {@code UNSUBSCRIBE} and leaving {@code PUBSUB NUMPAT} elevated while client stays open).
     * Verifies fixed behavior: restores {@code PUBSUB NUMPAT} to baseline upon disposal.
     */
    @Test
    public void testListenerContainerPatternCancel() {
        RedissonClient client = createInstance();
        RedissonConnectionFactory factory = new RedissonConnectionFactory(client);
        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(factory);
        Disposable disposable = null;
        try {
            long baselineNumpat = pubsubNumpat();
            Queue<String> received = new ConcurrentLinkedQueue<>();
            disposable = container.receive(
                            PatternTopic.of("rp:container:*"),
                            PatternTopic.of("rp:container:literal"))
                    .subscribe(msg -> received.add(msg.getMessage()));

            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat + 2);
            });

            redisson.getTopic("rp:container:hello", StringCodec.INSTANCE).publish("ping");
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(received).contains("ping");
            });

            disposable.dispose();

            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat);
            });
        } finally {
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
            try {
                container.destroy();
            } catch (Exception ignored) {
            }
            client.shutdown();
        }
    }

    /**
     * Regression for {@code ReactiveRedisMessageListenerContainer.destroy()} while pattern listeners are active:
     * failed on the unfixed implementation ({@code destroy()} triggers {@code cancel()} -&gt; no-arg {@code pUnsubscribe()},
     * emitting {@code UNSUBSCRIBE} and leaving {@code PUBSUB NUMPAT} elevated while {@code RedissonClient} stays open).
     * Verifies fixed behavior: restores {@code PUBSUB NUMPAT} to baseline upon container destruction.
     */
    @Test
    public void testListenerContainerPatternDestroy() throws Exception {
        RedissonClient client = createInstance();
        RedissonConnectionFactory factory = new RedissonConnectionFactory(client);
        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(factory);
        try {
            long baselineNumpat = pubsubNumpat();
            Queue<String> received = new ConcurrentLinkedQueue<>();
            container.receive(
                            PatternTopic.of("rp:destroy:*"),
                            PatternTopic.of("rp:destroy:literal"))
                    .subscribe(msg -> received.add(msg.getMessage()));

            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat + 2);
            });

            redisson.getTopic("rp:destroy:hello", StringCodec.INSTANCE).publish("ping");
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(received).contains("ping");
            });

            container.destroy();

            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(pubsubNumpat()).isEqualTo(baselineNumpat);
            });
        } finally {
            try {
                container.destroy();
            } catch (Exception ignored) {
            }
            client.shutdown();
        }
    }

    @Test
    public void testPubSub() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        AtomicLong counter = new AtomicLong();

        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
        template.listenTo(ChannelTopic.of("test")).flatMap(message -> {
            counter.incrementAndGet();
            return Mono.empty();
        }).subscribe();

        for (int i = 0; i < 40; i++) {
            ReactiveRedisConnection connection = factory.getReactiveConnection();
            connection.pubSubCommands().publish(ByteBuffer.wrap("test".getBytes()), ByteBuffer.wrap("msg".getBytes())).block();
        }

        Awaitility.await().atMost(Durations.ONE_SECOND).untilAsserted(() -> {
            assertThat(counter.get()).isEqualTo(40);
        });
    }

    @Test
    public void testTemplate() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        AtomicLong counter = new AtomicLong();

        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
        template.listenTo(ChannelTopic.of("test")).flatMap(message -> {
            counter.incrementAndGet();
            return Mono.empty();
        }).subscribe();

        template.listenTo(ChannelTopic.of("test2")).flatMap(message -> {
            counter.incrementAndGet();
            return Mono.empty();
        }).subscribe();

        ReactiveRedisConnection connection = factory.getReactiveConnection();
        connection.pubSubCommands().publish(ByteBuffer.wrap("test".getBytes()), ByteBuffer.wrap("msg".getBytes())).block();

        Awaitility.await().atMost(Durations.ONE_SECOND)
                    .until(() -> counter.get() == 1);

        BitFieldSubCommands commands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.UINT_32).valueAt(0)
                .get(BitFieldSubCommands.BitFieldType.UINT_32).valueAt(1)
                .get(BitFieldSubCommands.BitFieldType.UINT_32).valueAt(2);
        for (int i = 0; i < 128; i++) {
            template.opsForValue().setBit("key", i, true);
        }

        AtomicReference<List<Long>> result = new AtomicReference<>();
        template.opsForValue().bitField("key", commands).doOnNext(r -> {
            result.set(r);
        }).subscribe();

        Awaitility.waitAtMost(Durations.FIVE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
            assertThat(result.get()).isEqualTo(Arrays.asList(0L, 0L, 0L));
        });
    }

    @Test
    public void testSubscribe() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection connection = factory.getReactiveConnection();
        Mono<ReactiveSubscription> s = connection.pubSubCommands().createSubscription();
        AtomicReference<byte[]> msg = new AtomicReference<byte[]>();
        ReactiveSubscription ss = s.block();

        ss.subscribe(ByteBuffer.wrap("test".getBytes())).block();
        ss.receive().doOnEach(message -> {
            msg.set(message.get().getMessage().array());
        }).subscribe();
        
        connection.pubSubCommands().publish(ByteBuffer.wrap("test".getBytes()), ByteBuffer.wrap("msg".getBytes())).block();

        Awaitility.await().atMost(Durations.ONE_SECOND)
                    .until(() -> Arrays.equals("msg".getBytes(), msg.get()));
        
        ss.unsubscribe();
        
        connection.pubSubCommands().publish(ByteBuffer.wrap("test".getBytes()), ByteBuffer.wrap("msg".getBytes())).block();
    }
    
    @Test
    public void testUnSubscribe() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection connection = factory.getReactiveConnection();
        Mono<ReactiveSubscription> s = connection.pubSubCommands().createSubscription();
        AtomicReference<byte[]> msg = new AtomicReference<byte[]>();
        ReactiveSubscription ss = s.block();

        ss.subscribe(ByteBuffer.wrap("test".getBytes())).block();
        ss.receive().doOnEach(message -> {
            msg.set(message.get().getMessage().array());
        }).subscribe();
        
        connection.pubSubCommands().publish(ByteBuffer.wrap("test".getBytes()), ByteBuffer.wrap("msg".getBytes())).block();
        Awaitility.await().atMost(Durations.ONE_SECOND)
                    .until(() -> Arrays.equals("msg".getBytes(), msg.get()));
        
        ss.unsubscribe();
        
        connection.pubSubCommands().publish(ByteBuffer.wrap("test".getBytes()), ByteBuffer.wrap("msg".getBytes())).block();
        
        
    }

}
