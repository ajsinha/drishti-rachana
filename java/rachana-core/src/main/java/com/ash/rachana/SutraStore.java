/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.model.Sutra;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Sutras held in memory by {@code name@version} (split step A7; docs/design/CORE_AND_PROFILES.md section 2.2): every version of a
 * name kept, the newest the one used, listeners told of every change. Where Sutras come from (folders, packs and their precedence,
 * hot reload, the last good version) is the host's: it puts and removes here. Thread-safe.
 */
public final class SutraStore {

    /** A change: the Sutra put, or the one removed. */
    public record Change(Kind kind, Sutra sutra) {
        /** What happened. */
        public enum Kind { PUT, REMOVED }
    }

    private final Map<String, TreeMap<Integer, Sutra>> byName = new ConcurrentHashMap<>();
    private final List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

    /** Holds a Sutra (replacing the same {@code name@version}) and tells the listeners. */
    public void put(Sutra sutra) {
        Objects.requireNonNull(sutra, "a Sutra");
        byName.compute(sutra.name(), (n, old) -> {
            TreeMap<Integer, Sutra> v = old == null ? new TreeMap<>() : new TreeMap<>(old);
            v.put(sutra.version(), sutra);
            return v;
        });
        tell(new Change(Change.Kind.PUT, sutra));
    }

    /** Drops one {@code name@version}; true when it was held. */
    public boolean remove(String name, int version) {
        Sutra[] gone = new Sutra[1];
        byName.computeIfPresent(name, (n, old) -> {
            TreeMap<Integer, Sutra> v = new TreeMap<>(old);
            gone[0] = v.remove(version);
            return v.isEmpty() ? null : v;
        });
        if (gone[0] != null) {
            tell(new Change(Change.Kind.REMOVED, gone[0]));
        }
        return gone[0] != null;
    }

    /** The Sutra {@code ref} names: {@code name@N}, or {@code name} for its newest version. */
    public Optional<Sutra> get(String ref) {
        String r = ref == null ? "" : ref.trim();
        int at = r.indexOf('@');
        TreeMap<Integer, Sutra> v = byName.get(at < 0 ? r : r.substring(0, at));
        if (v == null || v.isEmpty()) {
            return Optional.empty();
        }
        if (at < 0) {
            return Optional.of(v.lastEntry().getValue());
        }
        try {
            return Optional.ofNullable(v.get(Integer.parseInt(r.substring(at + 1))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The newest version of every name, by name. */
    public List<Sutra> latest() {
        List<Sutra> out = new ArrayList<>();
        new TreeMap<>(byName).values().forEach(v -> out.add(v.lastEntry().getValue()));
        return out;
    }

    /** Every version of a name, oldest first. */
    public List<Sutra> versions(String name) {
        TreeMap<Integer, Sutra> v = byName.get(name);
        return v == null ? List.of() : List.copyOf(v.values());
    }

    /** Tells {@code listener} of every later change; the returned action stops it. */
    public Runnable listen(Consumer<Change> listener) {
        listeners.add(Objects.requireNonNull(listener, "a listener"));
        return () -> listeners.remove(listener);
    }

    private void tell(Change c) {
        listeners.forEach(l -> l.accept(c));
    }
}
