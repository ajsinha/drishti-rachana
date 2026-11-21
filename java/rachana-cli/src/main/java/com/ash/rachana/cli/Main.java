/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.cli;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.lib.Library;
import com.ash.rachana.lib.LibraryLock;
import com.ash.rachana.tools.Ed25519;
import com.ash.rachana.tools.LibraryTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The jar launcher of the {@code rachana} command. The library commands live here once (over {@link LibraryTool}); the npm and PyPI
 * launchers hand {@code rachana lib ...} to this class, so no library logic is written twice.
 *
 * <pre>
 * rachana lib build  DIR
 * rachana lib sign   DIR PUBLIC-KEY PRIVATE-SEED
 * rachana lib keygen
 * rachana lib pack   DIR [--out FOLDER] [--require-signed] [--publisher KEY]...
 * rachana lib verify DIR-OR-RLIB [--require-signed] [--publisher KEY]...
 * rachana lib lock   DIR-OR-RLIB... [--lock rachana.lock] [--require-signed] [--publisher KEY]...
 * </pre>
 *
 * Exit status: 0 done, 1 the library does not verify, 2 usage.
 */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Runs a command line and returns the exit status. */
    public static int run(String[] args) {
        if (args.length < 2 || !args[0].equals("lib")) {
            System.err.println("usage: rachana lib build|sign|keygen|pack|verify|lock ...  (the other commands: npx drishti-rachana-cli, or pip install drishti-rachana)");
            return 2;
        }
        List<String> pos = new ArrayList<>();
        Set<String> publishers = new HashSet<>();
        boolean signed = false;
        String out = null;
        String lock = LibraryLock.FILE;
        try {
            for (int i = 2; i < args.length; i++) {
                switch (args[i]) {
                    case "--require-signed" -> signed = true;
                    case "--publisher" -> publishers.add(arg(args, ++i));
                    case "--out" -> out = arg(args, ++i);
                    case "--lock" -> lock = arg(args, ++i);
                    default -> pos.add(args[i]);
                }
            }
            Library.Trust trust = signed || !publishers.isEmpty() ? new Library.Trust(Ed25519.VERIFIER, publishers, signed) : Library.Trust.NONE;
            switch (args[1]) {
                case "build" -> {
                    need(pos, 1);
                    LibraryTool.build(Path.of(pos.get(0)));
                    System.out.println("built " + pos.get(0));
                }
                case "sign" -> {
                    need(pos, 3);
                    LibraryTool.sign(Path.of(pos.get(0)), pos.get(1), pos.get(2));
                    System.out.println("signed " + pos.get(0));
                }
                case "keygen" -> {
                    String[] k = Ed25519.newKeyPair();
                    System.out.println("public  " + k[0]);
                    System.out.println("private " + k[1] + "   (keep it secret)");
                }
                case "pack" -> {
                    need(pos, 1);
                    Path d = Path.of(pos.get(0)).toAbsolutePath();
                    System.out.println(LibraryTool.pack(d, out == null ? d.getParent() : Path.of(out), trust));
                }
                case "verify" -> {
                    need(pos, 1);
                    List<SutraProblem> p = LibraryTool.verify(Path.of(pos.get(0)), trust);
                    p.forEach(x -> System.out.println(x));
                    if (!p.isEmpty()) {
                        return 1;
                    }
                    System.out.println("ok " + pos.get(0));
                }
                case "lock" -> {
                    need(pos, 1);
                    Path f = Path.of(lock);
                    List<LibraryLock.Entry> es = new ArrayList<>();
                    if (Files.exists(f)) {
                        es.addAll(LibraryLock.read(Files.readString(f)).entries().values());
                    }
                    for (String p : pos) {
                        es.add(LibraryTool.lockEntry(Path.of(p), trust));
                    }
                    Files.writeString(f, LibraryLock.of(es).write());
                    System.out.println("wrote " + f + " (" + pos.size() + " added)");
                }
                default -> {
                    System.err.println("rachana lib: unknown command " + args[1]);
                    return 2;
                }
            }
            return 0;
        } catch (SutraException e) {
            e.problems().forEach(x -> System.out.println(x));
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println("rachana lib: " + e.getMessage());
            return 2;
        } catch (java.io.IOException | java.io.UncheckedIOException e) {
            System.err.println("rachana lib: " + e.getMessage());
            return 1;
        }
    }

    private static String arg(String[] a, int i) {
        if (i >= a.length) {
            throw new IllegalArgumentException("a flag is missing its value");
        }
        return a[i];
    }

    private static void need(List<String> pos, int n) {
        if (pos.size() < n) {
            throw new IllegalArgumentException("missing arguments");
        }
    }
}
