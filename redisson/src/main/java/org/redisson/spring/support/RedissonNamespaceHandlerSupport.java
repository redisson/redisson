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

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonNamespaceHandlerSupport extends NamespaceHandlerSupport {
    
    @Override
    public void init() {
        RedissonNamespaceParserSupport helper
                = new RedissonNamespaceParserSupport();
        
        RedissonGenericObjectDefinitionParser defaultParser
                = new RedissonGenericObjectDefinitionParser(helper);
        
        RedissonReadAndWriteLockDefinitionParser readAndWriteLockParser
                = new RedissonReadAndWriteLockDefinitionParser(helper);
        
        RedissonMultiLockDefinitionParser nestedParser
                = new RedissonMultiLockDefinitionParser(helper);
        
        RedissonNestedElementAwareDecorator readWriteLockDecorator
                = new RedissonNestedElementAwareDecorator(
                        new String[]{
                            RedissonNamespaceParserSupport.READ_LOCK_ELEMENT,
                            RedissonNamespaceParserSupport.WRITE_LOCK_ELEMENT
                        },
                        RedissonNamespaceParserSupport.READ_WRITE_LOCK_REF_ATTRIBUTE);
        
        RedissonGenericObjectDefinitionParser readWriteLockParser
                = new RedissonGenericObjectDefinitionParser(helper,
                        readWriteLockDecorator);
        
        RedissonNestedElementAwareDecorator remoteServiceDecorator
                = new RedissonNestedElementAwareDecorator(
                        new String[]{
                            RedissonNamespaceParserSupport.RPC_SERVER_ELEMENT,
                            RedissonNamespaceParserSupport.RPC_CLIENT_ELEMENT
                        },
                        RedissonNamespaceParserSupport.REMOTE_SERVICE_REF_ATTRIBUTE);
        
        RedissonGenericObjectDefinitionParser remoteServiceParser
                = new RedissonGenericObjectDefinitionParser(helper,
                        remoteServiceDecorator);
        
        RedissonNestedElementAwareDecorator liveObjectServiceDecorator
                = new RedissonNestedElementAwareDecorator(
                        new String[]{
                            RedissonNamespaceParserSupport.LIVE_OBJECT_ELEMENT,
                            RedissonNamespaceParserSupport.LIVE_OBJECT_REGISTRATION_ELEMENT
                        },
                        RedissonNamespaceParserSupport.LIVE_OBJECT_SERVICE_REF_ATTRIBUTE);
        
        RedissonGenericObjectDefinitionParser liveObjectServiceParser
                = new RedissonGenericObjectDefinitionParser(helper,
                        liveObjectServiceDecorator);
        
        //root beans
        registerBeanDefinitionParser("client",
                new RedissonDefinitionParser(helper));
        registerBeanDefinitionParser("redis", new RedisDefinitionParser(helper));
        //object parsers
        registerBeanDefinitionParser("binary-stream", defaultParser);
        registerBeanDefinitionParser("geo", defaultParser);
        registerBeanDefinitionParser("set-cache", defaultParser);
        registerBeanDefinitionParser("map-cache", defaultParser);
        registerBeanDefinitionParser("bucket", defaultParser);
        registerBeanDefinitionParser("buckets", defaultParser);
        registerBeanDefinitionParser("hyper-log-log", defaultParser);
        registerBeanDefinitionParser("list", defaultParser);
        registerBeanDefinitionParser("list-multimap", defaultParser);
        registerBeanDefinitionParser("list-multimap-cache", defaultParser);
        registerBeanDefinitionParser("local-cached-map",
                new RedissonGenericObjectDefinitionParser(helper,
                        new LocalCachedMapOptionsDecorator()));
        registerBeanDefinitionParser("map", defaultParser);
        registerBeanDefinitionParser("set-multimap", defaultParser);
        registerBeanDefinitionParser("set-multimap-cache", defaultParser);
        registerBeanDefinitionParser("semaphore", defaultParser);
        registerBeanDefinitionParser("permit-expirable-semaphore", defaultParser);
        registerBeanDefinitionParser("lock", defaultParser);
        registerBeanDefinitionParser("fair-lock", defaultParser);
        registerBeanDefinitionParser("read-write-lock",readWriteLockParser);
        registerBeanDefinitionParser("read-lock", readAndWriteLockParser);
        registerBeanDefinitionParser("write-lock", readAndWriteLockParser);
        registerBeanDefinitionParser("multi-lock", nestedParser);
        registerBeanDefinitionParser("red-lock", nestedParser);
        registerBeanDefinitionParser("set", defaultParser);
        registerBeanDefinitionParser("sorted-set", defaultParser);
        registerBeanDefinitionParser("scored-sorted-set", defaultParser);
        registerBeanDefinitionParser("lex-sorted-set", defaultParser);
        registerBeanDefinitionParser("topic", defaultParser);
        registerBeanDefinitionParser("pattern-topic", defaultParser);
        registerBeanDefinitionParser("blocking-fair-queue", defaultParser);
        registerBeanDefinitionParser("queue", defaultParser);
        registerBeanDefinitionParser("delayed-queue",
                new RedissonGenericObjectDefinitionParser(helper,
                        new DelayedQueueDecorator()));
        registerBeanDefinitionParser("priority-queue", defaultParser);
        registerBeanDefinitionParser("priority-deque", defaultParser);
        registerBeanDefinitionParser("blocking-queue", defaultParser);
        registerBeanDefinitionParser("bounded-blocking-queue", defaultParser);
        registerBeanDefinitionParser("deque", defaultParser);
        registerBeanDefinitionParser("blocking-deque", defaultParser);
        registerBeanDefinitionParser("atomic-long", defaultParser);
        registerBeanDefinitionParser("atomic-double", defaultParser);
        registerBeanDefinitionParser("count-down-latch", defaultParser);
        registerBeanDefinitionParser("bit-set", defaultParser);
        registerBeanDefinitionParser("bloom-filter", defaultParser);
        registerBeanDefinitionParser("script", defaultParser);
        registerBeanDefinitionParser("executor-service", defaultParser);//nested unfinished
        registerBeanDefinitionParser("remote-service", remoteServiceParser);
        registerBeanDefinitionParser("rpc-server",
                new RedissonRPCServerDefinitionParser(helper));
        registerBeanDefinitionParser("rpc-client",
                new RedissonRPCClientDefinitionParser(helper,
                        new RemoteInvocationOptionDecorator()));
        registerBeanDefinitionParser("keys", defaultParser);
        registerBeanDefinitionParser("live-object-service", liveObjectServiceParser);
        registerBeanDefinitionParser("live-object",
                new RedissonLiveObjectDefinitionParser(helper));
        registerBeanDefinitionParser("live-object-registration",
                new RedissonLiveObjectRegistrationDefinitionParser(helper));
    }
    
}
