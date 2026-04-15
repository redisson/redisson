package org.redisson.transaction;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.RedissonListMultimapCache;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;
import org.redisson.api.map.MapLoader;
import org.redisson.api.options.LocalCachedMapOptions;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.SnappyCodecV2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonTransactionalLocalCachedMapTest extends RedisDockerTest {

    @Test
    public void testRemoval() {
        Map<String, String> externalStore = new HashMap<>();
        externalStore.put("k1", "v1");

        org.redisson.api.options.LocalCachedMapOptions<String, String> opts = org.redisson.api.options.LocalCachedMapOptions
                .<String, String>name("test").loader(new MapLoader<>() {
                    @Override
                    public String load(String key) {
                        return externalStore.get(key);
                    }

                    @Override
                    public Iterable<String> loadAllKeys() {
                        return externalStore.keySet();
                    }
                });

        RLocalCachedMap<String, String> lcMap = redisson.getLocalCachedMap(opts);

        RTransaction tx = redisson.createTransaction(TransactionOptions.defaults());
        RLocalCachedMap<String, String> txMap = tx.getLocalCachedMap(lcMap);

        txMap.fastRemove("k1");

        tx.commit();
    }

    @Test
    public void testDisabledKeys() {
        new MockUp<RedissonListMultimapCache>() {
            @Mock
            void removeAllAsync(Invocation invocation, Object key) {
                // skip
            }
        };

        CompositeCodec CODEC = new CompositeCodec(new StringCodec(), new SnappyCodecV2());;
        RLocalCachedMap<String, String> localCachedMap = redisson.getLocalCachedMap(
                org.redisson.api.options.LocalCachedMapOptions.<String, String>name("test1").codec(CODEC));

        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());

        RLocalCachedMap<String, String> transactionLocalCachedMap = transaction.getLocalCachedMap(
                localCachedMap);
        transactionLocalCachedMap.put("1", "1");
        transaction.commit();

        RLocalCachedMap < Object, Object > localCachedMap2 = redisson.getLocalCachedMap(
                org.redisson.api.options.LocalCachedMapOptions.name("test1").codec(CODEC));
    }

    @Test
    public void testPut() {
        RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap(LocalCachedMapOptions.name("test"));
        m1.put("1", "2");
        m1.put("3", "4");
        
        RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap(LocalCachedMapOptions.name("test"));
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
        RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap(LocalCachedMapOptions.name("test"));
        m1.put("1", "2");
        m1.put("3", "4");
        
        RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap(LocalCachedMapOptions.name("test"));
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
        RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap(LocalCachedMapOptions.name("test"));
        m1.put("1", "2");
        m1.put("3", "4");
        
        RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap(LocalCachedMapOptions.name("test"));
        m2.get("1");
        m2.get("3");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = transaction.getLocalCachedMap(m1);
        assertThat(map.get("1")).isEqualTo("2");
        assertThat(map.remove("3")).isEqualTo("4");
        
        assertThat(m1.get("3")).isEqualTo("4");
        
        transaction.rollback();

        redisson.getKeys().deleteByPattern("*redisson_unlock_latch*");
        assertThat(redisson.getKeys().count()).isEqualTo(1);
        
        assertThat(m1.get("1")).isEqualTo("2");
        assertThat(m1.get("3")).isEqualTo("4");
        assertThat(m2.get("1")).isEqualTo("2");
        assertThat(m2.get("3")).isEqualTo("4");
    }

    @Test
    public void testPut2Maps() {
        RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap(LocalCachedMapOptions.name("test1"));
        RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap(LocalCachedMapOptions.name("test2"));

        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> tMap1 = transaction.getLocalCachedMap(m1);
        RMap<String, String> tMap2 = transaction.getLocalCachedMap(m2);
        tMap1.put("1", "2");
        tMap2.put("3", "4");

        transaction.commit();

        assertThat(m1.get("1")).isEqualTo("2");
        assertThat(m2.get("3")).isEqualTo("4");
    }
    
    @Test
    public void testPutWithInCluster() {
        testInCluster(redisson -> {
            LocalCachedMapOptions<String, String> opts = LocalCachedMapOptions
                    .<String, String>name("test")
                    .syncStrategy(LocalCachedMapOptions.SyncStrategy.UPDATE);
            
            RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap(opts);
            m1.put("1", "1");
            
            RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap(opts);
            m2.get("1");
            
            RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
            RLocalCachedMap<String, String> transactionMap = transaction.getLocalCachedMap(m1);
            transactionMap.put("1", "2");
            
            assertThat(m1.get("1")).isEqualTo("1");
            assertThat(m2.get("1")).isEqualTo("1");
            
            transaction.commit();
            
            assertThat(m1.get("1")).isEqualTo("2");
            assertThat(m2.get("1")).isEqualTo("2");
        });
    }
    
    @Test
    public void testPutAsyncWithInCluster() {
        testInCluster(redisson -> {
            LocalCachedMapOptions<String, String> opts = LocalCachedMapOptions
                    .<String, String>name("test")
                    .syncStrategy(LocalCachedMapOptions.SyncStrategy.UPDATE);
            
            RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap(opts);
            m1.put("1", "1");
            
            RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap(opts);
            m2.get("1");
            
            RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
            RLocalCachedMap<String, String> transactionMap = transaction.getLocalCachedMap(m1);
            transactionMap.put("1", "2");
            
            assertThat(m1.get("1")).isEqualTo("1");
            assertThat(m2.get("1")).isEqualTo("1");
            
            try {
                transaction.commitAsync().get();
            } catch (ExecutionException | InterruptedException e) {
                // skip
            }
            
            assertThat(m1.get("1")).isEqualTo("2");
            assertThat(m2.get("1")).isEqualTo("2");
        });
    }
    
    @Test
    public void testPutWithUpdateSyncStrategy() {
        LocalCachedMapOptions<String, String> opts = LocalCachedMapOptions
                .<String, String>name("test")
                .useTopicPattern(true)
                .syncStrategy(LocalCachedMapOptions.SyncStrategy.UPDATE);
        
        RLocalCachedMap<String, String> m1 = redisson.getLocalCachedMap(opts);
        m1.put("1", "1");
        
        RLocalCachedMap<String, String> m2 = redisson.getLocalCachedMap(opts);
        m2.get("1");
        
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RLocalCachedMap<String, String> transactionMap = transaction.getLocalCachedMap(m1);
        transactionMap.put("1", "2");
        
        assertThat(m1.get("1")).isEqualTo("1");
        assertThat(m2.get("1")).isEqualTo("1");
        
        transaction.commit();
        
        assertThat(m1.get("1")).isEqualTo("2");
        assertThat(m2.get("1")).isEqualTo("2");
    }
}
