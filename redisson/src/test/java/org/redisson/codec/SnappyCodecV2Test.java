package org.redisson.codec;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;

public class SnappyCodecV2Test {

    @Test
    public void test() throws IOException {
        SnappyCodecV2 c = new SnappyCodecV2();
        List<TestObject> list = getData();
        ByteBuf t = c.getValueEncoder().encode(list);
        Object decodedList = c.getValueDecoder().decode(t, null);
        Assert.assertEquals(list, decodedList);
    }

    private List<TestObject> getData() {
        TestObject testObject1 = new TestObject();
        testObject1.setField0(1010);
        TestObject testObject2 = new TestObject();
        testObject2.setField0(1010);
        TestObject testObject3 = new TestObject();
        testObject3.setField0(1010);

        List<TestObject> partnerFundSourceList = new ArrayList<TestObject>();
        partnerFundSourceList.add(testObject1);
        partnerFundSourceList.add(testObject2);
        partnerFundSourceList.add(testObject3);

        return partnerFundSourceList;
    }

}

class TestObject implements Serializable
{

    private static final long serialVersionUID = 155311581144181716L;

    private long field0;
    private long field1;
    private int field2;
    private int field3;
    private int field4;

    public int getField3()
    {
        return field3;
    }

    public void setField3(int allowBisunessAcct)
    {
        this.field3 = allowBisunessAcct;
    }

    public int getField2()
    {
        return field2;
    }

    public void setField2(int fundSrcSubType)
    {
        this.field2 = fundSrcSubType;
    }

    public long getField0()
    {
        return field0;
    }

    public void setField0(long partnerId)
    {
        this.field0 = partnerId;
    }

    public long getField1()
    {
        return field1;
    }

    public void setField1(long productId)
    {
        this.field1 = productId;
    }

    public int getField4()
    {
        return field4;
    }

    public void setField4(int txnPymntSchldType)
    {
        this.field4 = txnPymntSchldType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (field0 ^ (field0 >>> 32));
        result = prime * result + (int) (field1 ^ (field1 >>> 32));
        result = prime * result + field2;
        result = prime * result + field3;
        result = prime * result + field4;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TestObject other = (TestObject) obj;
        if (field0 != other.field0)
            return false;
        if (field1 != other.field1)
            return false;
        if (field2 != other.field2)
            return false;
        if (field3 != other.field3)
            return false;
        if (field4 != other.field4)
            return false;
        return true;
    }

}
