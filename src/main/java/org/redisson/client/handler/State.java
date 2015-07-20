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
package org.redisson.client.handler;

import java.util.List;

public class State {

    private int index;
    private Object decoderState;

    private long size;
    private List<Object> respParts;

    public State() {
        super();
    }

    public void setSize(long size) {
        this.size = size;
    }
    public long getSize() {
        return size;
    }

    public void setRespParts(List<Object> respParts) {
        this.respParts = respParts;
    }
    public List<Object> getRespParts() {
        return respParts;
    }

    public void setIndex(int index) {
        this.index = index;
    }
    public int getIndex() {
        return index;
    }

    public <T> T getDecoderState() {
        return (T)decoderState;
    }
    public void setDecoderState(Object decoderState) {
        this.decoderState = decoderState;
    }

}
