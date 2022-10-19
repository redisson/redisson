/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class State {

    private int batchIndex;

    private int level = -1;

    private Object value;

    public State() {
    }

    public <T> T getValue() {
        return (T) value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public int getLevel() {
        return level;
    }

    public void incLevel() {
        level++;
    }
    
    public void decLevel() {
        level--;
    }
    
    public void setBatchIndex(int index) {
        this.batchIndex = index;
    }
    public int getBatchIndex() {
        return batchIndex;
    }

    @Override
    public String toString() {
        return "State [batchIndex=" + batchIndex + ", level=" + level + "]";
    }

}
