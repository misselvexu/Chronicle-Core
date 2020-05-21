/*
 * Copyright (c) 2016-2019 Chronicle Software Ltd
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
    static final Map<RandomAccessFile, StackTrace> FILES = Collections.synchronizedMap(new WeakHashMap<>());
    volatile boolean closed = false;

    public CleaningRandomAccessFile(String name, String mode) throws FileNotFoundException {
        super(name, mode);
        FILES.put(this, new StackTrace());
    }

    public CleaningRandomAccessFile(File file, String mode) throws FileNotFoundException {
        super(file, mode);
        FILES.put(this, new StackTrace());
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
        if (!closed)
            Closeable.closeQuietly(this);
    }
}
