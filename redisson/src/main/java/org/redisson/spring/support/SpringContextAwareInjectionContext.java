/**
 * Copyright (c) 2013-2019 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.spring.support;

import io.netty.util.internal.PlatformDependent;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.redisson.client.codec.Codec;
import org.redisson.misc.AbstractInjectionContext;
import org.redisson.misc.RedissonObjectBuilder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.expression.BeanExpressionContextAccessor;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All of the RedissonClient instances and redisson objects and services, available at the time of construction, are
 * registered as variables in the evaluation springContext.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 *
 */
class SpringContextAwareInjectionContext extends AbstractInjectionContext {

    /**
     *  (([A-Za-z]+)) Matches alpha characters for one or more times at the beginning as the first caption group
     *  \\(' Should have left bracket and a single quote after the word
     *  [^] for negative selection
     *  ([^'()]+) within the capture group shoud not be any more brackets or quotes. For one or more times.
     *  '\\) Should have a single quote and a right bracket to end the expression.
     *
     *  Example: RMap('myMap')
     */
    private static final Pattern redissonExpressionPattern = Pattern.compile("^([A-Za-z]+)\\('([^'()]+)'\\)$");

    /**
     *  [^#${]* There should not be a start of any expression before the legitimate start token "#{", other things're OK.
     *  (#\{[^{}]+}) Matches start token "#{" and end toke "}". There should be no nested expressions.
     *  [^}]* There should not be another end token(s) "}" after the legitimate end.
     */
    private static final Pattern spelExpressionPattern = Pattern.compile("^[^#${]*(#\\{[^{}]+})[^}]*$");

    /**
     *  [^$]* There should not be a start of a property token "$" before the legitimate start token "${", other things're OK.
     *  \\${ the expression start
     *  ([A-Za-z0-9.]+) one or more alphanumeric and period (.) are allowed and grouped in to a capture group
     *  }.*$ the expression end and anything additional is welcome.
     */
    private static final Pattern propertyExpressionPattern = Pattern.compile("^[^$]*\\$\\{([A-Za-z0-9.]+)}.*$");

    private static final ParserContext SPEL_TEMP_CONTEXT = new TemplateParserContext();

    private final ConcurrentMap<Integer, Expression> cache = PlatformDependent.<Integer, Expression>newConcurrentHashMap();

    private final ConfigurableEnvironment environment;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final StandardEvaluationContext springContext;
    private final RedissonClient defaultRedisson;

    SpringContextAwareInjectionContext(BeanFactory beanFactory) {
        final ConfigurableBeanFactory cbf = (ConfigurableBeanFactory) beanFactory;
        springContext = new StandardEvaluationContext(new BeanExpressionContext(cbf, new SimpleThreadScope()));
        springContext.addPropertyAccessor(new BeanExpressionContextAccessor());
        springContext.addPropertyAccessor(new BeanFactoryAccessor());
        springContext.addPropertyAccessor(new MapAccessor());
        springContext.addPropertyAccessor(new EnvironmentAccessor());
        springContext.setTypeLocator(new StandardTypeLocator(cbf.getBeanClassLoader()));
        springContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
        if (cbf.getConversionService() != null) {
            springContext.setTypeConverter(new StandardTypeConverter(cbf.getConversionService()));
        }

        environment = cbf.getBean(ConfigurableEnvironment.class);

        final ListableBeanFactory factory = (ListableBeanFactory) cbf;
        Map<String, RedissonClient> candidates = factory.getBeansOfType(RedissonClient.class);
        if (candidates.size() == 0) {
            throw new NoSuchBeanDefinitionException(RedissonClient.class);
        } else if (candidates.size() == 1) {
            this.defaultRedisson = candidates.values().iterator().next();
        } else if (factory.containsBean("redisson")) {
            final Object redisson = factory.getBean("redisson");
            if (redisson instanceof RedissonClient) {
                this.defaultRedisson = (RedissonClient) redisson;
            } else {
                this.defaultRedisson = null;
            }
        } else if (factory.containsBean("redissonClient")) {
            final Object redisson = factory.getBean("redissonClient");
            if (redisson instanceof RedissonClient) {
                this.defaultRedisson = (RedissonClient) redisson;
            } else {
                this.defaultRedisson = null;
            }
        } else {
            this.defaultRedisson = null;
        }
    }

