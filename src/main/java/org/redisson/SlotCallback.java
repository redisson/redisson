package org.redisson;

public interface SlotCallback<T, R> {

    void onSlotResult(T result);

    R onFinish();

}
