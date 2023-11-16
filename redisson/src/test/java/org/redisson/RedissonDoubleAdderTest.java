package org.redisson;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RDoubleAdder;

public class RedissonDoubleAdderTest extends RedisDockerTest {

    @Test
    public void testSum() {
        RDoubleAdder adder1 = redisson.getDoubleAdder("test1");
        RDoubleAdder adder2 = redisson.getDoubleAdder("test1");
        RDoubleAdder adder3 = redisson.getDoubleAdder("test1");
        
        adder1.add(2.38);
        adder2.add(4.14);
        adder3.add(1.48);
        
        Assertions.assertThat(adder1.sum()).isEqualTo(8);
        Assertions.assertThat(adder2.sum()).isEqualTo(8);
        Assertions.assertThat(adder3.sum()).isEqualTo(8);
    }
    
    @Test
    public void testReset() {
        RDoubleAdder adder1 = redisson.getDoubleAdder("test1");
        RDoubleAdder adder2 = redisson.getDoubleAdder("test1");
        RDoubleAdder adder3 = redisson.getDoubleAdder("test1");
        
        adder1.add(2);
        adder2.add(4);
        adder3.add(1);
        
        adder1.reset();
        
        Assertions.assertThat(adder1.sum()).isZero();
        Assertions.assertThat(adder2.sum()).isZero();
        Assertions.assertThat(adder3.sum()).isZero();
    }
    
}
