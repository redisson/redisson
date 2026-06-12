/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.client.protocol.Decoder;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class TDigestDoubleCodec extends StringCodec {

    public static final TDigestDoubleCodec INSTANCE = new TDigestDoubleCodec();

    private final Decoder<Object> decoder = (buf, state) -> {
        String str = (String) TDigestDoubleCodec.super.getValueDecoder().decode(buf, state);
        return parse(str);
    };

    /**
     * Parses a t-digest floating-point reply, mapping the special
     * {@code nan}, {@code inf} and {@code -inf} tokens.
     *
     * @param str raw reply value
     * @return parsed double, or {@code null} if the value is absent
     */
    public static Double parse(String str) {
        if (str == null) {
            return null;
        }
        switch (str) {
            case "nan":
            case "NaN":
                return Double.NaN;
            case "inf":
            case "+inf":
                return Double.POSITIVE_INFINITY;
            case "-inf":
                return Double.NEGATIVE_INFINITY;
            default:
                return Double.valueOf(str);
        }
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

}
