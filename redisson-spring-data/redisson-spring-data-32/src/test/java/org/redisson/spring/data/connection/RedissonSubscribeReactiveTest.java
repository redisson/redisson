package org.redisson.spring.data.connection;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveSubscription;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonSubscribeReactiveTest extends BaseConnectionTest {

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

        Awaitility.await().atMost(Duration.ONE_SECOND).untilAsserted(() -> {
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

        Awaitility.await().atMost(Duration.ONE_SECOND)
                    .until(() -> counter.get() == 1);
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

        Awaitility.await().atMost(Duration.ONE_SECOND)
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
        Awaitility.await().atMost(Duration.ONE_SECOND)
                    .until(() -> Arrays.equals("msg".getBytes(), msg.get()));
        
        ss.unsubscribe();
        
        connection.pubSubCommands().publish(ByteBuffer.wrap("test".getBytes()), ByteBuffer.wrap("msg".getBytes())).block();
        
        
    }

}
