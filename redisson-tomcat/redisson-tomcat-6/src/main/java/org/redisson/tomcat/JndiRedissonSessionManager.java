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
package org.redisson.tomcat;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.catalina.LifecycleException;
import org.redisson.api.RedissonClient;

/**
 * Redisson Session Manager for Apache Tomcat. 
 * Uses Redisson instance located in JNDI.
 * 
 * @author Nikita Koksharov 
 *
 */
public class JndiRedissonSessionManager extends RedissonSessionManager {

    private String jndiName;

    @Override
    public void setConfigPath(String configPath) {
        throw new IllegalArgumentException("configPath is unavaialble for JNDI based manager");
    }

    @Override
    protected RedissonClient buildClient() throws LifecycleException {
        InitialContext context = null;
        try {
            context = new InitialContext();
            Context envCtx = (Context) context.lookup("java:comp/env");
            return (RedissonClient) envCtx.lookup(jndiName);
        } catch (NamingException e) {
            throw new LifecycleException("Unable to locate Redisson instance by name: " + jndiName, e);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                    throw new LifecycleException("Unable to close JNDI context", e);
                }
            }
        }
    }

    public String getJndiName() {
        return jndiName;
    }

    public void setJndiName(String jndiName) {
        this.jndiName = jndiName;
    }

    @Override
    protected void shutdownRedisson() {
    }
    
}
