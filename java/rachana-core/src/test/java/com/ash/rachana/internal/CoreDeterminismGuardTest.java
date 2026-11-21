/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Core computes the same bytes on every runtime (A5): the JDK calls whose answers differ between runtimes (transcendental
 * maths, number text, {@code String.format}, {@code java.text}, {@code java.time.format}, locale names, the clock, the JDK's
 * case tables and white space in the value code) are made only by Core's own classes {@link CoreMath}, {@link NumberText},
 * {@link CoreText}, {@link CoreDecimal} and {@link DateText}. This guard reads Core's sources and lists every other use.
 */
class CoreDeterminismGuardTest {

    private static final Path SOURCES = Path.of("src/main/java/com/ash/rachana");
    private static final Set<String> OWNERS = Set.of("internal/CoreMath.java", "internal/NumberText.java", "internal/CoreText.java",
            "internal/CoreDecimal.java", "internal/DateText.java");
    /** Matches that are not the JDK call: {@code Sutra.strip()} is the Sutra's strip of panels. */
    private static final Set<String> NOT_THE_JDK = Set.of("control/ControlReads.java .strip()", "bind/ViewBuilder.java .strip()");

    /** Forbidden everywhere in Core. */
    private static final Pattern EVERYWHERE = Pattern.compile("\\b(?:Math\\.(?:exp|log|log10|log1p|expm1|pow|tanh|sinh|cosh|sin|cos"
            + "|tan|asin|acos|atan|atan2|cbrt|hypot|rint)\\(|StrictMath\\.|String\\.format\\(|\\.formatted\\(|java\\.text\\."
            + "|java\\.time\\.format\\.(?!DateTimeParseException)|TextStyle|getDisplayName\\(|System\\.nanoTime|System\\.currentTimeMillis|Double\\.toString\\("
            + "|Double\\.parseDouble\\(|Float\\.toString\\(|Float\\.parseFloat\\(|\\.sqrt\\(MathContext|unmodifiableSortedMap|appendReplacement|appendTail)");

    /** Forbidden in the value code (el/, format/, agg/, and the binder: bind/, control/, view/, wire/): what an expression's result or a formatted value is made of. */
    private static final Pattern VALUE_CODE = Pattern.compile("\\.toUpperCase\\(|\\.toLowerCase\\(|\\.strip\\(\\)|\\.stripLeading\\("
            + "|\\.stripTrailing\\(|Character\\.isWhitespace|Character\\.toUpperCase|Character\\.toLowerCase|\\.reverse\\(\\)"
            + "|BigDecimal\\.valueOf\\([a-z]+\\)");

    @Test
    void onlyCoresOwnClassesMakeRuntimeDependentCalls() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String rel = SOURCES.relativize(p).toString().replace('\\', '/');
                if (OWNERS.contains(rel)) {
                    continue;
                }
                boolean valueCode = rel.startsWith("el/") || rel.startsWith("format/") || rel.startsWith("agg/") || rel.startsWith("bind/") || rel.startsWith("control/")
                        || rel.startsWith("view/") || rel.startsWith("wire/");
                String[] lines = withoutComments(Files.readString(p)).split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    check(EVERYWHERE, lines[i], rel, i, found);
                    if (valueCode) {
                        check(VALUE_CODE, lines[i], rel, i, found);
                    }
                }
            }
        }
        assertEquals(List.of(), found, "runtime-dependent calls outside Core's own classes");
    }

    private static void check(Pattern p, String line, String rel, int i, List<String> found) {
        Matcher m = p.matcher(line);
        while (m.find()) {
            if (m.group().startsWith("BigDecimal.valueOf(") && !doubleArgument(line, m.group())) {
                continue;
            }
            if (!NOT_THE_JDK.contains(rel + " " + m.group())) {
                found.add(rel + ":" + (i + 1) + " " + m.group());
            }
        }
    }

    /** BigDecimal.valueOf(name): flagged when the name is a double in this line's file (d, x, f and the like are judged by use). */
    private static boolean doubleArgument(String line, String call) {
        String name = call.substring("BigDecimal.valueOf(".length(), call.length() - 1);
        return Pattern.compile("\\bdouble\\s+" + name + "\\b").matcher(line).find() || name.equals("d");
    }

    /** The source with comments blanked (line breaks kept, so line numbers hold); string literals are left as they are. */
    static String withoutComments(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '"' ) {
                int j = i + 1;
                while (j < n && s.charAt(j) != '"' && s.charAt(j) != '\n') {
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                b.append(s, i, Math.min(n, j + 1));
                i = j;
            } else if (c == '\'' && i + 2 < n) {
                int j = s.charAt(i + 1) == '\\' ? s.indexOf('\'', i + 2) : i + 2;
                b.append(s, i, j + 1);
                i = j;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
                b.append('\n');
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) {
                    if (s.charAt(k) == '\n') {
                        b.append('\n');
                    }
                }
                i = end - 1;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
