package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBinaryStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBinaryStreamTest extends BaseTest {

    @Test
    public void testAsyncReadWrite() throws ExecutionException, InterruptedException {
        RBinaryStream stream = redisson.getBinaryStream("test");

        AsynchronousByteChannel channel = stream.getAsynchronousChannel();
        ByteBuffer bb = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7});
        channel.write(bb).get();

        AsynchronousByteChannel channel2 = stream.getAsynchronousChannel();
        ByteBuffer b = ByteBuffer.allocate(7);
        channel2.read(b).get();

        b.flip();
        assertThat(b).isEqualByComparingTo(bb);
    }

    @Test
    public void testChannelOverwrite() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        SeekableByteChannel c = stream.getChannel();
        assertThat(c.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7}))).isEqualTo(7);
        c.position(3);
        assertThat(c.write(ByteBuffer.wrap(new byte[]{0, 9, 10}))).isEqualTo(3);
        assertThat(c.position()).isEqualTo(6);

        ByteBuffer b = ByteBuffer.allocate(3);
        int r = c.read(b);
        assertThat(c.position()).isEqualTo(7);
        assertThat(r).isEqualTo(1);
        b.flip();
        byte[] bb = new byte[b.remaining()];
        b.get(bb);
        assertThat(bb).isEqualTo(new byte[]{7});

        c.position(0);
        ByteBuffer state = ByteBuffer.allocate(7);
        c.read(state);
        byte[] bb1 = new byte[7];
        state.flip();
        state.get(bb1);
        assertThat(bb1).isEqualTo(new byte[]{1, 2, 3, 0, 9, 10, 7});
    }

    @Test
    public void testChannelPosition() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        SeekableByteChannel c = stream.getChannel();
        c.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7}));
        c.position(3);
        ByteBuffer b = ByteBuffer.allocate(3);
        c.read(b);
        assertThat(c.position()).isEqualTo(6);
        byte[] bb = new byte[3];
        b.flip();
        b.get(bb);
        assertThat(bb).isEqualTo(new byte[]{4, 5, 6});
    }

    @Test
    public void testChannelTruncate() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        SeekableByteChannel c = stream.getChannel();
        c.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7}));
        assertThat(c.size()).isEqualTo(7);

        c.truncate(3);
        c.position(0);
        c.truncate(10);
        ByteBuffer b = ByteBuffer.allocate(3);
        c.read(b);
        byte[] bb = new byte[3];
        b.flip();
        b.get(bb);
        assertThat(c.size()).isEqualTo(3);
        assertThat(bb).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    public void testEmptyRead() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        assertThat(stream.getInputStream().read()).isEqualTo(-1);
    }
    
    private void testLimit(int sizeInMBs, int chunkSize) throws IOException, NoSuchAlgorithmException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        
        MessageDigest hash = MessageDigest.getInstance("SHA-1");
        hash.reset();
        
        for (int i = 0; i < sizeInMBs; i++) {
            byte[] bytes = new byte[chunkSize];
            ThreadLocalRandom.current().nextBytes(bytes);
            hash.update(bytes);
            stream.getOutputStream().write(bytes);
        }
        
        String writtenDataHash = new BigInteger(1, hash.digest()).toString(16);
        
        hash.reset();
        InputStream s = stream.getInputStream();
        long readBytesTotal = 0;
        while (true) {
            byte[] bytes = new byte[ThreadLocalRandom.current().nextInt(0, chunkSize)];
            int readBytes = s.read(bytes);
            if (readBytes == -1) {
                break;
            }
            if (readBytes < bytes.length) {
                bytes = Arrays.copyOf(bytes, readBytes);
            }
            hash.update(bytes);
            readBytesTotal += readBytes;
        }
        String readDataHash = new BigInteger(1, hash.digest()).toString(16);
        
        assertThat(writtenDataHash).isEqualTo(readDataHash);
        assertThat(readBytesTotal).isEqualTo(sizeInMBs*chunkSize);
        assertThat(stream.size()).isEqualTo(sizeInMBs*chunkSize);
        
        assertThat(stream.size()).isEqualTo(sizeInMBs*chunkSize);
        assertThat(redisson.getBucket("test").isExists()).isTrue();
        if (sizeInMBs*chunkSize <= 512*1024*1024) {
            assertThat(redisson.getBucket("test:parts").isExists()).isFalse();
            assertThat(redisson.getBucket("test:1").isExists()).isFalse();
        } else {
            int parts = (sizeInMBs*chunkSize)/(512*1024*1024);
            for (int i = 1; i < parts-1; i++) {
                assertThat(redisson.getBucket("test:" + i).isExists()).isTrue();
            }
        }
    }

    @Test
    public void testSkip() throws IOException {
        RBinaryStream t = redisson.getBinaryStream("test");
        t.set(new byte[] {1, 2, 3, 4, 5, 6});
        
        InputStream is = t.getInputStream();
        is.skip(3);
        byte[] b = new byte[6];
        is.read(b);
        assertThat(b).isEqualTo(new byte[] {4, 5, 6, 0, 0, 0});
    }
    
    @Test
    public void testLimit512by1024() throws IOException, NoSuchAlgorithmException {
        testLimit(512, 1024*1024);
    }
    
