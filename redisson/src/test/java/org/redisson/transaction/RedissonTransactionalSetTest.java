package org.redisson.transaction;

import org.junit.jupiter.api.Test;
import org.redisson.BaseTest;
import org.redisson.api.RSet;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonTransactionalSetTest extends BaseTest {

    @Test
    public void testRemoveAll() {
        RSet<String> s = redisson.getSet("test");
        s.add("1");
        s.add("3");
        
        RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
        RSet<String> set = t.getSet("test");
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
        RSet<String> s = redisson.getSet("test");
        s.add("1");
        s.add("3");
        
        RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
        RSet<String> set = t.getSet("test");
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
        RSet<String> s = redisson.getSet("test");
        s.add("1");
        s.add("3");
        
        RTransaction t = redisson.createTransaction(TransactionOptions.defaults());
        RSet<String> set = t.getSet("test");
        set.remove("3");
        assertThat(set).containsOnly("1");
        
        assertThat(s).containsOnly("1", "3");
    }

    @Test
    public void testAdd() {
        RSet<String> s = redisson.getSet("test");
        s.add("1");
        s.add("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RSet<String> set = transaction.getSet("test");
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
    public void testRemove() {
        RSet<String> s = redisson.getSet("test");
        s.add("1");
        s.add("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RSet<String> set = transaction.getSet("test");
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

    @Test
    public void testExpire() throws InterruptedException {
        RSet<String> s = redisson.getSet("test");
        s.add("123");

        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RSet<String> set = transaction.getSet("test");
        assertThat(set.clearExpire()).isFalse();
        assertThat(set.expire(Duration.ofSeconds(2))).isTrue();
        assertThat(set.clearExpire()).isTrue();
        transaction.commit();

        Thread.sleep(2200);

        assertThat(s).containsOnly("123");

        RTransaction transaction2 = redisson.createTransaction(TransactionOptions.defaults());
        RSet<String> set2 = transaction2.getSet("test");
        assertThat(set2.expire(Duration.ofSeconds(1))).isTrue();
        transaction2.commit();

        Thread.sleep(1100);
        assertThat(s.isExists()).isFalse();
    }
    
}
