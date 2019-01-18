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

import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public final class RedisDefinitionParser
        extends AbstractSimpleBeanDefinitionParser {

    private static final String ADDRESS_ATTRIBUTE = "address";
    private static final String HOST_ATTRIBUTE = "host";
    private static final String PORT_ATTRIBUTE = "port";
    private static final String CONNECTION_TIMEOUT_ATTRIBUTE = "connectionTimeout";
    private static final String COMMAND_TIMEOUT_ATTRIBUTE = "commandTimeout";

    private final RedissonNamespaceParserSupport helper;

    public RedisDefinitionParser(RedissonNamespaceParserSupport helper) {
        this.helper = helper;
    }

    @Override
    protected Class<RedisClient> getBeanClass(Element element) {
        return RedisClient.class;
    }

    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        builder.getRawBeanDefinition().setBeanClass(RedisClient.class);
        if (helper.hasAttribute(element, HOST_ATTRIBUTE)) {
            helper.addConstructorArgs(element,
                    HOST_ATTRIBUTE, String.class, builder);
            helper.addConstructorArgs(element,
                    PORT_ATTRIBUTE, int.class, builder);
            helper.addConstructorArgs(element,
                    CONNECTION_TIMEOUT_ATTRIBUTE, int.class, builder);
            helper.addConstructorArgs(element,
                    COMMAND_TIMEOUT_ATTRIBUTE, int.class, builder);
        } else {
            BeanDefinitionBuilder b
                    = helper.createBeanDefinitionBuilder(element,
                            parserContext,
                            RedisClientConfig.class);
            String configId = helper.getId(null, b, parserContext);
            helper.parseAttributes(element, parserContext, b);
            BeanComponentDefinition def
                    = helper.registerBeanDefinition(b, configId,
                            null, parserContext);
            helper.addConstructorArgs(def, RedisClientConfig.class, builder);
        }
        builder.setDestroyMethodName("shutdown");
        parserContext.getDelegate().parseQualifierElements(element,
                builder.getRawBeanDefinition());
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }

    @Override
    protected boolean isEligibleAttribute(String attributeName) {
        return helper.isEligibleAttribute(attributeName);
    }

    @Override
    protected boolean isEligibleAttribute(Attr attribute, ParserContext parserContext) {
        return helper.isEligibleAttribute(attribute);
    }
}
