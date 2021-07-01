package org.redisson.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.BaseReactiveTest;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RTransactionReactive;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.LongCodec;

public class RedissonTransactionalBucketReactiveTest extends BaseReactiveTest {

    @Test
    public void testLock() {
        redisson.getBucket("e:1", LongCodec.INSTANCE).set(1L).block();
        redisson.getBucket("e:2", LongCodec.INSTANCE).set(1L).block();

        for (int j = 0; j < 10; j++) {
            RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults().timeout(30, TimeUnit.SECONDS));
            RBucketReactive<Long> e1 = transaction.getBucket("e:1", LongCodec.INSTANCE);
            RBucketReactive<Long> e2 = transaction.getBucket("e:2", LongCodec.INSTANCE);
            e1.get().map(i -> i + 1).flatMap(e1::set).block();
            e2.get().map(i -> i - 1).flatMap(e2::set).block();

            transaction.commit().block();
        }

        assertThat(redisson.getBucket("e:1", LongCodec.INSTANCE).get().block()).isEqualTo(11L);
        assertThat(redisson.getBucket("e:2", LongCodec.INSTANCE).get().block()).isEqualTo(-9L);
    }

    @Test
    public void testTimeout() throws InterruptedException {
        RBucketReactive<String> b = redisson.getBucket("test");
        sync(b.set("123"));
        
        RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults().timeout(3, TimeUnit.SECONDS));
        RBucketReactive<String> bucket = transaction.getBucket("test");
        sync(bucket.set("234"));
        
        Thread.sleep(3000);
        
        try {
            sync(transaction.commit());
            Assertions.fail();
        } catch (TransactionException e) {
            // skip
        }
        
        Thread.sleep(1000);
        
        assertThat(sync(b.get())).isEqualTo("123");
    }
    
    @Test
    public void testSet() {
        RBucketReactive<String> b = redisson.getBucket("test");
        sync(b.set("123"));
        
        RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBucketReactive<String> bucket = transaction.getBucket("test");
        sync(bucket.set("234"));
        assertThat(sync(bucket.get())).isEqualTo("234");
        
        sync(transaction.commit());
        
        assertThat(sync(redisson.getKeys().count())).isEqualTo(1);
        assertThat(sync(b.get())).isEqualTo("234");
    }
    
    @Test
    public void testGetAndSet() {
        RBucketReactive<String> b = redisson.getBucket("test");
        sync(b.set("123"));
        
        RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBucketReactive<String> bucket = transaction.getBucket("test");
        assertThat(sync(bucket.getAndSet("0"))).isEqualTo("123");
        assertThat(sync(bucket.get())).isEqualTo("0");
        assertThat(sync(bucket.getAndSet("324"))).isEqualTo("0");
        
        sync(transaction.commit());
        
        assertThat(sync(redisson.getKeys().count())).isEqualTo(1);
        assertThat(sync(b.get())).isEqualTo("324");
    }
    
    @Test
    public void testCompareAndSet() {
        RBucketReactive<String> b = redisson.getBucket("test");
        sync(b.set("123"));
        
        RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBucketReactive<String> bucket = transaction.getBucket("test");
        assertThat(sync(bucket.compareAndSet("0", "434"))).isFalse();
        assertThat(sync(bucket.get())).isEqualTo("123");
        assertThat(sync(bucket.compareAndSet("123", "232"))).isTrue();
        assertThat(sync(bucket.get())).isEqualTo("232");
        
        sync(transaction.commit());
        
        assertThat(sync(redisson.getKeys().count())).isEqualTo(1);
        assertThat(sync(b.get())).isEqualTo("232");
    }
    
    @Test
    public void testTrySet() {
        RBucketReactive<String> b = redisson.getBucket("test");
        sync(b.set("123"));
        
        RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBucketReactive<String> bucket = transaction.getBucket("test");
        assertThat(sync(bucket.trySet("0"))).isFalse();
        assertThat(sync(bucket.delete())).isTrue();
        assertThat(sync(bucket.trySet("324"))).isTrue();
        assertThat(sync(bucket.trySet("43"))).isFalse();
        
        sync(transaction.commit());
        
        assertThat(sync(redisson.getKeys().count())).isEqualTo(1);
        assertThat(sync(b.get())).isEqualTo("324");
    }
    
    @Test
    public void testGetAndRemove() {
        RBucketReactive<String> m = redisson.getBucket("test");
        sync(m.set("123"));
        
        RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBucketReactive<String> set = transaction.getBucket("test");
        assertThat(sync(set.get())).isEqualTo("123");
        assertThat(sync(set.size())).isEqualTo(6);
        assertThat(sync(set.getAndDelete())).isEqualTo("123");
        assertThat(sync(set.size())).isEqualTo(0);
        assertThat(sync(set.get())).isNull();
        assertThat(sync(set.getAndDelete())).isNull();
        
        sync(transaction.commit());
        
        assertThat(sync(redisson.getKeys().count())).isEqualTo(0);
        assertThat(sync(m.get())).isNull();
    }
    
    @Test
    public void testRollback() {
        RBucketReactive<Object> b = redisson.getBucket("test");
        sync(b.set("1234"));
        
        RTransactionReactive transaction = redisson.createTransaction(TransactionOptions.defaults());
        RBucketReactive<Object> bucket = transaction.getBucket("test");
        assertThat(sync(bucket.get())).isEqualTo("1234");
        assertThat(sync(bucket.getAndDelete())).isEqualTo("1234");
        
        assertThat(sync(b.get())).isEqualTo("1234");
        
        sync(transaction.rollback());
        
        assertThat(sync(redisson.getKeys().count())).isEqualTo(1);
        
        assertThat(sync(b.get())).isEqualTo("1234");
    }

    
}
