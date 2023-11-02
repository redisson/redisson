package org.redisson;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class VoidSlotCallback implements SlotCallback<Void, Void> {

    private final AtomicBoolean r = new AtomicBoolean();

    private final Object[] params;

    public VoidSlotCallback() {
        this(null);
    }

    public VoidSlotCallback(Object[] params) {
        this.params = params;
    }

    @Override
    public void onSlotResult(Void res) {
    }

    @Override
    public Void onFinish() {
        return null;
    }

    @Override
    public Object[] createParams(List<Object> params) {
        if (this.params != null) {
            return this.params;
        }
        return SlotCallback.super.createParams(params);
    }
}
