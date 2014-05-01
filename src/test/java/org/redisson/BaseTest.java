package org.redisson;

import java.util.Collection;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public abstract class BaseTest {

    protected Redisson redisson;

    @Before
    public void before() {
        redisson = Redisson.create();
    }

    @After
    public void after() {
        redisson.flushdb();
        redisson.shutdown();
    }

    protected void clear(Map<?, ?> map, Redisson redisson) {
        map.clear();
        Assert.assertEquals(0, map.size());
        redisson.shutdown();
    }

    protected void clear(Collection<?> collection, Redisson redisson) {
        collection.clear();
        Assert.assertEquals(0, collection.size());
        redisson.shutdown();
    }

}
