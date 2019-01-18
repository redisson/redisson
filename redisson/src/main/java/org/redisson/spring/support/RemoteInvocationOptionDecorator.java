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

import java.util.ArrayList;
import java.util.Arrays;
import org.redisson.api.RemoteInvocationOptions;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.Assert;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RemoteInvocationOptionDecorator implements RedissonNamespaceDecorator {
    
    @Override
    public void decorate(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, RedissonNamespaceParserSupport helper) {
        if (helper.hasElement(element,
                            RedissonNamespaceParserSupport.REMOTE_INVOCATION_OPTIONS_ELEMENT)) {
            Element options = helper.getSingleElement(element,
                        RedissonNamespaceParserSupport.REMOTE_INVOCATION_OPTIONS_ELEMENT);
            String optionBeanId = invokeOptions(options, parserContext, helper);
            if (helper.hasElement(element,
                            RedissonNamespaceParserSupport.REMOTE_NO_ACK_ELEMENT)) {
                helper.invoker(optionBeanId, "noAck", null, parserContext);
            }
            if (helper.hasElement(element,
                            RedissonNamespaceParserSupport.REMOTE_ACK_ELEMENT)) {
                Element remoteAck = helper.getSingleElement(element,
                        RedissonNamespaceParserSupport.REMOTE_ACK_ELEMENT);
                Assert.state(helper.hasAttribute(remoteAck,
                        RedissonNamespaceParserSupport.WITHIN_ATTRIBUTE),
                        "Missing \""
                        + RedissonNamespaceParserSupport.WITHIN_ATTRIBUTE
                        + "\" attribute in \""
                        + RedissonNamespaceParserSupport.REMOTE_ACK_ELEMENT
                        + "\" element.");
                ArrayList args = new ArrayList(2);
                args.add(helper.getAttribute(remoteAck,
                        RedissonNamespaceParserSupport.WITHIN_ATTRIBUTE));
                if (helper.hasAttribute(remoteAck,
                        RedissonNamespaceParserSupport.TIME_UNIT_ATTRIBUTE)) {
                    args.add(helper.getAttribute(remoteAck,
                            RedissonNamespaceParserSupport.TIME_UNIT_ATTRIBUTE));
                }
                helper.invoker(optionBeanId, "expectAckWithin", args.toArray(),
                        parserContext);
            }
            if (helper.hasElement(element,
                            RedissonNamespaceParserSupport.REMOTE_NO_RESULT_ELEMENT)) {
                helper.invoker(optionBeanId, "noResult", null, parserContext);
            }
            if (helper.hasElement(element,
                            RedissonNamespaceParserSupport.REMOTE_RESULT_ELEMENT)) {
                Element remoteResult = helper.getSingleElement(element,
                        RedissonNamespaceParserSupport.REMOTE_RESULT_ELEMENT);
                Assert.state(helper.hasAttribute(remoteResult,
                        RedissonNamespaceParserSupport.WITHIN_ATTRIBUTE),
                        "Missing \""
                        + RedissonNamespaceParserSupport.WITHIN_ATTRIBUTE
                        + "\" attribute in \""
                        + RedissonNamespaceParserSupport.REMOTE_RESULT_ELEMENT
                        + "\" element.");
                ArrayList args = new ArrayList(2);
                args.add(helper.getAttribute(remoteResult,
                        RedissonNamespaceParserSupport.WITHIN_ATTRIBUTE));
                if (helper.hasAttribute(remoteResult,
                        RedissonNamespaceParserSupport.TIME_UNIT_ATTRIBUTE)) {
                    args.add(helper.getAttribute(remoteResult,
                            RedissonNamespaceParserSupport.TIME_UNIT_ATTRIBUTE));
                }
                helper.invoker(optionBeanId, "expectResultWithin", args.toArray(),
                        parserContext);
            }
            MutablePropertyValues properties = builder.getRawBeanDefinition()
                    .getPropertyValues();
            PropertyValue propertyValue
                = properties.getPropertyValue("arguments");
            ManagedList<Object> args = new ManagedList();
            args.addAll(Arrays.asList(
                    (Object[]) propertyValue.getValue()));
            args.add(new RuntimeBeanReference(optionBeanId));
            properties.removePropertyValue("arguments");
            properties.addPropertyValue("arguments", args);
        }
    }
    
    private String invokeOptions(Element element, ParserContext parserContext, RedissonNamespaceParserSupport helper) {
        BeanComponentDefinition defaultOption
                = helper.factoryInvoker(element, RemoteInvocationOptions.class,
                        "defaults", null, parserContext);
        return defaultOption.getName();
    }

}
