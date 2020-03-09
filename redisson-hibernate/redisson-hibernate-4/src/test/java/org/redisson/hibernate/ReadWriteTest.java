package org.redisson.hibernate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.stat.Statistics;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.hibernate.RedissonRegionFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ReadWriteTest extends BaseCoreFunctionalTestCase {

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class[] { ItemReadWrite.class};
    }

    @Override
    protected void configure(Configuration cfg) {
        super.configure(cfg);
        cfg.setProperty(Environment.DRIVER, org.h2.Driver.class.getName());
        cfg.setProperty(Environment.URL, "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;MVCC=TRUE");
        cfg.setProperty(Environment.USER, "sa");
        cfg.setProperty(Environment.PASS, "");
        cfg.setProperty(Environment.CACHE_REGION_PREFIX, "");
        cfg.setProperty(Environment.GENERATE_STATISTICS, "true");

        cfg.setProperty(Environment.USE_SECOND_LEVEL_CACHE, "true");
        cfg.setProperty(Environment.USE_QUERY_CACHE, "true");
        cfg.setProperty(Environment.CACHE_REGION_FACTORY, RedissonRegionFactory.class.getName());
        
        cfg.setProperty("hibernate.cache.redisson.item.eviction.max_entries", "100");
        cfg.setProperty("hibernate.cache.redisson.item.expiration.time_to_live", "1500");
        cfg.setProperty("hibernate.cache.redisson.item.expiration.max_idle_time", "1000");
    }
    
    @Before
    public void before() {
        sessionFactory().getCache().evictEntityRegions();
        sessionFactory().getStatistics().clear();
    }

    @Test
    public void testTimeToLive() throws InterruptedException {
        Statistics stats = sessionFactory().getStatistics();
        
        Long id = null;
        Session s = openSession();
        s.beginTransaction();
        ItemReadWrite item = new ItemReadWrite( "data" );
        id = (Long) s.save( item );
        s.flush();
        s.getTransaction().commit();
        s.close();
        
        Thread.sleep(900);
        
        s = openSession();
        s.beginTransaction();
        item = (ItemReadWrite) s.get(ItemReadWrite.class, id);
        Assert.assertEquals("data", item.getName());
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item").getHitCount());
        Assert.assertEquals(0, stats.getSecondLevelCacheStatistics("item").getMissCount());

        Thread.sleep(600);
        
        s = openSession();
        s.beginTransaction();
        item = (ItemReadWrite) s.get(ItemReadWrite.class, id);
        Assert.assertEquals("data", item.getName());
        s.delete(item);
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item").getHitCount());
        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item").getMissCount());
    }
    
    @Test
    public void testQuery() {
        Statistics stats = sessionFactory().getStatistics();

        Session s = openSession();
        s.beginTransaction();
        ItemReadWrite item = new ItemReadWrite("data");
        item.getEntries().addAll(Arrays.asList("a", "b", "c"));
        s.save(item);
        s.flush();
        s.getTransaction().commit();
        
        s = openSession();
        s.beginTransaction();
        Query query = s.getNamedQuery("testQuery");
        query.setCacheable(true);
        query.setCacheRegion("myTestQuery");
        query.setParameter("name", "data");
        item = (ItemReadWrite) query.uniqueResult();
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("myTestQuery").getPutCount());

        s = openSession();
        s.beginTransaction();
        Query query2 = s.getNamedQuery("testQuery");
        query2.setCacheable(true);
        query2.setCacheRegion("myTestQuery");
        query2.setParameter("name", "data");
        item = (ItemReadWrite) query2.uniqueResult();
        s.delete(item);
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("myTestQuery").getHitCount());
        
        stats.logSummary();
        
    }
    
    @Test
    public void testCollection() {
        Long id = null;
        
        Statistics stats = sessionFactory().getStatistics();
        Session s = openSession();
        s.beginTransaction();
        ItemReadWrite item = new ItemReadWrite("data");
        item.getEntries().addAll(Arrays.asList("a", "b", "c"));
        id = (Long) s.save(item);
        s.flush();
        s.getTransaction().commit();

        s = openSession();
        s.beginTransaction();
        item = (ItemReadWrite) s.get(ItemReadWrite.class, id);
        assertThat(item.getEntries()).containsExactly("a", "b", "c");
        s.getTransaction().commit();
        s.close();

        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item_entries").getPutCount());
        
        s = openSession();
        s.beginTransaction();
        item = (ItemReadWrite) s.get(ItemReadWrite.class, id);
        assertThat(item.getEntries()).containsExactly("a", "b", "c");
        s.delete(item);
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item_entries").getHitCount());
    }
    
    @Test
    public void testNaturalId() {
        Statistics stats = sessionFactory().getStatistics();
        Session s = openSession();
        s.beginTransaction();
        ItemReadWrite item = new ItemReadWrite("data");
        item.setNid("123");
        s.save(item);
        s.flush();
        s.getTransaction().commit();

        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item").getPutCount());
        Assert.assertEquals(1, stats.getNaturalIdCacheStatistics("item##NaturalId").getPutCount());
        
        s = openSession();
        s.beginTransaction();
        item = (ItemReadWrite) s.bySimpleNaturalId(ItemReadWrite.class).load("123");
        assertThat(item).isNotNull();
        s.delete(item);
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item").getHitCount());
        Assert.assertEquals(1, stats.getNaturalIdCacheStatistics("item##NaturalId").getHitCount());

        sessionFactory().getStatistics().logSummary();
    }
    
    @Test
    public void testUpdateWithRefreshThenRollback() {
        Statistics stats = sessionFactory().getStatistics();
        Long id = null;
        Session s = openSession();
        s.beginTransaction();
        ItemReadWrite item = new ItemReadWrite( "data" );
        id = (Long) s.save( item );
        s.flush();
        s.getTransaction().commit();

        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item").getPutCount());

        s = openSession();
        s.beginTransaction();
        item = (ItemReadWrite) s.get(ItemReadWrite.class, id);
        item.setName("newdata");
        s.update(item);
        s.flush();
        s.refresh(item);
        s.getTransaction().rollback();
        s.clear();
        s.close();

        s = openSession();
        s.beginTransaction();
        item = (ItemReadWrite) s.get(ItemReadWrite.class, id);
        Assert.assertEquals("data", item.getName());
        s.delete(item);
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("item").getHitCount());
    }

    
}
