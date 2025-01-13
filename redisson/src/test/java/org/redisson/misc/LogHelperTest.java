package org.redisson.misc;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Philipp Marx
 */
public class LogHelperTest {

    @Test
    public void toStringWithNull() {
        assertThat(LogHelper.toString(null)).isEqualTo("null");
    }

    @Test
    public void toStringWithNestedPrimitives() {
        Object[] input = new Object[] { "0", 1, 2L, 3.1D, 4.2F, (byte) 5, '6' };

        assertThat(LogHelper.toString(input)).isEqualTo("[0, 1, 2, 3.1, 4.2, 5, 6]");
    }

    @Test
    public void toStringWithPrimitive() {
        assertThat(LogHelper.toString("0")).isEqualTo("0");
        assertThat(LogHelper.toString(1)).isEqualTo("1");
        assertThat(LogHelper.toString(2L)).isEqualTo("2");
        assertThat(LogHelper.toString(3.1D)).isEqualTo("3.1");
        assertThat(LogHelper.toString(4.2F)).isEqualTo("4.2");
        assertThat(LogHelper.toString((byte) 5)).isEqualTo("5");
        assertThat(LogHelper.toString('6')).isEqualTo("6");
    }

    @Test
    public void toStringWithNestedSmallArrays() {
        String[] strings = new String[] { "0" };
        int[] ints = new int[] { 1 };
        long[] longs = new long[] { 2L };
        double[] doubles = new double[] { 3.1D };
        float[] floats = new float[] { 4.2F };
        byte[] bytes = new byte[] { (byte) 5 };
        char[] chars = new char[] { '6' };

        Object[] input = new Object[] { strings, ints, longs, doubles, floats, bytes, chars };

        assertThat(LogHelper.toString(input)).isEqualTo("[[0], [1], [2], [3.1], [4.2], [5], [6]]");
    }
    
    @Test
    public void toStringWithNestedSmallCollections() {
        List<String> strings = Arrays.asList("0" );
        List<Integer> ints = Arrays.asList( 1 );
        List<Long> longs = Arrays.asList( 2L );
        List<Double> doubles = Arrays.asList( 3.1D );
        List<Float> floats = Arrays.asList( 4.2F );
        List<Byte> bytes = Arrays.asList( (byte) 5 );
        List<Character> chars = Arrays.asList( '6' );
        
        Object[] input = new Object[] { strings, ints, longs, doubles, floats, bytes, chars };
        
        assertThat(LogHelper.toString(input)).isEqualTo("[[0], [1], [2], [3.1], [4.2], [5], [6]]");
    }
    
    @Test
    public void toStringWithSmallArrays() {
        String[] strings = new String[] { "0" };
        int[] ints = new int[] { 1 };
        long[] longs = new long[] { 2L };
        double[] doubles = new double[] { 3.1D };
        float[] floats = new float[] { 4.2F };
        byte[] bytes = new byte[] { (byte) 5 };
        char[] chars = new char[] { '6' };

        assertThat(LogHelper.toString(strings)).isEqualTo("[0]");
        assertThat(LogHelper.toString(ints)).isEqualTo("[1]");
        assertThat(LogHelper.toString(longs)).isEqualTo("[2]");
        assertThat(LogHelper.toString(doubles)).isEqualTo("[3.1]");
        assertThat(LogHelper.toString(floats)).isEqualTo("[4.2]");
        assertThat(LogHelper.toString(bytes)).isEqualTo("[5]");
        assertThat(LogHelper.toString(chars)).isEqualTo("[6]");
    }
    
    @Test
    public void toStringWithSmallCollections() {
        List<String> strings = Collections.nCopies(1, "0");
        List<Integer> ints =  Collections.nCopies(1, 1);
        List<Long> longs =  Collections.nCopies(1, 2L);
        List<Double> doubles =  Collections.nCopies(1, 3.1D);
        List<Float> floats =  Collections.nCopies(1, 4.2F);
        List<Byte> bytes =  Collections.nCopies(1, (byte)5);
        List<Character> chars =  Collections.nCopies(1, '6');
        
        assertThat(LogHelper.toString(strings)).isEqualTo("[0]");
        assertThat(LogHelper.toString(ints)).isEqualTo("[1]");
        assertThat(LogHelper.toString(longs)).isEqualTo("[2]");
        assertThat(LogHelper.toString(doubles)).isEqualTo("[3.1]");
        assertThat(LogHelper.toString(floats)).isEqualTo("[4.2]");
        assertThat(LogHelper.toString(bytes)).isEqualTo("[5]");
        assertThat(LogHelper.toString(chars)).isEqualTo("[6]");
    }

