/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.api.queue;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class DequeMoveParams {

    public enum Direction {LEFT, RIGHT};

    private Direction sourceDirection;
    private Direction destDirection;
    private String destName;

    public Direction getSourceDirection() {
        return sourceDirection;
    }

    public void setSourceDirection(Direction sourceDirection) {
        this.sourceDirection = sourceDirection;
    }

    public Direction getDestDirection() {
        return destDirection;
    }

    public void setDestDirection(Direction destDirection) {
        this.destDirection = destDirection;
    }

    public String getDestName() {
        return destName;
    }

    public void setDestName(String destName) {
        this.destName = destName;
    }
}
