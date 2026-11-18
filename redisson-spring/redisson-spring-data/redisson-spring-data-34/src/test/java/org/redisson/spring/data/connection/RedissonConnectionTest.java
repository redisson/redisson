package org.redisson.spring.data.connection;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RGeo;
import org.redisson.api.RedissonClient;
import org.redisson.api.geo.GeoOrder;
import org.redisson.api.geo.GeoSearchArgs;
import org.redisson.api.geo.GeoUnit;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Range;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldIncrBy;
import org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldType;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.DefaultSortParameters;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisListCommands.Direction;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class RedissonConnectionTest extends BaseConnectionTest {

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
    public void testMember() {
        Map<Object, Boolean> r = redisTemplate.opsForSet().isMember("test", "val1", "val2");
        Map<Object, Boolean> ex = Map.of("val1", false, "val2", false);
        assertThat(r).isEqualTo(ex);

        redisTemplate.opsForSet().add("test", "val3");

        Map<Object, Boolean> r1 = redisTemplate.opsForSet().isMember("test", "val3", "val1");
        Map<Object, Boolean> ex2 = Map.of("val3", true, "val1", false);
        assertThat(r1).isEqualTo(ex2);
    }

    @Test
    public void testZRandMemberScore() {
        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);

        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection cc = factory.getReactiveConnection();

        Tuple b = cc.zSetCommands().zRandMemberWithScore(ByteBuffer.wrap("test".getBytes())).block();
        assertThat(b.getScore()).isNotNaN();
        assertThat(new String(b.getValue())).isIn("1", "2", "3");
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
    public void testBZPop() {
        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);

        ZSetOperations.TypedTuple<String> r = redisTemplate.boundZSetOps("test").popMin(Duration.ofSeconds(1));
        assertThat(r.getValue()).isEqualTo("1");
        assertThat(r.getScore()).isEqualTo(10);

        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection cc = factory.getReactiveConnection();

        Tuple r2 = cc.zSetCommands().bZPopMin(ByteBuffer.wrap("test".getBytes()), Duration.ofSeconds(1)).block();
        assertThat(r2.getValue()).isEqualTo("2".getBytes());
        assertThat(r2.getScore()).isEqualTo(20);
    }

    @Test
    public void testBZPopWithTimeoutUnderOneSecond() {
        byte[] key = "empty-zset".getBytes();

        long start = System.nanoTime();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertThat(connection.zSetCommands().bZPopMin(key, 500, TimeUnit.MILLISECONDS)).isNull();
            assertThat(connection.zSetCommands().bZPopMax(key, 500, TimeUnit.MILLISECONDS)).isNull();
        });
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(1000));
    }

    @Test
    public void testBZPopRejectsNegativeTimeout() {
        byte[] key = "empty-zset".getBytes();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertThatThrownBy(() -> connection.zSetCommands().bZPopMin(key, -500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> connection.zSetCommands().bZPopMax(key, -500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    public void testZPop() {
        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);

        ZSetOperations.TypedTuple<String> r = redisTemplate.boundZSetOps("test").popMin();
        assertThat(r.getValue()).isEqualTo("1");
        assertThat(r.getScore()).isEqualTo(10);

        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection cc = factory.getReactiveConnection();

        Tuple r2 = cc.zSetCommands().zPopMin(ByteBuffer.wrap("test".getBytes())).block();
        assertThat(r2.getValue()).isEqualTo("2".getBytes());
        assertThat(r2.getScore()).isEqualTo(20);
    }

    @Test
    public void testBZPopReturnsNullWhenTimeoutExpires() {
        byte[] key = "empty-zset".getBytes();

        assertThat(connection.zSetCommands().bZPopMin(key, 1, TimeUnit.SECONDS)).isNull();
        assertThat(connection.zSetCommands().bZPopMax(key, 1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    public void testBLMove() {
        byte[] source = "blmove-source".getBytes();
        byte[] destination = "blmove-destination".getBytes();
        connection.listCommands().rPush(source, "a".getBytes(), "b".getBytes());

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertThat(connection.listCommands().bLMove(source, destination, Direction.LEFT, Direction.RIGHT, 1))
                    .isEqualTo("a".getBytes());
        });
        assertThat(connection.listCommands().lRange(destination, 0, -1)).containsExactly("a".getBytes());
        assertThat(connection.listCommands().lRange(source, 0, -1)).containsExactly("b".getBytes());
    }

    @Test
    public void testBLMoveWithTimeoutUnderOneSecond() {
        byte[] source = "blmove-empty".getBytes();
        byte[] destination = "blmove-destination".getBytes();

        long start = System.nanoTime();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertThat(connection.listCommands().bLMove(source, destination, Direction.LEFT, Direction.RIGHT, 0.5))
                    .isNull();
        });
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(500));
    }

    @Test
    public void testBLMoveWithTimeoutUnderOneMillisecond() {
        byte[] source = "blmove-empty".getBytes();
        byte[] destination = "blmove-destination".getBytes();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertThat(connection.listCommands().bLMove(source, destination, Direction.LEFT, Direction.RIGHT, 0.0004))
                    .isNull();
        });
    }

    @Test
    public void testBLMoveRejectsNegativeTimeout() {
        byte[] source = "blmove-empty".getBytes();
        byte[] destination = "blmove-destination".getBytes();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertThatThrownBy(() -> connection.listCommands().bLMove(source, destination, Direction.LEFT, Direction.RIGHT, -0.5))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    public void testZRangeWithScores() {
        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);

        Set<ZSetOperations.TypedTuple<String>> objs = redisTemplate.boundZSetOps("test").rangeWithScores(0, 100);
        assertThat(objs).hasSize(3);
        assertThat(objs).containsExactlyInAnyOrder(ZSetOperations.TypedTuple.of("1", 10D),
                ZSetOperations.TypedTuple.of("2", 20D),
                ZSetOperations.TypedTuple.of("3", 30D));
    }

    @Test
    public void testZDiff() {
        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);
        redisTemplate.boundZSetOps("test").add("4", 30);

        redisTemplate.boundZSetOps("test2").add("5", 50);
        redisTemplate.boundZSetOps("test2").add("2", 20);
        redisTemplate.boundZSetOps("test2").add("3", 30);
        redisTemplate.boundZSetOps("test2").add("6", 60);

        Set<String> objs = redisTemplate.boundZSetOps("test").difference("test2");
        assertThat(objs).hasSize(2);
    }

    @Test
    public void testZLexCount() {
        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);

        Long size = redisTemplate.boundZSetOps("test").lexCount(Range.closed("1", "2"));
        assertThat(size).isEqualTo(2);
    }

    @Test
    public void testZRemLexByRange() {
        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);

        Long size = redisTemplate.boundZSetOps("test")
                .removeRangeByLex(Range.closed("1", "2"));
        assertThat(size).isEqualTo(2);
    }

    @Test
    public void testReverseRangeByLex() {
        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);

        Set<String> ops = redisTemplate.boundZSetOps("test")
                .reverseRangeByLex(Range.closed("1", "2")
                        , Limit.limit().count(10));
        assertThat(ops.size()).isEqualTo(2);
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
        Logger promiseLogger = (Logger) LoggerFactory.getLogger("io.netty.util.concurrent.DefaultPromise");
        ListAppender<ILoggingEvent> warnings = new ListAppender<>();
        warnings.start();
        promiseLogger.addAppender(warnings);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                long start = System.nanoTime();
                assertThat((List<Object>) connection.execute("BLPOP", "list".getBytes(), "4".getBytes())).isEmpty();
                assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(4));
                assertThat((List<Object>) connection.execute("BZMPOP", "1".getBytes(), "1".getBytes(), "zset".getBytes(), "MIN".getBytes())).isEmpty();
            });
        } finally {
            promiseLogger.detachAppender(warnings);
        }
        assertThat(warnings.list).isEmpty();
    }

    @Test
    public void testRandomMembers() {
        RedisTemplate<String, Integer> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();


        SetOperations<String, Integer> ops = redisTemplate.opsForSet();
        ops.add("val", 1, 2, 3, 4);
        Set<Integer> values = redisTemplate.opsForSet().distinctRandomMembers("val", 1L);
        assertThat(values).containsAnyOf(1, 2, 3, 4);

        Integer v = redisTemplate.opsForSet().randomMember("val");
        assertThat(v).isNotNull();
    }

    @Test
    public void testRangeByLex() {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();

        RedisZSetCommands.Range range = new RedisZSetCommands.Range();
        range.lt("c");
        Set<String> zSetValue = redisTemplate.opsForZSet().rangeByLex("val", range);
        assertThat(zSetValue).isEmpty();
    }

    @Test
    public void testGeo() {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();

        String key = "test_geo_key";
        Point point = new Point(116.401001, 40.119499);
        redisTemplate.opsForGeo().add(key, point, "a");

        point = new Point(111.545998, 36.133499);
        redisTemplate.opsForGeo().add(key, point, "b");

        point = new Point(111.483002, 36.030998);
        redisTemplate.opsForGeo().add(key, point, "c");
        Circle within = new Circle(116.401001, 40.119499, 80000);
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates();
        GeoResults<RedisGeoCommands.GeoLocation<String>> res = redisTemplate.opsForGeo().radius(key, within, args);
        assertThat(res.getContent().get(0).getContent().getName()).isEqualTo("a");
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

        Cursor<Tuple> t = connection.zScan("key".getBytes(), ScanOptions.scanOptions().build());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value1".getBytes());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value2".getBytes());
    }

    @Test
    public void testRandFieldWithValues() {
        connection.hSet("map".getBytes(), "key1".getBytes(), "value1".getBytes());
        connection.hSet("map".getBytes(), "key2".getBytes(), "value2".getBytes());
        connection.hSet("map".getBytes(), "key3".getBytes(), "value3".getBytes());

        List<Map.Entry<byte[], byte[]>> s = connection.hRandFieldWithValues("map".getBytes(), 2);
        assertThat(s).hasSize(2);

        Map.Entry<byte[], byte[]> s2 = connection.hRandFieldWithValues("map".getBytes());
        assertThat(s2).isNotNull();

        byte[] f = connection.hRandField("map".getBytes());
        assertThat((Object) f).isIn("key1".getBytes(), "key2".getBytes(), "key3".getBytes());
    }

    @Test
    public void testIdletime() {
        connection.set("idle".getBytes(), "value".getBytes());
        assertThat(connection.keyCommands().idletime("idle".getBytes())).isNotNull();
        assertThat(connection.keyCommands().idletime("missing".getBytes())).isNull();
    }

    @Test
    public void testGetClientList() {
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
    public void testFilterOkResponsesInTransaction() {
        // Test with filterOkResponses = false (default behavior)
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        RedissonConnectionFactory connectionFactory = new RedissonConnectionFactory(redisson);
        // connectionFactory.setFilterOkResponses(false);
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        List<Object> results = (List<Object>) redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.multi();
            connection.set("test:key1".getBytes(), "value1".getBytes());
            connection.set("test:key2".getBytes(), "value2".getBytes());
            connection.get("test:key1".getBytes());
            connection.get("test:key2".getBytes());
            return connection.exec();
        }, RedisSerializer.string()).get(0);

        // With filterOkResponses=false, all responses including "OK" should be preserved
        assertThat(results).hasSize(4);
        assertThat(results.get(0)).isEqualTo(true);
        assertThat(results.get(1)).isEqualTo(true);
        assertThat(results.get(2)).isEqualTo("value1");
        assertThat(results.get(3)).isEqualTo("value2");

        // Test with filterOkResponses = true
        connectionFactory.setFilterOkResponses(true);
        redisTemplate.afterPropertiesSet();

        List<Object> filteredResults = (List<Object>) redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.multi();
            connection.set("test:key3".getBytes(), "value3".getBytes());
            connection.set("test:key4".getBytes(), "value4".getBytes());
            connection.get("test:key3".getBytes());
            connection.get("test:key4".getBytes());
            return connection.exec();
        }, RedisSerializer.string()).get(0);

        // With filterOkResponses=true, "OK" responses should be filtered out
        assertThat(filteredResults).hasSize(2);
        assertThat(filteredResults.get(0)).isEqualTo("value3");
        assertThat(filteredResults.get(1)).isEqualTo("value4");
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
    public void testTime() {
        assertThat(connection.serverCommands().time()).isCloseTo(System.currentTimeMillis(), within(60_000L));
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
        assertThat(connection.zSetCommands().zInterWithScores(Aggregate.SUM, weights, a, b))
                .extracting(Tuple::getScore).containsExactly(8.0);
        assertThat(connection.zSetCommands().zUnionWithScores(Aggregate.MIN, weights, a, b))
                .extracting(Tuple::getScore).containsExactly(1.0, 2.0, 8.0);
    }

    @Test
    public void testScanWithTypeAndBinaryPattern() {
        byte[] binaryKey = {'b', (byte) 0xFF, 'k'};
        connection.stringCommands().set(binaryKey, "value".getBytes());
        connection.stringCommands().set("scan-string".getBytes(), "value".getBytes());
        connection.zSetCommands().zAdd("scan-zset".getBytes(), 1, "a".getBytes());

        List<byte[]> zsets = new ArrayList<>();
        try (Cursor<byte[]> cursor = connection.keyCommands().scan(KeyScanOptions.scanOptions(DataType.ZSET).build())) {
            cursor.forEachRemaining(zsets::add);
        }
        assertThat(zsets).containsExactly("scan-zset".getBytes());

        List<byte[]> keys = new ArrayList<>();
        try (Cursor<byte[]> cursor = connection.keyCommands().scan(ScanOptions.scanOptions().match(new byte[] {'b', (byte) 0xFF, '*'}).build())) {
            cursor.forEachRemaining(keys::add);
        }
        assertThat(keys).containsExactly(binaryKey);

        byte[] binaryPart = {(byte) 0xFF, 'x'};
        ScanOptions binaryPattern = ScanOptions.scanOptions().match(new byte[] {(byte) 0xFF, '*'}).build();
        connection.hashCommands().hSet("scan-hash".getBytes(), binaryPart, "value".getBytes());
        connection.hashCommands().hSet("scan-hash".getBytes(), "other".getBytes(), "value".getBytes());
        connection.setCommands().sAdd("scan-set".getBytes(), binaryPart, "other".getBytes());
        connection.zSetCommands().zAdd("scan-zset".getBytes(), 2, binaryPart);

        List<byte[]> fields = new ArrayList<>();
        try (Cursor<Map.Entry<byte[], byte[]>> cursor = connection.hashCommands().hScan("scan-hash".getBytes(), binaryPattern)) {
            cursor.forEachRemaining(e -> fields.add(e.getKey()));
        }
        assertThat(fields).containsExactly(binaryPart);
        List<byte[]> members = new ArrayList<>();
        try (Cursor<byte[]> cursor = connection.setCommands().sScan("scan-set".getBytes(), binaryPattern)) {
            cursor.forEachRemaining(members::add);
        }
        assertThat(members).containsExactly(binaryPart);
        List<byte[]> scored = new ArrayList<>();
        try (Cursor<Tuple> cursor = connection.zSetCommands().zScan("scan-zset".getBytes(), binaryPattern)) {
            cursor.forEachRemaining(t -> scored.add(t.getValue()));
        }
        assertThat(scored).containsExactly(binaryPart);
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

        List<GeoResult<RedisGeoCommands.GeoLocation<byte[]>>> search = connection.geoCommands().geoSearch(key,
                GeoReference.fromCoordinate(new Point(15, 37)), GeoShape.byRadius(new Distance(200, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().includeDistance().sortAscending())
                .getContent();
        assertThat(search).extracting(r -> new String(r.getContent().getName())).containsExactly("Catania", "Palermo");
        assertThat(search.get(1).getDistance().getValue()).isCloseTo(190.4424, within(0.01));
        assertThat(search.get(1).getContent().getPoint().getX()).isCloseTo(13.361389, within(0.0001));
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
    public void testGeoRadiusWithAny() {
        byte[] key = "geo-any".getBytes();
        connection.geoCommands().geoAdd(key, new Point(13.361389, 38.115556), "Palermo".getBytes());
        connection.geoCommands().geoAdd(key, new Point(15.087269, 37.502669), "Catania".getBytes());
        RGeo<String> geo = redisson.getGeo("geo-any", StringCodec.INSTANCE);
        List<String> anyByPoint = geo.search(GeoSearchArgs.from(15, 37).radius(300, GeoUnit.KILOMETERS).countAny(1));
        List<String> anyByMember = geo.search(GeoSearchArgs.from("Catania").radius(300, GeoUnit.KILOMETERS).countAny(1));
        assertThat(anyByPoint).isNotEqualTo(geo.search(GeoSearchArgs.from(15, 37).radius(300, GeoUnit.KILOMETERS).order(GeoOrder.ASC).count(1)));
        assertThat(anyByMember).isNotEqualTo(geo.search(GeoSearchArgs.from("Catania").radius(300, GeoUnit.KILOMETERS).order(GeoOrder.ASC).count(1)));

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance();
        args.limit(1, true);
        assertThat(connection.geoCommands().geoRadius(key, new Circle(new Point(15, 37), new Distance(300, Metrics.KILOMETERS)), args).getContent())
                .extracting(r -> new String(r.getContent().getName())).isEqualTo(anyByPoint);
        assertThat(connection.geoCommands().geoRadiusByMember(key, "Catania".getBytes(), new Distance(300, Metrics.KILOMETERS), args).getContent())
                .extracting(r -> new String(r.getContent().getName())).isEqualTo(anyByMember);
    }

    @Test
    public void testScoreRangesWithIntegerBounds() {
        byte[] key = "score-integer-bounds".getBytes();
        connection.zSetCommands().zAdd(key, 1, "a".getBytes());
        connection.zSetCommands().zAdd(key, 2, "b".getBytes());
        connection.zSetCommands().zAdd(key, 3, "c".getBytes());

        assertThat(connection.zSetCommands().zCount(key, Range.closed(1, 2))).isEqualTo(2);
        assertThat(connection.zSetCommands().zRangeByScore(key, Range.closed(1L, 2L)))
                .extracting(String::new).containsExactly("a", "b");
        assertThat(connection.zSetCommands().zRangeByScoreWithScores(key, Range.leftOpen(1, 3)))
                .extracting(Tuple::getScore).containsExactly(2.0, 3.0);
        assertThat(connection.zSetCommands().zRevRangeByScore(key, Range.closed(2, 3)))
                .extracting(String::new).containsExactly("c", "b");
        assertThat(connection.zSetCommands().zRangeStoreByScore("score-integer-bounds-dst".getBytes(), key,
                Range.closed(1, 2), Limit.unlimited())).isEqualTo(2);
        assertThat(connection.zSetCommands().zRemRangeByScore(key, Range.closed(1, 2))).isEqualTo(2);
    }

    @Test
    public void testZRangeStoreRev() {
        byte[] key = "zrangestore-rev".getBytes();
        byte[] dst = "zrangestore-rev-dst".getBytes();
        connection.zSetCommands().zAdd(key, 1, "a".getBytes());
        connection.zSetCommands().zAdd(key, 2, "b".getBytes());
        connection.zSetCommands().zAdd(key, 3, "c".getBytes());

        assertThat(connection.zSetCommands().zRangeStoreRevByScore(dst, key, Range.closed(1.0, 2.0), Limit.unlimited())).isEqualTo(2);
        assertThat(connection.zSetCommands().zRange(dst, 0, -1)).extracting(String::new).containsExactly("a", "b");

        assertThat(connection.zSetCommands().zRangeStoreRevByScore(dst, key, Range.unbounded(), Limit.limit().count(2))).isEqualTo(2);
        assertThat(connection.zSetCommands().zRange(dst, 0, -1)).extracting(String::new).containsExactly("b", "c");

        assertThat(connection.zSetCommands().zRangeStoreRevByLex(dst, key,
                Range.closed("a".getBytes(), "b".getBytes()), Limit.unlimited())).isEqualTo(2);
        assertThat(connection.zSetCommands().zRange(dst, 0, -1)).extracting(String::new).containsExactly("a", "b");

        assertThat(connection.zSetCommands().zRangeStoreRevByLex(dst, key, Range.unbounded(), Limit.limit().count(1))).isEqualTo(1);
        assertThat(connection.zSetCommands().zRange(dst, 0, -1)).extracting(String::new).containsExactly("c");
    }

    @Test
    public void testLexRangesWithBinaryBounds() {
        byte[] key = "lex-binary".getBytes();
        byte[] first = {(byte) 0xFF, 'a'};
        byte[] second = {(byte) 0xFF, 'b'};
        connection.zSetCommands().zAdd(key, 0, first);
        connection.zSetCommands().zAdd(key, 0, second);
        connection.zSetCommands().zAdd(key, 0, "c".getBytes());
        Range<byte[]> range = Range.closed(first, second);

        assertThat(connection.zSetCommands().zRangeByLex(key, range)).containsExactly(first, second);
        assertThat(connection.zSetCommands().zRangeByLex(key, Range.rightOpen(first, second))).containsExactly(first);
        assertThat(connection.zSetCommands().zRevRangeByLex(key, range)).containsExactly(second, first);
        assertThat(connection.zSetCommands().zLexCount(key, range)).isEqualTo(2);
        assertThat(connection.zSetCommands().zRangeStoreByLex("lex-binary-dst".getBytes(), key, range, Limit.unlimited())).isEqualTo(2);
        assertThat(connection.zSetCommands().zRangeStoreRevByLex("lex-binary-rev".getBytes(), key, range, Limit.unlimited())).isEqualTo(2);
        assertThat(connection.zSetCommands().zRemRangeByLex(key, range)).isEqualTo(2);
        assertThat(connection.zSetCommands().zRange(key, 0, -1)).containsExactly("c".getBytes());
    }

    @Test
    public void testZRandMemberWithScore() {
        byte[] key = "zrandmember-score".getBytes();
        assertThat(connection.zSetCommands().zRandMemberWithScore(key)).isNull();

        connection.zSetCommands().zAdd(key, 1.5, "a".getBytes());
        Tuple tuple = connection.zSetCommands().zRandMemberWithScore(key);
        assertThat(tuple.getValue()).isEqualTo("a".getBytes());
        assertThat(tuple.getScore()).isEqualTo(1.5);
    }

    @Test
    public void testZRandMemberWithScoreOverResp3() {
        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            RedisConnection resp3 = new RedissonConnectionFactory(client).getConnection();
            byte[] key = "zrandmember-score-resp3".getBytes();
            assertThat(resp3.zSetCommands().zRandMemberWithScore(key)).isNull();

            resp3.zSetCommands().zAdd(key, 1.5, "a".getBytes());
            Tuple tuple = resp3.zSetCommands().zRandMemberWithScore(key);
            assertThat(tuple.getValue()).isEqualTo("a".getBytes());
            assertThat(tuple.getScore()).isEqualTo(1.5);
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testScoredRepliesOverResp3() {
        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            RedisConnection resp3 = new RedissonConnectionFactory(client).getConnection();
            byte[] key = "scored-resp3".getBytes();
            byte[] other = "scored-resp3-other".getBytes();
            resp3.zSetCommands().zAdd(key, 1, "a".getBytes());
            resp3.zSetCommands().zAdd(key, 2, "b".getBytes());
            resp3.zSetCommands().zAdd(other, 2, "b".getBytes());

            assertThat(resp3.zSetCommands().zUnionWithScores(key, other))
                    .extracting(t -> new String(t.getValue()), Tuple::getScore)
                    .containsExactly(tuple("a", 1.0), tuple("b", 4.0));
            assertThat(resp3.zSetCommands().zInterWithScores(key, other))
                    .extracting(t -> new String(t.getValue()), Tuple::getScore)
                    .containsExactly(tuple("b", 4.0));
            assertThat(resp3.zSetCommands().zDiffWithScores(key, other))
                    .extracting(t -> new String(t.getValue()), Tuple::getScore)
                    .containsExactly(tuple("a", 1.0));
            assertThat(resp3.zSetCommands().zRandMemberWithScore(key, 2))
                    .extracting(t -> new String(t.getValue()), Tuple::getScore)
                    .containsExactlyInAnyOrder(tuple("a", 1.0), tuple("b", 2.0));
            assertThat(resp3.zSetCommands().zPopMin(key, 1))
                    .extracting(t -> new String(t.getValue()), Tuple::getScore)
                    .containsExactly(tuple("a", 1.0));
            assertThat(resp3.zSetCommands().zPopMax(other, 1))
                    .extracting(t -> new String(t.getValue()), Tuple::getScore)
                    .containsExactly(tuple("b", 2.0));
        } finally {
            client.shutdown();
        }
    }

}
