package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.api.RGeo;
import org.redisson.api.geo.GeoOrder;
import org.redisson.api.geo.GeoSearchArgs;
import org.redisson.api.geo.GeoUnit;
import org.redisson.client.codec.StringCodec;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.ReactiveGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchStoreCommandArgs;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;

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
    public void testGeoRadiusWithAny() {
        ByteBuffer key = addSicily("geo-any");
        RGeo<String> geo = redisson.getGeo("geo-any", StringCodec.INSTANCE);
        List<String> anyByPoint = geo.search(GeoSearchArgs.from(15, 37).radius(300, GeoUnit.KILOMETERS).countAny(1));
        List<String> anyByMember = geo.search(GeoSearchArgs.from("Catania").radius(300, GeoUnit.KILOMETERS).countAny(1));
        assertThat(anyByPoint).isNotEqualTo(geo.search(GeoSearchArgs.from(15, 37).radius(300, GeoUnit.KILOMETERS).order(GeoOrder.ASC).count(1)));
        assertThat(anyByMember).isNotEqualTo(geo.search(GeoSearchArgs.from("Catania").radius(300, GeoUnit.KILOMETERS).order(GeoOrder.ASC).count(1)));

        GeoRadiusCommandArgs args = GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance();
        args.limit(1, true);
        assertThat(geoCommands.geoRadius(key, new Circle(new Point(15, 37), new Distance(300, Metrics.KILOMETERS)), args).collectList().block())
                .extracting(RedissonReactiveGeoCommandsTest::name).isEqualTo(anyByPoint);
        assertThat(geoCommands.geoRadiusByMember(key, ByteBuffer.wrap("Catania".getBytes()), new Distance(300, Metrics.KILOMETERS), args).collectList().block())
                .extracting(RedissonReactiveGeoCommandsTest::name).isEqualTo(anyByMember);
    }

    @Test
    public void testGeoSearchFromMember() {
        ByteBuffer key = addSicily("geo-from-member");
        GeoReference<ByteBuffer> palermo = GeoReference.fromMember(ByteBuffer.wrap("Palermo".getBytes()));

        List<GeoResult<GeoLocation<ByteBuffer>>> results = geoCommands.geoSearch(key, palermo,
                GeoShape.byRadius(new Distance(200, Metrics.KILOMETERS)),
                GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending()).collectList().block();
        assertThat(results).extracting(RedissonReactiveGeoCommandsTest::name).containsExactly("Palermo", "Catania");

        ByteBuffer dest = ByteBuffer.wrap("geo-from-member-dest".getBytes());
        assertThat(geoCommands.geoSearchStore(dest, key, palermo, GeoShape.byRadius(new Distance(100, Metrics.KILOMETERS)),
                GeoSearchStoreCommandArgs.newGeoSearchStoreArgs()).block()).isEqualTo(1);
        assertThat(connection.zSetCommands().zRange("geo-from-member-dest".getBytes(), 0, -1))
                .extracting(String::new).containsExactly("Palermo");
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

        List<GeoResult<GeoLocation<ByteBuffer>>> search = geoCommands.geoSearch(key,
                GeoReference.fromCoordinate(new Point(15, 37)), GeoShape.byRadius(new Distance(200, Metrics.KILOMETERS)),
                GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().includeDistance().sortAscending()).collectList().block();
        assertThat(search).extracting(RedissonReactiveGeoCommandsTest::name).containsExactly("Catania", "Palermo");
        assertThat(search.get(1).getDistance().getValue()).isCloseTo(190.4424, within(0.01));
        assertThat(search.get(1).getContent().getPoint().getX()).isCloseTo(13.361389, within(0.0001));
    }

}
