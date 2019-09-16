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
package org.redisson.liveobject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;

import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RSetMultimap;
import org.redisson.api.RedissonClient;
import org.redisson.api.condition.Condition;
import org.redisson.liveobject.condition.ANDCondition;
import org.redisson.liveobject.condition.EQCondition;
import org.redisson.liveobject.condition.GECondition;
import org.redisson.liveobject.condition.GTCondition;
import org.redisson.liveobject.condition.LECondition;
import org.redisson.liveobject.condition.LTCondition;
import org.redisson.liveobject.condition.ORCondition;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.liveobject.resolver.NamingScheme;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LiveObjectSearch {
    
    private final RedissonClient redisson;
    private final RedissonObjectBuilder objectBuilder;

    public LiveObjectSearch(RedissonClient redisson, RedissonObjectBuilder objectBuilder) {
        super();
        this.redisson = redisson;
        this.objectBuilder = objectBuilder;
    }

    private Set<Object> traverseAnd(ANDCondition condition, NamingScheme namingScheme, Class<?> entityClass) {
        Set<Object> allIds = new HashSet<Object>();
        
        RSet<Object> firstEqSet = null;
        
        List<String> eqNames = new ArrayList<String>();
        
        Map<RScoredSortedSet<Object>, Number> gtNumericNames = new HashMap<>();
        Map<RScoredSortedSet<Object>, Number> geNumericNames = new HashMap<>();
        Map<RScoredSortedSet<Object>, Number> ltNumericNames = new HashMap<>();
        Map<RScoredSortedSet<Object>, Number> leNumericNames = new HashMap<>();
        Map<RScoredSortedSet<Object>, Number> eqNumericNames = new HashMap<>();
        
        for (Condition cond : condition.getConditions()) {
            if (cond instanceof EQCondition) {
                EQCondition eqc = (EQCondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, eqc.getName());
                if (eqc.getValue() instanceof Number) {
                    RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                    eqNumericNames.put(values, (Number) eqc.getValue());
                } else {
                    RSetMultimap<Object, Object> map = redisson.getSetMultimap(indexName, namingScheme.getCodec());
                    RSet<Object> values = map.get(eqc.getValue());
                    if (firstEqSet == null) {
                        firstEqSet = values;
                    } else {
                        eqNames.add(values.getName());
                    }
                }
            }
            if (cond instanceof LTCondition) {
                LTCondition ltc = (LTCondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, ltc.getName());
                RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                ltNumericNames.put(values, ltc.getValue());
            }
            if (cond instanceof LECondition) {
                LECondition lec = (LECondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, lec.getName());
                RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                leNumericNames.put(values, lec.getValue());
            }
            if (cond instanceof GECondition) {
                GECondition gec = (GECondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, gec.getName());
                RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                geNumericNames.put(values, gec.getValue());
            }
            if (cond instanceof GTCondition) {
                GTCondition gtc = (GTCondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, gtc.getName());
                RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                gtNumericNames.put(values, gtc.getValue());
            }
            
            if (cond instanceof ORCondition) {
                Collection<Object> ids = traverseOr((ORCondition) cond, namingScheme, entityClass);
                if (ids.isEmpty()) {
                    return Collections.emptySet();
                }
                allIds.addAll(ids);
            }
        }
        
        if (firstEqSet != null) {
            if (eqNames.isEmpty()) {
                if (!allIds.isEmpty()) {
                    allIds.retainAll(firstEqSet.readAll());    
                } else {
                    allIds.addAll(firstEqSet.readAll());
                }
            } else {
                Set<Object> intersect = firstEqSet.readIntersection(eqNames.toArray(new String[eqNames.size()]));
                if (!allIds.isEmpty()) {
                    allIds.retainAll(intersect);    
                } else {
                    allIds.addAll(intersect);
                }
            }
        }

        if (!checkValueRange(allIds, eqNumericNames, (r, v) -> {
            return r.valueRange(v.doubleValue(), true, v.doubleValue(), true);
        })) {
            return Collections.emptySet();
        }

        if (!checkValueRange(allIds, gtNumericNames, (r, v) -> {
            return r.valueRange(v.doubleValue(), false, Double.POSITIVE_INFINITY, false);
        })) {
            return Collections.emptySet();
        }

        if (!checkValueRange(allIds, geNumericNames, (r, v) -> {
            return r.valueRange(v.doubleValue(), true, Double.POSITIVE_INFINITY, false);
        })) {
            return Collections.emptySet();
        }
        
        if (!checkValueRange(allIds, ltNumericNames, (r, v) -> {
            return r.valueRange(Double.NEGATIVE_INFINITY, false, v.doubleValue(), false);
        })) {
            return Collections.emptySet();
        }

        if (!checkValueRange(allIds, leNumericNames, (r, v) -> {
            return r.valueRange(Double.NEGATIVE_INFINITY, false, v.doubleValue(), true);
        })) {
            return Collections.emptySet();
        }
        
        return allIds;
    }

    private boolean checkValueRange(Set<Object> allIds, Map<RScoredSortedSet<Object>, Number> numericNames, 
                        BiFunction<RScoredSortedSet<Object>, Number, Collection<Object>> func) {
        if (!numericNames.isEmpty()) {
            Set<Object> gtAllIds = new HashSet<>();
            boolean firstFill = false;
            for (Entry<RScoredSortedSet<Object>, Number> e : numericNames.entrySet()) {
                Collection<Object> gtIds = func.apply(e.getKey(), e.getValue());
                if (gtIds.isEmpty()) {
                    return false;
                }
                if (!firstFill) {
                    gtAllIds.addAll(gtIds);
                    firstFill = true;
                } else {
                    gtAllIds.retainAll(gtIds);
                }
            }
            if (!allIds.isEmpty()) {
                allIds.retainAll(gtAllIds);    
            } else {
                allIds.addAll(gtAllIds);
            }
        }
        return true;
    }

    private Set<Object> traverseOr(ORCondition condition, NamingScheme namingScheme, Class<?> entityClass) {
        Set<Object> allIds = new HashSet<Object>();
        
        RSet<Object> firstEqSet = null;
        List<String> eqNames = new ArrayList<String>();

        Map<RScoredSortedSet<Object>, Number> ltNumericNames = new HashMap<>();
        Map<RScoredSortedSet<Object>, Number> leNumericNames = new HashMap<>();
        Map<RScoredSortedSet<Object>, Number> gtNumericNames = new HashMap<>();
        Map<RScoredSortedSet<Object>, Number> geNumericNames = new HashMap<>();
        Map<RScoredSortedSet<Object>, Number> eqNumericNames = new HashMap<>();
        
        for (Condition cond : condition.getConditions()) {
            if (cond instanceof EQCondition) {
                EQCondition eqc = (EQCondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, eqc.getName());
                if (eqc.getValue() instanceof Number) {
                    RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                    eqNumericNames.put(values, (Number) eqc.getValue());
                } else {
                    RSetMultimap<Object, Object> map = redisson.getSetMultimap(indexName, namingScheme.getCodec());
                    RSet<Object> values = map.get(eqc.getValue());
                    if (firstEqSet == null) {
                        firstEqSet = values;
                    } else {
                        eqNames.add(values.getName());
                    }
                }
            }
            if (cond instanceof GTCondition) {
                GTCondition gtc = (GTCondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, gtc.getName());
                RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                gtNumericNames.put(values, gtc.getValue());
            }
            if (cond instanceof GECondition) {
                GECondition gec = (GECondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, gec.getName());
                RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                geNumericNames.put(values, gec.getValue());
            }
            if (cond instanceof LTCondition) {
                LTCondition ltc = (LTCondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, ltc.getName());
                RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                ltNumericNames.put(values, ltc.getValue());
            }
            if (cond instanceof LECondition) {
                LECondition lec = (LECondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, lec.getName());
                RScoredSortedSet<Object> values = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                leNumericNames.put(values, lec.getValue());
            }
            if (cond instanceof ANDCondition) {
                Collection<Object> ids = traverseAnd((ANDCondition) cond, namingScheme, entityClass);
                allIds.addAll(ids);
            }
        }
        if (firstEqSet != null) {
            if (eqNames.isEmpty()) {
                allIds.addAll(firstEqSet.readAll());
            } else {
                allIds.addAll(firstEqSet.readUnion(eqNames.toArray(new String[eqNames.size()])));
            }
        }
        
        for (Entry<RScoredSortedSet<Object>, Number> e : eqNumericNames.entrySet()) {
            Collection<Object> ids = e.getKey().valueRange(e.getValue().doubleValue(), true, e.getValue().doubleValue(), true);
            allIds.addAll(ids);
        }
        
        for (Entry<RScoredSortedSet<Object>, Number> e : gtNumericNames.entrySet()) {
            Collection<Object> ids = e.getKey().valueRange(e.getValue().doubleValue(), false, Double.POSITIVE_INFINITY, false);
            allIds.addAll(ids);
        }

        for (Entry<RScoredSortedSet<Object>, Number> e : geNumericNames.entrySet()) {
            Collection<Object> ids = e.getKey().valueRange(e.getValue().doubleValue(), true, Double.POSITIVE_INFINITY, false);
            allIds.addAll(ids);
        }
        
        for (Entry<RScoredSortedSet<Object>, Number> e : ltNumericNames.entrySet()) {
            Collection<Object> ids = e.getKey().valueRange(Double.NEGATIVE_INFINITY, false, e.getValue().doubleValue(), false);
            allIds.addAll(ids);
        }

        for (Entry<RScoredSortedSet<Object>, Number> e : leNumericNames.entrySet()) {
            Collection<Object> ids = e.getKey().valueRange(Double.NEGATIVE_INFINITY, false, e.getValue().doubleValue(), true);
            allIds.addAll(ids);
        }

        return allIds;
    }
    
    public Set<Object> find(Class<?> entityClass, Condition condition) {
        NamingScheme namingScheme = objectBuilder.getNamingScheme(entityClass);

        Set<Object> ids = Collections.emptySet();
        if (condition instanceof EQCondition) {
            EQCondition c = (EQCondition) condition;
            String indexName = namingScheme.getIndexName(entityClass, c.getName());
            
            if (c.getValue() instanceof Number) {
                RScoredSortedSet<Object> set = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
                double v = ((Number) c.getValue()).doubleValue();
                Collection<Object> gtIds = set.valueRange(v, true, v, true);
                ids = new HashSet<>(gtIds);
            } else {
                RSetMultimap<Object, Object> map = redisson.getSetMultimap(indexName, namingScheme.getCodec());
                ids = map.getAll(c.getValue());
            }
        } else if (condition instanceof GTCondition) {
            GTCondition c = (GTCondition) condition;
            String indexName = namingScheme.getIndexName(entityClass, c.getName());
            RScoredSortedSet<Object> set = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
            Collection<Object> gtIds = set.valueRange(c.getValue().doubleValue(), false, Double.POSITIVE_INFINITY, false);
            ids = new HashSet<>(gtIds);
        } else if (condition instanceof GECondition) {
            GECondition c = (GECondition) condition;
            String indexName = namingScheme.getIndexName(entityClass, c.getName());
            RScoredSortedSet<Object> set = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
            Collection<Object> gtIds = set.valueRange(c.getValue().doubleValue(), true, Double.POSITIVE_INFINITY, false);
            ids = new HashSet<>(gtIds);
        } else if (condition instanceof LTCondition) {
            LTCondition c = (LTCondition) condition;
            String indexName = namingScheme.getIndexName(entityClass, c.getName());
            RScoredSortedSet<Object> set = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
            Collection<Object> gtIds = set.valueRange(Double.NEGATIVE_INFINITY, false, c.getValue().doubleValue(), false);
            ids = new HashSet<>(gtIds);
        } else if (condition instanceof LECondition) {
            LECondition c = (LECondition) condition;
            String indexName = namingScheme.getIndexName(entityClass, c.getName());
            RScoredSortedSet<Object> set = redisson.getScoredSortedSet(indexName, namingScheme.getCodec());
            Collection<Object> gtIds = set.valueRange(Double.NEGATIVE_INFINITY, false, c.getValue().doubleValue(), true);
            ids = new HashSet<>(gtIds);
        } else if (condition instanceof ORCondition) {
            ids = traverseOr((ORCondition) condition, namingScheme, entityClass);
        } else if (condition instanceof ANDCondition) {
            ids = traverseAnd((ANDCondition) condition, namingScheme, entityClass);
        }
        
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids;
    }

    
}
