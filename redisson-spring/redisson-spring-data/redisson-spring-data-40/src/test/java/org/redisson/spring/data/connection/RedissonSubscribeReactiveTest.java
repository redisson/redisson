package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSubscribeReactiveTest extends BaseConnectionTest {

    @Test
    public void testBinaryChannelAndPattern() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        byte[] channel = {(byte) 0xFF, 'c', '1'};
        byte[] pattern = {(byte) 0xFF, 'p', '*'};
        byte[] patternChannel = {(byte) 0xFF, 'p', '1'};
        Queue<byte[]> subscribed = new ConcurrentLinkedQueue<>();
        ReactiveSubscription subscription = factory.getReactiveConnection().pubSubCommands().createSubscription(new SubscriptionListener() {
            @Override
            public void onChannelSubscribed(byte[] name, long count) {
                subscribed.add(name);
            }

            @Override
            public void onPatternSubscribed(byte[] name, long count) {
                subscribed.add(name);
            }
        }).block();
        subscription.subscribe(ByteBuffer.wrap(channel)).block();
        subscription.pSubscribe(ByteBuffer.wrap(pattern)).block();
        Queue<ReactiveSubscription.Message<ByteBuffer, ByteBuffer>> received = new ConcurrentLinkedQueue<>();
        subscription.receive().subscribe(received::add);

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(subscribed).containsExactlyInAnyOrder(channel, pattern);
        });
        RedisConnection connection = factory.getConnection();
        connection.publish(channel, "m1".getBytes());
        connection.publish(patternChannel, "m2".getBytes());

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(received).hasSize(2);
        });
        for (ReactiveSubscription.Message<ByteBuffer, ByteBuffer> message : received) {
            if (message instanceof ReactiveSubscription.PatternMessage) {
                assertThat(((ReactiveSubscription.PatternMessage<ByteBuffer, ByteBuffer, ByteBuffer>) message).getPattern()).isEqualTo(ByteBuffer.wrap(pattern));
                assertThat(message.getChannel()).isEqualTo(ByteBuffer.wrap(patternChannel));
                assertThat(message.getMessage()).isEqualTo(ByteBuffer.wrap("m2".getBytes()));
            } else {
                assertThat(message.getChannel()).isEqualTo(ByteBuffer.wrap(channel));
                assertThat(message.getMessage()).isEqualTo(ByteBuffer.wrap("m1".getBytes()));
            }
        }
        subscription.cancel().block();
    }

    @Test
    public void testSubscribeAfterReceive() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveSubscription subscription = factory.getReactiveConnection().pubSubCommands().createSubscription().block();
        subscription.subscribe(ByteBuffer.wrap("first".getBytes())).block();
        Queue<String> received = new ConcurrentLinkedQueue<>();
        Queue<String> receivedTwice = new ConcurrentLinkedQueue<>();
        subscription.receive().subscribe(message -> received.add(new String(message.getMessage().array())));
        subscription.receive().subscribe(message -> receivedTwice.add(new String(message.getMessage().array())));

        subscription.subscribe(ByteBuffer.wrap("second".getBytes())).block();
        subscription.pSubscribe(ByteBuffer.wrap("third*".getBytes())).block();
        RedisConnection connection = factory.getConnection();
        connection.publish("first".getBytes(), "m1".getBytes());
        connection.publish("second".getBytes(), "m2".getBytes());
        connection.publish("third1".getBytes(), "m3".getBytes());

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(received).containsExactlyInAnyOrder("m1", "m2", "m3");
            assertThat(receivedTwice).containsExactlyInAnyOrder("m1", "m2", "m3");
        });
        subscription.cancel().block();
    }

    @Test
    public void testCancelKeepsOtherSubscriptions() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ByteBuffer channel = ByteBuffer.wrap("shared".getBytes());
        ReactiveSubscription first = factory.getReactiveConnection().pubSubCommands().createSubscription().block();
        ReactiveSubscription second = factory.getReactiveConnection().pubSubCommands().createSubscription().block();
        first.subscribe(channel).block();
        second.subscribe(channel).block();
        Queue<String> received = new ConcurrentLinkedQueue<>();
        first.receive().subscribe();
        second.receive().subscribe(message -> received.add(new String(message.getMessage().array())));

        first.cancel().block();
        factory.getConnection().publish("shared".getBytes(), "after".getBytes());

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(received).containsExactly("after");
        });
        second.cancel().block();
    }

    @Test
    public void testDisposedListenerKeepsOtherListeners() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
        Queue<String> first = new ConcurrentLinkedQueue<>();
        Queue<String> second = new ConcurrentLinkedQueue<>();
        Disposable a = template.listenToChannel("shared").subscribe(message -> first.add(message.getMessage()));
        Disposable b = template.listenToChannel("shared").subscribe(message -> second.add(message.getMessage()));

        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            template.convertAndSend("shared", "before").block();
            assertThat(first).contains("before");
            assertThat(second).contains("before");
        });

        a.dispose();
        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            second.clear();
            template.convertAndSend("shared", "after").block();
            assertThat(second).contains("after");
        });
        b.dispose();
    }

    @Test
    public void testPUnsubscribeAll() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        Supplier<Long> subscribedPatterns = () -> redisson.getScript().eval(RScript.Mode.READ_ONLY,
                                                    "return redis.call('pubsub', 'numpat')", RScript.ReturnType.LONG);
        ReactiveSubscription subscription = factory.getReactiveConnection().pubSubCommands().createSubscription().block();
        subscription.pSubscribe(ByteBuffer.wrap("rp*".getBytes())).block();
        subscription.receive().subscribe();
        assertThat(subscribedPatterns.get()).isEqualTo(1);

        subscription.pUnsubscribe().block();

        assertThat(subscription.getPatterns()).isEmpty();
        Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
            assertThat(subscribedPatterns.get()).isZero();
        });
        subscription.cancel().block();
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
