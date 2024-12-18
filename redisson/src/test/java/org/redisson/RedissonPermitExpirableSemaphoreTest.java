package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.NameMapper;
import org.redisson.api.RFuture;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonPermitExpirableSemaphoreTest extends BaseConcurrentTest {

    @Test
    public void testGetInClusterNameMapper() {
        testInCluster(client -> {
            Config config = client.getConfig();
            config.setSlavesSyncTimeout(2000);
            config.useClusterServers()
                    .setNameMapper(new NameMapper() {
                        @Override
                        public String map(String name) {
                            return "test::" + name;
                        }

                        @Override
                        public String unmap(String name) {
                            return name.replace("test::", "");
                        }
                    });
            RedissonClient redisson = Redisson.create(config);

            RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("semaphore");
            s.trySetPermits(1);
            try {
                String v = s.acquire();
                s.release(v);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            redisson.shutdown();

        });
    }

    @Test
    public void testUpdateLeaseTime() throws InterruptedException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test");
        semaphore.trySetPermits(1);
        assertThat(semaphore.updateLeaseTime("1234", 1, TimeUnit.SECONDS)).isFalse();
        String id = semaphore.acquire();
        assertThat(semaphore.updateLeaseTime(id, 1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(1200);
        assertThat(semaphore.updateLeaseTime(id, 1, TimeUnit.SECONDS)).isFalse();
        String id2 = semaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
        assertThat(semaphore.updateLeaseTime(id2, 3, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(2800);
        assertThat(semaphore.availablePermits()).isZero();
        Thread.sleep(500);
        assertThat(semaphore.availablePermits()).isOne();
        assertThat(semaphore.updateLeaseTime(id2, 2, TimeUnit.SECONDS)).isFalse();
    }
    
    @Test
    public void testNotExistent() {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("testSemaphoreForNPE");
        Assertions.assertEquals(0, semaphore.availablePermits());
    }
    
    @Test
    public void testAvailablePermits() throws InterruptedException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test-semaphore");
        assertThat(semaphore.trySetPermits(2)).isTrue();
        Assertions.assertEquals(2, semaphore.availablePermits());
        String acquire1 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire1).isNotNull();
        String acquire2 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire2).isNotNull();
        String acquire3 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire3).isNull();
        Assertions.assertEquals(0, semaphore.availablePermits());
        Thread.sleep(1100);
        String acquire4 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire4).isNotNull();
        Thread.sleep(1100);
        Assertions.assertEquals(2, semaphore.availablePermits());
    }

    @Test
    public void testAcquiredPermits() throws InterruptedException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test-semaphore");
        assertThat(semaphore.trySetPermits(2)).isTrue();
        Assertions.assertEquals(0, semaphore.acquiredPermits());
        String acquire1 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire1).isNotNull();
        Assertions.assertEquals(1, semaphore.acquiredPermits());
        String acquire2 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire2).isNotNull();
        String acquire3 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire3).isNull();
        Assertions.assertEquals(2, semaphore.acquiredPermits());
        Thread.sleep(1100);
        String acquire4 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire4).isNotNull();
        Thread.sleep(1100);
        Assertions.assertEquals(0, semaphore.acquiredPermits());
    }

    @Test
    public void testGetPermits() throws InterruptedException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test-semaphore");
        assertThat(semaphore.trySetPermits(2)).isTrue();
        Assertions.assertEquals(2, semaphore.getPermits());
        String acquire1 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire1).isNotNull();
        String acquire2 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire2).isNotNull();
        String acquire3 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire3).isNull();
        Assertions.assertEquals(2, semaphore.getPermits());
        Thread.sleep(1100);
        String acquire4 = semaphore.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire4).isNotNull();
        Thread.sleep(1100);
        Assertions.assertEquals(2, semaphore.getPermits());
    }

    @Test
    public void testExpiration() throws InterruptedException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("some-key");
        semaphore.trySetPermits(1);
        semaphore.expire(Duration.ofSeconds(3));
        semaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
        Thread.sleep(4100);
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testExpire() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(1);
        String permitId = s.acquire(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        AtomicBoolean bool = new AtomicBoolean();
        Thread t = new Thread() {
            public void run() {
                RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
                try {
                    String permitId = s.acquire();
                    long spendTime = System.currentTimeMillis() - startTime;
                    assertThat(spendTime).isBetween(1900L, 2100L);
                    s.release(permitId);
                    bool.set(true);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            };
        };

        t.start();
        t.join();

        assertThat(s.tryRelease(permitId)).isFalse();
        assertThat(bool.get()).isTrue();
    }

    @Test
    public void testExpireTryAcquire() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(1);
        String permitId = s.tryAcquire(100, 2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        AtomicBoolean bool = new AtomicBoolean();
        Thread t = new Thread() {
            public void run() {
                RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
                try {
                    String permitId = s.acquire();
                    long spendTime = System.currentTimeMillis() - startTime;
                    assertThat(spendTime).isBetween(1900L, 2100L);
                    s.release(permitId);
                    bool.set(true);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            };
        };

        t.start();
        t.join();

        assertThat(s.tryRelease(permitId)).isFalse();
        assertThat(bool.get()).isTrue();
    }

    
    @Test
    public void testTrySetPermits() {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        assertThat(s.trySetPermits(10)).isTrue();
        assertThat(s.availablePermits()).isEqualTo(10);
        assertThat(s.trySetPermits(15)).isFalse();
        assertThat(s.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testSetPermits() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.setPermits(10);
        assertThat(s.getPermits()).isEqualTo(10);
        assertThat(s.availablePermits()).isEqualTo(10);
        assertThat(s.acquiredPermits()).isEqualTo(0);

        // attempts to set available permits fail
        assertThat(s.trySetPermits(15)).isFalse();
        assertThat(s.getPermits()).isEqualTo(10);
        assertThat(s.availablePermits()).isEqualTo(10);
        assertThat(s.acquiredPermits()).isEqualTo(0);

        // attempts to set max permits succeeds
        s.setPermits(15);
        assertThat(s.getPermits()).isEqualTo(15);
        assertThat(s.availablePermits()).isEqualTo(15);
        assertThat(s.acquiredPermits()).isEqualTo(0);

        // setting to existing value succeeds
        s.setPermits(15);
        assertThat(s.getPermits()).isEqualTo(15);
        assertThat(s.availablePermits()).isEqualTo(15);
        assertThat(s.acquiredPermits()).isEqualTo(0);

        // decreasing max permits succeeds
        s.setPermits(5);
        assertThat(s.getPermits()).isEqualTo(5);
        assertThat(s.availablePermits()).isEqualTo(5);
        assertThat(s.acquiredPermits()).isEqualTo(0);

        // changing the max after acquiring permits succeeds
        String acquire1 = s.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire1).isNotNull();
        String acquire2 = s.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire2).isNotNull();
        String acquire3 = s.tryAcquire(200, 1000, TimeUnit.MILLISECONDS);
        assertThat(acquire3).isNotNull();

        assertThat(s.getPermits()).isEqualTo(5);
        assertThat(s.availablePermits()).isEqualTo(2);
        assertThat(s.acquiredPermits()).isEqualTo(3);

        // decreasing the max to the number of claimed permits is allowed
        s.setPermits(3);
        assertThat(s.getPermits()).isEqualTo(3);
        assertThat(s.availablePermits()).isEqualTo(0);
        assertThat(s.acquiredPermits()).isEqualTo(3);

        // decreasing the max to below the number of claimed permits is allowed
        // and results in a negative number of available permits
        s.setPermits(2);
        assertThat(s.getPermits()).isEqualTo(2);
        assertThat(s.availablePermits()).isEqualTo(-1);
        assertThat(s.acquiredPermits()).isEqualTo(3);
    }

    @Test
    public void testAddPermits() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(10);
        
        s.addPermits(5);
        assertThat(s.availablePermits()).isEqualTo(15);
        s.addPermits(-10);
        assertThat(s.availablePermits()).isEqualTo(5);
        s.addPermits(-10);
        assertThat(s.availablePermits()).isEqualTo(-5);
    }
    
    @Test
    public void testBlockingAcquire() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(1);
        String permitId = s.acquire();
        assertThat(permitId).hasSize(32);

        Thread t = new Thread() {
            @Override
            public void run() {
                RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                s.release(permitId);
            }
        };

        t.start();

        assertThat(s.availablePermits()).isEqualTo(0);
        s.acquire();
        assertThat(s.tryAcquire()).isNull();
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testBlockingAcquireMany() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(10);
        List<String> permitsIds = s.acquire(6);
        assertThat(s.availablePermits()).isEqualTo(4);

        s.release(permitsIds);
        assertThat(s.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testTryAcquire() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(1);
        String permitId = s.tryAcquire();
        assertThat(permitId).hasSize(32);

        Thread t = new Thread() {
            @Override
            public void run() {
                RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release(permitId);
            }
        };

        assertThat(s.tryAcquire()).isNull();

        t.start();
        t.join(1);

        long startTime = System.currentTimeMillis();
        String permitId2 = s.tryAcquire(1, TimeUnit.SECONDS);
        assertThat(permitId2).hasSize(32);
        assertThat(System.currentTimeMillis() - startTime).isBetween(450L, 600L);
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testTryAcquireMany() {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(10);
        List<String> permitsIds = s.tryAcquire(4);
        assertThat(permitsIds).hasSize(4);

        List<String> permitsIds2 = s.tryAcquire(5);
        assertThat(permitsIds2).hasSize(5);

        assertThat(s.availablePermits()).isEqualTo(1);
    }

    @Test
    public void testReleaseWithoutPermits() {
        Assertions.assertThrows(RedisException.class, () -> {
            RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
            s.release("1234");
        });
    }

    @Test
    public void testReleaseExpired() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(1);
        String permitId = s.tryAcquire(100, 100, TimeUnit.MILLISECONDS);
        Thread.sleep(200);
        boolean released = s.tryRelease(permitId);
        assertThat(released).isFalse();
    }

    @Test
    public void testConcurrency_SingleInstance() throws InterruptedException {
        final AtomicInteger lockedCounter = new AtomicInteger();

        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(1);

        int iterations = 100;
        testSingleInstanceConcurrency(iterations, r -> {
            RPermitExpirableSemaphore s1 = redisson.getPermitExpirableSemaphore("test");
            try {
                String permitId = s1.acquire();
            int value = lockedCounter.get();
            lockedCounter.set(value + 1);
            s1.release(permitId);
            }catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });

        assertThat(lockedCounter.get()).isEqualTo(iterations);
    }

    @Test
    public void testConcurrencyLoop_MultiInstance() throws InterruptedException {
        final int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(1);

        testMultiInstanceConcurrency(16, r -> {
            for (int i = 0; i < iterations; i++) {
                try {
                    String permitId = r.getPermitExpirableSemaphore("test").acquire();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    int value = lockedCounter.get();
                    lockedCounter.set(value + 1);
                    r.getPermitExpirableSemaphore("test").release(permitId);
                }catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        });

        assertThat(lockedCounter.get()).isEqualTo(16 * iterations);
    }

    @Test
    public void testAcquire() {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test.sync_semaphore");
        semaphore.trySetPermits(1);
        Awaitility.await().atMost(Duration.ofMillis(100)).pollDelay(Duration.ofMillis(10)).untilAsserted(() -> {
            RFuture<String> permit = semaphore.acquireAsync(5000L, TimeUnit.MILLISECONDS);
            assertThat(permit.get()).isNotNull();
        });
    }

    @Test
    public void testRelease() throws InterruptedException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test");
        semaphore.trySetPermits(10);

        List<String> permitsIds = semaphore.acquire(6);
        assertThat(permitsIds).hasSize(6);
        assertThat(semaphore.availablePermits()).isEqualTo(4);

        List<String> permitsIdsFirstPart = permitsIds.subList(0, 4);
        semaphore.release(permitsIdsFirstPart);
        assertThat(semaphore.availablePermits()).isEqualTo(8);

        List<String> permitsIdsSecondPart = permitsIds.subList(4, 6);
        semaphore.release(permitsIdsSecondPart);
        assertThat(semaphore.availablePermits()).isEqualTo(10);

        Assertions.assertThrows(RedisException.class, () -> semaphore.release(permitsIds));

    }

    @Test
    public void testAcquireAsyncMany() throws ExecutionException, InterruptedException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test");
        semaphore.trySetPermits(10);

        RFuture<List<String>> permitsIds = semaphore.acquireAsync(6);

        Awaitility.await().atMost(Duration.ofMillis(100)).pollDelay(Duration.ofMillis(10)).untilAsserted(() -> {
            assertThat(permitsIds.isDone()).isTrue();
        });
        assertThat(permitsIds.get()).hasSize(6);
        assertThat(semaphore.availablePermits()).isEqualTo(4);
    }

    @Test
    public void testReleaseAsyncMany() throws InterruptedException, ExecutionException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test");
        semaphore.trySetPermits(10);

        List<String> permitsIds = semaphore.acquire(6);
        assertThat(permitsIds).hasSize(6);
        assertThat(semaphore.availablePermits()).isEqualTo(4);

        List<String> permitsIdsFirstPart = permitsIds.subList(0, 4);
        RFuture<Integer> releaseResult1 = semaphore.tryReleaseAsync(permitsIdsFirstPart);
        Awaitility.await().atMost(Duration.ofMillis(100)).pollDelay(Duration.ofMillis(10)).untilAsserted(() -> {
            assertThat(releaseResult1.isDone()).isTrue();
        });
        assertThat(releaseResult1.get()).isEqualTo(4);
        assertThat(semaphore.availablePermits()).isEqualTo(8);

        List<String> permitsIdsSecondPart = permitsIds.subList(4, 6);
        RFuture<Integer> releaseResult2 = semaphore.tryReleaseAsync(permitsIdsSecondPart);
        Awaitility.await().atMost(Duration.ofMillis(100)).pollDelay(Duration.ofMillis(10)).untilAsserted(() -> {
            assertThat(releaseResult2.isDone()).isTrue();
        });
        assertThat(releaseResult2.get()).isEqualTo(2);
        assertThat(semaphore.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testReleaseManyExpiredDoesNotThrow() throws InterruptedException, ExecutionException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test");
        semaphore.trySetPermits(10);

        List<String> permitsIds = semaphore.acquire(6, 100, TimeUnit.MILLISECONDS);
        assertThat(permitsIds).hasSize(6);
        assertThat(semaphore.availablePermits()).isEqualTo(4);
        Thread.sleep(250);

        semaphore.acquire(100, TimeUnit.MILLISECONDS);
        assertThat(semaphore.availablePermits()).isEqualTo(9);
        Awaitility.await().atMost(Duration.ofMillis(250)).pollDelay(Duration.ofMillis(10)).untilAsserted(() -> {
            assertThat(semaphore.availablePermits()).isEqualTo(10);
        });
    }

    @Test
    public void testTryReleaseManyExpired() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(10);
        List<String> timedPermitsIds = s.tryAcquire(3, 100, 100, TimeUnit.MILLISECONDS);
        List<String> infinitePermitsIds = s.tryAcquire(3);
        assertThat(s.availablePermits()).isEqualTo(4);
        
        Thread.sleep(200);
        
        int released = s.tryRelease(infinitePermitsIds);
        assertThat(released).isEqualTo(3);
        assertThat(s.availablePermits()).isEqualTo(10);

        released = s.tryRelease(timedPermitsIds);
        assertThat(released).isEqualTo(0);
        assertThat(s.availablePermits()).isEqualTo(10);
    }

    @Test
    public void testReleaseManyWithout() throws InterruptedException {
        RPermitExpirableSemaphore s = redisson.getPermitExpirableSemaphore("test");
        s.trySetPermits(10);
        List<String> timedPermitsIds = s.acquire(2,100, TimeUnit.MILLISECONDS);
        List<String> permitsIds = s.tryAcquire(8);
        List<String> permitsIdsFirstPart = permitsIds.subList(0, 2);

        int released = s.tryRelease(permitsIdsFirstPart);
        assertThat(released).isEqualTo(2);
        assertThat(s.availablePermits()).isEqualTo(2);

        Thread.sleep(100);

        Assertions.assertThrows(RedisException.class, () -> s.release(timedPermitsIds));
        assertThat(s.availablePermits()).isEqualTo(4);

        List<String> permitsIdsThirdPart = permitsIds.subList(4, 6);
        permitsIdsThirdPart.addAll(permitsIdsFirstPart);
        Assertions.assertThrows(RedisException.class, () -> s.release(permitsIdsThirdPart));
        assertThat(s.availablePermits()).isEqualTo(4);


    }
    
    @Test
    public void testGetLeaseTime() throws InterruptedException {
        RPermitExpirableSemaphore semaphore = redisson.getPermitExpirableSemaphore("test");
        semaphore.trySetPermits(1);
        
        Assertions.assertThrows(RedisException.class, () -> semaphore.getLeaseTime("1234"));
        
        String permitId = semaphore.acquire();
        Assertions.assertEquals(-1, semaphore.getLeaseTime(permitId));
        
        semaphore.release(permitId);
        permitId = semaphore.acquire(1100, TimeUnit.MILLISECONDS);
        assertThat(permitId).isNotNull();
        Thread.sleep(100);
        assertThat(semaphore.getLeaseTime(permitId)).isLessThanOrEqualTo(1000);
        Awaitility.await().atMost(Duration.ofMillis(1100)).untilAsserted(() -> {
            assertThat(semaphore.availablePermits()).isEqualTo(1);
        });
    }
}
