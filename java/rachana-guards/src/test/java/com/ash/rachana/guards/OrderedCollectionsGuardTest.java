/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * G3 gate F1: the same input gives the same bytes on every JVM run, on both bundles and in Python. Every main source of every module
 * (whatever it writes: a ViewModel, an answer, a page, a file, a log line) builds its fixed maps and sets in the order written
 * ({@code com.ash.rachana.model.Ordered}) and its working maps and sets as {@code LinkedHashMap}, {@code LinkedHashSet} or a sorted
 * collection. Refused here: {@code Map.of} with two or more keys, {@code Map.ofEntries}, {@code Set.of} with two or more items,
 * {@code Map.copyOf}, {@code Set.copyOf} (their order is drawn afresh in every JVM run), {@code HashMap}, {@code HashSet} and the
 * collectors that make them (their order is the hash's, not the input's; TeaVM's differs), and an unsorted walk over a
 * {@code ConcurrentHashMap} or {@code IdentityHashMap} (allowed only to close, remove or pick a minimum or maximum).
 */
class OrderedCollectionsGuardTest {
    private static final Pattern FACTORY = Pattern.compile("(?<![\\w.])(?:java\\.util\\.)?(Map\\.ofEntries|Map\\.of|Set\\.of|Map\\.copyOf|Set\\.copyOf)\\(");
    private static final Pattern HASHED = Pattern.compile("(?<![\\w.])(?:java\\.util\\.)?(HashMap|HashSet)(?=<|::new|\\()");
    private static final Pattern COLLECTOR = Pattern.compile("Collectors\\.(toSet|toUnmodifiableSet|toUnmodifiableMap|toMap|groupingBy|toConcurrentMap)\\(");
    private static final Pattern UNORDERED_FIELD = Pattern.compile("(\\w+)\\s*=\\s*(?:java\\.util\\.Collections\\.newSetFromMap\\()?new (?:java\\.util\\.(?:concurrent\\.)?)?(ConcurrentHashMap|IdentityHashMap)\\b");
    private static final List<String> WALK_OK = List.of("new TreeMap<>(", "new TreeSet<>(", "new java.util.TreeMap<>(", "new java.util.TreeSet<>(",
            ".sorted(", "removeIf(", "close", ".min(", ".max(");

    @Test
    void mainSourcesBuildNoUnorderedCollection() throws Exception {
        List<Path> files = Sources.nonEmpty(Sources.files(".java")).stream()
                .filter(f -> f.toString().replace('\\', '/').contains("/src/main/java/"))
                .filter(f -> !f.getFileName().toString().equals("Ordered.java")).toList();
        assertTrue(files.size() > 300, "the guard saw too few main sources: " + files.size());
        List<String> found = new ArrayList<>();
        for (Path f : files) {
            found.addAll(scan(Sources.javaRoot().relativize(f).toString(), Files.readString(f)));
        }
        assertTrue(found.isEmpty(), "an unordered collection in a main source (use Ordered.map/set/copy/copySet, LinkedHashMap/LinkedHashSet, "
                + "or sort before walking):\n" + String.join("\n", found));
    }

    @Test
    void theGuardSeesEachForm() {
        String src = "class X {\n Object a = Map.of(\"a\", 1, \"b\", 2);\n Object b = java.util.Set.of(1, 2);\n Object c = Map.copyOf(m);\n"
                + " Object d = new HashMap<>();\n Object e = s.collect(Collectors.toSet());\n Map<String, Integer> f = new ConcurrentHashMap<>();\n"
                + " void g() { f.values().forEach(System.out::println); }\n Object h = Map.of(\"one\", 1);\n Object i = Set.of(1);\n"
                + " Object j = s.collect(Collectors.toMap(k -> k, v -> v, (x, y) -> x, LinkedHashMap::new));\n"
                + " void k() { new TreeMap<>(f).values().forEach(System.out::println); f.values().forEach(Closeable::close); }\n"
                + " String l = \"Map.of(\\\"a\\\", 1, \\\"b\\\", 2)\"; // Map.of(a, 1, b, 2)\n}\n";
        List<String> hits = scan("X.java", src);
        assertTrue(hits.size() == 6 && hits.stream().noneMatch(h -> h.startsWith("X.java:9:") || h.startsWith("X.java:10:")
                || h.startsWith("X.java:11:") || h.startsWith("X.java:12:") || h.startsWith("X.java:13:")), String.join("\n", hits));
    }

    static List<String> scan(String name, String text) {
        String s = blank(text);
        List<String> out = new ArrayList<>();
        Matcher m = FACTORY.matcher(s);
        while (m.find()) {
            String kind = m.group(1);
            int n = arguments(s, m.end() - 1);
            if (kind.equals("Map.of") && n < 4 || kind.equals("Set.of") && n < 2) {
                continue;
            }
            out.add(at(name, text, m.start()) + kind);
        }
        m = HASHED.matcher(s);
        while (m.find()) {
            out.add(at(name, text, m.start()) + m.group(1));
        }
        m = COLLECTOR.matcher(s);
        while (m.find()) {
            int n = arguments(s, m.end() - 1);
            boolean supplied = m.group(1).equals("toMap") && n == 4 || m.group(1).equals("groupingBy") && n == 3;
            if (!supplied) {
                out.add(at(name, text, m.start()) + "Collectors." + m.group(1));
            }
        }
        Set<String> unordered = new LinkedHashSet<>();
        m = UNORDERED_FIELD.matcher(s);
        while (m.find()) {
            unordered.add(m.group(1));
        }
        for (String field : unordered) {
            Matcher w = Pattern.compile("(?<![\\w.])" + Pattern.quote(field) + "\\.(keySet\\(\\)|values\\(\\)|entrySet\\(\\)|forEach\\(|stream\\(\\))").matcher(s);
            while (w.find()) {
                String line = lineOf(s, w.start());
                if (WALK_OK.stream().noneMatch(line::contains)) {
                    out.add(at(name, text, w.start()) + "an unsorted walk over " + field);
                }
            }
        }
        return out;
    }

    private static String at(String name, String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return name + ":" + line + ": ";
    }

    private static String lineOf(String s, int offset) {
        int a = s.lastIndexOf('\n', offset) + 1;
        int b = s.indexOf('\n', offset);
        return s.substring(a, b < 0 ? s.length() : b);
    }

    /** The number of top-level arguments of the call whose '(' is at {@code open}. */
    private static int arguments(String s, int open) {
        int depth = 0;
        int commas = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(open + 1, i).isBlank() ? 0 : commas + 1;
                }
            } else if (c == ',' && depth == 1) {
                commas++;
            }
        }
        return -1;
    }

    /** The text with comments and the insides of string and character literals blanked (same length, same lines). */
    static String blank(String t) {
        StringBuilder b = new StringBuilder(t.length());
        int i = 0;
        int n = t.length();
        while (i < n) {
            if (t.startsWith("//", i)) {
                int j = t.indexOf('\n', i);
                j = j < 0 ? n : j;
                b.append(" ".repeat(j - i));
                i = j;
            } else if (t.startsWith("/*", i) || t.startsWith("\"\"\"", i)) {
                String end = t.startsWith("/*", i) ? "*/" : "\"\"\"";
                int j = t.indexOf(end, i + 2 + (end.length() == 3 ? 1 : 0));
                j = j < 0 ? n : j + end.length();
                for (int k = i; k < j; k++) {
                    b.append(t.charAt(k) == '\n' ? '\n' : ' ');
                }
                i = j;
            } else if (t.charAt(i) == '"' || t.charAt(i) == '\'') {
                char q = t.charAt(i);
                int j = i + 1;
                while (j < n && t.charAt(j) != q) {
                    j += t.charAt(j) == '\\' ? 2 : 1;
                }
                b.append(q).append("s".repeat(Math.max(0, j - i - 1))).append(q);
                i = j + 1;
            } else {
                b.append(t.charAt(i++));
            }
        }
        return b.toString();
    }
}
