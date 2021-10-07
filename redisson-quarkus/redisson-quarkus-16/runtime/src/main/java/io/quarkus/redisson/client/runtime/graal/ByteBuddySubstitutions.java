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

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.lang.reflect.AnnotatedElement;

@TargetClass(className = "net.bytebuddy.description.type.TypeDescription$Generic$AnnotationReader$ForTypeVariableBoundType$OfFormalTypeVariable")
final class OfFormalTypeVariableSubstitute {

    @Substitute
    public AnnotatedElement resolve() {
        return null;
    }

}

@TargetClass(className = "net.bytebuddy.description.type.TypeDescription$Generic$AnnotationReader$ForTypeVariableBoundType")
final class ForTypeVariableBoundTypeSubstitute {

    @Substitute
    protected AnnotatedElement resolve(AnnotatedElement annotatedElement) {
        return null;
    }

}
