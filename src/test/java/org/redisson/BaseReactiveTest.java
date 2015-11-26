package org.redisson;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import rx.Observable;
import rx.Single;

public abstract class BaseReactiveTest {

    protected static RedissonReactiveClient redisson;

    @BeforeClass
    public static void beforeClass() {
        redisson = createInstance();
    }

    @AfterClass
    public static void afterClass() {
        redisson.shutdown();
    }

    public <V> V sync(Single<V> ob) {
        return ob.toBlocking().value();
    }

    public static Config createConfig() {
        String redisAddress = System.getProperty("redisAddress");
        if (redisAddress == null) {
            redisAddress = "127.0.0.1:6379";
        }
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress);
        return config;
    }

    public static RedissonReactiveClient createInstance() {
        Config config = createConfig();
        return Redisson.createReactive(config);
    }

    @After
    public void after() {
        redisson.flushdb();
    }

}
