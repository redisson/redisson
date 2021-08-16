package io.quarkus.redisson.client.runtime.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "org.redisson.codec.JsonJacksonCodec")
final class JsonJacksonCodecSubstitute {

    @Substitute
    private void warmup() {
    }

}

@TargetClass(className = "org.redisson.codec.MarshallingCodec")
final class MarshallingCodecSubstitute {

    @Substitute
    private void warmup() {
    }

}
