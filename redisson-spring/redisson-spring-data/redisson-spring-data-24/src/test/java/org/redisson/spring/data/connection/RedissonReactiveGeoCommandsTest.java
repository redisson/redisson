package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.ReactiveGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class RedissonReactiveGeoCommandsTest extends BaseConnectionTest {

    private final ReactiveGeoCommands geoCommands =
            new RedissonConnectionFactory(redisson).getReactiveConnection().geoCommands();

    private ByteBuffer addSicily(String key) {
        connection.geoCommands().geoAdd(key.getBytes(), new Point(13.361389, 38.115556), "Palermo".getBytes());
        connection.geoCommands().geoAdd(key.getBytes(), new Point(15.087269, 37.502669), "Catania".getBytes());
        return ByteBuffer.wrap(key.getBytes());
    }

    private static String name(GeoResult<GeoLocation<ByteBuffer>> result) {
        ByteBuffer buffer = result.getContent().getName();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return new String(bytes);
    }

    @Test
    public void testGeoDistOfMissingMember() {
        ByteBuffer key = addSicily("geo-dist-missing");

        assertThat(geoCommands.geoDist(key, ByteBuffer.wrap("Palermo".getBytes()), ByteBuffer.wrap("Rome".getBytes())).blockOptional()).isEmpty();
        assertThat(geoCommands.geoDist(key, ByteBuffer.wrap("Palermo".getBytes()), ByteBuffer.wrap("Catania".getBytes()), Metrics.KILOMETERS).block().getValue())
                .isCloseTo(166.2742, within(0.01));
    }

    @Test
    public void testDistancesWithoutUnit() {
        ByteBuffer key = addSicily("geo-neutral");
        ByteBuffer palermo = ByteBuffer.wrap("Palermo".getBytes());

        assertThat(geoCommands.geoRadius(key, new Circle(new Point(15, 37), new Distance(200_000))).collectList().block())
                .extracting(RedissonReactiveGeoCommandsTest::name).containsExactlyInAnyOrder("Palermo", "Catania");
        assertThat(geoCommands.geoRadiusByMember(key, palermo, new Distance(200_000)).collectList().block())
                .extracting(RedissonReactiveGeoCommandsTest::name).containsExactlyInAnyOrder("Palermo", "Catania");
        Distance distance = geoCommands.geoDist(key, palermo, ByteBuffer.wrap("Catania".getBytes()), Metrics.NEUTRAL).block();
        assertThat(distance.getValue()).isCloseTo(166274.15, within(0.1));
        assertThat(distance.getMetric()).isEqualTo(Metrics.NEUTRAL);

        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(new RedissonConnectionFactory(redisson));
        assertThat(template.opsForGeo().radius("geo-neutral", "Palermo", 200_000).collectList().block()).hasSize(2);
    }

    @Test
    public void testGeoRadiusWithCoordinates() {
        ByteBuffer key = addSicily("geo-coordinates");

        List<GeoResult<GeoLocation<ByteBuffer>>> results = geoCommands.geoRadius(key,
                new Circle(new Point(15, 37), new Distance(200, Metrics.KILOMETERS)),
                GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates().sortAscending()).collectList().block();

        assertThat(results).extracting(RedissonReactiveGeoCommandsTest::name).containsExactly("Catania", "Palermo");
        assertThat(results.get(0).getContent().getPoint().getX()).isCloseTo(15.087269, within(0.0001));
        assertThat(results.get(1).getContent().getPoint().getY()).isCloseTo(38.115556, within(0.0001));
    }

    @Test
    public void testGeoWithCoordinatesAndDistance() {
        ByteBuffer key = addSicily("geo-coordinates-distance");

        List<GeoResult<GeoLocation<ByteBuffer>>> byPoint = geoCommands.geoRadius(key,
                new Circle(new Point(15, 37), new Distance(200, Metrics.KILOMETERS)),
                GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates().includeDistance().sortAscending()).collectList().block();
        assertThat(byPoint).extracting(RedissonReactiveGeoCommandsTest::name).containsExactly("Catania", "Palermo");
        assertThat(byPoint.get(0).getDistance().getValue()).isCloseTo(56.4413, within(0.01));
        assertThat(byPoint.get(0).getContent().getPoint().getX()).isCloseTo(15.087269, within(0.0001));

        List<GeoResult<GeoLocation<ByteBuffer>>> byMember = geoCommands.geoRadiusByMember(key,
                ByteBuffer.wrap("Palermo".getBytes()), new Distance(200, Metrics.KILOMETERS),
                GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates().includeDistance().sortDescending()).collectList().block();
        assertThat(byMember).extracting(RedissonReactiveGeoCommandsTest::name).containsExactly("Catania", "Palermo");
        assertThat(byMember.get(0).getDistance().getValue()).isCloseTo(166.2742, within(0.01));
        assertThat(byMember.get(0).getContent().getPoint().getY()).isCloseTo(37.502669, within(0.0001));
    }

}
