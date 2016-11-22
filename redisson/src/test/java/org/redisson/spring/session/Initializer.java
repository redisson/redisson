package org.redisson.spring.session;

import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

public class Initializer extends AbstractHttpSessionApplicationInitializer {

    public static Class<?> CONFIG_CLASS = Config.class;
    
    public Initializer() {
        super(CONFIG_CLASS);
    }
    
}
