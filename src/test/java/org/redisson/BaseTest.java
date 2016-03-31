package org.redisson;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public abstract class BaseTest {

    protected static RedissonClient redisson;
    protected static RedisRunner.RedisProcess redis;
    
    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        System.out.println("Starting up...");
        redis = defaultRedisTestInstance();
        redisson = createInstance();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                afterClass();
            } catch (InterruptedException ex) {
            }
        }));
    }

    @AfterClass
    public static void afterClass() throws InterruptedException {
        System.out.println("Shutting down...");
        redisson.shutdown();
        redis.stop();
    }

    public static Config createConfig() {
        String redisAddress = System.getProperty("redisAddress");
        if (redisAddress == null) {
            redisAddress = "127.0.0.1:6379";
        }
        Config config = new Config();
//        config.setCodec(new MsgPackJacksonCodec());
//        config.useSentinelServers().setMasterName("mymaster").addSentinelAddress("127.0.0.1:26379", "127.0.0.1:26389");
//        config.useClusterServers().addNodeAddress("127.0.0.1:7004", "127.0.0.1:7001", "127.0.0.1:7000");
        config.useSingleServer().setAddress(redisAddress);
//        .setPassword("mypass1");
//        config.useMasterSlaveConnection()
//        .setMasterAddress("127.0.0.1:6379")
//        .addSlaveAddress("127.0.0.1:6399")
//        .addSlaveAddress("127.0.0.1:6389");
        return config;
    }

    public static RedissonClient createInstance() {
        Config config = createConfig();
        return Redisson.create(config);
    }

    @Before
    public void before() {
        redisson.getKeys().flushall();
    }
    
    private static RedisRunner.RedisProcess defaultRedisTestInstance() throws IOException, InterruptedException {
        return new RedisRunner().run();
    }

}
