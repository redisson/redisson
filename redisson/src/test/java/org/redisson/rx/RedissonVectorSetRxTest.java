package org.redisson.rx;

import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.redisson.api.RVectorSetRx;
import org.redisson.api.vector.VectorAddArgs;
import org.redisson.api.vector.VectorSimilarArgs;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonVectorSetRxTest extends BaseRxTest {

    @Test
    public void testEmptyCollectionResultsAsAbsent() {
        RVectorSetRx set = redisson.getVectorSet("{vector}:rx:empty");

        assertThat(sync(set.add(VectorAddArgs.element("A").vector(1.0, 1.0)))).isTrue();

        assertNoValues(set.getVector("missing").test());
        assertNoValues(set.getRawVector("missing").test());
        assertNoValues(set.getNeighbors("missing").test());
        assertNoValues(set.getNeighborEntries("missing").test());
        assertNoValues(set.random(0).test());
        assertNoValues(set.getSimilar(VectorSimilarArgs.vector(1.0, 1.0).filter(".missing == 1")).test());
        assertNoValues(set.getSimilarEntries(VectorSimilarArgs.vector(1.0, 1.0).filter(".missing == 1")).test());
        assertNoValues(set.getSimilarEntriesWithAttributes(VectorSimilarArgs.vector(1.0, 1.0).filter(".missing == 1")).test());
    }

    @Test
    public void testCollectionResultsReturned() {
        RVectorSetRx set = redisson.getVectorSet("{vector}:rx:notEmpty");

        assertThat(sync(set.add(VectorAddArgs.element("A").vector(1.0, 1.0)))).isTrue();
        assertThat(sync(set.add(VectorAddArgs.element("B").vector(1.1, 1.1)))).isTrue();

        assertThat(sync(set.getVector("A"))).hasSize(2);
        assertThat(sync(set.getRawVector("A"))).isNotEmpty();
        assertThat(sync(set.getNeighbors("A"))).isNotEmpty();
        assertThat(sync(set.getNeighborEntries("A"))).isNotEmpty();
        assertThat(sync(set.random(2))).isNotEmpty();
        assertThat(sync(set.getSimilar(VectorSimilarArgs.vector(1.0, 1.0).count(2)))).contains("A");
        assertThat(sync(set.getSimilarEntries(VectorSimilarArgs.vector(1.0, 1.0).count(2)))).isNotEmpty();
        assertThat(sync(set.getSimilarEntriesWithAttributes(VectorSimilarArgs.vector(1.0, 1.0).count(2)))).isNotEmpty();
    }

    private static void assertNoValues(TestObserver<?> observer) {
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertNoValues();
    }

}
