/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.compat;

import com.ash.rachana.Rachana;
import com.ash.rachana.RachanaOptions;
import com.ash.rachana.SutraException;
import com.ash.rachana.SutraExpressions;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.SutraWriter;
import com.ash.rachana.conformance.Cases;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.parse.BlockLibrary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core's compiler over the corpus (CG3, gate finding F1): one {@link Rachana} with the conformance suite's Drishti profile
 * ({@link Cases#profile}: the entity additions, no function of its own) and its own {@link BlockLibrary} (the corpus's UI kit and each
 * group's pack blocks, read again per group as Drishti reads a pack). Every Sutra is parsed, its expressions checked, and written back
 * by {@link SutraWriter}; the document is the one tools/regime/CorpusCompile.java writes on the reference, byte for byte. The Drishti
 * profile's format names do not enter these documents (the check reads no format), so none are loaded.
 */
final class CoreRuntime implements CorpusRuntime {

    @Override public String name() { return "core"; }

    @Override public void compileAll(Corpus corpus, Path out) throws Exception {
        compileAll(corpus, out, "1.2", tree -> { });
    }

    /**
     * As {@link #compileAll(Corpus, Path)} on a host of {@code level}, after {@code prepare} has changed the corpus's scratch copy (its
     * folder holds {@code tree/} and {@code kit/}): the upgrade proof compiles the upgraded Sutras so.
     */
    static void compileAll(Corpus corpus, Path out, String level, Prepare prepare) throws Exception {
        Path scratch = ReferenceBridgeRuntime.scratch(corpus);
        try {
            prepare.accept(scratch);
            BlockLibrary blocks = new BlockLibrary();
            blocks.kit(scratch.resolve("kit"));
            Rachana rachana = Rachana.with(RachanaOptions.DEFAULTS, Cases.profile("drishti", blocks).build()).language(level);
            SutraExpressions check = new SutraExpressions(rachana.compiler());
            Map<String, Object> groups = Json.object(Json.parse(Files.readString(scratch.resolve("groups.json"))));
            for (Object go : Json.array(groups.get("groups"))) {
                Map<String, Object> g = Json.object(go);
                String dir = (String) g.get("dir");
                Path gd = scratch.resolve("tree").resolve(dir);
                List<SutraProblem> blockProblems = blocks.load(List.of(Files.isDirectory(gd.resolve("sutras")) ? gd.resolve("sutras") : gd));
                for (Object fo : Json.array(g.get("sutras"))) {
                    String rel = (String) fo;
                    Path p = scratch.resolve("tree").resolve(rel);
                    List<SutraProblem> ps = new ArrayList<>();
                    String model = null;
                    try {
                        Sutra s = rachana.parser().parse(Files.readString(p, StandardCharsets.UTF_8), p.getFileName().toString(), (String) g.get("pack"));
                        ps.addAll(check.check(s));
                        model = new SutraWriter().write(s, s.name(), s.version(), "");
                    } catch (SutraException e) {
                        ps.addAll(e.problems());
                    }
                    StringBuilder sb = new StringBuilder("{\n  \"file\": ").append(str(rel)).append(",\n  \"group\": ").append(str(dir)).append(",\n  \"problems\": [");
                    boolean first = true;
                    for (SutraProblem q : ps) {
                        sb.append(first ? "\n    " : ",\n    ").append("{\"code\": ").append(str(q.code())).append(", \"message\": ").append(str(q.message()))
                                .append(", \"line\": ").append(q.location().line()).append(", \"column\": ").append(q.location().column()).append('}');
                        first = false;
                    }
                    sb.append(first ? "]" : "\n  ]").append(",\n  \"model\": ").append(model == null ? "null" : str(model)).append(",\n  \"plan\": null\n}\n");
                    Path o = out.resolve("compiled").resolve(rel + ".json");
                    Files.createDirectories(o.getParent());
                    Files.writeString(o, sb.toString(), StandardCharsets.UTF_8);
                }
                if (!blockProblems.isEmpty()) {
                    Path o = out.resolve("compiled").resolve(dir + "/BLOCKS.problems.txt");
                    Files.createDirectories(o.getParent());
                    Files.writeString(o, String.join("\n", blockProblems.stream().map(x -> x.code() + " " + x.message()).toList()) + "\n");
                }
            }
        } finally {
            ReferenceBridgeRuntime.deleteTree(scratch);
        }
    }

    /** A change made to a corpus's scratch copy before it is read. */
    interface Prepare {
        void accept(Path scratch) throws Exception;
    }

    /** A JSON string as CorpusCompile.java writes it. */
    static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) { b.append(String.format("\\u%04x", (int) c)); } else { b.append(c); } }
            }
        }
        return b.append('"').toString();
    }
}
