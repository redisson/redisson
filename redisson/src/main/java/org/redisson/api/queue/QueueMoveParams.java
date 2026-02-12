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
package org.redisson.api.queue;

import org.redisson.api.BaseSyncParams;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class QueueMoveParams extends BaseSyncParams<QueueMoveArgs> implements QueueMoveArgs, QueueMoveDestination {

    private String[] ids = new String[0];
    private int firstCount;
    private String destination;

    public QueueMoveParams(String[] ids) {
        this.ids = ids;
    }

    public QueueMoveParams(int firstCount) {
        this.firstCount = firstCount;
    }

    @Override
    public QueueMoveArgs destination(String queueName) {
        this.destination = queueName;
        return this;
    }

    public String getDestination() {
        return destination;
    }

    public String[] getIds() {
        return ids;
    }

    public int getFirstCount() {
        return firstCount;
    }

}
