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
package org.redisson.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that the method is a generic field setter for a Live Object.
 * <p>
 * The annotated method takes two arguments - the name of the field to write as a
 * {@link String} and the new value - and stores the value into that field. Unlike
 * the deprecated {@link RFieldAccessor} annotation, the method may have any name.
 * Example:
 * <pre>
 *       &#064;RSetter
 *       public &lt;T&gt; void set(String field, T value) {
 *       }
 * </pre>
 *
 * @see RGetter
 *
 * @author Nikita Koksharov
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RSetter {
}
