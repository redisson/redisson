package org.redisson;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public abstract class BaseTest {
    
    protected RedissonClient redisson;
    protected static RedissonClient defaultRedisson;

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
            RedisRunner.startDefaultRedisServerInstance();
            defaultRedisson = createInstance();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    defaultRedisson.shutdown();
                    try {
                        RedisRunner.shutDownDefaultRedisServerInstance();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            });
    }

    @Before
    public void before() throws IOException, InterruptedException {
            if (redisson == null) {
                redisson = defaultRedisson;
            }
            if (flushBetweenTests()) {
                redisson.getKeys().flushall();
            }
    }

    @After
    public void after() throws InterruptedException {
    }

    public static Config createConfig() {
//        String redisAddress = System.getProperty("redisAddress");
//        if (redisAddress == null) {
//            redisAddress = "127.0.0.1:6379";
//        }
        Config config = new Config();
//        config.setCodec(new MsgPackJacksonCodec());
//        config.useSentinelServers().setMasterName("mymaster").addSentinelAddress("127.0.0.1:26379", "127.0.0.1:26389");
//        config.useClusterServers().addNodeAddress("127.0.0.1:7004", "127.0.0.1:7001", "127.0.0.1:7000");
        config.useSingleServer()
                .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
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

    protected boolean flushBetweenTests() {
        return true;
    }
}
