package org.redisson.hibernate;

import org.hibernate.Session;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.stat.Statistics;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CollectionTest extends BaseCoreFunctionalTestCase {

    @Entity
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    public static class A implements Serializable {
      @Id
      Long id;

      @Column(unique = true) String uniqueField;

      @ManyToMany
      @JoinTable(
          name = "a_b",
          joinColumns = @JoinColumn(
              name = "unique_field", referencedColumnName = "uniqueField"),
          inverseJoinColumns = @JoinColumn(
              name = "b_id", referencedColumnName = "id"))
      @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
      private List<B> bs = new ArrayList<>();
    }

    @Entity
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    public static class B implements Serializable {
      @Id Long id;

    }

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class[] { A.class, B.class };
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

        cfg.setProperty(Environment.SHOW_SQL, "true");
        cfg.setProperty(Environment.USE_SECOND_LEVEL_CACHE, "true");
        cfg.setProperty(Environment.USE_QUERY_CACHE, "true");
        cfg.setProperty(Environment.CACHE_REGION_FACTORY, RedissonRegionFactory.class.getName());
    }
    
    @Before
    public void before() {
        sessionFactory().getCache().evictEntityRegions();
        sessionFactory().getStatistics().clear();
    }

    @Test
    public void testQuery() {
        Statistics stats = sessionFactory().getStatistics();

        Session s = openSession();
        s.beginTransaction();

        A a = new A();
        a.id = 1L;
        a.uniqueField = "1";
        B b = new B();
        b.id = 1L;
        s.save(b);
        a.bs.add(b);
        s.save(a);
        s.flush();
        s.getTransaction().commit();

        s = openSession();
        s.beginTransaction();
        A a1 = s.get(A.class, 1L);
        System.out.println("here1");
        assertThat(a1.bs).hasSize(1);
        s.getTransaction().commit();

        Assert.assertEquals(0, stats.getSecondLevelCacheStatistics("org.redisson.hibernate.CollectionTest$A.bs").getHitCount());

        s = openSession();
        s.beginTransaction();
        A a2 = s.get(A.class, 1L);
        B b2 = a2.bs.iterator().next();
        assertThat(a2.bs.size()).isEqualTo(1);
        s.getTransaction().commit();

        s.close();

        Assert.assertEquals(1, stats.getSecondLevelCacheStatistics("org.redisson.hibernate.CollectionTest$A.bs").getHitCount());

        stats.logSummary();
        
    }
    
}
