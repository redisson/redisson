package org.redisson.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;

import io.netty.buffer.ByteBuf;

public class FSTCodecTest {

    static class MyClass extends ConcurrentHashMap {

        public MyClass() {
        }
        
    }
    
    @Test
    public void testClassVisiblity() throws IOException {
        FstCodec codec = new FstCodec();
        MyClass mm = new MyClass();
        mm.put("1", "2");
        ByteBuf output = codec.getValueEncoder().encode(mm);
        Object s = codec.getValueDecoder().decode(output, null);
        assertThat(s).isNotNull();
    }
    
}
