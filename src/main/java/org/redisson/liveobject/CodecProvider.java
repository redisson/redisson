package org.redisson.liveobject;

import org.redisson.client.codec.Codec;
import org.redisson.core.RObject;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public interface CodecProvider {

    Codec getCodec(Class<? extends Codec> codecClass);

    Codec getCodec(Class<? extends Codec> codecClass, Class<? extends RObject> rObjectClass, String name);

    Codec getCodec(Class<? extends Codec> codecClass, RObject rObject, String name);

    void registerCodec(Class<? extends Codec> codecClass, Codec codec);

    void registerCodec(Class<? extends Codec> codecClass, Class<? extends RObject> rObjectClass, String name, Codec codec);

    void registerCodec(Class<? extends Codec> codecClass, RObject rObject, String name, Codec codec);
}
