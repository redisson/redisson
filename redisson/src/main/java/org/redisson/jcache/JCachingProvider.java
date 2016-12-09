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
package org.redisson.jcache;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;

import org.redisson.Redisson;
import org.redisson.config.Config;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class JCachingProvider implements CachingProvider {

    private final ConcurrentMap<ClassLoader, ConcurrentMap<URI, CacheManager>> managers = 
                            new ConcurrentHashMap<ClassLoader, ConcurrentMap<URI, CacheManager>>();
    
    private static URI DEFAULT_URI;
    
    static {
        try {
            DEFAULT_URI = JCachingProvider.class.getResource("/redisson-jcache.json").toURI();
        } catch (Exception e) {
            // trying next format
            try {
                DEFAULT_URI = JCachingProvider.class.getResource("/redisson-jcache.yaml").toURI();
            } catch (Exception e1) {
                // skip
            }
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
        
        ConcurrentMap<URI, CacheManager> value = new ConcurrentHashMap<URI, CacheManager>();
        ConcurrentMap<URI, CacheManager> oldValue = managers.putIfAbsent(classLoader, value);
        if (oldValue != null) {
            value = oldValue;
        }
        
        CacheManager manager = value.get(uri);
        if (manager != null) {
            return manager;
        }
        
        Config config = null;
        try {
            config = Config.fromJSON(uri.toURL());
        } catch (IOException e) {
            try {
                config = Config.fromYAML(uri.toURL());
            } catch (IOException e2) {
                throw new IllegalArgumentException("Can't parse config " + uri, e2);
            }
        }
        
        Redisson redisson = (Redisson) Redisson.create(config);
        manager = new JCacheManager(redisson, classLoader, this, properties, uri);
        CacheManager oldManager = value.putIfAbsent(uri, manager);
        if (oldManager != null) {
            redisson.shutdown();
            manager = oldManager;
        }
        return manager;
    }

    @Override
    public ClassLoader getDefaultClassLoader() {
        return getClass().getClassLoader();
    }

    @Override
    public URI getDefaultURI() {
        return DEFAULT_URI;
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
        synchronized (managers) {
            for (ClassLoader classLoader : managers.keySet()) {
                close(classLoader);
            }
        }
    }

    @Override
    public void close(ClassLoader classLoader) {
        Map<URI, CacheManager> uri2manager = managers.remove(classLoader);
        if (uri2manager != null) {
            for (CacheManager manager : uri2manager.values()) {
                manager.close();
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
