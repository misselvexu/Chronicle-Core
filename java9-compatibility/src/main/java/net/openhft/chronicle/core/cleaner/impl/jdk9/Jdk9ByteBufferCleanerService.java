package net.openhft.chronicle.core.cleaner.impl.jdk9;

import net.openhft.chronicle.core.cleaner.spi.ByteBufferCleanerService;
import sun.nio.ch.DirectBuffer;

import java.nio.ByteBuffer;

public final class Jdk9ByteBufferCleanerService implements ByteBufferCleanerService {
    @Override
    public void clean(final ByteBuffer buffer) {
        if (buffer instanceof DirectBuffer) {
            ((DirectBuffer) buffer).cleaner().clean();
        }
    }

    @Override
    public int impact() {
        return NO_IMPACT;
    }
}
