/*
 * Copyright (c) 2016-2019 Chronicle Software Ltd
 */

package net.openhft.chronicle.core;

public interface ReferenceOwner {
    ReferenceOwner INIT = new VanillaReferenceOwner("init");

    static ReferenceOwner temporary(String name) {
        return Jvm.isReferenceTracing() ? new VanillaReferenceOwner(name) : INIT;
    }

    class VanillaReferenceOwner implements ReferenceOwner {
        private final String name;

        public VanillaReferenceOwner(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "VanillaReferenceOwner{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }
}
