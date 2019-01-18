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

import org.redisson.api.LocalCachedMapOptions;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.Conventions;
import org.springframework.util.Assert;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class LocalCachedMapOptionsDecorator implements RedissonNamespaceDecorator {
    
    @Override
    public void decorate(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, RedissonNamespaceParserSupport helper) {
        NodeList list = element.getElementsByTagNameNS(
                RedissonNamespaceParserSupport.REDISSON_NAMESPACE,
                RedissonNamespaceParserSupport.LOCAL_CACHED_MAP_OPTIONS_ELEMENT);
        Element options = null;
        String id;
        if (list.getLength() == 1) {
            options = (Element) list.item(0);
            id = invokeOptions(options, parserContext, helper);
            for (int i = 0; i < options.getAttributes().getLength(); i++) {
                Attr item = (Attr) options.getAttributes().item(i);
                if (helper.isEligibleAttribute(item)
                        && !RedissonNamespaceParserSupport.TIME_TO_LIVE_UNIT_ATTRIBUTE
                        .equals(item.getLocalName())
                        && !RedissonNamespaceParserSupport.MAX_IDLE_UNIT_ATTRIBUTE
                        .equals(item.getLocalName())) {
                    helper.invoker(id,
                            helper.getName(item),
                            new Object[]{item.getValue()},
                            parserContext);
                }
            }
            invokeTimeUnitOptions(options, id, parserContext, helper,
                    RedissonNamespaceParserSupport.TIME_TO_LIVE_ATTRIBUTE,
                    RedissonNamespaceParserSupport.TIME_TO_LIVE_UNIT_ATTRIBUTE);
            
            invokeTimeUnitOptions(options, id, parserContext, helper,
                    RedissonNamespaceParserSupport.MAX_IDLE_ATTRIBUTE,
                    RedissonNamespaceParserSupport.MAX_IDLE_UNIT_ATTRIBUTE);
        } else {
            id = invokeOptions(options, parserContext, helper);
        }
        helper.addConstructorArgs(new RuntimeBeanReference(id),
                LocalCachedMapOptions.class, builder);
    }
    
    private String invokeOptions(Element element, ParserContext parserContext, RedissonNamespaceParserSupport helper) {
        BeanComponentDefinition defaultOption
                = helper.factoryInvoker(element, LocalCachedMapOptions.class,
                        "defaults", null, parserContext);
        return defaultOption.getName();
    }
    
    private void invokeTimeUnitOptions(Element element, String id, ParserContext parserContext, RedissonNamespaceParserSupport helper, String timeAttrubute, String timeUnitAttribute) {
        if (helper.hasAttribute(element, timeUnitAttribute)) {
            Assert.state(
                    helper.hasAttribute(element, timeAttrubute),
                    "Missing \"" + timeAttrubute + "\" attribute in \""
                    + RedissonNamespaceParserSupport.LOCAL_CACHED_MAP_OPTIONS_ELEMENT
                    + "\" element.");
            helper.invoker(id,
                    Conventions.attributeNameToPropertyName(timeAttrubute),
                    new Object[]{
                        Integer.parseInt(
                                helper.getAttribute(element, timeAttrubute)),
                        helper.getAttribute(element, timeUnitAttribute)},
                    parserContext);
        }
    }
}
