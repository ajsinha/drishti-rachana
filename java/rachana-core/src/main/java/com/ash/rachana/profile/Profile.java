/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.profile;

import com.ash.rachana.el.Calendars;
import com.ash.rachana.el.FunctionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named set of additions a host hands to {@link com.ash.rachana.Rachana#with}: a value built in code; nothing is discovered by
 * scanning and nothing is global (docs/design/CORE_AND_PROFILES.md section 3). A profile adds; it never changes what Core means: a
 * function whose name Core holds is refused when the instance is built.
 *
 * <p>Split step A6 gives a profile its function providers, its format files and a business-day calendar resolver. Keys, panel kinds,
 * source forms and binders follow with the model split (A7) and the binder (A13).
 *
 * <pre>{@code
 * Profile drishti = Profile.named("drishti").functions(engineFunctions).formats(packFormatsFile).build();
 * Rachana rachana = Rachana.with(options, Profile.core(), drishti);
 * }</pre>
 */
public final class Profile {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Profile CORE = new Profile("core", List.of(), List.of(), null);

    private final String name;
    private final List<FunctionProvider> functions;
    private final List<String> formatFiles;
    private final Calendars.Resolver calendars;

    private Profile(String name, List<FunctionProvider> functions, List<String> formatFiles, Calendars.Resolver calendars) {
        this.name = name;
        this.functions = List.copyOf(functions);
        this.formatFiles = List.copyOf(formatFiles);
        this.calendars = calendars;
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

        public Profile build() {
            return new Profile(name, functions, formatFiles, calendars);
        }
    }
}
