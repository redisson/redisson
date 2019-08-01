package org.redisson.tomcat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedissonSessionManagerTest {

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
        
        write(executor, "test", "1234");
        
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
    public void testUpdateTwoServers() throws Exception {
        TomcatServer server1 = new TomcatServer("myapp", 8080, "src/test/");
        server1.start();

        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);
        
        write(executor, "test", "1234");

        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        server2.start();

        read(8081, executor, "test", "1234");
        read(executor, "test", "1234");
        write(executor, "test", "324");
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
        
        write(executor, "test", "1234");

        TomcatServer server2 = new TomcatServer("myapp", 8081, "src/test/");
        server2.start();

        Thread.sleep(30000);
        
        read(8081, executor, "test", "1234");

        Thread.sleep(40000);
        
        read(executor, "test", "1234");
        
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
        
        write(executor, "test", "1234");
        Cookie cookie = cookieStore.getCookies().get(0);

        Executor.closeIdleConnections();
        server.stop();

        server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();
        
        executor = Executor.newInstance();
        cookieStore = new BasicCookieStore();
        cookieStore.addCookie(cookie);
        executor.use(cookieStore);
        read(executor, "test", "1234");
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
        
        write(executor, "test", "1234");
        read(executor, "test", "1234");
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
        
        write(executor, "test", "1");
        recreate(executor, "test", "2");
        read(executor, "test", "2");
        
        Executor.closeIdleConnections();
        server.stop();
    }
    
    @Test
    public void testUpdate() throws Exception {
        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();

        Executor executor = Executor.newInstance();
        
        write(executor, "test", "1");
        read(executor, "test", "1");
        write(executor, "test", "2");
        read(executor, "test", "2");
        
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
        
        write(executor, "test", "1234");
        Cookie cookie = cookieStore.getCookies().get(0);
        invalidate(executor);

        Executor.closeIdleConnections();
        
        executor = Executor.newInstance();
        cookieStore = new BasicCookieStore();
        cookieStore.addCookie(cookie);
        executor.use(cookieStore);
        read(executor, "test", "null");
        invalidate(executor);
        
        Executor.closeIdleConnections();
        server.stop();
        
        Assert.assertEquals(0, r.getKeys().count());
    }
    
    private void write(Executor executor, String key, String value) throws IOException, ClientProtocolException {
        String url = "http://localhost:8080/myapp/write?key=" + key + "&value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals("OK", response);
    }
    
    private void read(int port, Executor executor, String key, String value) throws IOException, ClientProtocolException {
        String url = "http://localhost:" + port + "/myapp/read?key=" + key;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals(value, response);
    }
    
    private void read(Executor executor, String key, String value) throws IOException, ClientProtocolException {
        String url = "http://localhost:8080/myapp/read?key=" + key;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals(value, response);
    }

    private void remove(Executor executor, String key, String value) throws IOException, ClientProtocolException {
        String url = "http://localhost:8080/myapp/remove?key=" + key;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals(value, response);
    }
    
    private void invalidate(Executor executor) throws IOException, ClientProtocolException {
        String url = "http://localhost:8080/myapp/invalidate";
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals("OK", response);
    }

    private void recreate(Executor executor, String key, String value) throws IOException, ClientProtocolException {
        String url = "http://localhost:8080/myapp/recreate?key=" + key + "&value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals("OK", response);
    }
    
}
