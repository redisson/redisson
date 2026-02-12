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
package org.redisson.jcache;

import org.redisson.Redisson;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.error.YAMLException;

import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class JCachingProvider implements CachingProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JCachingProvider.class);

    private final Map<ClassLoader, Map<URI, CacheManager>> managers = new ConcurrentHashMap<>();
    
    private static final String DEFAULT_URI_PATH = "jsr107-default-config";
    private static URI defaulturi;
    
    static {
        try {
            defaulturi = new URI(DEFAULT_URI_PATH);
        } catch (URISyntaxException e) {
            throw new javax.cache.CacheException(e);
        }
    }
    
    @Override
    public CacheManager getCacheManager(URI uri, ClassLoader classLoader, Properties properties) {
        if (uri == null) {
            uri = getDefaultURI();
        }
        if (uri == null) {
            throw new CacheException("Uri is not defined. Can't load default configuration");
        }
        
        if (classLoader == null) {
            classLoader = getDefaultClassLoader();
        }

        Map<URI, CacheManager> value = managers.computeIfAbsent(classLoader, k -> new ConcurrentHashMap<>());
        CacheManager manager = value.get(uri);
        if (manager != null) {
            return manager;
        }
        
        Config config = loadConfig(uri);
        
        Redisson redisson = null;
        if (config != null) {
            redisson = (Redisson) Redisson.create(config);
        }
        manager = new JCacheManager(redisson, classLoader, this, properties, uri);
        CacheManager oldManager = value.putIfAbsent(uri, manager);
        if (oldManager != null) {
            if (redisson != null) {
                redisson.shutdown();
            }
            manager = oldManager;
        }
        LOG.info("JCacheManager created with uri: {} and properties: {}", uri, properties);
        return manager;
    }

    private Config loadConfig(URI uri) {
        Config config = null;
        try {
            URL yamlUrl = null;
            if (DEFAULT_URI_PATH.equals(uri.getPath())) {
                yamlUrl = JCachingProvider.class.getResource("/redisson-jcache.yaml");
            } else {
                yamlUrl = uri.toURL();
            }
            if (yamlUrl != null) {
                config = Config.fromYAML(yamlUrl);
            } else {
                throw new FileNotFoundException("/redisson-jcache.yaml");
            }
        } catch (YAMLException e) {
            throw new CacheException(e);
        } catch (IOException e) {
            // skip
        }
        return config;
    }

    @Override
    public ClassLoader getDefaultClassLoader() {
        return getClass().getClassLoader();
    }

    @Override
    public URI getDefaultURI() {
        return defaulturi;
    }

    @Override
    public Properties getDefaultProperties() {
        return new Properties();
    }

    @Override
    public CacheManager getCacheManager(URI uri, ClassLoader classLoader) {
        return getCacheManager(uri, classLoader, getDefaultProperties());
    }

    @Override
    public CacheManager getCacheManager() {
        return getCacheManager(getDefaultURI(), getDefaultClassLoader());
    }

    @Override
    public void close() {
        for (ClassLoader classLoader : managers.keySet()) {
            close(classLoader);
        }
    }

    @Override
    public void close(ClassLoader classLoader) {
        Map<URI, CacheManager> uri2manager = managers.remove(classLoader);
        if (uri2manager != null) {
            for (Map.Entry<URI, CacheManager> entry : uri2manager.entrySet()) {
                entry.getValue().close();
                LOG.info("JCacheManager closed with uri: {} and properties: {}",
                        entry.getKey(), entry.getValue().getProperties());
            }
        }
    }

    @Override
    public void close(URI uri, ClassLoader classLoader) {
        Map<URI, CacheManager> uri2manager = managers.get(classLoader);
        if (uri2manager == null) {
            return;
        }
        CacheManager manager = uri2manager.remove(uri);
        if (manager == null) {
            return;
        }
        manager.close();
        LOG.info("JCacheManager closed with uri: {} and properties: {}", uri, manager.getProperties());
        if (uri2manager.isEmpty()) {
            managers.remove(classLoader, Collections.emptyMap());
        }
    }

    @Override
    public boolean isSupported(OptionalFeature optionalFeature) {
        // TODO implement support of store_by_reference
        return false;
    }

}
