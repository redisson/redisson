package org.redisson;

import io.netty.handler.codec.EncoderException;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.codec.FstCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.core.RemoteInvocationOptions;
import org.redisson.remote.RRemoteAsync;
import org.redisson.remote.RemoteServiceAckTimeoutException;
import org.redisson.remote.RemoteServiceTimeoutException;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import io.netty.util.concurrent.Future;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

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
        
        Future<Void> voidMethod(String name, Long param);
        
        Future<Long> resultMethod(Long value);
        
        Future<Void> errorMethod();
        
        Future<Void> errorMethodWithCause();
        
        Future<Void> timeoutMethod();
        
    }
    
    @RRemoteAsync(RemoteInterface.class)
    public interface RemoteInterfaceWrongMethodAsync {
        
        Future<Void> voidMethod1(String name, Long param);
        
        Future<Long> resultMethod(Long value);
        
    }
    
    @RRemoteAsync(RemoteInterface.class)
    public interface RemoteInterfaceWrongParamsAsync {
        
        Future<Void> voidMethod(Long param, String name);
        
        Future<Long> resultMethod(Long value);
        
    }

    
    public interface RemoteInterface {
        
        void voidMethod(String name, Long param);

        Long resultMethod(Long value);
        
        void errorMethod() throws IOException;
        
        void errorMethodWithCause();
        
        void timeoutMethod() throws InterruptedException;

        Pojo doSomethingWithPojo(Pojo pojo);

        SerializablePojo doSomethingWithSerializablePojo(SerializablePojo pojo);

    }
    
    public class RemoteImpl implements RemoteInterface {

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
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testWrongMethodAsync() throws InterruptedException {
        redisson.getRemoteSerivce().get(RemoteInterfaceWrongMethodAsync.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongParamsAsync() throws InterruptedException {
        redisson.getRemoteSerivce().get(RemoteInterfaceWrongParamsAsync.class);
    }
    
    @Test
    public void testAsync() throws InterruptedException {
        RedissonClient r1 = createInstance();
        r1.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = createInstance();
        RemoteInterfaceAsync ri = r2.getRemoteSerivce().get(RemoteInterfaceAsync.class);
        
        Future<Void> f = ri.voidMethod("someName", 100L);
        f.sync();
        Future<Long> resFuture = ri.resultMethod(100L);
        resFuture.sync();
        assertThat(resFuture.getNow()).isEqualTo(200);

        r1.shutdown();
        r2.shutdown();
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
        server.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl() {
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
                        RemoteInterface ri = client.getRemoteSerivce().get(RemoteInterface.class, clientAmount * 3, TimeUnit.SECONDS, clientAmount * 3, TimeUnit.SECONDS);
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
        r1.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = createInstance();
        RemoteInterface ri = r2.getRemoteSerivce().get(RemoteInterface.class, 1, TimeUnit.SECONDS);
        
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
        r1.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = createInstance();
        RemoteInterface ri = r2.getRemoteSerivce().get(RemoteInterface.class);
        
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
        
        r1.shutdown();
        r2.shutdown();
    }

    @Test
    public void testInvocationWithServiceName() {
        RedissonClient server = createInstance();
        RedissonClient client = createInstance();

        server.getRemoteSerivce("MyServiceNamespace").register(RemoteInterface.class, new RemoteImpl());

        RemoteInterface serviceRemoteInterface = client.getRemoteSerivce("MyServiceNamespace").get(RemoteInterface.class);
        RemoteInterface otherServiceRemoteInterface = client.getRemoteSerivce("MyOtherServiceNamespace").get(RemoteInterface.class);
        RemoteInterface defaultServiceRemoteInterface = client.getRemoteSerivce().get(RemoteInterface.class);

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
            RemoteInterface service = client.getRemoteSerivce().get(RemoteInterface.class);

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
            server.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());

            RemoteInterface service = client.getRemoteSerivce().get(RemoteInterface.class);

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
                assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
                assertThat(e.getCause().getMessage()).contains("Pojo does not implement Serializable");
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
            server.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());

            RemoteInterface service = client.getRemoteSerivce().get(RemoteInterface.class);

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
            server.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());

            // no ack but an execution timeout of 1 second
            RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noAck().expectResultWithin(1, TimeUnit.SECONDS);
            RemoteInterface service = client.getRemoteSerivce().get(RemoteInterface.class, options);

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
            server.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());

            // no ack but an execution timeout of 1 second
            RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noAck().expectResultWithin(1, TimeUnit.SECONDS);
            RemoteInterfaceAsync service = client.getRemoteSerivce().get(RemoteInterfaceAsync.class, options);

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
            server.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());

            // fire and forget with an ack timeout of 1 sec
            RemoteInvocationOptions options = RemoteInvocationOptions.defaults().expectAckWithin(1, TimeUnit.SECONDS).noResult();
            RemoteInterface service = client.getRemoteSerivce().get(RemoteInterface.class, options);

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
            server.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());

            // no ack fire and forget
            RemoteInvocationOptions options = RemoteInvocationOptions.defaults().noAck().noResult();
            RemoteInterface service = client.getRemoteSerivce().get(RemoteInterface.class, options);
            RemoteInterface invalidService = client.getRemoteSerivce("Invalid").get(RemoteInterface.class, options);

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
}
