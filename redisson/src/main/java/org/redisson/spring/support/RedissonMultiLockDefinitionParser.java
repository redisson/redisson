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

import java.util.List;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.Conventions;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonMultiLockDefinitionParser
        extends AbstractRedissonNamespaceDefinitionParser {
    
    public RedissonMultiLockDefinitionParser(RedissonNamespaceParserSupport helper) {
        super(helper,
                RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE);
    }

    @Override
    protected void parseNested(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, BeanDefinition bd) {
        bd.setDependsOn(element.getAttribute(
                RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE));
        List<Element> childElements = DomUtils.getChildElements(element);
        for (Element elt : childElements) {
            String localName = elt.getLocalName();
            if (BeanDefinitionParserDelegate
                    .QUALIFIER_ELEMENT.equals(localName)) {
                continue;//parsed elsewhere
            }
            String id;
            if (BeanDefinitionParserDelegate.REF_ELEMENT.equals(localName)){
                id = elt.getAttribute(
                        BeanDefinitionParserDelegate.BEAN_REF_ATTRIBUTE);
            } else {
                if (!elt.hasAttribute(
                        RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE)) {
                    helper.setAttribute(elt,
                            RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE,
                            element.getAttribute(
                                    RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE));
                }
                helper.populateIdAttribute(elt, builder, parserContext);
                parserContext.getDelegate().parseCustomElement(elt, bd);
                id = elt.getAttribute(
                        RedissonNamespaceParserSupport.ID_ATTRIBUTE);
            }
            ConstructorArgumentValues args
                    = builder.getRawBeanDefinition()
                            .getConstructorArgumentValues();
            if (args.getArgumentCount() > 0) {
                ConstructorArgumentValues.ValueHolder value
                        = args.getIndexedArgumentValues().get(0);
                ManagedList list;
                if (value.getValue() instanceof ManagedList) {
                    list = (ManagedList) value.getValue();
                } else {
                    list = new ManagedList();
                    list.add(value.getValue());
                    value.setValue(list);
                    value.setType(ManagedList.class.getName());
                }
                list.add(new RuntimeBeanReference(id));
            } else {
                builder.addConstructorArgReference(id);
            }
        }
    }

    @Override
    protected String getBeanClassName(Element element) {
        String elementName
                = Conventions.attributeNameToPropertyName(
                        element.getLocalName());
        return RedissonNamespaceParserSupport.IMPL_CLASS_PATH_PREFIX
                + StringUtils.capitalize(elementName);
    }
    
}
