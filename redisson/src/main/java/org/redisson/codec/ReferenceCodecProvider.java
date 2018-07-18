/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.codec;

import org.redisson.client.codec.Codec;
import org.redisson.api.RObject;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RObjectField;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public interface ReferenceCodecProvider {

    /**
     * Get codec instance by its class.
     * 
     * @param <T> the expected codec type.
     * @param codecClass the codec class used to lookup the codec.
     * @return the cached codec instance.
     */
    <T extends Codec> T getCodec(Class<T> codecClass);
    
    /**
     * Get a codec instance by a REntity annotation and the class annotated with
     * it.
     * 
     * @param <T> the expected codec type.
     * @param anno REntity annotation used on the class.
     * @param cls The class that has the REntity annotation.
     * @return the cached codec instance.
     */
    <T extends Codec> T getCodec(REntity anno, Class<?> cls);
    
    /**
     * Get a codec instance by a RObjectField annotation and the class annotated
     * with REntity, the implementation class of RObject the field is going to
     * be transformed into and the name of the field with this RObjectField 
     * annotation.
     * 
     * @param <T> the expected codec type.
     * @param <K> the type of the RObject.
     * @param anno RObjectField annotation used on the field.
     * @param cls The class that has the REntity annotation.
     * @param rObjectClass the implementation class of RObject the field is going
     * to be transformed into.
     * @param fieldName the name of the field with this RObjectField annotation.
     * @return the cached codec instance.
     */
    <T extends Codec, K extends RObject> T getCodec(RObjectField anno, Class<?> cls, Class<K> rObjectClass, String fieldName);

    /**
     * Get a codec instance by its class, the implementation class of the RObject
     * interface.
     * 
     * @param <T> the expected codec type.
     * @param <K> the RObject type.
     * @param codecClass the codec class used to lookup the codec.
     * @param rObjectClass the class of the RObject implementation.
     * @return the cached codec instance.
     */
    <T extends Codec, K extends RObject> T getCodec(Class<T> codecClass, Class<K> rObjectClass);

    /**
     * Get a codec instance by its class, the implementation class of the RObject
     * interface and the name of RObject (the value returned by RObject.getName()
     * method).
     * 
     * @param <T> the expected codec type.
     * @param <K> the RObject type.
     * @param codecClass the codec class used to lookup the codec.
     * @param rObjectClass the class of the RObject implementation.
     * @param name the name of RObject.
     * @return the cached codec instance.
     */
    <T extends Codec, K extends RObject> T getCodec(Class<T> codecClass, Class<K> rObjectClass, String name);

    /**
     * Get a codec instance by its class and an instance of the RObject.
     * 
     * @param <T> the expected codec type.
     * @param codecClass the codec class used to lookup the codec.
     * @param rObject instance of the RObject implementation.
     * @return the cached codec instance.
     */
    <T extends Codec> T getCodec(Class<T> codecClass, RObject rObject);

    /**
     * Register a codec by its class or super class.
     * 
     * @param <T> the codec type to register.
     * @param codecClass the codec Class to register it can be a super class of 
     * the instance.
     * @param codec the codec instance.
     */
    <T extends Codec> void registerCodec(Class<T> codecClass, T codec);
    
    /**
     * Register a codec by the REntity annotation and the class annotated with
     * it.
     * 
     * @param <T> the codec type to register.
     * @param anno REntity annotation used on the class.
     * @param cls The class that has the REntity annotation.
     * @param codec the codec instance.
     */
    <T extends Codec> void registerCodec(REntity anno, Class<?> cls, T codec);
    
    /**
     * Register a codec by the RObjectField annotation, the class annotated with
     * REntity, the implementation class of RObject the field is going to
     * be transformed into and the name of the field with this RObjectField 
     * annotation.
     * 
     * @param <T> the codec type to register.
     * @param <K> the type of the RObject.
     * @param anno RObjectField annotation used on the field.
     * @param cls The class that has the REntity annotation.
     * @param rObjectClass the implementation class of RObject the field is going
     * to be transformed into.
     * @param fieldName the name of the field with this RObjectField annotation.
     * @param codec the codec instance.
     */
    <T extends Codec, K extends RObject> void registerCodec(RObjectField anno, Class<?> cls, Class<K> rObjectClass, String fieldName, T codec);

    /**
     * Register a codec by its class or super class and the class of the RObject 
     * implementation.
     * 
     * @param <T> the codec type to register.
     * @param <K> the RObjectField type.
     * @param codecClass the codec Class to register it can be a super class of 
     * the instance.
     * @param rObjectClass the class of the RObject implementation.
     * @param codec the codec instance.
     */
    <T extends Codec, K extends RObject> void registerCodec(Class<T> codecClass, Class<K> rObjectClass, T codec);
    
    /**
     * Register a codec by its class or super class, the class of the RObject
     * implementation and the name of RObject (the value returned by 
     * RObjectField.getName() method).
     * 
     * @param <T> the codec type to register.
     * @param <K> the RObjectField type.
     * @param codecClass the codec Class to register it can be a super class of 
     * the instance.
     * @param rObjectClass the class of the RObject implementation.
     * @param name the name of RObject.
     * @param codec the codec instance.
     */
    <T extends Codec, K extends RObject> void registerCodec(Class<T> codecClass, Class<K> rObjectClass, String name, T codec);

    /**
     * Register a codec by its class or super class and an instance of the 
     * RObject.
     * 
     * @param <T> the codec type to register.
     * @param codecClass the codec Class to register it can be a super class of 
     * the instance.
     * @param rObject instance of the RObject implementation.
     * @param codec the codec instance.
     */
    <T extends Codec> void registerCodec(Class<T> codecClass, RObject rObject, T codec);
}
