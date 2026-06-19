/*
 * This file is licensed under BSD-3-Clause, not Apache-2.0; see notice below.
 * Vendored in from https://github.com/EsotericSoftware/kryo/blob/90a1c0b0e6519098a33316cc48b3895759739261/src/com/esotericsoftware/kryo/serializers/DefaultSerializers.java#L859
 * as it is not yet part of a released version of Kryo5.
 *
 * Released under BSD-3-Clause license. Original license:
 *
 * Copyright (c) 2008-2025, Nathan Sweet
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */
// SPDX-License-Identifier: BSD-3-Clause

package org.redisson.codec;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Serializer for {@link ConcurrentHashMap.KeySetView}.
 *
 * @author Andreas Bergander
 */
class Kryo5KeySetViewSerializer extends Serializer<ConcurrentHashMap.KeySetView> {
    public void write(Kryo kryo, Output output, ConcurrentHashMap.KeySetView set) {
        kryo.writeClassAndObject(output, set.getMap());
        kryo.writeClassAndObject(output, set.getMappedValue());
    }

    public ConcurrentHashMap.KeySetView read(Kryo kryo, Input input, Class<? extends ConcurrentHashMap.KeySetView> type) {
        return createKeySetView((ConcurrentHashMap) kryo.readClassAndObject(input), kryo.readClassAndObject(input));
    }

    public ConcurrentHashMap.KeySetView copy(Kryo kryo, ConcurrentHashMap.KeySetView original) {
        return createKeySetView(kryo.copy(original.getMap()), kryo.copy(original.getMappedValue()));
    }

    private ConcurrentHashMap.KeySetView createKeySetView(ConcurrentHashMap map, Object mappedValue) {
        return map.keySet(mappedValue);
    }
}
