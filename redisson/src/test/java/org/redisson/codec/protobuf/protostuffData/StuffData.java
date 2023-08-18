package org.redisson.codec.protobuf.protostuffData;

import java.util.List;
import java.util.Objects;

public class StuffData {
    private String name;
    private int age;
    private List<String> hobbies;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public List<String> getHobbies() {
        return hobbies;
    }

    public void setHobbies(List<String> hobbies) {
        this.hobbies = hobbies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StuffData stuffData = (StuffData) o;

        if (age != stuffData.age) return false;
        if (!Objects.equals(name, stuffData.name)) return false;
        return Objects.equals(hobbies, stuffData.hobbies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, hobbies);
    }
}
