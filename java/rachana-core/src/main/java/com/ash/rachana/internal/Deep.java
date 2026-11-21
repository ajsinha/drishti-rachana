/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The stack guard of Core's recursive walks (SPEC 4.12): the parser, the checker, the printer and the binder walk a tree whose depth
 * the expression limits bound (200 levels by default), so on any reasonable stack the bound refuses a deep input ({@code RCH-2101})
 * long before the stack ends. A host thread may still have less stack than the bound needs (a small {@code -Xss}, a pool thread
 * called from deep inside its host, a debugger): then the walk is run again from the start on a thread of Core's own with a stack
 * sized for the bounds, so the answer is the same as on any other thread and runtime, and Rachana never crashes on a deep input.
 * Only when that thread too runs out (a host raised the bounds past it) does {@code tooDeep} answer: the walk's own refusal.
 *
 * <p>The JavaScript bundles replace this class (java/rachana-js): an engine has no second thread to retry on, so there an exhausted
 * stack ({@code RangeError}, or {@code InternalError} in Firefox) is the walk's refusal at once.
 */
public final class Deep {

    /** The stack of the retry thread: room for the default bounds many hundred times over, reserved, not committed. */
    static final long STACK_BYTES = 512L << 20;

    /** The thread-bound state a walk reads (the Sutra declarations in force), carried onto the retry thread. */
    private static final List<UnaryOperator<Supplier<Object>>> CARRIERS = new CopyOnWriteArrayList<>();

    private static final ThreadLocal<Boolean> RETRYING = new ThreadLocal<>();

    private Deep() {
    }

    /**
     * Registers how the calling thread's state reaches the retry thread: {@code carrier} wraps a walk, on the calling thread, into one
     * that installs that state wherever it runs.
     */
    public static void carry(UnaryOperator<Supplier<Object>> carrier) {
        CARRIERS.add(carrier);
    }

    /**
     * {@code walk}'s answer. When the stack runs out, the walk again from the start on a thread with {@link #STACK_BYTES} of stack;
     * when that too runs out, {@code tooDeep}'s answer (it may throw the walk's refusal). A walk must not keep state across attempts.
     */
    @SuppressWarnings("unchecked")
    public static <T> T call(Supplier<T> walk, Supplier<T> tooDeep) {
        try {
            return walk.get();
        } catch (StackOverflowError e) {
            if (Boolean.TRUE.equals(RETRYING.get())) {
                throw e;                                       // already on the retry thread: the outermost call answers
            }
        }
        Supplier<Object> work = (Supplier<Object>) walk;
        for (UnaryOperator<Supplier<Object>> c : CARRIERS) {
            work = c.apply(work);
        }
        Supplier<Object> carried = work;
        Object[] out = new Object[1];
        Throwable[] failed = new Throwable[1];
        Thread t = new Thread(null, () -> {
            RETRYING.set(Boolean.TRUE);
            try {
                out[0] = carried.get();
            } catch (Throwable x) {
                failed[0] = x;
            }
        }, "rachana-deep", STACK_BYTES);
        t.setDaemon(true);
        t.start();
        boolean interrupted = false;
        while (true) {
            try {
                t.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;                              // the walk is short and bounded: finish it, keep the interrupt
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        Throwable x = failed[0];
        if (x == null) {
            return (T) out[0];
        }
        if (x instanceof StackOverflowError) {
            return tooDeep.get();
        }
        if (x instanceof RuntimeException r) {
            throw r;
        }
        if (x instanceof Error err) {
            throw err;
        }
        throw new IllegalStateException(x);
    }
}
