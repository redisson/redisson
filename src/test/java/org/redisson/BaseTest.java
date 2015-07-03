package org.redisson;

import java.util.Collection;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.redisson.codec.SerializationCodec;

public abstract class BaseTest {

    protected Redisson redisson;

    @Before
    public void before() {
        this.redisson = createInstance();
    }

    public static Redisson createInstance() {
        String redisAddress = System.getProperty("redisAddress");
        if (redisAddress == null) {
            redisAddress = "127.0.0.1:6379";
        }
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress);
//        config.setCodec(new SerializationCodec());
        return Redisson.create(config);
    }

    @After
    public void after() {
        try {
            redisson.flushdb();
        } finally {
            redisson.shutdown();
        }
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
