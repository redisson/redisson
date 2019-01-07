package org.redisson.spring.support;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.Test;
import org.redisson.ClusterRunner;
import org.redisson.RedisRunner;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import io.netty.channel.EventLoopGroup;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class SpringNamespaceWikiTest {
    
    @Test
    public void testSingle() throws Exception {
        RedisRunner.RedisProcess run = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        try {
            ((ConfigurableApplicationContext)
                new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_single.xml"))
                .close();
        } finally {
            run.stop();
        }
    }
    
    @Test
    public void testRedisClient() throws Exception {
        RedisRunner.RedisProcess run = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        try {
            ClassPathXmlApplicationContext context
                    = new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_redis_client.xml");
            RedisClient redisClient = context.getBean(RedisClient.class);
            RedisConnection connection = redisClient.connect();
            Map<String, String> info = connection.sync(RedisCommands.INFO_ALL);
            assertThat(info, notNullValue());
            assertThat(info, not(info.isEmpty()));
            ((ConfigurableApplicationContext) context).close();
        } finally {
            run.stop();
        }
    }
    
    @Test
    public void testSingleWithPlaceholder() throws Exception {        
        RedisRunner.RedisProcess run = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        System.setProperty("redisson.redisAddress", run.getRedisServerAddressAndPort());
        System.setProperty("redisson.threads", "1");
        System.setProperty("redisson.nettyThreads", "2");
        System.setProperty("redisson.codecRef", "myCodec");
        System.setProperty("redisson.useLinuxNativeEpoll", "false");
        System.setProperty("redisson.redissonReferenceEnabled", "false");
        System.setProperty("redisson.codecProviderRef", "myCodecProvider");
        System.setProperty("redisson.resolverProviderRef", "myResolverProvider");
        System.setProperty("redisson.executorRef", "myExecutor");
        System.setProperty("redisson.eventLoopGroupRef", "myEventLoopGroup");
        System.setProperty("redisson.idleConnectionTimeout", "10000");
        System.setProperty("redisson.pingTimeout", "20000");
        System.setProperty("redisson.connectTimeout", "30000");
        System.setProperty("redisson.timeout", "40000");
        System.setProperty("redisson.retryAttempts", "5");
        System.setProperty("redisson.retryInterval", "60000");
        System.setProperty("redisson.reconnectionTimeout", "70000");
        System.setProperty("redisson.failedAttempts", "8");
        System.setProperty("redisson.password", "do_not_use_if_it_is_not_set");
        System.setProperty("redisson.subscriptionsPerConnection", "10");
        System.setProperty("redisson.clientName", "client_name");
        System.setProperty("redisson.sslEnableEndpointIdentification", "true");
        System.setProperty("redisson.sslProvider", "JDK");
        System.setProperty("redisson.sslTruststore", "/tmp/truststore.p12");
        System.setProperty("redisson.sslTruststorePassword", "not_set");
        System.setProperty("redisson.sslKeystore", "/tmp/keystore.p12");
        System.setProperty("redisson.sslKeystorePassword", "not_set");
        System.setProperty("redisson.subscriptionConnectionMinimumIdleSize", "11");
        System.setProperty("redisson.subscriptionConnectionPoolSize", "12");
        System.setProperty("redisson.connectionMinimumIdleSize", "13");
        System.setProperty("redisson.connectionPoolSize", "14");
        System.setProperty("redisson.database", "15");
        System.setProperty("redisson.dnsMonitoring", "false");
        System.setProperty("redisson.dnsMonitoringInterval", "80000");
        
        try {
            ClassPathXmlApplicationContext context
                    = new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_single_with_placeholder.xml");
            RedissonClient redisson = context.getBean("myId", RedissonClient.class);
            assertNotNull(redisson);
            Config config = redisson.getConfig();
            assertEquals(1, config.getThreads());
            assertEquals(2, config.getNettyThreads());
            assertSame(context.getBean("myCodec", Codec.class), config.getCodec());
            assertEquals(false, config.isReferenceEnabled());
            assertSame(context.getBean("myExecutor", Executor.class), config.getExecutor());
            assertSame(context.getBean("myEventLoopGroup", EventLoopGroup.class), config.getEventLoopGroup());
            Method method = Config.class.getDeclaredMethod("getSingleServerConfig", (Class<?>[]) null);
            method.setAccessible(true);
            SingleServerConfig single = (SingleServerConfig) method.invoke(config, (Object[]) null);
            assertEquals(10000, single.getIdleConnectionTimeout());
            assertEquals(20000, single.getPingTimeout());
            assertEquals(30000, single.getConnectTimeout());
            assertEquals(40000, single.getTimeout());
            assertEquals(5, single.getRetryAttempts());
            assertEquals(60000, single.getRetryInterval());
            assertEquals("do_not_use_if_it_is_not_set", single.getPassword());
            assertEquals(10, single.getSubscriptionsPerConnection());
            assertEquals("client_name", single.getClientName());
            assertEquals(11, single.getSubscriptionConnectionMinimumIdleSize());
            assertEquals(12, single.getSubscriptionConnectionPoolSize());
            assertEquals(13, single.getConnectionMinimumIdleSize());
            assertEquals(14, single.getConnectionPoolSize());
            assertEquals(15, single.getDatabase());
            assertEquals(80000, single.getDnsMonitoringInterval());
            ((ConfigurableApplicationContext) context).close();
        } finally {
            run.stop();
        }
    }
    
    @Test
    public void testMasterSlave() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        try {
            ((ConfigurableApplicationContext)
                    new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_master_slave.xml"))
                    .close();
        } finally {
            master.stop();
            slave1.stop();
            slave2.stop();
        }
    }
    
    @Test
    public void testSentinel() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
//                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "do_not_use_if_it_is_not_set")
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
//                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "do_not_use_if_it_is_not_set")
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
//                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "do_not_use_if_it_is_not_set")
                .run();
        try {
            ((ConfigurableApplicationContext)
                    new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_sentinel.xml"))
                    .close();
        } finally {
            master.stop();
            slave1.stop();
            slave2.stop();
            sentinel1.stop();
            sentinel2.stop();
            sentinel3.stop();
        }
    }
    
    @Test
    public void testReplicated() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        try {
            ((ConfigurableApplicationContext)
                    new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_replicated.xml"))
                    .close();
        } finally {
            master.stop();
            slave1.stop();
            slave2.stop();
        }
    }
    
    @Test
    public void testCluster() throws Exception {
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(new RedisRunner()
                        .requirepass("do_not_use_if_it_is_not_set")
                        .port(6379)
                        .randomDir()
                        .nosave())
                .addNode(new RedisRunner()
                        .requirepass("do_not_use_if_it_is_not_set")
                        .port(6380)
                        .randomDir()
                        .nosave())
                .addNode(new RedisRunner()
                        .requirepass("do_not_use_if_it_is_not_set")
                        .port(6381)
                        .randomDir()
                        .nosave());
        ClusterRunner.ClusterProcesses cluster = clusterRunner.run();
        
        try {
            ((ConfigurableApplicationContext)
                    new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_cluster.xml"))
                    .close();
        } finally {
            cluster.shutdown();
        }
    }
}
