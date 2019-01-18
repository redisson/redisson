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

import org.redisson.spring.misc.BeanMethodInvoker;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonRPCServerDefinitionParser
    extends AbstractRedissonNamespaceDefinitionParser {

    public RedissonRPCServerDefinitionParser(RedissonNamespaceParserSupport helper) {
        super(helper, RedissonNamespaceParserSupport.REMOTE_SERVICE_REF_ATTRIBUTE);
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
        builder.addPropertyValue("targetMethod", "register");
        ManagedList args = new ManagedList(); 
        args.add(apiClass);
        args.add(new RuntimeBeanReference(
                        helper.getAttribute(element,
                                BeanDefinitionParserDelegate.BEAN_REF_ATTRIBUTE)));
        String workers = null;
        if (helper.hasAttribute(element,
                RedissonNamespaceParserSupport.CONCURRENT_WORKERS_ATTRIBUTE)) {
            workers = helper.getAttribute(element,
                    RedissonNamespaceParserSupport.CONCURRENT_WORKERS_ATTRIBUTE);
        }
        if (StringUtils.hasText(workers)) {
            args.add(Integer.parseInt(workers));
        }
        if (helper.hasAttribute(element,
                RedissonNamespaceParserSupport.EXECUTOR_REF_ATTRIBUTE)) {
            Assert.state(helper.hasAttribute(element,
                    RedissonNamespaceParserSupport.CONCURRENT_WORKERS_ATTRIBUTE),
                    "The \""
                    + RedissonNamespaceParserSupport.CONCURRENT_WORKERS_ATTRIBUTE
                    + "\" attribute in \""
                    + RedissonNamespaceParserSupport.RPC_SERVER_ELEMENT
                    + "\" element is required when \""
                    + RedissonNamespaceParserSupport.EXECUTOR_REF_ATTRIBUTE
                    + "\" attribute is specified.");
            args.add(new RuntimeBeanReference(
                    helper.getAttribute(element,
                            RedissonNamespaceParserSupport.EXECUTOR_REF_ATTRIBUTE)));
        }
        builder.addPropertyValue("arguments", args);
    }

    @Override
    protected Class<?> getBeanClass(Element element) {
        return BeanMethodInvoker.class;
    }
    
}
