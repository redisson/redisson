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
package org.redisson.jcache.bean;

import javax.cache.configuration.CompleteConfiguration;
import javax.cache.management.CacheMXBean;

import org.redisson.jcache.JCache;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class JCacheManagementMXBean implements CacheMXBean {

    private final JCache<?, ?> cache;
    
    public JCacheManagementMXBean(JCache<?, ?> cache) {
        super();
        this.cache = cache;
    }

    @Override
    public String getKeyType() {
        return cache.getConfiguration(CompleteConfiguration.class).getKeyType().getName();
    }

    @Override
    public String getValueType() {
        return cache.getConfiguration(CompleteConfiguration.class).getValueType().getName();
    }

    @Override
    public boolean isReadThrough() {
        return cache.getConfiguration(CompleteConfiguration.class).isReadThrough();
    }

    @Override
    public boolean isWriteThrough() {
        return cache.getConfiguration(CompleteConfiguration.class).isWriteThrough();
    }

    @Override
    public boolean isStoreByValue() {
        return cache.getConfiguration(CompleteConfiguration.class).isStoreByValue();
    }

    @Override
    public boolean isStatisticsEnabled() {
        return cache.getConfiguration(CompleteConfiguration.class).isStatisticsEnabled();
    }

    @Override
    public boolean isManagementEnabled() {
        return cache.getConfiguration(CompleteConfiguration.class).isManagementEnabled();
    }

}
