package org.redisson.tomcat;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

public class TestHttpSessionListener implements HttpSessionListener {

    public static int CREATED_INVOCATION_COUNTER;
    public static int DESTROYED_INVOCATION_COUNTER;
    
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        CREATED_INVOCATION_COUNTER++;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        DESTROYED_INVOCATION_COUNTER++;
    }

}
