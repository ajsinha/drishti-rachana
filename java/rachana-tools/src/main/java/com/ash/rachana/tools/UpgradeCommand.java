/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.tools;

import com.ash.rachana.profile.Grammar;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The command line of {@link Upgrade}, the one the Java, Node and Python launchers run:
 *
 * <pre>
 * rachana upgrade [paths ...] [--to 1.2] [--from 1.0] [--write] [--diff] [--report file.json] [--rules]
 * </pre>
 *
 * Paths are Sutras ({@code *.sutra.yaml}) and fragments ({@code *.fragment.yaml}, {@code *.block.yaml}), or folders searched for them
 * (dot folders and {@code node_modules} skipped). Without {@code --write} nothing is written (a dry run): each file's changes and flags are
 * printed, with {@code --diff} its unified diff. {@code --to} defaults to the newest level; {@code --from} is the level of the fragments
 * (a Sutra declares its own). Exit status: 0 done, 1 a file was refused or holds a blocking flag (nothing written for it), 2 usage.
 */
public final class UpgradeCommand {

    private UpgradeCommand() {}

    public static int run(String[] args, PrintStream out, PrintStream err) {
        List<String> paths = new ArrayList<>();
        String to = Grammar.DEFAULT_LEVEL;
        String from = Grammar.LEVEL_1_0;
        boolean write = false;
        boolean diff = false;
        String report = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--to" -> to = value(args, ++i);
                case "--from" -> from = value(args, ++i);
                case "--write" -> write = true;
                case "--diff" -> diff = true;
                case "--report" -> report = value(args, ++i);
                case "--rules" -> {
                    Upgrade.rules().forEach(out::println);
                    return 0;
                }
                default -> {
                    if (args[i].startsWith("--")) {
                        err.println("rachana upgrade: unknown option " + args[i]);
                        return 2;
                    }
                    paths.add(args[i]);
                }
            }
        }
        if (to == null || from == null || !Grammar.LEVELS.contains(to) || !Grammar.LEVELS.contains(from)) {
            err.println("rachana upgrade: --to and --from name a level: " + String.join(", ", Grammar.LEVELS));
            return 2;
        }
        if (paths.isEmpty()) {
            paths.add(".");
        }
        List<Upgrade.Result> results = new ArrayList<>();
        try {
            for (Path f : files(paths)) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                Upgrade.Result r = Upgrade.upgrade(f.toString(), text, from, to, null);
                results.add(r);
                print(r, out);
                if (diff && r.writable()) {
                    out.print(Upgrade.diff(f.toString(), text, r.text()));
                }
                if (write && r.writable() && r.changed()) {
                    Files.writeString(f, r.text(), StandardCharsets.UTF_8);
                }
            }
            if (report != null) {
                Files.writeString(Path.of(report), com.ash.rachana.parse.Json.write(Upgrade.report(results)) + "\n", StandardCharsets.UTF_8);
            }
        } catch (IOException | IllegalArgumentException e) {
            err.println("rachana upgrade: " + e.getMessage());
            return 2;
        }
        long bad = results.stream().filter(r -> !r.writable()).count();
        long changed = results.stream().filter(r -> r.changed() && r.writable()).count();
        out.println(results.size() + " file(s): " + changed + (write ? " upgraded" : " to upgrade (dry run: --write writes them)") + ", " + bad
                + " refused or blocked");
        return bad > 0 ? 1 : 0;
    }

    private static String value(String[] args, int i) {
        return i < args.length ? args[i] : null;
    }

    private static void print(Upgrade.Result r, PrintStream out) {
        if (r.refused() != null) {
            out.println(r.file() + ": refused: " + r.refused());
            return;
        }
        out.println(r.file() + ": " + r.kind() + " " + r.from() + " -> " + r.to() + ", " + r.changes().size() + " change(s), " + r.flags().size()
                + " flag(s)" + (r.writable() ? "" : ", blocked"));
        r.changes().forEach(c -> out.println("  change " + c.rule() + " " + c.path() + " (line " + c.line() + "): " + c.before() + "  ->  " + c.after()));
        r.flags().forEach(x -> out.println("  " + x.severity() + " " + x.rule() + " " + x.path() + " (line " + x.line() + "): " + x.message()
                + "  [" + x.expression() + "]"));
    }

    /** The files of the paths: each file as given, each folder's Sutras and fragments, sorted. */
    static List<Path> files(List<String> paths) throws IOException {
        List<Path> out = new ArrayList<>();
        for (String p : paths) {
            Path path = Path.of(p);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("no such file or folder: " + p);
            }
            if (Files.isDirectory(path)) {
                try (Stream<Path> s = Files.walk(path)) {
                    s.filter(Files::isRegularFile).filter(UpgradeCommand::upgradable)
                            .filter(f -> path.relativize(f).toString().replace('\\', '/').chars().noneMatch(c -> false))
                            .filter(f -> !hidden(path, f)).sorted().forEach(out::add);
                }
            } else {
                out.add(path);
            }
        }
        return out;
    }

    static boolean upgradable(Path f) {
        String n = f.getFileName().toString();
        return n.endsWith(".sutra.yaml") || n.endsWith(".fragment.yaml") || n.endsWith(".block.yaml");
    }

    private static boolean hidden(Path root, Path f) {
        for (Path part : root.relativize(f)) {
            String n = part.toString();
            if (n.startsWith(".") || n.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }
}
