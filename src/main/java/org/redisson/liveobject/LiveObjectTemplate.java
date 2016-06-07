package org.redisson.liveobject;

import org.redisson.core.RMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class LiveObjectTemplate implements RLiveObject {

    private RMap liveObjectLiveMap;
    
    /**
     * @return the liveObjectLiveMap
     */
    @Override
    public RMap getLiveObjectLiveMap() {
        return liveObjectLiveMap;
    }

}
