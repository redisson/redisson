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
package org.redisson.api;

import java.util.Comparator;
import java.util.List;
import java.util.Queue;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RPriorityQueue<V> extends Queue<V>, RObject {

    Comparator<? super V> comparator();
    
    List<V> readAll();
    
    V pollLastAndOfferFirstTo(String dequeName);
    
    /**
     * Sets new comparator only if current set is empty
     *
     * @param comparator for values
     * @return <code>true</code> if new comparator setted
     *         <code>false</code> otherwise
     */
    boolean trySetComparator(Comparator<? super V> comparator);

}
