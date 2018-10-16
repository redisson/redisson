package org.redisson.codec;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;

public class SnappyCodecV2Test {

    @Test
    public void test2() throws IOException {
        SnappyCodecV2 c = new SnappyCodecV2();
        TestObject2 list = getObject();
        ByteBuf t = c.getValueEncoder().encode(list);
        c.getValueDecoder().decode(t, null);
    }

    private TestObject2 getObject() {
        TestObject2 partnerDetails = new TestObject2();
        return partnerDetails;
    }
    
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

class TestObject2 implements Serializable
{
    private static final long serialVersionUID = 6607118534080094372L;

    private String field1;
    private String field2;
    private String field3;

    private String field4;

    private long field5;
    private long field6;

    private int field7;
    private int field8;

    private int field9;
    private int field10;
    private int field11;
    private int field12;
    private int[] field13;
    private int field14;

    private byte[] field15;
    private byte[] field16;
    private byte[] field17;
    private byte[] field18;
    private String field19;
    private String field20;
    private String field21;
    private String field22;
    private int field23;

    private String field24;
    private String field25;
    private String field26;
    private String field27;
    private long field28;
    private String field29;
    private String field30;
    private int field31;
    private long field32;

    private String field33;
    private String field34;

    private boolean field35;

    public boolean isField35()
    {
        return field35;
    }

    public void setField35(boolean field35)
    {
        this.field35 = field35;
    }

    /**
     * @return the addressId
     */
    public long getField32()
    {
        return field32;
    }

    /**
     * @param addressId
     *            the addressId to set
     */
    public void setField32(long field32)
    {
        this.field32 = field32;
    }

    /**
     * @return the industryType
     */
    public int getField31()
    {
        return field31;
    }

    /**
     * @param industryType
     *            the industryType to set
     */
    public void setField31(int field31)
    {
        this.field31 = field31;
    }

    /**
     * @return the federalEin
     */
    public String getField30()
    {
        return field30;
    }

    /**
     * @param federalEin
     *            the federalEin to set
     */
    public void setField30(String field30)
    {
        this.field30 = field30;
    }

    public String getField29()
    {
        return field29;
    }

    public void setField29(String field29)
    {
        this.field29 = field29;
    }

    public String getField24()
    {
        return field24;
    }

    public void setField24(String field24)
    {
        this.field24 = field24;
    }

    public String getField25()
    {
        return field25;
    }

    public void setField25(String field25)
    {
        this.field25 = field25;
    }

    public String getField26()
    {
        return field26;
    }

    public void setField26(String field26)
    {
        this.field26 = field26;
    }

    public long getField28()
    {
        return field28;
    }

    public void setField28(long field28)
    {
        this.field28 = field28;
    }

    public String getField27()
    {
        return field27;
    }

    public void setField27(String field27)
    {
        this.field27 = field27;
    }

    public int getField9()
    {
        return field9;
    }

    public void setField9(int field9)
    {
        this.field9 = field9;
    }

    public long getField6()
    {
        return field6;
    }

    public void setField6(long field6)
    {
        this.field6 = field6;
    }

    public String getField4()
    {
        return field4;
    }

    public void setField4(String field4)
    {
        this.field4 = field4;
    }

    public String getField1()
    {
        return field1;
    }

    public void setField1(String field1)
    {
        this.field1 = field1;
    }

    public int[] getField13()
    {
        return field13;
    }

    public void setField13(int[] field13)
    {
        this.field13 = field13;
    }

    public byte[] getField16()
    {
        return field16;
    }

    public void setField16(byte[] field16)
    {
        this.field16 = field16;
    }

    public String getField19()
    {
        return field19;
    }

    public void setField19(String field19)
    {
        this.field19 = field19;
    }

    public byte[] getField18()
    {
        return field18;
    }

    public void setField18(byte[] field18)
    {
        this.field18 = field18;
    }

    public String getField21()
    {
        return field21;
    }

    public void setField21(String field21)
    {
        this.field21 = field21;
    }

    public byte[] getField15()
    {
        return field15;
    }

    public void setField15(byte[] field15)
    {
        this.field15 = field15;
    }

    public String getField20()
    {
        return field20;
    }

    public void setField20(String field20)
    {
        this.field20 = field20;
    }

    public byte[] getField17()
    {
        return field17;
    }

    public void setField17(byte[] field17)
    {
        this.field17 = field17;
    }

    public String getField22()
    {
        return field22;
    }

    public void setField22(String field22)
    {
        this.field22 = field22;
    }

    public int getField10()
    {
        return field10;
    }

    public void setField10(int field10)
    {
        this.field10 = field10;
    }

    public int getField23()
    {
        return field23;
    }

    public void setField23(int field23)
    {
        this.field23 = field23;
    }

    public int getField12()
    {
        return field12;
    }

    public void setField12(int field12)
    {
        this.field12 = field12;
    }

    public int getField11()
    {
        return field11;
    }

    public void setField11(int field11)
    {
        this.field11 = field11;
    }

    /**
     * @return the csrContactNo
     */
    public String getField33()
    {
        return field33;
    }

    /**
     * @param csrContactNo
     *            the csrContactNo to set
     */
    public void setField33(String field33)
    {
        this.field33 = field33;
    }

    /**
     * @return the partnerSiteUrl
     */
    public String getField34()
    {
        return field34;
    }

    /**
     * @param partnerSiteUrl
     *            the partnerSiteUrl to set
     */
    public void setField34(String field34)
    {
        this.field34 = field34;
    }

    /**
     * @return the partnerId
     */
    public long getField5()
    {
        return field5;
    }

    /**
     * @param partnerId
     *            the partnerId to set
     */
    public void setField5(long field5)
    {
        this.field5 = field5;
    }

    public int getField7()
    {
        return field7;
    }

    public void setField7(int field7)
    {
        this.field7 = field7;
    }

    /**
     * @return the clientId
     */
    public int getField8()
    {
        return field8;
    }

    /**
     * @param clientId
     *            the clientId to set
     */
    public void setField8(int field8)
    {
        this.field8 = field8;
    }

    public int getField14()
    {
        return field14;
    }

    public void setField14(int field14)
    {
        this.field14 = field14;
    }

    public String getField2()
    {
        return field2;
    }

    public void setField2(String field2)
    {
        this.field2 = field2;
    }

    public String getField3()
    {
        return field3;
    }

    public void setField3(String field3)
    {
        this.field3 = field3;
    }

    public String getCustRegFieldIdsString()
    {
        if (field13 != null && field13.length > 0)
            return Arrays.toString(field13);

        return "";
    }

    public String getByteStringVal(byte[] byteArray)
    {
        if (byteArray != null && byteArray.length > 0)
            return Arrays.toString(byteArray);

        return "";
    }
}

