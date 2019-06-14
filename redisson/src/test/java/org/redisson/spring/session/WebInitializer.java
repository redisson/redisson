package org.redisson.spring.session;

import org.springframework.web.server.adapter.AbstractReactiveWebInitializer;

public class WebInitializer extends AbstractReactiveWebInitializer {

    public static Class<?> CONFIG_CLASS = HttpConfig.class;
    
    @Override
    protected Class<?>[] getConfigClasses() {
        return new Class[] {CONFIG_CLASS};
    }
    
}
