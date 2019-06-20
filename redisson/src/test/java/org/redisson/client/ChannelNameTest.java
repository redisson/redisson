package org.redisson.client;

import org.junit.Assert;
import org.junit.Test;

public class ChannelNameTest {

  @Test
  public void testCharAt() {
    Assert.assertEquals('\u0001', new ChannelName(new byte[]{1, 0}).charAt(0));
  }

  @Test
  public void testEquals() {
    final ChannelName channelName = new ChannelName(new byte[]{});
    Assert.assertFalse(channelName.equals(null));
    Assert.assertFalse(channelName.equals(new ChannelName((byte[])null)));

    Assert.assertTrue(channelName.equals(channelName));
    Assert.assertTrue(channelName.equals(""));
  }

  @Test
  public void testGetname() {
    Assert.assertNull(new ChannelName((byte[])null).getName());
  }

  @Test
  public void testHashCode() {
    Assert.assertEquals(131396, new ChannelName("foo").hashCode());
  }

  @Test
  public void testLength() {
    Assert.assertEquals(1, new ChannelName(new byte[]{0}).length());
  }

  @Test
  public void testSubSequence() {
    Assert.assertEquals("", new ChannelName(new byte[]{}).subSequence(0, 0));
  }

  @Test
  public void testToString() {
    Assert.assertEquals("\u0001\u0000", new ChannelName(new byte[]{1, 0}).toString());
  }
}
