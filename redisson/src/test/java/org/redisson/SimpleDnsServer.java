package org.redisson;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleDnsServer {

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final Channel channel;
    private String ip = "127.0.0.1";
    private final int port;
    private volatile List<String> rotation;
    private final AtomicInteger rotationIndex = new AtomicInteger();

    public SimpleDnsServer() throws InterruptedException {
        this(ThreadLocalRandom.current().nextInt(49152, 65535), Collections.emptyList());
    }

    public SimpleDnsServer(int port) throws InterruptedException {
        this(port, Collections.emptyList());
    }

    public SimpleDnsServer(List<String> rotationIPs) throws InterruptedException {
        this(ThreadLocalRandom.current().nextInt(49152, 65535), rotationIPs);
    }

    private SimpleDnsServer(int port, List<String> rotation) throws InterruptedException {
        this.rotation = rotation;
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new DatagramDnsQueryDecoder());
                        ch.pipeline().addLast(new DatagramDnsResponseEncoder());
                        ch.pipeline().addLast(new DnsMessageHandler());
                    }
                });

        this.port = port;
        ChannelFuture future = bootstrap.bind(port).sync();
        channel = future.channel();
    }

    public InetSocketAddress getAddr() {
        return new InetSocketAddress(ip, port);
    }

    public void stop() {
        channel.close();
        group.shutdownGracefully();
    }

    public void updateIP(String ip) {
        this.ip = ip;
    }

    public void updateRotation(List<String> rotation) {
        this.rotation = rotation;
    }

    private class DnsMessageHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) throws Exception {
            DefaultDnsQuestion question = query.recordAt(DnsSection.QUESTION);
            String requestedDomain = question.name();
            List<String> current = rotation;

            String answerIp;
            if (current == null || current.isEmpty()) {
                answerIp = ip;
            } else {
                int idx = Math.floorMod(rotationIndex.getAndIncrement(), current.size());
                answerIp = current.get(idx);
            }

            DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
            response.addRecord(DnsSection.QUESTION, question);
            response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(question.name(), DnsRecordType.A, 0,
                    Unpooled.wrappedBuffer(InetAddress.getByName(answerIp).getAddress()))); // Example IP

            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
