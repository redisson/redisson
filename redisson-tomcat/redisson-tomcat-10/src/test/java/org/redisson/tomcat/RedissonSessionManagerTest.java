package org.redisson.tomcat;

import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.RedissonClient;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Testcontainers
public class RedissonSessionManagerTest {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
                                                        .withFixedExposedPort(6379, 6379);

    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"context_memory.xml"},
            {"context_redis.xml"},
            {"context_redis_after_request.xml"},
            {"context_memory_after_request.xml"}
            });
    }

    private void prepare(String contextName) throws IOException {
        String basePath = "src/test/webapp/META-INF/";
        Files.deleteIfExists(Paths.get(basePath + "context.xml"));
        Files.copy(Paths.get(basePath + contextName), Paths.get(basePath + "context.xml"));
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testUpdateMaxInactiveInterval(String contextName) throws Exception {
        prepare(contextName);
        TomcatServer server1 = new TomcatServer("myapp", 8080, "src/test/");
        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        try {
            server1.start();
            server2.start();
            
            Executor executor = Executor.newInstance();
            BasicCookieStore cookieStore = new BasicCookieStore();
            executor.use(cookieStore);
            
            write(8080, executor, "test", "from_server1");
            write(8081, executor, "test", "from_server2");
            
            writeInternal(8080, executor, 3000);
            readInternal(8081, executor, 3000);
            
        } finally {
            Executor.closeIdleConnections();
            server1.stop();
            server2.stop();
        }
    }
    
    @ParameterizedTest
    @MethodSource("data")
    public void testUpdateTwoServers_readValue(String contextName) throws Exception {
        prepare(contextName);
        TomcatServer server1 = new TomcatServer("myapp", 8080, "src/test/");
        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        try {
            server1.start();
            server2.start();

            Executor executor = Executor.newInstance();
            BasicCookieStore cookieStore = new BasicCookieStore();
            executor.use(cookieStore);

            write(8080, executor, "test", "from_server1");
            write(8081, executor, "test", "from_server2");

            read(8080, executor, "test", "from_server2");
            read(8081, executor, "test", "from_server2");

        } finally {
            Executor.closeIdleConnections();
            server1.stop();
            server2.stop();
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testHttpSessionListener(String contextName) throws Exception {
        prepare(contextName);
        TomcatServer server1 = new TomcatServer("myapp", 8080, "src/test/");
        server1.start();

        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        server2.start();

        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);

        TestHttpSessionListener.CREATED_INVOCATION_COUNTER = 0;
        TestHttpSessionListener.DESTROYED_INVOCATION_COUNTER = 0;

        write(8080, executor, "test", "1234");

        TomcatServer server3 = new TomcatServer("myapp", 8082, "src/test/");
        server3.start();

        invalidate(8080, executor);

        Thread.sleep(500);

        Assertions.assertEquals(2, TestHttpSessionListener.CREATED_INVOCATION_COUNTER);
        Assertions.assertEquals(3, TestHttpSessionListener.DESTROYED_INVOCATION_COUNTER);

        Executor.closeIdleConnections();
        server1.stop();
        server2.stop();
        server3.stop();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testCollection(String contextName) throws Exception {
        prepare(contextName);
        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();

        Executor executor = Executor.newInstance();

        writeColl(8080, executor, "test", "1234");

        Executor.closeIdleConnections();
        server.stop();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testUpdateTwoServers_twoValues(String contextName) throws Exception {
        prepare(contextName);
        TomcatServer server1 = new TomcatServer("myapp", 8080, "src/test/");
        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        try {
            server1.start();
            server2.start();

            Executor executor = Executor.newInstance();
            BasicCookieStore cookieStore = new BasicCookieStore();
            executor.use(cookieStore);

            write(8080, executor, "key1", "value1");
            write(8080, executor, "key2", "value1");

            write(8081, executor, "key1", "value2");
            write(8081, executor, "key2", "value2");

            read(8080, executor, "key1", "value2");
            read(8080, executor, "key2", "value2");

        } finally {
            Executor.closeIdleConnections();
            server1.stop();
            server2.stop();
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testUpdateTwoServers(String contextName) throws Exception {
        prepare(contextName);
        TomcatServer server1 = new TomcatServer("myapp", 8080, "src/test/");
        server1.start();
        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        server2.start();

        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);

        write(8080, executor, "test", "1234");

        read(8081, executor, "test", "1234");
        read(8080, executor, "test", "1234");
        write(8080, executor, "test", "324");
        read(8081, executor, "test", "324");
        
        Executor.closeIdleConnections();
        server1.stop();
        server2.stop();
    }


    @ParameterizedTest
    @MethodSource("data")
    public void testInvalidateListener(String contextName) throws Exception {
        prepare(contextName);
        TomcatServer server1 = new TomcatServer("myapp", 8080, "src/test/");
        server1.start();
        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        server2.start();

        server1.getSessionManager().getRedisson().getKeys().flushall();

        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);

        write(8080, executor, "test", "1234");
        read(8081, executor, "test", "1234");

        TestHttpSessionListener.DESTROYED_INVOCATION_COUNTER = 0;
        invalidate(8081, executor);

        Executor.closeIdleConnections();
        server1.stop();
        server2.stop();

        Assertions.assertEquals(2, TestHttpSessionListener.DESTROYED_INVOCATION_COUNTER);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testExpiration(String contextName) throws Exception {
        prepare(contextName);
        TomcatServer server1 = new TomcatServer("myapp", 8080, "src/test/");
        server1.start();

        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);
        
        write(8080, executor, "test", "1234");

        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        server2.start();

        Thread.sleep(30000);
        
        read(8081, executor, "test", "1234");

        Thread.sleep(40000);
        
        executor.use(cookieStore);
        read(8080, executor, "test", "1234");
        
        Executor.closeIdleConnections();
        server1.stop();
        server2.stop();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testSwitchServer(String contextName) throws Exception {
        prepare(contextName);
        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();

        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);
        
        write(8080, executor, "test", "1234");
        System.out.println("done1");
        Cookie cookie = cookieStore.getCookies().get(0);

        Executor.closeIdleConnections();
        server.stop();

        server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();
        
        executor = Executor.newInstance();
        cookieStore = new BasicCookieStore();
        cookieStore.addCookie(cookie);
        executor.use(cookieStore);
        read(8080, executor, "test", "1234");
        remove(executor, "test", "null");
        
        Executor.closeIdleConnections();
        server.stop();
    }


    @ParameterizedTest
    @MethodSource("data")
    public void testWriteReadRemove(String contextName) throws Exception {
        prepare(contextName);
        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();

        Executor executor = Executor.newInstance();
        
        write(8080, executor, "test", "1234");
        read(8080, executor, "test", "1234");
        remove(executor, "test", "null");
        
        Executor.closeIdleConnections();
        server.stop();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testRecreate(String contextName) throws Exception {
        prepare(contextName);
        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();

        Executor executor = Executor.newInstance();
        
        write(8080, executor, "test", "1");
        recreate(executor, "test", "2");
        read(8080, executor, "test", "2");
        
        Executor.closeIdleConnections();
        server.stop();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testUpdate(String contextName) throws Exception {
        prepare(contextName);
        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();

        Executor executor = Executor.newInstance();
        
        write(8080, executor, "test", "1");
        read(8080, executor, "test", "1");
        write(8080, executor, "test", "2");
        read(8080, executor, "test", "2");
        
        Executor.closeIdleConnections();
        server.stop();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testInvalidate(String contextName) throws Exception {
        prepare(contextName);

        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();

        RedissonClient r = server.getSessionManager().getRedisson();
        r.getKeys().flushall();

        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);
        
        write(8080, executor, "test", "1234");
        Cookie cookie = cookieStore.getCookies().get(0);
        invalidate(8080, executor);

        Executor.closeIdleConnections();
        
        executor = Executor.newInstance();
        cookieStore = new BasicCookieStore();
        cookieStore.addCookie(cookie);
        executor.use(cookieStore);
        read(8080, executor, "test", "null");
        invalidate(8080, executor);
        
        TimeUnit.SECONDS.sleep(61);
        Assertions.assertEquals(0, r.getKeys().count());

        Executor.closeIdleConnections();
        server.stop();
    }

    private void writeColl(int port, Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:" + port + "/myapp/write-coll?key=" + key + "&value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assertions.assertEquals("OK", response);
    }

    private void write(int port, Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:" + port + "/myapp/write?key=" + key + "&value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assertions.assertEquals("OK", response);
    }

    private void read(int port, Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:" + port + "/myapp/read?key=" + key;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assertions.assertEquals(value, response);
    }
    
    private void writeInternal(int port, Executor executor, Object value) throws IOException {
        String url = "http://localhost:" + port + "/myapp/writeInternal?value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assertions.assertEquals("OK", response);
    }
    
    private void readInternal(int port, Executor executor, Object value) throws IOException {
        String url = "http://localhost:" + port + "/myapp/readInternal";
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assertions.assertEquals(value.toString(), response);
    }
    
    private void remove(Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:8080/myapp/remove?key=" + key;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assertions.assertEquals(value, response);
    }
    
    private void invalidate(int port, Executor executor) throws IOException {
        String url = "http://localhost:" + port + "/myapp/invalidate";
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assertions.assertEquals("OK", response);
    }

    private void recreate(Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:8080/myapp/recreate?key=" + key + "&value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assertions.assertEquals("OK", response);
    }


    
}
