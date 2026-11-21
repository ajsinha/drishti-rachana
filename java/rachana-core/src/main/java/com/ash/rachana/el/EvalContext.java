/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.api.Node;
import com.ash.rachana.format.Formats;

/**
 * What an expression can see: the document ({@code $}), the current row ({@code @}) and its index
 * ({@code #index}), and the named formats for {@code fmt(...)}; on a Sutra Next screen also the events panels emitted
 * ({@code @<panel>.<event>.<field>}, {@code @parent...}) and the panel's own source record ({@code $src}), held in {@link Scope}.
 * Immutable; derive a row context with {@link #withRow}.
 *
 * @param root the document
 * @param row the current row or element, or null outside a row
 * @param index position of the row, or -1
 * @param formats named formats
 * @param scope events and source of the panel being bound (never null)
 * @param locals the names {@code let} bound and {@code reduce}'s {@code @acc}, innermost first (null when none)
 */
public record EvalContext(Node root, Object row, int index, Formats formats, java.util.Map<String, Object> ctl, java.time.LocalDate today,
        Scope scope, Locals locals) {

    /**
     * What a panel of a Sutra Next screen reads besides the document.
     *
     * @param events per panel id, what it emitted: a mapping of event name to payload (absent until the event fires)
     * @param parent the parent panel's events, for a child panel ({@code @parent}); null elsewhere
     * @param src the record or records the panel's {@code source:} names ({@code $src}); null when it has none
     */
    public record Scope(java.util.Map<String, Node> events, Node parent, Object src) {
        public static final Scope NONE = new Scope(java.util.Map.of(), null, null);

        public Scope {
            events = events == null ? java.util.Map.of() : events;
        }

        /** The same events with {@code next} as the source record. */
        public Scope withSrc(Object next) {
            return new Scope(events, parent, next);
        }
    }

    public EvalContext {
        ctl = ctl == null ? java.util.Map.of() : ctl;
        scope = scope == null ? Scope.NONE : scope;
    }

    public EvalContext(Node root, Object row, int index, Formats formats, java.util.Map<String, Object> ctl, java.time.LocalDate today,
            Scope scope) {
        this(root, row, index, formats, ctl, today, scope, null);
    }

    public EvalContext(Node root, Object row, int index, Formats formats, java.util.Map<String, Object> ctl, java.time.LocalDate today) {
        this(root, row, index, formats, ctl, today, Scope.NONE);
    }

    /** A name bound inside an expression ({@code let(n = ..., ...)}, {@code @acc}), and the ones bound outside it. */
    public record Locals(String name, Object value, Locals outer) {}

    /** The same context with {@code name} bound to {@code value} (hiding an outer binding of the same name). */
    public EvalContext withLocal(String name, Object value) {
        return new EvalContext(root, row, index, formats, ctl, today, scope, new Locals(name, value, locals));
    }

    /** The value bound to {@code name}, innermost first; missing when none is (the parser binds every name it reads). */
    public Object local(String name) {
        for (Locals l = locals; l != null; l = l.outer()) {
            if (l.name().equals(name)) {
                return l.value();
            }
        }
        return Node.missing();
    }

    public EvalContext(Node root, Object row, int index, Formats formats, java.util.Map<String, Object> ctl) {
        this(root, row, index, formats, ctl, null);
    }

    public EvalContext(Node root, Object row, int index, Formats formats) {
        this(root, row, index, formats, java.util.Map.of());
    }

    /** The same context for a view of this business date: {@code today()} reads it (null outside a view). */
    public EvalContext withToday(java.time.LocalDate date) {
        return new EvalContext(root, row, index, formats, ctl, date, scope, locals);
    }

    public static EvalContext of(Node root, Formats formats) {
        return new EvalContext(root, null, -1, formats);
    }

    /**
     * The same context with {@code row} as the current element ({@code @}): the element it was in (or the document) becomes its parent,
     * read as {@code ^}.
     */
    public EvalContext withRow(Object row, int index) {
        return new EvalContext(root, row, index, formats, ctl, today, scope, new Locals(PARENT, this.row != null ? this.row : root, locals));
    }

    /**
     * The same context with the elements of a grain as the current element and its parents: {@code chain} outermost first
     * ({@code [leg, cashFlow]}), the last one {@code @}, the one before it {@code ^}, and so on; the document is above them all.
     */
    public EvalContext withRows(java.util.List<?> chain) {
        EvalContext c = this;
        for (int i = 0; i < chain.size(); i++) {
            c = c.withRow(chain.get(i), i == chain.size() - 1 ? index : -1);
        }
        return c;
    }

    /** The name the parent of the current element is bound to (never a name {@code let} can bind). */
    static final String PARENT = "^";

    /** The {@code up}-th parent of the current element ({@code ^} is 1); missing when there is none. */
    public Object parent(int up) {
        int seen = 0;
        for (Locals l = locals; l != null; l = l.outer()) {
            if (PARENT.equals(l.name()) && ++seen == up) {
                return l.value() == null ? Node.missing() : l.value();
            }
        }
        return Node.missing();
    }

    /** The as-of date of the view or query ({@code asof()}, read also as {@code today()}); null outside one. */
    public java.time.LocalDate asOf() {
        return today;
    }

    /**
     * The same context with the values of a Sutra's controls, read in expressions as {@code ctl.<id>}: text for most kinds, a
     * list of text for a multi-select, a whole number for top-n, {@code from..to} text for a range.
     */
    public EvalContext withControls(java.util.Map<String, Object> values) {
        java.util.Map<String, Object> kept = null;                    // the state values stay (withState)
        for (java.util.Map.Entry<String, Object> e : ctl.entrySet()) {
            if (e.getKey().startsWith(STATE_KEY)) {
                kept = kept == null ? new java.util.LinkedHashMap<>(values == null ? java.util.Map.of() : values) : kept;
                kept.put(e.getKey(), e.getValue());
            }
        }
        return new EvalContext(root, row, index, formats, kept == null ? values : java.util.Collections.unmodifiableMap(kept), today, scope, locals);
    }

    /**
     * The key under which a state value is held among the screen's values ({@link #ctl()}): a control's id has no dot, so the two never
     * meet, and a {@code let} memoised for a bind follows both.
     */
    public static final String STATE_KEY = "state.";

    /**
     * The same context with the screen's state values (SUTRA_EXPANSION S1), read as {@code state.<id>}; the controls are kept. Text,
     * an exact number, a boolean, an ISO date text, or a list of text; null for empty.
     */
    public EvalContext withState(java.util.Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        java.util.Map<String, Object> all = new java.util.LinkedHashMap<>(ctl);
        values.forEach((k, v) -> all.put(STATE_KEY + k, v));
        return new EvalContext(root, row, index, formats, java.util.Collections.unmodifiableMap(all), today, scope, locals);
    }

    /** The state value {@code id} ({@code state.<id>}), or null when the context holds none. */
    public Object state(String id) {
        return ctl.get(STATE_KEY + id);
    }

    /** The same context reading {@code next} as its events and source. */
    public EvalContext withScope(Scope next) {
        return new EvalContext(root, row, index, formats, ctl, today, next, locals);
    }

    /** The same context over another document, keeping the controls, the date and the events. */
    public EvalContext withRoot(Node next) {
        return new EvalContext(next, null, -1, formats, ctl, today, scope, locals);
    }

    /** What a panel's {@code source:} named, read as {@code $src}; null when it names none. */
    public Object src() {
        return scope.src();
    }

    /** What {@code panel} emitted ({@code @<panel>}), or for {@code parent} the parent panel's; missing until it fires. */
    public Node event(String panel) {
        Node d = "parent".equals(panel) ? scope.parent() : scope.events().get(panel);
        return d == null ? Node.missing() : d;
    }
}
