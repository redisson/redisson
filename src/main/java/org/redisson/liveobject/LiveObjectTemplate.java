package org.redisson.liveobject;

import org.redisson.core.RMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class LiveObjectTemplate implements RLiveObject {

    private Long liveObjectTTL;
    private Object liveObjectId;
    private RMap liveObjectLiveMap;
    private String liveObjectMapKey;

    /**
     * @return the liveObjectTTL
     */
    @Override
    public Long getLiveObjectTTL() {
        return liveObjectTTL;
    }

    /**
     * @param liveObjectTTL the liveObjectTTL to set
     */
    @Override
    public void setLiveObjectTTL(Long liveObjectTTL) {
        this.liveObjectTTL = liveObjectTTL;
    }

    /**
     * @return the liveObjectId
     */
    @Override
    public Object getLiveObjectId() {
        return liveObjectId;
    }

    /**
     * @param liveObjectId the liveObjectId to set
     */
    @Override
    public void setLiveObjectId(Object liveObjectId) {
        this.liveObjectId = liveObjectId;
    }

    /**
     * @return the liveObjectLiveMap
     */
    @Override
    public RMap getLiveObjectLiveMap() {
        return liveObjectLiveMap;
    }

    /**
     * @param liveObjectLiveMap the liveObjectLiveMap to set
     */
    @Override
    public void setLiveObjectLiveMap(RMap liveObjectLiveMap) {
        this.liveObjectLiveMap = liveObjectLiveMap;
    }

    /**
     * @return the liveObjectMapKey
     */
    @Override
    public String getLiveObjectMapKey() {
        return liveObjectMapKey;
    }

    /**
     * @param liveObjectMapKey the liveObjectMapKey to set
     */
    @Override
    public void setLiveObjectMapKey(String liveObjectMapKey) {
        this.liveObjectMapKey = liveObjectMapKey;
    }

    
}
