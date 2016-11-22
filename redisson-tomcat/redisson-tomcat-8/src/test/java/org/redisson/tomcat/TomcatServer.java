package org.redisson.tomcat;

import java.net.MalformedURLException;

import javax.servlet.ServletException;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomcatServer {
    
    private Tomcat tomcat = new Tomcat();
    private int port;
    private boolean isRunning;

    private static final Logger LOG = LoggerFactory.getLogger(TomcatServer.class);
    private static final boolean isInfo = LOG.isInfoEnabled();

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

        tomcat.addWebapp(contextPath, appBase + "webapp");
    }

    /**
     * Start the tomcat embedded server
     */
    public void start() throws LifecycleException {
        tomcat.start();
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

}