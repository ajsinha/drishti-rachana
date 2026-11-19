/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.parse.YamlEvents.Event;
import com.ash.rachana.parse.YamlEvents.Type;
import com.ash.rachana.parse.YamlStream.Mark;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads YAML into a {@link PNode} tree, keeping line and column for every node: Core's own reader, with no library
 * underneath. Thread-safe (each read has its own state).
 *
 * <p>What it reads, and how, is the language's YAML as it was first defined (on a YAML 1.1 reader): the same tree, the
 * same types for the same text ({@code yes} and {@code on} are booleans, {@code 1.0} is a number, {@code 0x1F} is 31,
 * quoted text is always text, dates stay text), the same positions, and the same error texts. YAML a Sutra must not
 * depend on is reported as an {@link Issue} rather than read silently: a key written twice in one mapping (the first is
 * kept), a second document after {@code ---}, a tag other than the core ones, and an anchor or alias.
 *
 * <p>Limits: nesting deeper than 1,000 collections and numbers longer than 1,000 characters are refused; input over
 * 3,145,728 code points is refused.
 */
public final class PositionalYamlReader {

    /**
     * YAML the reader accepted but a Sutra may not contain.
     *
     * @param message what is wrong, for the author
     * @param line 1-based line
     * @param column 1-based column
     */
    public record Issue(String message, int line, int column) {}

    /** The tags of the YAML core schema: writing them changes nothing a Sutra cannot express plainly. */
    private static final Set<String> CORE_TAGS = Set.of("tag:yaml.org,2002:str", "tag:yaml.org,2002:int", "tag:yaml.org,2002:float",
            "tag:yaml.org,2002:bool", "tag:yaml.org,2002:null", "tag:yaml.org,2002:map", "tag:yaml.org,2002:seq");
    private static final String YAML_TAG = "tag:yaml.org,2002:";
    private static final int MAX_DEPTH = 1000;
    private static final int MAX_NUMBER_LENGTH = 1000;

    /** The tree, ignoring the {@link Issue}s (for editors that rewrite a Sutra already checked). */
    public PNode read(String yaml) {
        return read(yaml, new ArrayList<>());
    }

    /**
     * @param yaml the text
     * @param issues receives what a Sutra may not contain, with where it was written
     * @throws YamlSyntaxException when the text is not YAML
     */
    public PNode read(String yaml, List<Issue> issues) {
        Tokens p = new Tokens(yaml);
        Tok t = p.next();
        if (t == null) {
            return new PNode(Map.of(), 1, 1);
        }
        PNode root = node(p, t, issues);
        Tok more = p.next();
        if (more != null) {
            issues.add(new Issue("a second YAML document (after '---'): a Sutra file holds exactly one, and the rest would be ignored",
                    Math.max(1, p.tokenLine() - 1), 1));
        }
        return root;
    }

    private PNode node(Tokens p, Tok t, List<Issue> issues) {
        int line = p.tokenLine();
        int col = p.tokenColumn();
        String tag = p.typeId();
        if (tag != null && !CORE_TAGS.contains(tag)) {
            String shown = tag.startsWith(YAML_TAG) ? "!!" + tag.substring(YAML_TAG.length()) : "!" + tag;
            issues.add(new Issue("YAML tag '" + shown + "' is not allowed in a Sutra: write the value plainly", line, col));
        }
        if (p.anchor != null || t == Tok.ALIAS) {
            issues.add(new Issue(t == Tok.ALIAS ? "YAML alias '*" + p.value + "' is not allowed in a Sutra: write the value out"
                    : "YAML anchor '&" + p.anchor + "' is not allowed in a Sutra: write the value out", line, col));
        }
        return switch (t) {
            case START_OBJECT -> {
                Map<String, PNode> m = new LinkedHashMap<>();
                Map<String, Integer> keyLines = new LinkedHashMap<>();
                while (p.next() == Tok.FIELD_NAME) {
                    String name = p.fieldName;
                    int atLine = p.tokenLine();
                    int atCol = p.tokenColumn();
                    if (p.keyAnchor != null) {
                        issues.add(new Issue("YAML anchor '&" + p.keyAnchor + "' is not allowed in a Sutra: write the value out", atLine, atCol));
                    }
                    PNode value = node(p, p.next(), issues);
                    Integer first = keyLines.putIfAbsent(name, atLine);
                    m.putIfAbsent(name, value);
                    if (first != null) {
                        issues.add(new Issue("duplicate key '" + name + "' (first written at line " + first
                                + "): YAML would keep only the last, so write each key once", atLine, atCol));
                    }
                }
                yield new PNode(m, line, col);
            }
            case START_ARRAY -> {
                List<PNode> l = new ArrayList<>();
                Tok n;
                while ((n = p.next()) != Tok.END_ARRAY) {
                    l.add(node(p, n, issues));
                }
                yield new PNode(l, line, col);
            }
            case VALUE, ALIAS -> new PNode(p.scalarValue(), line, col);
            default -> throw new IllegalStateException("unexpected " + t);
        };
    }

