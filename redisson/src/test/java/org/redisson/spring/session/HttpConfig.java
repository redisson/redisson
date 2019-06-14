package org.redisson.spring.session;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.spring.session.config.EnableRedissonHttpSession;
import org.redisson.spring.session.config.EnableRedissonWebSession;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

@EnableRedissonHttpSession
//@EnableRedissonWebSession
public class HttpConfig {

    @Bean
    public RedissonClient redisson() {
        return Redisson.create();
    }
    
    @Bean
    public SessionEventsListener listener() {
        return new SessionEventsListener();
    }
    
    @Bean(WebHttpHandlerBuilder.WEB_HANDLER_BEAN_NAME)
    public WebHandler dispatcherHandler(ApplicationContext context) {
        return new DispatcherHandler(context);
    }
    
}
