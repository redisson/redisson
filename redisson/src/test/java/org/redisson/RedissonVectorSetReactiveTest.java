package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RVectorSetReactive;
import org.redisson.api.vector.VectorAddArgs;
import org.redisson.api.vector.VectorSimilarArgs;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonVectorSetReactiveTest extends BaseReactiveTest {

    @Test
    public void testEmptyCollectionResultsAsAbsent() {
        RVectorSetReactive set = redisson.getVectorSet("{vector}:reactive:empty");

        assertThat(sync(set.add(VectorAddArgs.element("A").vector(1.0, 1.0)))).isTrue();

        set.getVector("missing").as(StepVerifier::create).verifyComplete();
        set.getRawVector("missing").as(StepVerifier::create).verifyComplete();
        set.getNeighbors("missing").as(StepVerifier::create).verifyComplete();
        set.getNeighborEntries("missing").as(StepVerifier::create).verifyComplete();
        set.random(0).as(StepVerifier::create).verifyComplete();
        set.getSimilar(VectorSimilarArgs.vector(1.0, 1.0).filter(".missing == 1"))
                .as(StepVerifier::create)
                .verifyComplete();
        set.getSimilarEntries(VectorSimilarArgs.vector(1.0, 1.0).filter(".missing == 1"))
                .as(StepVerifier::create)
                .verifyComplete();
        set.getSimilarEntriesWithAttributes(VectorSimilarArgs.vector(1.0, 1.0).filter(".missing == 1"))
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    public void testCollectionResultsReturned() {
        RVectorSetReactive set = redisson.getVectorSet("{vector}:reactive:notEmpty");

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

}
