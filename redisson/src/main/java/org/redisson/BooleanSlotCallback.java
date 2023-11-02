package org.redisson;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class BooleanSlotCallback implements SlotCallback<Boolean, Boolean> {

    private final AtomicBoolean r = new AtomicBoolean();

    private final Object[] params;

    public BooleanSlotCallback() {
        this(null);
    }

    public BooleanSlotCallback(Object[] params) {
        this.params = params;
    }

    @Override
    public void onSlotResult(Boolean res) {
        if (res) {
            r.set(true);
        }
    }

    @Override
    public Boolean onFinish() {
        return r.get();
    }

    @Override
    public Object[] createParams(List<Object> params) {
        if (params == null && this.params != null) {
            return this.params;
        }
        return SlotCallback.super.createParams(params);
    }
}
