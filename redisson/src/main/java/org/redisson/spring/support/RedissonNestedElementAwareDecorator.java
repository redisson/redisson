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

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonNestedElementAwareDecorator implements RedissonNamespaceDecorator {

    private final String[] nestedElements;
    private final String referenceAttribute;

    public RedissonNestedElementAwareDecorator(String[] nestedElements, String referenceAttribute) {
        this.nestedElements = nestedElements;
        this.referenceAttribute = referenceAttribute;
    }
    
    @Override
    public void decorate(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, RedissonNamespaceParserSupport helper) {
        for (String nestedElement : nestedElements) {
            parseNested(element, nestedElement, parserContext, builder, helper);
        }
    }

    private void parseNested(Element element, String eltType, ParserContext parserContext, BeanDefinitionBuilder builder, RedissonNamespaceParserSupport helper) {
        NodeList list = element.getElementsByTagNameNS(
                RedissonNamespaceParserSupport.REDISSON_NAMESPACE, eltType);
        if (list.getLength() == 1) {
            Element elt = (Element) list.item(0);
            if (StringUtils.hasText(referenceAttribute)) {
                helper.setAttribute(elt, referenceAttribute,
                        helper.getAttribute(element,
                                RedissonNamespaceParserSupport.ID_ATTRIBUTE));
                helper.setAttribute(elt, RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE,
                        helper.getAttribute(element,
                                RedissonNamespaceParserSupport.REDISSON_REF_ATTRIBUTE));
            }
            parserContext.getDelegate()
                    .parseCustomElement(elt, builder.getRawBeanDefinition());
        }
    }
}
