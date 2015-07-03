package org.redisson;

import org.junit.After;
import org.junit.Before;

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

}
