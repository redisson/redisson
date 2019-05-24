package org.redisson.spring.support;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.ClusterRunner;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.RedissonRuntimeEnvironment;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.codec.FstCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.MsgPackJacksonCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class SpringRInjectTest extends BaseTest {

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
        System.setProperty("redisAddress", RedisRunner.getDefaultRedisServerBindAddressAndPort());
        System.setProperty("my.map", "myRandomMap");
        System.setProperty("my.list", "myRandomList");
        System.setProperty("my.set", "myRandomSet");
        System.setProperty("my.cache", "cacheTest");
        context = new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/rinject.xml");
    }
 
    public static void stopContext() throws Exception {
        ((ConfigurableApplicationContext) context).close();
    }

    public static class RInjectRedisson {

        @RInject(name = "mySet", codec = JsonJacksonCodec.class, redissonBeanRef = "myOtherRedisson") // test custom codec, test object creation, test type match, test alternative bean ref
        private RSet set;

        @RInject("@myOtherRedisson") // test bean reference, test value()
        private Redisson redisson;

        @RInject //default redisson instance, test default redisson lookup
        private RedissonClient r1;

        @RInject("#{systemProperties['my.list']}") //test SpEL parsing then object creation
        private List list;

        @RInject("RMapCache('#{systemProperties['my.cache']}')") //test SpEL parsing then Redisson Expression then object creation
        private Map map;

        @RInject("${my.set}") //test property parsing then object creation.
        private Set set1;

        @RInject("RMap('#{ 'myPrefix:' + '${my.map}' }')") //test property parsing then object creation.
        private Map map1;

        public RInjectRedisson() {
        }

        public RSet getSet() {
            return set;
        }

        public void setSet(RSet set) {
            this.set = set;
        }

        public Redisson getRedisson() {
            return redisson;
        }

        public void setRedisson(Redisson redisson) {
            this.redisson = redisson;
        }

        public RedissonClient getR1() {
            return r1;
        }

        public void setR1(RedissonClient r1) {
            this.r1 = r1;
        }

        public List getList() {
            return list;
        }

        public void setList(List list) {
            this.list = list;
        }

        public Map getMap() {
            return map;
        }

        public void setMap(Map map) {
            this.map = map;
        }

        public Set getSet1() {
            return set1;
        }

        public void setSet1(Set set1) {
            this.set1 = set1;
        }

        public Map getMap1() {
            return map1;
        }

        public void setMap1(Map map1) {
            this.map1 = map1;
        }
    }

    @Test
    public void testNamespace() {
        Object bean = context.getBean("myRedisson1");
        assertTrue(bean instanceof Redisson);
        Object bean1 = context.getBean("myOtherRedisson");
        assertTrue(bean1 instanceof Redisson);
    }

    @Test
    public void testRInjectPostProcessorCreation() {
        assertNotNull(context.getBean(RInjectBeanPostProcessor.class));
    }

    @Test
    public void testRInject() {
        RInjectRedisson bean = context.getAutowireCapableBeanFactory().getBean(RInjectRedisson.class);
        assertNotNull(bean.getRedisson());
        assertNotNull(bean.getSet());
        assertNotNull(bean.getSet1());
        assertNotNull(bean.getR1());
        assertNotNull(bean.getList());
        assertNotNull(bean.getMap());
        assertNotNull(bean.getMap1());
        String clientName = bean.getRedisson().getConnectionManager().getEntrySet().iterator().next().getClient().getConfig().getClientName();
        assertEquals("myOtherRedisson", clientName);
        assertEquals("mySet", bean.getSet().getName());
        assertTrue(bean.getList() instanceof RList);
        assertEquals("myRandomList", ((RList)bean.getList()).getName());
        assertTrue(bean.getMap() instanceof RMapCache);
        assertEquals("cacheTest", ((RMapCache)bean.getMap()).getName());
        assertTrue(bean.getSet1() instanceof RSet);
        assertEquals("myRandomSet", ((RSet)bean.getSet1()).getName());
        assertTrue(bean.getMap1() instanceof RMap);
        assertEquals("myPrefix:myRandomMap", ((RMap)bean.getMap1()).getName());
        assertEquals(JsonJacksonCodec.class, bean.getSet().getCodec().getClass());
    }

}
