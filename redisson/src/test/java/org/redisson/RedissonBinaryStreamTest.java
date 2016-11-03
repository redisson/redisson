package org.redisson;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;
import org.redisson.api.RBinaryStream;

public class RedissonBinaryStreamTest extends BaseTest {

    @Test
    public void testRead() throws IOException {
        RBinaryStream stream = redisson.getBinaryStream("test");
        byte[] value = {1, 2, 3, 4, 5, 6};
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
        
        assertThat(value).isEqualTo(readValue);
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
