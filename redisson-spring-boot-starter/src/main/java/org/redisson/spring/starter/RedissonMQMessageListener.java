package org.redisson.spring.starter;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedissonMQMessageListener {

    String topic();
}