//    @Test
    public void testLimit1024By1000() throws IOException, NoSuchAlgorithmException {
        testLimit(1024, 1000*1000);
    }

    @Test
    public void testSet100() {
        RBinaryStream stream = redisson.getBinaryStream("test");

        byte[] bytes = new byte[100*1024*1024];
        ThreadLocalRandom.current().nextBytes(bytes);
        stream.set(bytes);
        
        assertThat(stream.size()).isEqualTo(bytes.length);
        assertThat(stream.get()).isEqualTo(bytes);
    }
    
//    @Test
    public void testSet1024() {
        RBinaryStream stream = redisson.getBinaryStream("test");

        byte[] bytes = new byte[1024*1024*1024];
        ThreadLocalRandom.current().nextBytes(bytes);
        stream.set(bytes);
        
        assertThat(stream.size()).isEqualTo(bytes.length);
        assertThat(redisson.getBucket("{test}:parts").isExists()).isTrue();
        assertThat(redisson.getBucket("test").size()).isEqualTo(512*1024*1024);
        assertThat(redisson.getBucket("test:1").size()).isEqualTo(bytes.length - 512*1024*1024);
    }
    
//    @Test
    public void testLimit1024By1024() throws IOException, NoSuchAlgorithmException {
        testLimit(1024, 1024*1024);
    }
    
    @Test
    public void testRead() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        byte[] value = {1, 2, 3, 4, 5, (byte)0xFF};
        stream.set(value);
        
        InputStream s = stream.getInputStream();
        int b = 0;
        byte[] readValue = new byte[6];
        int i = 0;
        while (true) {
            b = s.read();
            if (b == -1) {
                break;
            }
            readValue[i] = (byte) b;
            i++;
        }
        
        assertThat(readValue).isEqualTo(value);
    }
    
    @Test
    public void testReadArray() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        byte[] value = {1, 2, 3, 4, 5, 6};
        stream.set(value);
        
        InputStream s = stream.getInputStream();
        byte[] b = new byte[6];
        assertThat(s.read(b)).isEqualTo(6);
        assertThat(s.read(b)).isEqualTo(-1);
        
        assertThat(b).isEqualTo(value);
    }
    
    @Test
    public void testReadArrayWithOffset() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        byte[] value = {1, 2, 3, 4, 5, 6};
        stream.set(value);

        InputStream s = stream.getInputStream();
        byte[] b = new byte[4];
        assertThat(s.read(b, 1, 3)).isEqualTo(3);
        
        byte[] valuesRead = {0, 1, 2, 3};
        assertThat(b).isEqualTo(valuesRead);
    }

    @Test
    public void testWriteArray() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        OutputStream os = stream.getOutputStream();
        byte[] value = {1, 2, 3, 4, 5, 6};
        os.write(value);
        
        byte[] s = stream.get();
        assertThat(s).isEqualTo(value);
    }
    
    @Test
    public void testWriteArrayWithOffset() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        OutputStream os = stream.getOutputStream();

        byte[] value = {1, 2, 3, 4, 5, 6};
        os.write(value, 0, 3);
        byte[] s = stream.get();
        
        assertThat(s).isEqualTo(new byte[] {1, 2, 3});
        
        os.write(value, 3, 3);
        s = stream.get();
        
        assertThat(s).isEqualTo(value);
    }

    
}
