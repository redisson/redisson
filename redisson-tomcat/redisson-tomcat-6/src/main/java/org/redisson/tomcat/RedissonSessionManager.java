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
package org.redisson.tomcat;

import java.io.File;
import java.io.IOException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.util.LifecycleSupport;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * Redisson Session Manager for Apache Tomcat
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonSessionManager extends ManagerBase implements Lifecycle {

    private final Log log = LogFactory.getLog(RedissonSessionManager.class);

    protected LifecycleSupport lifecycle = new LifecycleSupport(this);
    
    private RedissonClient redisson;
    private String configPath;
    
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }
    
    public String getConfigPath() {
        return configPath;
    }
    
    @Override
    public int getRejectedSessions() {
        return 0;
    }

    @Override
    public void load() throws ClassNotFoundException, IOException {
    }

    @Override
    public void setRejectedSessions(int sessions) {
    }

    @Override
    public void unload() throws IOException {
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }

    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    @Override
    public Session createSession(String sessionId) {
        RedissonSession session = (RedissonSession) createEmptySession();
        
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(((Context) getContainer()).getSessionTimeout() * 60);

        if (sessionId == null) {
            sessionId = generateSessionId();
        }
        
        session.setId(sessionId);
        session.save();
        
        return session;
    }

    public RMap<String, Object> getMap(String sessionId) {
        return redisson.getMap("redisson_tomcat_session:" + sessionId);
    }
    
    @Override
    public Session findSession(String id) throws IOException {
        Session result = super.findSession(id);
        if (result == null && id != null) {
            RedissonSession session = (RedissonSession) createEmptySession();
            session.setId(id);
            session.load();
            return session;
        }
        
        return result;
    }
    
    @Override
    public Session createEmptySession() {
        return new RedissonSession(this);
    }
    
    @Override
    public void remove(Session session) {
        super.remove(session);
        
        getMap(session.getId()).delete();
    }
    
    public RedissonClient getRedisson() {
        return redisson;
    }
    
    @Override
    public void start() throws LifecycleException {
        Config config = null;
        try {
            config = Config.fromJSON(new File(configPath), getClass().getClassLoader());
        } catch (IOException e) {
            // trying next format
            try {
                config = Config.fromYAML(new File(configPath), getClass().getClassLoader());
            } catch (IOException e1) {
                log.error("Can't parse json config " + configPath, e);
                throw new LifecycleException("Can't parse yaml config " + configPath, e1);
            }
        }

        try {
            redisson = Redisson.create(config);
        } catch (Exception e) {
            throw new LifecycleException(e);
        }
        
        lifecycle.fireLifecycleEvent(START_EVENT, null);
    }

    @Override
    public void stop() throws LifecycleException {
        try {
            if (redisson != null) {
                redisson.shutdown();
            }
        } catch (Exception e) {
            throw new LifecycleException(e);
        }
        
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
    }
    
}
