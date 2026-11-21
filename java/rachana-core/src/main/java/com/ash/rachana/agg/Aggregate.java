/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.agg;

import java.util.List;

/**
 * An aggregate's state and maths (split step A9, decision N7): four steps, start, add (a columnar batch), merge, finish, and a
 * {@link Kind}. Core's list functions ({@code sum(list)}, {@code percentile(list, p)} …) run these; a host's pivot engine wraps the
 * same classes in its own function contract, so a list and a pivot cell are computed by one implementation. A state crosses threads,
 * nodes and caches, so it is serialisable ({@link #encode}, {@link #decode}) and weighed ({@link #bytes}); the bytes are those the
 * engine wrote before the move, so partial states of old and new nodes merge.
 *
 * <p>A function may also say how a SQL engine makes its state ({@link #sqlState}, {@link #fromSql}): the host's tabular provider then
 * computes the states of every cell in one query, and they are merged and finished as {@link #add}'s would be. A holistic function's
 * SQL form is its final figure for that grouping set.
 *
 * @param <S> the state
 */
public interface Aggregate<S> {

    /** The function's name ({@code sum}, {@code avg}, {@code median}). */
    String name();

    Kind kind();

    /** The number of input columns ({@code count()} has none, {@code wavg(x, w)} two). */
    int inputs();

    /** True when the inputs are read as numbers (false: as text, a distinct count of names). */
    default boolean numeric() {
        return true;
    }

    /** An empty state. Pure; no data. */
    S start();

    /** The state with a batch of rows added; may return a new object or the one given. */
    S add(S state, Batch batch);

    /** Two partial states as one: mergeable exactly, associative and commutative; holistic: the union of the values. */
    S merge(S a, S b);

    /** The figure; null when there is nothing to say (no values), with the reason given by {@link #emptyNote}. */
    Double finish(S state);

    /** The figure as shown: {@link #finish}'s number, or text for a function whose answer is text ({@code string_agg}). */
    default Object result(S state) {
        return finish(state);
    }

    /** The unit of the figure when it is not the input's: {@code count}, the empty text for a pure number, null for the input's. */
    default String resultUnit() {
        return null;
    }

    /** Why {@link #finish} is null for a state. */
    default String emptyNote() {
        return "no values in the cell";
    }

    byte[] encode(S state);

    S decode(byte[] bytes);

    /** The bytes a state holds (a cache's weigher). */
    long bytes(S state);

    /** True when a total of this function is the plain sum of its parts' figures (sum, count). */
    default boolean plainSum() {
        return false;
    }

    /** The SQL aggregate expressions whose values make the state ({@code inputs}: the quoted input expressions); empty when none. */
    List<String> sqlState(List<String> inputs);

    /** The state from the values of {@link #sqlState}'s expressions, in order. */
    S fromSql(Object[] parts);

    /** True when the state made by {@link #fromSql} may be merged with another (false for a holistic function's final figure). */
    default boolean sqlStateMergeable() {
        return kind() != Kind.HOLISTIC;
    }
}