    // ---- the token layer: events as the original reader handed them on ----

    private enum Tok { START_OBJECT, END_OBJECT, START_ARRAY, END_ARRAY, FIELD_NAME, VALUE, ALIAS }

    /** The kind of a scalar's value once its type is decided. */
    private enum Num { NONE, INT, FLOAT }

    private static final class Tokens {
        private final YamlEvents events;
        private Event lastEvent;
        private Event tagEvent;
        private final ArrayList<Boolean> contexts = new ArrayList<>();
        private Tok current;
        private boolean closed;
        String fieldName;
        String anchor;
        String keyAnchor;
        Object value;
        private Num num = Num.NONE;
        private String numberText;
        private boolean negative;

        Tokens(String yaml) {
            this.events = new YamlEvents(new YamlScanner(new YamlStream(yaml)));
        }

        private boolean inObject() {
            return !contexts.isEmpty() && contexts.get(contexts.size() - 1);
        }

        int tokenLine() {
            return lastEvent == null ? -1 : lastEvent.start().line + 1;
        }

        int tokenColumn() {
            return lastEvent == null ? -1 : lastEvent.start().column + 1;
        }

        private YamlSyntaxException error(String message) {
            Mark end = lastEvent == null ? null : lastEvent.end();
            return new YamlSyntaxException(message, end == null ? -1 : end.line + 1, end == null ? -1 : end.column + 1);
        }

        String typeId() {
            if (tagEvent == null) {
                return null;
            }
            String tag = tagEvent.tag();
            if (tag == null) {
                return null;
            }
            while (tag.startsWith("!")) {
                tag = tag.substring(1);
            }
            return tag;
        }

        private Event event() {
            try {
                return events.next();
            } catch (YamlScanner.Problem | YamlStream.Failure e) {
                throw error(e.getMessage());
            } catch (NumberFormatException e) {
                throw error("Malformed Number token: failed to tokenize due to (" + e.getClass().getName() + "): " + e.getMessage());
            }
        }

        Tok next() {
            num = Num.NONE;
            anchor = null;
            keyAnchor = null;
            if (closed) {
                return null;
            }
            while (true) {
                Event evt = event();
                if (evt == null) {
                    return current = null;
                }
                lastEvent = evt;
                if (inObject() && current != Tok.FIELD_NAME) {
                    if (evt.type() != Type.SCALAR) {
                        if (evt.type() == Type.MAPPING_END) {
                            contexts.remove(contexts.size() - 1);
                            return current = Tok.END_OBJECT;
                        }
                        throw error("Expected a field name (Scalar value in YAML), got this instead: " + evt);
                    }
                    fieldName = evt.value();
                    keyAnchor = evt.anchor();
                    return current = Tok.FIELD_NAME;
                }
                tagEvent = evt;
                switch (evt.type()) {
                    case SCALAR -> {
                        anchor = evt.anchor();
                        return current = decode(evt);
                    }
                    case MAPPING_START -> {
                        anchor = evt.anchor();
                        push(true);
                        return current = Tok.START_OBJECT;
                    }
                    case MAPPING_END -> throw error("Not expecting END_OBJECT but a value");
                    case SEQUENCE_START -> {
                        anchor = evt.anchor();
                        push(false);
                        return current = Tok.START_ARRAY;
                    }
                    case SEQUENCE_END -> {
                        contexts.remove(contexts.size() - 1);
                        return current = Tok.END_ARRAY;
                    }
                    case ALIAS -> {
                        value = evt.value();
                        return current = Tok.ALIAS;
                    }
                    case STREAM_END -> {
                        closed = true;
                        return current = null;
                    }
                    default -> {
                        // stream and document markers carry nothing
                    }
                }
            }
        }

