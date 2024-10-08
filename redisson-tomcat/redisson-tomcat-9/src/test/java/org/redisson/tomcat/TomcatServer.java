package org.redisson.tomcat;

import java.net.MalformedURLException;

import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Manager;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomcatServer {
    
    private Tomcat tomcat = new Tomcat();
    private Context context;
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

        context = tomcat.addWebapp(contextPath, appBase + "/webapp");
    }

    void setManager(Manager manager) {
        if (context.getManager() != null) {
            throw new IllegalArgumentException("Manager already configured");
        }
        context.setManager(manager);
    }

    /**
     * Start the tomcat embedded server
     * @throws InterruptedException 
     */
    public void start() throws LifecycleException, InterruptedException {
        tomcat.start();
        tomcat.getConnector();
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