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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.session.StandardSession;
import org.redisson.api.RSet;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.tomcat.RedissonSessionManager.ReadMode;
import org.redisson.tomcat.RedissonSessionManager.UpdateMode;

/**
 * Redisson Session object for Apache Tomcat
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonSession extends StandardSession {

    private static final String IS_NEW_ATTR = "session:isNew";
    private static final String IS_VALID_ATTR = "session:isValid";
    private static final String THIS_ACCESSED_TIME_ATTR = "session:thisAccessedTime";
    private static final String MAX_INACTIVE_INTERVAL_ATTR = "session:maxInactiveInterval";
    private static final String LAST_ACCESSED_TIME_ATTR = "session:lastAccessedTime";
    private static final String CREATION_TIME_ATTR = "session:creationTime";
    private static final String IS_EXPIRATION_LOCKED = "session:isExpirationLocked";
    
    public static final Set<String> ATTRS = new HashSet<String>(Arrays.asList(
            IS_NEW_ATTR, IS_VALID_ATTR, 
            THIS_ACCESSED_TIME_ATTR, MAX_INACTIVE_INTERVAL_ATTR, 
            LAST_ACCESSED_TIME_ATTR, CREATION_TIME_ATTR, IS_EXPIRATION_LOCKED
            ));
    
    private boolean isExpirationLocked;
    private boolean loaded;
    private final RedissonSessionManager redissonManager;
    private final Map<String, Object> attrs;
    private RMap<String, Object> map;
    private final RTopic topic;
    private final RedissonSessionManager.ReadMode readMode;
    private final UpdateMode updateMode;
    
    private Set<String> removedAttributes = Collections.emptySet();
    
    private final boolean broadcastSessionEvents;
    
    public RedissonSession(RedissonSessionManager manager, RedissonSessionManager.ReadMode readMode, UpdateMode updateMode, boolean broadcastSessionEvents) {
        super(manager);
        this.redissonManager = manager;
        this.readMode = readMode;
        this.updateMode = updateMode;
        this.topic = redissonManager.getTopic();
        this.broadcastSessionEvents = broadcastSessionEvents;
        
        if (updateMode == UpdateMode.AFTER_REQUEST) {
            removedAttributes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        }
        
        try {
            Field attr = StandardSession.class.getDeclaredField("attributes");
            attrs = (Map<String, Object>) attr.get(this);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final long serialVersionUID = -2518607181636076487L;

    @Override
    public Object getAttribute(String name) {
        if (readMode == ReadMode.REDIS) {
            if (!isValidInternal()) {
                throw new IllegalStateException(sm.getString("standardSession.getAttribute.ise"));
            }

            if (name == null) {
                return null;
            }

            return map.get(name);
        } else {
            if (!loaded) {
                synchronized (this) {
                    if (!loaded) {
                        Map<String, Object> storedAttrs = map.readAllMap();
                        
                        load(storedAttrs);
                        loaded = true;
                    }
                }
            }
        }

        return super.getAttribute(name);
    }
    
    @Override
    public Enumeration<String> getAttributeNames() {
        if (readMode == ReadMode.REDIS) {
            if (!isValidInternal()) {
                throw new IllegalStateException
                    (sm.getString("standardSession.getAttributeNames.ise"));
            }
            return Collections.enumeration(map.readAllKeySet());
        }
        
        return super.getAttributeNames();
    }

    @Override
    public String[] getValueNames() {
        if (readMode == ReadMode.REDIS) {
            if (!isValidInternal()) {
                throw new IllegalStateException
                    (sm.getString("standardSession.getAttributeNames.ise"));
            }
            Set<String> keys = map.readAllKeySet();
            return keys.toArray(new String[keys.size()]);
        }
        
        return super.getValueNames();
    }
    
    public void delete() {
        if (map == null) {
            map = redissonManager.getMap(id);
        }
        
        if (broadcastSessionEvents) {
            RSet<String> set = redissonManager.getNotifiedNodes(id);
            set.add(redissonManager.getNodeId());
            set.expire(60, TimeUnit.SECONDS);
            map.fastPut(IS_EXPIRATION_LOCKED, true);
            map.expire(60, TimeUnit.SECONDS);
        } else {
            map.delete();
        }
        if (readMode == ReadMode.MEMORY) {
            topic.publish(new AttributesClearMessage(redissonManager.getNodeId(), getId()));
        }
        map = null;
    }
    
    @Override
    public void setCreationTime(long time) {
        super.setCreationTime(time);

        if (map != null) {
            Map<String, Object> newMap = new HashMap<String, Object>(3);
            newMap.put(CREATION_TIME_ATTR, creationTime);
            newMap.put(LAST_ACCESSED_TIME_ATTR, lastAccessedTime);
            newMap.put(THIS_ACCESSED_TIME_ATTR, thisAccessedTime);
            map.putAll(newMap);
            if (readMode == ReadMode.MEMORY) {
                topic.publish(createPutAllMessage(newMap));
            }
        }
    }
    
    @Override
    public void access() {
        super.access();
        
        if (map != null) {
            Map<String, Object> newMap = new HashMap<String, Object>(2);
            newMap.put(LAST_ACCESSED_TIME_ATTR, lastAccessedTime);
            newMap.put(THIS_ACCESSED_TIME_ATTR, thisAccessedTime);
            map.putAll(newMap);
            if (readMode == ReadMode.MEMORY) {
                topic.publish(createPutAllMessage(newMap));
            }
            expireSession();
        }
    }

    protected void expireSession() {
        if (isExpirationLocked) {
            return;
        }
        if (maxInactiveInterval >= 0) {
            map.expire(maxInactiveInterval + 60, TimeUnit.SECONDS);
        }
    }

    protected AttributesPutAllMessage createPutAllMessage(Map<String, Object> newMap) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (Entry<String, Object> entry : newMap.entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        try {
            return new AttributesPutAllMessage(redissonManager.getNodeId(), getId(), map, this.map.getCodec().getMapValueEncoder());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    @Override
    public void setMaxInactiveInterval(int interval) {
        super.setMaxInactiveInterval(interval);
        
        if (map != null) {
            fastPut(MAX_INACTIVE_INTERVAL_ATTR, maxInactiveInterval);
            expireSession();
        }
    }

    private void fastPut(String name, Object value) {
        map.fastPut(name, value);
        if (readMode == ReadMode.MEMORY) {
            try {
                topic.publish(new AttributeUpdateMessage(redissonManager.getNodeId(), getId(), name, value, this.map.getCodec().getMapValueEncoder()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
    
    @Override
    public void setValid(boolean isValid) {
        super.setValid(isValid);
        
        if (map != null) {
            if (!isValid && !map.isExists()) {
                return;
            }
            
            fastPut(IS_VALID_ATTR, isValid);
        }
    }
    
    @Override
    public void setNew(boolean isNew) {
        super.setNew(isNew);
        
        if (map != null) {
            fastPut(IS_NEW_ATTR, isNew);
        }
    }
    
    @Override
    public void endAccess() {
        boolean oldValue = isNew;
        super.endAccess();

        if (isNew != oldValue && map != null) {
            fastPut(IS_NEW_ATTR, isNew);
        }
    }
    
    public void superSetAttribute(String name, Object value, boolean notify) {
        super.setAttribute(name, value, notify);
    }
    
    @Override
    public void setAttribute(String name, Object value, boolean notify) {
        super.setAttribute(name, value, notify);

        if (value == null) {
            return;
        }
        if (updateMode == UpdateMode.DEFAULT && map != null) {
            fastPut(name, value);
        }
        if (updateMode == UpdateMode.AFTER_REQUEST) {
            removedAttributes.remove(name);
        }
    }
    
    public void superRemoveAttributeInternal(String name, boolean notify) {
        super.removeAttributeInternal(name, notify);
    }
    
    @Override
    protected void removeAttributeInternal(String name, boolean notify) {
        super.removeAttributeInternal(name, notify);
        
        if (updateMode == UpdateMode.DEFAULT && map != null) {
            map.fastRemove(name);
            if (readMode == ReadMode.MEMORY) {
                topic.publish(new AttributeRemoveMessage(redissonManager.getNodeId(), getId(), new HashSet<String>(Arrays.asList(name))));
            }
        }
        if (updateMode == UpdateMode.AFTER_REQUEST) {
            removedAttributes.add(name);
        }
    }
    
    public void save() {
        if (map == null) {
            map = redissonManager.getMap(id);
        }
        
        Map<String, Object> newMap = new HashMap<String, Object>();
        newMap.put(CREATION_TIME_ATTR, creationTime);
        newMap.put(LAST_ACCESSED_TIME_ATTR, lastAccessedTime);
        newMap.put(THIS_ACCESSED_TIME_ATTR, thisAccessedTime);
        newMap.put(MAX_INACTIVE_INTERVAL_ATTR, maxInactiveInterval);
        newMap.put(IS_VALID_ATTR, isValid);
        newMap.put(IS_NEW_ATTR, isNew);
        if (broadcastSessionEvents) {
            newMap.put(IS_EXPIRATION_LOCKED, isExpirationLocked);
        }
        
        if (attrs != null) {
            for (Entry<String, Object> entry : attrs.entrySet()) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        
        map.putAll(newMap);
        map.fastRemove(removedAttributes.toArray(new String[removedAttributes.size()]));
        
        if (readMode == ReadMode.MEMORY) {
            topic.publish(createPutAllMessage(newMap));
            
            if (updateMode == UpdateMode.AFTER_REQUEST) {
                if (!removedAttributes.isEmpty()) {
                    topic.publish(new AttributeRemoveMessage(redissonManager.getNodeId(), getId(), removedAttributes));
                }
            }
        }
        removedAttributes.clear();
        
        expireSession();
    }
    
    public void load(Map<String, Object> attrs) {
        Long creationTime = (Long) attrs.remove(CREATION_TIME_ATTR);
        if (creationTime != null) {
            this.creationTime = creationTime;
        }
        Long lastAccessedTime = (Long) attrs.remove(LAST_ACCESSED_TIME_ATTR);
        if (lastAccessedTime != null) {
            this.lastAccessedTime = lastAccessedTime;
        }
        Integer maxInactiveInterval = (Integer) attrs.remove(MAX_INACTIVE_INTERVAL_ATTR);
        if (maxInactiveInterval != null) {
            this.maxInactiveInterval = maxInactiveInterval;
        }
        Long thisAccessedTime = (Long) attrs.remove(THIS_ACCESSED_TIME_ATTR);
        if (thisAccessedTime != null) {
            this.thisAccessedTime = thisAccessedTime;
        }
        Boolean isValid = (Boolean) attrs.remove(IS_VALID_ATTR);
        if (isValid != null) {
            this.isValid = isValid;
        }
        Boolean isNew = (Boolean) attrs.remove(IS_NEW_ATTR);
        if (isNew != null) {
            this.isNew = isNew;
        }
        Boolean isExpirationLocked = (Boolean) attrs.remove(IS_EXPIRATION_LOCKED);
        if (isExpirationLocked != null) {
            this.isExpirationLocked = isExpirationLocked;
        }

        for (Entry<String, Object> entry : attrs.entrySet()) {
            super.setAttribute(entry.getKey(), entry.getValue(), false);
        }
    }
    
    @Override
    public void recycle() {
        super.recycle();
        map = null;
    }
    
}
