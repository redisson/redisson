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

import org.redisson.api.RQueue;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.Assert;
import org.w3c.dom.Element;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class DelayedQueueDecorator implements RedissonNamespaceDecorator {

    private static final String DESTINATION_QUEUE_REF = "destination-queue-ref";

    @Override
    public void decorate(Element element, ParserContext parserContext, BeanDefinitionBuilder builder, RedissonNamespaceParserSupport helper) {
        Assert.state(element.hasAttribute(DESTINATION_QUEUE_REF),
                "Illegal state. property \"" + DESTINATION_QUEUE_REF
                + "\" is required in the \""
                + helper.getName(element)
                + "\" element.");
        helper.addConstructorArgs(new RuntimeBeanReference(
                        helper.getAttribute(element, DESTINATION_QUEUE_REF)),
                RQueue.class, builder);
    }
}
