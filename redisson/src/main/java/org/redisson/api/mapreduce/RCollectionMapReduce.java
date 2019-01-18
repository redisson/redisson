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
package org.redisson.api.mapreduce;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RList;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RSetCache;
import org.redisson.api.RSortedSet;

/**
 * 
 * MapReduce allows to process large amount of data stored in 
 * {@link RSet}, {@link RList}, {@link RSetCache}, {@link RScoredSortedSet}, {@link RSortedSet} and others  
 * using Mapper, Reducer and/or Collator tasks launched across Redisson Nodes. 
 * <p>
 * Usage example:
 * 
 * <pre>
 *   public class WordMapper implements RCollectionMapper&lt;String, String, Integer&gt; {
 *
 *       public void map(String value, RCollector&lt;String, Integer&gt; collector) {
 *           String[] words = value.split(&quot;[^a-zA-Z]&quot;);
 *           for (String word : words) {
 *               collector.emit(word, 1);
 *           }
 *       }
 *       
 *   }
 *   
 *   public class WordReducer implements RReducer&lt;String, Integer&gt; {
 *
 *       public Integer reduce(String reducedKey, Iterator&lt;Integer&gt; iter) {
 *           int sum = 0;
 *           while (iter.hasNext()) {
 *              Integer i = (Integer) iter.next();
 *              sum += i;
 *           }
 *           return sum;
 *       }
 *       
 *   }
 *
 *   public class WordCollator implements RCollator&lt;String, Integer, Integer&gt; {
 *
 *       public Integer collate(Map&lt;String, Integer&gt; resultMap) {
 *           int result = 0;
 *           for (Integer count : resultMap.values()) {
 *               result += count;
 *           }
 *           return result;
 *       }
 *       
 *   }
 *   
 *   RList&lt;String&gt; list = redisson.getList(&quot;myWords&quot;);
 *   
 *   Map&lt;String, Integer&gt; wordsCount = list.&lt;String, Integer&gt;mapReduce()
 *     .mapper(new WordMapper())
 *     .reducer(new WordReducer())
 *     .execute();
 *
 *   Integer totalCount = list.&lt;String, Integer&gt;mapReduce()
 *     .mapper(new WordMapper())
 *     .reducer(new WordReducer())
 *     .execute(new WordCollator());
 *
 * </pre>
 * 
 * @author Nikita Koksharov
 *
 * @param <VIn> input value
 * @param <KOut> output key
 * @param <VOut> output value
 */
public interface RCollectionMapReduce<VIn, KOut, VOut> extends RMapReduceExecutor<VIn, KOut, VOut> {

    /**
     * Defines timeout for MapReduce process
     * 
     * @param timeout for process
     * @param unit of timeout
     * @return self instance
     */
    RCollectionMapReduce<VIn, KOut, VOut> timeout(long timeout, TimeUnit unit);
    
    /**
     * Setup Mapper object
     * 
     * @param mapper used during MapReduce
     * @return self instance
     */
    RCollectionMapReduce<VIn, KOut, VOut> mapper(RCollectionMapper<VIn, KOut, VOut> mapper);
    
    /**
     * Setup Reducer object
     * 
     * @param reducer used during MapReduce
     * @return self instance
     */
    RCollectionMapReduce<VIn, KOut, VOut> reducer(RReducer<KOut, VOut> reducer);
    
}
