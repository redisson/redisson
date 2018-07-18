package org.redisson.spring.transaction;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.RedisRunner;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.TransactionSuspensionNotSupportedException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RedissonTransactionContextConfig.class)
public class RedissonTransactionManagerTest {

    @Autowired
    private RedissonClient redisson;
    
    @Autowired
    private TransactionalBean transactionalBean;
    
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
        transactionalBean.testTransactionIsNotNull();
        transactionalBean.testNoTransaction();
        
        transactionalBean.testCommit();
        RMap<String, String> map1 = redisson.getMap("test1");
        assertThat(map1.get("1")).isEqualTo("2");
        
        try {
            transactionalBean.testRollback();
            Assert.fail();
        } catch (IllegalStateException e) {
            // skip
        }
        RMap<String, String> map2 = redisson.getMap("test2");
        assertThat(map2.get("1")).isNull();
        
        transactionalBean.testCommitAfterRollback();
        assertThat(map2.get("1")).isEqualTo("2");
        
        try {
            transactionalBean.testNestedNewTransaction();
            Assert.fail();
        } catch (TransactionSuspensionNotSupportedException e) {
            // skip
        }
        RMap<String, String> mapTr1 = redisson.getMap("tr1");
        assertThat(mapTr1.get("1")).isNull();
        RMap<String, String> mapTr2 = redisson.getMap("tr2");
        assertThat(mapTr2.get("2")).isNull();
        
        transactionalBean.testPropagationRequired();
        RMap<String, String> mapTr3 = redisson.getMap("tr3");
        assertThat(mapTr3.get("2")).isEqualTo("4");

        try {
            transactionalBean.testPropagationRequiredWithException();
            Assert.fail();
        } catch (IllegalStateException e) {
            // skip
        }
        RMap<String, String> mapTr4 = redisson.getMap("tr4");
        assertThat(mapTr4.get("1")).isNull();
        RMap<String, String> mapTr5 = redisson.getMap("tr5");
        assertThat(mapTr5.get("2")).isNull();

    }

    
}
