/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.client.codec;

import org.redisson.client.protocol.Encoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class GeoEntryCodec extends StringCodec {

    private final ThreadLocal<Integer> pos = new ThreadLocal<Integer>() {
        protected Integer initialValue() {
            return 0;
        };
    };
    
    private final Codec delegate;

    public GeoEntryCodec(Codec delegate) {
        super();
        this.delegate = delegate;
    }

    @Override
    public Encoder getValueEncoder() {
        Integer p = pos.get() + 1;
        pos.set(p);
        if (p % 3 == 0) {
            return delegate.getValueEncoder();
        }
        return super.getValueEncoder();
    }

}
