package org.redisson;

import java.util.ArrayList;
import java.util.Collections;
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
        final String name = "testAdd_SingleInstance";

        Redisson r = Redisson.create();
        RSortedSet<Integer> map = r.getSortedSet(name);
        map.clear();

        int length = 5000;
        final List<Integer> elements = new ArrayList<Integer>();
        for (int i = 1; i < length+1; i++) {
            elements.add(i);
        }
        Collections.shuffle(elements);
        final AtomicInteger counter = new AtomicInteger(-1);
        testSingleInstanceConcurrency(length, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                RSortedSet<Integer> set = redisson.getSortedSet(name);
                int c = counter.incrementAndGet();
                Integer element = elements.get(c);
                Assert.assertTrue(set.add(element));
            }
        });

//        for (Integer integer : map) {
//            System.out.println("int: " + integer);
//        }
        
        Collections.sort(elements);
        Integer[] p = elements.toArray(new Integer[elements.size()]);
        MatcherAssert.assertThat(map, Matchers.contains(p));

        map.clear();
        r.shutdown();
    }

    @Test
    public void testAddNegative_SingleInstance() throws InterruptedException {
        final String name = "testAddNegative_SingleInstance";

        Redisson r = Redisson.create();
        RSortedSet<Integer> map = r.getSortedSet(name);
        map.clear();

        int length = 1000;
        final AtomicInteger counter = new AtomicInteger();
        testSingleInstanceConcurrency(length, new RedissonRunnable() {
            @Override
            public void run(Redisson redisson) {
                RSortedSet<Integer> set = redisson.getSortedSet(name);
                int c = counter.decrementAndGet();
                Assert.assertTrue(set.add(c));
            }
        });

        List<Integer> elements = new ArrayList<Integer>();
        for (int i = -length; i < 0; i++) {
            elements.add(i);
        }
        Integer[] p = elements.toArray(new Integer[elements.size()]);
        MatcherAssert.assertThat(map, Matchers.contains(p));

        map.clear();
        r.shutdown();
    }

}
