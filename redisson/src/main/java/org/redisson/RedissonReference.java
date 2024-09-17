/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson;

import org.redisson.api.RObject;
import org.redisson.api.RObjectReactive;
import org.redisson.api.RObjectRx;
import org.redisson.api.annotation.REntity;
import org.redisson.client.codec.Codec;
import org.redisson.liveobject.misc.ClassUtils;

import java.io.Serializable;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public class RedissonReference implements Serializable {

    private static final long serialVersionUID = -2378564460151709127L;
    
    private String type;
    private String keyName;
    private String codec;

    public RedissonReference() {
    }

    public RedissonReference(Class<?> type, String keyName) {
        this(type, keyName, null);
    }

    public RedissonReference(Class<?> type, String keyName, Codec codec) {
        if (!ClassUtils.isAnnotationPresent(type, REntity.class)
                && !RObject.class.isAssignableFrom(type)
                    && !RObjectReactive.class.isAssignableFrom(type)
                        && !RObjectRx.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Class reference has to be a type of either RObject/RLiveObject/RObjectReactive/RObjectRx");
        }
        if (RObjectReactive.class.isAssignableFrom(type)) {
            String t = type.getName().replaceFirst("Reactive", "");
            if (!isAvailable(t)) {
                throw new IllegalArgumentException("There is no compatible type for " + type);
            }
            this.type = t;
        } else if (RObjectRx.class.isAssignableFrom(type)) {
            String t = type.getName().replaceFirst("Rx", "");
            if (!isAvailable(t)) {
                throw new IllegalArgumentException("There is no compatible type for " + type);
            }
            this.type = t;
        } else {
            this.type = type.getName();
        }
        this.keyName = keyName;
        if (codec != null) {
            this.codec = codec.getClass().getName();
        }
    }

    /**
     * @return the type
     * @throws java.lang.ClassNotFoundException - if the class cannot be located
     */
    public Class<?> getType() throws ClassNotFoundException {
        return Class.forName(type);
    }

    public Class<?> getRxJavaType() throws ClassNotFoundException {
        String rxName = type + "Rx";
        if (isAvailable(rxName)) {
            return Class.forName(rxName); //live object is not supported in reactive client
        }
        throw new ClassNotFoundException("There is no RxJava compatible type for " + type);
    }

    /**
     * @return the type
     * @throws java.lang.ClassNotFoundException - if the class cannot be located
     */
    public Class<?> getReactiveType() throws ClassNotFoundException {
        String reactiveName = type + "Reactive";
        if (isAvailable(reactiveName)) {
            return Class.forName(reactiveName); //live object is not supported in reactive client
        }
        throw new ClassNotFoundException("There is no Reactive compatible type for " + type);
    }

    /**
     * @return type name in string
     */
    public String getTypeName() {
        return type;
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
    
    public String getCodec() {
        return codec;
    }

    /**
     * @return the codec
     * @throws java.lang.ClassNotFoundException - if the class cannot be located
     */
    public Class<? extends Codec> getCodecType() throws ClassNotFoundException {
        if (codec != null) {
            return (Class<? extends Codec>) Class.forName(codec);
        }
        return null;
    }

    private boolean isAvailable(String type) {
        try {
            Class.forName(type);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
