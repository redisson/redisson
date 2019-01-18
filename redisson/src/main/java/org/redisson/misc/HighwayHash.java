/**
 * Copyright (c) 2013-2019 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.misc;

/**
 * HighwayHash algorithm. See <a href="https://github.com/google/highwayhash">
 * HighwayHash on GitHub</a>
 */
public final class HighwayHash {
  private final long[] v0 = new long[4];
  private final long[] v1 = new long[4];
  private final long[] mul0 = new long[4];
  private final long[] mul1 = new long[4];
  private boolean done = false;

  /**
   * @param key0 first 8 bytes of the key
   * @param key1 next 8 bytes of the key
   * @param key2 next 8 bytes of the key
   * @param key3 last 8 bytes of the key
   */
  public HighwayHash(long key0, long key1, long key2, long key3) {
    reset(key0, key1, key2, key3);
  }

  /**
   * @param key array of size 4 with the key to initialize the hash with
   */
  public HighwayHash(long[] key) {
    if (key.length != 4) {
      throw new IllegalArgumentException(String.format("Key length (%s) must be 4", key.length));
    }
    reset(key[0], key[1], key[2], key[3]);
  }

  /**
   * Updates the hash with 32 bytes of data. If you can read 4 long values
   * from your data efficiently, prefer using update() instead for more speed.
   * @param packet data array which has a length of at least pos + 32
   * @param pos position in the array to read the first of 32 bytes from
   */
  public void updatePacket(byte[] packet, int pos) {
    if (pos < 0) {
      throw new IllegalArgumentException(String.format("Pos (%s) must be positive", pos));
    }
    if (pos + 32 > packet.length) {
      throw new IllegalArgumentException("packet must have at least 32 bytes after pos");
    }
    long a0 = read64(packet, pos + 0);
    long a1 = read64(packet, pos + 8);
    long a2 = read64(packet, pos + 16);
    long a3 = read64(packet, pos + 24);
    update(a0, a1, a2, a3);
  }

  /**
   * Updates the hash with 32 bytes of data given as 4 longs. This function is
   * more efficient than updatePacket when you can use it.
   * @param a0 first 8 bytes in little endian 64-bit long
   * @param a1 next 8 bytes in little endian 64-bit long
   * @param a2 next 8 bytes in little endian 64-bit long
   * @param a3 last 8 bytes in little endian 64-bit long
   */
  public void update(long a0, long a1, long a2, long a3) {
    if (done) {
      throw new IllegalStateException("Can compute a hash only once per instance");
    }
    v1[0] += mul0[0] + a0;
    v1[1] += mul0[1] + a1;
    v1[2] += mul0[2] + a2;
    v1[3] += mul0[3] + a3;
    for (int i = 0; i < 4; ++i) {
      mul0[i] ^= (v1[i] & 0xffffffffL) * (v0[i] >>> 32);
      v0[i] += mul1[i];
      mul1[i] ^= (v0[i] & 0xffffffffL) * (v1[i] >>> 32);
    }
    v0[0] += zipperMerge0(v1[1], v1[0]);
    v0[1] += zipperMerge1(v1[1], v1[0]);
    v0[2] += zipperMerge0(v1[3], v1[2]);
    v0[3] += zipperMerge1(v1[3], v1[2]);
    v1[0] += zipperMerge0(v0[1], v0[0]);
    v1[1] += zipperMerge1(v0[1], v0[0]);
    v1[2] += zipperMerge0(v0[3], v0[2]);
    v1[3] += zipperMerge1(v0[3], v0[2]);
  }


