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
package io.quarkus.redisson.client.runtime.graal;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Without this substitution following warning will show:
 * Warning: RecomputeFieldValue.FieldOffset automatic substitution failed.
 * The automatic substitution registration was attempted because a call to jdk.internal.misc.Unsafe.objectFieldOffset(Field) was detected in the static initializer of org.jboss.marshalling.river.RiverUnmarshaller.
 * Add a RecomputeFieldValue.FieldOffset manual substitution for org.jboss.marshalling.river.RiverUnmarshaller.proxyInvocationHandlerOffset.
 * Detailed failure reason(s): The argument of Unsafe.objectFieldOffset(Field) is not a constant field.
 *
 * @author ineednousername https://github.com/ineednousername
 */
@TargetClass(className = "org.jboss.marshalling.river.RiverUnmarshaller")
@SuppressWarnings(value = /*is used during native image generation.*/ "unused")
public final class RiverUnmarshaller {
    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, declClassName = "org.jboss.marshalling.river.RiverUnmarshaller", name = "proxyInvocationHandlerOffset")
    private static long proxyInvocationHandlerOffset;
}
