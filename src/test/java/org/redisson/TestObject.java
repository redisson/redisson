package org.redisson;

public class TestObject implements Comparable<TestObject> {

    private String name;
    private String value;

    public TestObject() {
    }

    public TestObject(String name, String value) {
        super();
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int compareTo(TestObject o) {
        int res = name.compareTo(o.name);
        if (res == 0) {
            return value.compareTo(o.value);
        }
        return res;
    }



}
