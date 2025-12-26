package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class RedissonConnectionTest extends BaseConnectionTest {
    
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
    public void testGeoPos() {
        connection.geoAdd("key1".getBytes(), new Point(13.361389, 38.115556), "value1".getBytes());
        connection.geoAdd("key1".getBytes(), new Point(15.087269, 37.502669), "value2".getBytes());

        List<Point> s = connection.geoPos("key1".getBytes(), "value1".getBytes(), "value2".getBytes());
        assertThat(s).hasSize(2);

        List<Point> se = connection.geoPos("key2".getBytes(), "value1".getBytes(), "value2".getBytes());
        assertThat(se).isEmpty();
    }

    @Test
    public void testRadius() {
        connection.geoAdd("key1".getBytes(), new Point(13.361389, 38.115556), "value1".getBytes());
        connection.geoAdd("key1".getBytes(), new Point(15.087269, 37.502669), "value2".getBytes());

        GeoResults<RedisGeoCommands.GeoLocation<byte[]>> l = connection.geoRadius("key1".getBytes(), new Circle(new Point(15, 37), new Distance(200, RedisGeoCommands.DistanceUnit.KILOMETERS)));
        assertThat(l.getContent()).hasSize(2);
        assertThat(l.getContent().get(0).getContent().getName()).isEqualTo("value1".getBytes());
        assertThat(l.getContent().get(1).getContent().getName()).isEqualTo("value2".getBytes());
    }

    @Test
    public void testRadiusWithCoords() {
        connection.geoAdd("key1".getBytes(), new Point(13.361389, 38.115556), "value1".getBytes());
        connection.geoAdd("key1".getBytes(), new Point(15.087269, 37.502669), "value2".getBytes());

        GeoResults<RedisGeoCommands.GeoLocation<byte[]>> l = connection.geoRadius("key1".getBytes(),
                new Circle(new Point(15, 37), new Distance(200, RedisGeoCommands.DistanceUnit.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates());

        assertThat(l.getContent()).hasSize(2);
        assertThat(l.getContent().get(0).getContent().getName()).isEqualTo("value1".getBytes());
        assertThat(l.getContent().get(0).getContent().getPoint().toString()).isEqualTo(new Point(13.361389, 38.115556).toString());
        assertThat(l.getContent().get(1).getContent().getName()).isEqualTo("value2".getBytes());
        assertThat(l.getContent().get(1).getContent().getPoint().toString()).isEqualTo(new Point(15.087267, 37.502668).toString());

        GeoResults<RedisGeoCommands.GeoLocation<byte[]>> l2 = connection.geoRadius("key1".getBytes(),
                new Circle(new Point(15, 37), new Distance(200, RedisGeoCommands.DistanceUnit.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs());

        assertThat(l2.getContent()).hasSize(2);
        assertThat(l2.getContent().get(0).getContent().getName()).isEqualTo("value1".getBytes());
        assertThat(l2.getContent().get(1).getContent().getName()).isEqualTo("value2".getBytes());

    }

    @Test
    public void testHSetGet() {
        assertThat(connection.hSet("key".getBytes(), "field".getBytes(), "value".getBytes())).isTrue();
        assertThat(connection.hGet("key".getBytes(), "field".getBytes())).isEqualTo("value".getBytes());
    }

    @Test
    public void testGetClientList() {
        List<RedisClientInfo> info = connection.getClientList();
        assertThat(info.size()).isGreaterThan(10);
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

}
