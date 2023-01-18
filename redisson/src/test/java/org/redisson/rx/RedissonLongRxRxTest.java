package org.redisson.rx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLongRx;

public class RedissonLongRxRxTest extends BaseRxTest {
	@Test
	public void testDecrementAndGet() {
		RAtomicLongRx al = redisson.getAtomicLong("test");
		Assertions.assertEquals(3, sync(al.addAndGet(3)).intValue());
		Assertions.assertEquals(9, sync(al.addAndGet(6)).intValue());
		Assertions.assertEquals(7, sync(al.decrementAndGet(2)).intValue());
		Assertions.assertEquals(4, sync(al.decrementAndGet(3)).intValue());
		Assertions.assertEquals(-1, sync(al.decrementAndGet(5)).intValue());

	}

}
