package org.redisson;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.MsgPackJacksonCodec;

public abstract class BaseTest {

  protected static RedissonClient redisson;

  @BeforeClass
  public static void beforeClass() {
    redisson = createInstance();
  }

  @AfterClass
  public static void afterClass() {
    redisson.shutdown();
  }

  public static Config createConfig() {
    String redisAddress = System.getProperty("redisAddress");
    if (redisAddress == null) {
      redisAddress = "127.0.0.1:6379";
    }
    Config config = new Config();
    config.setCodec(new MsgPackJacksonCodec());
    // config.useSentinelConnection().setMasterName("mymaster").addSentinelAddress("127.0.0.1:26379",
    // "127.0.0.1:26389");
    // config.useClusterServers().addNodeAddress("127.0.0.1:7004", "127.0.0.1:7001",
    // "127.0.0.1:7000");
    config.useSingleServer().setAddress(redisAddress);
    // .setPassword("mypass1");
    // config.useMasterSlaveConnection()
    // .setMasterAddress("127.0.0.1:6379")
    // .addSlaveAddress("127.0.0.1:6399")
    // .addSlaveAddress("127.0.0.1:6389");
    return config;
  }

  public static RedissonClient createInstance() {
    Config config = createConfig();
    return Redisson.create(config);
  }

  @After
  public void after() {
    redisson.flushdb();
  }

}
