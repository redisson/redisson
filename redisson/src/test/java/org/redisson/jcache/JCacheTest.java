package org.redisson.jcache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.Caching;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.AbstractBaseTest;

public class JCacheTest extends AbstractBaseTest {
    
    @Test
    public void test() throws InterruptedException, IllegalArgumentException, URISyntaxException {
        MutableConfiguration<String, String> config = new MutableConfiguration<>();
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, 1)));
        config.setStoreByValue(true);
        
        Cache<String, String> cache = Caching.getCachingProvider().getCacheManager(getClass().getResource("redisson-jcache.json").toURI(), null)
                .createCache("test", config);

        CountDownLatch latch = new CountDownLatch(1);
        
        String key = "123";
        ExpiredListener clientListener = new ExpiredListener(latch, key, "90");
        MutableCacheEntryListenerConfiguration<String, String> listenerConfiguration = 
                new MutableCacheEntryListenerConfiguration<String, String>(FactoryBuilder.factoryOf(clientListener), null, true, true);
        cache.registerCacheEntryListener(listenerConfiguration);

        cache.put(key, "90");
        Assert.assertNotNull(cache.get(key));
        
        latch.await();
        
        
        Assert.assertNull(cache.get(key));
    }
    
    public static class ExpiredListener implements CacheEntryExpiredListener<String, String>, Serializable {

        private Object key;
        private Object value;
        private CountDownLatch latch;
        
        public ExpiredListener(CountDownLatch latch, Object key, Object value) {
            super();
            this.latch = latch;
            this.key = key;
            this.value = value;
        }

        @Override
        public void onExpired(Iterable<CacheEntryEvent<? extends String, ? extends String>> events)
                throws CacheEntryListenerException {
            CacheEntryEvent<? extends String, ? extends String> entry = events.iterator().next();
            
            assertThat(entry.getKey()).isEqualTo(key);
            assertThat(entry.getValue()).isEqualTo(value);
            latch.countDown();
        }
    }
}