  /**
   * Updates the hash with the last 1 to 31 bytes of the data. You must use
   * updatePacket first per 32 bytes of the data, if and only if 1 to 31 bytes
   * of the data are not processed after that, updateRemainder must be used for
   * those final bytes.
   * @param bytes data array which has a length of at least pos + size_mod32
   * @param pos position in the array to start reading size_mod32 bytes from
   * @param size_mod32 the amount of bytes to read
   */
  public void updateRemainder(byte[] bytes, int pos, int size_mod32) {
    if (pos < 0) {
      throw new IllegalArgumentException(String.format("Pos (%s) must be positive", pos));
    }
    if (size_mod32 < 0 || size_mod32 >= 32) {
      throw new IllegalArgumentException(
          String.format("size_mod32 (%s) must be between 0 and 31", size_mod32));
    }
    if (pos + size_mod32 > bytes.length) {
      throw new IllegalArgumentException("bytes must have at least size_mod32 bytes after pos");
    }
    int size_mod4 = size_mod32 & 3;
    int remainder = size_mod32 & ~3;
    byte[] packet = new byte[32];
    for (int i = 0; i < 4; ++i) {
      v0[i] += ((long)size_mod32 << 32) + size_mod32;
    }
    rotate32By(size_mod32, v1);
    for (int i = 0; i < remainder; i++) {
      packet[i] = bytes[pos + i];
    }
    if ((size_mod32 & 16) != 0) {
      for (int i = 0; i < 4; i++) {
        packet[28 + i] = bytes[pos + remainder + i + size_mod4 - 4];
      }
    } else {
      if (size_mod4 != 0) {
        packet[16 + 0] = bytes[pos + remainder + 0];
        packet[16 + 1] = bytes[pos + remainder + (size_mod4 >>> 1)];
        packet[16 + 2] = bytes[pos + remainder + (size_mod4 - 1)];
      }
    }
    updatePacket(packet, 0);
  }

  /**
   * Computes the hash value after all bytes were processed. Invalidates the
   * state.
   *
   * NOTE: The 64-bit HighwayHash algorithm is declared stable and no longer subject to change.
   *
   * @return 64-bit hash
   */
  public long finalize64() {
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    done = true;
    return v0[0] + v1[0] + mul0[0] + mul1[0];
  }

  /**
   * Computes the hash value after all bytes were processed. Invalidates the
   * state.
   *
   * NOTE: The 128-bit HighwayHash algorithm is not yet frozen and subject to change.
   *
   * @return array of size 2 containing 128-bit hash
   */
  public long[] finalize128() {
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    done = true;
    long[] hash = new long[2];
    hash[0] = v0[0] + mul0[0] + v1[2] + mul1[2];
    hash[1] = v0[1] + mul0[1] + v1[3] + mul1[3];
    return hash;
  }

  /**
   * Computes the hash value after all bytes were processed. Invalidates the
   * state.
   *
   * NOTE: The 256-bit HighwayHash algorithm is not yet frozen and subject to change.
   *
   * @return array of size 4 containing 256-bit hash
   */
  public long[] finalize256() {
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    permuteAndUpdate();
    done = true;
    long[] hash = new long[4];
    modularReduction(v1[1] + mul1[1], v1[0] + mul1[0],
                     v0[1] + mul0[1], v0[0] + mul0[0],
                     hash, 0);
    modularReduction(v1[3] + mul1[3], v1[2] + mul1[2],
                     v0[3] + mul0[3], v0[2] + mul0[2],
                     hash, 2);
    return hash;
  }
  private void reset(long key0, long key1, long key2, long key3) {
    mul0[0] = 0xdbe6d5d5fe4cce2fL;
    mul0[1] = 0xa4093822299f31d0L;
    mul0[2] = 0x13198a2e03707344L;
    mul0[3] = 0x243f6a8885a308d3L;
    mul1[0] = 0x3bd39e10cb0ef593L;
    mul1[1] = 0xc0acf169b5f18a8cL;
    mul1[2] = 0xbe5466cf34e90c6cL;
    mul1[3] = 0x452821e638d01377L;
    v0[0] = mul0[0] ^ key0;
    v0[1] = mul0[1] ^ key1;
    v0[2] = mul0[2] ^ key2;
    v0[3] = mul0[3] ^ key3;
    v1[0] = mul1[0] ^ ((key0 >>> 32) | (key0 << 32));
    v1[1] = mul1[1] ^ ((key1 >>> 32) | (key1 << 32));
    v1[2] = mul1[2] ^ ((key2 >>> 32) | (key2 << 32));
    v1[3] = mul1[3] ^ ((key3 >>> 32) | (key3 << 32));
  }

  private long zipperMerge0(long v1, long v0) {
    return (((v0 & 0xff000000L) | (v1 & 0xff00000000L)) >>> 24) |
             (((v0 & 0xff0000000000L) | (v1 & 0xff000000000000L)) >>> 16) |
             (v0 & 0xff0000L) | ((v0 & 0xff00L) << 32) |
             ((v1 & 0xff00000000000000L) >>> 8) | (v0 << 56);
  }

