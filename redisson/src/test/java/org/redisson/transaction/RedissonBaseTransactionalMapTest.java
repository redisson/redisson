package org.redisson.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;

public abstract class RedissonBaseTransactionalMapTest extends BaseTest {

    protected abstract RMap<String, String> getMap();
    
    protected abstract RMap<String, String> getTransactionalMap(RTransaction transaction);
    
    @Test
    public void testFastPut() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2000);
        for (int i = 0; i < 2000; i++) {
            executor.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
                    RMap<String, String> map = getTransactionalMap(t);
                    map.fastPut("1", "1");
                    t.commit();
                }
            });
        }
        
        executor.shutdown();
        assertThat(executor.awaitTermination(2, TimeUnit.MINUTES)).isTrue();
    }
    
    
    @Test
    public void testPutAll() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(t);
        Map<String, String> putMap = new HashMap<String, String>();
        putMap.put("4", "5");
        putMap.put("6", "7");
        map.putAll(putMap);
        assertThat(m.keySet()).containsOnly("1", "3");
        
        t.commit();
        
        assertThat(m.keySet()).containsOnly("1", "3", "4", "6");
    }

    @Test
    public void testKeySet() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(t);
        map.remove("3");
        assertThat(map.keySet()).containsOnly("1");
        
        assertThat(m.keySet()).containsOnly("1", "3");
    }
    
    @Test
    public void testReplace2() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(transaction);
        assertThat(map.replace("3", "4", "10")).isTrue();
        assertThat(map.replace("1", "1", "3")).isFalse();
        assertThat(map.replace("3", "10", "11")).isTrue();
        
        assertThat(m.get("3")).isEqualTo("4");
        assertThat(m.get("1")).isEqualTo("2");
        assertThat(m.size()).isEqualTo(2);
        
        transaction.commit();
        
        assertThat(m.size()).isEqualTo(2);
        assertThat(m.get("3")).isEqualTo("11");
        assertThat(m.get("1")).isEqualTo("2");
    }
    
    @Test
    public void testReplace() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(transaction);
        assertThat(map.replace("3", "10")).isEqualTo("4");
        assertThat(map.replace("5", "0")).isNull();
        
        assertThat(m.get("3")).isEqualTo("4");
        assertThat(m.size()).isEqualTo(2);
        
        transaction.commit();
        
        assertThat(m.size()).isEqualTo(2);
        assertThat(m.get("3")).isEqualTo("10");
        assertThat(m.get("5")).isNull();
        
        RTransaction transaction2 = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map2 = getTransactionalMap(transaction2);
        assertThat(map2.replace("3", "20")).isEqualTo("10");
        assertThat(map2.replace("3", "30")).isEqualTo("20");
        
        assertThat(m.get("3")).isEqualTo("10");
        assertThat(m.size()).isEqualTo(2);
        
        transaction2.commit();
        
        assertThat(m.get("3")).isEqualTo("30");
    }
    
    @Test
    public void testPutIfAbsent() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(transaction);
        assertThat(map.putIfAbsent("3", "2")).isEqualTo("4");
        assertThat(map.putIfAbsent("5", "6")).isNull();
        assertThat(map.putIfAbsent("5", "7")).isEqualTo("6");
        
        assertThat(m.get("3")).isEqualTo("4");
        assertThat(m.size()).isEqualTo(2);
        
        transaction.commit();
        
        assertThat(m.get("1")).isEqualTo("2");
        assertThat(m.get("3")).isEqualTo("4");
        assertThat(m.get("5")).isEqualTo("6");
    }

    @Test
    public void testPutIfAbsentRemove() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(transaction);
        assertThat(map.putIfAbsent("3", "2")).isEqualTo("4");
        assertThat(map.putIfAbsent("5", "6")).isNull();
        assertThat(map.putIfAbsent("5", "7")).isEqualTo("6");
        assertThat(map.remove("5")).isEqualTo("6");
        assertThat(map.putIfAbsent("5", "8")).isNull();
        
        assertThat(m.get("3")).isEqualTo("4");
        assertThat(m.size()).isEqualTo(2);
        
        transaction.commit();
        
        assertThat(m.get("1")).isEqualTo("2");
        assertThat(m.get("3")).isEqualTo("4");
        assertThat(m.get("5")).isEqualTo("8");
    }

    
    @Test
    public void testRemove() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(transaction);
        assertThat(map.get("1")).isEqualTo("2");
        assertThat(map.remove("3")).isEqualTo("4");
        assertThat(map.remove("3")).isNull();
        assertThat(map.remove("3")).isNull();
        
        assertThat(m.get("3")).isEqualTo("4");
        
        transaction.commit();
        
        assertThat(m.get("1")).isEqualTo("2");
        assertThat(m.get("3")).isNull();
    }
    
    @Test
    public void testPut() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(transaction);
        assertThat(map.put("3", "5")).isEqualTo("4");
        assertThat(map.get("3")).isEqualTo("5");
        
        assertThat(m.get("3")).isEqualTo("4");
        
        transaction.commit();
        
        assertThat(m.get("1")).isEqualTo("2");
        assertThat(m.get("3")).isEqualTo("5");
    }
    
    @Test
    public void testPutRemove() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(transaction);
        assertThat(map.get("1")).isEqualTo("2");
        assertThat(map.remove("3")).isEqualTo("4");
        assertThat(map.put("3", "5")).isNull();
        assertThat(map.get("3")).isEqualTo("5");
        
        assertThat(m.get("3")).isEqualTo("4");
        
        transaction.commit();
        
        assertThat(m.get("1")).isEqualTo("2");
        assertThat(m.get("3")).isEqualTo("5");
    }
    
    @Test
    public void testRollback() {
        RMap<String, String> m = getMap();
        m.put("1", "2");
        m.put("3", "4");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = getTransactionalMap(transaction);
        assertThat(map.get("1")).isEqualTo("2");
        assertThat(map.remove("3")).isEqualTo("4");
        
        assertThat(m.get("3")).isEqualTo("4");
        
        transaction.rollback();
        
        assertThat(redisson.getKeys().count()).isEqualTo(1);
        
        assertThat(m.get("1")).isEqualTo("2");
        assertThat(m.get("3")).isEqualTo("4");
    }

    
}
