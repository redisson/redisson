package org.redisson.misc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * @author Philipp Marx
 */
public class LogHelperTest {

    @Test
    public void toStringWithNull() {
        assertThat(LogHelper.toString(null), is("null"));
    }

    @Test
    public void toStringWithNestedPrimitives() {
        Object[] input = new Object[] { "0", 1, 2L, 3.1D, 4.2F, (byte) 5, '6' };

        assertThat(LogHelper.toString(input), is("[0, 1, 2, 3.1, 4.2, 5, 6]"));
    }

    @Test
    public void toStringWithPrimitive() {
        assertThat(LogHelper.toString("0"), is("0"));
        assertThat(LogHelper.toString(1), is("1"));
        assertThat(LogHelper.toString(2L), is("2"));
        assertThat(LogHelper.toString(3.1D), is("3.1"));
        assertThat(LogHelper.toString(4.2F), is("4.2"));
        assertThat(LogHelper.toString((byte) 5), is("5"));
        assertThat(LogHelper.toString('6'), is("6"));
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

        assertThat(LogHelper.toString(input), is("[[0], [1], [2], [3.1], [4.2], [5], [6]]"));
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
        
        assertThat(LogHelper.toString(input), is("[[0], [1], [2], [3.1], [4.2], [5], [6]]"));
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

        assertThat(LogHelper.toString(strings), is("[0]"));
        assertThat(LogHelper.toString(ints), is("[1]"));
        assertThat(LogHelper.toString(longs), is("[2]"));
        assertThat(LogHelper.toString(doubles), is("[3.1]"));
        assertThat(LogHelper.toString(floats), is("[4.2]"));
        assertThat(LogHelper.toString(bytes), is("[5]"));
        assertThat(LogHelper.toString(chars), is("[6]"));
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
        
        assertThat(LogHelper.toString(strings), is("[0]"));
        assertThat(LogHelper.toString(ints), is("[1]"));
        assertThat(LogHelper.toString(longs), is("[2]"));
        assertThat(LogHelper.toString(doubles), is("[3.1]"));
        assertThat(LogHelper.toString(floats), is("[4.2]"));
        assertThat(LogHelper.toString(bytes), is("[5]"));
        assertThat(LogHelper.toString(chars), is("[6]"));
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

        assertThat(LogHelper.toString(input), is(sb.toString()));
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
        
        assertThat(LogHelper.toString(input), is(sb.toString()));
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

        assertThat(LogHelper.toString(strings), is("[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ...]"));
        assertThat(LogHelper.toString(ints), is("[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, ...]"));
        assertThat(LogHelper.toString(longs), is("[2, 2, 2, 2, 2, 2, 2, 2, 2, 2, ...]"));
        assertThat(LogHelper.toString(doubles), is("[3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, ...]"));
        assertThat(LogHelper.toString(floats), is("[4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, ...]"));
        assertThat(LogHelper.toString(bytes), is("[5, 5, 5, 5, 5, 5, 5, 5, 5, 5, ...]"));
        assertThat(LogHelper.toString(chars), is("[6, 6, 6, 6, 6, 6, 6, 6, 6, 6, ...]"));
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
        
        assertThat(LogHelper.toString(strings), is("[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ...]"));
        assertThat(LogHelper.toString(ints), is("[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, ...]"));
        assertThat(LogHelper.toString(longs), is("[2, 2, 2, 2, 2, 2, 2, 2, 2, 2, ...]"));
        assertThat(LogHelper.toString(doubles), is("[3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, ...]"));
        assertThat(LogHelper.toString(floats), is("[4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, ...]"));
        assertThat(LogHelper.toString(bytes), is("[5, 5, 5, 5, 5, 5, 5, 5, 5, 5, ...]"));
        assertThat(LogHelper.toString(chars), is("[6, 6, 6, 6, 6, 6, 6, 6, 6, 6, ...]"));
    }

    @Test
    public void toStringWithSmallString() {
        char[] charsForStr = new char[100];
        Arrays.fill(charsForStr, '7');
        String string = new String(charsForStr);

        assertThat(LogHelper.toString(string), is(string));
    }

    @Test
    public void toStringWithBigString() {
        char[] charsForStr = new char[150];
        Arrays.fill(charsForStr, '7');
        String string = new String(charsForStr);

        assertThat(LogHelper.toString(string), is(string.substring(0, 100) + "..."));
    }
}
