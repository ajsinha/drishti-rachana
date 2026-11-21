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
import com.ash.rachana.tools.LibraryGates;
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
 * rachana lib gate  DIR [--strict]        the quality gates LG1-LG5, LG8; exit 1 when one fails
 * rachana lib freeze DIR                  records the released baseline (FROZEN.sha384, released/); run build after
 * rachana lib contracts  DIR-OR-RLIB [--json]
 * rachana lib check-host DIR-OR-RLIB --base URL [--table CONTRACT=TABLE]... [--json]
 * </pre>
 *
 * Exit status: 0 done, 1 the library does not verify (check-host: a column breaks a fragment, or the host does not answer), 2 usage.
 */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Runs a command line and returns the exit status. */
    public static int run(String[] args) {
        if (args.length > 0 && args[0].equals("upgrade")) {           // rachana upgrade: Upgrade (rachana-tools), the one implementation
            return com.ash.rachana.tools.UpgradeCommand.run(java.util.Arrays.copyOfRange(args, 1, args.length), System.out, System.err);
        }
        if (args.length < 2 || !args[0].equals("lib")) {
            System.err.println("usage: rachana upgrade [paths] [--to L] [--write] [--diff] [--report f] | rachana lib build|sign|keygen|pack|verify|lock|gate|freeze|contracts|check-host ...  (the other commands: npx drishti-rachana-cli, or pip install drishti-rachana)");
            return 2;
        }
        List<String> pos = new ArrayList<>();
        Set<String> publishers = new HashSet<>();
        boolean signed = false;
        String out = null;
        String lock = LibraryLock.FILE;
        String base = null;
        boolean json = false;
        boolean strict = false;
        java.util.Map<String, String> tables = new java.util.LinkedHashMap<>();
        try {
            for (int i = 2; i < args.length; i++) {
                switch (args[i]) {
                    case "--require-signed" -> signed = true;
                    case "--publisher" -> publishers.add(arg(args, ++i));
                    case "--out" -> out = arg(args, ++i);
                    case "--lock" -> lock = arg(args, ++i);
                    case "--base" -> base = arg(args, ++i);
                    case "--json" -> json = true;
                    case "--strict" -> strict = true;
                    case "--table" -> {
                        String t = arg(args, ++i);
                        int eq = t.indexOf('=');
                        if (eq <= 0 || eq == t.length() - 1) {
                            throw new IllegalArgumentException("--table is CONTRACT=TABLE, not " + t);
                        }
                        tables.put(t.substring(0, eq), t.substring(eq + 1));
                    }
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
                case "gate" -> {
                    need(pos, 1);
                    Path dir = Path.of(pos.get(0)).toAbsolutePath().normalize();
                    boolean ok = true;
                    for (LibraryGates.Result r : LibraryGates.run(dir.getParent(), dir.getFileName().toString(), strict)) {
                        System.out.println((r.pass() ? "pass " : "FAIL ") + r.gate() + " " + r.title());
                        r.failures().forEach(f -> System.out.println("       " + f));
                        r.notes().forEach(f -> System.out.println("     . " + f));
                        ok &= r.pass();
                    }
                    return ok ? 0 : 1;
                }
                case "freeze" -> {
                    need(pos, 1);
                    Path dir = Path.of(pos.get(0)).toAbsolutePath().normalize();
                    LibraryGates.freeze(dir.getParent(), dir.getFileName().toString());
                    System.out.println("froze " + dir.getFileName() + " (run: rachana lib build " + pos.get(0) + ")");
                }
                case "contracts" -> {
                    need(pos, 1);
                    List<LibraryTool.Contract> cs = LibraryTool.contracts(Path.of(pos.get(0)), trust);
                    if (json) {
                        System.out.println(com.ash.rachana.parse.Json.write(cs.stream().map(Main::contract).toList()));
                    } else {
                        cs.forEach(c -> System.out.print(text(c)));
                        System.out.println(cs.size() + (cs.size() == 1 ? " contract" : " contracts"));
                    }
                }
                case "check-host" -> {
                    need(pos, 1);
                    if (base == null) {
                        throw new IllegalArgumentException("check-host needs --base <url> (the host's Data Protocol address)");
                    }
                    List<LibraryTool.Fit> fits = LibraryTool.checkHost(LibraryTool.contracts(Path.of(pos.get(0)), trust), tables, schemas(base));
                    if (json) {
                        System.out.println(com.ash.rachana.parse.Json.write(fits.stream().map(Main::fit).toList()));
                    } else {
                        System.out.print(LibraryTool.report(fits));
                    }
                    return fits.stream().anyMatch(LibraryTool.Fit::breaks) ? 1 : 0;
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

    /**
     * The schema of a table as a Data Protocol host gives it ({@code GET <base>/tables/<name>}, PROTOCOL 4), or null when the host has no
     * such table (404); another failure stops the command.
     */
    static java.util.function.Function<String, com.ash.rachana.table.TableSchema> schemas(String base) {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return table -> {
            try {
                java.net.http.HttpResponse<String> r = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(root + "/tables/"
                        + java.net.URLEncoder.encode(table, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")))
                        .header("Accept", "application/json").GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 404) {
                    return null;
                }
                if (r.statusCode() != 200) {
                    throw new java.io.UncheckedIOException(new java.io.IOException("the host answered " + r.statusCode() + " for table " + table));
                }
                return com.ash.rachana.table.TableSchema.of(com.ash.rachana.parse.Json.read(r.body()));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(new java.io.IOException("the host at " + root + " does not answer: " + e.getMessage(), e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.UncheckedIOException(new java.io.IOException("interrupted", e));
            }
        };
    }

    private static java.util.Map<String, Object> contract(LibraryTool.Contract c) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("contract", c.name());
        m.put("title", c.title());
        m.put("grain", c.grain());
        m.put("key", c.key());
        m.put("columns", c.columns().stream().map(x -> {
            java.util.Map<String, Object> col = new java.util.LinkedHashMap<>();
            col.put("name", x.name());
            col.put("type", x.type());
            col.put("optional", x.optional());
            col.put("doc", x.doc());
            return col;
        }).toList());
        m.put("usedBy", c.usedBy());
        return m;
    }

    private static java.util.Map<String, Object> fit(LibraryTool.Fit f) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("contract", f.contract());
        m.put("table", f.table());
        m.put("column", f.column());
        m.put("needed", f.needed());
        m.put("found", f.found());
        m.put("status", f.status());
        return m;
    }

    private static String text(LibraryTool.Contract c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.name()).append("  ").append(c.title() == null ? "" : c.title());
        if (c.grain() != null) {
            sb.append(" (").append(c.grain()).append(c.key().isEmpty() ? "" : "; key " + String.join(", ", c.key())).append(')');
        }
        sb.append(c.usedBy().isEmpty() ? "  (no fragment names it)" : "  used by " + String.join(", ", c.usedBy())).append('\n');
        for (LibraryTool.ContractColumn x : c.columns()) {
            sb.append(String.format("  %-24s %-10s %-9s %s%n", x.name(), x.type(), x.optional() ? "optional" : "required", x.doc() == null ? "" : x.doc()));
        }
        return sb.toString();
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
