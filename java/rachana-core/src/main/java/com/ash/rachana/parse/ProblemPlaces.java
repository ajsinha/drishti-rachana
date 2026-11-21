/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.Sutra;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a problem of a Sutra file is, as SPEC 6.1 gives it: the JSON Pointer of the YAML node ({@code /panels/2/limit}) and the
 * node's whole range (start and end line, column and offset). A problem is raised with the line and column of a node; this index
 * of the file's nodes, read once from the same text on the first problem, turns that into the node's path and range.
 *
 * <p>The node a position names: the entry whose key is written there ({@code kind:} of {@code /panels/0/kind}: the range runs from
 * the key to the end of its value), else the deepest node that starts there, else the deepest node that holds it, else the
 * document. A problem located in another file (a fragment, a library's lock) is left as it is. Lines and columns are 1-based and
 * counted as the reader counts them (a column in code points); offsets are 0-based and in UTF-16 code units, the unit editors use
 * (SPEC 4.13 item 4); the end is exclusive.
 *
 * <p>Two indexes are equal: they say where things were written, not what a Sutra means, so two Sutras equal by content stay equal
 * whatever the layout of their texts.
 */
public final class ProblemPlaces {

    /** One YAML node: its pointer, depth, where its key starts (null for a list item or the root), its start and end. */
    private record Entry(String pointer, int depth, int[] key, int[] start, int[] end) {}

    private final String text;
    private final String file;
    private List<Entry> entries;

    private ProblemPlaces(String text, String file) {
        this.text = text == null ? "" : text;
        this.file = file;
    }

    /** The index of {@code yaml}, the text of {@code file}; nothing is read until a problem is placed. */
    public static ProblemPlaces of(String yaml, String file) {
        return new ProblemPlaces(yaml, file);
    }

    /** {@code problems} of {@code s} placed in the text it was read from, or as they are when the Sutra was not read from a text. */
    public static List<SutraProblem> place(Sutra s, List<SutraProblem> problems) {
        return s != null && s.extension(Sutra.PLACES) instanceof ProblemPlaces p ? p.place(problems) : problems;
    }

    /** Each problem placed (a new list). */
    public List<SutraProblem> place(List<SutraProblem> problems) {
        List<SutraProblem> out = new ArrayList<>(problems.size());
        problems.forEach(p -> out.add(place(p)));
        return out;
    }

    /** The problem with its path and range; as it is when it already has them or is located in another file. */
    public SutraProblem place(SutraProblem p) {
        if (p.path() != null || p.location() == null || (file != null && !file.equals(p.location().file()))) {
            return p;
        }
        int line = p.location().line() - 1, col = p.location().column() - 1;
        Entry best = null;
        int[] from = null;
        for (Entry e : index()) {                                     // 1: the entry whose key is written there
            if (e.key() != null && e.key()[0] == line && e.key()[1] == col && (best == null || e.depth() > best.depth())) {
                best = e;
                from = e.key();
            }
        }
        if (best == null) {
            for (Entry e : index()) {                                 // 2: the deepest node that starts there
                if (e.start()[0] == line && e.start()[1] == col && (best == null || e.depth() > best.depth())) {
                    best = e;
                    from = e.start();
                }
            }
        }
        if (best == null) {
            for (Entry e : index()) {                                 // 3: the deepest node that holds it
                int[] s = e.key() != null ? e.key() : e.start();
                if (before(s, line, col) && !before(e.end(), line, col + 1) && (best == null || e.depth() > best.depth())) {
                    best = e;
                    from = s;
                }
            }
        }
        if (best == null) {                                           // 4: the document (or a text that is not YAML)
            int at = offset(Math.max(0, line), Math.max(0, col));
            int l = Math.max(1, p.location().line()), c = Math.max(1, p.location().column());
            return p.placed("", new SutraProblem.Range(l, c, at, l, c, at));
        }
        return p.placed(best.pointer(), new SutraProblem.Range(from[0] + 1, from[1] + 1, utf16(from[2]), best.end()[0] + 1, best.end()[1] + 1,
                utf16(best.end()[2])));
    }

    /** True when {@code m} is at or before line {@code line}, column {@code col} (0-based). */
    private static boolean before(int[] m, int line, int col) {
        return m[0] < line || (m[0] == line && m[1] <= col);
    }

    /** The 0-based UTF-16 offset of a 0-based line and column (in code points) of the text. */
    private int offset(int line, int col) {
        int i = 0, l = 0;
        while (l < line && i < text.length()) {
            if (text.charAt(i++) == '\n') {
                l++;
            }
        }
        int end = i;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        return text.offsetByCodePoints(i, Math.min(col, text.codePointCount(i, end)));
    }

    /** The UTF-16 offset of the reader's code point index. */
    private int utf16(int codePoints) {
        return text.offsetByCodePoints(0, Math.min(codePoints, text.codePointCount(0, text.length())));
    }

    private List<Entry> index() {
        if (entries == null) {
            List<Entry> out = new ArrayList<>();
            try {
                read(out);
            } catch (RuntimeException e) {                            // not YAML: problems are placed at the document
                out.clear();
            }
            entries = out;
        }
        return entries;
    }

    /** An open mapping or list: its pointer, depth, the next list index or the pending key, and the end of its last child. */
    private static final class Frame {
        final String pointer;
        final int depth;
        final boolean map;
        final int[] key;
        final int[] start;
        int index;
        String pendingKey;
        int[] pendingKeyStart;
        boolean expectKey = true;
        int[] lastEnd;

        Frame(String pointer, int depth, boolean map, int[] key, int[] start) {
            this.pointer = pointer;
            this.depth = depth;
            this.map = map;
            this.key = key;
            this.start = start;
        }
    }

    private void read(List<Entry> out) {
        YamlEvents events = new YamlEvents(new YamlScanner(new YamlStream(text)));
        List<Frame> stack = new ArrayList<>();
        YamlEvents.Event ev;
        while ((ev = events.next()) != null) {
            Frame top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
            switch (ev.type()) {
                case SCALAR, ALIAS -> {
                    if (top != null && top.map && top.expectKey) {     // a key: remembered for the value that follows
                        top.pendingKey = ev.value() == null ? "" : ev.value();
                        top.pendingKeyStart = mark(ev.start());
                        top.expectKey = false;
                    } else {
                        String[] child = child(top);
                        int[] key = top != null && top.map ? top.pendingKeyStart : null;
                        Entry e = new Entry(child[0], depth(top), key, mark(ev.start()), mark(ev.end()));
                        if (!inKey(stack)) {
                            out.add(e);
                        }
                        advance(top, e.end());
                    }
                }
                case MAPPING_START, SEQUENCE_START -> {
                    if (top != null && top.map && top.expectKey) {     // a complex key: kept as text-less, its value placed by the key
                        top.pendingKey = "?";
                        top.pendingKeyStart = mark(ev.start());
                        top.expectKey = false;
                        stack.add(new Frame(null, -1, ev.type() == YamlEvents.Type.MAPPING_START, null, mark(ev.start())));
                        break;
                    }
                    String[] child = child(top);
                    int[] key = top != null && top.map ? top.pendingKeyStart : null;
                    stack.add(new Frame(child[0], depth(top), ev.type() == YamlEvents.Type.MAPPING_START, key, mark(ev.start())));
                }
                case MAPPING_END, SEQUENCE_END -> {
                    Frame f = stack.remove(stack.size() - 1);
                    int[] end = f.lastEnd != null ? f.lastEnd : mark(ev.end());
                    Frame parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                    if (f.pointer == null) {                          // the end of a complex key
                        if (parent != null) {
                            parent.lastEnd = end;
                        }
                        break;
                    }
                    if (!inKey(stack)) {
                        out.add(new Entry(f.pointer, f.depth, f.key, f.start, end));
                    }
                    advance(parent, end);
                }
                default -> { }
            }
        }
    }

    /** True inside a complex key (a mapping or list written as a key): its nodes have no pointer. */
    private static boolean inKey(List<Frame> stack) {
        for (Frame f : stack) {
            if (f.pointer == null) {
                return true;
            }
        }
        return false;
    }

    private static int depth(Frame top) {
        return top == null ? 0 : top.depth + 1;
    }

    /** The pointer of the next child of {@code top} ({@code ""} for the document). */
    private static String[] child(Frame top) {
        if (top == null) {
            return new String[] {""};
        }
        String step = top.map ? escape(top.pendingKey == null ? "" : top.pendingKey) : Integer.toString(top.index);
        return new String[] {(top.pointer == null ? "" : top.pointer) + "/" + step};
    }

    private static void advance(Frame top, int[] end) {
        if (top == null) {
            return;
        }
        if (top.map) {
            top.expectKey = true;
            top.pendingKey = null;
            top.pendingKeyStart = null;
        } else {
            top.index++;
        }
        top.lastEnd = end;
    }

    /** RFC 6901: {@code ~} is {@code ~0}, {@code /} is {@code ~1}. */
    static String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static int[] mark(YamlStream.Mark m) {
        return new int[] {m.line, m.column, m.index};
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProblemPlaces;
    }

    @Override
    public int hashCode() {
        return 0x5150;                                                // fixed: a Sutra's hash never depends on a run
    }

    @Override
    public String toString() {
        return "ProblemPlaces[" + file + "]";
    }
}
