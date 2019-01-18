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

import org.redisson.api.RDestroyable;
import org.redisson.client.codec.Codec;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.Conventions;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonGenericObjectDefinitionParser
        extends AbstractRedissonNamespaceDefinitionParser {
    
    private final static String KEY_ATTRIBUTE = "key";
    private final static String TOPIC_ATTRIBUTE = "topic";
    private final static String PATTERN_ATTRIBUTE = "pattern";
    private final static String SERVICE_ATTRIBUTE = "service";
    private final static String CODEC_REF_ATTRIBUTE = "codec-ref";
    private final static String FAIL_LOCK = "fairLock";

    RedissonGenericObjectDefinitionParser(RedissonNamespaceParserSupport helper) {
        super(helper, RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE);
    }
    
    RedissonGenericObjectDefinitionParser(RedissonNamespaceParserSupport helper, RedissonNamespaceDecorator decorator) {
        super(helper,
                RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE,
                decorator);
    }

    @Override
    protected void parseNested(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, BeanDefinition bd) {
        bd.setFactoryBeanName(element.getAttribute(
                RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE));
        String typeName
                = Conventions.attributeNameToPropertyName(element.getLocalName());
        bd.setFactoryMethodName("get" + StringUtils.capitalize(typeName));
        
        helper.addConstructorArgs(element, KEY_ATTRIBUTE,
                String.class, builder);
        helper.addConstructorArgs(element, TOPIC_ATTRIBUTE,
                String.class, builder);
        helper.addConstructorArgs(element, PATTERN_ATTRIBUTE,
                String.class, builder);
        helper.addConstructorArgs(element, SERVICE_ATTRIBUTE,
                String.class, builder);
        helper.addConstructorArgs(element, CODEC_REF_ATTRIBUTE,
                Codec.class, builder);
        if (RDestroyable.class.isAssignableFrom(getBeanClass(element))) {
            ((AbstractBeanDefinition) bd).setDestroyMethodName("destroy");
        }
    }
    
    @Override
    protected Class<?> getBeanClass(Element element) {
        String elementName
                = Conventions.attributeNameToPropertyName(
                        element.getLocalName());
        try {
            return Class.forName(RedissonNamespaceParserSupport.API_CLASS_PATH_PREFIX
                    + (StringUtils.capitalize(FAIL_LOCK.equals(elementName)
                            ? "lock"
                            : elementName)));
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
    
}
