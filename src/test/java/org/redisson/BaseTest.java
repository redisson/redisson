package org.redisson;

import java.util.Collection;
import java.util.Map;

import org.junit.Assert;

public abstract class BaseTest {

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
