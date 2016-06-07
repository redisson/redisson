package org.redisson.liveobject;

import org.redisson.core.RMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public interface RLiveObject {
    
    /**
     * @return the liveObjectLiveMap
     */
    public RMap getLiveObjectLiveMap();
}
