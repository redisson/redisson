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
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.Conventions;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReadAndWriteLockDefinitionParser
        extends AbstractRedissonNamespaceDefinitionParser {
    
    public RedissonReadAndWriteLockDefinitionParser(RedissonNamespaceParserSupport helper) {
        super(helper,
                RedissonNamespaceParserSupport.READ_WRITE_LOCK_REF_ATTRIBUTE);
    }

    @Override
    protected void parseNested(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, BeanDefinition bd) {
        bd.setFactoryBeanName(element.getAttribute(
                RedissonNamespaceParserSupport.READ_WRITE_LOCK_REF_ATTRIBUTE));
        String typeName
                = Conventions.attributeNameToPropertyName(element.getLocalName());
        bd.setFactoryMethodName(typeName);
    }
    
    @Override
    protected Class<?> getBeanClass(Element element) {
        try {
            return Class.forName(RedissonNamespaceParserSupport.API_CLASS_PATH_PREFIX
                    + "Lock");
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

}
