/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.model.Sutra;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and validates a Sutra: one YAML document ({@code <name>.v<N>.sutra.yaml}) that starts with {@code rachana: 1}.
 * Stateless and thread-safe.
 */
public final class SutraParser {

    /** The Rachana language version this parser reads; every Sutra declares it ({@code rachana: 1}). */
    public static final int LANGUAGE = 1;

    /** The file name used for text that comes from Studio rather than from a file. */
    public static final String STUDIO = "studio.sutra.yaml";

    /**
     * YAML a Sutra must not depend on: a key written twice in one mapping, a second document, a tag other than the core
     * ones (see {@link PositionalYamlReader}).
     */
    public static final String AMBIGUOUS_YAML = "RCH-2033";

    private final PositionalYamlReader reader = new PositionalYamlReader();
    private final com.ash.rachana.profile.Grammar grammar;

    /** A parser of Core's grammar alone ({@link com.ash.rachana.profile.Grammar#core()}). */
    public SutraParser() {
        this(com.ash.rachana.profile.Grammar.core());
    }

    /** A parser of this grammar: Core's and its profiles' ({@link com.ash.rachana.Rachana#parser()}). */
    public SutraParser(com.ash.rachana.profile.Grammar grammar) {
        this.grammar = java.util.Objects.requireNonNull(grammar, "a grammar");
    }

    /** The grammar it reads. */
    public com.ash.rachana.profile.Grammar grammar() {
        return grammar;
    }

    /**
     * The language level a Sutra's text declares (SPEC 2.1): {@code rachana: 1} is {@code 1.0}, {@code rachana: 1.N} is {@code 1.N}
     * (written as a number or a text; the minor is read as written, so {@code 1.10} is ten); null when the text does not read as YAML,
     * has no {@code rachana:} key or another form (another major, a malformed minor). A host reads a Sutra at this level, which may be
     * one it does not implement ({@link com.ash.rachana.profile.Grammar#LEVELS}).
     */
    public static String declaredLevel(String yaml) {
        try {
            PNode root = new PositionalYamlReader().read(yaml);
            return root != null && root.isMap() ? level(root.map().get("rachana"), yaml) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The level the {@code rachana:} node declares, read from its text as written; null for none or another form. */
    static String level(PNode lang, String yaml) {
        if (lang == null) {
            return null;
        }
        if (lang.value() instanceof Long l) {
            return l == LANGUAGE ? "1.0" : null;
        }
        String written = lang.value() instanceof String t ? t : lang.value() instanceof Double ? raw(yaml, lang) : null;
        if (written == null || !written.startsWith(LANGUAGE + ".")) {
            return null;
        }
        String minor = written.substring(2);
        if (minor.isEmpty() || minor.length() > 4 || minor.length() > 1 && minor.charAt(0) == '0') {
            return null;
        }
        for (int i = 0; i < minor.length(); i++) {
            if (minor.charAt(i) < '0' || minor.charAt(i) > '9') {
                return null;
            }
        }
        return LANGUAGE + "." + minor;
    }

    /** A plain scalar's text as written at the node's place (so 1.10 stays ten, not the double 1.1). */
    private static String raw(String yaml, PNode n) {
        String[] lines = yaml.split("\n", -1);
        if (n.line() < 1 || n.line() > lines.length) {
            return null;
        }
        String line = lines[n.line() - 1];
        int i = n.column() - 1;
        if (i < 0 || i >= line.length()) {
            return null;
        }
        int j = i;
        while (j < line.length() && " \t\r,}]#".indexOf(line.charAt(j)) < 0) {
            j++;
        }
        return line.substring(i, j);
    }

    /**
     * @param yaml the file content: the Sutra YAML
     * @param file the file name used in problem locations
     * @param domain the folder it was read from (the default of a profile's {@code domain} key)
     * @throws SutraException listing every problem found
     */
    public Sutra parse(String yaml, String file, String domain) {
        PNode root;
        List<PositionalYamlReader.Issue> issues = new ArrayList<>();
        ProblemPlaces places = ProblemPlaces.of(yaml, file);           // SPEC 6.1: every problem with its path and range
        try {
            root = reader.read(yaml, issues);
        } catch (YamlSyntaxException e) {
            throw new SutraException(places.place(List.of(new SutraProblem("RCH-2001", "YAML syntax: " + e.getMessage(),
                    new SourceLocation(file, e.line(), e.column())))));
        }
        String declared = root.isMap() ? level(root.map().get("rachana"), yaml) : null;
        com.ash.rachana.profile.Grammar g = declared != null && com.ash.rachana.profile.Grammar.LEVELS.contains(declared)
                && SutraBlocks.atLeast(grammar.level(), declared) ? grammar.declaredAt(declared) : grammar;   // SPEC 2.1: read at its own level
        SutraBuilder b = new SutraBuilder(file, g);
        b.declared = declared;
        b.places = places;
        Sutra s = b.build(root, domain);
        if (!issues.isEmpty() || !b.problems().isEmpty()) {
            List<SutraProblem> all = new ArrayList<>();
            issues.forEach(i -> all.add(new SutraProblem(AMBIGUOUS_YAML, i.message(), new SourceLocation(file, i.line(), i.column()))));
            all.addAll(b.problems());
            throw new SutraException(places.place(all));
        }
        return s;
    }
}
