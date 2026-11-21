package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.domain.Range.Bound;
import org.springframework.data.redis.connection.DefaultTuple;
import org.springframework.data.redis.connection.ReactiveZSetCommands.ZAddCommand;
import org.springframework.data.redis.connection.ReactiveZSetCommands;
import org.springframework.data.redis.connection.RedisZSetCommands.Limit;
import org.springframework.data.redis.connection.RedisZSetCommands.Tuple;
import org.springframework.data.redis.core.ScanOptions;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class RedissonReactiveZSetCommandsTest extends BaseConnectionTest {

    private final ReactiveZSetCommands zSetCommands =
            new RedissonConnectionFactory(redisson).getReactiveConnection().zSetCommands();

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
    public void testZAddGtAndLt() {
        byte[] key = "zadd-gt-lt".getBytes();
        connection.zSetCommands().zAdd(key, 2, "a".getBytes());
        ByteBuffer keyBuf = ByteBuffer.wrap(key);

        zSetCommands.zAdd(Mono.just(ZAddCommand.tuple(new DefaultTuple("a".getBytes(), 1.0)).to(keyBuf).gt().ch())).blockLast();
        assertThat(connection.zSetCommands().zScore(key, "a".getBytes())).isEqualTo(2.0);
        zSetCommands.zAdd(Mono.just(ZAddCommand.tuple(new DefaultTuple("a".getBytes(), 3.0)).to(keyBuf).gt().ch())).blockLast();
        assertThat(connection.zSetCommands().zScore(key, "a".getBytes())).isEqualTo(3.0);

        zSetCommands.zAdd(Mono.just(ZAddCommand.tuple(new DefaultTuple("a".getBytes(), 4.0)).to(keyBuf).lt().ch())).blockLast();
        assertThat(connection.zSetCommands().zScore(key, "a".getBytes())).isEqualTo(3.0);
        zSetCommands.zAdd(Mono.just(ZAddCommand.tuple(new DefaultTuple("a".getBytes(), 0.5)).to(keyBuf).lt().ch())).blockLast();
        assertThat(connection.zSetCommands().zScore(key, "a".getBytes())).isEqualTo(0.5);
    }

    private static String string(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return new String(bytes);
    }

}
