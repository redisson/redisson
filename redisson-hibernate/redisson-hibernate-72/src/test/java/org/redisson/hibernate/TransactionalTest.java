package org.redisson.hibernate;

import org.hibernate.Session;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@Testcontainers
public class TransactionalTest extends BaseSessionFactoryFunctionalTest {

    @Container
    public static final GenericContainer H2 = new FixedHostPortGenericContainer("oscarfonts/h2:latest")
                                                .withFixedExposedPort(1521, 1521);

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
                                                .withFixedExposedPort(6379, 6379);

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class[] { ItemTransactional.class};
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
        ItemTransactional item = new ItemTransactional("data");
        item.getEntries().addAll(Arrays.asList("a", "b", "c"));
        s.persist(item);
        s.flush();
        s.getTransaction().commit();
        
        s = sessionFactory().openSession();
        s.beginTransaction();
        Query query = s.getNamedQuery("testQuery");
        query.setCacheable(true);
        query.setCacheRegion("myTestQuery");
        query.setParameter("name", "data");
        item = (ItemTransactional) query.uniqueResult();
        s.getTransaction().commit();
        s.close();
        
        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("myTestQuery").getPutCount());

        s = sessionFactory().openSession();
        s.beginTransaction();
        Query query2 = s.getNamedQuery("testQuery");
        query2.setCacheable(true);
        query2.setCacheRegion("myTestQuery");
        query2.setParameter("name", "data");
        item = (ItemTransactional) query2.uniqueResult();
        s.remove(item);
        s.getTransaction().commit();
        s.close();
        
        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("myTestQuery").getHitCount());
        
        stats.logSummary();
        
    }
    
    @Test
    public void testCollection() {
        Long id = null;
        
        Statistics stats = sessionFactory().getStatistics();
        Session s = sessionFactory().openSession();
        s.beginTransaction();
        ItemTransactional item = new ItemTransactional("data");
        item.getEntries().addAll(Arrays.asList("a", "b", "c"));
        s.persist(item);
        id = item.getId();
        s.flush();
        s.getTransaction().commit();

        s = sessionFactory().openSession();
        s.beginTransaction();
        item = (ItemTransactional) s.get(ItemTransactional.class, id);
        assertThat(item.getEntries()).containsExactly("a", "b", "c");
        s.getTransaction().commit();
        s.close();

        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("item_entries").getPutCount());
        
        s = sessionFactory().openSession();
        s.beginTransaction();
        item = (ItemTransactional) s.get(ItemTransactional.class, id);
        assertThat(item.getEntries()).containsExactly("a", "b", "c");
        s.remove(item);
        s.getTransaction().commit();
        s.close();
        
        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("item_entries").getHitCount());
    }
    
    @Test
    public void testNaturalId() {
        Statistics stats = sessionFactory().getStatistics();
        Session s = sessionFactory().openSession();
        s.beginTransaction();
        ItemTransactional item = new ItemTransactional("data");
        item.setNid("123");
        s.persist(item);
        s.flush();
        s.getTransaction().commit();

        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("item").getPutCount());
        Assertions.assertEquals(1, stats.getNaturalIdStatistics(ItemTransactional.class.getName()).getCachePutCount());
        
        s = sessionFactory().openSession();
        s.beginTransaction();
        item = (ItemTransactional) s.bySimpleNaturalId(ItemTransactional.class).load("123");
        assertThat(item).isNotNull();
        s.remove(item);
        s.getTransaction().commit();
        s.close();
        
        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("item").getHitCount());
        Assertions.assertEquals(1, stats.getNaturalIdStatistics(ItemTransactional.class.getName()).getCacheHitCount());

        sessionFactory().getStatistics().logSummary();
    }
    
    @Test
    public void testUpdateWithRefreshThenRollback() {
        Statistics stats = sessionFactory().getStatistics();
        Long id = null;
        Session s = sessionFactory().openSession();
        s.beginTransaction();
        ItemTransactional item = new ItemTransactional( "data" );
        s.persist( item );
        id = item.getId();
        s.flush();
        s.getTransaction().commit();

        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("item").getPutCount());

        s = sessionFactory().openSession();
        s.beginTransaction();
        item = (ItemTransactional) s.get(ItemTransactional.class, id);
        item.setName("newdata");
        s.merge(item);
        s.flush();
        s.refresh(item);
        s.getTransaction().rollback();
        s.clear();
        s.close();

        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("item").getHitCount());
    }

    
}
