package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RFuture;
import org.redisson.api.RRemoteService;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RRemoteAsync;
import org.redisson.api.annotation.RRemoteReactive;
import org.redisson.api.annotation.RRemoteRx;
import org.redisson.codec.FstCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.remote.RemoteServiceAckTimeoutException;
import org.redisson.remote.RemoteServiceTimeoutException;

import io.reactivex.Completable;
import io.reactivex.Single;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public class RedissonRemoteServiceTest extends BaseTest {

    public static class Pojo {

        private String stringField;

        public Pojo() {
        }

        public Pojo(String stringField) {
            this.stringField = stringField;
        }

        public String getStringField() {
            return stringField;
        }

        public void setStringField(String stringField) {
            this.stringField = stringField;
        }
    }

    public static class SerializablePojo implements Serializable {

        private String stringField;

        public SerializablePojo() {
        }

        public SerializablePojo(String stringField) {
            this.stringField = stringField;
        }

        public String getStringField() {
            return stringField;
        }

        public void setStringField(String stringField) {
            this.stringField = stringField;
        }
    }

    @RRemoteAsync(RemoteInterface.class)
    public interface RemoteInterfaceAsync {
        
        RFuture<Void> cancelMethod();
        
        RFuture<Void> voidMethod(String name, Long param);
        
        RFuture<Long> resultMethod(Long value);
        
        RFuture<Void> errorMethod();
        
        RFuture<Void> errorMethodWithCause();
        
        RFuture<Void> timeoutMethod();
        
    }

    @RRemoteReactive(RemoteInterface.class)
    public interface RemoteInterfaceReactive {
        
        Mono<Void> cancelMethod();
        
        Mono<Void> voidMethod(String name, Long param);
        
        Mono<Long> resultMethod(Long value);
        
        Mono<Void> errorMethod();
        
        Mono<Void> errorMethodWithCause();
        
        Mono<Void> timeoutMethod();
        
    }

    @RRemoteRx(RemoteInterface.class)
    public interface RemoteInterfaceRx {
        
        Completable cancelMethod();
        
        Completable voidMethod(String name, Long param);
        
        Single<Long> resultMethod(Long value);
        
        Completable errorMethod();
        
        Completable errorMethodWithCause();
        
        Completable timeoutMethod();
        
    }
    
    @RRemoteAsync(RemoteInterface.class)
    public interface RemoteInterfaceWrongMethodAsync {
        
        RFuture<Void> voidMethod1(String name, Long param);
        
        RFuture<Long> resultMethod(Long value);
        
    }
    
    @RRemoteAsync(RemoteInterface.class)
    public interface RemoteInterfaceWrongParamsAsync {
        
        RFuture<Void> voidMethod(Long param, String name);
        
        RFuture<Long> resultMethod(Long value);
        
    }

    
    public interface RemoteInterface {
        
        void cancelMethod() throws InterruptedException;
        
        void voidMethod(String name, Long param);

        Long resultMethod(Long value);
        
        void errorMethod() throws IOException;
        
        void errorMethodWithCause();
        
        void timeoutMethod() throws InterruptedException;

        Pojo doSomethingWithPojo(Pojo pojo);

        SerializablePojo doSomethingWithSerializablePojo(SerializablePojo pojo);
        
        String methodOverload();
        
        String methodOverload(String str);
        
        String methodOverload(Long lng);
        
        String methodOverload(String str, Long lng);

    }
    
    public static class RemoteImpl implements RemoteInterface {

        private AtomicInteger iterations;
        
        public RemoteImpl() {
        }
        
        public RemoteImpl(AtomicInteger iterations) {
            super();
            this.iterations = iterations;
        }

        @Override
        public void cancelMethod() throws InterruptedException {
            while (true) {
                int i = iterations.incrementAndGet();
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("interrupted! " + i);
                    return;
                }
            }
        }
        
        @Override
        public void voidMethod(String name, Long param) {
            System.out.println(name + " " + param);
        }

        @Override
        public Long resultMethod(Long value) {
            return value*2;
        }
        
        @Override
        public void errorMethod() throws IOException {
            throw new IOException("Checking error throw");
        }
        
        @Override
        public void errorMethodWithCause() {
            try {
                int s = 2 / 0;
            } catch (Exception e) {
                throw new RuntimeException("Checking error throw", e);
            }
        }

        @Override
        public void timeoutMethod() throws InterruptedException {
            Thread.sleep(2000);
        }

        @Override
        public Pojo doSomethingWithPojo(Pojo pojo) {
            return pojo;
        }

        @Override
        public SerializablePojo doSomethingWithSerializablePojo(SerializablePojo pojo) {
            return pojo;
        }

        @Override
        public String methodOverload() {
            return "methodOverload()";
        }

        @Override
        public String methodOverload(Long lng) {
            return "methodOverload(Long lng)";
        }

        @Override
        public String methodOverload(String str) {
            return "methodOverload(String str)";
        }

        @Override
        public String methodOverload(String str, Long lng) {
            return "methodOverload(String str, Long lng)";
        }
        
    }

    @Test
    public void testConcurrentInvocations() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        RRemoteService remoteService = redisson.getRemoteService();
        remoteService.register(RemoteInterface.class, new RemoteImpl());
        RemoteInterface service = redisson.getRemoteService().get(RemoteInterface.class);

        List<Future<?>> futures = new ArrayList<>();

        int iterations = 1000;
        AtomicBoolean bool = new AtomicBoolean();
        for (int i = 0; i < iterations; i++) {
            futures.add(executorService.submit(() -> {
                try {
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        service.resultMethod(1L);
                    } else {
                        service.methodOverload();
                    }
                } catch (Exception e) {
                    bool.set(true);
                }
            }));
        }

        while (!futures.stream().allMatch(Future::isDone)) {}

        assertThat(bool.get()).isFalse();
        remoteService.deregister(RemoteInterface.class);
    }

    @Test
    public void testPendingInvocations() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RRemoteService rs = redisson.getRemoteService();
        rs.register(RemoteInterface.class, new RemoteImpl(), 1, executor);
        
        assertThat(rs.getPendingInvocations(RemoteInterface.class)).isEqualTo(0);
        
        RemoteInterfaceAsync ri = redisson.getRemoteService().get(RemoteInterfaceAsync.class);
        
        for (int i = 0; i < 5; i++) {
            ri.timeoutMethod();
        }
        Thread.sleep(1000);
        assertThat(rs.getPendingInvocations(RemoteInterface.class)).isEqualTo(4);
        Thread.sleep(9000);
        assertThat(rs.getPendingInvocations(RemoteInterface.class)).isEqualTo(0);

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        
        rs.deregister(RemoteInterface.class);
    }
    
    @Test
    public void testFreeWorkers() throws InterruptedException, ExecutionException {
        RedissonClient r1 = createInstance();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RRemoteService rs = r1.getRemoteService();
        rs.register(RemoteInterface.class, new RemoteImpl(), 1, executor);
        assertThat(rs.getFreeWorkers(RemoteInterface.class)).isEqualTo(1);
        
        RedissonClient r2 = createInstance();
        RemoteInterfaceAsync ri = r2.getRemoteService().get(RemoteInterfaceAsync.class);
        
        RFuture<Void> f = ri.timeoutMethod();
        Thread.sleep(100);
        assertThat(rs.getFreeWorkers(RemoteInterface.class)).isEqualTo(0);
        f.get();
        assertThat(rs.getFreeWorkers(RemoteInterface.class)).isEqualTo(1);

        r1.shutdown();
        r2.shutdown();
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }
    
    @Test
    public void testCancelAsync() throws InterruptedException {
        RedissonClient r1 = createInstance();
        AtomicInteger iterations = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        r1.getKeys().flushall();
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl(iterations), 1, executor);
        
        RedissonClient r2 = createInstance();
        RemoteInterfaceAsync ri = r2.getRemoteService().get(RemoteInterfaceAsync.class);
        
        RFuture<Void> f = ri.cancelMethod();
        Thread.sleep(500);
        assertThat(f.cancel(true)).isTrue();
        
        executor.shutdown();
        r1.shutdown();
        r2.shutdown();
        
        assertThat(iterations.get()).isLessThan(Integer.MAX_VALUE / 2);
        
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testCancelReactive() throws InterruptedException {
        RedissonReactiveClient r1 = Redisson.createReactive(createConfig());
        AtomicInteger iterations = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        r1.getKeys().flushall();
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl(iterations), 1, executor);
        
        RedissonReactiveClient r2 = Redisson.createReactive(createConfig());
        RemoteInterfaceReactive ri = r2.getRemoteService().get(RemoteInterfaceReactive.class);
        
        Mono<Void> f = ri.cancelMethod();
        Disposable t = f.doOnSubscribe(s -> s.request(1)).subscribe();
        Thread.sleep(500);
        t.dispose();
        
        executor.shutdown();
        r1.shutdown();
        r2.shutdown();
        
        assertThat(iterations.get()).isLessThan(Integer.MAX_VALUE / 2);
        
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testWrongMethodAsync() throws InterruptedException {
        redisson.getRemoteService().get(RemoteInterfaceWrongMethodAsync.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongParamsAsync() throws InterruptedException {
        redisson.getRemoteService().get(RemoteInterfaceWrongParamsAsync.class);
    }
    
    @Test
    public void testAsync() throws InterruptedException {
        RedissonClient r1 = createInstance();
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = createInstance();
        RemoteInterfaceAsync ri = r2.getRemoteService().get(RemoteInterfaceAsync.class);
        
        RFuture<Void> f = ri.voidMethod("someName", 100L);
        f.sync();
        RFuture<Long> resFuture = ri.resultMethod(100L);
        resFuture.sync();
        assertThat(resFuture.getNow()).isEqualTo(200);

        r1.shutdown();
        r2.shutdown();
    }

    @Test
    public void testReactive() throws InterruptedException {
        RedissonReactiveClient r1 = Redisson.createReactive(createConfig());
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonReactiveClient r2 = Redisson.createReactive(createConfig());
        RemoteInterfaceReactive ri = r2.getRemoteService().get(RemoteInterfaceReactive.class);
        
        Mono<Void> f = ri.voidMethod("someName", 100L);
        f.block();
        Mono<Long> resFuture = ri.resultMethod(100L);
        assertThat(resFuture.block()).isEqualTo(200);

        r1.shutdown();
        r2.shutdown();
    }

    @Test
    public void testRx() throws InterruptedException {
        RedissonRxClient r1 = Redisson.createRx(createConfig());
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonRxClient r2 = Redisson.createRx(createConfig());
        RemoteInterfaceRx ri = r2.getRemoteService().get(RemoteInterfaceRx.class);
        
        Completable f = ri.voidMethod("someName", 100L);
        f.blockingGet();
        Single<Long> resFuture = ri.resultMethod(100L);
        assertThat(resFuture.blockingGet()).isEqualTo(200);

        r1.shutdown();
        r2.shutdown();
    }
    
    @Test
    public void testExecutorAsync() throws InterruptedException {
        RedissonClient r1 = createInstance();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl(), 1, executor);
        
        RedissonClient r2 = createInstance();
        RemoteInterfaceAsync ri = r2.getRemoteService().get(RemoteInterfaceAsync.class);
        
        RFuture<Void> f = ri.voidMethod("someName", 100L);
        f.sync();
        RFuture<Long> resFuture = ri.resultMethod(100L);
        resFuture.sync();
        assertThat(resFuture.getNow()).isEqualTo(200);

        r1.shutdown();
        r2.shutdown();
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }


    @Test
    public void testExecutorsAmountConcurrency() throws InterruptedException {

        // Redisson server and client
        final RedissonClient server = createInstance();
        final RedissonClient client = createInstance();

        final int serverAmount = 1;
        final int clientAmount = 10;

        // to store the current call concurrency count
        final AtomicInteger concurrency = new AtomicInteger(0);

        // a flag to indicate the the allowed concurrency was exceeded
        final AtomicBoolean concurrencyIsExceeded = new AtomicBoolean(false);

        // the server: register a service with an overrided timeoutMethod method that:
        // - incr the concurrency
        // - check if concurrency is greater than what was allowed, and if yes set the concurrencyOfOneIsExceeded flag
        // - wait 2s
        // - decr the concurrency
        server.getRemoteService().register(RemoteInterface.class, new RemoteImpl() {
            @Override
            public void timeoutMethod() throws InterruptedException {
                try {
                    if (concurrency.incrementAndGet() > serverAmount) {
                        concurrencyIsExceeded.compareAndSet(false, true);
                    }
                    super.timeoutMethod();
                } finally {
                    concurrency.decrementAndGet();
                }
            }
        }, serverAmount);

        // a latch to force the client threads to execute simultaneously
        // (as far as practicable, this is hard to predict)
        final CountDownLatch readyLatch = new CountDownLatch(1);

        // the client: starts a couple of threads that will:
        // - await for the ready latch
        // - then call timeoutMethod
        java.util.concurrent.Future[] clientFutures = new java.util.concurrent.Future[clientAmount];
        ExecutorService executor = Executors.newFixedThreadPool(clientAmount);
        for (int i = 0; i < clientAmount; i++) {
            clientFutures[i] = executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        RemoteInterface ri = client.getRemoteService().get(RemoteInterface.class, clientAmount * 3, TimeUnit.SECONDS, clientAmount * 3, TimeUnit.SECONDS);
                        readyLatch.await();
                        ri.timeoutMethod();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            });
        }

        // open the latch to wake the threads
        readyLatch.countDown();

        // await for the client threads to terminate
        for (java.util.concurrent.Future clientFuture : clientFutures) {
            try {
                clientFuture.get();
            } catch (ExecutionException e) {
                // ignore
            }
        }
        executor.shutdown();
        executor.awaitTermination(clientAmount * 3, TimeUnit.SECONDS);

        // shutdown the server and the client
        server.shutdown();
        client.shutdown();

        // do the concurrencyIsExceeded flag was set ?
        // if yes, that would indicate that the server exceeded its expected concurrency
        assertThat(concurrencyIsExceeded.get()).isEqualTo(false);
    }

    @Test(expected = RemoteServiceTimeoutException.class)
    public void testTimeout() throws InterruptedException {
        RedissonClient r1 = createInstance();
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = createInstance();
        RemoteInterface ri = r2.getRemoteService().get(RemoteInterface.class, 1, TimeUnit.SECONDS);
        
        try {
            ri.timeoutMethod();
        } finally {
            r1.shutdown();
            r2.shutdown();
        }
    }

    @Test
    public void testInvocations() {
        RedissonClient r1 = createInstance();
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = createInstance();
        RemoteInterface ri = r2.getRemoteService().get(RemoteInterface.class);
        
        ri.voidMethod("someName", 100L);
        assertThat(ri.resultMethod(100L)).isEqualTo(200);

        try {
            ri.errorMethod();
            Assert.fail();
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("Checking error throw");
        }
        
        try {
            ri.errorMethodWithCause();
            Assert.fail();
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(ArithmeticException.class);
            assertThat(e.getCause().getMessage()).isEqualTo("/ by zero");
        }
        
        assertThat(r1.getKeys().count()).isZero();
        r1.shutdown();
        r2.shutdown();
    }

    @Test
    public void testInvocationWithServiceName() {
        RedissonClient server = createInstance();
        RedissonClient client = createInstance();

        server.getRemoteService("MyServiceNamespace").register(RemoteInterface.class, new RemoteImpl());

        RemoteInterface serviceRemoteInterface = client.getRemoteService("MyServiceNamespace").get(RemoteInterface.class);
        RemoteInterface otherServiceRemoteInterface = client.getRemoteService("MyOtherServiceNamespace").get(RemoteInterface.class);
        RemoteInterface defaultServiceRemoteInterface = client.getRemoteService().get(RemoteInterface.class);

        assertThat(serviceRemoteInterface.resultMethod(21L)).isEqualTo(42L);

        try {
            otherServiceRemoteInterface.resultMethod(21L);
            Assert.fail("Invoking a service in an unregistered custom services namespace should throw");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(RemoteServiceAckTimeoutException.class);
        }

        try {
            defaultServiceRemoteInterface.resultMethod(21L);
            Assert.fail("Invoking a service in the unregistered default services namespace should throw");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(RemoteServiceAckTimeoutException.class);
        }

        client.shutdown();
        server.shutdown();
    }

    @Test
    public void testProxyToStringEqualsAndHashCode() {
        RedissonClient client = createInstance();
        try {
            RemoteInterface service = client.getRemoteService().get(RemoteInterface.class);

            try {
                System.out.println(service.toString());
            } catch (Exception e) {
                Assert.fail("calling toString on the client service proxy should not make a remote call");
            }

            try {
                assertThat(service.hashCode() == service.hashCode()).isTrue();
            } catch (Exception e) {
                Assert.fail("calling hashCode on the client service proxy should not make a remote call");
            }

            try {
                assertThat(service.equals(service)).isTrue();
            } catch (Exception e) {
                Assert.fail("calling equals on the client service proxy should not make a remote call");
            }

        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testInvocationWithFstCodec() {
        RedissonClient server = Redisson.create(createConfig().setCodec(new FstCodec()));
        RedissonClient client = Redisson.create(createConfig().setCodec(new FstCodec()));
        try {
            server.getRemoteService().register(RemoteInterface.class, new RemoteImpl());

            RemoteInterface service = client.getRemoteService().get(RemoteInterface.class);

            assertThat(service.resultMethod(21L)).as("Should be compatible with FstCodec").isEqualTo(42L);

            try {
                assertThat(service.doSomethingWithSerializablePojo(new SerializablePojo("test")).getStringField()).isEqualTo("test");
            } catch (Exception e) {
                Assert.fail("Should be compatible with FstCodec");
            }

            try {
                assertThat(service.doSomethingWithPojo(new Pojo("test")).getStringField()).isEqualTo("test");
                Assert.fail("FstCodec should not be able to serialize a not serializable class");
            } catch (Exception e) {
                assertThat(e).isInstanceOf(RuntimeException.class);
                assertThat(e.getMessage()).contains("Pojo does not implement Serializable");
            }
        } finally {
            client.shutdown();
            server.shutdown();
        }
    }

    @Test
    public void testInvocationWithSerializationCodec() {
        RedissonClient server = Redisson.create(createConfig().setCodec(new SerializationCodec()));
        RedissonClient client = Redisson.create(createConfig().setCodec(new SerializationCodec()));
        try {
            server.getRemoteService().register(RemoteInterface.class, new RemoteImpl());

            RemoteInterface service = client.getRemoteService().get(RemoteInterface.class);

            try {
                assertThat(service.resultMethod(21L)).isEqualTo(42L);
            } catch (Exception e) {
                Assert.fail("Should be compatible with SerializationCodec");
            }

            try {
                assertThat(service.doSomethingWithSerializablePojo(new SerializablePojo("test")).getStringField()).isEqualTo("test");
            } catch (Exception e) {
                e.printStackTrace();
                Assert.fail("Should be compatible with SerializationCodec");
            }

            try {
                assertThat(service.doSomethingWithPojo(new Pojo("test")).getStringField()).isEqualTo("test");
                Assert.fail("SerializationCodec should not be able to serialize a not serializable class");
            } catch (Exception e) {
                e.printStackTrace();
                assertThat(e.getCause()).isInstanceOf(NotSerializableException.class);
                assertThat(e.getCause().getMessage()).contains("Pojo");
            }
        } finally {
            client.shutdown();
            server.shutdown();
        }
    }

    @Test
    public void testNoAckWithResultInvocations() throws InterruptedException {
        RedissonClient server = createInstance();
        RedissonClient client = createInstance();
        try {
            server.getRemoteService().register(RemoteInterface.class, new RemoteImpl());

            // no ack but an execution timeout of 1 second
            RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noAck().expectResultWithin(1, TimeUnit.SECONDS);
            RemoteInterface service = client.getRemoteService().get(RemoteInterface.class, options);

            service.voidMethod("noAck", 100L);
            assertThat(service.resultMethod(21L)).isEqualTo(42);

            try {
                service.errorMethod();
                Assert.fail();
            } catch (IOException e) {
                assertThat(e.getMessage()).isEqualTo("Checking error throw");
            }

            try {
                service.errorMethodWithCause();
                Assert.fail();
            } catch (Exception e) {
                assertThat(e.getCause()).isInstanceOf(ArithmeticException.class);
                assertThat(e.getCause().getMessage()).isEqualTo("/ by zero");
            }

            try {
                service.timeoutMethod();
                Assert.fail("noAck option should still wait for the server to return a response and throw if the execution timeout is exceeded");
            } catch (Exception e) {
                assertThat(e).isInstanceOf(RemoteServiceTimeoutException.class);
            }
        } finally {
            client.shutdown();
            server.shutdown();
        }
    }

    @Test
    public void testNoAckWithResultInvocationsAsync() throws InterruptedException, ExecutionException {
        RedissonClient server = createInstance();
        RedissonClient client = createInstance();
        try {
            server.getRemoteService().register(RemoteInterface.class, new RemoteImpl());

            // no ack but an execution timeout of 1 second
            RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noAck().expectResultWithin(1, TimeUnit.SECONDS);
            RemoteInterfaceAsync service = client.getRemoteService().get(RemoteInterfaceAsync.class, options);

            service.voidMethod("noAck", 100L).get();
            assertThat(service.resultMethod(21L).get()).isEqualTo(42);

            try {
                service.errorMethod().get();
                Assert.fail();
            } catch (Exception e) {
                assertThat(e.getCause().getMessage()).isEqualTo("Checking error throw");
            }

            try {
                service.errorMethodWithCause().get();
                Assert.fail();
            } catch (Exception e) {
                assertThat(e.getCause().getCause()).isInstanceOf(ArithmeticException.class);
                assertThat(e.getCause().getCause().getMessage()).isEqualTo("/ by zero");
            }

            try {
                service.timeoutMethod().get();
                Assert.fail("noAck option should still wait for the server to return a response and throw if the execution timeout is exceeded");
            } catch (Exception e) {
                assertThat(e.getCause()).isInstanceOf(RemoteServiceTimeoutException.class);
            }
        } finally {
            client.shutdown();
            server.shutdown();
        }
    }

    @Test
    public void testAckWithoutResultInvocations() throws InterruptedException {
        RedissonClient server = createInstance();
        RedissonClient client = createInstance();
        try {
            server.getRemoteService().register(RemoteInterface.class, new RemoteImpl());

            // fire and forget with an ack timeout of 1 sec
            RemoteInvocationOptions options = RemoteInvocationOptions.defaults().expectAckWithin(1, TimeUnit.SECONDS).noResult();
            RemoteInterface service = client.getRemoteService().get(RemoteInterface.class, options);

            service.voidMethod("noResult", 100L);

            try {
                service.resultMethod(100L);
                Assert.fail();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalArgumentException.class);
            }

            try {
                service.errorMethod();
            } catch (IOException e) {
                Assert.fail("noResult option should not throw server side exception");
            }

            try {
                service.errorMethodWithCause();
            } catch (Exception e) {
                Assert.fail("noResult option should not throw server side exception");
            }

            long time = System.currentTimeMillis();
            service.timeoutMethod();
            time = System.currentTimeMillis() - time;
            assertThat(time).describedAs("noResult option should not wait for the server to return a response").isLessThan(2000);

            try {
                service.timeoutMethod();
                Assert.fail("noResult option should still wait for the server to ack the request and throw if the ack timeout is exceeded");
            } catch (Exception e) {
                assertThat(e).isInstanceOf(RemoteServiceAckTimeoutException.class);
            }
        } finally {
            client.shutdown();
            server.shutdown();
        }
    }

    @Test
    public void testNoAckWithoutResultInvocations() throws InterruptedException {
        RedissonClient server = createInstance();
        RedissonClient client = createInstance();
        try {
            server.getRemoteService().register(RemoteInterface.class, new RemoteImpl());

            // no ack fire and forget
            RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noAck().noResult();
            RemoteInterface service = client.getRemoteService().get(RemoteInterface.class, options);
            RemoteInterface invalidService = client.getRemoteService("Invalid").get(RemoteInterface.class, options);

            service.voidMethod("noAck/noResult", 100L);

            try {
                service.resultMethod(100L);
                Assert.fail();
            } catch (Exception e) {
                assertThat(e).isInstanceOf(IllegalArgumentException.class);
            }

            try {
                service.errorMethod();
            } catch (IOException e) {
                Assert.fail("noAck with noResult options should not throw server side exception");
            }

            try {
                service.errorMethodWithCause();
            } catch (Exception e) {
                Assert.fail("noAck with noResult options should not throw server side exception");
            }

            long time = System.currentTimeMillis();
            service.timeoutMethod();
            time = System.currentTimeMillis() - time;
            assertThat(time).describedAs("noAck with noResult options should not wait for the server to return a response").isLessThan(2000);

            try {
                invalidService.voidMethod("noAck/noResult", 21L);
            } catch (Exception e) {
                Assert.fail("noAck with noResult options should not throw any exception even while invoking a service in an unregistered services namespace");
            }
        } finally {
            client.shutdown();
            server.shutdown();
        }
    }
    
    @Test
    public void testMethodOverload() {
        RedissonClient r1 = createInstance();
        r1.getRemoteService().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = createInstance();
        RemoteInterface ri = r2.getRemoteService().get(RemoteInterface.class);
        
        assertThat(ri.methodOverload()).isEqualTo("methodOverload()");
        assertThat(ri.methodOverload(1l)).isEqualTo("methodOverload(Long lng)");
        assertThat(ri.methodOverload("")).isEqualTo("methodOverload(String str)");
        assertThat(ri.methodOverload("", 1l)).isEqualTo("methodOverload(String str, Long lng)");

        r1.shutdown();
        r2.shutdown();
    }
}
