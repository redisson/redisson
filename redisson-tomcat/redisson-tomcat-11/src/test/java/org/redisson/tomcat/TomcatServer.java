package org.redisson.tomcat;

import jakarta.servlet.ServletException;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;

public class TomcatServer {
    
    private Tomcat tomcat = new Tomcat();
    private int port;
    private boolean isRunning;

    private static final Logger LOG = LoggerFactory.getLogger(TomcatServer.class);
    private static final boolean isInfo = LOG.isInfoEnabled();

    private final Context c;
    private RedissonSessionManager sessionManager;

    public TomcatServer(String contextPath, int port, String appBase) throws MalformedURLException, ServletException {
        if(contextPath == null || appBase == null || appBase.length() == 0) {
            throw new IllegalArgumentException("Context path or appbase should not be null");
        }
        if(!contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }

        tomcat.setBaseDir("."); // location where temp dir is created
        tomcat.setPort(port);
        tomcat.getHost().setAppBase(".");

        c = tomcat.addWebapp(contextPath, appBase + "/webapp");
    }

    /**
     * Start the tomcat embedded server
     * @throws InterruptedException 
     */
    public void start() throws LifecycleException {
        tomcat.start();
        tomcat.getConnector();
        sessionManager = (RedissonSessionManager) c.getManager();
        isRunning = true;
    }

    /**
     * Stop the tomcat embedded server
     */
    public void stop() throws LifecycleException {
        if(!isRunning) {
            LOG.warn("Tomcat server is not running @ port={}", port);
            return;
        }

        if(isInfo) LOG.info("Stopping the Tomcat server");

        tomcat.stop();
        tomcat.destroy();
        tomcat.getServer().await();
        isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public RedissonSessionManager getSessionManager() {
        return sessionManager;
    }
}