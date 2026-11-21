/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Core's stack guard in the JavaScript build: replaces the JVM's {@code Deep} (java/rachana-core) in the bundles. An engine has no
 * second thread to run a walk again on, so an exhausted stack is the walk's refusal at once: TeaVM's {@link StackOverflowError}, or
 * the engine's own error as TeaVM hands it over (V8's and WebKit's {@code RangeError: Maximum call stack size exceeded}, Firefox's
 * {@code InternalError: too much recursion}). The bounds (SPEC 4.12) keep every walk far inside an engine's stack, so this answers
 * only on a starved stack, never instead of a result.
 */
public final class Deep {

    private Deep() {
    }

    /** Nothing to carry: the engine runs every walk on its one thread. */
    public static void carry(UnaryOperator<Supplier<Object>> carrier) {
    }

    /** {@code walk}'s answer; when the stack runs out, {@code tooDeep}'s (it may throw the walk's refusal). */
    public static <T> T call(Supplier<T> walk, Supplier<T> tooDeep) {
        try {
            return walk.get();
        } catch (StackOverflowError e) {
            return tooDeep.get();
        } catch (RuntimeException e) {
            if (exhausted(e)) {
                return tooDeep.get();
            }
            throw e;
        }
    }

    /** The engine's own stack error, as TeaVM wraps it. */
    static boolean exhausted(Throwable e) {
        String m = e.getMessage();
        return m != null && (m.contains("Maximum call stack size exceeded") || m.contains("too much recursion")
                || m.contains("call stack size exceeded"));
    }
}
