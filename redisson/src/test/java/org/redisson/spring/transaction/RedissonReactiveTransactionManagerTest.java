package org.redisson.spring.transaction;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.RedisRunner;
import org.redisson.RedissonReactive;
import org.redisson.api.RMap;
import org.redisson.api.RMapReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RedissonReactiveTransactionContextConfig.class)
public class RedissonReactiveTransactionManagerTest {

    @Autowired
    private RedissonReactiveClient redisson;
    
    @Autowired
    private ReactiveTransactionalBean transactionalBean;
    
    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        RedisRunner.startDefaultRedisServerInstance();
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        RedisRunner.shutDownDefaultRedisServerInstance();
    }
    
    @Test
    public void test() {
        transactionalBean.testTransactionIsNotNull().block();
        transactionalBean.testNoTransaction().block();
        
        transactionalBean.testCommit().block();
        RMapReactive<String, String> map1 = redisson.getMap("test1");
        assertThat(map1.get("1").block()).isEqualTo("2");
        
        try {
            transactionalBean.testRollback().block();
            Assert.fail();
        } catch (IllegalStateException e) {
            // skip
        }
        RMapReactive<String, String> map2 = redisson.getMap("test2");
        assertThat(map2.get("1").block()).isNull();

        transactionalBean.testCommitAfterRollback().block();
        assertThat(map2.get("1").block()).isEqualTo("2");

        RMapReactive<String, String> mapTr1 = redisson.getMap("tr1");
        assertThat(mapTr1.get("1").block()).isNull();
        RMapReactive<String, String> mapTr2 = redisson.getMap("tr2");
        assertThat(mapTr2.get("2").block()).isNull();

        transactionalBean.testPropagationRequired().block();
        RMapReactive<String, String> mapTr3 = redisson.getMap("tr3");
        assertThat(mapTr3.get("2").block()).isEqualTo("4");

        try {
            transactionalBean.testPropagationRequiredWithException().block();
            Assert.fail();
        } catch (IllegalStateException e) {
            // skip
        }
        RMapReactive<String, String> mapTr4 = redisson.getMap("tr4");
        assertThat(mapTr4.get("1").block()).isNull();
        RMapReactive<String, String> mapTr5 = redisson.getMap("tr5");
        assertThat(mapTr5.get("2").block()).isNull();

    }

    
}
