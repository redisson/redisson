package org.redisson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RSortedSet;

public class ConcurrentRedissonSortedSetTest extends BaseConcurrentTest {

    @Test
    public void testAdd_SingleInstance() throws InterruptedException {
        final String name = "testSingleReplaceOldValue_SingleInstance";

        Redisson r = Redisson.create();
        RSortedSet<Integer> map = r.getSortedSet(name);
        map.clear();

        int length = 1000;
        final AtomicInteger counter = new AtomicInteger();
        testSingleInstanceConcurrency(length, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                RSortedSet<Integer> set = redisson.getSortedSet(name);
                int c = counter.incrementAndGet();
                Assert.assertTrue(set.add(c));
            }
        });

        List<Integer> elements = new ArrayList<Integer>();
        for (int i = 1; i < length+1; i++) {
            elements.add(i);
        }
        MatcherAssert.assertThat(map, Matchers.contains(elements.toArray(new Integer[elements.size()])));
        r.shutdown();
    }

}
