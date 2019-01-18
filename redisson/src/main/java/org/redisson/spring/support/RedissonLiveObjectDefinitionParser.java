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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.Assert;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonLiveObjectDefinitionParser
    extends AbstractRedissonNamespaceDefinitionParser {

    public RedissonLiveObjectDefinitionParser(RedissonNamespaceParserSupport helper) {
        super(helper, RedissonNamespaceParserSupport.LIVE_OBJECT_SERVICE_REF_ATTRIBUTE);
    }

    @Override
    protected void parseNested(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, BeanDefinition bd) {
        Class<?> apiClass;
        try {
            apiClass = Class.forName(helper.getAttribute(element,
                    RedissonNamespaceParserSupport.CLASS_ATTRIBUTE));
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException(
                    "The class [" + helper.getAttribute(element,
                            RedissonNamespaceParserSupport.CLASS_ATTRIBUTE)
                    + "] specified in \"api-class\" attribute has not "
                    + "found. Please check the class path.", ex);
        }
        Assert.state(helper.hasAttribute(element,
                RedissonNamespaceParserSupport.OBJECT_ID_ATTRIBUTE)
                || helper.hasAttribute(element,
                        RedissonNamespaceParserSupport.OBJECT_ID_REF_ATTRIBUTE),
                "One of \""
                + RedissonNamespaceParserSupport.OBJECT_ID_ATTRIBUTE
                + "\" or \""
                + RedissonNamespaceParserSupport.OBJECT_ID_REF_ATTRIBUTE
                + "\" attribute is required in the \""
                + RedissonNamespaceParserSupport.LIVE_OBJECT_ELEMENT
                + "\" element.");
        builder.addPropertyValue("targetObject", new RuntimeBeanReference(
                helper.getAttribute(element,
                        RedissonNamespaceParserSupport.LIVE_OBJECT_SERVICE_REF_ATTRIBUTE)));
        builder.addPropertyValue("targetMethod", "get");
        ManagedList args = new ManagedList(); 
        args.add(apiClass);
        if (helper.hasAttribute(element,
                RedissonNamespaceParserSupport.OBJECT_ID_ATTRIBUTE)) {
            args.add(helper.getAttribute(element,
                    RedissonNamespaceParserSupport.OBJECT_ID_ATTRIBUTE));
        }
        if (helper.hasAttribute(element,
                RedissonNamespaceParserSupport.OBJECT_ID_REF_ATTRIBUTE)) {
            args.add(new RuntimeBeanReference(
                    helper.getAttribute(element,
                            RedissonNamespaceParserSupport.OBJECT_ID_REF_ATTRIBUTE)));
        }
        builder.addPropertyValue("arguments", args);
    }

    @Override
    protected Class<?> getBeanClass(Element element) {
        return MethodInvokingFactoryBean.class;
    }
    
}