        private void push(boolean object) {
            contexts.add(object);
            if (contexts.size() > MAX_DEPTH) {
                throw new YamlSyntaxException("Document nesting depth (" + contexts.size() + ") exceeds the maximum allowed (" + MAX_DEPTH
                        + ", from `StreamReadConstraints.getMaxNestingDepth()`)", 0, 0);
            }
        }

        /** The value of the current scalar token, its number parsed as the original reader did. */
        Object scalarValue() {
            if (num == Num.INT) {
                if (value != null) {
                    return value;
                }
                int len = numberText.length() - (negative ? 1 : 0);
                if (len <= 18) {
                    return Long.parseLong(numberText);
                }
                if (numberText.length() > MAX_NUMBER_LENGTH) {
                    throw tooLong(numberText.length());
                }
                BigInteger n = new BigInteger(numberText);
                return len == 19 && n.bitLength() <= 63 ? (Object) n.longValue() : n;
            }
            if (num == Num.FLOAT) {
                if (numberText.length() > MAX_NUMBER_LENGTH) {
                    throw tooLong(numberText.length());
                }
                try {
                    return Double.parseDouble(numberText);
                } catch (NumberFormatException e) {
                    throw error("Malformed numeric value '" + value + "'");
                }
            }
            return value;
        }

        private static YamlSyntaxException tooLong(int length) {
            return new YamlSyntaxException("Number value length (" + length + ") exceeds the maximum allowed (" + MAX_NUMBER_LENGTH
                    + ", from `StreamReadConstraints.getMaxNumberLength()`)", 0, 0);
        }

        /** Decides a scalar's type: implicit by its text (YAML 1.1) when untagged, else by its core tag. */
        private Tok decode(Event evt) {
            String text = evt.value();
            value = text;
            String tag = evt.tag();
            int len = text.length();
            if (tag == null || tag.equals("!")) {
                String type = evt.plainImplicit() ? YamlScalars.resolve(text) : "str";
                switch (type) {
                    case "int" -> {
                        return number(text, len);
                    }
                    case "float" -> {
                        return decimal(text);
                    }
                    case "bool" -> {
                        Boolean b = bool(text, len);
                        if (b != null) {
                            value = b;
                        }
                        return Tok.VALUE;
                    }
                    case "null" -> {
                        value = null;
                        return Tok.VALUE;
                    }
                    default -> {
                        return Tok.VALUE;
                    }
                }
            }
            String typeTag = tag;
            if (typeTag.startsWith(YAML_TAG)) {
                typeTag = typeTag.substring(YAML_TAG.length());
                if (typeTag.contains(",")) {
                    String[] tags = typeTag.split(",");
                    typeTag = tags.length == 0 ? "" : tags[0];
                }
            }
            if ("bool".equals(typeTag)) {
                Boolean b = bool(text, len);
                if (b != null) {
                    value = b;
                }
            } else if (len > 0 && !"binary".equals(typeTag)) {
                switch (typeTag) {
                    case "int" -> {
                        return number(text, len);
                    }
                    case "float" -> {
                        return decimal(text);
                    }
                    case "null" -> value = null;
                    default -> {
                        // text
                    }
                }
            }
            return Tok.VALUE;
        }

