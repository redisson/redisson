package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.domain.Range.Bound;
import org.springframework.data.redis.connection.ReactiveZSetCommands;
import org.springframework.data.redis.connection.RedisZSetCommands.Limit;
import org.springframework.data.redis.connection.RedisZSetCommands.Tuple;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(zSetCommands.zRangeWithScores(keyBuf, Range.of(Bound.inclusive(1L), Bound.unbounded())).map(Tuple::getScore).collectList().block())
                .containsExactly(2.0, 3.0);
        assertThat(zSetCommands.zRevRangeWithScores(keyBuf, Range.unbounded()).map(Tuple::getScore).collectList().block())
                .containsExactly(3.0, 2.0, 1.0);
        assertThat(zSetCommands.zRemRangeByRank(keyBuf, Range.of(Bound.inclusive(1L), Bound.unbounded())).block()).isEqualTo(2);
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
        assertThat(zSetCommands.zRangeByLex(keyBuf, Range.of(Bound.exclusive("a"), Bound.unbounded())).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("b", "c");
        assertThat(zSetCommands.zRangeByLex(keyBuf, Range.unbounded(), Limit.limit().count(2)).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("a", "b");
        assertThat(zSetCommands.zRevRangeByLex(keyBuf, Range.unbounded()).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("c", "b", "a");
        assertThat(zSetCommands.zRevRangeByLex(keyBuf, Range.of(Bound.unbounded(), Bound.inclusive("b"))).map(RedissonReactiveZSetCommandsTest::string).collectList().block())
                .containsExactly("b", "a");
    }

    private static String string(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return new String(bytes);
    }

}
