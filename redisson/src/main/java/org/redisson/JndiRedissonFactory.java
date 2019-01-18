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
package org.redisson;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * Redisson object factory used to register instance in JNDI registry. 
 * 
 * @author Nikita Koksharov
 *
 */
public class JndiRedissonFactory implements ObjectFactory {

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment)
            throws Exception {
        Reference ref = (Reference) obj;
        RefAddr addr = ref.get("configPath");
        return buildClient(addr.getContent().toString());
    }
    
    protected RedissonClient buildClient(String configPath) throws NamingException {
        Config config = null;
        try {
            config = Config.fromJSON(new File(configPath), getClass().getClassLoader());
        } catch (IOException e) {
            // trying next format
            try {
                config = Config.fromYAML(new File(configPath), getClass().getClassLoader());
            } catch (IOException e1) {
                NamingException ex = new NamingException("Can't parse yaml config " + configPath);
                ex.initCause(e1);
                throw ex;
            }
        }
        
        try {
            return Redisson.create(config);
        } catch (Exception e) {
            NamingException ex = new NamingException();
            ex.initCause(e);
            throw ex;
        }
    }

}
