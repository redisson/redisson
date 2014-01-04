package org.redisson;

import java.util.Collection;

import org.junit.Assert;

public abstract class BaseRedissonTest {

    protected void clear(Collection<?> collection, Redisson redisson) {
        collection.clear();
        Assert.assertEquals(0, collection.size());

        redisson.shutdown();
    }

}
