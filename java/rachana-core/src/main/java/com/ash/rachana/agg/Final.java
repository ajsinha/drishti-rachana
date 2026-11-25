/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.agg;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * A holistic figure a provider finished itself (SPEC.md section 26.4: "a provider may also answer final figures for functions only it
 * can finish"): an exact median DuckDB computed for one grouping set from its own rows, or a cell's VaR from its own members' summed vector.
 * It is never merged with another cell's: a total is its own set's figure.
 *
 * @param value the figure, null when the cell has none
 */
public record Final(Double value) {

    static final byte TAG = 9;

    /**
     * The state's bytes, as an aggregate state is stored and sent between nodes: a tag, whether there is a figure, the figure. Public for
     * a host that finishes figures of its own (a cell's once-per-parent figure, a provider's native measure) and stores them beside Core's
     * states, so it keeps no copy of this format (G4 binder pass R-2).
     */
    public byte[] encode() {
        return ByteBuffer.allocate(10).put(TAG).put((byte) (value == null ? 0 : 1)).putDouble(value == null ? 0 : value).array();
    }

    /** True when {@code bytes} are a {@link #encode() finished figure's} state (its tag), so a host tells them from other states. */
    public static boolean isEncoded(byte[] bytes) {
        return bytes != null && bytes.length > 0 && bytes[0] == TAG;
    }

    /** The figure {@code b} holds ({@link #encode()}'s bytes). */
    public static Final decode(byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.get();
        boolean has = bb.get() == 1;
        double v = bb.getDouble();
        return new Final(has ? v : null);
    }

    /**
     * A provider's native measure as a function (VaR, ES): holistic, its state the figure the provider finished per cell.
     *
     * @param name the name a view writes
     * @param plainSum true for an additive native measure (a PV, a trade count): a total is the sum of its cells
     */
    public static Aggregate<Object> function(String name, boolean plainSum) {
        return new Aggregate<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Kind kind() {
                return Kind.HOLISTIC;
            }

            @Override
            public int inputs() {
                return 0;
            }

            @Override
            public Object start() {
                return new Final(null);
            }

            @Override
            public Object add(Object state, Batch batch) {
                throw new UnsupportedOperationException(name + " is computed by its provider from each cell's own members");
            }

            @Override
            public Object merge(Object a, Object b) {
                throw new IllegalStateException(name + " is holistic: each cell and total is computed from its own members, never merged");
            }

            @Override
            public Double finish(Object state) {
                return ((Final) state).value();
            }

            @Override
            public byte[] encode(Object state) {
                return ((Final) state).encode();
            }

            @Override
            public Object decode(byte[] bytes) {
                return Final.decode(bytes);
            }

            @Override
            public long bytes(Object state) {
                return 32;
            }

            @Override
            public boolean plainSum() {
                return plainSum;
            }

            @Override
            public List<String> sqlState(List<String> inputs) {
                return List.of();
            }

            @Override
            public Object fromSql(Object[] parts) {
                throw new UnsupportedOperationException(name);
            }
        };
    }
}
