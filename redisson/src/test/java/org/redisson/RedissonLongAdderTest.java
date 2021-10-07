package org.redisson;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLongAdder;

public class RedissonLongAdderTest extends BaseTest {

    @Test
    public void testSum() {
        RLongAdder adder1 = redisson.getLongAdder("test1");
        RLongAdder adder2 = redisson.getLongAdder("test1");
        RLongAdder adder3 = redisson.getLongAdder("test1");
        
        adder1.add(2);
        adder2.add(4);
        adder3.add(1);
        
        Assertions.assertThat(adder1.sum()).isEqualTo(7);
        Assertions.assertThat(adder2.sum()).isEqualTo(7);
        Assertions.assertThat(adder3.sum()).isEqualTo(7);
    }
    
    @Test
    public void testReset() {
        RLongAdder adder1 = redisson.getLongAdder("test1");
        RLongAdder adder2 = redisson.getLongAdder("test1");
        RLongAdder adder3 = redisson.getLongAdder("test1");
        
        adder1.add(2);
        adder2.add(4);
        adder3.add(1);
        
        adder1.reset();
        
        Assertions.assertThat(adder1.sum()).isZero();
        Assertions.assertThat(adder2.sum()).isZero();
        Assertions.assertThat(adder3.sum()).isZero();
    }
    
}
