/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.profile;

import com.ash.rachana.el.Calendars;
import com.ash.rachana.el.FunctionProvider;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.parse.FragmentResolver;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import com.ash.rachana.model.Ordered;

/**
 * A named set of additions a host hands to {@link com.ash.rachana.Rachana#with}: a value built in code; nothing is discovered by
 * scanning and nothing is global (docs/DESIGN.md section 3). A profile adds; it never changes what Core means: a
 * function whose name Core holds is refused when the instance is built.
 *
 * <p>Split step A6 gives a profile its function providers, its format files and a business-day calendar resolver; the model split (A7)
 * its top-level keys ({@link SutraKey}), the panel kinds and source forms it brings, {@code form.save} and a fragment resolver (they
 * make the instance's {@link Grammar}). Binders follow with the binder (A13).
 *
 * <pre>{@code
 * Profile drishti = Profile.named("drishti").functions(engineFunctions).formats(packFormatsFile).build();
 * Rachana rachana = Rachana.with(options, Profile.core(), drishti);
 * }</pre>
 */
public final class Profile {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Profile CORE = new Profile("core", List.of(), List.of(), null, List.of(), Set.of(), Set.of(), false, null);

    private final String name;
    private final List<FunctionProvider> functions;
    private final List<String> formatFiles;
    private final Calendars.Resolver calendars;
    private final List<SutraKey> keys;
    private final Set<PanelKind> panelKinds;
    private final Set<String> sourceForms;
    private final boolean formSave;
    private final FragmentResolver fragments;

    private Profile(String name, List<FunctionProvider> functions, List<String> formatFiles, Calendars.Resolver calendars, List<SutraKey> keys,
            Set<PanelKind> panelKinds, Set<String> sourceForms, boolean formSave, FragmentResolver fragments) {
        this.name = name;
        this.functions = List.copyOf(functions);
        this.formatFiles = List.copyOf(formatFiles);
        this.calendars = calendars;
        this.keys = List.copyOf(keys);
        this.panelKinds = Ordered.copySet(panelKinds);
        this.sourceForms = Ordered.copySet(sourceForms);
        this.formSave = formSave;
        this.fragments = fragments;
    }

    /** The Core profile: what is built in, nothing added. Every instance has it, named or not. */
    public static Profile core() {
        return CORE;
    }

    /** A builder of the profile {@code name} (lower case, digits and dashes: {@code drishti}, {@code x-acme-risk}); not {@code core}. */
    public static Builder named(String name) {
        Objects.requireNonNull(name, "a profile has a name");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("a profile's name is lower case letters, digits and dashes, starting with a letter: '" + name + "'");
        }
        if ("core".equals(name)) {
            throw new IllegalArgumentException("'core' is Core's own profile: use Profile.core()");
        }
        return new Builder(name);
    }

    /** Its name ({@code core}, {@code drishti}). */
    public String name() {
        return name;
    }

    /** True for {@link #core()}. */
    public boolean isCore() {
        return this == CORE;
    }

    /** The function providers it registers, in order. */
    public List<FunctionProvider> functions() {
        return functions;
    }

    /** The format files it adds, read in order after the bundled table. */
    public List<String> formatFiles() {
        return formatFiles;
    }

    /** Its business-day calendars, or null when it brings none (Core's own answer). */
    public Calendars.Resolver calendars() {
        return calendars;
    }

    /** The top-level keys it adds, in order. */
    public List<SutraKey> keys() {
        return keys;
    }

    /** The panel kinds of the grammar it brings (of {@link Grammar#PROFILE_KINDS}). */
    public Set<PanelKind> panelKinds() {
        return panelKinds;
    }

    /** The source forms it brings (of {@link Grammar#SOURCE_FORMS}). */
    public Set<String> sourceForms() {
        return sourceForms;
    }

    /** Whether it brings {@code form.save} (where a submitted form goes). */
    public boolean formSave() {
        return formSave;
    }

    /** Its fragment resolver, or null when it brings none. */
    public FragmentResolver fragments() {
        return fragments;
    }

    @Override
    public String toString() {
        return "Profile[" + name + "]";
    }

    /** Builds a {@link Profile}. Not thread-safe; the profile it builds is immutable. */
    public static final class Builder {
        private final String name;
        private final List<FunctionProvider> functions = new ArrayList<>();
        private final List<String> formatFiles = new ArrayList<>();
        private Calendars.Resolver calendars;
        private final List<SutraKey> keys = new ArrayList<>();
        private final Set<PanelKind> panelKinds = EnumSet.noneOf(PanelKind.class);
        private final Set<String> sourceForms = new LinkedHashSet<>();
        private boolean formSave;
        private FragmentResolver fragments;

        private Builder(String name) {
            this.name = name;
        }

        /** Adds function providers (their names must be new to Core and to every other provider of the instance). */
        public Builder functions(FunctionProvider... providers) {
            for (FunctionProvider p : providers) {
                functions.add(Objects.requireNonNull(p, "a function provider"));
            }
            return this;
        }

        /** Adds format files, read after the bundled table in the order given. */
        public Builder formats(String... files) {
            for (String f : files) {
                formatFiles.add(Objects.requireNonNull(f, "a format file"));
            }
            return this;
        }

        /** The business-day calendars of the profile's date functions (one profile of an instance may give them). */
        public Builder calendars(Calendars.Resolver resolver) {
            this.calendars = Objects.requireNonNull(resolver, "a calendar resolver");
            return this;
        }

        /** Adds top-level keys (none of Core's: checked when the instance is built). */
        public Builder keys(SutraKey... add) {
            for (SutraKey k : add) {
                keys.add(Objects.requireNonNull(k, "a key"));
            }
            return this;
        }

        /** Brings panel kinds of the grammar that belong to a profile ({@link Grammar#PROFILE_KINDS}: {@code links}, {@code provenance}). */
        public Builder panelKinds(PanelKind... kinds) {
            for (PanelKind k : kinds) {
                if (!Grammar.PROFILE_KINDS.contains(k)) {
                    throw new IllegalArgumentException("'" + k.id() + "' is a Core panel kind");
                }
                panelKinds.add(k);
            }
            return this;
        }

        /** Brings source forms ({@link Grammar#SOURCE_FORMS}: {@code kind}). */
        public Builder sourceForms(String... forms) {
            for (String f : forms) {
                if (!Grammar.SOURCE_FORMS.contains(f)) {
                    throw new IllegalArgumentException("'" + f + "' is not a source form a profile brings (" + Grammar.SOURCE_FORMS + ")");
                }
                sourceForms.add(f);
            }
            return this;
        }

        /** Brings {@code form.save}: where a submitted form goes. */
        public Builder formSave() {
            this.formSave = true;
            return this;
        }

        /** Where {@code use:} finds fragments (one profile of an instance may give it; Core's default is none: every use is unknown). */
        public Builder fragments(FragmentResolver resolver) {
            this.fragments = Objects.requireNonNull(resolver, "a fragment resolver");
            return this;
        }

        public Profile build() {
            return new Profile(name, functions, formatFiles, calendars, keys, panelKinds, sourceForms, formSave, fragments);
        }
    }
}
