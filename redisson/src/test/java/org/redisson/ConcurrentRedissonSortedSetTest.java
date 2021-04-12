package org.redisson;

import static org.assertj.core.api.Assertions.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSortedSet;
import org.redisson.api.RedissonClient;

public class ConcurrentRedissonSortedSetTest extends BaseConcurrentTest {

    @Test
    public void testAdd_SingleInstance() throws InterruptedException {
        final String name = "testAdd_SingleInstance";

        RedissonClient r = BaseTest.createInstance();
        RSortedSet<Integer> map = r.getSortedSet(name);
        map.clear();

        int length = 5000;
        final List<Integer> elements = new ArrayList<Integer>();
        for (int i = 1; i < length + 1; i++) {
            elements.add(i);
        }
        Collections.shuffle(elements);
        final AtomicInteger counter = new AtomicInteger(-1);
        testSingleInstanceConcurrency(length, rc -> {
            RSortedSet<Integer> set = rc.getSortedSet(name);
            int c = counter.incrementAndGet();
            Integer element = elements.get(c);
            Assertions.assertTrue(set.add(element));
        });

        Collections.sort(elements);
        Integer[] p = elements.toArray(new Integer[elements.size()]);
        assertThat(map).containsExactly(p);

        map.clear();
        r.shutdown();
    }

    @Test
    public void testAddRemove_SingleInstance() throws InterruptedException, NoSuchAlgorithmException {
        final String name = "testAddNegative_SingleInstance";

        RedissonClient r = BaseTest.createInstance();
        RSortedSet<Integer> map = r.getSortedSet(name);
        map.clear();
        int length = 1000;
        for (int i = 0; i < length; i++) {
            map.add(i);
        }

        final AtomicInteger counter = new AtomicInteger(length);
        final Random rnd = SecureRandom.getInstanceStrong();
        testSingleInstanceConcurrency(length, rc -> {
            RSortedSet<Integer> set = rc.getSortedSet(name);
            int c = counter.incrementAndGet();
            Assertions.assertTrue(set.add(c));
            set.remove(rnd.nextInt(length));
        });

        Assertions.assertEquals(counter.get(), length*2);
        
        Integer prevVal = null;
        for (Integer val : map) {
            if (prevVal == null) {
                prevVal = val;
                continue;
            }
            if (val < prevVal) {
                Assertions.fail();
            }
        }
        
        r.shutdown();
    }

}
