/*
 * Copyright 2016-2020 Chronicle Software
 *
 * https://chronicle.software
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

package net.openhft.chronicle.core.io;

import net.openhft.chronicle.core.Jvm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;

public interface Closeable extends java.io.Closeable, QueryCloseable {

    static void closeQuietly(@NotNull Object... closeables) {
        closeQuietly((Object) closeables);
    }

    static void closeQuietly(@Nullable Object o) {
        if (o instanceof Collection) {
            ((Collection) o).forEach(Closeable::closeQuietly);
        } else if (o instanceof Object[]) {
            for (Object o2 : (Object[]) o) {
                closeQuietly(o2);
            }
        } else if (o instanceof java.io.Closeable) {
            try {
                ((java.io.Closeable) o).close();
            } catch (IOException | IllegalStateException e) {
                Jvm.debug().on(Closeable.class, e);
            }
        }
    }

    /**
     * Doesn't throw a checked exception.
     */
    @Override
    void close();

    @Deprecated
    default void notifyClosing() {
        // take an action before everything else closes.
    }
}
