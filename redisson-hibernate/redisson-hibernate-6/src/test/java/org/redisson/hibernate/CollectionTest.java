package org.redisson.hibernate;

import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.stat.Statistics;
import org.hibernate.testing.orm.junit.BaseSessionFactoryFunctionalTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@Testcontainers
public class CollectionTest extends BaseSessionFactoryFunctionalTest {

    @Container
    public static final GenericContainer H2 = new FixedHostPortGenericContainer("oscarfonts/h2:latest")
                                                        .withFixedExposedPort(1521, 1521);

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
                                                        .withFixedExposedPort(6379, 6379);

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

    @BeforeEach
    public void before() {
        sessionFactory().getCache().evictEntityData();
        sessionFactory().getStatistics().clear();
    }

    @Test
    public void testQuery() {
        Statistics stats = sessionFactory().getStatistics();

        Session s = sessionFactory().openSession();
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

        s = sessionFactory().openSession();
        s.beginTransaction();
        A a1 = s.get(A.class, 1L);
        assertThat(a1.bs).hasSize(1);
        s.getTransaction().commit();

        Assertions.assertEquals(0, stats.getDomainDataRegionStatistics("org.redisson.hibernate.CollectionTest$A.bs").getHitCount());

        s = sessionFactory().openSession();
        s.beginTransaction();
        A a2 = s.get(A.class, 1L);
        B b2 = a2.bs.iterator().next();
        assertThat(a2.bs.size()).isEqualTo(1);
        s.getTransaction().commit();

        s.close();

        Assertions.assertEquals(1, stats.getDomainDataRegionStatistics("org.redisson.hibernate.CollectionTest$A.bs").getHitCount());

        stats.logSummary();
        
    }
    
}
