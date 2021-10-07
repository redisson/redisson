/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.apache.catalina.session.StandardSession;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.tomcat.RedissonSessionManager.ReadMode;
import org.redisson.tomcat.RedissonSessionManager.UpdateMode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final String PRINCIPAL_ATTR = "session:principal";
    private static final String AUTHTYPE_ATTR = "session:authtype";
    
    public static final Set<String> ATTRS = new HashSet<String>(Arrays.asList(
            IS_NEW_ATTR, IS_VALID_ATTR, 
            THIS_ACCESSED_TIME_ATTR, MAX_INACTIVE_INTERVAL_ATTR, 
            LAST_ACCESSED_TIME_ATTR, CREATION_TIME_ATTR, IS_EXPIRATION_LOCKED,
            PRINCIPAL_ATTR, AUTHTYPE_ATTR
            ));
    
    private boolean isExpirationLocked;
    private boolean loaded;
    private final RedissonSessionManager redissonManager;
    private final Map<String, Object> attrs;
    private RMap<String, Object> map;
    private final RTopic topic;
    private final ReadMode readMode;
    private final UpdateMode updateMode;

    private final AtomicInteger usages = new AtomicInteger();
    private Map<String, Object> loadedAttributes = Collections.emptyMap();
    private Map<String, Object> updatedAttributes = Collections.emptyMap();
    private Set<String> removedAttributes = Collections.emptySet();

    private final boolean broadcastSessionEvents;
    private final boolean broadcastSessionUpdates;

    public RedissonSession(RedissonSessionManager manager, ReadMode readMode, UpdateMode updateMode, boolean broadcastSessionEvents, boolean broadcastSessionUpdates) {
        super(manager);
        this.redissonManager = manager;
        this.readMode = readMode;
        this.updateMode = updateMode;
        this.topic = redissonManager.getTopic();
        this.broadcastSessionEvents = broadcastSessionEvents;
        this.broadcastSessionUpdates = broadcastSessionUpdates;
        
        if (updateMode == UpdateMode.AFTER_REQUEST) {
            removedAttributes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        }
        if (readMode == ReadMode.REDIS) {
            loadedAttributes = new ConcurrentHashMap<>();
            updatedAttributes = new ConcurrentHashMap<>();
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

            if (removedAttributes.contains(name)) {
                return super.getAttribute(name);
            }

            Object value = loadedAttributes.get(name);
            if (value == null) {
                value = map.get(name);
                if (value != null) {
                    loadedAttributes.put(name, value);
                }
            }

            return value;
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

            Set<String> attributeKeys = new HashSet<>();
            attributeKeys.addAll(map.readAllKeySet());
            attributeKeys.addAll(loadedAttributes.keySet());
            return Collections.enumeration(attributeKeys);
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
        if (readMode == ReadMode.MEMORY && this.broadcastSessionUpdates) {
            topic.publish(new AttributesClearMessage(redissonManager.getNodeId(), getId()));
        }
        map = null;
        loadedAttributes.clear();
        updatedAttributes.clear();
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
            if (readMode == ReadMode.MEMORY && this.broadcastSessionUpdates) {
                topic.publish(createPutAllMessage(newMap));
            }
        }
    }
    
    @Override
    public void access() {
        super.access();
        
        if (map != null) {
            fastPut(THIS_ACCESSED_TIME_ATTR, thisAccessedTime);
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
        if (map == null) {
            return;
        }
        map.fastPut(name, value);
        if (readMode == ReadMode.MEMORY && this.broadcastSessionUpdates) {
            try {
                topic.publish(new AttributeUpdateMessage(redissonManager.getNodeId(), getId(), name, value, this.map.getCodec().getMapValueEncoder()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public void setPrincipal(Principal principal) {
        super.setPrincipal(principal);

        if (principal == null) {
            removeRedisAttribute(PRINCIPAL_ATTR);
        } else {
            fastPut(PRINCIPAL_ATTR, principal);
        }
    }

    @Override
    public void setAuthType(String authType) {
        super.setAuthType(authType);

        if (authType == null) {
            removeRedisAttribute(AUTHTYPE_ATTR);
        } else {
            fastPut(AUTHTYPE_ATTR, authType);
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

        if (map != null) {
            Map<String, Object> newMap = new HashMap<>(3);
            if (isNew != oldValue) {
                newMap.put(IS_NEW_ATTR, isNew);
            }
            newMap.put(LAST_ACCESSED_TIME_ATTR, lastAccessedTime);
            newMap.put(THIS_ACCESSED_TIME_ATTR, thisAccessedTime);
            map.putAll(newMap);
            if (readMode == ReadMode.MEMORY && this.broadcastSessionUpdates) {
                topic.publish(createPutAllMessage(newMap));
            }
            expireSession();
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
        if (readMode == ReadMode.REDIS) {
            loadedAttributes.put(name, value);
            updatedAttributes.put(name, value);
        }
        if (updateMode == UpdateMode.AFTER_REQUEST) {
            removedAttributes.remove(name);
        }
    }
    
    public void superRemoveAttributeInternal(String name, boolean notify) {
        super.removeAttributeInternal(name, notify);
    }

    @Override
    public long getIdleTimeInternal() {
        long idleTime = super.getIdleTimeInternal();
        if (map != null && readMode == ReadMode.REDIS) {
            if (idleTime >= getMaxInactiveInterval() * 1000) {
                load(map.getAll(RedissonSession.ATTRS));
                idleTime = super.getIdleTimeInternal();
            }
        }
        return idleTime;
    }

    @Override
    protected void removeAttributeInternal(String name, boolean notify) {
        super.removeAttributeInternal(name, notify);

        removeRedisAttribute(name);
    }

    private void removeRedisAttribute(String name) {
        if (updateMode == UpdateMode.DEFAULT && map != null) {
            map.fastRemove(name);
            if (readMode == ReadMode.MEMORY && this.broadcastSessionUpdates) {
                topic.publish(new AttributeRemoveMessage(redissonManager.getNodeId(), getId(), new HashSet<String>(Arrays.asList(name))));
            }
        }
        if (readMode == ReadMode.REDIS) {
            loadedAttributes.remove(name);
            updatedAttributes.remove(name);
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
        if (principal != null) {
            newMap.put(PRINCIPAL_ATTR, principal);
        }
        if (authType != null) {
            newMap.put(AUTHTYPE_ATTR, authType);
        }
        if (broadcastSessionEvents) {
            newMap.put(IS_EXPIRATION_LOCKED, isExpirationLocked);
        }

        if (readMode == ReadMode.MEMORY) {
            if (attrs != null) {
                for (Entry<String, Object> entry : attrs.entrySet()) {
                    newMap.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            newMap.putAll(updatedAttributes);
            updatedAttributes.clear();
        }

        map.putAll(newMap);
        map.fastRemove(removedAttributes.toArray(new String[0]));
        
        if (readMode == ReadMode.MEMORY && this.broadcastSessionUpdates) {
            topic.publish(createPutAllMessage(newMap));
            
            if (updateMode == UpdateMode.AFTER_REQUEST) {
                if (!removedAttributes.isEmpty()) {
                    topic.publish(new AttributeRemoveMessage(redissonManager.getNodeId(), getId(), new HashSet<>(removedAttributes)));
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
        Principal p = (Principal) attrs.remove(PRINCIPAL_ATTR);
        if (p != null) {
            this.principal = p;
        }
        String authType = (String) attrs.remove(AUTHTYPE_ATTR);
        if (authType != null) {
            this.authType = authType;
        }

        if (readMode == ReadMode.MEMORY) {
            for (Entry<String, Object> entry : attrs.entrySet()) {
                super.setAttribute(entry.getKey(), entry.getValue(), false);
            }
        }
    }
    
    @Override
    public void recycle() {
        super.recycle();
        map = null;
        loadedAttributes.clear();
        updatedAttributes.clear();
        removedAttributes.clear();
    }

    public void startUsage() {
        usages.incrementAndGet();
    }

    public void endUsage() {
        // don't decrement usages if startUsage wasn't called
//        if (usages.decrementAndGet() == 0) {
        if (usages.get() == 0 || usages.decrementAndGet() == 0) {
            loadedAttributes.clear();
        }
    }
}
