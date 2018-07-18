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
package org.redisson.client.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.redisson.client.protocol.decoder.DecoderState;

public class State {

    private int batchIndex;
    private DecoderState decoderState;

    private int level = -1;
    private List<StateLevel> levels;
    private final boolean makeCheckpoint;

    public State(boolean makeCheckpoint) {
        this.makeCheckpoint = makeCheckpoint;
    }

    public boolean isMakeCheckpoint() {
        return makeCheckpoint;
    }

    public void resetLevel() {
        level = -1;
        levels.clear();
    }
    public int decLevel() {
        return --level;
    }
    public int incLevel() {
        return ++level;
    }
    
    public StateLevel getLastLevel() {
        if (levels == null || levels.isEmpty()) {
            return null;
        }
        return levels.get(level);
    }
    
    public void addLevel(StateLevel stateLevel) {
        if (levels == null) {
            levels = new ArrayList<StateLevel>(2);
        }
        levels.add(stateLevel);
    }
    public List<StateLevel> getLevels() {
        if (levels == null) {
            return Collections.emptyList();
        }
        return levels;
    }

    public void setBatchIndex(int index) {
        this.batchIndex = index;
    }
    public int getBatchIndex() {
        return batchIndex;
    }

    public <T extends DecoderState> T getDecoderState() {
        return (T) decoderState;
    }
    public void setDecoderState(DecoderState decoderState) {
        this.decoderState = decoderState;
    }

    @Override
    public String toString() {
        return "State [batchIndex=" + batchIndex + ", decoderState=" + decoderState + ", level=" + level + ", levels="
                + levels + "]";
    }

    
    
}