        private static Boolean bool(String v, int len) {
            switch (len) {
                case 1 -> {
                    char c = v.charAt(0);
                    if (c == 'y' || c == 'Y') {
                        return Boolean.TRUE;
                    }
                    if (c == 'n' || c == 'N') {
                        return Boolean.FALSE;
                    }
                }
                case 2 -> {
                    if ("no".equalsIgnoreCase(v)) {
                        return Boolean.FALSE;
                    }
                    if ("on".equalsIgnoreCase(v)) {
                        return Boolean.TRUE;
                    }
                }
                case 3 -> {
                    if ("yes".equalsIgnoreCase(v)) {
                        return Boolean.TRUE;
                    }
                    if ("off".equalsIgnoreCase(v)) {
                        return Boolean.FALSE;
                    }
                }
                case 4 -> {
                    if ("true".equalsIgnoreCase(v)) {
                        return Boolean.TRUE;
                    }
                }
                case 5 -> {
                    if ("false".equalsIgnoreCase(v)) {
                        return Boolean.FALSE;
                    }
                }
                default -> {
                    // not a boolean word
                }
            }
            return null;
        }

        private Tok decimal(String text) {
            num = Num.FLOAT;
            numberText = text.indexOf('_') < 0 || text.isEmpty() ? text : strip(text);
            return Tok.VALUE;
        }

        private static String strip(String s) {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = s.charAt(0) == '+' ? 1 : 0; i < s.length(); i++) {
                if (s.charAt(i) != '_') {
                    sb.append(s.charAt(i));
                }
            }
            return sb.toString();
        }

        private static String digits(String s, int from) {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = from; i < s.length(); i++) {
                if (s.charAt(i) != '_') {
                    sb.append(s.charAt(i));
                }
            }
            return sb.toString();
        }

        private Tok number(String v, int len) {
            int i;
            char ch = v.charAt(0);
            if (ch == '-') {
                negative = true;
                i = 1;
            } else if (ch == '+') {
                negative = false;
                if (len == 1) {
                    return Tok.VALUE;
                }
                i = 1;
            } else {
                negative = false;
                i = 0;
            }
            if (len == i) {
                return Tok.VALUE;
            }
            if (v.charAt(i) == '0') {
                if (++i == len) {
                    num = Num.INT;
                    value = 0L;
                    return Tok.VALUE;
                }
                ch = v.charAt(i);
                switch (ch) {
                    case 'b', 'B' -> {
                        return radix(v, i + 1, 2, 31, 63);
                    }
                    case 'x', 'X' -> {
                        return radix(v, i + 1, 16, 7, 15);
                    }
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '_' -> {
                        return radix(v, i, 8, 10, 21);
                    }
                    default -> {
                        return Tok.VALUE;
                    }
                }
            }
            boolean underscores = false;
            while (true) {
                int c = v.charAt(i);
                if (c > '9' || c < '0') {
                    if (c == '_') {
                        underscores = true;
                    } else {
                        break;
                    }
                }
                if (++i == len) {
                    num = Num.INT;
                    value = null;
                    if (underscores) {
                        numberText = strip(v);
                        if (numberText.isEmpty() || "-".equals(numberText)) {
                            throw error("Invalid number ('" + v + "')");
                        }
                    } else {
                        numberText = v;
                    }
                    return Tok.VALUE;
                }
            }
            return Tok.VALUE;
        }

        private Tok radix(String v, int from, int base, int intDigits, int longDigits) {
            String cleansed = digits(v, from);
            int digitLen = cleansed.length();
            num = Num.INT;
            try {
                if (digitLen <= intDigits) {
                    long n = Integer.parseInt(cleansed, base);
                    value = negative ? -n : n;
                } else if (digitLen <= longDigits) {
                    long n = Long.parseLong(cleansed, base);
                    value = negative ? -n : n;
                } else {
                    BigInteger n = new BigInteger(cleansed, base);
                    value = negative ? n.negate() : n;
                }
            } catch (NumberFormatException e) {
                throw error("Invalid base-" + base + " number ('" + cleansed + "'), problem: " + e.getMessage());
            }
            return Tok.VALUE;
        }
    }
}
