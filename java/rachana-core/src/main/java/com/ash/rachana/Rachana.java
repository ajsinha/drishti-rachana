/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.el.Calendars;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.FunctionProvider;
import com.ash.rachana.el.FunctionTable;
import com.ash.rachana.format.Formats;
import com.ash.rachana.profile.Profile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One Rachana: settings and profiles in, a compiler, a function table and a format table out (split step A6;
 * docs/design/CORE_AND_PROFILES.md section 3). Everything a profile registers lives in the instance: two instances in one JVM never see
 * each other's functions, calendars, patterns or compiled expressions. An instance never changes; a host whose functions come and go
 * (a module started or stopped) builds a new instance and swaps it in, and the old cache goes with the old instance.
 *
 * <pre>{@code
 * Rachana rachana = Rachana.with(RachanaOptions.DEFAULTS, Profile.core(), drishti);
 * Expr e = rachana.compiler().compile("upper(@.DESK)");
 * }</pre>
 */
public final class Rachana {

    private final RachanaOptions options;
    private final List<Profile> profiles;
    private final FunctionTable functions;
    private final ElCompiler compiler;
    private final Formats formats;
    private final com.ash.rachana.profile.Grammar grammar;
    private final com.ash.rachana.parse.SutraParser parser;

    private Rachana(RachanaOptions options, List<Profile> profiles, FunctionTable functions, Formats formats) {
        this(options, profiles, functions, formats, com.ash.rachana.profile.Grammar.of(profiles));
    }

    private Rachana(RachanaOptions options, List<Profile> profiles, FunctionTable functions, Formats formats, com.ash.rachana.profile.Grammar grammar) {
        this.grammar = grammar;
        this.parser = new com.ash.rachana.parse.SutraParser(grammar);
        this.options = options;
        this.profiles = profiles;
        this.functions = functions;
        this.compiler = new ElCompiler(options.expressionCacheSize(), options.expressionLimits(), functions, grammar.level());
        this.formats = formats;
    }

    /** The name of the library, from its internal banner. */
    public static String name() {
        return com.ash.rachana.internal.Banner.NAME;
    }

    /**
     * An instance with these settings and profiles. Core is always there ({@link Profile#core()} may be named or left out); the other
     * profiles add, in the order given: their function providers, their format files (after the options' own, before the site file),
     * and at most one of them its calendars.
     *
     * @throws IllegalArgumentException when a profile is named twice, two profiles bring calendars or a fragment resolver, or a profile
     *     adds a Core key or another profile's key
     * @throws IllegalStateException when a profile's function has a Core name or another provider's name
     */
    public static Rachana with(RachanaOptions options, Profile... profiles) {
        RachanaOptions opts = options == null ? RachanaOptions.DEFAULTS : options;
        List<Profile> all = new ArrayList<>();
        all.add(Profile.core());
        Set<String> names = new HashSet<>(Set.of("core"));
        List<FunctionProvider> providers = new ArrayList<>();
        List<String> formatFiles = new ArrayList<>(opts.formatFiles());
        Calendars.Resolver calendars = null;
        String calendarsFrom = null;
        for (Profile p : profiles) {
            if (p == null || p.isCore()) {
                continue;
            }
            if (!names.add(p.name())) {
                throw new IllegalArgumentException("the profile '" + p.name() + "' is given twice");
            }
            all.add(p);
            providers.addAll(p.functions());
            formatFiles.addAll(p.formatFiles());
            if (p.calendars() != null) {
                if (calendars != null) {
                    throw new IllegalArgumentException("two profiles bring business-day calendars ('" + calendarsFrom + "', '" + p.name()
                            + "'): one resolver answers per instance");
                }
                calendars = p.calendars();
                calendarsFrom = p.name();
            }
        }
        FunctionTable table = FunctionTable.of(providers, Calendars.of(calendars), opts.regexLimits());
        return new Rachana(opts, List.copyOf(all), table, Formats.load(opts.formatsFile(), formatFiles));
    }

    /** Core alone, with Core's settings. */
    public static Rachana core() {
        return with(RachanaOptions.DEFAULTS);
    }

    /** The settings it was built with. */
    public RachanaOptions options() {
        return options;
    }

    /** Its profiles, Core first. */
    public List<Profile> profiles() {
        return profiles;
    }

    /** The profile of this name, if the instance has it. */
    public Optional<Profile> profile(String name) {
        return profiles.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    /** Its function table: Core's functions and its profiles'. */
    public FunctionTable functions() {
        return functions;
    }

    /** Its compiler (its own cache, its own function table, the options' limits). */
    public ElCompiler compiler() {
        return compiler;
    }

    /** Its format table: the bundled names, the options' and the profiles' files, then the site file. */
    public Formats formats() {
        return formats;
    }

    /** Its Sutra grammar: Core's and what its profiles add (split step A7). */
    public com.ash.rachana.profile.Grammar grammar() {
        return grammar;
    }

    /**
     * The same instance at another language level (SUTRA_EXPANSION 6.2): its grammar, parser and schema read that level. Every instance
     * starts at {@code 1.0}; a host raises the level in its own release. Level {@code 1.1} adds the panel kinds {@code grid}, {@code tree}
     * and {@code chart}, whose views carry datasets (ViewModel contract 1.1).
     *
     * @throws IllegalArgumentException for a level this runtime does not implement
     */
    public Rachana language(String level) {
        return level.equals(grammar.level()) ? this : new Rachana(options, profiles, functions, formats, grammar.atLevel(level));
    }

    /** The language level its grammar reads ({@code 1.0} unless raised with {@link #language(String)}). */
    public String language() {
        return grammar.level();
    }

    /** Its Sutra parser (of {@link #grammar()}). */
    public com.ash.rachana.parse.SutraParser parser() {
        return parser;
    }

    /**
     * The JSON Schema of its Sutras: Core's grammar and its profiles' keys and kinds.
     *
     * @param formats format names this server knows (core and packs)
     */
    public java.util.Map<String, Object> schema(java.util.Collection<String> formats) {
        return RachanaSchema.build(formats, grammar);
    }

    /**
     * A binder over this instance's compiler and formats with a host's hooks (split step A14); {@code BindHooks.core()} and
     * {@code PivotEngine.NONE} for Core alone.
     */
    public com.ash.rachana.bind.Binder binder(com.ash.rachana.bind.BindHooks hooks, com.ash.rachana.bind.PivotEngine pivots) {
        return new com.ash.rachana.bind.Binder(compiler, formats, hooks, pivots);
    }

    /**
     * Core's view pipeline over this instance (split step A14): a Sutra over an entity's data into a ViewModel, the panels' sources
     * read through {@code tables} within {@code budget}, dates from {@code dates}.
     */
    public com.ash.rachana.bind.ViewBuilder views(com.ash.rachana.bind.BindHooks hooks, com.ash.rachana.bind.PivotEngine pivots,
            com.ash.rachana.bind.TableProvider tables, com.ash.rachana.control.BusinessDays dates, java.time.Duration budget) {
        return new com.ash.rachana.bind.ViewBuilder(compiler, formats, binder(hooks, pivots), tables, dates, budget);
    }
}
