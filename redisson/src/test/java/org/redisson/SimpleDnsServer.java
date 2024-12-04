package org.redisson;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.*;

import java.net.InetAddress;

public class SimpleDnsServer {

    EventLoopGroup group = new NioEventLoopGroup();
    private Channel channel;
    private String ip = "127.0.0.1";

    public SimpleDnsServer() throws InterruptedException {
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

            ChannelFuture future = bootstrap.bind(55).sync(); // Bind to port 53 for DNS
            channel = future.channel();
    }

    public void stop() {
        channel.close();
        group.shutdownGracefully();
    }

    public void updateIP(String ip) {
        this.ip = ip;
    }

    private class DnsMessageHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) throws Exception {
            DefaultDnsQuestion question = query.recordAt(DnsSection.QUESTION);
            String requestedDomain = question.name();

//            System.out.println("Received DNS query for: " + requestedDomain + " " + query);

            // Create a response with a dummy IP address
            DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
            response.addRecord(DnsSection.QUESTION, question);
            response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(question.name(), DnsRecordType.A, 0,
                    Unpooled.wrappedBuffer(InetAddress.getByName(ip).getAddress()))); // Example IP

            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
