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
package org.redisson.api.condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.redisson.liveobject.condition.ANDCondition;
import org.redisson.liveobject.condition.EQCondition;
import org.redisson.liveobject.condition.ORCondition;

/**
 * Conditions factory to search for Live Objects by fields.
 * 
 * @author Nikita Koksharov
 *
 */
public final class Conditions {

    /**
     * Returns "IN" condition for property by <code>name</code> and allowed set of <code>values</code> 
     * 
     * @param name - name of property
     * @param values - array of allowed values 
     * @return condition
     */
    public static Condition in(String name, Object... values) {
        List<Condition> conditions = new ArrayList<Condition>();
        for (Object value : values) {
            conditions.add(eq(name, value));
        }
        return or(conditions.toArray(new Condition[conditions.size()]));
    }

    /**
     * Returns "IN" condition for property by <code>name</code> and allowed set of <code>values</code>
     * 
     * @param name - name of property
     * @param values - collection of allowed values 
     * @return condition
     */
    public static Condition in(String name, Collection<?> values) {
        List<Condition> conditions = new ArrayList<Condition>();
        for (Object value : values) {
            conditions.add(eq(name, value));
        }
        return or(conditions.toArray(new Condition[conditions.size()]));
    }
    
    /**
     * Returns "EQUALS" condition which restricts property by <code>name</code> to defined <code>value</code>
     * 
     * @param name - name of property
     * @param value - defined value
     * @return condition
     */
    public static Condition eq(String name, Object value) {
        return new EQCondition(name, value);
    }
    
    /**
     * Returns "OR" condition for collection of nested <code>conditions</code>
     * 
     * @param conditions - nested condition objects
     * @return condition
     */
    public static Condition or(Condition... conditions) {
        return new ORCondition(conditions);
    }

    /**
     * Returns "AND" condition for collection of nested <code>conditions</code>
     * 
     * @param conditions - nested condition objects
     * @return condition
     */
    public static Condition and(Condition... conditions) {
        return new ANDCondition(conditions);
    }

    private Conditions() {
    }
    
}
