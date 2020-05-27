/*
 * Copyright 2016-2020 Chronicle Software
 *
 * https://chronicle.software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.openhft.chronicle.core;

import net.openhft.chronicle.core.io.Closeable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A RandomAccessFile must be explicitly close or cause a resources leak.
 * <p>
 * Weak references RAF can result in a resource leak when GC'ed which doesn't appear if the GC isn't running.
 */

public class CleaningRandomAccessFile extends RandomAccessFile {
    private static final Map<RandomAccessFile, StackTrace> FILES = Collections.synchronizedMap(new WeakHashMap<>());
    private final String fileName;
    volatile boolean closed = false;

    public CleaningRandomAccessFile(String fileName, String mode) throws FileNotFoundException {
        this(new File(fileName), mode);
    }

    public CleaningRandomAccessFile(File file, String mode) throws FileNotFoundException {
        super(file, mode);
        this.fileName = file.getAbsolutePath();
        FILES.put(this, new StackTrace());
    }

    @Override
    public String toString() {
        return "CleaningRandomAccessFile{" +
                "fileName='" + fileName + '\'' +
                ", closed=" + closed +
                '}';
    }

    public static Map<RandomAccessFile, StackTrace> openFiles() {
        synchronized (FILES) {
            return new LinkedHashMap<>(FILES);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        closed = true;
        FILES.remove(this);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!closed) {
            Jvm.warn().on(getClass(), "File was discarded rather than close()ed " + fileName);
            Closeable.closeQuietly(this);
        }
    }
}
