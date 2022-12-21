package org.redisson.spring.session;

import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

public class HttpInitializer extends AbstractHttpSessionApplicationInitializer {

    public static Class<?> CONFIG_CLASS = HttpConfig.class;
    
    public HttpInitializer() {
        super(CONFIG_CLASS);
    }

    @Override
    public void onStartup(jakarta.servlet.ServletContext servletContext) {
    }
}
