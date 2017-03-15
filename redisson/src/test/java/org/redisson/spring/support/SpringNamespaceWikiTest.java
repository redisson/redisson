package org.redisson.spring.support;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.redisson.ClusterRunner;
import org.redisson.RedisRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class SpringNamespaceWikiTest {
    
    @Test
    public void testSingle() throws Exception {
        RedisRunner.RedisProcess run = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        try {
            ((ConfigurableApplicationContext)
                new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_single.xml"))
                .close();
        } finally {
            run.stop();
        }
    }
    
    @Test
    public void testMasterSlave() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        try {
            ((ConfigurableApplicationContext)
                    new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_master_slave.xml"))
                    .close();
        } finally {
            master.stop();
            slave1.stop();
            slave2.stop();
        }
    }
    
    @Test
    public void testSentinel() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
//                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "do_not_use_if_it_is_not_set")
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
//                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "do_not_use_if_it_is_not_set")
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
//                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .sentinelAuthPass("myMaster", "do_not_use_if_it_is_not_set")
                .run();
        try {
            ((ConfigurableApplicationContext)
                    new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_sentinel.xml"))
                    .close();
        } finally {
            master.stop();
            slave1.stop();
            slave2.stop();
            sentinel1.stop();
            sentinel2.stop();
            sentinel3.stop();
        }
    }
    
    @Test
    public void testReplicated() throws Exception {
        RedisRunner.RedisProcess master = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .requirepass("do_not_use_if_it_is_not_set")
                .masterauth("do_not_use_if_it_is_not_set")
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        try {
            ((ConfigurableApplicationContext)
                    new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_replicated.xml"))
                    .close();
        } finally {
            master.stop();
            slave1.stop();
            slave2.stop();
        }
    }
    
    @Test
    public void testCluster() throws Exception {
        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(new RedisRunner()
                        .requirepass("do_not_use_if_it_is_not_set")
                        .port(6379)
                        .randomDir()
                        .nosave())
                .addNode(new RedisRunner()
                        .requirepass("do_not_use_if_it_is_not_set")
                        .port(6380)
                        .randomDir()
                        .nosave())
                .addNode(new RedisRunner()
                        .requirepass("do_not_use_if_it_is_not_set")
                        .port(6381)
                        .randomDir()
                        .nosave());
        List<RedisRunner.RedisProcess> nodes = clusterRunner.run();
        
        try {
            ((ConfigurableApplicationContext)
                    new ClassPathXmlApplicationContext("classpath:org/redisson/spring/support/namespace_wiki_cluster.xml"))
                    .close();
        } finally {
            for (RedisRunner.RedisProcess node : nodes) {
                node.stop();
            }
        }
    }
}
