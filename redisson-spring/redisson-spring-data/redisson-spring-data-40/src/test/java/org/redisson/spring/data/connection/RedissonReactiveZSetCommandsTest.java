package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.springframework.data.domain.Range;
import org.springframework.data.domain.Range.Bound;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveZSetCommands;
import org.springframework.data.redis.connection.ReactiveZSetCommands.BZPopCommand;
import org.springframework.data.redis.connection.ReactiveZSetCommands.ZAddCommand;
import org.springframework.data.redis.connection.zset.DefaultTuple;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

public class RedissonReactiveZSetCommandsTest extends BaseConnectionTest {

    private final ReactiveZSetCommands zSetCommands =
            new RedissonConnectionFactory(redisson).getReactiveConnection().zSetCommands();

    @Test
    public void testBZPopWithTimeoutUnderOneSecond() {
        ByteBuffer key = ByteBuffer.wrap("empty-zset".getBytes());
        Duration timeout = Duration.ofMillis(500);

        long start = System.nanoTime();
        assertThat(zSetCommands.bZPopMin(key, timeout).block(Duration.ofSeconds(10))).isNull();
        assertThat(zSetCommands.bZPopMax(key, timeout).block(Duration.ofSeconds(10))).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(timeout.multipliedBy(2));
    }

    @Test
    public void testBZPopRejectsNegativeTimeout() {
        BZPopCommand command = BZPopCommand.min().from(ByteBuffer.wrap("empty-zset".getBytes()))
                .blockingFor(-500, TimeUnit.MILLISECONDS);

        assertThatThrownBy(() -> zSetCommands.bZPop(Mono.just(command))
                .flatMap(CommandResponse::getOutput)
                .blockFirst(Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testRankRangesWithAnUnboundedSide() {
        byte[] key = "rank-unbounded".getBytes();
        connection.zSetCommands().zAdd(key, 1, "a".getBytes());
        connection.zSetCommands().zAdd(key, 2, "b".getBytes());
        connection.zSetCommands().zAdd(key, 3, "c".getBytes());
        ByteBuffer keyBuf = ByteBuffer.wrap(key);

        assertThat(zSetCommands.zRange(keyBuf, Range.unbounded()).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("a", "b", "c");
        assertThat(zSetCommands.zRevRange(keyBuf, Range.unbounded()).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("c", "b", "a");
        assertThat(zSetCommands.zRangeWithScores(keyBuf, Range.rightUnbounded(Bound.inclusive(1L))).map(Tuple::getScore).collectList().block())
                .containsExactly(2.0, 3.0);
        assertThat(zSetCommands.zRevRangeWithScores(keyBuf, Range.unbounded()).map(Tuple::getScore).collectList().block())
                .containsExactly(3.0, 2.0, 1.0);
        assertThat(zSetCommands.zRemRangeByRank(keyBuf, Range.rightUnbounded(Bound.inclusive(1L))).block()).isEqualTo(2);
        assertThat(connection.zSetCommands().zCard(key)).isEqualTo(1);
    }

    @Test
    public void testLexRangesWithAnUnboundedSide() {
        byte[] key = "lex-unbounded".getBytes();
        connection.zSetCommands().zAdd(key, 0, "a".getBytes());
        connection.zSetCommands().zAdd(key, 0, "b".getBytes());
        connection.zSetCommands().zAdd(key, 0, "c".getBytes());
        ByteBuffer keyBuf = ByteBuffer.wrap(key);

        assertThat(zSetCommands.zRangeByLex(keyBuf, Range.unbounded()).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("a", "b", "c");
        assertThat(zSetCommands.zRangeByLex(keyBuf, Range.rightUnbounded(Bound.exclusive("a"))).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("b", "c");
        assertThat(zSetCommands.zRangeByLex(keyBuf, Range.unbounded(), Limit.limit().count(2)).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("a", "b");
        assertThat(zSetCommands.zRevRangeByLex(keyBuf, Range.unbounded()).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("c", "b", "a");
        assertThat(zSetCommands.zRevRangeByLex(keyBuf, Range.leftUnbounded(Bound.inclusive("b"))).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("b", "a");
        assertThat(zSetCommands.zRemRangeByLex(keyBuf, Range.rightUnbounded(Bound.inclusive("b"))).block()).isEqualTo(2);
        assertThat(connection.zSetCommands().zCard(key)).isEqualTo(1);
    }

    @Test
    public void testZLexCount() {
        byte[] key = "lex-count".getBytes();
        connection.zSetCommands().zAdd(key, 0, "a".getBytes());
        connection.zSetCommands().zAdd(key, 0, "b".getBytes());
        connection.zSetCommands().zAdd(key, 0, "c".getBytes());
        ByteBuffer keyBuf = ByteBuffer.wrap(key);

        assertThat(zSetCommands.zLexCount(keyBuf, Range.closed("a", "b")).block()).isEqualTo(2);
        assertThat(zSetCommands.zLexCount(keyBuf, Range.rightOpen("a", "b")).block()).isEqualTo(1);
        assertThat(zSetCommands.zLexCount(keyBuf, Range.unbounded()).block()).isEqualTo(3);
    }

    @Test
    public void testZScan() {
        byte[] key = "zscan".getBytes();
        connection.zSetCommands().zAdd(key, 1, "a".getBytes());
        connection.zSetCommands().zAdd(key, 2, "b".getBytes());
        connection.zSetCommands().zAdd(key, 3, "c".getBytes());

        List<Tuple> all = zSetCommands.zScan(ByteBuffer.wrap(key)).collectList().block();
        assertThat(all).extracting(t -> new String(t.getValue()), Tuple::getScore)
                .containsExactlyInAnyOrder(tuple("a", 1.0), tuple("b", 2.0), tuple("c", 3.0));

        List<Tuple> matched = zSetCommands.zScan(ByteBuffer.wrap(key), ScanOptions.scanOptions().match("b*").build()).collectList().block();
        assertThat(matched).extracting(t -> new String(t.getValue()), Tuple::getScore)
                .containsExactly(tuple("b", 2.0));
    }

    @Test
    public void testZScanWithBinaryPattern() {
        byte[] key = "zscan-binary".getBytes();
        byte[] binaryMember = {(byte) 0xFF, 'm'};
        connection.zSetCommands().zAdd(key, 1, binaryMember);
        connection.zSetCommands().zAdd(key, 2, "other".getBytes());

        assertThat(zSetCommands.zScan(ByteBuffer.wrap(key), ScanOptions.scanOptions().match(new byte[] {(byte) 0xFF, '*'}).build())
                .map(Tuple::getValue).collectList().block()).containsExactly(binaryMember);
    }

    @Test
    public void testZDiffWithScores() {
        byte[] key = "zdiff-scores".getBytes();
        byte[] other = "zdiff-scores-other".getBytes();
        connection.zSetCommands().zAdd(key, 1, "a".getBytes());
        connection.zSetCommands().zAdd(key, 2, "b".getBytes());
        connection.zSetCommands().zAdd(other, 2, "b".getBytes());
        List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap(key), ByteBuffer.wrap(other));

        assertThat(zSetCommands.zDiffWithScores(keys).collectList().block())
                .extracting(t -> new String(t.getValue()), Tuple::getScore)
                .containsExactly(tuple("a", 1.0));

        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            ReactiveZSetCommands resp3 = new RedissonConnectionFactory(client).getReactiveConnection().zSetCommands();
            assertThat(resp3.zDiffWithScores(keys).collectList().block())
                    .extracting(t -> new String(t.getValue()), Tuple::getScore)
                    .containsExactly(tuple("a", 1.0));
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testZRangeStore() {
        byte[] key = "zrangestore".getBytes();
        byte[] dst = "zrangestore-dst".getBytes();
        connection.zSetCommands().zAdd(key, 1, "a".getBytes());
        connection.zSetCommands().zAdd(key, 2, "b".getBytes());
        connection.zSetCommands().zAdd(key, 3, "c".getBytes());
        ByteBuffer src = ByteBuffer.wrap(key);
        ByteBuffer dest = ByteBuffer.wrap(dst);

        assertThat(zSetCommands.zRangeStoreByLex(src, dest, Range.closed("a", "b"), Limit.unlimited()).block()).isEqualTo(2);
        assertThat(zSetCommands.zRangeStoreByLex(src, dest, Range.unbounded(), Limit.limit().count(2)).block()).isEqualTo(2);
        assertThat(connection.zSetCommands().zRange(dst, 0, -1)).extracting(String::new).containsExactly("a", "b");

        assertThat(zSetCommands.zRangeStoreByScore(src, dest, Range.leftOpen(1.0, 2.0), Limit.unlimited()).block()).isEqualTo(1);
        assertThat(connection.zSetCommands().zRange(dst, 0, -1)).extracting(String::new).containsExactly("b");
        assertThat(zSetCommands.zRangeStoreByScore(src, dest, Range.unbounded(), Limit.unlimited()).block()).isEqualTo(3);
        assertThat(zSetCommands.zRangeStoreByScore(src, dest,
                Range.closed(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY), Limit.unlimited()).block()).isEqualTo(3);

        assertThat(zSetCommands.zRangeStoreRevByScore(src, dest, Range.closed(1.0, 2.0), Limit.unlimited()).block()).isEqualTo(2);
        assertThat(zSetCommands.zRangeStoreRevByScore(src, dest, Range.unbounded(), Limit.limit().count(1)).block()).isEqualTo(1);
        assertThat(connection.zSetCommands().zRange(dst, 0, -1)).extracting(String::new).containsExactly("c");
        assertThat(zSetCommands.zRangeStoreRevByLex(src, dest, Range.closed("a", "b"), Limit.unlimited()).block()).isEqualTo(2);
        assertThat(connection.zSetCommands().zRange(dst, 0, -1)).extracting(String::new).containsExactly("a", "b");
    }

    @Test
    public void testZAddGtAndLt() {
        byte[] key = "zadd-gt-lt".getBytes();
        connection.zSetCommands().zAdd(key, 2, "a".getBytes());
        ByteBuffer keyBuf = ByteBuffer.wrap(key);

        zSetCommands.zAdd(Mono.just(ZAddCommand.tuple(new DefaultTuple("a".getBytes(), 1.0)).to(keyBuf).gt())).blockLast();
        assertThat(connection.zSetCommands().zScore(key, "a".getBytes())).isEqualTo(2.0);
        zSetCommands.zAdd(Mono.just(ZAddCommand.tuple(new DefaultTuple("a".getBytes(), 3.0)).to(keyBuf).gt())).blockLast();
        assertThat(connection.zSetCommands().zScore(key, "a".getBytes())).isEqualTo(3.0);

        zSetCommands.zAdd(Mono.just(ZAddCommand.tuple(new DefaultTuple("a".getBytes(), 4.0)).to(keyBuf).lt())).blockLast();
        assertThat(connection.zSetCommands().zScore(key, "a".getBytes())).isEqualTo(3.0);
        zSetCommands.zAdd(Mono.just(ZAddCommand.tuple(new DefaultTuple("a".getBytes(), 0.5)).to(keyBuf).lt())).blockLast();
        assertThat(connection.zSetCommands().zScore(key, "a".getBytes())).isEqualTo(0.5);
    }

    @Test
    public void testZRandMemberWithScoreKeepsRepeats() {
        byte[] key = "zrandmember-repeats".getBytes();
        connection.zSetCommands().zAdd(key, 1, "a".getBytes());
        connection.zSetCommands().zAdd(key, 2, "b".getBytes());

        assertThat(zSetCommands.zRandMemberWithScore(ByteBuffer.wrap(key), -6).collectList().block())
                .hasSize(6)
                .extracting(t -> new String(t.getValue()), Tuple::getScore)
                .isSubsetOf(tuple("a", 1.0), tuple("b", 2.0));

        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            ReactiveZSetCommands resp3 = new RedissonConnectionFactory(client).getReactiveConnection().zSetCommands();
            assertThat(resp3.zRandMemberWithScore(ByteBuffer.wrap(key), -6).collectList().block())
                    .hasSize(6)
                    .extracting(t -> new String(t.getValue()), Tuple::getScore)
                    .isSubsetOf(tuple("a", 1.0), tuple("b", 2.0));
        } finally {
            client.shutdown();
        }
    }

    private static String string(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return new String(bytes);
    }

    @Test
    public void testBZPopWaitsOnlyOnTheGivenKey() {
        connection.zSetCommands().zAdd("0".getBytes(), 1, "from-key-zero".getBytes());
        ByteBuffer key = ByteBuffer.wrap("empty-zset".getBytes());

        assertThat(zSetCommands.bZPopMin(key, Duration.ofSeconds(1)).block(Duration.ofSeconds(10))).isNull();
        assertThat(zSetCommands.bZPopMax(key, Duration.ofSeconds(1)).block(Duration.ofSeconds(10))).isNull();
        assertThat(connection.zSetCommands().zCard("0".getBytes())).isEqualTo(1);
    }

}
