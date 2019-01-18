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
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.Assert;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public abstract class AbstractRedissonNamespaceDefinitionParser
        extends AbstractSingleBeanDefinitionParser {
    
    protected final RedissonNamespaceParserSupport helper;
    private final RedissonNamespaceDecorator decorator;
    private final String parentRefAttribute;

    protected AbstractRedissonNamespaceDefinitionParser(RedissonNamespaceParserSupport helper, String parentRefAttribute) {
        this.helper = helper;
        this.parentRefAttribute = parentRefAttribute;
        this.decorator = new RedissonNamespaceDefaultDecorator();
    }

    public AbstractRedissonNamespaceDefinitionParser(RedissonNamespaceParserSupport helper, String parentRefAttribute, RedissonNamespaceDecorator decorator) {
        this.helper = helper;
        this.parentRefAttribute = parentRefAttribute;
        this.decorator = decorator;
    }

    @Override
    protected final void doParse(Element element, BeanDefinitionBuilder builder) {
    }
    
    @Override
    protected final void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        Assert.state(helper.isRedissonNS(element),
                "Illegal state. "
                        + this.getClass().getName()
                + " can only parse "
                        + RedissonNamespaceParserSupport.REDISSON_NAMESPACE
                + " namespace elements");
        Assert.state(element.hasAttribute(parentRefAttribute),
                "Illegal state. property \"" + parentRefAttribute 
                        + "\" is required in the \""
                + helper.getName(element)
                + "\" element.");
        
        helper.populateIdAttribute(element, builder, parserContext);
        AbstractBeanDefinition bd = builder.getRawBeanDefinition();
        parseNested(element, parserContext, builder, bd);
        decorator.decorate(element, parserContext, builder, helper);
        parserContext.getDelegate().parseQualifierElements(element, bd);
        if (parserContext.isNested()) {
            helper.registerBeanDefinition(builder, element, parserContext);
        }
    }
    
    protected abstract void parseNested(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, BeanDefinition bd);
    
    @Override
    protected final boolean shouldGenerateIdAsFallback() {
        return true;
    }

}
