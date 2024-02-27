package org.redisson.transaction;

import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.api.RSetCache;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonTransactionalSetCacheTest extends RedisDockerTest {

    @Test
    public void testRemoveAll() {
        RSetCache<String> s = redisson.getSetCache("test");
        s.add("1");
        s.add("3");
        
        RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
        RSetCache<String> set = t.getSetCache("test");
        Set<String> putSet = new HashSet<String>();
        putSet.add("4");
        putSet.add("3");
        set.removeAll(putSet);
        assertThat(s).containsOnly("1", "3");
        assertThat(set).containsOnly("1");
        
        t.commit();
        
        assertThat(s).containsOnly("1");
    }
    
    @Test
    public void testPutAll() {
        RSetCache<String> s = redisson.getSetCache("test");
        s.add("1");
        s.add("3");
        
        RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
        RSetCache<String> set = t.getSetCache("test");
        Set<String> putSet = new HashSet<String>();
        putSet.add("4");
        putSet.add("6");
        set.addAll(putSet);
        assertThat(s).containsOnly("1", "3");
        assertThat(set).containsOnly("1", "3", "4", "6");
        
        t.commit();
        
        assertThat(s).containsOnly("1", "3", "4", "6");
    }
    
    @Test
    public void testKeySet() {
        RSetCache<String> s = redisson.getSetCache("test");
        s.add("1");
        s.add("3");
        
        RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
        RSetCache<String> set = t.getSetCache("test");
        set.remove("3");
        assertThat(set).containsOnly("1");
        
        assertThat(s).containsOnly("1", "3");
    }

    @Test
    public void testAdd() {
        RSetCache<String> s = redisson.getSetCache("test");
        s.add("1");
        s.add("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RSetCache<String> set = transaction.getSetCache("test");
        assertThat(set.add("4")).isTrue();
        assertThat(set.add("3")).isFalse();
        assertThat(set.contains("4")).isTrue();
        
        assertThat(s.contains("4")).isFalse();
        
        transaction.commit();
        
        assertThat(s.size()).isEqualTo(3);
        assertThat(s.contains("1")).isTrue();
        assertThat(s.contains("3")).isTrue();
        assertThat(s.contains("4")).isTrue();
    }

    @Test
    public void testAddTTL() throws InterruptedException {
        RSetCache<String> s = redisson.getSetCache("test");
        s.add("1");
        s.add("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RSetCache<String> set = transaction.getSetCache("test");
        assertThat(set.add("4", 2, TimeUnit.SECONDS)).isTrue();
        assertThat(set.add("3")).isFalse();
        assertThat(set.contains("4")).isTrue();
        
        assertThat(s.contains("4")).isFalse();
        
        transaction.commit();
        
        assertThat(s.size()).isEqualTo(3);
        assertThat(s.contains("1")).isTrue();
        assertThat(s.contains("3")).isTrue();
        assertThat(s.contains("4")).isTrue();
        
        Thread.sleep(2000);
        
        assertThat(s.contains("4")).isFalse();
    }

    
    @Test
    public void testRemove() {
        RSetCache<String> s = redisson.getSetCache("test");
        s.add("1");
        s.add("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RSetCache<String> set = transaction.getSetCache("test");
        assertThat(set.contains("1")).isTrue();
        assertThat(set.remove("3")).isTrue();
        assertThat(set.remove("3")).isFalse();
        assertThat(set.remove("3")).isFalse();
        
        assertThat(s.contains("3")).isTrue();
        
        transaction.commit();
        
        assertThat(s.size()).isEqualTo(1);
        assertThat(s.contains("1")).isTrue();
        assertThat(s.contains("3")).isFalse();
    }

    
}
