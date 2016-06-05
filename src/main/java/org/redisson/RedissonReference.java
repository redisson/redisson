package org.redisson;

import org.redisson.client.codec.Codec;
import org.redisson.core.RObject;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonReference {

    private String type;
    private String keyName;
    private String codec;

    public RedissonReference() {
    }

    public RedissonReference(Class<? extends RObject> type, String keyName) {
        this.type = type.getCanonicalName();
        this.keyName = keyName;
        this.codec = null;
    }

    public RedissonReference(Class<? extends RObject> type, String keyName, Codec codec) {
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
    public Class<? extends RObject> getType() throws Exception {
        return (Class<? extends RObject>) Class.forName(type);
    }

    /**
     * @return type name in string
     */
    public String getTypeName() {
        return type;
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
    public String getKeyName() {
        return keyName;
    }

    /**
     * @param keyName the keyName to set
     */
    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    /**
     * @return the codec
     */
    public Class<? extends Codec> getCodecType() throws Exception {
        return (Class<? extends Codec>) (codec == null
                ? null
                : Class.forName(codec));
    }

    /**
     * @return Codec name in string
     */
    public String getCodecName() {
        return codec;
    }

    /**
     * @param codec the codec to set
     */
    public void setCodecType(Class<? extends Codec> codec) {
        this.codec = codec.getCanonicalName();
    }

}
