/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.jcache.bean.EmptyStatisticsMXBean;
import org.redisson.jcache.bean.JCacheManagementMXBean;
import org.redisson.jcache.bean.JCacheStatisticsMXBean;
import org.redisson.jcache.configuration.JCacheConfiguration;
import org.redisson.jcache.configuration.RedissonConfiguration;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.spi.CachingProvider;
import javax.management.*;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class JCacheManager implements CacheManager {

    private static final EmptyStatisticsMXBean EMPTY_INSTANCE = new EmptyStatisticsMXBean();
    private static final MBeanServer MBEAN_SERVER = ManagementFactory.getPlatformMBeanServer();
    
    private final ClassLoader classLoader;
    private final CachingProvider cacheProvider;
    private final Properties properties;
    private final URI uri;
    private final ConcurrentMap<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();
    private final ConcurrentMap<Cache<?, ?>, JCacheStatisticsMXBean> statBeans = new ConcurrentHashMap<>();
    private final ConcurrentMap<Cache<?, ?>, JCacheManagementMXBean> managementBeans = new ConcurrentHashMap<>();
    
    private final AtomicBoolean closed = new AtomicBoolean();
    
    private final Redisson redisson;
    
    JCacheManager(Redisson redisson, ClassLoader classLoader, CachingProvider cacheProvider, Properties properties, URI uri) {
        super();
        this.classLoader = classLoader;
        this.cacheProvider = cacheProvider;
        this.properties = properties;
        this.uri = uri;
        this.redisson = redisson;
    }

    @Override
    public CachingProvider getCachingProvider() {
        return cacheProvider;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException();
        }
    }
    
    @Override
    public <K, V, C extends Configuration<K, V>> Cache<K, V> createCache(String cacheName, C configuration)
            throws IllegalArgumentException {
        checkNotClosed();
        Redisson cacheRedisson = redisson;
        
        if (cacheName == null) {
            throw new NullPointerException();
        }
        if (configuration == null) {
            throw new NullPointerException();
        }
        
        if (cacheRedisson == null && !(configuration instanceof RedissonConfiguration)) {
            throw new IllegalStateException("Default configuration hasn't been specified!");
        }
        
        boolean hasOwnRedisson = false;
        if (configuration instanceof RedissonConfiguration) {
            RedissonConfiguration<K, V> rc = (RedissonConfiguration<K, V>) configuration;
            if (rc.getConfig() != null) {
                cacheRedisson = (Redisson) Redisson.create(rc.getConfig());
                hasOwnRedisson = true;
            } else {
                cacheRedisson = (Redisson) rc.getRedisson();
            }
        }
        
        JCacheConfiguration<K, V> cfg = new JCacheConfiguration<K, V>(configuration);
        JCache<K, V> cache = new JCache<>(this, cacheRedisson, cacheName, cfg, hasOwnRedisson);
        Cache<?, ?> oldCache = caches.putIfAbsent(cacheName, cache);
        if (oldCache != null) {
            throw new CacheException("Cache " + cacheName + " already exists");
        }
        if (cfg.isStatisticsEnabled()) {
            enableStatistics(cacheName, true);
        }
        if (cfg.isManagementEnabled()) {
            enableManagement(cacheName, true);
        }
        return cache;
    }

    @Override
    public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType) {
        checkNotClosed();
        if (cacheName == null) {
            throw new NullPointerException();
        }
        if (keyType == null) {
            throw new NullPointerException();
        }
        if (valueType == null) {
            throw new NullPointerException();
        }
        
        Cache<?, ?> cache = caches.get(cacheName);
        if (cache == null) {
            return null;
        }
        
        if (!keyType.isAssignableFrom(cache.getConfiguration(CompleteConfiguration.class).getKeyType())) {
            throw new ClassCastException("Wrong type of key for " + cacheName);
        }
        if (!valueType.isAssignableFrom(cache.getConfiguration(CompleteConfiguration.class).getValueType())) {
            throw new ClassCastException("Wrong type of value for " + cacheName);
        }
        return (Cache<K, V>) cache;
    }

    @Override
    public <K, V> Cache<K, V> getCache(String cacheName) {
        checkNotClosed();
        Cache<K, V> cache = (Cache<K, V>) getCache(cacheName, Object.class, Object.class);
        if (cache != null) {
            if (!cache.getConfiguration(CompleteConfiguration.class).getKeyType().isAssignableFrom(Object.class)) {
                throw new IllegalArgumentException("Wrong type of key for " + cacheName);
            }
            if (!cache.getConfiguration(CompleteConfiguration.class).getValueType().isAssignableFrom(Object.class)) {
                throw new IllegalArgumentException("Wrong type of value for " + cacheName);
            }
        }
        return cache;
    }

    @Override
    public Iterable<String> getCacheNames() {
        return Collections.unmodifiableSet(new HashSet<>(caches.keySet()));
    }

    @Override
    public void destroyCache(String cacheName) {
        checkNotClosed();
        if (cacheName == null) {
            throw new NullPointerException();
        }

        Cache<?, ?> cache = caches.get(cacheName);
        if (cache != null) {
            cache.clear();
            cache.close();
        }
    }
    
    public void closeCache(JCache<?, ?> cache) {
        caches.remove(cache.getRawName());
        unregisterStatisticsBean(cache);
        unregisterManagementBean(cache);
    }

    @Override
    public void enableManagement(String cacheName, boolean enabled) {
        checkNotClosed();
        if (cacheName == null) {
            throw new NullPointerException();
        }
        
        Cache<?, ?> cache = caches.get(cacheName);
        if (cache == null) {
            throw new NullPointerException();
        }
        
        if (enabled) {
            JCacheManagementMXBean statBean = managementBeans.get(cache);
            if (statBean == null) {
                statBean = new JCacheManagementMXBean(cache);
                JCacheManagementMXBean oldBean = managementBeans.putIfAbsent(cache, statBean);
                if (oldBean != null) {
                    statBean = oldBean;
                }
            }
            try {
                ObjectName objectName = queryNames("Configuration", cache);
                if (MBEAN_SERVER.queryNames(objectName, null).isEmpty()) {
                    MBEAN_SERVER.registerMBean(statBean, objectName);
                }
            } catch (MalformedObjectNameException e) {
                throw new CacheException(e);
            } catch (InstanceAlreadyExistsException e) {
                throw new CacheException(e);
            } catch (MBeanRegistrationException e) {
                throw new CacheException(e);
            } catch (NotCompliantMBeanException e) {
                throw new CacheException(e);
            }
        } else {
            unregisterManagementBean(cache);
        }
        cache.getConfiguration(JCacheConfiguration.class).setManagementEnabled(enabled);
    }

    private ObjectName queryNames(String baseName, Cache<?, ?> cache) throws MalformedObjectNameException {
        String name = getName(baseName, cache);
        return new ObjectName(name);
    }

    private void unregisterManagementBean(Cache<?, ?> cache) {
        JCacheManagementMXBean statBean = managementBeans.remove(cache);
        if (statBean != null) {
            try {
                ObjectName name = queryNames("Configuration", cache);
                for (ObjectName objectName : MBEAN_SERVER.queryNames(name, null)) {
                    MBEAN_SERVER.unregisterMBean(objectName);
                }
            } catch (MalformedObjectNameException e) {
                throw new CacheException(e);
            } catch (MBeanRegistrationException e) {
                throw new CacheException(e);
            } catch (InstanceNotFoundException e) {
                throw new CacheException(e);
            }
        }
    }

    public JCacheStatisticsMXBean getStatBean(JCache<?, ?> cache) {
        JCacheStatisticsMXBean bean = statBeans.get(cache);
        if (bean != null) {
            return bean;
        }
        return EMPTY_INSTANCE;
    }
    
    private String getName(String name, Cache<?, ?> cache) {
        return "javax.cache:type=Cache" + name + ",CacheManager="
                + getURI().toString().replaceAll(",|:|=|\n", ".")
                + ",Cache=" + cache.getName().replaceAll(",|:|=|\n", ".");
    }
    
    @Override
    public void enableStatistics(String cacheName, boolean enabled) {
        checkNotClosed();
        if (cacheName == null) {
            throw new NullPointerException();
        }
        
        Cache<?, ?> cache = caches.get(cacheName);
        if (cache == null) {
            throw new NullPointerException();
        }
        
        if (enabled) {
            JCacheStatisticsMXBean statBean = statBeans.get(cache);
            if (statBean == null) {
                statBean = new JCacheStatisticsMXBean();
                JCacheStatisticsMXBean oldBean = statBeans.putIfAbsent(cache, statBean);
                if (oldBean != null) {
                    statBean = oldBean;
                }
            }
            try {
                ObjectName objectName = queryNames("Statistics", cache);
                if (!MBEAN_SERVER.isRegistered(objectName)) {
                    MBEAN_SERVER.registerMBean(statBean, objectName);
                }
            } catch (MalformedObjectNameException e) {
                throw new CacheException(e);
            } catch (InstanceAlreadyExistsException e) {
                throw new CacheException(e);
            } catch (MBeanRegistrationException e) {
                throw new CacheException(e);
            } catch (NotCompliantMBeanException e) {
                throw new CacheException(e);
            }
        } else {
            unregisterStatisticsBean(cache);
        }
        cache.getConfiguration(JCacheConfiguration.class).setStatisticsEnabled(enabled);
    }

    private void unregisterStatisticsBean(Cache<?, ?> cache) {
        JCacheStatisticsMXBean statBean = statBeans.remove(cache);
        if (statBean != null) {
            try {
                ObjectName name = queryNames("Statistics", cache);
                for (ObjectName objectName : MBEAN_SERVER.queryNames(name, null)) {
                    MBEAN_SERVER.unregisterMBean(objectName);
                }
            } catch (MalformedObjectNameException e) {
                throw new CacheException(e);
            } catch (MBeanRegistrationException e) {
                throw new CacheException(e);
            } catch (InstanceNotFoundException e) {
                throw new CacheException(e);
            }
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (cacheProvider != null) {
                cacheProvider.close(uri, classLoader);
            }
            for (Cache<?, ?> cache : caches.values()) {
                try {
                    cache.close();
                } catch (Exception e) {
                    // skip
                }
            }
            if (redisson != null) {
                redisson.shutdown();
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(getClass())) {
            return clazz.cast(this);
        }
        throw new IllegalArgumentException();
    }


}
