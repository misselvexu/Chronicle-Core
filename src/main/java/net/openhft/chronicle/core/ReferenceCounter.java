/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.core;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

public final class ReferenceCounter implements ReferenceCounted {

    private final AtomicInteger value = new AtomicInteger(1);
    private final Runnable onRelease;

    ReferenceCounter(final Runnable onRelease) {
        this.onRelease = onRelease;
    }

    @NotNull
    public static ReferenceCounted onReleased(final Runnable onRelease) {
        return Jvm.isReferenceTracing()
                ? new TracingReferenceCounter(onRelease)
                : new ReferenceCounter(onRelease);
    }

    @Override
    public void reserve(ReferenceOwner id) throws IllegalStateException {
        for (; ; ) {

            int v = value.get();
            if (v <= 0) {
                throw new IllegalStateException("Released");
            }
            if (value.compareAndSet(v, v + 1)) {
                break;
            }
        }
    }

    @Override
    public void reserveTransfer(ReferenceOwner from, ReferenceOwner to) {
        // nothing to do.
    }

    @Override
    public boolean tryReserve(ReferenceOwner id) {
        for (; ; ) {
            int v = value.get();
            if (v <= 0)
                return false;

            if (value.compareAndSet(v, v + 1)) {
                return true;
            }
        }
    }

    @Override
    public void release(ReferenceOwner id) throws IllegalStateException {
        for (; ; ) {
            int v = value.get();
            if (v <= 0) {
                throw new IllegalStateException("Released");
            }
            if (value.compareAndSet(v, v - 1)) {
                if (v == 1)
                    onRelease.run();
                break;
            }
        }
    }

    @Override
    public void releaseLast(ReferenceOwner id) throws IllegalStateException {
        for (; ; ) {
            int v = value.get();
            if (v <= 0) {
                throw new IllegalStateException("Released");
            }
            if (v > 1) {
                throw new IllegalStateException("Not the last released");
            }
            if (value.compareAndSet(1, 0)) {
                onRelease.run();
                break;
            }
        }
    }

    @Override
    public int refCount() {
        return value.get();
    }

    /**
     * Use refCount() instead.
     *
     * @return the reference counter
     */
    @Deprecated // For removal
    public long get() {
        return value.get();
    }


    @NotNull
    public String toString() {
        return Long.toString(value.get());
    }


}