/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class State {

    private int batchIndex;
    private DecoderState decoderState;

    private int level = -1;
    private List<StateLevel> levels;

    public State() {
    }

    public int getLevel() {
        return level;
    }

    public StateLevel getLastLevel() {
        if (levels == null || levels.isEmpty()) {
            return null;
        }
        return levels.get(level);
    }
    
    public void incLevel() {
        level++;
    }
    
    public void decLevel() {
        level--;
    }
    
    public void addLevel(StateLevel stateLevel) {
        if (levels == null) {
            levels = new ArrayList<StateLevel>(2);
        }
        levels.add(stateLevel);
        level++;
    }
    public void removeLastLevel() {
        levels.remove(level);
        level--;
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
