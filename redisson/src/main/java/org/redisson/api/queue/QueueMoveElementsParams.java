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

/**
 *
 * @author Nikita Koksharov
 *
 */
public class QueueMoveElementsParams implements QueueMoveElementsArgs,
                                                QueueMoveElementsAmount,
                                                QueueMoveElementsOrder {

    public enum Selector {COUNT, EXACTLY};

    public enum Ordering {OBO, BULK};

    private final String destName;
    private Selector selector;
    private int count;
    private Ordering ordering = Ordering.BULK;

    QueueMoveElementsParams(String destName) {
        this.destName = destName;
    }

    @Override
    public QueueMoveElementsOrder count(int value) {
        selector = Selector.COUNT;
        count = value;
        return this;
    }

    @Override
    public QueueMoveElementsOrder exactly(int value) {
        selector = Selector.EXACTLY;
        count = value;
        return this;
    }

    @Override
    public QueueMoveElementsArgs bulk() {
        ordering = Ordering.BULK;
        return this;
    }

    @Override
    public QueueMoveElementsArgs oneByOne() {
        ordering = Ordering.OBO;
        return this;
    }

    public String getDestName() {
        return destName;
    }

    public Selector getSelector() {
        return selector;
    }

    public int getCount() {
        return count;
    }

    public Ordering getOrdering() {
        return ordering;
    }
}
