package org.redisson.spring.starter;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
public class RedissonListenerConfiguration implements ApplicationContextAware, SmartInitializingSingleton {

    private final static Logger log = LoggerFactory.getLogger(RedissonListenerConfiguration.class);

    private ConfigurableApplicationContext applicationContext;
    private StandardEnvironment environment;

    public RedissonListenerConfiguration(StandardEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(RedissonMQMessageListener.class)
                .entrySet().stream().filter(entry -> !ScopedProxyUtils.isScopedTarget(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        beans.forEach(this::createRedissonListener);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

    public void createRedissonListener(String name, Object bean) {
        RedissonClient redissonClient = applicationContext.getBean(RedissonClient.class);

        Class<?> clazz = AopProxyUtils.ultimateTargetClass(bean);

        RedissonMQMessageListener annotation = clazz.getAnnotation(RedissonMQMessageListener.class);

        String tn = this.environment.resolvePlaceholders(annotation.topic());
        RTopic topic = redissonClient.getTopic(tn);

        if (MessageListener.class.isAssignableFrom(clazz)) {
            topic.addListener((Class) getMessageType(bean), (MessageListener) bean);
        }

    }

    private Type getMessageType(Object listener) {
        Class<?> targetClass = null;
        if (listener != null) {
            targetClass = AopProxyUtils.ultimateTargetClass(listener);
        }
        Type matchedGenericInterface = null;
        while (Objects.nonNull(targetClass)) {
            Type[] interfaces = targetClass.getGenericInterfaces();
            if (Objects.nonNull(interfaces)) {
                for (Type type : interfaces) {
                    if (type instanceof ParameterizedType &&
                            (Objects.equals(((ParameterizedType) type).getRawType(), MessageListener.class) || Objects.equals(((ParameterizedType) type).getRawType(), MessageListener.class))) {
                        matchedGenericInterface = type;
                        break;
                    }
                }
            }
            targetClass = targetClass.getSuperclass();
        }
        if (Objects.isNull(matchedGenericInterface)) {
            return Object.class;
        }

        Type[] actualTypeArguments = ((ParameterizedType) matchedGenericInterface).getActualTypeArguments();
        if (Objects.nonNull(actualTypeArguments) && actualTypeArguments.length > 0) {
            return actualTypeArguments[0];
        }
        return Object.class;
    }
}