    /**
     * Supported format:
     *     "@beanName" : bean lookup
     *     "@beanName.getXX()" : bean lookup
     *     "#{myProperties['mykeys.map1']}" : string - create
     *     "${mykeys.map1}" : string - create
     *     "RMap('#{myProperty['abc']}')" : string - create
     *     "RMap('${abc}')" : string - create
     *     "RMap('abc')" : create
     *     "myKey" : create
     *
     * TODO:
     *     Reigister created instance as a bean in Spring
     */
    @Override
    protected <T, C extends Codec > T resolveRedissonObjects(RedissonClient redissonClient, Class<T> expected, String name, Class<C> codecClass) {
        String value = tryEvaluateProperty(name);

        try {
            final Object result = tryEvaluateSpEL(value, expected);
            if (result != null) {
                if (result instanceof String) {
                    value = (String) result;
                } else {
                    return (T) result;
                }
            }
        } catch (SpelEvaluationException e) {
                throw new IllegalArgumentException(
                        "Failed to resolve expression [" + name + "] for type " + expected.getName(), e);
        }

        final Matcher redissonExpressionMatcher = getRedissonExpressionMatcher(value);
        if (redissonExpressionMatcher.matches()) {
            final String objName = redissonExpressionMatcher.group(1);
            final Class<?> supportedTypes = RedissonObjectBuilder.getSupportedTypes(objName);
            if (supportedTypes == null) {
                throw new IllegalArgumentException("Redisson does not support the \"" + objName + "\" mentioned in \"" + name + "\"");
            }
            expected = (Class<T>) supportedTypes;
            value = redissonExpressionMatcher.group(2);
        }

        return super.resolveRedissonObjects(redissonClient, expected, value, codecClass);
    }

    @Override
    protected RedissonClient resolveRedisson(String expectedName, Class targetType, RInject rInject) {
        if (RedissonClient.class.isAssignableFrom(targetType)) {
            if ("".equals(expectedName)) {
                return getDefaultRedissonOrException();
            } else {
                try {
                    return evaluateSpELForRedisson(expectedName);//resolve SpEL
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "No default RedissonClient bean discovered, please specify the correct bean in RInject.name() or RInject.value().", e);
                }
            }
        }

        String ref = rInject.redissonBeanRef();
        if ("".equals(ref)) {
            return getDefaultRedissonOrException();
        } else {
            return evaluateSpELForRedisson(ref);
        }
    }

    private String tryEvaluateProperty(String expr) {
        if (!isPropertyExpression(expr)) {
            return expr;
        }

        if (environment == null) {
            throw new IllegalStateException(
                    "Unable to resolve properties \"" + expr
                            + "\" because " + ConfigurableEnvironment.class.getName()
                            + " is not found in the Spring Context.");
        }

        return environment.resolvePlaceholders(expr);
    }

    private Object tryEvaluateSpEL(String expr, Class<?> expected) {
        if (!isSpELExpression(expr)) {
            return null;
        }
        Object value = evaluateSpEL(expr);
        if (value == null) {//valid SpEL expression but results in null value returned. i.e. nothing found
            throw new IllegalArgumentException(
                    "No result found for expression [" + expr + "] for type " + expected.getName());
        }
        Class valueClass = value.getClass();
        if (expected.isAssignableFrom(valueClass)) {
            return value;
        }

        if (!(value instanceof String) || "".equals(value)) {
            throw new NoSuchBeanDefinitionException(
                    "Expression [" + expr + "] expected for type " + expected.getName()
                            + " but found " + valueClass.getName() + " instead.");
        }
        return value;
    }

    private RedissonClient getDefaultRedissonOrException() {
        if (defaultRedisson == null) {
            throw new IllegalStateException(
                    "No default RedissonClient bean discovered, please specify the correct bean in RInject.name() or RInject.value().");
        }
        return defaultRedisson;
    }

    private Object evaluateSpEL(String expr) {
        return parseExpression(expr).getValue(springContext);
    }

    private RedissonClient evaluateSpELForRedisson(String expr) {
        return parseExpression(expr).getValue(springContext, RedissonClient.class);
    }

    protected Expression parseExpression(String expr) {
        if (!cache.containsKey(expr.hashCode())) {
            cache.putIfAbsent(expr.hashCode(),
                    isSpELExpression(expr)
                            ? parser.parseExpression(expr, SPEL_TEMP_CONTEXT)
                            : parser.parseExpression(expr));
        }
        return cache.get(expr.hashCode());
    }

    protected boolean isSpELExpression(String expr) {
        return spelExpressionPattern.matcher(expr).matches();
    }

    protected boolean isPropertyExpression(String expr) {
        return propertyExpressionPattern.matcher(expr).matches();
    }

    private Matcher getRedissonExpressionMatcher(String expr) {
        return redissonExpressionPattern.matcher(expr);
    }
}
