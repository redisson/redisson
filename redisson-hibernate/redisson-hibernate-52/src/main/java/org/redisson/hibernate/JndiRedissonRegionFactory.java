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
package org.redisson.hibernate;

import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hibernate.cache.CacheException;
import org.hibernate.engine.jndi.internal.JndiServiceImpl;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.internal.util.jndi.JndiHelper;
import org.redisson.api.RedissonClient;

/**
 * Hibernate Cache region factory based on Redisson. 
 * Uses Redisson instance located in JNDI.
 * 
 * @author Nikita Koksharov 
 *
 */
public class JndiRedissonRegionFactory extends RedissonRegionFactory {

    private static final long serialVersionUID = -4814502675083325567L;

    public static final String JNDI_NAME = CONFIG_PREFIX + "jndi_name";
    
    @Override
    protected RedissonClient createRedissonClient(Properties properties) {
        String jndiName = ConfigurationHelper.getString(JNDI_NAME, properties);
        if (jndiName == null) {
            throw new CacheException(JNDI_NAME + " property not set");
        }
        
        Properties jndiProperties = JndiServiceImpl.extractJndiProperties(properties);
        InitialContext context = null;
        try {
            context = new InitialContext(jndiProperties);
            return (RedissonClient) context.lookup(jndiName);
        } catch (NamingException e) {
            throw new CacheException("Unable to locate Redisson instance by name: " + jndiName, e);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                    throw new CacheException("Unable to close JNDI context", e);
                }
            }
        }
    }

    @Override
    public void stop() {
    }

}
