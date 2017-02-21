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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.session.StandardSession;
import org.redisson.api.RMap;

/**
 * Redisson Session object for Apache Tomcat
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonSession extends StandardSession {

    private final RedissonSessionManager redissonManager;
    private final Map<String, Object> attrs;
    private RMap<String, Object> map;
    
    public RedissonSession(RedissonSessionManager manager) {
        super(manager);
        this.redissonManager = manager;
        
        try {
            Field attr = StandardSession.class.getDeclaredField("attributes");
            attrs = (Map<String, Object>) attr.get(this);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final long serialVersionUID = -2518607181636076487L;

    @Override
    public void setId(String id, boolean notify) {
        super.setId(id, notify);
        map = redissonManager.getMap(id);
    }
    
    @Override
    public void setCreationTime(long time) {
        super.setCreationTime(time);

        if (map != null) {
            Map<String, Object> newMap = new HashMap<String, Object>(3);
            newMap.put("session:creationTime", creationTime);
            newMap.put("session:lastAccessedTime", lastAccessedTime);
            newMap.put("session:thisAccessedTime", thisAccessedTime);
            map.putAll(newMap);
        }
    }
    
    @Override
    public void access() {
        super.access();
        
        if (map != null) {
            Map<String, Object> newMap = new HashMap<String, Object>(2);
            newMap.put("session:lastAccessedTime", lastAccessedTime);
            newMap.put("session:thisAccessedTime", thisAccessedTime);
            map.putAll(newMap);
            if (getMaxInactiveInterval() >= 0) {
                map.expire(getMaxInactiveInterval(), TimeUnit.SECONDS);
            }
        }
    }
    
    @Override
    public void setMaxInactiveInterval(int interval) {
        super.setMaxInactiveInterval(interval);
        
        if (map != null) {
            map.fastPut("session:maxInactiveInterval", maxInactiveInterval);
            if (maxInactiveInterval >= 0) {
                map.expire(getMaxInactiveInterval(), TimeUnit.SECONDS);
            }
        }
    }
    
    @Override
    public void setValid(boolean isValid) {
        super.setValid(isValid);
        
        if (map != null) {
            map.fastPut("session:isValid", isValid);
        }
    }
    
    @Override
    public void setNew(boolean isNew) {
        super.setNew(isNew);
        
        if (map != null) {
            map.fastPut("session:isNew", isNew);
        }
    }
    
    @Override
    public void endAccess() {
        boolean oldValue = isNew;
        super.endAccess();

        if (isNew != oldValue) {
            map.fastPut("session:isNew", isNew);
        }
    }
    
    @Override
    public void setAttribute(String name, Object value, boolean notify) {
        super.setAttribute(name, value, notify);
        
        if (map != null && value != null) {
            map.fastPut(name, value);
        }
    }
    
    @Override
    protected void removeAttributeInternal(String name, boolean notify) {
        super.removeAttributeInternal(name, notify);
        
        if (map != null) {
            map.fastRemove(name);
        }
    }
    
    public void save() {
        Map<String, Object> newMap = new HashMap<String, Object>();
        newMap.put("session:creationTime", creationTime);
        newMap.put("session:lastAccessedTime", lastAccessedTime);
        newMap.put("session:thisAccessedTime", thisAccessedTime);
        newMap.put("session:maxInactiveInterval", maxInactiveInterval);
        newMap.put("session:isValid", isValid);
        newMap.put("session:isNew", isNew);
        
        for (Entry<String, Object> entry : attrs.entrySet()) {
            newMap.put(entry.getKey(), entry.getValue());
        }
        
        map.putAll(newMap);
        
        if (maxInactiveInterval >= 0) {
            map.expire(getMaxInactiveInterval(), TimeUnit.SECONDS);
        }
    }
    
    public void load() {
        Set<Entry<String, Object>> entrySet = map.readAllEntrySet();
        for (Entry<String, Object> entry : entrySet) {
            if ("session:creationTime".equals(entry.getKey())) {
                creationTime = (Long) entry.getValue();
            } else if ("session:lastAccessedTime".equals(entry.getKey())) {
                lastAccessedTime = (Long) entry.getValue();
            } else if ("session:thisAccessedTime".equals(entry.getKey())) {
                thisAccessedTime = (Long) entry.getValue();
            } else if ("session:maxInactiveInterval".equals(entry.getKey())) {
                maxInactiveInterval = (Integer) entry.getValue();
            } else if ("session:isValid".equals(entry.getKey())) {
                isValid = (Boolean) entry.getValue();
            } else if ("session:isNew".equals(entry.getKey())) {
                isNew = (Boolean) entry.getValue();
            } else {
                setAttribute(entry.getKey(), entry.getValue(), false);
            }
        }
    }
    
}
