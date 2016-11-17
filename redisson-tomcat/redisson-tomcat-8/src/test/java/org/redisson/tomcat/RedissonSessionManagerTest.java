package org.redisson.tomcat;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.Assert;
import org.junit.Test;

public class RedissonSessionManagerTest {

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
        
        Executor.closeIdleConnections();
        server.stop();
    }
    
    private void write(Executor executor, String key, String value) throws IOException, ClientProtocolException {
        String url = "http://localhost:8080/myapp/write?key=" + key + "&value=" + value;
        String response = executor.execute(Request.Get(url)).returnContent().asString();
        Assert.assertEquals("OK", response);
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
