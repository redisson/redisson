package org.redisson.transaction;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.redisson.BaseTest;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;

public class RedissonTransactionalLocalCachedMapTest extends BaseTest {

    @Test
    public void testPut() throws InterruptedException {
        RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        m1.put("1", "2");
        m1.put("3", "4");
        
        RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        m2.get("1");
        m2.get("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = transaction.getLocalCachedMap(m1);
        assertThat(map.put("3", "5")).isEqualTo("4");
        assertThat(map.get("3")).isEqualTo("5");
        
        assertThat(m1.get("3")).isEqualTo("4");
        assertThat(m2.get("3")).isEqualTo("4");
        
        transaction.commit();
        
        assertThat(m1.get("3")).isEqualTo("5");
        assertThat(m2.get("3")).isEqualTo("5");
    }
    
    @Test
    public void testPutRemove() {
        RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        m1.put("1", "2");
        m1.put("3", "4");
        
        RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        m2.get("1");
        m2.get("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = transaction.getLocalCachedMap(m1);
        assertThat(map.get("1")).isEqualTo("2");
        assertThat(map.remove("3")).isEqualTo("4");
        assertThat(map.put("3", "5")).isNull();
        assertThat(map.get("3")).isEqualTo("5");
        
        assertThat(m1.get("3")).isEqualTo("4");
        assertThat(m2.get("3")).isEqualTo("4");
        
        transaction.commit();
        
        assertThat(m1.get("1")).isEqualTo("2");
        assertThat(m1.get("3")).isEqualTo("5");
        assertThat(m2.get("1")).isEqualTo("2");
        assertThat(m2.get("3")).isEqualTo("5");
    }
    
    @Test
    public void testRollback() {
        RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        m1.put("1", "2");
        m1.put("3", "4");
        
        RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        m2.get("1");
        m2.get("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = transaction.getLocalCachedMap(m1);
        assertThat(map.get("1")).isEqualTo("2");
        assertThat(map.remove("3")).isEqualTo("4");
        
        assertThat(m1.get("3")).isEqualTo("4");
        
        transaction.rollback();
        
        assertThat(redisson.getKeys().count()).isEqualTo(1);
        
        assertThat(m1.get("1")).isEqualTo("2");
        assertThat(m1.get("3")).isEqualTo("4");
        assertThat(m2.get("1")).isEqualTo("2");
        assertThat(m2.get("3")).isEqualTo("4");
    }

    
}
