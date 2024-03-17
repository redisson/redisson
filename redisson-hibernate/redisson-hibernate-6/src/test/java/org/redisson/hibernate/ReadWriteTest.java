package org.redisson.hibernate;

import org.hibernate.Session;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.Query;
import org.hibernate.stat.Statistics;
import org.hibernate.testing.orm.junit.BaseSessionFactoryFunctionalTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@Testcontainers
public class ReadWriteTest extends BaseSessionFactoryFunctionalTest {

    @Container
    public static final GenericContainer H2 = new FixedHostPortGenericContainer("oscarfonts/h2:latest")
            .withFixedExposedPort(1521, 1521);

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
                                                .withFixedExposedPort(6379, 6379);

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class[] { ItemReadWrite.class};
    }

    @Override
    protected void applySettings(StandardServiceRegistryBuilder builder) {
        builder.applySetting("hibernate.cache.redisson.item.eviction.max_entries", "100");
        builder.applySetting("hibernate.cache.redisson.item.expiration.time_to_live", "1500");
        builder.applySetting("hibernate.cache.redisson.item.expiration.max_idle_time", "1000");
    }
    
    @BeforeEach
    public void before() {
        sessionFactory().getCache().evictAllRegions();
        sessionFactory().getStatistics().clear();
    }

    @Test
    public void testQuery() {
        Statistics stats = sessionFactory().getStatistics();

        Session s = sessionFactory().openSession();
        s.beginTransaction();
        ItemReadWrite item = new ItemReadWrite("data");
        item.getEntries().addAll(Arrays.asList("a", "b", "c"));
        s.save(item);
        s.flush();
        s.getTransaction().commit();
        
        s = sessionFactory().openSession();
        s.beginTransaction();
        Query<ItemReadWrite> query = s.getNamedQuery("testQuery");
        query.setCacheable(true);
        query.setCacheRegion("myTestQuery");
        query.setParameter("name", "data");
        item = query.uniqueResult();
        s.getTransaction().commit();
        s.close();
        
        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("myTestQuery").getPutCount());

        s = sessionFactory().openSession();
        s.beginTransaction();
        Query<ItemReadWrite> query2 = s.getNamedQuery("testQuery");
        query2.setCacheable(true);
        query2.setCacheRegion("myTestQuery");
        query2.setParameter("name", "data");
        item = query2.uniqueResult();
        s.delete(item);
        s.getTransaction().commit();
        s.close();

        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("myTestQuery").getHitCount());
        
        stats.logSummary();
    }
        
}
