package org.redisson;

public class TestObject {

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

}
