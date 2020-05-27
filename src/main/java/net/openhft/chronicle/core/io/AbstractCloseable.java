/*
 * Copyright 2016-2020 Chronicle Software
 *
 * https://chronicle.software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.core.io;

import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.core.StackTrace;

public abstract class AbstractCloseable implements Closeable {

    protected transient final StackTrace createdHere;
    private transient volatile StackTrace closedHere;
    protected transient volatile boolean closed;

    public AbstractCloseable() {
        createdHere = Jvm.isResourceTracing() ? new StackTrace("Created here") : null;
    }

    @Override
    public void close() {
        closed = true;
        closedHere = Jvm.isResourceTracing() ? new StackTrace("Closed Here") : null;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public StackTrace closedHere() {
        return closedHere;
    }

    protected void resetClosed() {
        closed = false;
        closedHere = null;
    }

    protected void checkFinalize() {
        if (closed)
            Jvm.warn().on(getClass(), "Discarded without being released", createdHere);
    }
}
