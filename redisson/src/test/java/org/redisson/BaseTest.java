package org.redisson;

import java.io.IOException;
import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class BaseTest {
    
    protected RedissonClient redisson;
    protected static RedissonClient defaultRedisson;

    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
    	
    	Config config = getConfig();
    	
    	defaultRedisson = Redisson.create(config);
    	
//        if (!RedissonRuntimeEnvironment.isTravis) {
//            RedisRunner.startDefaultRedisServerInstance();
//            defaultRedisson = createInstance();
//            Runtime.getRuntime().addShutdownHook(new Thread() {
//                @Override
//                public void run() {
//                    defaultRedisson.shutdown();
//                    try {
//                        RedisRunner.shutDownDefaultRedisServerInstance();
//                    } catch (InterruptedException e) {
//                        // TODO Auto-generated catch block
//                        e.printStackTrace();
//                    }
//                }
//            });
//        }
    }

	private static Config getConfig() throws IOException, JsonParseException, JsonProcessingException {
		URL stream = BaseTest.class.getClassLoader().getResource("redisson-config.json");
		JsonFactory f = new JsonFactory();
		f.enable(Feature.ALLOW_COMMENTS);
		f.disable(Feature.ALLOW_SINGLE_QUOTES);
		JsonParser p = f.createParser(stream);
		ObjectMapper mapper = new ObjectMapper();
		TreeNode tree = mapper.readTree(p);
		String json = mapper.writer().writeValueAsString(tree);
    	Config config = Config.fromJSON(json);
		return config;
	}

    @Before
    public void before() throws IOException, InterruptedException {
    	
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
            redisson = createInstance();
        } else {
            if (redisson == null) {
                redisson = defaultRedisson;
            }
            if (flushBetweenTests()) {
                redisson.getKeys().flushall();
            }
        }
    }

    @After
    public void after() throws InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            redisson.shutdown();
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

    public static Config createConfig() {
		Config config = null;
    	try {
			config = getConfig();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return config;
//        String redisAddress = System.getProperty("redisAddress");
//        if (redisAddress == null) {
//            redisAddress = "127.0.0.1:6379";
//        }
//        Config config = new Config();
//        config.setCodec(new MsgPackJacksonCodec());
//        config.useSentinelServers().setMasterName("mymaster").addSentinelAddress("127.0.0.1:26379", "127.0.0.1:26389");
//        config.useClusterServers().addNodeAddress("127.0.0.1:7004", "127.0.0.1:7001", "127.0.0.1:7000");
//        config.useSingleServer()
//                .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
//        .setPassword("mypass1");
//        config.useMasterSlaveConnection()
//        .setMasterAddress("127.0.0.1:6379")
//        .addSlaveAddress("127.0.0.1:6399")
//        .addSlaveAddress("127.0.0.1:6389");
//        return config;
    }

    public static RedissonClient createInstance() {
        Config config = createConfig();
        return Redisson.create(config);
    }

    protected boolean flushBetweenTests() {
        return true;
    }
}
