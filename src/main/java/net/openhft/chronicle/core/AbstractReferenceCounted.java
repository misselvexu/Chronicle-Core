/*
 * Copyright (c) 2016-2019 Chronicle Software Ltd
 */

package net.openhft.chronicle.core;

import net.openhft.chronicle.core.io.AbstractCloseable;

public abstract class AbstractReferenceCounted extends AbstractCloseable implements ReferenceCounted {
    private final ReferenceCounted referenceCounted = ReferenceCounter.onReleased(this::performRelease);

    protected void performRelease() {
        referenceCounted.checkReferences();
    }

    @Override
    public void reserve(ReferenceOwner id) throws IllegalStateException {
        referenceCounted.reserve(id);
    }

    @Override
    public void release(ReferenceOwner id) throws IllegalStateException {
        referenceCounted.release(id);
    }

    @Override
    public void releaseLast(ReferenceOwner id) {
        referenceCounted.releaseLast(id);
    }

    @Override
    public boolean tryReserve(ReferenceOwner id) {
        return referenceCounted.tryReserve(id);
    }

    @Override
    public int refCount() {
        return referenceCounted.refCount();
    }

    @Override
    public void checkReferences() {
        referenceCounted.checkReferences();
    }

    protected void checkFinalize() {
        if (refCount() > 0)
            Jvm.warn().on(getClass(), "Discarded without being released", createdHere);
    }
}
