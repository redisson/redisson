package org.redisson.tomcat;

import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@RunWith(Parameterized.class)
public class RedissonSessionManagerTest {

    @Parameterized.Parameters(name= "{index} - {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {"context_memory.xml"},
            {"context_redis.xml"},
            {"context_redis_after_request.xml"},
            {"context_memory_after_request.xml"}
            });
    }

    @Parameterized.Parameter(0)
    public String contextName;

    @Before
    public void before() throws IOException {
        String basePath = "src/test/webapp/META-INF/";
        Files.deleteIfExists(Paths.get(basePath + "context.xml"));
        Files.copy(Paths.get(basePath + contextName), Paths.get(basePath + "context.xml"));
    }

    @Test
    public void testUpdateTwoServers_readValue() throws Exception {
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
            read(8080, executor, "test", "from_server2");

        } finally {
            Executor.closeIdleConnections();
            server1.stop();
            server2.stop();
        }
    }

    @Test
    public void testHttpSessionListener() throws Exception {
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

        invalidate(executor);

        Thread.sleep(500);

        Assert.assertEquals(2, TestHttpSessionListener.CREATED_INVOCATION_COUNTER);
        Assert.assertEquals(3, TestHttpSessionListener.DESTROYED_INVOCATION_COUNTER);

        Executor.closeIdleConnections();
        server1.stop();
        server2.stop();
        server3.stop();
    }

    @Test
    public void testUpdateTwoServers_twoValues() throws Exception {
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

    @Test
    public void testUpdateTwoServers() throws Exception {
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

    
    @Test
    public void testExpiration() throws Exception {
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
    
    @Test
    public void testSwitchServer() throws Exception {
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

    
    @Test
    public void testWriteReadRemove() throws Exception {
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
    
    @Test
    public void testRecreate() throws Exception {
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
    
    @Test
    public void testUpdate() throws Exception {
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


    @Test
    public void testInvalidate() throws Exception {
        File f = Paths.get("").toAbsolutePath().resolve("src/test/webapp/WEB-INF/redisson.yaml").toFile();
        Config config = Config.fromYAML(f);
        RedissonClient r = Redisson.create(config);
        r.getKeys().flushall();

        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();

        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);
        
        write(8080, executor, "test", "1234");
        Cookie cookie = cookieStore.getCookies().get(0);
        invalidate(executor);

        Executor.closeIdleConnections();
        
        executor = Executor.newInstance();
        cookieStore = new BasicCookieStore();
        cookieStore.addCookie(cookie);
        executor.use(cookieStore);
        read(8080, executor, "test", "null");
        invalidate(executor);
        
        Executor.closeIdleConnections();
        server.stop();

        TimeUnit.SECONDS.sleep(60);
        Assert.assertEquals(0, r.getKeys().count());
    }
    
    private void write(int port, Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:" + port + "/myapp/write?key=" + key + "&value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals("OK", response);
    }

    private void read(int port, Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:" + port + "/myapp/read?key=" + key;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals(value, response);
    }
    
    private void remove(Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:8080/myapp/remove?key=" + key;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals(value, response);
    }
    
    private void invalidate(Executor executor) throws IOException {
        String url = "http://localhost:8080/myapp/invalidate";
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals("OK", response);
    }

    private void recreate(Executor executor, String key, String value) throws IOException {
        String url = "http://localhost:8080/myapp/recreate?key=" + key + "&value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals("OK", response);
    }


    
}
