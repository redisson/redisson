package org.redisson;

import org.redisson.client.codec.Codec;
import org.redisson.core.RObject;

/**
 *
 * @author ruigu
 */
public class RedissonReference {
    private String type;
    private Object keyName;
    private String codec;

    public RedissonReference() {
    }

    public RedissonReference(Class<? extends RObject> type, Object keyName) {
        this.type = type.getCanonicalName();
        this.keyName = keyName;
        this.codec = null;
    }

    public RedissonReference(Class<? extends RObject> type, Object keyName, Codec codec) {
        this.type = type.getCanonicalName();
        this.keyName = keyName;
        this.codec = codec.getClass().getCanonicalName();
    }

    public boolean isDefaultCodec() {
        return codec == null;
    }

    /**
     * @return the type
     */
    public Class<? extends RObject> getType() {
        try {
            return (Class<? extends RObject>) Class.forName(type);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    /**
     * @param type the type to set
     */
    public void setType(Class<? extends RObject> type) {
        this.type = type.getCanonicalName();
    }

    /**
     * @return the keyName
     */
    public Object getKeyName() {
        return keyName;
    }

    /**
     * @param keyName the keyName to set
     */
    public void setKeyName(Object keyName) {
        this.keyName = keyName;
    }

    /**
     * @return the codec
     */
    public Codec getCodec() throws Exception {
        return codec == null
                ? null
                : (Codec) Class.forName(codec).newInstance();
    }

    /**
     * @param codec the codec to set
     */
    public void setCodec(Codec codec) {
        this.codec = codec.getClass().getCanonicalName();
    }
    
    
}
