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
package org.redisson.spring.cache;

import org.redisson.MapCacheNativeWrapper;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RMapCacheNative;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.ResourceLoaderAware;

import java.util.Map;

/**
 * A {@link CacheManager} implementation
 * backed by Redisson instance.
 *
 * @author Nikita Koksharov
 *
 */
@SuppressWarnings("unchecked")
public class RedissonSpringCacheNativeManager extends RedissonSpringCacheManager implements CacheManager, ResourceLoaderAware, InitializingBean {

    /**
     * Creates CacheManager supplied by Redisson instance
     *
     * @param redisson object
     */
    public RedissonSpringCacheNativeManager(RedissonClient redisson) {
        this(redisson, (String) null, null);
    }

    /**
     * Creates CacheManager supplied by Redisson instance and
     * Cache config mapped by Cache name
     *
     * @param redisson object
     * @param config object
     */
    public RedissonSpringCacheNativeManager(RedissonClient redisson, Map<String, ? extends CacheConfig> config) {
        this(redisson, config, null);
        validateProps();
    }

    /**
     * Creates CacheManager supplied by Redisson instance, Codec instance
     * and Cache config mapped by Cache name.
     * <p>
     * Each Cache instance share one Codec instance.
     *
     * @param redisson object
     * @param config object
     * @param codec object
     */
    public RedissonSpringCacheNativeManager(RedissonClient redisson, Map<String, ? extends CacheConfig> config, Codec codec) {
        super(redisson, config, codec);
        validateProps();
    }

    /**
     * Creates CacheManager supplied by Redisson instance
     * and Cache config mapped by Cache name.
     * <p>
     * Loads the config file from the class path, interpreting plain paths as class path resource names
     * that include the package path (e.g. "mypackage/myresource.txt").
     *
     * @param redisson object
     * @param configLocation path
     */
    public RedissonSpringCacheNativeManager(RedissonClient redisson, String configLocation) {
        this(redisson, configLocation, null);
    }

    /**
     * Creates CacheManager supplied by Redisson instance, Codec instance
     * and Config location path.
     * <p>
     * Each Cache instance share one Codec instance.
     * <p>
     * Loads the config file from the class path, interpreting plain paths as class path resource names
     * that include the package path (e.g. "mypackage/myresource.txt").
     *
     * @param redisson object
     * @param configLocation path
     * @param codec object
     */
    public RedissonSpringCacheNativeManager(RedissonClient redisson, String configLocation, Codec codec) {
        super(redisson, configLocation, codec);
    }

    private void validateProps() {
        for (CacheConfig value : configMap.values()) {
            if (value.getMaxIdleTime() > 0) {
                throw new UnsupportedOperationException("maxIdleTime isn't supported");
            }
            if (value.getMaxSize() > 0) {
                throw new UnsupportedOperationException("maxSize isn't supported");
            }
        }
    }

    @Override
    public void setConfig(Map<String, ? extends CacheConfig> config) {
        super.setConfig(config);
        validateProps();
    }

    @Override
    protected RMap<Object, Object> getMap(String name, CacheConfig config) {
        if (codec != null) {
            return redisson.getMapCacheNative(name, codec);
        }
        return redisson.getMapCacheNative(name);
    }

    @Override
    protected RMapCache<Object, Object> getMapCache(String name, CacheConfig config) {
        RMapCacheNative<Object, Object> map = (RMapCacheNative<Object, Object>) getMap(name, config);
        return new MapCacheNativeWrapper<>(map);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        if (configLocation != null) {
            validateProps();
        }
    }
}
