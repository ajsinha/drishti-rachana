// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
//
// Compatibility regime, CG3 (SUTRA_EXPANSION 6.6): the compile half of the customer-corpus export. Written against Drishti's
// reference jars (read only: nothing of Drishti is changed, nothing is written beside it). Reads a scratch copy of the shipped Sutras
// and blocks that corpus_export.py made, grouped as groups.json says, and for every Sutra file writes, under <out>/compiled/, one
// canonical JSON document: the problem list (codes spelled RCH-, in the order found), the canonical model text (the reference's
// SutraWriter) or null when the file did not parse, and the plan (null: the i7a reference has no plan; the plan comes with Core).
//
// Run:  java -cp <drishti jars> tools/regime/CorpusCompile.java <scratch dir> <out dir>
import com.ash.drishti.rachana.SutraException;
import com.ash.drishti.rachana.SutraExpressions;
import com.ash.drishti.rachana.SutraProblem;
import com.ash.drishti.rachana.SutraWriter;
import com.ash.drishti.rachana.el.ElCompiler;
import com.ash.drishti.rachana.model.Sutra;
import com.ash.drishti.rachana.parse.SutraBlocks;
import com.ash.drishti.rachana.parse.SutraParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class CorpusCompile {
    public static void main(String[] a) throws Exception {
        Path scratch = Path.of(a[0]), out = Path.of(a[1]);
        JsonNode groups = new ObjectMapper().readTree(Files.readString(scratch.resolve("groups.json")));
        SutraParser parser = new SutraParser();
        int files = 0, withProblems = 0;
        for (JsonNode g : groups.get("groups")) {
            SutraBlocks.kit(scratch.resolve("kit"));
            List<Path> blockDirs = new ArrayList<>();
            Path gd = scratch.resolve("tree").resolve(g.get("dir").asText());
            blockDirs.add(Files.isDirectory(gd.resolve("sutras")) ? gd.resolve("sutras") : gd);   // as PackSutrasTest loads a pack: its sutras folder
            List<SutraProblem> blockProblems = SutraBlocks.load(blockDirs);
            SutraExpressions check = new SutraExpressions(new ElCompiler());
            for (JsonNode f : g.get("sutras")) {
                String rel = f.asText();
                Path p = scratch.resolve("tree").resolve(rel);
                List<SutraProblem> ps = new ArrayList<>();
                String model = null;
                try {
                    Sutra s = parser.parse(Files.readString(p, StandardCharsets.UTF_8), p.getFileName().toString(), g.get("pack").asText());
                    ps.addAll(check.check(s));
                    model = new SutraWriter().write(s, s.name(), s.version(), "");
                } catch (SutraException e) {
                    ps.addAll(e.problems());
                }
                StringBuilder sb = new StringBuilder("{\n  \"file\": ").append(str(rel)).append(",\n  \"group\": ").append(str(g.get("dir").asText()))
                        .append(",\n  \"problems\": [");
                boolean first = true;
                for (SutraProblem q : ps) {
                    sb.append(first ? "\n    " : ",\n    ").append("{\"code\": ").append(str(q.code().replaceFirst("^(DRS|DEL)-", "RCH-")))
                            .append(", \"message\": ").append(str(q.message())).append(", \"line\": ").append(q.location().line())
                            .append(", \"column\": ").append(q.location().column()).append('}');
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
                Path o = out.resolve("compiled").resolve(g.get("dir").asText() + "/BLOCKS.problems.txt");
                Files.createDirectories(o.getParent());
                Files.writeString(o, String.join("\n", blockProblems.stream().map(x -> x.code().replaceFirst("^(DRS|DEL)-", "RCH-") + " " + x.message()).toList()) + "\n");
            }
            SutraBlocks.kit(null);
            SutraBlocks.load(List.of());
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