    @Test
    public void toStringWithNestedBigArrays() {
        String[] strings = new String[15];
        Arrays.fill(strings, "0");
        int[] ints = new int[15];
        Arrays.fill(ints, 1);
        long[] longs = new long[15];
        Arrays.fill(longs, 2L);
        double[] doubles = new double[15];
        Arrays.fill(doubles, 3.1D);
        float[] floats = new float[15];
        Arrays.fill(floats, 4.2F);
        byte[] bytes = new byte[15];
        Arrays.fill(bytes, (byte) 5);
        char[] chars = new char[15];
        Arrays.fill(chars, '6');

        Object[] input = new Object[] { strings, ints, longs, doubles, floats, bytes, chars };
        StringBuilder sb = new StringBuilder();
        sb.append("[[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ...], ");
        sb.append("[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, ...], ");
        sb.append("[2, 2, 2, 2, 2, 2, 2, 2, 2, 2, ...], ");
        sb.append("[3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, ...], ");
        sb.append("[4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, ...], ");
        sb.append("[5, 5, 5, 5, 5, 5, 5, 5, 5, 5, ...], ");
        sb.append("[6, 6, 6, 6, 6, 6, 6, 6, 6, 6, ...]]");

        assertThat(LogHelper.toString(input)).isEqualTo(sb.toString());
    }
    
    @Test
    public void toStringWithNestedBigCollections() {
        List<String> strings = Collections.nCopies(15, "0");
        List<Integer> ints =  Collections.nCopies(15, 1);
        List<Long> longs =  Collections.nCopies(15, 2L);
        List<Double> doubles =  Collections.nCopies(15, 3.1D);
        List<Float> floats =  Collections.nCopies(15, 4.2F);
        List<Byte> bytes =  Collections.nCopies(15, (byte)5);
        List<Character> chars =  Collections.nCopies(15, '6');
        
        Object[] input = new Object[] { strings, ints, longs, doubles, floats, bytes, chars };
        StringBuilder sb = new StringBuilder();
        sb.append("[[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ...], ");
        sb.append("[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, ...], ");
        sb.append("[2, 2, 2, 2, 2, 2, 2, 2, 2, 2, ...], ");
        sb.append("[3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, ...], ");
        sb.append("[4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, ...], ");
        sb.append("[5, 5, 5, 5, 5, 5, 5, 5, 5, 5, ...], ");
        sb.append("[6, 6, 6, 6, 6, 6, 6, 6, 6, 6, ...]]");
        
        assertThat(LogHelper.toString(input)).isEqualTo(sb.toString());
    }

    @Test
    public void toStringWithBigArrays() {
        String[] strings = new String[15];
        Arrays.fill(strings, "0");
        int[] ints = new int[15];
        Arrays.fill(ints, 1);
        long[] longs = new long[15];
        Arrays.fill(longs, 2L);
        double[] doubles = new double[15];
        Arrays.fill(doubles, 3.1D);
        float[] floats = new float[15];
        Arrays.fill(floats, 4.2F);
        byte[] bytes = new byte[15];
        Arrays.fill(bytes, (byte) 5);
        char[] chars = new char[15];
        Arrays.fill(chars, '6');

        assertThat(LogHelper.toString(strings)).isEqualTo("[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ...]");
        assertThat(LogHelper.toString(ints)).isEqualTo("[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, ...]");
        assertThat(LogHelper.toString(longs)).isEqualTo("[2, 2, 2, 2, 2, 2, 2, 2, 2, 2, ...]");
        assertThat(LogHelper.toString(doubles)).isEqualTo("[3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, ...]");
        assertThat(LogHelper.toString(floats)).isEqualTo("[4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, ...]");
        assertThat(LogHelper.toString(bytes)).isEqualTo("[5, 5, 5, 5, 5, 5, 5, 5, 5, 5, ...]");
        assertThat(LogHelper.toString(chars)).isEqualTo("[6, 6, 6, 6, 6, 6, 6, 6, 6, 6, ...]");
    }
    
    @Test
    public void toStringWithBigCollections() {
        List<String> strings = Collections.nCopies(15, "0");
        List<Integer> ints =  Collections.nCopies(15, 1);
        List<Long> longs =  Collections.nCopies(15, 2L);
        List<Double> doubles =  Collections.nCopies(15, 3.1D);
        List<Float> floats =  Collections.nCopies(15, 4.2F);
        List<Byte> bytes =  Collections.nCopies(15, (byte)5);
        List<Character> chars =  Collections.nCopies(15, '6');
        
        assertThat(LogHelper.toString(strings)).isEqualTo("[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ...]");
        assertThat(LogHelper.toString(ints)).isEqualTo("[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, ...]");
        assertThat(LogHelper.toString(longs)).isEqualTo("[2, 2, 2, 2, 2, 2, 2, 2, 2, 2, ...]");
        assertThat(LogHelper.toString(doubles)).isEqualTo("[3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, ...]");
        assertThat(LogHelper.toString(floats)).isEqualTo("[4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, ...]");
        assertThat(LogHelper.toString(bytes)).isEqualTo("[5, 5, 5, 5, 5, 5, 5, 5, 5, 5, ...]");
        assertThat(LogHelper.toString(chars)).isEqualTo("[6, 6, 6, 6, 6, 6, 6, 6, 6, 6, ...]");
    }

    @Test
    public void toStringWithSmallString() {
        char[] charsForStr = new char[100];
        Arrays.fill(charsForStr, '7');
        String string = new String(charsForStr);

        assertThat(LogHelper.toString(string)).isEqualTo(string);
    }

    @Test
    public void toStringWithBigString() {
        char[] charsForStr = new char[1500];
        Arrays.fill(charsForStr, '7');
        String string = new String(charsForStr);

        assertThat(LogHelper.toString(string)).isEqualTo(string.substring(0, 1000) + "...");
    }
}
