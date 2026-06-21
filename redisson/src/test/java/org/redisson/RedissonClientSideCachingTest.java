package org.redisson;

import io.netty.util.IllegalReferenceCountException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RClientSideCaching;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.ClientSideCachingOptions;
import org.redisson.client.RedisException;
import org.redisson.command.CommandAsyncService;
import org.redisson.config.Config;
import org.redisson.config.Protocol;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class RedissonClientSideCachingTest extends RedisDockerTest {

    @Test
    public void testBucket() {
        Config c = redisson.getConfig();
        c.setProtocol(Protocol.RESP3);

        RedissonClient rs = Redisson.create(c);

        RClientSideCaching csc = rs.getClientSideCaching(ClientSideCachingOptions.defaults());
        RBucket<String> b = csc.getBucket("test1");
        Assertions.assertThat(b.get()).isNull();
        Assertions.assertThat(b.get()).isNull();

        RBucket<Object> b2 = rs.getBucket("test1");
        b2.set("123");

        Assertions.assertThat(b.get()).isEqualTo("123");

        csc.destroy();

        RBucket<Object> b3 = rs.getBucket("test1");
        b3.set("123");

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

    @Test
    public void testInterruptedGet() throws InterruptedException {
        Config c = redisson.getConfig();
        c.setProtocol(Protocol.RESP3);

        RedissonClient rs = Redisson.create(c);
        try {
            RClientSideCaching csc = rs.getClientSideCaching(ClientSideCachingOptions.defaults());
            RBucket<String> bucket = csc.getBucket("csc-cancel-test-1");

            AtomicReference<Throwable> caught = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);

            Thread reader = new Thread(() -> {
                started.countDown();
                try {
                    bucket.get();
                } catch (Throwable t) {
                    caught.set(t);
                } finally {
                    done.countDown();
                }
            });

            reader.start();
            started.await();
            reader.interrupt();
            done.await();

            Assertions.assertThat(caught.get())
                    .isNotNull()
                    .isInstanceOf(RedisException.class);

            csc.destroy();
        } finally {
            rs.shutdown();
        }
    }

    @Test
    public void testCancelledFutureUnwrapping() {
        Config c = redisson.getConfig();
        c.setProtocol(Protocol.RESP3);

        RedissonClient rs = Redisson.create(c);
        try {
            CommandAsyncService commandService = (CommandAsyncService) ((Redisson) rs).getCommandExecutor();

            CompletableFuture<String> cancelledFuture = new CompletableFuture<>();
            cancelledFuture.cancel(true);

            Assertions.assertThatThrownBy(() -> {
                        commandService.get(cancelledFuture);
                    }).isInstanceOf(RedisException.class)
                    .hasCauseInstanceOf(CancellationException.class);

        } finally {
            rs.shutdown();
        }
    }

    @Test
    public void testBucketGetAfterCancellation() {
        Config c = redisson.getConfig();
        c.setProtocol(Protocol.RESP3);

        RedissonClient rs = Redisson.create(c);
        try {
            RClientSideCaching csc = rs.getClientSideCaching(ClientSideCachingOptions.defaults());

            RBucket<String> cached = csc.getBucket("csc-cancel-test-3");
            RBucket<String> direct = rs.getBucket("csc-cancel-test-3");

            Assertions.assertThat(cached.get()).isNull();

            direct.set("world");
            Assertions.assertThat(cached.get()).isEqualTo("world");

            direct.set("updated");
            Assertions.assertThat(cached.get()).isEqualTo("updated");

            csc.destroy();
        } finally {
            rs.shutdown();
        }
    }

}
