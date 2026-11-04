/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.cache;

import io.netty.buffer.ByteBuf;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.junit.jupiter.api.Test;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.AddressResolverGroupFactory;
import org.redisson.connection.ServiceManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalCacheListenerTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testDisableKeysEvictsKeyFromCacheKeyMap() throws Exception {
        Config config = new Config();
        config.setEventLoopGroup(new DefaultEventLoopGroup());
        config.setAddressResolverGroupFactory((channelType, socketChannelType, nameServerProvider) ->
                (io.netty.resolver.AddressResolverGroup) DefaultAddressResolverGroup.INSTANCE);
        MasterSlaveServersConfig msConfig = new MasterSlaveServersConfig();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        ServiceManager sm = new ServiceManager(msConfig, config);

        try {
            CommandAsyncExecutor executor = (CommandAsyncExecutor) Proxy.newProxyInstance(
                    LocalCacheListenerTest.class.getClassLoader(),
                    new Class<?>[]{CommandAsyncExecutor.class},
                    (proxy, method, args) -> {
                        if ("getServiceManager".equals(method.getName())) {
                            return sm;
                        }
                        return null;
                    });

            LocalCachedMapOptions<?, ?> options = LocalCachedMapOptions.defaults().useObjectAsCacheKey(true);
            LocalCacheListener listener = new LocalCacheListener("testCache", executor, null, null, options, 60000L, false) {
                @Override
                protected CacheValue updateCache(ByteBuf keyBuf, ByteBuf valueBuf) throws IOException {
                    return null;
                }
            };

            Map<CacheKey, CacheValue> cache = new ConcurrentHashMap<>();
            Map<Object, CacheKey> cacheKeyMap = new ConcurrentHashMap<>();

            setSuperField(listener, "cache", cache);
            setSuperField(listener, "cacheKeyMap", cacheKeyMap);

            String userKey = "userKey123";
            String userVal = "userVal456";
            CacheKey cacheKey = new CacheKey(new byte[]{1, 2, 3, 4});
            CacheValue cacheValue = new CacheValue(userKey, userVal);

            cache.put(cacheKey, cacheValue);
            cacheKeyMap.put(userKey, cacheKey);

            assertThat(cacheKeyMap).containsKey(userKey);

            Method disableKeysMethod = LocalCacheListener.class.getDeclaredMethod("disableKeys", String.class, Set.class, long.class);
            disableKeysMethod.setAccessible(true);
            disableKeysMethod.invoke(listener, "req-1", Collections.singleton(cacheKey), 5000L);

            // Verifies that cacheKeyMap entry was removed by user key (userKey123), not user value (userVal456)
            assertThat(cacheKeyMap).doesNotContainKey(userKey);
            assertThat(cacheKeyMap).isEmpty();
        } finally {
            sm.close();
        }
    }

    private static void setSuperField(Object target, String fieldName, Object val) throws Exception {
        Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, val);
    }
}
