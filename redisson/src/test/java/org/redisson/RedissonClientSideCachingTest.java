package org.redisson;

import io.netty.util.IllegalReferenceCountException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RClientSideCaching;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.ClientSideCachingOptions;
import org.redisson.config.Config;
import org.redisson.config.Protocol;

import java.util.concurrent.atomic.AtomicReference;

public class RedissonClientSideCachingTest extends RedisDockerTest {

    @Test
    public void testBucket() throws InterruptedException {
        Config c = redisson.getConfig();
        c.setProtocol(Protocol.RESP3);

        RedissonClient rs = Redisson.create(c);

        RClientSideCaching csc = rs.getClientSideCaching(ClientSideCachingOptions.defaults());
        RBucket<String> b = csc.getBucket("test1");
        Assertions.assertThat(b.get()).isNull();
        Assertions.assertThat(b.get()).isNull();

        RBucket<Object> b2 = rs.getBucket("test1");
        b2.set("123");
        Thread.sleep(100);

        Assertions.assertThat(b.get()).isEqualTo("123");

        rs.shutdown();
    }

    @Test
    public void testSortSet() throws InterruptedException {
        Config c = redisson.getConfig();
        c.setProtocol(Protocol.RESP3);

        RedissonClient rs = Redisson.create(c);

        RClientSideCaching csc = rs.getClientSideCaching(ClientSideCachingOptions.defaults());
        RScoredSortedSet<String> set = csc.getScoredSortedSet("sampleKey");

        set.add(0.1,"a");
        set.add(0.2,"b");


        Thread t1=new Thread(()->{
           set.revRank("a");
        });

        t1.start();
        t1.join();

        AtomicReference<Throwable> threadException = new AtomicReference<>();
        Thread t2 = new Thread(() -> {
            try {
                set.revRank("a");
            } catch (IllegalReferenceCountException e) {
                threadException.set(e);
            }
        });

        t2.start();
        t2.join();

        Assertions.assertThat(threadException.get()).isNull();

        rs.shutdown();

    }

}
