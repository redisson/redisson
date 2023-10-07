package org.redisson.pubsub;

import mockit.Injectable;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;

import org.junit.jupiter.api.Test;
import org.redisson.RedissonCountDownLatchEntry;

public class CountDownLatchPubSubTest {

  @Tested
  private CountDownLatchPubSub countDownLatchPubSub;

  @Injectable
  private PublishSubscribeService publishSubscribeService;

  @Test
  public void testOnZeroMessageAllListenersExecuted(@Mocked Runnable listener) {
    int listenCount = 10;
    RedissonCountDownLatchEntry entry = new RedissonCountDownLatchEntry(null);
    for (int i = 0; i < listenCount; i++) {
      entry.addListener(listener);
    }
    countDownLatchPubSub.onMessage(entry, CountDownLatchPubSub.ZERO_COUNT_MESSAGE);
    new Verifications() {{
      listener.run();
      times = listenCount;
    }};
  }
}