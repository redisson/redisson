package org.redisson.client.handler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RedissonClient;
import org.redisson.api.WorkerOptions;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandWriteExceptionTest {

    private static final Logger log = LoggerFactory.getLogger(CommandWriteExceptionTest.class);


    protected static RedissonClient redisson;

    @BeforeAll
    public static void beforeClass() throws IOException, InterruptedException {
        RedisRunner.startDefaultRedisServerInstance();
        redisson = createInstance();
    }

    @AfterAll
    public static void afterClass() throws InterruptedException {
        redisson.shutdown();
        RedisRunner.shutDownDefaultRedisServerInstance();
    }

    public static Config createConfig() {

        Config config = new Config();
        config.setCodec(new StringCodec());
        config.useSingleServer()
            .setConnectionPoolSize(2)
            .setConnectionMinimumIdleSize(1)
            .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        return config;
    }

    public static RedissonClient createInstance() {
        Config config = createConfig();
        return Redisson.create(config);
    }


    @Test
    public void testGetAndClearExpire() {


        RScheduledExecutorService executorService = redisson.getExecutorService("redis-executor-test");
        executorService.registerWorkers(WorkerOptions.defaults());

        String orderInfo = "orderInfo";
        String orderNo = "orderId_";
        RMapCache<String, String> mapCache = redisson.getMapCache("mapCache");
        mapCache.clear();
        for (int i = 0; i < 100; i++) {
            mapCache.put(orderNo + i, orderInfo + "_" + i, 30, TimeUnit.MINUTES);
        }

        long startTime = System.currentTimeMillis();
        for (int j = 0; j < 10; j++) {
            try {
                executorService.cancelTask(null);
            } catch (Exception ex) {
                // ignore
            }
        }
        log.info("cancelTask, time cost(ms):{}", System.currentTimeMillis() - startTime);
        startTime = System.currentTimeMillis();
        try {
            String cacheValue = mapCache.get(orderNo + "0");
            log.info("cacheValue={}, time cost(ms):{}", cacheValue, System.currentTimeMillis() - startTime);
        } catch (Exception ex) {
            log.error("fetch cache fail,time cost(ms):{}, ex:{}", System.currentTimeMillis() - startTime,
                ex.getMessage(), ex);
        }
        try {
            Thread.sleep(10000);
        } catch (Exception ex) {
            //ignore
        }

        executorService.shutdown();


    }

}
