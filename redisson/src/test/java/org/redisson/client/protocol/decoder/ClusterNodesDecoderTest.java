package org.redisson.client.protocol.decoder;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.cluster.ClusterNodeInfo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ClusterNodesDecoderTest {

    @Test
    public void testIPs() throws IOException {
        ClusterNodesDecoder decoder = new ClusterNodesDecoder(false);
        ByteBuf buf = Unpooled.buffer();
        
        String info = "7af253f8c20a3b3fbd481801bd361ec6643c6f0b 192.168.234.129:7001@17001 master - 0 1478865073260 8 connected 5461-10922\n" +
        "a0d6a300f9f3b139c89cf45b75dbb7e4a01bb6b5 192.168.234.131:7005@17005 slave 5b00efb410f14ba5bb0a153c057e431d9ee4562e 0 1478865072251 5 connected\n" +
        "454b8aaab7d8687822923da37a91fc0eecbe7a88 192.168.234.130:7002@17002 slave 7af253f8c20a3b3fbd481801bd361ec6643c6f0b 0 1478865072755 8 connected\n" +
        "5b00efb410f14ba5bb0a153c057e431d9ee4562e 192.168.234.131:7004@17004 master - 0 1478865071746 5 connected 10923-16383\n" +
        "14edcdebea55853533a24d5cdc560ecc06ec5295 192.168.234.130:7003@17003 myself,master - 0 0 7 connected 0-5460\n" +
        "58d9f7c6d801aeebaf0e04e1aacb991e7e0ca8ff 192.168.234.129:7000@17000 slave 14edcdebea55853533a24d5cdc560ecc06ec5295 0 1478865071241 7 connected\n";
        
        byte[] src = info.getBytes();
        buf.writeBytes(src);
        List<ClusterNodeInfo> nodes = decoder.decode(buf, null);
        ClusterNodeInfo node = nodes.get(0);
        Assertions.assertEquals("192.168.234.129", node.getAddress().getHost());
        Assertions.assertEquals(7001, node.getAddress().getPort());
    }
    
    @Test
    public void testHostnames() throws IOException {
        ClusterNodesDecoder decoder = new ClusterNodesDecoder(false);
        ByteBuf buf = Unpooled.buffer();

        String info = "7af253f8c20a3b3fbd481801bd361ec6643c6f0b 192.168.234.129:7001@17001,hostname1 master - 0 1478865073260 8 connected 5461-10922\n" +
        "a0d6a300f9f3b139c89cf45b75dbb7e4a01bb6b5 192.168.234.131:7005@17005,hostname2 slave 5b00efb410f14ba5bb0a153c057e431d9ee4562e 0 1478865072251 5 connected\n" +
        "454b8aaab7d8687822923da37a91fc0eecbe7a88 192.168.234.130:7002@17002,hostname3 slave 7af253f8c20a3b3fbd481801bd361ec6643c6f0b 0 1478865072755 8 connected\n" +
        "5b00efb410f14ba5bb0a153c057e431d9ee4562e 192.168.234.131:7004@17004,hostname4 master - 0 1478865071746 5 connected 10923-16383\n" +
        "14edcdebea55853533a24d5cdc560ecc06ec5295 192.168.234.130:7003@17003,hostname5 myself,master - 0 0 7 connected 0-5460\n" +
        "58d9f7c6d801aeebaf0e04e1aacb991e7e0ca8ff 192.168.234.129:7000@17000,hostname6 slave 14edcdebea55853533a24d5cdc560ecc06ec5295 0 1478865071241 7 connected\n";

        byte[] src = info.getBytes();
        buf.writeBytes(src);
        List<ClusterNodeInfo> nodes = decoder.decode(buf, null);
        ClusterNodeInfo node = nodes.get(0);
        Assertions.assertEquals("hostname1", node.getAddress().getHost());
        Assertions.assertEquals(7001, node.getAddress().getPort());
    }

}
