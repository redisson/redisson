package org.redisson;

import org.redisson.client.codec.Codec;
import org.redisson.core.RObject;
import org.redisson.liveobject.annotation.REntity;

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

    public RedissonReference(Class type, String keyName) {
        this(type, keyName, null);
    }

    public RedissonReference(Class type, String keyName, Codec codec) {
        if (!type.isAnnotationPresent(REntity.class) && !RObject.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Class reference has to be a type of either RObject or RLiveObject");
        }
        this.type = type.getName();
        this.keyName = keyName;
        this.codec = codec != null ? codec.getClass().getCanonicalName() : null;
    }

    public boolean isDefaultCodec() {
        return codec == null;
    }

    /**
     * @return the type
     * @throws java.lang.Exception - which could be:
     *     LinkageError - if the linkage fails
     *     ExceptionInInitializerError - if the initialization provoked by this method fails
     *     ClassNotFoundException - if the class cannot be located
     */
    public Class getType() throws Exception {
        return Class.forName(type);
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
    public void setType(Class type) {
        if (!type.isAnnotationPresent(REntity.class) && !RObject.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Class reference has to be a type of either RObject or RLiveObject");
        }
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
     * @throws java.lang.Exception - which could be:
     *     LinkageError - if the linkage fails
     *     ExceptionInInitializerError - if the initialization provoked by this method fails
     *     ClassNotFoundException - if the class cannot be located 
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
