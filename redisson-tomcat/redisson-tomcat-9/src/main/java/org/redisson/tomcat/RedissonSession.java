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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.session.StandardSession;
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
    
    public static final Set<String> ATTRS = new HashSet<String>(Arrays.asList(IS_NEW_ATTR, IS_VALID_ATTR, 
            THIS_ACCESSED_TIME_ATTR, MAX_INACTIVE_INTERVAL_ATTR, LAST_ACCESSED_TIME_ATTR, CREATION_TIME_ATTR));
    
    private final RedissonSessionManager redissonManager;
    private final Map<String, Object> attrs;
    private RMap<String, Object> map;
    private final RTopic topic;
    private final RedissonSessionManager.ReadMode readMode;
    private final UpdateMode updateMode;
    
    public RedissonSession(RedissonSessionManager manager, RedissonSessionManager.ReadMode readMode, UpdateMode updateMode) {
        super(manager);
        this.redissonManager = manager;
        this.readMode = readMode;
        this.updateMode = updateMode;
        this.topic = redissonManager.getTopic();
        
        try {
            Field attr = StandardSession.class.getDeclaredField("attributes");
            attrs = (Map<String, Object>) attr.get(this);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final long serialVersionUID = -2518607181636076487L;

    private static ThreadLocal<Map<String, Object>> requestSessionAttributeCache = new ThreadLocal<Map<String, Object>>();

    private Object getRequestSessionAttributeCacheValue(String name) {
    	Map<String, Object> localMap = requestSessionAttributeCache.get();
    	if (localMap != null) {
    		return localMap.get(name);
    	}
    	return null;
    }
    
    private void setRequestSessionAttributeCacheValue(String name, Object value) {
    	if (name != null) {
	    	Map<String, Object> localMap = requestSessionAttributeCache.get();
	    	if (localMap != null) {
	    		localMap.remove(name);
	    	}
	    	if (value != null) {
	    		if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean) && !(value instanceof Character)) { // do not cache common immutable classes
	    			if (localMap == null) {
	    				localMap = new HashMap<String, Object>();
	    				requestSessionAttributeCache.set(localMap);
	    			}
	    			localMap.put(name, value);
	    		}
	    	}
    	}
    }

    public void saveRequestSessionAttributeCache() {
    	Map<String, Object> localMap = requestSessionAttributeCache.get();
    	if (localMap != null && !localMap.isEmpty()) { // check if we gave to the application mutable objects or the application set mutable objects
    		//only store the attributes that were provided and/or stored to the application as mutable to prevent serialization of potentially big objects that were not touched by the current http request.
    		if (updateMode != UpdateMode.AFTER_REQUEST) {//check if updateMode is not AFTER_REQUEST to not store attributes into Redis twice, as after request will update all attributes at the end of the request.
    			save(localMap);
    		}
    	}
    }

    // use static method here so that if the session gets invalidated and we can't get the id out of it in RequestSessionAttributeCacheValve we should still be able to properly clean the thread
    public static void clearRequestSessionAttributeCache() {
    	requestSessionAttributeCache.remove();
    }

    @Override
    public Object getAttribute(String name) {
    	Object localResult = getRequestSessionAttributeCacheValue(name);
    	if (localResult != null) {
    		return localResult;
    	}
    	Object result = null;
        if (readMode == ReadMode.REDIS) {
            result = map.get(name);
        } else {
        	result = super.getAttribute(name);
        }

        //try to add redis object (readMode="REDIS") that was read or regular object when we have readMode="MEMORY" into thread cache so 
        //that at the end of the request we are going to update redis with those mutable objects, because web application could have alter those objects once received during request processing.
    	setRequestSessionAttributeCacheValue(name, result);
        return result;
    }
    
    public void delete() {
        map.delete();
		clearRequestSessionAttributeCache();
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
            return new AttributesPutAllMessage(redissonManager.getNodeId(), getId(), map);
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
                topic.publish(new AttributeUpdateMessage(redissonManager.getNodeId(), getId(), name, value));
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
        
    	setRequestSessionAttributeCacheValue(name, value);
		
        if (updateMode == UpdateMode.DEFAULT && map != null && value != null) {
            fastPut(name, value);
        }
    }
    
    public void superRemoveAttributeInternal(String name, boolean notify) {
        super.removeAttributeInternal(name, notify);
    }
    
    @Override
    protected void removeAttributeInternal(String name, boolean notify) {
        super.removeAttributeInternal(name, notify);

    	setRequestSessionAttributeCacheValue(name, null);
        
        if (updateMode == UpdateMode.DEFAULT && map != null) {
            map.fastRemove(name);
            if (readMode == ReadMode.MEMORY) {
                topic.publish(new AttributeRemoveMessage(redissonManager.getNodeId(), getId(), name));
            }
        }
    }

    public void save() {
    	save(attrs);
    }
    
    protected void save(Map<String, Object> attributes) {
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
        
        if (attributes != null) {
            for (Entry<String, Object> entry : attributes.entrySet()) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        
        map.putAll(newMap);
        if (readMode == ReadMode.MEMORY) {
            topic.publish(createPutAllMessage(newMap));
        }
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
