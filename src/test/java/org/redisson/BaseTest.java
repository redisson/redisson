package org.redisson;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class BaseTest {

    protected static Redisson redisson;

    @BeforeClass
    public static void beforeClass() {
        redisson = createInstance();
    }

    @AfterClass
    public static void afterClass() {
        redisson.shutdown();
    }

    public static Redisson createInstance() {
        String redisAddress = System.getProperty("redisAddress");
        if (redisAddress == null) {
            redisAddress = "127.0.0.1:6379";
        }
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress);
        return Redisson.create(config);
    }

    @After
    public void after() {
        redisson.flushdb();
    }

}
