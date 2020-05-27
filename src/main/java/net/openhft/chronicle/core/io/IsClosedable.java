/*
 * Copyright (c) 2016-2019 Chronicle Software Ltd
 */

package net.openhft.chronicle.core.io;

import net.openhft.chronicle.core.StackTrace;

public interface IsClosedable {
    default void checkIsNotClosed() {
        if (isClosed())
            throw new IllegalStateException("Closed", closedHere());
    }

    default boolean isClosed() {
        throw new UnsupportedOperationException();
    }

    default StackTrace closedHere() {
        return null;
    }
}
