// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
//
// Customer-corpus export, the compile half (Rachana 1.0: Core is the one implementation, the language is level 1.2). Reads the scratch
// copy corpus_export.py made (tree/, kit/fragments/, groups.tsv: one line per Sutra, "<group dir> TAB <pack> TAB <Sutra file>") and for
// every Sutra writes under <out>/compiled/ one canonical JSON document: the problem list, the canonical model text (SutraWriter) or null
// when the file did not parse, and the plan (null). The same document com.ash.rachana.compat.CoreRuntime writes, byte for byte.
//
// Run:  java -cp <Core, conformance and api jars> tools/regime/CorpusCompileCore.java <scratch dir> <out dir>
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
import java.nio.file.*;
import java.util.*;

public class CorpusCompileCore {
    public static void main(String[] a) throws Exception {
        Path scratch = Path.of(a[0]), out = Path.of(a[1]);
        Map<String, String> packs = new LinkedHashMap<>();
        Map<String, List<String>> sutras = new LinkedHashMap<>();
        for (String l : Files.readAllLines(scratch.resolve("groups.tsv"))) {
            String[] f = l.split("\t");
            packs.put(f[0], f[1]);
            sutras.computeIfAbsent(f[0], k -> new ArrayList<>()).add(f[2]);
        }
        BlockLibrary blocks = new BlockLibrary();
        blocks.kit(scratch.resolve("kit"));
        Rachana rachana = Rachana.with(RachanaOptions.DEFAULTS, Cases.profile("drishti", blocks).build());
        SutraExpressions check = new SutraExpressions(rachana.compiler());
        int files = 0, withProblems = 0;
        for (var g : sutras.entrySet()) {
            String dir = g.getKey();
            Path gd = scratch.resolve("tree").resolve(dir);
            List<SutraProblem> blockProblems = blocks.load(List.of(Files.isDirectory(gd.resolve("sutras")) ? gd.resolve("sutras") : gd));
            for (String rel : g.getValue()) {
                Path p = scratch.resolve("tree").resolve(rel);
                List<SutraProblem> ps = new ArrayList<>();
                String model = null;
                try {
                    Sutra s = rachana.parser().parse(Files.readString(p, StandardCharsets.UTF_8), p.getFileName().toString(), packs.get(dir));
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
                files++;
                if (!ps.isEmpty()) { withProblems++; }
            }
            if (!blockProblems.isEmpty()) {
                Path o = out.resolve("compiled").resolve(dir + "/BLOCKS.problems.txt");
                Files.createDirectories(o.getParent());
                Files.writeString(o, String.join("\n", blockProblems.stream().map(x -> x.code() + " " + x.message()).toList()) + "\n");
            }
        }
        System.out.println("compiled " + files + " Sutras, " + withProblems + " with problems");
    }

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
