package org.redisson.spring.support;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.ClusterRunner;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.RedissonRuntimeEnvironment;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.codec.MsgPackJacksonCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class SpringNamespaceTest extends BaseTest {

    private static ApplicationContext context;
    
    @BeforeClass
    public static void setupClass() throws Exception {
        if (!RedissonRuntimeEnvironment.isTravis) {
            startContext();
        }
    }

    @AfterClass
    public static void shutDownClass() throws Exception {
        if (!RedissonRuntimeEnvironment.isTravis) {
            stopContext();
        }
    }
    
    @Before
    public void setup() throws Exception {
        if (RedissonRuntimeEnvironment.isTravis) {
            startContext();
        }
    }

    @After
    public void shutDown() throws Exception {
        if (RedissonRuntimeEnvironment.isTravis) {
            stopContext();
        }
    }

    public static void startContext() throws Exception {
        System.setProperty("redisAddress", "redis://" + RedisRunner.getDefaultRedisServerBindAddressAndPort());
        
        //Needs a instance running on the default port, launch it if there isn't one already
        if (RedisRunner.isFreePort(6379)) {
            new RedisRunner()
                    .nosave()
                    .randomDir()
                    .run();
        }
        
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .slaveof(
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerBindAddress(),
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerPort())
                .run();
        System.setProperty("slave1Address", "redis://" + slave1.getRedisServerAddressAndPort());
        
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .slaveof(
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerBindAddress(),
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerPort())
                .run();
        System.setProperty("slave2Address", "redis://" + slave2.getRedisServerAddressAndPort());
        
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .sentinel()
                .sentinelMonitor(
                        "myMaster",
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerBindAddress(),
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerPort(),
                        2).run();
        System.setProperty("sentinel1Address", "redis://" + sentinel1.getRedisServerAddressAndPort());
        
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .sentinel()
                .sentinelMonitor(
                        "myMaster",
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerBindAddress(),
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerPort(),
                        2).run();
        System.setProperty("sentinel2Address", "redis://" + sentinel2.getRedisServerAddressAndPort());
        
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .sentinel()
                .sentinelMonitor(
                        "myMaster",
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerBindAddress(),
                        RedisRunner.getDefaultRedisServerInstance().getRedisServerPort(),
                        2).run();
        System.setProperty("sentinel3Address", "redis://" + sentinel3.getRedisServerAddressAndPort());
        RedisRunner slave = new RedisRunner().randomPort().randomDir().nosave();
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(new RedisRunner().randomPort().randomDir().nosave(),//master1
                        new RedisRunner().randomPort().randomDir().nosave(),//slave1-1
                        new RedisRunner().randomPort().randomDir().nosave(),//slave1-2
                        slave)//slave1-3
                .addNode(new RedisRunner().randomPort().randomDir().nosave(),//master2
                        new RedisRunner().randomPort().randomDir().nosave(),//slave2-1
                        new RedisRunner().randomPort().randomDir().nosave())//slave2-2
                .addNode(new RedisRunner().randomPort().randomDir().nosave(),//master3
                        new RedisRunner().randomPort().randomDir().nosave(),//slave3-1
                        new RedisRunner().randomPort().randomDir().nosave())//slave3-2
                .addNode(slave,//slave1-3
                        new RedisRunner().randomPort().randomDir().nosave(),//slave1-3-1
                        new RedisRunner().randomPort().randomDir().nosave());//slave1-3-2
        final AtomicLong index = new AtomicLong(0);
        clusterRunner.run().getNodes().stream().forEach((node) -> {
            System.setProperty("node" + (index.incrementAndGet()) + "Address", "redis://" + node.getRedisServerAddressAndPort());
        });
        
        context = new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace.xml");
    }
 
    public static void stopContext() throws Exception {
        ((ConfigurableApplicationContext) context).close();
    }

    public static class AutowireRedisson {

        @Autowired
        @Qualifier("redisson1")
        private Redisson redisson1;

        @Autowired
        @Qualifier("redisson2")
        private RedissonClient redisson2;

        @Autowired
        @Qualifier("redisson3")
        private Redisson redisson3;

        @Autowired
        @Qualifier("redisson4")
        private RedissonClient redisson4;

        @Autowired
        @Qualifier("myRedisson1")
        private Redisson redisson5;

        @Autowired
        @Qualifier("myRedisson2")
        private Redisson redisson6;

        @Autowired
        @Qualifier("qualifier1")
        private RedissonClient redisson7;

        @Autowired
        @Qualifier("qualifier2")
        private RedissonClient redisson8;

        /**
         * @return the redisson1
         */
        public Redisson getRedisson1() {
            return redisson1;
        }

        /**
         * @param redisson1 the redisson1 to set
         */
        public void setRedisson1(Redisson redisson1) {
            this.redisson1 = redisson1;
        }

        /**
         * @return the redisson2
         */
        public RedissonClient getRedisson2() {
            return redisson2;
        }

        /**
         * @param redisson2 the redisson2 to set
         */
        public void setRedisson2(RedissonClient redisson2) {
            this.redisson2 = redisson2;
        }

        /**
         * @return the redisson3
         */
        public Redisson getRedisson3() {
            return redisson3;
        }

        /**
         * @param redisson3 the redisson3 to set
         */
        public void setRedisson3(Redisson redisson3) {
            this.redisson3 = redisson3;
        }

        /**
         * @return the redisson4
         */
        public RedissonClient getRedisson4() {
            return redisson4;
        }

        /**
         * @param redisson4 the redisson4 to set
         */
        public void setRedisson4(RedissonClient redisson4) {
            this.redisson4 = redisson4;
        }

        /**
         * @return the redisson5
         */
        public Redisson getRedisson5() {
            return redisson5;
        }

        /**
         * @param redisson5 the redisson5 to set
         */
        public void setRedisson5(Redisson redisson5) {
            this.redisson5 = redisson5;
        }

        /**
         * @return the redisson6
         */
        public Redisson getRedisson6() {
            return redisson6;
        }

        /**
         * @param redisson6 the redisson6 to set
         */
        public void setRedisson6(Redisson redisson6) {
            this.redisson6 = redisson6;
        }

        /**
         * @return the redisson7
         */
        public RedissonClient getRedisson7() {
            return redisson7;
        }

        /**
         * @param redisson7 the redisson7 to set
         */
        public void setRedisson7(RedissonClient redisson7) {
            this.redisson7 = redisson7;
        }

        /**
         * @return the redisson8
         */
        public RedissonClient getRedisson8() {
            return redisson8;
        }

        /**
         * @param redisson8 the redisson8 to set
         */
        public void setRedisson8(RedissonClient redisson8) {
            this.redisson8 = redisson8;
        }

    }

    @Test
    public void testNamespace() {
        Object bean = context.getBean("myRedisson1");
        assertTrue(bean instanceof Redisson);
    }

    @Test
    public void testAlias() {
        Object origin = context.getBean("myRedisson1");
        assertTrue(origin instanceof Redisson);
        Object bean = context.getBean("redisson1");
        assertTrue(bean instanceof Redisson);
        assertEquals(origin, bean);
        bean = context.getBean("redisson2");
        assertTrue(bean instanceof Redisson);
        assertEquals(origin, bean);
    }
    
    @Test
    public void testAutowire() {
        AutowireRedisson bean = context.getAutowireCapableBeanFactory().getBean(AutowireRedisson.class);
        assertNotNull(bean.getRedisson1());
        assertNotNull(bean.getRedisson2());
        assertNotNull(bean.getRedisson3());
        assertNotNull(bean.getRedisson4());
        assertNotNull(bean.getRedisson5());
        assertNotNull(bean.getRedisson6());
        assertNotNull(bean.getRedisson7());
        assertNotNull(bean.getRedisson8());
        assertEquals(bean.getRedisson1(), bean.getRedisson2());
        assertEquals(bean.getRedisson1(), bean.getRedisson5());
        assertNotEquals(bean.getRedisson1(), bean.getRedisson7());
        assertNotEquals(bean.getRedisson1(), bean.getRedisson8());
        assertEquals(bean.getRedisson3(), bean.getRedisson4());
        assertEquals(bean.getRedisson3(), bean.getRedisson6());
        assertNotEquals(bean.getRedisson3(), bean.getRedisson7());
        assertNotEquals(bean.getRedisson3(), bean.getRedisson8());
        assertNotEquals(bean.getRedisson7(), bean.getRedisson8());
    }
    
    @Test
    public void testBeanRef() {
        AutowireRedisson bean = context.getAutowireCapableBeanFactory().getBean(AutowireRedisson.class);
        assertTrue(bean.getRedisson1().getConfig().getCodec() instanceof MsgPackJacksonCodec);
        assertFalse(bean.getRedisson3().getConfig().getCodec() instanceof MsgPackJacksonCodec);
        assertFalse(bean.getRedisson7().getConfig().getCodec() instanceof MsgPackJacksonCodec);
        assertFalse(bean.getRedisson8().getConfig().getCodec() instanceof MsgPackJacksonCodec);
    }
    
    public static class AutowireRedis {
        
        @Autowired
        private RedisClient redisClient;

        /**
         * @return the redisClient
         */
        public RedisClient getRedisClient() {
            return redisClient;
        }

        /**
         * @param redisClient the redisClient to set
         */
        public void setRedisClient(RedisClient redisClient) {
            this.redisClient = redisClient;
        }
    }
    
    @Test
    public void testAutowireRedis() {
        AutowireRedis bean = context.getAutowireCapableBeanFactory().getBean(AutowireRedis.class);
        RedisConnection connection = bean.getRedisClient().connect();
        assertTrue(connection.isActive());
        connection.closeAsync().awaitUninterruptibly();
    }
}
