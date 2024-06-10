package org.redisson.client.handler;

import io.netty.channel.Channel;
import io.netty.channel.local.LocalChannel;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.redisson.client.ChannelName;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.decoder.ObjectDecoder;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;
import org.redisson.client.protocol.pubsub.PubSubType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

class CommandPubSubDecoderTest {

  private static class PartsArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(Collections.singletonList("message")),
          Arguments.of(Arrays.asList("pmessage", "test*".getBytes(CharsetUtil.UTF_8))),
          Arguments.of(Arrays.asList("pmessage", "test*".getBytes(CharsetUtil.UTF_8),
                                     "test1".getBytes(CharsetUtil.UTF_8))),
          Arguments.of(Arrays.asList("smessage", "test".getBytes(CharsetUtil.UTF_8))),
          Arguments.of(Arrays.asList("message", "test".getBytes(CharsetUtil.UTF_8)))
      );
    }
  }

  private static class SubscribePartsArgumentsProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(new ObjectDecoder<>(LongCodec.INSTANCE.getValueDecoder()),
                       PubSubType.SSUBSCRIBE, Arrays.asList("smessage",
                                                            UUID.randomUUID()
                                                                .toString().getBytes(
                                                                    CharsetUtil.UTF_8),
                                                            123L)),
          Arguments.of(new ObjectDecoder<>(LongCodec.INSTANCE.getValueDecoder()),
                       PubSubType.PSUBSCRIBE, Arrays.asList("pmessage",
                                                            "test*".getBytes(CharsetUtil.UTF_8),
                                                            "test1".getBytes(CharsetUtil.UTF_8),
                                                            123L)),
          Arguments.of(new ObjectDecoder<>(LongCodec.INSTANCE.getValueDecoder()),
                       PubSubType.SUBSCRIBE, Arrays.asList("message",
                                                           UUID.randomUUID()
                                                               .toString().getBytes(
                                                                   CharsetUtil.UTF_8),
                                                           123L))
      );
    }
  }

  @ParameterizedTest
  @ArgumentsSource(SubscribePartsArgumentsProvider.class)
  void testPositiveSubscribeMessageDecoder(MultiDecoder<Object> multiDecoder, PubSubType subType,
                                           List<Object> parts) {
    // GIVEN
    // initialize internal deps
    Channel channel = new LocalChannel();
    RedisConnection connection = new RedisPubSubConnection(null, channel, null);
    RedisClientConfig config = new RedisClientConfig().setAddress("redis://127.0.0.1:6379")
                                                      .setExecutor(new ForkJoinPool());
    CommandPubSubDecoder decoder = new CommandPubSubDecoder(config);
    // do subscribe
    ChannelName channelName = new ChannelName(new String((byte[]) parts.get(1), CharsetUtil.UTF_8));
    PubSubStatusMessage result = new PubSubStatusMessage(subType, channelName);
    CommandData<Object, Object> subscribeCommandData =
        new CommandData<>(null, multiDecoder, null,
                          new RedisCommand<>(subType.name(), multiDecoder), null);
    decoder.addPubSubCommand(channelName, subscribeCommandData);
    try {
      decoder.decodeResult(subscribeCommandData, parts, channel, result);
    } catch (IOException e) {
      fail(e);
    }
    // prepare message command
    CommandData<Object, Object> messageCommandData =
        new CommandData<>(null, multiDecoder, null,
                          new RedisCommand<>((String) parts.get(0), multiDecoder), null);
    // WHEN
    MultiDecoder<Object> actualDecoder = decoder.messageDecoder(messageCommandData, parts);
    //THEN
    assertEquals(multiDecoder, actualDecoder);
  }

  @Test
  void testNegativeSubscribeMessageDecoder() {
    // GIVEN
    PubSubType subType = PubSubType.SUBSCRIBE;
    List<Object> parts = Arrays.asList("message",
                                       UUID.randomUUID().toString().getBytes(CharsetUtil.UTF_8));
    MultiDecoder<Object> subMultiDecoder = new ObjectDecoder<>(
        ByteArrayCodec.INSTANCE.getValueDecoder());
    // initialize internal deps
    Channel channel = new LocalChannel();
    RedisConnection connection = new RedisPubSubConnection(null, channel, null);
    RedisClientConfig config = new RedisClientConfig().setAddress("redis://127.0.0.1:6379")
                                                      .setExecutor(new ForkJoinPool());
    CommandPubSubDecoder decoder = new CommandPubSubDecoder(config);
    // do subscribe
    ChannelName channelName = new ChannelName(new String((byte[]) parts.get(1), CharsetUtil.UTF_8));
    PubSubStatusMessage result = new PubSubStatusMessage(subType, channelName);
    CommandData<Object, Object> subscribeCommandData =
        new CommandData<>(null, subMultiDecoder, null,
                          new RedisCommand<>(subType.name(), subMultiDecoder), null);
    decoder.addPubSubCommand(channelName, subscribeCommandData);
    try {
      decoder.decodeResult(subscribeCommandData, parts, channel, result);
    } catch (IOException e) {
      fail(e);
    }
    // prepare message command
    MultiDecoder<Object> msgMultiDecoder = new ObjectDecoder<>(
        ByteArrayCodec.INSTANCE.getValueDecoder());
    CommandData<Object, Object> messageCommandData =
        new CommandData<>(null, msgMultiDecoder, null,
                          new RedisCommand<>((String) parts.get(0), msgMultiDecoder), null);
    // WHEN
    MultiDecoder<Object> actualDecoder = decoder.messageDecoder(messageCommandData, parts);
    //THEN
    assertNotEquals(msgMultiDecoder, actualDecoder);
    assertEquals(subMultiDecoder, actualDecoder);
  }

  @ParameterizedTest
  @ArgumentsSource(SubscribePartsArgumentsProvider.class)
  void testSelectDecoder(MultiDecoder<Object> multiDecoder, PubSubType subType,
                         List<Object> extendedPart) {
    // GIVEN
    List<Object> parts = extendedPart.subList(0, extendedPart.size() - 1);
    // initialize internal deps
    Channel channel = new LocalChannel();
    RedisConnection connection = new RedisPubSubConnection(null, channel, null);
    RedisClientConfig config = new RedisClientConfig().setAddress("redis://127.0.0.1:6379")
                                                      .setExecutor(new ForkJoinPool());
    CommandPubSubDecoder decoder = new CommandPubSubDecoder(config);
    // do subscribe
    ChannelName channelName = new ChannelName(new String((byte[]) parts.get(1), CharsetUtil.UTF_8));
    PubSubStatusMessage result = new PubSubStatusMessage(subType, channelName);
    CommandData<Object, Object> subscribeCommandData =
        new CommandData<>(null, multiDecoder, null,
                          new RedisCommand<>(subType.name(), multiDecoder), null);
    decoder.addPubSubCommand(channelName, subscribeCommandData);
    try {
      decoder.decodeResult(subscribeCommandData, parts, channel, result);
    } catch (IOException e) {
      fail(e);
    }
    // WHEN
    Decoder<Object> actualDecoder = decoder.selectDecoder(null, parts, 0, null);
    //THEN
    assertEquals(multiDecoder.getDecoder(null, 0, null, 0), actualDecoder);
  }


  @ParameterizedTest
  @ArgumentsSource(PartsArgumentsProvider.class)
  void testSelectDecoder(List<Object> parts) {
    // GIVEN
    RedisClientConfig config = new RedisClientConfig().setAddress("redis://127.0.0.1:6379")
                                                      .setExecutor(new ForkJoinPool());
    CommandPubSubDecoder decoder = new CommandPubSubDecoder(config);
    Decoder<Object> expectedDecoder = ByteArrayCodec.INSTANCE.getValueDecoder();
    // WHEN
    Decoder<Object> actualDecoder = decoder.selectDecoder(null, parts, 0, null);
    //THEN
    assertEquals(expectedDecoder, actualDecoder);
  }

  @Test
  void testDifferentSelectDecoder() {
    // GIVEN
    MultiDecoder<Object> multiDecoder = new ObjectDecoder<>(LongCodec.INSTANCE.getValueDecoder());
    PubSubType subType = PubSubType.PSUBSCRIBE;
    List<Object> parts = Arrays.asList("pmessage", "test*".getBytes(CharsetUtil.UTF_8));

    // initialize internal deps
    Channel channel = new LocalChannel();
    RedisConnection connection = new RedisPubSubConnection(null, channel, null);
    RedisClientConfig config = new RedisClientConfig().setAddress("redis://127.0.0.1:6379")
                                                      .setExecutor(new ForkJoinPool());
    CommandPubSubDecoder decoder = new CommandPubSubDecoder(config);
    // do subscribe
    ChannelName channelName = new ChannelName(new String((byte[]) parts.get(1), CharsetUtil.UTF_8));
    PubSubStatusMessage result = new PubSubStatusMessage(subType, channelName);
    CommandData<Object, Object> subscribeCommandData =
        new CommandData<>(null, multiDecoder, null,
                          new RedisCommand<>(subType.name(), multiDecoder), null);
    decoder.addPubSubCommand(channelName, subscribeCommandData);
    try {
      decoder.decodeResult(subscribeCommandData, parts, channel, result);
    } catch (IOException e) {
      fail(e);
    }
    // WHEN
    Decoder<Object> actualDecoder = decoder.selectDecoder(null, parts, 0, null);
    //THEN
    assertNotEquals(multiDecoder.getDecoder(null, 0, null, 0), actualDecoder);
    assertEquals(ByteArrayCodec.INSTANCE.getValueDecoder(), actualDecoder);
  }
}