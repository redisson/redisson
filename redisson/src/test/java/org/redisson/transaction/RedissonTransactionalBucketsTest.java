package org.redisson.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.api.RBucket;
import org.redisson.api.RBuckets;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;

public class RedissonTransactionalBucketsTest extends BaseTest {

    @Test
    public void testGet() {
        RBucket<String> b = redisson.getBucket("test");
        b.set("123");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBuckets buckets = transaction.getBuckets();
        assertThat(buckets.get("test").get("test")).isEqualTo("123");
        
        transaction.commit();
        
        assertThat(redisson.getKeys().count()).isEqualTo(1);
        assertThat(b.get()).isEqualTo("123");
    }
    
    @Test
    public void testSet() {
        RBucket<String> b1 = redisson.getBucket("test1");
        b1.set("1");
        RBucket<String> b2 = redisson.getBucket("test2");
        b2.set("2");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBuckets buckets = transaction.getBuckets();
        Map<String, Object> bbs = new LinkedHashMap<>();
        bbs.put("test1", "11");
        bbs.put("test2", "22");
        buckets.set(bbs);
        
        Map<String, Object> newBuckets = buckets.get("test1", "test2");
        assertThat(newBuckets).isEqualTo(bbs);
        
        transaction.commit();
        
        assertThat(redisson.getBuckets().get("test1", "test2")).isEqualTo(bbs);
        assertThat(redisson.getKeys().count()).isEqualTo(2);
    }

    @Test
    public void testTrySet() {
        redisson.getBucket("test1").set("1");
        redisson.getBucket("test2").set("2");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBuckets buckets = transaction.getBuckets();
        Map<String, Object> bbs1 = new LinkedHashMap<>();
        bbs1.put("test1", "10");
        bbs1.put("test2", "20");
        assertThat(buckets.trySet(bbs1)).isFalse();
        assertThat(buckets.delete("test1", "test2")).isEqualTo(2);
        Map<String, Object> bbs2 = new LinkedHashMap<>();
        bbs2.put("test1", "11");
        bbs2.put("test2", "22");
        assertThat(buckets.trySet(bbs2)).isTrue();
        Map<String, Object> bbs3 = new LinkedHashMap<>();
        bbs3.put("test1", "13");
        bbs3.put("test2", "23");
        assertThat(buckets.trySet(bbs3)).isFalse();
        
        System.out.println("commit " + Thread.currentThread().getId());
        transaction.commit();
        redisson.getKeys().getKeys().forEach(x -> System.out.println(x));
        
//        assertThat(redisson.getBuckets().get("test1", "test2")).isEqualTo(bbs2);
        assertThat(redisson.getKeys().count()).isEqualTo(2);
    }
    
}
