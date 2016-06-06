package org.redisson.liveobject;

import org.redisson.core.RMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public interface RLiveObject {
    
    /**
     * @return the liveObjectTTL
     */
    public Long getLiveObjectTTL();

    /**
     * @param liveObjectTTL the liveObjectTTL to set
     */
    public void setLiveObjectTTL(Long liveObjectTTL);

    /**
     * @return the LiveObjectId
     */
    public Object getLiveObjectId();

    /**
     * @param LiveObjectId the LiveObjectId to set
     */
    public void setLiveObjectId(Object LiveObjectId);

    /**
     * @return the liveObjectLiveMap
     */
    public RMap getLiveObjectLiveMap();

    /**
     * @param liveObjectLiveMap the liveObjectLiveMap to set
     */
    public void setLiveObjectLiveMap(RMap liveObjectLiveMap);

    /**
     * @return the liveObjectMapKey
     */
    public String getLiveObjectMapKey();

    /**
     * @param liveObjectMapKey the liveObjectMapKey to set
     */
    public void setLiveObjectMapKey(String liveObjectMapKey);
}
