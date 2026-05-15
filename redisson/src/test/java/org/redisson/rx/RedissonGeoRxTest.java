package org.redisson.rx;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.redisson.api.RGeoRx;
import org.redisson.api.geo.GeoSearchArgs;
import org.redisson.api.geo.GeoUnit;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonGeoRxTest extends BaseRxTest {

    @Test
    public void testEmptyCollectionResultsAsAbsent() {
        RGeoRx<String> geo = redisson.getGeo("{geo}:rx:empty");
        GeoSearchArgs args = GeoSearchArgs.from(0, 0).radius(1, GeoUnit.METERS);

        assertNoValues(geo.hash("test").test());
        assertNoValues(geo.pos("test").test());
        assertNoValues(geo.search(args).test());
        assertNoValues(geo.searchWithDistance(args).test());
        assertNoValues(geo.searchWithPosition(args).test());
    }

    @Test
    public void testCollectionResultsReturned() {
        RGeoRx<String> geo = redisson.getGeo("{geo}:rx:notEmpty");
        GeoSearchArgs args = GeoSearchArgs.from(13.361389, 38.115556).radius(1, GeoUnit.KILOMETERS);

        assertThat(sync(geo.add(13.361389, 38.115556, "Palermo"))).isEqualTo(1L);
        assertThat(sync(geo.hash("Palermo"))).containsKey("Palermo");
        assertThat(sync(geo.pos("Palermo"))).containsKey("Palermo");
        assertThat(sync(geo.search(args))).contains("Palermo");
        assertThat(sync(geo.searchWithDistance(args))).containsKey("Palermo");
        assertThat(sync(geo.searchWithPosition(args))).containsKey("Palermo");
    }

    private static void assertNoValues(TestObserver<?> observer) {
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertNoValues();
    }

}
