package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;
import org.redisson.api.RBinaryStream;

public class RedissonBinaryStreamTest extends BaseTest {

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
    
    @Test
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
    
    @Test
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
    
    @Test
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
