package org.redisson.client.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.config.CommandMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CommandDecoderSkipTest {

    private static final class FakeRedisServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private final List<String> receivedCommands = new CopyOnWriteArrayList<>();
        private ByteBuf acc;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            acc = ctx.alloc().buffer();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            acc.writeBytes(msg);
            while (parseCommand(ctx)) {
            }
        }

        private boolean parseCommand(ChannelHandlerContext ctx) {
            acc.markReaderIndex();
            String header = readLine(acc);
            if (header == null || header.isEmpty() || header.charAt(0) != '*') {
                acc.resetReaderIndex();
                return false;
            }
            int args = Integer.parseInt(header.substring(1));
            String commandName = null;
            for (int i = 0; i < args; i++) {
                String argHeader = readLine(acc);
                if (argHeader == null || argHeader.isEmpty() || argHeader.charAt(0) != '$') {
                    acc.resetReaderIndex();
                    return false;
                }
                int len = Integer.parseInt(argHeader.substring(1));
                if (acc.readableBytes() < len + 2) {
                    acc.resetReaderIndex();
                    return false;
                }
                byte[] payload = new byte[len];
                acc.readBytes(payload);
                acc.skipBytes(2);
                if (i == 0) {
                    commandName = new String(payload, CharsetUtil.UTF_8);
                }
            }
            reply(ctx, commandName);
            return true;
        }

        private void reply(ChannelHandlerContext ctx, String commandName) {
            byte[] reply;
            if ("PING".equals(commandName)) {
                reply = "+PONG\r\n".getBytes(CharsetUtil.UTF_8);
            } else if ("SCARD".equals(commandName)) {
                reply = ":5\r\n".getBytes(CharsetUtil.UTF_8);
            } else if ("GETBOOL".equals(commandName)) {
                reply = "#t\r\n".getBytes(CharsetUtil.UTF_8);
            } else {
                reply = "-ERR unexpected command\r\n".getBytes(CharsetUtil.UTF_8);
            }
            ctx.writeAndFlush(Unpooled.wrappedBuffer(reply));
        }

        private static String readLine(ByteBuf in) {
            int lfIdx = in.indexOf(in.readerIndex(), in.writerIndex(), (byte) '\n');
            if (lfIdx == -1) {
                return null;
            }
            int len = lfIdx - in.readerIndex() - 1;
            if (len < 0) {
                return "";
            }
            byte[] dst = new byte[len];
            in.readBytes(dst);
            in.skipBytes(2);
            return new String(dst, CharsetUtil.UTF_8);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (acc != null) {
                acc.release();
            }
        }
    }

    private static final AtomicLong COUNTER = new AtomicLong();

    private EventLoopGroup serverGroup;
    private EventLoopGroup clientGroup;
    private Channel clientChannel;

    @BeforeEach
    void setUp() throws InterruptedException {
        EventLoopGroup serverGroup = new DefaultEventLoopGroup(1);
        EventLoopGroup clientGroup = new DefaultEventLoopGroup(1);
        this.serverGroup = serverGroup;
        this.clientGroup = clientGroup;
        LocalAddress address = new LocalAddress("commands-queue-order-test-" + COUNTER.incrementAndGet());

        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(serverGroup)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new FakeRedisServerHandler());
                    }
                });
        serverBootstrap.bind(address).sync();

        Bootstrap bootstrap = new Bootstrap()
                .group(clientGroup)
                .channel(LocalChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(
                                new CommandEncoder(CommandMapper.direct()),
                                CommandBatchEncoder.INSTANCE,
                                new CommandsQueue(),
                                new CommandDecoder("redis"));
                    }
                });
        clientChannel = bootstrap.connect(address).sync().channel();
    }

    @AfterEach
    void tearDown() {
        if (clientChannel != null) {
            clientChannel.close().syncUninterruptibly();
        }
        serverGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        clientGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
    }

    @Test
    void testSkipOfCompletedCommandKeepsReplyStreamAligned() throws Exception {
        // GETBOOL replies with a RESP3 boolean (#t). Its promise is already completed
        // when the reply arrives, like a command timed out or a cancelled keepalive ping.
        CompletableFuture<Object> timedOut = new CompletableFuture<>();
        timedOut.completeExceptionally(new TimeoutException());
        CompletableFuture<Object> scardPromise = new CompletableFuture<>();
        CompletableFuture<Object> pingPromise = new CompletableFuture<>();

        clientChannel.writeAndFlush(new CommandData<>(timedOut, StringCodec.INSTANCE,
                new RedisStrictCommand<>("GETBOOL"), new Object[0]));
        clientChannel.writeAndFlush(new CommandData<>(scardPromise, StringCodec.INSTANCE,
                RedisCommands.SCARD_INT, new Object[]{"test"}));
        clientChannel.writeAndFlush(new CommandData<>(pingPromise, StringCodec.INSTANCE,
                RedisCommands.PING, new Object[0]));

        // the late GETBOOL reply must be skipped whole: the following commands
        // still receive their own replies
        assertThat(scardPromise.get(10, TimeUnit.SECONDS)).isEqualTo(5);
        assertThat(pingPromise.get(10, TimeUnit.SECONDS)).isEqualTo("PONG");
    }

}