  private long zipperMerge1(long v1, long v0) {
    return (((v1 & 0xff000000L) | (v0 & 0xff00000000L)) >>> 24) |
             (v1 & 0xff0000L) | ((v1 & 0xff0000000000L) >>> 16) |
             ((v1 & 0xff00L) << 24) | ((v0 & 0xff000000000000L) >>> 8) |
             ((v1 & 0xffL) << 48) | (v0 & 0xff00000000000000L);
  }

  private long read64(byte[] src, int pos) {
    // Mask with 0xffL so that it is 0..255 as long (byte can only be -128..127)
    return (src[pos + 0] & 0xffL) | ((src[pos + 1] & 0xffL) << 8) |
        ((src[pos + 2] & 0xffL) << 16) | ((src[pos + 3] & 0xffL) << 24) |
        ((src[pos + 4] & 0xffL) << 32) | ((src[pos + 5] & 0xffL) << 40) |
        ((src[pos + 6] & 0xffL) << 48) | ((src[pos + 7] & 0xffL) << 56);
  }

  private void rotate32By(long count, long[] lanes) {
    for (int i = 0; i < 4; ++i) {
      long half0 = (lanes[i] & 0xffffffffL);
      long half1 = (lanes[i] >>> 32) & 0xffffffffL;
      lanes[i] = ((half0 << count)  & 0xffffffffL) | (half0 >>> (32 - count));
      lanes[i] |= ((long)(((half1 << count) & 0xffffffffL) |
          (half1 >>> (32 - count)))) << 32;
    }
  }

  private void permuteAndUpdate() {
    update((v0[2] >>> 32) | (v0[2] << 32),
        (v0[3] >>> 32) | (v0[3] << 32),
        (v0[0] >>> 32) | (v0[0] << 32),
        (v0[1] >>> 32) | (v0[1] << 32));
  }

  private void modularReduction(long a3_unmasked, long a2, long a1,
                                long a0, long[] hash, int pos) {
    long a3 = a3_unmasked & 0x3FFFFFFFFFFFFFFFL;
    hash[pos + 1] = a1 ^ ((a3 << 1) | (a2 >>> 63)) ^ ((a3 << 2) | (a2 >>> 62));
    hash[pos + 0] = a0 ^ (a2 << 1) ^ (a2 << 2);
  }

  //////////////////////////////////////////////////////////////////////////////

  /**
   * NOTE: The 64-bit HighwayHash algorithm is declared stable and no longer subject to change.
   *
   * @param data array with data bytes
   * @param offset position of first byte of data to read from
   * @param length number of bytes from data to read
   * @param key array of size 4 with the key to initialize the hash with
   * @return 64-bit hash for the given data
   */
  public static long hash64(byte[] data, int offset, int length, long[] key) {
    HighwayHash h = new HighwayHash(key);
    h.processAll(data, offset, length);
    return h.finalize64();
  }

  /**
   * NOTE: The 128-bit HighwayHash algorithm is not yet frozen and subject to change.
   *
   * @param data array with data bytes
   * @param offset position of first byte of data to read from
   * @param length number of bytes from data to read
   * @param key array of size 4 with the key to initialize the hash with
   * @return array of size 2 containing 128-bit hash for the given data
   */
  public static long[] hash128(
      byte[] data, int offset, int length, long[] key) {
    HighwayHash h = new HighwayHash(key);
    h.processAll(data, offset, length);
    return h.finalize128();
  }

  /**
   * NOTE: The 256-bit HighwayHash algorithm is not yet frozen and subject to change.
   *
   * @param data array with data bytes
   * @param offset position of first byte of data to read from
   * @param length number of bytes from data to read
   * @param key array of size 4 with the key to initialize the hash with
   * @return array of size 4 containing 256-bit hash for the given data
   */
  public static long[] hash256(
      byte[] data, int offset, int length, long[] key) {
    HighwayHash h = new HighwayHash(key);
    h.processAll(data, offset, length);
    return h.finalize256();
  }

  private void processAll(byte[] data, int offset, int length) {
    int i;
    for (i = 0; i + 32 <= length; i += 32) {
      updatePacket(data, offset + i);
    }
    if ((length & 31) != 0) {
      updateRemainder(data, offset + i, length & 31);
    }
  }
}