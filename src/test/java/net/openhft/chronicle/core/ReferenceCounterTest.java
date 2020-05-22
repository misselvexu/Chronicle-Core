/*
 * Copyright (c) 2016-2019 Chronicle Software Ltd
 */

package net.openhft.chronicle.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class ReferenceCounterTest {

    private static final ReferenceOwner ro1 = ReferenceOwner.temporary("one");
    private static final ReferenceOwner ro2 = ReferenceOwner.temporary("two");
    private final BiFunction<Runnable, Boolean, ReferenceCounted> rcf;

    public ReferenceCounterTest(String name, BiFunction<Runnable, Boolean, ReferenceCounted> rcf) {
        this.rcf = rcf;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> params() {
        BiFunction<Runnable, Boolean, ReferenceCounted> rc = ReferenceCounter::new;
        BiFunction<Runnable, Boolean, ReferenceCounted> trc = TracingReferenceCounter::new;
        return Arrays.asList(
                new Object[]{"reference-counter", rc},
                new Object[]{"tracing-referemce-counter", trc}
        );
    }

    @Test
    public void releaseOnZero() {
        StateMachine sm = new StateMachine();
        ReferenceCounted rc = rcf.apply(sm, false);
        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.reserve(ReferenceOwner.INIT);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        rc.reserve(ro1);

        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.reserve(ro1);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        rc.reserve(ro2);

        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.reserve(ro2);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        rc.release(ro1);

        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.release(ro1);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        rc.release(ro2);

        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.release(ro2);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        assertEquals(State.NOT_OK, sm.state);
        sm.state = State.OK;

        rc.releaseLast();

        assertEquals(State.RELEASED, sm.state);

        try {
            rc.releaseLast();
            fail();
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    public void releaseOnOne() {
        StateMachine sm = new StateMachine();
        ReferenceCounted rc = rcf.apply(sm, true);
        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.reserve(ReferenceOwner.INIT);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        rc.reserve(ro1);

        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.reserve(ro1);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        rc.reserve(ro2);

        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.reserve(ro2);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        rc.release(ro1);

        if (rc instanceof TracingReferenceCounter) {
            try {
                rc.release(ro1);
                fail();
            } catch (IllegalStateException ignored) {
            }
        }

        assertEquals(State.NOT_OK, sm.state);
        sm.state = State.OK;

        rc.release(ro2);

        assertEquals(State.RELEASED, sm.state);

        try {
            rc.releaseLast();
            fail();
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    public void preventEarlyInitForReleaseOnOne() {
        StateMachine sm = new StateMachine();
        TracingReferenceCounter trc = new TracingReferenceCounter(sm, true);
        assertEquals(1, trc.refCount());
        trc.reserve(ro1);
        assertEquals(2, trc.refCount());
        try {
            trc.release(ReferenceOwner.INIT);
            fail();
        } catch (IllegalStateException ignored) {
        }
        assertEquals(2, trc.refCount());
        sm.state = State.OK;
        trc.release(ro1);
        assertEquals(State.RELEASED, sm.state);
        assertEquals(0, trc.refCount());
        // called implicitly trc.releaseLast();
    }

    enum State {
        NOT_OK,
        OK,
        RELEASED
    }

    static class StateMachine implements Runnable {
        State state = State.NOT_OK;

        @Override
        public void run() {
            assertEquals(State.OK, state);
            state = State.RELEASED;
        }
    }
}