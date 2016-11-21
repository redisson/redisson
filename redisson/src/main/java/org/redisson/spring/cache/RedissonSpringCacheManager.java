/**
 * Copyright 2016 Nikita Koksharov
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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * A {@link org.springframework.cache.CacheManager} implementation
 * backed by Redisson instance.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSpringCacheManager implements CacheManager, ResourceLoaderAware, InitializingBean {

    private ResourceLoader resourceLoader;

    private Codec codec;

    private RedissonClient redisson;

    private Map<String, CacheConfig> configMap = new LinkedHashMap<String, CacheConfig>();
    
    private Map<Pattern, CacheConfig> regExpMap = new LinkedHashMap<Pattern, CacheConfig>();

    private String configLocation;
    
    private String regExpConfigLocation;

    public RedissonSpringCacheManager() {
    }

    /**
     * Creates CacheManager supplied by Redisson instance and
     * Cache config mapped by Cache name
     *
     * @param redisson object
     * @param config object
     */
    public RedissonSpringCacheManager(RedissonClient redisson, Map<String, CacheConfig> config) {
        this(redisson, config, null, null);
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
    public RedissonSpringCacheManager(RedissonClient redisson, Map<String, CacheConfig> config, Codec codec) {
        this(redisson, config, null, codec);
    }

    /**
     * Creates CacheManager supplied by Redisson instance and
     * Cache config mapped by Cache name and RegExp config mapped by pattern.
     *
     * @param redisson object
     * @param config object
     * @param regExpConfig
     */
    public RedissonSpringCacheManager(RedissonClient redisson, Map<String, CacheConfig> config, Map<Pattern, CacheConfig> regExpConfig) {
        this(redisson, config, regExpConfig, null);
    }

    /**
     * Creates CacheManager supplied by Redisson instance, Codec instance,
     * Cache config mapped by Cache name and RegExp config mapped by pattern.
     * <p>
     * Each Cache instance share one Codec instance.
     *
     * @param redisson object
     * @param config object
     * @param regExpConfig
     * @param codec object
     */
    public RedissonSpringCacheManager(RedissonClient redisson, Map<String, CacheConfig> config, Map<Pattern, CacheConfig> regExpConfig, Codec codec) {
        this.redisson = redisson;
        if (config != null) {
            this.configMap.putAll(config);
        }
        if (regExpConfig != null) {
            this.regExpMap.putAll(regExpConfig);
        }
        this.codec = codec;
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
    public RedissonSpringCacheManager(RedissonClient redisson, String configLocation) {
        this(redisson, configLocation, null, null);
    }

    /**
     * Creates CacheManager supplied by Redisson instance, 
     * Cache config location path and RegExp pattern config location path.
     * <p>
     * Loads the config file from the class path, interpreting plain paths as class path resource names
     * that include the package path (e.g. "mypackage/myresource.txt").
     *
     * @param redisson object
     * @param configLocation path
     * @param regExpConfigLocation
     */
    public RedissonSpringCacheManager(RedissonClient redisson, String configLocation, String regExpConfigLocation) {
        this(redisson, configLocation, regExpConfigLocation, null);
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
    public RedissonSpringCacheManager(RedissonClient redisson, String configLocation, Codec codec) {
        this(redisson, configLocation, null, codec);

    }

    
    /**
     * Creates CacheManager supplied by Redisson instance, Codec instance,
     * Config location path and RegExp config location path.
     * <p>
     * Each Cache instance share one Codec instance.
     * <p>
     * Loads the config file from the class path, interpreting plain paths as class path resource names
     * that include the package path (e.g. "mypackage/myresource.txt").
     *
     * @param redisson object
     * @param configLocation path
     * @param regExpConfigLocation
     * @param codec object
     */
    public RedissonSpringCacheManager(RedissonClient redisson, String configLocation, String regExpConfigLocation, Codec codec) {
        this.redisson = redisson;
        this.configLocation = configLocation;
        this.regExpConfigLocation = regExpConfigLocation;
        this.codec = codec;
    }

    /**
     * Set cache config location
     *
     * @param configLocation object
     */
    public void setConfigLocation(String configLocation) {
        this.configLocation = configLocation;
    }

    /**
     * Set RegExp pattern config location
     *
     * @param regExpConfigLocation object
     */
    public void setRegExpConfigLocation(String regExpConfigLocation) {
        this.regExpConfigLocation = regExpConfigLocation;
    }

    /**
     * Set cache config mapped by cache name
     *
     * @param config object
     */
    public void setConfig(Map<String, CacheConfig> config) {
        this.configMap.clear();
        if (config == null) {
            return;
        }
        this.configMap.putAll(config);
    }

    /**
     * Set cache regexp config mapped by pattern
     *
     * @param config object
     */
    public void setRegExpConfig(Map<Pattern, CacheConfig> config) {
        this.regExpMap.clear();
        if (config == null) {
            return;
        }
        this.regExpMap.putAll(config);
    }

    /**
     * Set Redisson instance
     *
     * @param redisson instance
     */
    public void setRedisson(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * Set Codec instance shared between all Cache instances
     *
     * @param codec object
     */
    public void setCodec(Codec codec) {
        this.codec = codec;
    }

    @Override
    public Cache getCache(String name) {
        CacheConfig config = configMap.get(name);
        final String cacheName;
        if (config == null) {
            Pattern pattern = testRegExp(name);
            if (pattern == null) {
                config = new CacheConfig();
                configMap.put(name, config);
                
                RMap<Object, Object> map = createMap(name);
                return new RedissonCache(redisson, map);
            } else {
                config = regExpMap.get(pattern);
                cacheName = pattern.pattern();
            }
        } else {
            cacheName = name;
        }
        if (config.getMaxIdleTime() == 0 && config.getTTL() == 0) {
            RMap<Object, Object> map = createMap(cacheName);
            return new RedissonCache(redisson, map);
        }
        RMapCache<Object, Object> map = createMapCache(cacheName);
        return new RedissonCache(redisson, map, config);
    }

    private Pattern testRegExp(String name) {
        for (Pattern exp : regExpMap.keySet()) {
            if (exp.matcher(name).matches()) {
                return exp;
            }
        }
        return null;
    }
    
    private RMap<Object, Object> createMap(String name) {
        if (codec != null) {
            return redisson.getMap(name, codec);
        }
        return redisson.getMap(name);
    }

    private RMapCache<Object, Object> createMapCache(String name) {
        if (codec != null) {
            return redisson.getMapCache(name, codec);
        }
        return redisson.getMapCache(name);
    }

    @Override
    public Collection<String> getCacheNames() {
        Set<String> names = Collections.emptySet();
        names.addAll(getConfigNames());
        names.addAll(getPatternNames());
        return Collections.unmodifiableSet(names);
    }

    public Collection<String> getConfigNames() {
        return Collections.unmodifiableSet(configMap.keySet());
    }

    public Collection<String> getPatternNames() {
        Set<String> patterns = Collections.emptySet();
        for (Pattern k : regExpMap.keySet()) {
            patterns.add(k.pattern());
        }
        return Collections.unmodifiableSet(patterns);
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (configLocation != null) {
            Resource resource = resourceLoader.getResource(configLocation);
            try {
                setConfig(CacheConfig.fromJSON(resource.getInputStream()));
            } catch (IOException e) {
                // try to read yaml
                try {
                    setConfig(CacheConfig.fromYAML(resource.getInputStream()));
                } catch (IOException e1) {
                    throw new BeanDefinitionStoreException(
                            "Could not parse cache configuration at [" + configLocation + "]", e1);
                }
            }
        }
        if (regExpConfigLocation != null) {
            Resource resource = resourceLoader.getResource(regExpConfigLocation);
            Map<String, CacheConfig> confs;
            try {
                confs = CacheConfig.fromJSON(resource.getInputStream());
            } catch (IOException e) {
                // try to read yaml
                try {
                    confs = CacheConfig.fromYAML(resource.getInputStream());
                } catch (IOException e1) {
                    throw new BeanDefinitionStoreException(
                            "Could not parse cache configuration at [" + configLocation + "]", e1);
                }
            }
            for (Map.Entry<String, CacheConfig> conf : confs.entrySet()) {
                regExpMap.put(Pattern.compile(conf.getKey()), conf.getValue());
            }
        }
    }
    
}
