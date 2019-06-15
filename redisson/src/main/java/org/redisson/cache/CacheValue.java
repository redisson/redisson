package org.redisson.cache;

import java.io.Serializable;


/**
 * 
 * @author Nikita Koksharov
 *
 */
@SuppressWarnings("EqualsHashCode")
public class CacheValue implements Serializable {
    
    private final Object key;
    private final Object value;
    
    public CacheValue(Object key, Object value) {
        super();
        this.key = key;
        this.value = value;
    }
    
    public Object getKey() {
        return key;
    }
    
    public Object getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CacheValue other = (CacheValue) obj;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "CacheValue [key=" + key + ", value=" + value + "]";
    }
    
}
