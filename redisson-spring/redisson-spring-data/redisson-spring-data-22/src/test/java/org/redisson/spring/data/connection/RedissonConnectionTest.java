package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldIncrBy;
import org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldType;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.DefaultSortParameters;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.RedisZSetCommands.Aggregate;
import org.springframework.data.redis.connection.RedisZSetCommands.Tuple;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class RedissonConnectionTest extends BaseConnectionTest {

    @Test
    public void testSetExpiration2() {
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(10), SetOption.SET_IF_ABSENT)).isTrue();
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(10), SetOption.SET_IF_ABSENT)).isFalse();
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(10), SetOption.SET_IF_ABSENT)).isFalse();
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }

    @Test
    public void testSetRejectsNullExpirationAndOption() {
        byte[] key = "set-null-arguments".getBytes();

        assertThatThrownBy(() -> connection.stringCommands().set(key, "value".getBytes(), null, SetOption.upsert()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expiration");
        assertThatThrownBy(() -> connection.stringCommands().set(key, "value".getBytes(), Expiration.persistent(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option");
        assertThat(connection.keyCommands().exists(key)).isFalse();
    }

    @Test
    public void testBitFieldIncrByWithOverflow() {
        BitFieldSubCommands incr = BitFieldSubCommands.create()
                .incr(BitFieldType.unsigned(8)).valueAt(0).overflow(BitFieldIncrBy.Overflow.SAT).by(300);
        ReactiveRedisConnection reactive = new RedissonConnectionFactory(redisson).getReactiveConnection();

        assertThat(connection.stringCommands().bitField("bitfield-overflow".getBytes(), incr)).containsExactly(255L);
        assertThat(reactive.stringCommands().bitField(ByteBuffer.wrap("bitfield-overflow-reactive".getBytes()), incr).block())
                .containsExactly(255L);
    }

    @Test
    public void testSortWithStore() {
        byte[] key = "sort-source".getBytes();
        byte[] storeKey = "sort-copy".getBytes();
        connection.listCommands().rPush(key, "2".getBytes(), "1".getBytes(), "3".getBytes());

        assertThat(connection.keyCommands().sort(key, new DefaultSortParameters(), storeKey)).isEqualTo(3);
        assertThat(connection.listCommands().lRange(storeKey, 0, -1))
                .containsExactly("1".getBytes(), "2".getBytes(), "3".getBytes());
    }

    @Test
    public void testExecute() {
        Long s = (Long) connection.execute("ttl", "key".getBytes());
        assertThat(s).isEqualTo(-2);
        connection.execute("flushDb");

        assertThat(connection.execute("PING")).isEqualTo("PONG".getBytes());
        assertThat(connection.execute("set", "key".getBytes(), "value".getBytes())).isEqualTo("OK".getBytes());
        assertThat(connection.execute("TYPE", "key".getBytes())).isEqualTo("string".getBytes());
        assertThat(connection.execute("INCRBY", "counter".getBytes(), "5".getBytes())).isEqualTo(5L);
        assertThat(connection.execute("OBJECT", "ENCODING".getBytes(), "counter".getBytes())).isEqualTo("int".getBytes());
        assertThat(connection.execute("RPUSH", "list".getBytes(), "a".getBytes(), "b".getBytes())).isEqualTo(2L);
        assertThat((List<Object>) connection.execute("LRANGE", "list".getBytes(), "0".getBytes(), "-1".getBytes()))
                .containsExactly("a".getBytes(), "b".getBytes());
        assertThat(connection.execute("DEL", "key".getBytes(), "counter".getBytes())).isEqualTo(2L);
        assertThat(connection.execute("GET", "key".getBytes())).isNull();
        assertThatThrownBy(() -> connection.execute("NOSUCHCOMMAND")).isInstanceOf(DataAccessException.class);
    }

    @Test
    public void testExecuteBinaryArguments() {
        byte[] key = {(byte) 0xFF, (byte) 0xFE, 'k'};
        byte[] value = {(byte) 0xFF, 0, (byte) 0x80};

        assertThat(connection.execute("SET", key, value)).isEqualTo("OK".getBytes());
        assertThat(connection.get(key)).isEqualTo(value);
        assertThat(connection.execute("GET", key)).isEqualTo(value);
    }

    @Test
    public void testExecuteRejectsConnectionStateCommands() {
        assertThatThrownBy(() -> connection.execute("SELECT", "1".getBytes()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> connection.execute("client", "reply".getBytes(), "off".getBytes()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThat(connection.execute("CLIENT", "ID".getBytes())).isInstanceOf(Long.class);
        assertThat(connection.execute("PING")).isEqualTo("PONG".getBytes());
    }

    @Test
    public void testClusterConnectionsOutsideClusterMode() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        assertThatThrownBy(factory::getClusterConnection).isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(factory::getReactiveClusterConnection).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    public void testExecuteBlockingCommand() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            long start = System.nanoTime();
            assertThat((List<Object>) connection.execute("BLPOP", "list".getBytes(), "4".getBytes())).isEmpty();
            assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(4));
            assertThat((List<Object>) connection.execute("BZMPOP", "1".getBytes(), "1".getBytes(), "zset".getBytes(), "MIN".getBytes())).isEmpty();
        });
    }

    @Test
    public void testZSet() {
        connection.zAdd(new byte[] {1}, -1, new byte[] {1});
        connection.zAdd(new byte[] {1}, 2, new byte[] {2});
        connection.zAdd(new byte[] {1}, 10, new byte[] {3});

        assertThat(connection.zRangeByScore(new byte[] {1}, Double.NEGATIVE_INFINITY, 5))
                .containsOnly(new byte[] {1}, new byte[] {2});
    }

    @Test
    public void testEcho() {
        assertThat(connection.echo("test".getBytes())).isEqualTo("test".getBytes());
    }

    @Test
    public void testSetGet() {
        connection.set("key".getBytes(), "value".getBytes());
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testSetExpiration() {
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(111122), SetOption.SET_IF_ABSENT)).isTrue();
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testHSetGet() {
        assertThat(connection.hSet("key".getBytes(), "field".getBytes(), "value".getBytes())).isTrue();
        assertThat(connection.hGet("key".getBytes(), "field".getBytes())).isEqualTo("value".getBytes());
    }

    @Test
    public void testZScan() {
        connection.zAdd("key".getBytes(), 1, "value1".getBytes());
        connection.zAdd("key".getBytes(), 2, "value2".getBytes());

        Cursor<RedisZSetCommands.Tuple> t = connection.zScan("key".getBytes(), ScanOptions.scanOptions().build());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value1".getBytes());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value2".getBytes());
    }

    @Test
    public void testIdletime() {
        connection.set("idle".getBytes(), "value".getBytes());
        assertThat(connection.keyCommands().idletime("idle".getBytes())).isNotNull();
        assertThat(connection.keyCommands().idletime("missing".getBytes())).isNull();
    }

        @Test
    public void testGetClientList() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();

        List<RedisClientInfo> info = connection.getClientList();
        assertThat(info.size()).isGreaterThan(10);
        assertThat(info.get(0).getAddressPort()).isNotEmpty();
        assertThat(redisTemplate.getClientList().get(0).getDatabaseId()).isZero();

        connection.openPipeline();
        connection.getClientList();
        List<Object> results = connection.closePipeline();
        assertThat(((List<RedisClientInfo>) results.get(0)).get(0).getAddressPort()).isNotEmpty();
    }

    @Test
    public void testPTtl() {
        connection.set("key1".getBytes(), "value1".getBytes());
        connection.set("key2".getBytes(), "value2".getBytes());
        connection.expire("key1".getBytes(), 10);

        assertThat(connection.pTtl("key1".getBytes(), TimeUnit.MILLISECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(10000L);
        assertThat(connection.pTtl("key1".getBytes(), TimeUnit.SECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(10L);
        assertThat(connection.pTtl("key1".getBytes(), TimeUnit.MINUTES)).isEqualTo(0L);
        assertThat(connection.pTtl("key1".getBytes())).isGreaterThan(0L).isLessThanOrEqualTo(10000L);

        assertThat(connection.pTtl("key2".getBytes(), TimeUnit.SECONDS)).isEqualTo(-1L);
        assertThat(connection.pTtl("key2".getBytes(), TimeUnit.MINUTES)).isEqualTo(-1L);
        assertThat(connection.pTtl("key2".getBytes())).isEqualTo(-1L);

        assertThat(connection.pTtl("key3".getBytes(), TimeUnit.SECONDS)).isEqualTo(-2L);
        assertThat(connection.pTtl("key3".getBytes(), TimeUnit.MINUTES)).isEqualTo(-2L);
        assertThat(connection.pTtl("key3".getBytes())).isEqualTo(-2L);
    }

    @Test
    public void testTtl() {
        connection.set("key1".getBytes(), "value1".getBytes());
        connection.set("key2".getBytes(), "value2".getBytes());
        connection.expire("key1".getBytes(), 10);

        assertThat(connection.ttl("key1".getBytes(), TimeUnit.MILLISECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(10000L);
        assertThat(connection.ttl("key1".getBytes(), TimeUnit.SECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(10L);
        assertThat(connection.ttl("key1".getBytes(), TimeUnit.MINUTES)).isEqualTo(0L);
        assertThat(connection.ttl("key1".getBytes())).isGreaterThan(0L).isLessThanOrEqualTo(10000L);

        assertThat(connection.ttl("key2".getBytes(), TimeUnit.SECONDS)).isEqualTo(-1L);
        assertThat(connection.ttl("key2".getBytes(), TimeUnit.MINUTES)).isEqualTo(-1L);
        assertThat(connection.ttl("key2".getBytes())).isEqualTo(-1L);

        assertThat(connection.ttl("key3".getBytes(), TimeUnit.SECONDS)).isEqualTo(-2L);
        assertThat(connection.ttl("key3".getBytes(), TimeUnit.MINUTES)).isEqualTo(-2L);
        assertThat(connection.ttl("key3".getBytes())).isEqualTo(-2L);
    }

    @Test
    public void testZSetAggregatesWithIntWeights() {
        byte[] a = "zweights-a".getBytes();
        byte[] b = "zweights-b".getBytes();
        connection.zSetCommands().zAdd(a, 1, "x".getBytes());
        connection.zSetCommands().zAdd(a, 2, "y".getBytes());
        connection.zSetCommands().zAdd(b, 3, "y".getBytes());
        connection.zSetCommands().zAdd(b, 4, "z".getBytes());
        int[] weights = {1, 2};

        assertThat(connection.zSetCommands().zInterStore("zweights-inter".getBytes(), Aggregate.SUM, weights, a, b)).isEqualTo(1);
        assertThat(connection.zSetCommands().zScore("zweights-inter".getBytes(), "y".getBytes())).isEqualTo(8.0);
        assertThat(connection.zSetCommands().zUnionStore("zweights-union".getBytes(), Aggregate.MAX, weights, a, b)).isEqualTo(3);
        assertThat(connection.zSetCommands().zScore("zweights-union".getBytes(), "y".getBytes())).isEqualTo(6.0);
    }

    @Test
    public void testGeoWithCoordinatesAndDistance() {
        byte[] key = "geo-coordinates-distance".getBytes();
        connection.geoCommands().geoAdd(key, new Point(13.361389, 38.115556), "Palermo".getBytes());
        connection.geoCommands().geoAdd(key, new Point(15.087269, 37.502669), "Catania".getBytes());

        List<GeoResult<RedisGeoCommands.GeoLocation<byte[]>>> byPoint = connection.geoCommands().geoRadius(key,
                new Circle(new Point(15, 37), new Distance(200, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates().includeDistance().sortAscending())
                .getContent();
        assertThat(byPoint).extracting(r -> new String(r.getContent().getName())).containsExactly("Catania", "Palermo");
        assertThat(byPoint.get(0).getDistance().getValue()).isCloseTo(56.4413, within(0.01));
        assertThat(byPoint.get(0).getDistance().getMetric()).isEqualTo(Metrics.KILOMETERS);
        assertThat(byPoint.get(0).getContent().getPoint().getX()).isCloseTo(15.087269, within(0.0001));

        List<GeoResult<RedisGeoCommands.GeoLocation<byte[]>>> byMember = connection.geoCommands().geoRadiusByMember(key,
                "Palermo".getBytes(), new Distance(200, Metrics.KILOMETERS),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates().includeDistance().sortDescending())
                .getContent();
        assertThat(byMember).extracting(r -> new String(r.getContent().getName())).containsExactly("Catania", "Palermo");
        assertThat(byMember.get(0).getDistance().getValue()).isCloseTo(166.2742, within(0.01));
        assertThat(byMember.get(0).getContent().getPoint().getY()).isCloseTo(37.502669, within(0.0001));
    }

    @Test
    public void testGeoDistOfMissingMember() {
        byte[] key = "geo-dist-missing".getBytes();
        connection.geoCommands().geoAdd(key, new Point(13.361389, 38.115556), "Palermo".getBytes());

        assertThat(connection.geoCommands().geoDist(key, "Palermo".getBytes(), "Catania".getBytes())).isNull();
        assertThat(connection.geoCommands().geoDist("geo-dist-none".getBytes(), "Palermo".getBytes(), "Catania".getBytes(), Metrics.KILOMETERS)).isNull();

        RedissonConnection pipelined = new RedissonConnection(redisson);
        pipelined.openPipeline();
        pipelined.geoCommands().geoDist(key, "Palermo".getBytes(), "Catania".getBytes());
        pipelined.geoCommands().geoDist(key, "Palermo".getBytes(), "Palermo".getBytes(), Metrics.KILOMETERS);
        assertThat(pipelined.closePipeline()).containsExactly(null, new Distance(0, Metrics.KILOMETERS));
    }

    @Test
    public void testScoreRangesWithIntegerBounds() {
        byte[] key = "score-integer-bounds".getBytes();
        connection.zSetCommands().zAdd(key, 1, "a".getBytes());
        connection.zSetCommands().zAdd(key, 2, "b".getBytes());
        connection.zSetCommands().zAdd(key, 3, "c".getBytes());

        assertThat(connection.zSetCommands().zCount(key, RedisZSetCommands.Range.range().gte(1).lte(2))).isEqualTo(2);
        assertThat(connection.zSetCommands().zRangeByScore(key, RedisZSetCommands.Range.range().gte(1L).lte(2L)))
                .extracting(String::new).containsExactly("a", "b");
        assertThat(connection.zSetCommands().zRangeByScoreWithScores(key, RedisZSetCommands.Range.range().gt(1).lte(3)))
                .extracting(Tuple::getScore).containsExactly(2.0, 3.0);
        assertThat(connection.zSetCommands().zRevRangeByScore(key, RedisZSetCommands.Range.range().gte(2).lte(3)))
                .extracting(String::new).containsExactly("c", "b");
        assertThat(connection.zSetCommands().zRemRangeByScore(key, RedisZSetCommands.Range.range().gte(1).lte(2))).isEqualTo(2);
    }

    @Test
    public void testLexRangesWithBinaryBounds() {
        byte[] key = "lex-binary".getBytes();
        byte[] first = {(byte) 0xFF, 'a'};
        byte[] second = {(byte) 0xFF, 'b'};
        connection.zSetCommands().zAdd(key, 0, first);
        connection.zSetCommands().zAdd(key, 0, second);
        connection.zSetCommands().zAdd(key, 0, "c".getBytes());
        RedisZSetCommands.Range range = RedisZSetCommands.Range.range().gte(first).lte(second);

        assertThat(connection.zSetCommands().zRangeByLex(key, range)).containsExactly(first, second);
        assertThat(connection.zSetCommands().zRangeByLex(key, RedisZSetCommands.Range.range().gte(first).lt(second))).containsExactly(first);
    }

}
