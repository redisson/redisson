package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.codec.FstCodec;
import org.redisson.codec.SerializationCodec;
import org.redisson.remote.RemoteServiceTimeoutException;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonRemoteServiceTest extends BaseTest {

    public class Pojo {

        private String stringField;

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

    public class SerializablePojo extends Pojo implements Serializable {

        public SerializablePojo(String stringField) {
            super(stringField);
        }
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

    @Test
    public void testExecutorsAmountConcurrency() throws InterruptedException {

        // Redisson server and client
        final RedissonClient server = Redisson.create();
        final RedissonClient client = Redisson.create();

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
        Future[] clientFutures = new Future[clientAmount];
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
        for (Future clientFuture : clientFutures) {
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
        RedissonClient r1 = Redisson.create();
        r1.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = Redisson.create();
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
        RedissonClient r1 = Redisson.create();
        r1.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());
        
        RedissonClient r2 = Redisson.create();
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
        String name = "MyServiceName";

        RedissonClient r1 = Redisson.create();
        r1.getRemoteSerivce(name).register(RemoteInterface.class, new RemoteImpl());

        RedissonClient r2 = Redisson.create();
        RemoteInterface ri = r2.getRemoteSerivce(name).get(RemoteInterface.class);

        ri.voidMethod("someName", 100L);
        assertThat(ri.resultMethod(100L)).isEqualTo(200);

        r1.shutdown();
        r2.shutdown();
    }

    @Test
    public void testProxyToStringEqualsAndHashCode() {
        RedissonClient client = Redisson.create();
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

            try {
                assertThat(service.resultMethod(21L)).isEqualTo(42L);
            } catch (Exception e) {
                Assert.fail("Should be compatible with FstCodec");
            }

            try {
                assertThat(service.doSomethingWithPojo(new SerializablePojo("test")).getStringField()).isEqualTo("test");
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
            server.getRemoteSerivce().register(RemoteInterface.class, new RemoteImpl());

            RemoteInterface service = client.getRemoteSerivce().get(RemoteInterface.class);

            try {
                assertThat(service.resultMethod(21L)).isEqualTo(42L);
            } catch (Exception e) {
                Assert.fail("Should be compatible with SerializationCodec");
            }

            try {
                assertThat(service.doSomethingWithPojo(new SerializablePojo("test")).getStringField()).isEqualTo("test");
            } catch (Exception e) {
                Assert.fail("Should be compatible with SerializationCodec");
            }

            try {
                assertThat(service.doSomethingWithPojo(new Pojo("test")).getStringField()).isEqualTo("test");
                Assert.fail("SerializationCodec should not be able to serialize a not serializable class");
            } catch (Exception e) {
                assertThat(e).isInstanceOf(NotSerializableException.class);
                assertThat(e.getMessage()).contains("Pojo");
            }
        } finally {
            client.shutdown();
            server.shutdown();
        }
    }
}
