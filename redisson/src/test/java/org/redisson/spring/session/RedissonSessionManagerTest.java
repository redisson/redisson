package org.redisson.spring.session;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.RedisRunner;
import org.redisson.RedisRunner.KEYSPACE_EVENTS_OPTIONS;
import org.redisson.RedissonRuntimeEnvironment;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class RedissonSessionManagerTest {

    private static RedisRunner.RedisProcess defaultRedisInstance;
    
    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            defaultRedisInstance.stop();
        }
    }
    
    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            defaultRedisInstance = new RedisRunner()
                    .nosave()
                    .port(6379)
                    .randomDir()
                    .notifyKeyspaceEvents(KEYSPACE_EVENTS_OPTIONS.E, 
                                        KEYSPACE_EVENTS_OPTIONS.x,
                                        KEYSPACE_EVENTS_OPTIONS.g)
                    .run();

        }
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
    public void testExpire() throws Exception {
        Initializer.CONFIG_CLASS = ConfigTimeout.class;
        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();
        WebApplicationContext wa = WebApplicationContextUtils.getRequiredWebApplicationContext(server.getServletContext());
        SessionEventsListener listener = wa.getBean(SessionEventsListener.class);
        
        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);
        
        write(executor, "test", "1234");
        Cookie cookie = cookieStore.getCookies().get(0);

        Assert.assertEquals(1, listener.getSessionCreatedEvents());
        Assert.assertEquals(0, listener.getSessionExpiredEvents());

        Executor.closeIdleConnections();

        Thread.sleep(6000);
        
        Assert.assertEquals(1, listener.getSessionCreatedEvents());
        Assert.assertEquals(1, listener.getSessionExpiredEvents());
        
        executor = Executor.newInstance();
        cookieStore = new BasicCookieStore();
        cookieStore.addCookie(cookie);
        executor.use(cookieStore);
        read(executor, "test", "null");
        
        Assert.assertEquals(2, listener.getSessionCreatedEvents());

        write(executor, "test", "1234");
        Thread.sleep(3000);
        read(executor, "test", "1234");
        Thread.sleep(3000);
        Assert.assertEquals(1, listener.getSessionExpiredEvents());
        Thread.sleep(1000);
        Assert.assertEquals(1, listener.getSessionExpiredEvents());
        Thread.sleep(3000);
        Assert.assertEquals(2, listener.getSessionExpiredEvents());
        
        Executor.closeIdleConnections();
        server.stop();
    }

    @Test
    public void testInvalidate() throws Exception {
        // start the server at http://localhost:8080/myapp
        TomcatServer server = new TomcatServer("myapp", 8080, "src/test/");
        server.start();
        WebApplicationContext wa = WebApplicationContextUtils.getRequiredWebApplicationContext(server.getServletContext());
        SessionEventsListener listener = wa.getBean(SessionEventsListener.class);
        
        Executor executor = Executor.newInstance();
        BasicCookieStore cookieStore = new BasicCookieStore();
        executor.use(cookieStore);
        
        write(executor, "test", "1234");
        Cookie cookie = cookieStore.getCookies().get(0);
        
        Thread.sleep(50);
        
        Assert.assertEquals(1, listener.getSessionCreatedEvents());
        Assert.assertEquals(0, listener.getSessionDeletedEvents());
        
        invalidate(executor);
        
        Assert.assertEquals(1, listener.getSessionCreatedEvents());
        Assert.assertEquals(1, listener.getSessionDeletedEvents());

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
