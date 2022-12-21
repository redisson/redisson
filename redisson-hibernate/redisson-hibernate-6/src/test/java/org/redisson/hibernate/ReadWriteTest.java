package org.redisson.hibernate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.stat.Statistics;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
        cfg.setProperty(Environment.URL, "jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1;");
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
        sessionFactory().getCache().evictAllRegions();
        sessionFactory().getStatistics().clear();
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
        Query<ItemReadWrite> query = s.getNamedQuery("testQuery");
        query.setCacheable(true);
        query.setCacheRegion("myTestQuery");
        query.setParameter("name", "data");
        item = query.uniqueResult();
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getDomainDataRegionStatistics("myTestQuery").getPutCount());

        s = openSession();
        s.beginTransaction();
        Query<ItemReadWrite> query2 = s.getNamedQuery("testQuery");
        query2.setCacheable(true);
        query2.setCacheRegion("myTestQuery");
        query2.setParameter("name", "data");
        item = query2.uniqueResult();
        s.delete(item);
        s.getTransaction().commit();
        s.close();
        
        Assert.assertEquals(1, stats.getDomainDataRegionStatistics("myTestQuery").getHitCount());
        
        stats.logSummary();
    }
        
}
