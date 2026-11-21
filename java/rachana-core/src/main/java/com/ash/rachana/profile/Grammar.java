/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.profile;

import com.ash.rachana.model.PanelKind;
import com.ash.rachana.parse.FragmentResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Sutra grammar of one {@link com.ash.rachana.Rachana} instance (split step A7): Core's, plus what its profiles add — top-level
 * keys, the panel kinds that belong to a profile ({@code links}, {@code provenance}), source forms ({@code kind}), {@code form.save}
 * and the editing operations that go with them — and the resolver of {@code use:} fragments. Immutable.
 */
public final class Grammar {

    /** The keys Core reads at the top of a Sutra. A profile cannot add one of these. */
    public static final Set<String> CORE_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(List.of("rachana", "sutra", "version", "title",
            "strip", "panels", "keys", "description", "notes", "controls", "doc", "refresh", "layout", "phone")));

    /** The panel kinds of the grammar that a profile brings (the rest are Core's). */
    public static final Set<PanelKind> PROFILE_KINDS = Collections.unmodifiableSet(EnumSet.of(PanelKind.LINKS, PanelKind.PROVENANCE));

    /** Language level 1.0: what every grammar reads unless its host raises the level (SUTRA_EXPANSION 6.2). */
    public static final String LEVEL_1_0 = "1.0";

    /** The language levels this runtime implements, oldest first. */
    public static final List<String> LEVELS = List.of("1.0", "1.1", "1.2");

    /**
     * Core keys a later language level brings, with that level: below it they are not offered and using one is {@code RCH-2008}.
     * {@code libraries} (level 1.2): the libraries a Sutra uses, each with its major ({@code libraries: [finance@1]}, LIBRARIES.md 7.1).
     */
    public static final Map<String, String> LATER_KEYS = Map.of("libraries", "1.2");

    /** The source forms a profile may bring: {@code kind} ({@code source: { kind, id | where | search, pack, params }}). */
    public static final Set<String> SOURCE_FORMS = Set.of("kind");

    private static final Grammar CORE = new Grammar(Map.of(), EnumSet.noneOf(PanelKind.class), Set.of(), false, FragmentResolver.none(), LEVEL_1_0,
            com.ash.rachana.lib.LibrarySet.none());

    private final Map<String, SutraKey> keys;
    private final Set<PanelKind> profileKinds;
    private final Set<String> sourceForms;
    private final boolean formSave;
    private final FragmentResolver fragments;
    private final String level;
    private final com.ash.rachana.lib.LibrarySet libraries;

    private Grammar(Map<String, SutraKey> keys, Set<PanelKind> profileKinds, Set<String> sourceForms, boolean formSave, FragmentResolver fragments,
            String level, com.ash.rachana.lib.LibrarySet libraries) {
        this.level = level;
        this.libraries = libraries;
        this.keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
        this.profileKinds = profileKinds.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(profileKinds));
        this.sourceForms = Set.copyOf(sourceForms);
        this.formSave = formSave;
        this.fragments = fragments;
    }

    /** Core's grammar alone: no profile key, kind, source form or {@code form.save}; no fragments ({@link FragmentResolver#none()}). */
    public static Grammar core() {
        return CORE;
    }

    /**
     * The grammar of these profiles over Core's, in order.
     *
     * @throws IllegalArgumentException when a profile adds a Core key, two profiles add the same key, or two profiles bring a fragment
     *     resolver
     */
    public static Grammar of(List<Profile> profiles) {
        Map<String, SutraKey> keys = new LinkedHashMap<>();
        Set<PanelKind> kinds = EnumSet.noneOf(PanelKind.class);
        Set<String> forms = new LinkedHashSet<>();
        boolean save = false;
        FragmentResolver fragments = null;
        String fragmentsFrom = null;
        for (Profile p : profiles) {
            for (SutraKey k : p.keys()) {
                if (CORE_KEYS.contains(k.name()) || LATER_KEYS.containsKey(k.name())) {
                    throw new IllegalArgumentException("the profile '" + p.name() + "' cannot add the key '" + k.name() + "': Core reads it");
                }
                if (keys.putIfAbsent(k.name(), k) != null) {
                    throw new IllegalArgumentException("the key '" + k.name() + "' is added twice (profile '" + p.name() + "')");
                }
            }
            kinds.addAll(p.panelKinds());
            forms.addAll(p.sourceForms());
            save |= p.formSave();
            if (p.fragments() != null) {
                if (fragments != null) {
                    throw new IllegalArgumentException("two profiles bring a fragment resolver ('" + fragmentsFrom + "', '" + p.name() + "')");
                }
                fragments = p.fragments();
                fragmentsFrom = p.name();
            }
        }
        if (keys.isEmpty() && kinds.isEmpty() && forms.isEmpty() && !save && fragments == null) {
            return CORE;
        }
        return new Grammar(keys, kinds, forms, save, fragments == null ? FragmentResolver.none() : fragments, LEVEL_1_0,
                com.ash.rachana.lib.LibrarySet.none());
    }

    /**
     * This grammar at another language level (SUTRA_EXPANSION 6.2): a name brought by a later level is not offered (not in the
     * lists of a problem, not in the schema) and using it is {@code RCH-2008}. Every grammar starts at {@link #LEVEL_1_0}.
     *
     * @throws IllegalArgumentException for a level this runtime does not implement ({@link #LEVELS})
     */
    public Grammar atLevel(String next) {
        if (!LEVELS.contains(next)) {
            throw new IllegalArgumentException("language level '" + next + "' is not one of " + String.join(", ", LEVELS));
        }
        return next.equals(level) ? this : new Grammar(keys, profileKinds, sourceForms, formSave, fragments, next, libraries);
    }

    /** This grammar with another fragment resolver in place of its profile's (a test, a tool reading a folder of fragments). */
    public Grammar withFragments(FragmentResolver resolver) {
        return new Grammar(keys, profileKinds, sourceForms, formSave, resolver == null ? FragmentResolver.none() : resolver, level, libraries);
    }

    /**
     * This grammar with the libraries a host offers (LIBRARIES.md 10): where {@code libraries: [name@N]} and {@code use: name/fragment@N}
     * find their library, the lock that pins it and the signatures it trusts. Read from level 1.2.
     */
    public Grammar withLibraries(com.ash.rachana.lib.LibrarySet set) {
        return new Grammar(keys, profileKinds, sourceForms, formSave, fragments, level, set == null ? com.ash.rachana.lib.LibrarySet.none() : set);
    }

    /** The libraries it reads from ({@link com.ash.rachana.lib.LibrarySet#none()} unless a host gives some). */
    public com.ash.rachana.lib.LibrarySet libraries() {
        return libraries;
    }

    /** The level a later Core key needs, or null for a key of every level (or none). */
    public static String keySince(String name) {
        return LATER_KEYS.get(name);
    }

    /** The language level this grammar reads ({@code 1.0} unless raised). */
    public String level() {
        return level;
    }

    /** Whether the kind exists in Core or a profile, at any level (a later level's kind at a lower one is {@code RCH-2008}). */
    public boolean known(PanelKind kind) {
        return !PROFILE_KINDS.contains(kind) || profileKinds.contains(kind);
    }

    /** The top-level keys the profiles add, in the order added. */
    public Map<String, SutraKey> keys() {
        return keys;
    }

    /** Whether a Sutra may write this top-level key (Core's or a profile's). */
    public boolean hasKey(String name) {
        return CORE_KEYS.contains(name) || keys.containsKey(name)
                || LATER_KEYS.containsKey(name) && com.ash.rachana.parse.SutraBlocks.atLeast(level, LATER_KEYS.get(name));
    }

    /** Whether a panel may be of this kind: every Core kind, and a profile's kind when a profile brings it. */
    public boolean accepts(PanelKind kind) {
        return known(kind) && kind.at(level);
    }

    /** The panel kinds a Sutra may use, in the grammar's order. */
    public List<PanelKind> panelKinds() {
        List<PanelKind> out = new ArrayList<>();
        for (PanelKind k : PanelKind.values()) {
            if (accepts(k)) {
                out.add(k);
            }
        }
        return out;
    }

    /** Whether a panel's source may take this form ({@code kind}); a {@code table} source is Core's. */
    public boolean sourceForm(String form) {
        return sourceForms.contains(form);
    }

    /** Whether a form may say where a submitted form goes ({@code form.save}). */
    public boolean formSave() {
        return formSave;
    }

    /** Where {@code use: name@version} finds its fragment. */
    public FragmentResolver fragments() {
        return fragments;
    }
}
