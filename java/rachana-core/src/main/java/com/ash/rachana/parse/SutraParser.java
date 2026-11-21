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
     * @param yaml the file content: the Sutra YAML
     * @param file the file name used in problem locations
     * @param domain the folder it was read from (the default of a profile's {@code domain} key)
     * @throws SutraException listing every problem found
     */
    public Sutra parse(String yaml, String file, String domain) {
        PNode root;
        List<PositionalYamlReader.Issue> issues = new ArrayList<>();
        try {
            root = reader.read(yaml, issues);
        } catch (YamlSyntaxException e) {
            throw new SutraException(List.of(new SutraProblem("RCH-2001", "YAML syntax: " + e.getMessage(),
                    new SourceLocation(file, e.line(), e.column()))));
        }
        SutraBuilder b = new SutraBuilder(file, grammar);
        Sutra s = b.build(root, domain);
        if (!issues.isEmpty() || !b.problems().isEmpty()) {
            List<SutraProblem> all = new ArrayList<>();
            issues.forEach(i -> all.add(new SutraProblem(AMBIGUOUS_YAML, i.message(), new SourceLocation(file, i.line(), i.column()))));
            all.addAll(b.problems());
            throw new SutraException(all);
        }
        return s;
    }
}
