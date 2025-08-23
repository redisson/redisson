package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RVectorSet;
import org.redisson.api.vector.QuantizationType;
import org.redisson.api.vector.VectorAddArgs;
import org.redisson.api.vector.VectorInfo;
import org.redisson.api.vector.VectorSimilarArgs;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.ScoredEntry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonVectorSetTest extends RedisDockerTest {

    private RVectorSet vectorSet;
    private static final String TEST_SET_NAME = "test-vector-set";
    private static final String ELEMENT_A = "A";
    private static final String ELEMENT_B = "B";
    private static final String ELEMENT_C = "C";
    private static final String ELEMENT_D = "D";
    private static final String ELEMENT_E = "E";

    @BeforeEach
    public void setUp() {
        vectorSet = redisson.getVectorSet(TEST_SET_NAME);
        vectorSet.delete();

        vectorSet.add(VectorAddArgs.element(ELEMENT_A).vector(1.0, 1.0));
        vectorSet.add(VectorAddArgs.element(ELEMENT_B).vector(-1.0, -1.0));
        vectorSet.add(VectorAddArgs.element(ELEMENT_C).vector(-1.0, 1.0));
        vectorSet.add(VectorAddArgs.element(ELEMENT_D).vector(1.0, -1.0));
    }

    @Test
    public void testAdd() {
        boolean result = vectorSet.add(VectorAddArgs.element(ELEMENT_E).vector(1D, 0D));
        assertThat(result).isTrue();

        List<Double> vectors1 = vectorSet.getVector(ELEMENT_E);
        assertThat(vectors1.stream().map(v -> Math.round(v))).containsOnlyOnce(1L, 0L);

        boolean replaceResult = vectorSet.add(VectorAddArgs.element(ELEMENT_E).vector(4D, 10D));
        assertThat(replaceResult).isFalse();

        List<Double> vectors2 = vectorSet.getVector(ELEMENT_E);
        assertThat(vectors2.stream().map(v -> Math.round(v))).containsOnlyOnce(10L, 4L);

        assertThat(vectorSet.size()).isEqualTo(5);
    }

    @Test
    public void testSize() {
        assertThat(vectorSet.size()).isEqualTo(4);

        vectorSet.add(VectorAddArgs.element(ELEMENT_E).vector(1.0, 0.0));
        assertThat(vectorSet.size()).isEqualTo(5);

        vectorSet.remove(ELEMENT_E);
        assertThat(vectorSet.size()).isEqualTo(4);
    }

    @Test
    public void testDimensions() {
        assertThat(vectorSet.dimensions()).isEqualTo(2);

        RVectorSet highDimSet = redisson.getVectorSet("high-dim-set");
        highDimSet.add(VectorAddArgs.element("high-dim").vector(1.0, 2.0, 3.0, 4.0, 5.0));

        assertThat(highDimSet.dimensions()).isEqualTo(5);
    }

    @Test
    public void testGetVector() {
        List<Double> vectorA = vectorSet.getVector(ELEMENT_A);

        assertThat(vectorA).hasSize(2);
        assertThat(vectorA.get(0)).isCloseTo(1.0, offset(0.1));
        assertThat(vectorA.get(1)).isCloseTo(1.0, offset(0.1));

        List<Double> nonExistentVector = vectorSet.getVector("NON_EXISTENT");
        assertThat(nonExistentVector).isEmpty();
    }

    @Test
    public void testGetRawVector() {
        RVectorSet vectorSet = redisson.getVectorSet("raw-vectors");
        vectorSet.add(VectorAddArgs.element("high-dim").vector(1.0, 2.0, 3.0, 4.0, 5.0));

        List<Object> rawVectorA = vectorSet.getRawVector("high-dim");

        assertThat(rawVectorA).hasSize(4);

        List<Object> nonExistentRawVector = vectorSet.getRawVector("NON_EXISTENT");
        assertThat(nonExistentRawVector).isEmpty();
    }

    @Test
    public void testGetAndSetAttributes() {
        TestAttributes attrs = new TestAttributes("test value", 42);

        boolean setResult = vectorSet.setAttributes(ELEMENT_A, attrs);
        assertThat(setResult).isTrue();

        TestAttributes retrievedAttrs = vectorSet.getAttributes(ELEMENT_A, TestAttributes.class);
        assertThat(retrievedAttrs.getStringValue()).isEqualTo("test value");
        assertThat(retrievedAttrs.getIntValue()).isEqualTo(42);

        TestAttributes attrs2 = new TestAttributes("test 2", 52);
        boolean r2 = vectorSet.setAttributes(ELEMENT_A, attrs2);
        assertThat(r2).isTrue();

        TestAttributes retrievedAttrs2 = vectorSet.getAttributes(ELEMENT_A, TestAttributes.class);
        assertThat(retrievedAttrs2.getStringValue()).isEqualTo("test 2");
        assertThat(retrievedAttrs2.getIntValue()).isEqualTo(52);

        boolean nonExistentSetResult = vectorSet.setAttributes("NON_EXISTENT", attrs);
        assertThat(nonExistentSetResult).isFalse();

        TestAttributes nonExistentAttrs = vectorSet.getAttributes("NON_EXISTENT", TestAttributes.class);
        assertThat(nonExistentAttrs).isNull();
    }

    @Test
    public void testGetInfo() {
        VectorInfo info = vectorSet.getInfo();
        assertThat(info.getDimensions()).isEqualTo(2);
        assertThat(info.getSize()).isEqualTo(4);
        assertThat(info.getQuantizationType()).isEqualTo(QuantizationType.Q8);

        RVectorSet vectorSet2 = redisson.getVectorSet("test2");
        vectorSet2.add(VectorAddArgs.element("G")
                .vector(0.7, 0.7, 0.4)
                .quantization(QuantizationType.BIN)
                .explorationFactor(5));

        VectorInfo info2 = vectorSet2.getInfo();
        assertThat(info2.getDimensions()).isEqualTo(3);
        assertThat(info2.getQuantizationType()).isEqualTo(QuantizationType.BIN);
        assertThat(info2.getSize()).isEqualTo(1);

        RVectorSet vectorSet3 = redisson.getVectorSet("test3");
        vectorSet3.add(VectorAddArgs.element("G")
                .vector(0.7, 0.7, 0.4)
                .quantization(QuantizationType.NOQUANT)
                .explorationFactor(5));

        VectorInfo info3 = vectorSet3.getInfo();
        assertThat(info3.getDimensions()).isEqualTo(3);
        assertThat(info3.getQuantizationType()).isEqualTo(QuantizationType.NOQUANT);
        assertThat(info3.getSize()).isEqualTo(1);

    }

    @Test
    public void testGetNeighbors() {
        
        vectorSet.add(VectorAddArgs.element(ELEMENT_E).vector(1.1, 1.1));

        
        List<String> neighbors = vectorSet.getNeighbors(ELEMENT_A);

        
        assertThat(neighbors).isNotEmpty();

        
        assertThat(neighbors).contains(ELEMENT_E);

        
        List<String> nonExistentNeighbors = vectorSet.getNeighbors("NON_EXISTENT");
        assertThat(nonExistentNeighbors).isEmpty();
    }

    @Test
    public void testGetNeighborEntries() {
        
        vectorSet.add(VectorAddArgs.element(ELEMENT_E).vector(1.1, 1.1));

        
        List<ScoredEntry<String>> neighborEntries = vectorSet.getNeighborEntries(ELEMENT_A);

        
        assertThat(neighborEntries).isNotEmpty();

        
        boolean hasElementE = false;
        for (ScoredEntry<String> entry : neighborEntries) {
            if (entry.getValue().equals(ELEMENT_E)) {
                hasElementE = true;
                
                assertThat(entry.getScore()).isBetween(0.0, 1.0);
                break;
            }
        }
        assertThat(hasElementE).isTrue();

        
        List<ScoredEntry<String>> nonExistentNeighborEntries = vectorSet.getNeighborEntries("NON_EXISTENT");
        assertThat(nonExistentNeighborEntries).isEmpty();
    }

    @Test
    public void testRandom() {
        
        String randomElement = vectorSet.random();

        
        assertThat(randomElement).isIn(ELEMENT_A, ELEMENT_B, ELEMENT_C, ELEMENT_D);

        
        for (String element : vectorSet.random(100)) {
            vectorSet.remove(element);
        }

        
        String emptyRandom = vectorSet.random();
        assertThat(emptyRandom).isNull();

        
        vectorSet.add(VectorAddArgs.element(ELEMENT_A).vector(1.0, 1.0));
        vectorSet.add(VectorAddArgs.element(ELEMENT_B).vector(-1.0, -1.0));
        vectorSet.add(VectorAddArgs.element(ELEMENT_C).vector(-1.0, 1.0));
        vectorSet.add(VectorAddArgs.element(ELEMENT_D).vector(1.0, -1.0));
    }

    @Test
    public void testRandomMultiple() {
        
        List<String> randomElements = vectorSet.random(3);

        
        assertThat(randomElements).hasSizeLessThanOrEqualTo(3);

        
        for (String element : randomElements) {
            assertThat(element).isIn(ELEMENT_A, ELEMENT_B, ELEMENT_C, ELEMENT_D);
        }

        
        List<String> allElements = vectorSet.random(10);
        assertThat(allElements).hasSize(4); 

        
        List<String> zeroElements = vectorSet.random(0);
        assertThat(zeroElements).isEmpty();
    }

    @Test
    public void testRemove() {
        
        boolean removeResult = vectorSet.remove(ELEMENT_A);
        assertThat(removeResult).isTrue();

        
        assertThat(vectorSet.size()).isEqualTo(3);

        
        List<Double> vectorA = vectorSet.getVector(ELEMENT_A);
        assertThat(vectorA).isEmpty();

        
        boolean nonExistentRemove = vectorSet.remove("NON_EXISTENT_ELEMENT");
        assertThat(nonExistentRemove).isFalse();

        
        vectorSet.add(VectorAddArgs.element(ELEMENT_A).vector(1.0, 1.0));
    }

    @Test
    public void testGetSimilar() {
        
        List<String> similarToA = vectorSet.getSimilar(VectorSimilarArgs.element(ELEMENT_A).count(2));

        
        assertThat(similarToA).hasSizeLessThanOrEqualTo(2);

        
        List<String> similarToVector = vectorSet.getSimilar(VectorSimilarArgs.vector(1.0, 1.0).count(2));

        
        assertThat(similarToVector).hasSizeLessThanOrEqualTo(2);

        
        assertThat(similarToVector).contains(ELEMENT_A);

        Assertions.assertThrows(RedisException.class, () -> {
            List<String> nonExistentSimilar = vectorSet.getSimilar(VectorSimilarArgs.element("NON_EXISTENT").count(2));
            assertThat(nonExistentSimilar).isEmpty();
        });
    }

    @Test
    public void testGetSimilarEntries() {
        
        List<ScoredEntry<String>> similarToA = vectorSet.getSimilarEntries(VectorSimilarArgs.element(ELEMENT_A).count(2));

        
        assertThat(similarToA).hasSizeLessThanOrEqualTo(2);

        
        List<ScoredEntry<String>> similarToVector = vectorSet.getSimilarEntries(VectorSimilarArgs.vector(1.0, 1.0).count(2));

        
        assertThat(similarToVector).hasSizeLessThanOrEqualTo(2);

        
        boolean hasElementA = false;
        for (ScoredEntry<String> entry : similarToVector) {
            if (entry.getValue().equals(ELEMENT_A)) {
                hasElementA = true;
                assertThat(entry.getScore()).isCloseTo(1.0, offset(0.1));
                break;
            }
        }
        assertThat(hasElementA).isTrue();

        Assertions.assertThrows(RedisException.class, () -> {
            vectorSet.getSimilarEntries(VectorSimilarArgs.element("NON_EXISTENT").count(2));
        });
    }

    @Test
    public void testAddWithAttributes() {
        
        TestAttributes attrs = new TestAttributes("test attribute", 100);

        boolean result = vectorSet.add(VectorAddArgs.element("F").vector(0.5, 0.5).attributes(attrs));
        assertThat(result).isTrue();

        
        TestAttributes retrievedAttrs = vectorSet.getAttributes("F", TestAttributes.class);
        assertThat(retrievedAttrs.getStringValue()).isEqualTo("test attribute");
        assertThat(retrievedAttrs.getIntValue()).isEqualTo(100);
    }

    @Test
    public void testAddWithQuantization() {
        
        boolean result = vectorSet.add(VectorAddArgs.element("G")
                .vector(0.7, 0.7)
                .quantization(QuantizationType.Q8)
                .explorationFactor(5));

        assertThat(result).isTrue();

        
        assertThat(vectorSet.getVector("G")).hasSize(2);
        assertThat(vectorSet.getVector("АА")).isEmpty();
    }

    @Test
    public void testGetSimilarWithFiltering() {
        
        TestAttributes attrs1 = new TestAttributes("category1", 2000);
        TestAttributes attrs2 = new TestAttributes("category2", 1980);

        vectorSet.add(VectorAddArgs.element("F1").vector(0.5, 0.5).attributes(attrs1));
        vectorSet.add(VectorAddArgs.element("F2").vector(0.6, 0.6).attributes(attrs2));

        
        List<String> filteredResults = vectorSet.getSimilar(
                VectorSimilarArgs.vector(0.5, 0.5)
                        .count(5)
                        .filter(".intValue > 1990")
        );

        
        assertThat(filteredResults).contains("F1");
        assertThat(filteredResults).doesNotContain("F2");
    }

    @Test
    public void testAddInvalidDimension() {
        assertThrows(RedisException.class, () -> {
            vectorSet.add(VectorAddArgs.element("invalid-dim").vector(1.0, 2.0, 3.0));
        });
    }

    @Test
    public void testGetSimilarWithLinearScan() {
        List<String> linearScanResults = vectorSet.getSimilar(
                VectorSimilarArgs.element(ELEMENT_A)
                        .count(2)
                        .useLinearScan()
        );

        assertThat(linearScanResults).isNotEmpty();
    }

    @Test
    public void testAddWithReduceOption() {
        RVectorSet vectorSet = redisson.getVectorSet("test2");
        boolean initResult = vectorSet.add(VectorAddArgs.element("FirstElement")
                .vector(1.0, 1.0, 1.0, 1.0)
                .reduce(2));
        assertThat(initResult).isTrue();

        boolean result = vectorSet.add(VectorAddArgs.element("H")
                .vector(0.8, 0.8, 0.8, 0.8)
                .reduce(2));
        assertThat(result).isTrue();

        assertThat(vectorSet.getVector("H")).hasSize(2);
    }

    @Test
    public void testGetSimilarWithEffort() {
        List<ScoredEntry<String>> results = vectorSet.getSimilarEntries(
                VectorSimilarArgs.vector(1.0, 1.0)
                        .count(2)
                        .explorationFactor(10)
        );

        assertThat(results).isNotEmpty();
        assertThat(results.size()).isLessThanOrEqualTo(2);
    }

    @Test
    public void testGetSimilarWithMainThread() {
        
        List<String> results = vectorSet.getSimilar(
                VectorSimilarArgs.element(ELEMENT_A)
                        .count(2)
                        .useMainThread()
        );

        assertThat(results).isNotEmpty();
    }

    @Test
    public void testAddWithCheckAndSet() {
        
        boolean result = vectorSet.add(VectorAddArgs.element("CAS")
                .vector(0.3, 0.3)
                .useCheckAndSet());

        assertThat(result).isTrue();

        
        List<Double> vector = vectorSet.getVector("CAS");
        assertThat(vector).hasSize(2);
    }

    
    public static class TestAttributes {
        private String stringValue;
        private int intValue;

        
        public TestAttributes() {
        }

        public TestAttributes(String stringValue, int intValue) {
            this.stringValue = stringValue;
            this.intValue = intValue;
        }

        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }

        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }
    }
}
