/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
