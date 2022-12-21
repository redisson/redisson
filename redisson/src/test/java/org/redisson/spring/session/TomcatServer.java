package org.redisson.spring.session;

import java.io.File;
import java.net.MalformedURLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

public class TomcatServer {
    
    private Tomcat tomcat = new Tomcat();
    private StandardContext ctx;

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

        ctx = (StandardContext) tomcat.addWebapp(contextPath, appBase);
        ctx.setDelegate(true);
        
        File additionWebInfClasses = new File("target/test-classes");
        StandardRoot resources = new StandardRoot();
        DirResourceSet webResourceSet = new DirResourceSet();
        webResourceSet.setBase(additionWebInfClasses.toString());
        webResourceSet.setWebAppMount("/WEB-INF/classes");
        resources.addPostResources(webResourceSet);
        ctx.setResources(resources);
    }

    /**
     * Start the tomcat embedded server
     */
    public void start() throws LifecycleException {
        tomcat.start();
    }

    /**
     * Stop the tomcat embedded server
     */
    public void stop() throws LifecycleException {
        tomcat.stop();
        tomcat.destroy();
        tomcat.getServer().await();
    }

    public ServletContext getServletContext() {
        return ctx.getServletContext();
    }
    

}