/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonRPCClientDefinitionParser
    extends AbstractRedissonNamespaceDefinitionParser {

    public RedissonRPCClientDefinitionParser(RedissonNamespaceParserSupport helper, RedissonNamespaceDecorator decorator) {
        super(helper,
                RedissonNamespaceParserSupport.REMOTE_SERVICE_REF_ATTRIBUTE,
                decorator);
    }

    @Override
    protected void parseNested(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, BeanDefinition bd) {
        Class<?> apiClass;
        try {
            apiClass = Class.forName(helper.getAttribute(element,
                    RedissonNamespaceParserSupport.API_CLASS_ATTRIBUTE));
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException(
                    "The class [" + helper.getAttribute(element,
                            RedissonNamespaceParserSupport.API_CLASS_ATTRIBUTE)
                            + "] specified in \""
                            + RedissonNamespaceParserSupport.API_CLASS_ATTRIBUTE
                            + "\" attribute has not "
                            + "found. Please check the class path.", ex);
        }
        builder.addPropertyValue("targetObject", new RuntimeBeanReference(
                helper.getAttribute(element,
                        RedissonNamespaceParserSupport.REMOTE_SERVICE_REF_ATTRIBUTE)));
        builder.addPropertyValue("targetMethod", "get");
        builder.addPropertyValue("arguments", new Object[] {apiClass});
    }

    @Override
    protected Class<?> getBeanClass(Element element) {
        return MethodInvokingFactoryBean.class;
    }
    
}
