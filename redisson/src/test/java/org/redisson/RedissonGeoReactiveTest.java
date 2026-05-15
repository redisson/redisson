package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RGeoReactive;
import org.redisson.api.geo.GeoSearchArgs;
import org.redisson.api.geo.GeoUnit;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonGeoReactiveTest extends BaseReactiveTest {

    @Test
    public void testEmptyCollectionResultsAsAbsent() {
        RGeoReactive<String> geo = redisson.getGeo("{geo}:reactive:empty");
        GeoSearchArgs args = GeoSearchArgs.from(0, 0).radius(1, GeoUnit.METERS);

        geo.hash("test").as(StepVerifier::create).verifyComplete();
        geo.pos("test").as(StepVerifier::create).verifyComplete();
        geo.search(args).as(StepVerifier::create).verifyComplete();
        geo.searchWithDistance(args).as(StepVerifier::create).verifyComplete();
        geo.searchWithPosition(args).as(StepVerifier::create).verifyComplete();
    }

    @Test
    public void testCollectionResultsReturned() {
        RGeoReactive<String> geo = redisson.getGeo("{geo}:reactive:notEmpty");
        GeoSearchArgs args = GeoSearchArgs.from(13.361389, 38.115556).radius(1, GeoUnit.KILOMETERS);

        assertThat(sync(geo.add(13.361389, 38.115556, "Palermo"))).isEqualTo(1L);
        assertThat(sync(geo.hash("Palermo"))).containsKey("Palermo");
        assertThat(sync(geo.pos("Palermo"))).containsKey("Palermo");
        assertThat(sync(geo.search(args))).contains("Palermo");
        assertThat(sync(geo.searchWithDistance(args))).containsKey("Palermo");
        assertThat(sync(geo.searchWithPosition(args))).containsKey("Palermo");
    }

}
