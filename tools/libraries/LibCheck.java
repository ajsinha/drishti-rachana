/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana Libraries. Proprietary: see the LICENSE file at the root of this repository.
 */

import com.ash.rachana.Rachana;
import com.ash.rachana.SutraException;
import com.ash.rachana.SutraExpressions;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.lib.LibraryResolver;
import com.ash.rachana.lib.LibrarySet;
import com.ash.rachana.model.Sutra;
import com.ash.rachana.table.LiveScreen;
import com.ash.rachana.view.ViewJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The library tests: every example Sutra of every library compiles at language level 1.2 against the library as built, with no
 * problem and no advisory, and its ViewModel equals the golden snapshot beside it.
 *
 * <pre>
 * java -cp "results/libraries/rachana-jars/*" tools/libraries/LibCheck.java LIBRARIES-DIR [--update] [--only FRAGMENT]
 * </pre>
 *
 * LIBRARIES-DIR holds one folder per library (already built: the hashes must match). {@code --update} writes the goldens
 * (the library must then be built again: tools/build.py does). Exit status 0 all pass, 1 a failure.
 */
public final class LibCheck {

    public static void main(String[] args) throws Exception {
        Path root = null;
        boolean update = false;
        String only = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--update" -> update = true;
                case "--only" -> only = args[++i];
                default -> root = Path.of(args[i]);
            }
        }
        if (root == null) {
            System.err.println("usage: LibCheck LIBRARIES-DIR [--update] [--only FRAGMENT]");
            System.exit(2);
        }
        Rachana rachana = Rachana.core().language("1.2").libraries(LibrarySet.of(LibraryResolver.folder(root)));
        ElCompiler compiler = rachana.compiler();                // at language level 1.2, like the screens
        int pass = 0, fail = 0;
        Map<Path, String> writes = new java.util.TreeMap<>();
        List<Path> libs;
        try (Stream<Path> s = Files.list(root)) {
            libs = s.filter(Files::isDirectory).sorted().toList();
        }
        for (Path lib : libs) {
            Path ex = lib.resolve("examples");
            if (!Files.isDirectory(ex)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> s = Files.list(ex)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".sutra.yaml")).sorted().toList();
            }
            for (Path f : files) {
                String name = f.getFileName().toString().replace(".sutra.yaml", "");
                if (only != null && !name.startsWith(only)) {
                    continue;
                }
                String label = lib.getFileName() + "/" + name;
                List<String> errors = new ArrayList<>();
                String json = null;
                try {
                    Sutra sutra = rachana.parser().parse(Files.readString(f, StandardCharsets.UTF_8), f.getFileName().toString(), "library");
                    for (SutraProblem p : new SutraExpressions(compiler).check(sutra)) {
                        errors.add("problem " + p);
                    }
                    for (SutraProblem p : sutra.advisories()) {
                        errors.add("advisory " + p);
                    }
                    if (errors.isEmpty()) {
                        LiveScreen screen = new LiveScreen(rachana, sutra, LocalDate.of(2026, 1, 15), null, Map.of());
                        json = com.ash.rachana.tools.LibraryGates.canonical(ViewJson.write(screen.view())) + "\n";   // object keys sorted: see com.ash.rachana.tools.LibraryGates.canonical()
                    }
                } catch (SutraException e) {
                    e.problems().forEach(p -> errors.add("problem " + p));
                } catch (RuntimeException e) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                Path golden = lib.resolve("goldens").resolve(name + ".viewmodel.json");
                if (errors.isEmpty()) {
                    if (update) {
                        writes.put(golden, json);                       // written at the end: the library's hashes must not change under the run
                    } else if (!Files.exists(golden)) {
                        errors.add("no golden " + golden + " (run with --update)");
                    } else if (!com.ash.rachana.tools.LibraryGates.canonical(Files.readString(golden, StandardCharsets.UTF_8).trim()).equals(json.trim())) {
                        errors.add("the ViewModel differs from " + golden);
                    }
                }
                if (errors.isEmpty()) {
                    pass++;
                    System.out.println("ok   " + label);
                } else {
                    fail++;
                    System.out.println("FAIL " + label);
                    errors.forEach(e -> System.out.println("       " + e));
                }
            }
        }
        for (Map.Entry<Path, String> w : writes.entrySet()) {
            Files.createDirectories(w.getKey().getParent());
            Files.writeString(w.getKey(), w.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
