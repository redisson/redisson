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
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.Conventions;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonNamespaceParserSupport {
    
    public static final String REDISSON_NAMESPACE
            = "http://redisson.org/schema/redisson";
    
    static final String REF_SUFFIX = "-ref";
    static final String API_CLASS_PATH_PREFIX = "org.redisson.api.R";
    static final String IMPL_CLASS_PATH_PREFIX = "org.redisson.Redisson";
    
    static final String ID_ATTRIBUTE = "id";
    static final String NAME_ATTRIBUTE = "name";
    static final String REDISSON_REF_ATTRIBUTE = "redisson-ref";
    static final String READ_WRITE_LOCK_REF_ATTRIBUTE = "read-write-lock-ref";
    static final String EXECUTOR_REF_ATTRIBUTE = "executor-ref";
    static final String REMOTE_SERVICE_REF_ATTRIBUTE = "remote-service-ref";
    static final String LIVE_OBJECT_SERVICE_REF_ATTRIBUTE
            = "live-object-service-ref";
    static final String OBJECT_ID_REF_ATTRIBUTE = "object-id-ref";
    
    static final String MAX_IDLE_ATTRIBUTE = "max-idle";
    static final String TIME_TO_LIVE_ATTRIBUTE = "time-to-live";
    static final String MAX_IDLE_UNIT_ATTRIBUTE = "max-idle-unit";
    static final String TIME_TO_LIVE_UNIT_ATTRIBUTE = "time-to-live-unit";
    static final String CONCURRENT_WORKERS_ATTRIBUTE = "concurrent-workers";
    static final String WITHIN_ATTRIBUTE = "within";
    static final String TIME_UNIT_ATTRIBUTE = "time-unit";
    static final String API_CLASS_ATTRIBUTE = "api-class";
    static final String CLASS_ATTRIBUTE = "class";
    static final String OBJECT_ID_ATTRIBUTE = "object-id";
    
    static final String READ_LOCK_ELEMENT = "read-lock";
    static final String WRITE_LOCK_ELEMENT = "write-lock";
    static final String RPC_SERVER_ELEMENT = "rpc-server";
    static final String RPC_CLIENT_ELEMENT = "rpc-client";
    static final String REMOTE_INVOCATION_OPTIONS_ELEMENT
            = "remote-invocation-options";
    static final String REMOTE_NO_ACK_ELEMENT = "remote-no-ack";
    static final String REMOTE_ACK_ELEMENT = "remote-ack";
    static final String REMOTE_NO_RESULT_ELEMENT = "remote-no-result";
    static final String REMOTE_RESULT_ELEMENT = "remote-result";
    static final String LOCAL_CACHED_MAP_OPTIONS_ELEMENT
            = "local-cached-map-options";
    static final String LIVE_OBJECT_ELEMENT
            = "live-object";
    static final String LIVE_OBJECT_REGISTRATION_ELEMENT
            = "live-object-registration";
    
    public String[] parseAliase(Element element) {
        if (element == null) {
            return null;
        }
        String[] aliases = null;
        String name = element.getAttribute(NAME_ATTRIBUTE);
        if (StringUtils.hasLength(name)) {
            aliases = StringUtils.trimArrayElements(
                    StringUtils.commaDelimitedListToStringArray(name));
        }
        return aliases;
    }

    public void parseAttributes(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        NamedNodeMap attributes = element.getAttributes();
        for (int x = 0; x < attributes.getLength(); x++) {
            Attr attribute = (Attr) attributes.item(x);
            if (isEligibleAttribute(attribute)) {
                String propertyName = attribute.getLocalName();
                if (propertyName.endsWith(REF_SUFFIX)) {
                    propertyName = propertyName.substring(0, attribute.getLocalName().length() - REF_SUFFIX.length());
                }
                propertyName = Conventions
                        .attributeNameToPropertyName(propertyName);
                Assert.state(StringUtils.hasText(propertyName),
                        "Illegal property name returned from"
                                + " 'extractPropertyName(String)': cannot be"
                                + " null or empty.");
                if (attribute.getLocalName().endsWith(REF_SUFFIX)) {
                    builder.addPropertyReference(propertyName,
                            attribute.getValue());
                } else {
                    builder.addPropertyValue(propertyName, attribute.getValue());
                }
            }
        }
    }
    
    public BeanDefinitionBuilder createBeanDefinitionBuilder(Element element, ParserContext parserContext, Class<?> cls) {
        BeanDefinitionBuilder builder
                = BeanDefinitionBuilder.genericBeanDefinition();
        builder.getRawBeanDefinition().setBeanClass(cls);
        builder.getRawBeanDefinition()
                .setSource(parserContext.extractSource(element));
        if (parserContext.isNested()) {
            builder.setScope(parserContext.getContainingBeanDefinition()
                    .getScope());
        }
        if (parserContext.isDefaultLazyInit()) {
            builder.setLazyInit(true);
        }
        return builder;
    }

    public BeanComponentDefinition registerBeanDefinition(BeanDefinitionBuilder builder, String id, String[] aliases, ParserContext parserContext) {
        BeanDefinitionHolder holder
                = new BeanDefinitionHolder(builder.getBeanDefinition(), id,
                        aliases);
        BeanDefinitionReaderUtils
                .registerBeanDefinition(holder, parserContext.getRegistry());
        BeanComponentDefinition componentDefinition
                = new BeanComponentDefinition(holder);
        parserContext.registerComponent(componentDefinition);
        return componentDefinition;
    }
    
    public BeanComponentDefinition registerBeanDefinition(BeanDefinitionBuilder builder, Element element, ParserContext parserContext) {
        BeanDefinitionHolder holder
                = new BeanDefinitionHolder(builder.getBeanDefinition(),
                        getId(element, builder, parserContext),
                        parseAliase(element));
        BeanDefinitionReaderUtils
                .registerBeanDefinition(holder, parserContext.getRegistry());
        BeanComponentDefinition componentDefinition
                = new BeanComponentDefinition(holder);
        parserContext.registerComponent(componentDefinition);
        return componentDefinition;
    }
    
    public void addConstructorArgs(Element element, String attribute, Class<?> type, BeanDefinition bd) {
        if (element.hasAttribute(attribute)) {
            addConstructorArgs(element.getAttribute(attribute), type, bd);
        }
    }
    
    public void addConstructorArgs(Object value, Class<?> type, BeanDefinition bd) {
        ConstructorArgumentValues.ValueHolder vHolder
                = new ConstructorArgumentValues.ValueHolder(value, type.getName());
        ConstructorArgumentValues args
                = bd.getConstructorArgumentValues();
        args.addIndexedArgumentValue(args.getArgumentCount(), vHolder);
    }
    
    public void addConstructorArgs(Element element, String attribute, Class<?> type, BeanDefinitionBuilder builder) {
        addConstructorArgs(element, attribute, type, builder.getRawBeanDefinition());
    }
    
    public void addConstructorArgs(Object value, Class<?> type, BeanDefinitionBuilder builder) {
        addConstructorArgs(value, type, builder.getRawBeanDefinition());
    }
    
    public String getName(Node node) {
        return Conventions.attributeNameToPropertyName(node.getLocalName());
    }
    
    public String getId(Element element, BeanDefinitionBuilder builder, ParserContext parserContext) {
        String id = null;
        if (element != null) {
            id = element.getAttribute(ID_ATTRIBUTE);
        }
        if (!StringUtils.hasText(id)) {
            id = generateId(builder, parserContext);
        }
        return id;
    }
    
    public String generateId(BeanDefinitionBuilder builder, ParserContext parserContext) {
        return parserContext.getReaderContext()
                .generateBeanName(builder.getRawBeanDefinition());
    }
    
    public void populateIdAttribute(Element element, BeanDefinitionBuilder builder, ParserContext parserContext) {
        if (element == null) {
            return;
        }
        if (!StringUtils.hasText(element.getAttribute(ID_ATTRIBUTE))) {
            element.setAttribute(ID_ATTRIBUTE,
                    generateId(builder, parserContext));
        }
    }
    
    public BeanComponentDefinition factoryInvoker(Element element, String bean, String method, Object[] args, ParserContext parserContext) {
        BeanDefinitionBuilder builder
                = preInvoke(element, bean, method, args, parserContext, true);
        builder.addPropertyReference("targetObject", bean);
        return doInvoke(element, builder, parserContext);
    }
    
    public BeanComponentDefinition factoryInvoker(Element element, Object obj, String method, Object[] args, ParserContext parserContext) {
        BeanDefinitionBuilder builder
                = preInvoke(element, obj, method, args, parserContext, true);
        builder.addPropertyValue("targetObject", obj);
        return doInvoke(element, builder, parserContext);
    }
    
    
    public BeanComponentDefinition factoryInvoker(String bean, String method, Object[] args, ParserContext parserContext) {
        return factoryInvoker(null, bean, method, args, parserContext);
    }
    
    public BeanComponentDefinition factoryInvoker(Object obj, String method, Object[] args, ParserContext parserContext) {
        return factoryInvoker(null, obj, method, args, parserContext);
    }
    
    
    public BeanComponentDefinition invoker(Element element, String bean, String method, Object[] args, ParserContext parserContext) {
        BeanDefinitionBuilder builder
                = preInvoke(element, bean, method, args, parserContext, false);
        builder.addPropertyReference("targetObject", bean);
        return doInvoke(element, builder, parserContext);
    }
    
    public BeanComponentDefinition invoker(Element element, Object obj, String method, Object[] args, ParserContext parserContext) {
        BeanDefinitionBuilder builder
                = preInvoke(element, obj, method, args, parserContext, false);
        builder.addPropertyValue("targetObject", obj);
        return doInvoke(element, builder, parserContext);
    }
    
    
    public BeanComponentDefinition invoker(String bean, String method, Object[] args, ParserContext parserContext) {
        return invoker(null, bean, method, args, parserContext);
    }
    
    public BeanComponentDefinition invoker(Object obj, String method, Object[] args, ParserContext parserContext) {
        return invoker(null, obj, method, args, parserContext);
    }
    
    private BeanDefinitionBuilder preInvoke(Element element, Object obj, String method, Object[] args, ParserContext parserContext, boolean factory) {
        Class<?> beanClass = BeanMethodInvoker.class;
        if (factory) {
            beanClass = MethodInvokingFactoryBean.class;
        }
        
        BeanDefinitionBuilder builder
                = createBeanDefinitionBuilder(element, parserContext, beanClass);
        if (obj instanceof Class) {
            builder.addPropertyValue("staticMethod",
                    ((Class<?>) obj).getName() + "." + method);
        } else {
            builder.addPropertyValue("targetMethod", method);
        }
        builder.addPropertyValue("arguments", args);
        if (element != null) {
            parserContext.getDelegate().parseQualifierElements(element,
                    builder.getRawBeanDefinition());
        }
        return builder;
    }
    
    private BeanComponentDefinition doInvoke(Element element, BeanDefinitionBuilder builder, ParserContext parserContext) {
        String id = getId(element, builder, parserContext);
        return registerBeanDefinition(builder, id,
                parseAliase(element), parserContext);
    }
    
    public boolean isEligibleAttribute(String attributeName) {
        return  !"xmlns".equals(attributeName)
                && !attributeName.startsWith("xmlns:")
                && !ID_ATTRIBUTE.equals(attributeName)
                && !NAME_ATTRIBUTE.equals(attributeName);
    }

    public boolean isEligibleAttribute(Attr attribute) {
        return  isEligibleAttribute(attribute.getName());
    }
    
    public boolean isRedissonNS(Node node) {
        return node != null
                && REDISSON_NAMESPACE.equals(node.getNamespaceURI());
    }
    
    public String getAttribute(Element element, String attribute) {
        return element.getAttribute(attribute);
    }
    
    public void setAttribute(Element element, String attribute, String value) {
        element.setAttribute(attribute, value);
    }
    
    public boolean hasAttribute(Element element, String attribute) {
        return element.hasAttribute(attribute);
    }
    
    public boolean hasElement(Element element, String tagName) {
        return element.getElementsByTagNameNS(
                    RedissonNamespaceParserSupport.REDISSON_NAMESPACE, tagName)
                .getLength() > 0;
    }
    
    public Element getSingleElement(Element element, String tagName) {
        return (Element) element.getElementsByTagNameNS(
                    RedissonNamespaceParserSupport.REDISSON_NAMESPACE, tagName)
                .item(0);
    }
}
