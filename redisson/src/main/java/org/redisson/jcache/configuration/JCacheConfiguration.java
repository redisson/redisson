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
package org.redisson.jcache.configuration;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;

/**
 * Configuration object for JCache {@link javax.cache.Cache}
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class JCacheConfiguration<K, V> implements CompleteConfiguration<K, V> {

    private static final long serialVersionUID = -7861479608049089078L;
    
    private final ExpiryPolicy expiryPolicy;
    private final MutableConfiguration<K, V> delegate;
    
    public JCacheConfiguration(Configuration<K, V> configuration) {
        if (configuration != null) {
            if (configuration instanceof RedissonConfiguration) {
                configuration = ((RedissonConfiguration<K, V>)configuration).getJcacheConfig();
            }
            
            if (configuration instanceof CompleteConfiguration) {
                delegate = new MutableConfiguration<K, V>((CompleteConfiguration<K, V>) configuration);
            } else {
                delegate = new MutableConfiguration<K, V>();
                delegate.setStoreByValue(configuration.isStoreByValue());
                delegate.setTypes(configuration.getKeyType(), configuration.getValueType());
            }
        } else {
            delegate = new MutableConfiguration<K, V>();
        }
        
        this.expiryPolicy = delegate.getExpiryPolicyFactory().create();
    }
    
    @Override
    public Class<K> getKeyType() {
        if (delegate.getKeyType() == null) {
            return (Class<K>) Object.class; 
        }
        return delegate.getKeyType();
    }

    @Override
    public Class<V> getValueType() {
        if (delegate.getValueType() == null) {
            return (Class<V>) Object.class;
        }
        return delegate.getValueType();
    }

    @Override
    public boolean isStoreByValue() {
        return delegate.isStoreByValue();
    }

    @Override
    public boolean isReadThrough() {
        return delegate.isReadThrough();
    }

    @Override
    public boolean isWriteThrough() {
        return delegate.isWriteThrough();
    }

    @Override
    public boolean isStatisticsEnabled() {
        return delegate.isStatisticsEnabled();
    }
    
    public void setStatisticsEnabled(boolean enabled) {
        delegate.setStatisticsEnabled(enabled);
    }
    
    public void setManagementEnabled(boolean enabled) {
        delegate.setManagementEnabled(enabled);
    }

    @Override
    public boolean isManagementEnabled() {
        return delegate.isManagementEnabled();
    }

    @Override
    public Iterable<CacheEntryListenerConfiguration<K, V>> getCacheEntryListenerConfigurations() {
        return delegate.getCacheEntryListenerConfigurations();
    }
    
    public void addCacheEntryListenerConfiguration(
            CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        delegate.addCacheEntryListenerConfiguration(cacheEntryListenerConfiguration);
    }
    
    public void removeCacheEntryListenerConfiguration(
            CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        delegate.removeCacheEntryListenerConfiguration(cacheEntryListenerConfiguration);
    }

    @Override
    public Factory<CacheLoader<K, V>> getCacheLoaderFactory() {
        return delegate.getCacheLoaderFactory();
    }

    @Override
    public Factory<CacheWriter<? super K, ? super V>> getCacheWriterFactory() {
        return delegate.getCacheWriterFactory();
    }

    @Override
    public Factory<ExpiryPolicy> getExpiryPolicyFactory() {
        return delegate.getExpiryPolicyFactory();
    }
    
    public ExpiryPolicy getExpiryPolicy() {
        return expiryPolicy;
    }

    
    
}
