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
