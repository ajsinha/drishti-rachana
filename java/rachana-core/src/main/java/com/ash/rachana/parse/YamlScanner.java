/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.parse.YamlStream.Mark;
import java.io.ByteArrayOutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns YAML text into tokens (YAML 1.1, the language's YAML): indentation, simple keys, the three scalar styles and
 * block scalars, flow collections, tags, anchors and directives. Comments are skipped.
 *
 * <p>The token rules, the order in which problems are found and the wording of every error are those of the reader the
 * language was first defined against (the PyYAML family's scanner): a Sutra that was refused before is refused with the
 * same text. Not thread-safe: one per read.
 */
final class YamlScanner {

    /** Token kinds, with the names error texts use. */
    enum Kind {
        ALIAS("<alias>"), ANCHOR("<anchor>"), BLOCK_END("<block end>"), BLOCK_ENTRY("-"),
        BLOCK_MAPPING_START("<block mapping start>"), BLOCK_SEQUENCE_START("<block sequence start>"), DIRECTIVE("<directive>"),
        DOCUMENT_END("<document end>"), DOCUMENT_START("<document start>"), FLOW_ENTRY(","), FLOW_MAPPING_END("}"),
        FLOW_MAPPING_START("{"), FLOW_SEQUENCE_END("]"), FLOW_SEQUENCE_START("["), KEY("?"), SCALAR("<scalar>"),
        STREAM_END("<stream end>"), STREAM_START("<stream start>"), TAG("<tag>"), VALUE(":");

        final String shown;

        Kind(String shown) {
            this.shown = shown;
        }

        @Override
        public String toString() {
            return shown;
        }
    }

    /**
     * One token. {@code value} is a scalar's text, an anchor's or alias's name, or a directive's name; {@code style} a
     * scalar's style ({@code 0} plain, {@code '\''}, {@code '"'}, {@code '|'}, {@code '>'}); {@code handle} and
     * {@code suffix} a tag's parts; {@code args} a directive's values.
     */
    record Token(Kind kind, Mark start, Mark end, String value, char style, String handle, String suffix, List<String> args) {
        static Token of(Kind kind, Mark start, Mark end) {
            return new Token(kind, start, end, null, (char) 0, null, null, null);
        }

        boolean plain() {
            return style == 0;
        }
    }

    /** A syntax error with the wording of the PyYAML family: context, its place, problem, its place. */
    static final class Problem extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Problem(String context, Mark contextMark, String problem, Mark problemMark) {
            super(text(context, contextMark, problem, problemMark), null, false, false);
        }

        private static String text(String context, Mark contextMark, String problem, Mark problemMark) {
            StringBuilder lines = new StringBuilder();
            if (context != null) {
                lines.append(context).append('\n');
            }
            if (contextMark != null) {
                lines.append(contextMark).append('\n');
            }
            if (problem != null) {
                lines.append(problem).append('\n');
            }
            if (problemMark != null) {
                lines.append(problemMark).append('\n');
            }
            return lines.toString();
        }
    }

    private record SimpleKey(int tokenNumber, boolean required, int index, int line, int column, Mark mark) {}

    private static final String ESCAPE_KEYS = "0abtnvfre \"\\N_LP";
    private static final String[] ESCAPE_VALUES = {"\0", "\u0007", "\u0008", "\u0009", "\n", "\u000B", "\u000C", "\r", "\u001B",
        " ", "\"", "\\", "\u0085", " ", " ", " "};
    private static final String ALPHA = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-_";
    private static final String URI_CHARS = ALPHA + "-;/?:@&=+$,_.!~*'()[]%";

    private final YamlStream reader;
    private boolean done;
    private int flowLevel;
    private final List<Token> tokens = new ArrayList<>();
    private int tokensTaken;
    private int indent = -1;
    private final ArrayDeque<Integer> indents = new ArrayDeque<>();
    private boolean allowSimpleKey = true;
    private final Map<Integer, SimpleKey> possibleSimpleKeys = new LinkedHashMap<>();

    YamlScanner(YamlStream reader) {
        this.reader = reader;
        Mark mark = reader.mark();
        tokens.add(Token.of(Kind.STREAM_START, mark, mark));
    }

    // ---- character classes ----

    private static boolean isLinebr(int c) {
        return c == '\n' || c == 0x85 || c == 0x2028 || c == 0x2029;
    }

    private static boolean isNullOrLinebr(int c) {
        return c == 0 || c == '\r' || isLinebr(c);
    }

    private static boolean isNullBlLinebr(int c) {
        return c == ' ' || isNullOrLinebr(c);
    }

    private static boolean isNullBlTLinebr(int c) {
        return c == '\t' || isNullBlLinebr(c);
    }

    private static boolean in(int c, String set) {
        return set.indexOf(c) >= 0;
    }

    private static boolean isAlpha(int c) {
        return c < 128 && in(c, ALPHA);
    }

    private static String chars(int c) {
        return new String(Character.toChars(c));
    }

    // ---- the token queue ----

    boolean check(Kind... choices) {
        while (needMoreTokens()) {
            fetchMoreTokens();
        }
        if (tokens.isEmpty()) {
            return false;
        }
        if (choices.length == 0) {
            return true;
        }
        Kind first = tokens.get(0).kind();
        for (Kind k : choices) {
            if (first == k) {
                return true;
            }
        }
        return false;
    }

    Token peek() {
        while (needMoreTokens()) {
            fetchMoreTokens();
        }
        return tokens.get(0);
    }

    Token next() {
        tokensTaken++;
        return tokens.remove(0);
    }

    private boolean needMoreTokens() {
        if (done) {
            return false;
        }
        if (tokens.isEmpty()) {
            return true;
        }
        stalePossibleSimpleKeys();
        return nextPossibleSimpleKey() == tokensTaken;
    }

    private void fetchMoreTokens() {
        if (reader.index() > 3 * 1024 * 1024) {
            throw new YamlStream.Failure("The incoming YAML document exceeds the limit: " + 3 * 1024 * 1024 + " code points.");
        }
        scanToNextToken();
        stalePossibleSimpleKeys();
        unwindIndent(reader.column());
        int c = reader.peek();
        switch (c) {
            case 0 -> {
                fetchStreamEnd();
                return;
            }
            case '%' -> {
                if (reader.column() == 0) {
                    fetchDirective();
                    return;
                }
            }
            case '-' -> {
                if (checkDocumentIndicator("---")) {
                    fetchDocumentIndicator(Kind.DOCUMENT_START);
                    return;
                } else if (isNullBlTLinebr(reader.peek(1))) {
                    fetchBlockEntry();
                    return;
                }
            }
            case '.' -> {
                if (checkDocumentIndicator("...")) {
                    fetchDocumentIndicator(Kind.DOCUMENT_END);
                    return;
                }
            }
            case '[' -> {
                fetchFlowCollectionStart(Kind.FLOW_SEQUENCE_START);
                return;
            }
            case '{' -> {
                fetchFlowCollectionStart(Kind.FLOW_MAPPING_START);
                return;
            }
            case ']' -> {
                fetchFlowCollectionEnd(Kind.FLOW_SEQUENCE_END);
                return;
            }
            case '}' -> {
                fetchFlowCollectionEnd(Kind.FLOW_MAPPING_END);
                return;
            }
            case ',' -> {
                fetchFlowEntry();
                return;
            }
            case '?' -> {
                if (flowLevel != 0 || isNullBlTLinebr(reader.peek(1))) {
                    fetchKey();
                    return;
                }
            }
            case ':' -> {
                if (flowLevel != 0 || isNullBlTLinebr(reader.peek(1))) {
                    fetchValue();
                    return;
                }
            }
            case '*' -> {
                fetchAnchor(false);
                return;
            }
            case '&' -> {
                fetchAnchor(true);
                return;
            }
            case '!' -> {
                savePossibleSimpleKey();
                allowSimpleKey = false;
                addToken(scanTag());
                return;
            }
            case '|', '>' -> {
                if (flowLevel == 0) {
                    allowSimpleKey = true;
                    removePossibleSimpleKey();
                    addToken(scanBlockScalar((char) c));
                    return;
                }
            }
            case '\'', '"' -> {
                savePossibleSimpleKey();
                allowSimpleKey = false;
                addToken(scanFlowScalar((char) c));
                return;
            }
            default -> {
                // fall through to the plain scalar check
            }
        }
        if (checkPlain()) {
            savePossibleSimpleKey();
            allowSimpleKey = false;
            addToken(scanPlain());
            return;
        }
        String shown = escapeChar(chars(c));
        if (c == '\t') {
            shown += "(TAB)";
        }
        throw new Problem("while scanning for the next token", null,
                "found character '" + shown + "' that cannot start any token. (Do not use " + shown + " for indentation)", reader.mark());
    }

    private void addToken(Token t) {
        tokens.add(t);
    }

    private static String escapeChar(String shown) {
        for (int i = 0; i < ESCAPE_VALUES.length; i++) {
            if (ESCAPE_VALUES[i].equals(shown)) {
                return "\\" + ESCAPE_KEYS.charAt(i);
            }
        }
        return shown;
    }

    // ---- simple keys ----

    private int nextPossibleSimpleKey() {
        Iterator<SimpleKey> it = possibleSimpleKeys.values().iterator();
        return it.hasNext() ? it.next().tokenNumber() : -1;
    }

    private void stalePossibleSimpleKeys() {
        for (Iterator<SimpleKey> it = possibleSimpleKeys.values().iterator(); it.hasNext();) {
            SimpleKey key = it.next();
            if (key.line() != reader.line() || reader.index() - key.index() > 1024) {
                if (key.required()) {
                    throw new Problem("while scanning a simple key", key.mark(), "could not find expected ':'", reader.mark());
                }
                it.remove();
            }
        }
    }

    private void savePossibleSimpleKey() {
        boolean required = flowLevel == 0 && indent == reader.column();
        if (!allowSimpleKey && required) {
            throw new YamlStream.Failure("A simple key is required only if it is the first token in the current line");
        }
        if (allowSimpleKey) {
            removePossibleSimpleKey();
            int tokenNumber = tokensTaken + tokens.size();
            possibleSimpleKeys.put(flowLevel, new SimpleKey(tokenNumber, required, reader.index(), reader.line(), reader.column(),
                    reader.mark()));
        }
    }

    private void removePossibleSimpleKey() {
        SimpleKey key = possibleSimpleKeys.remove(flowLevel);
        if (key != null && key.required()) {
            throw new Problem("while scanning a simple key", key.mark(), "could not find expected ':'", reader.mark());
        }
    }

    // ---- indentation ----

    private void unwindIndent(int col) {
        if (flowLevel != 0) {
            return;
        }
        while (indent > col) {
            Mark mark = reader.mark();
            indent = indents.pop();
            addToken(Token.of(Kind.BLOCK_END, mark, mark));
        }
    }

    private boolean addIndent(int column) {
        if (indent < column) {
            indents.push(indent);
            indent = column;
            return true;
        }
        return false;
    }

    // ---- fetchers ----

    private void fetchStreamEnd() {
        unwindIndent(-1);
        removePossibleSimpleKey();
        allowSimpleKey = false;
        possibleSimpleKeys.clear();
        Mark mark = reader.mark();
        addToken(Token.of(Kind.STREAM_END, mark, mark));
        done = true;
    }

    private void fetchDirective() {
        unwindIndent(-1);
        removePossibleSimpleKey();
        allowSimpleKey = false;
        addToken(scanDirective());
    }

    private boolean checkDocumentIndicator(String indicator) {
        return reader.column() == 0 && indicator.equals(reader.prefix(3)) && isNullBlTLinebr(reader.peek(3));
    }

    private void fetchDocumentIndicator(Kind kind) {
        unwindIndent(-1);
        removePossibleSimpleKey();
        allowSimpleKey = false;
        Mark start = reader.mark();
        reader.forward(3);
        addToken(Token.of(kind, start, reader.mark()));
    }

    private void fetchFlowCollectionStart(Kind kind) {
        savePossibleSimpleKey();
        flowLevel++;
        allowSimpleKey = true;
        Mark start = reader.mark();
        reader.forward(1);
        addToken(Token.of(kind, start, reader.mark()));
    }

    private void fetchFlowCollectionEnd(Kind kind) {
        removePossibleSimpleKey();
        flowLevel--;
        allowSimpleKey = false;
        Mark start = reader.mark();
        reader.forward();
        addToken(Token.of(kind, start, reader.mark()));
    }

    private void fetchFlowEntry() {
        allowSimpleKey = true;
        removePossibleSimpleKey();
        Mark start = reader.mark();
        reader.forward();
        addToken(Token.of(Kind.FLOW_ENTRY, start, reader.mark()));
    }

    private void fetchBlockEntry() {
        if (flowLevel == 0) {
            if (!allowSimpleKey) {
                throw new Problem(null, null, "sequence entries are not allowed here", reader.mark());
            }
            if (addIndent(reader.column())) {
                Mark mark = reader.mark();
                addToken(Token.of(Kind.BLOCK_SEQUENCE_START, mark, mark));
            }
        }
        allowSimpleKey = true;
        removePossibleSimpleKey();
        Mark start = reader.mark();
        reader.forward();
        addToken(Token.of(Kind.BLOCK_ENTRY, start, reader.mark()));
    }

    private void fetchKey() {
        if (flowLevel == 0) {
            if (!allowSimpleKey) {
                throw new Problem(null, null, "mapping keys are not allowed here", reader.mark());
            }
            if (addIndent(reader.column())) {
                Mark mark = reader.mark();
                addToken(Token.of(Kind.BLOCK_MAPPING_START, mark, mark));
            }
        }
        allowSimpleKey = flowLevel == 0;
        removePossibleSimpleKey();
        Mark start = reader.mark();
        reader.forward();
        addToken(Token.of(Kind.KEY, start, reader.mark()));
    }

    private void fetchValue() {
        SimpleKey key = possibleSimpleKeys.remove(flowLevel);
        if (key != null) {
            tokens.add(key.tokenNumber() - tokensTaken, Token.of(Kind.KEY, key.mark(), key.mark()));
            if (flowLevel == 0 && addIndent(key.column())) {
                tokens.add(key.tokenNumber() - tokensTaken, Token.of(Kind.BLOCK_MAPPING_START, key.mark(), key.mark()));
            }
            allowSimpleKey = false;
        } else {
            if (flowLevel == 0 && !allowSimpleKey) {
                throw new Problem(null, null, "mapping values are not allowed here", reader.mark());
            }
            if (flowLevel == 0 && addIndent(reader.column())) {
                Mark mark = reader.mark();
                addToken(Token.of(Kind.BLOCK_MAPPING_START, mark, mark));
            }
            allowSimpleKey = flowLevel == 0;
            removePossibleSimpleKey();
        }
        Mark start = reader.mark();
        reader.forward();
        addToken(Token.of(Kind.VALUE, start, reader.mark()));
    }

    private void fetchAnchor(boolean anchor) {
        savePossibleSimpleKey();
        allowSimpleKey = false;
        addToken(scanAnchor(anchor));
    }

    private boolean checkPlain() {
        int c = reader.peek();
        return !isNullBlTLinebr(c) && !in(c, "-?:,[]{}#&*!|>'\"%@`")
                || !isNullBlTLinebr(reader.peek(1)) && (c == '-' || flowLevel == 0 && in(c, "?:"));
    }

    // ---- scanners ----

    private void scanToNextToken() {
        if (reader.index() == 0 && reader.peek() == 0xFEFF) {
            reader.forward();
        }
        boolean found = false;
        while (!found) {
            int ff = 0;
            while (reader.peek(ff) == ' ') {
                ff++;
            }
            if (ff > 0) {
                reader.forward(ff);
            }
            if (reader.peek() == '#') {
                skipComment();
            }
            if (!scanLineBreak().isEmpty()) {
                if (flowLevel == 0) {
                    allowSimpleKey = true;
                }
            } else {
                found = true;
            }
        }
    }

    private void skipComment() {
        reader.forward();
        int length = 0;
        while (!isNullOrLinebr(reader.peek(length))) {
            length++;
        }
        reader.prefixForward(length);
    }

    private Token scanDirective() {
        Mark start = reader.mark();
        reader.forward();
        String name = scanDirectiveName(start);
        List<String> value = null;
        Mark end;
        if ("YAML".equals(name)) {
            value = scanYamlDirectiveValue(start);
            end = reader.mark();
        } else if ("TAG".equals(name)) {
            value = scanTagDirectiveValue(start);
            end = reader.mark();
        } else {
            end = reader.mark();
            int ff = 0;
            while (!isNullOrLinebr(reader.peek(ff))) {
                ff++;
            }
            if (ff > 0) {
                reader.forward(ff);
            }
        }
        scanDirectiveIgnoredLine(start);
        return new Token(Kind.DIRECTIVE, start, end, name, (char) 0, null, null, value);
    }

    private Problem directive(Mark start, String problem) {
        return new Problem("while scanning a directive", start, problem, reader.mark());
    }

    private String scanDirectiveName(Mark start) {
        int length = 0;
        int c = reader.peek(length);
        while (isAlpha(c)) {
            length++;
            c = reader.peek(length);
        }
        if (length == 0) {
            throw directive(start, "expected alphabetic or numeric character, but found " + chars(c) + "(" + c + ")");
        }
        String value = reader.prefixForward(length);
        c = reader.peek();
        if (!isNullBlLinebr(c)) {
            throw directive(start, "expected alphabetic or numeric character, but found " + chars(c) + "(" + c + ")");
        }
        return value;
    }

    private List<String> scanYamlDirectiveValue(Mark start) {
        while (reader.peek() == ' ') {
            reader.forward();
        }
        String major = scanYamlDirectiveNumber(start);
        int c = reader.peek();
        if (c != '.') {
            throw directive(start, "expected a digit or '.', but found " + chars(c) + "(" + c + ")");
        }
        reader.forward();
        String minor = scanYamlDirectiveNumber(start);
        c = reader.peek();
        if (!isNullBlLinebr(c)) {
            throw directive(start, "expected a digit or ' ', but found " + chars(c) + "(" + c + ")");
        }
        return List.of(major, minor);
    }

    private String scanYamlDirectiveNumber(Mark start) {
        int c = reader.peek();
        if (!Character.isDigit(c)) {
            throw directive(start, "expected a digit, but found " + chars(c) + "(" + c + ")");
        }
        int length = 0;
        while (Character.isDigit(reader.peek(length))) {
            length++;
        }
        String number = reader.prefixForward(length);
        if (length > 3) {
            throw new Problem("while scanning a YAML directive", start, "found a number which cannot represent a valid version: " + number,
                    reader.mark());
        }
        return String.valueOf(Integer.parseInt(number));
    }

    private List<String> scanTagDirectiveValue(Mark start) {
        while (reader.peek() == ' ') {
            reader.forward();
        }
        String handle = scanTagHandle("directive", start);
        int c = reader.peek();
        if (c != ' ') {
            throw directive(start, "expected ' ', but found " + chars(c) + "(" + c + ")");
        }
        while (reader.peek() == ' ') {
            reader.forward();
        }
        String prefix = scanTagUri("directive", start);
        c = reader.peek();
        if (!isNullBlLinebr(c)) {
            throw directive(start, "expected ' ', but found " + chars(c) + "(" + c + ")");
        }
        return List.of(handle, prefix);
    }

    private void scanDirectiveIgnoredLine(Mark start) {
        while (reader.peek() == ' ') {
            reader.forward();
        }
        if (reader.peek() == '#') {
            skipComment();
        }
        int c = reader.peek();
        if (scanLineBreak().isEmpty() && c != 0) {
            throw directive(start, "expected a comment or a line break, but found " + chars(c) + "(" + c + ")");
        }
    }

    private Token scanAnchor(boolean isAnchor) {
        Mark start = reader.mark();
        String name = reader.peek() == '*' ? "alias" : "anchor";
        reader.forward();
        int length = 0;
        int c = reader.peek(length);
        while (!isNullBlTLinebr(c) && !in(c, ":,[]{}/.*&")) {
            length++;
            c = reader.peek(length);
        }
        if (length == 0) {
            throw new Problem("while scanning an " + name, start, "unexpected character found " + chars(c) + "(" + c + ")", reader.mark());
        }
        String value = reader.prefixForward(length);
        c = reader.peek();
        if (!isNullBlTLinebr(c) && !in(c, "?:,]}%@`")) {
            throw new Problem("while scanning an " + name, start, "unexpected character found " + chars(c) + "(" + c + ")", reader.mark());
        }
        return new Token(isAnchor ? Kind.ANCHOR : Kind.ALIAS, start, reader.mark(), value, (char) 0, null, null, null);
    }

    private Token scanTag() {
        Mark start = reader.mark();
        int c = reader.peek(1);
        String handle = null;
        String suffix;
        if (c == '<') {
            reader.forward(2);
            suffix = scanTagUri("tag", start);
            c = reader.peek();
            if (c != '>') {
                throw new Problem("while scanning a tag", start, "expected '>', but found '" + chars(c) + "' (" + c + ")", reader.mark());
            }
            reader.forward();
        } else if (isNullBlTLinebr(c)) {
            suffix = "!";
            reader.forward();
        } else {
            int length = 1;
            boolean useHandle = false;
            while (!isNullBlLinebr(c)) {
                if (c == '!') {
                    useHandle = true;
                    break;
                }
                length++;
                c = reader.peek(length);
            }
            if (useHandle) {
                handle = scanTagHandle("tag", start);
            } else {
                handle = "!";
                reader.forward();
            }
            suffix = scanTagUri("tag", start);
        }
        c = reader.peek();
        if (!isNullBlLinebr(c)) {
            throw new Problem("while scanning a tag", start, "expected ' ', but found '" + chars(c) + "' (" + c + ")", reader.mark());
        }
        return new Token(Kind.TAG, start, reader.mark(), null, (char) 0, handle, suffix, null);
    }

    private Token scanBlockScalar(char style) {
        boolean folded = style == '>';
        StringBuilder chunks = new StringBuilder();
        Mark start = reader.mark();
        reader.forward();
        Boolean chomping = null;
        int increment = -1;
        int c = reader.peek();
        if (c == '-' || c == '+') {
            chomping = c == '+';
            reader.forward();
            c = reader.peek();
            if (Character.isDigit(c)) {
                increment = Integer.parseInt(chars(c));
                if (increment == 0) {
                    throw new Problem("while scanning a block scalar", start, "expected indentation indicator in the range 1-9, but found 0",
                            reader.mark());
                }
                reader.forward();
            }
        } else if (Character.isDigit(c)) {
            increment = Integer.parseInt(chars(c));
            if (increment == 0) {
                throw new Problem("while scanning a block scalar", start, "expected indentation indicator in the range 1-9, but found 0",
                        reader.mark());
            }
            reader.forward();
            c = reader.peek();
            if (c == '-' || c == '+') {
                chomping = c == '+';
                reader.forward();
            }
        }
        c = reader.peek();
        if (!isNullBlLinebr(c)) {
            throw new Problem("while scanning a block scalar", start,
                    "expected chomping or indentation indicators, but found " + chars(c) + "(" + c + ")", reader.mark());
        }
        while (reader.peek() == ' ') {
            reader.forward();
        }
        if (reader.peek() == '#') {
            skipComment();
        }
        c = reader.peek();
        if (scanLineBreak().isEmpty() && c != 0) {
            throw new Problem("while scanning a block scalar", start, "expected a comment or a line break, but found " + chars(c) + "(" + c + ")",
                    reader.mark());
        }
        int minIndent = Math.max(1, indent + 1);
        String breaks;
        int blockIndent;
        Mark end;
        if (increment == -1) {
            StringBuilder b = new StringBuilder();
            int maxIndent = 0;
            end = reader.mark();
            while (isLinebr(reader.peek()) || reader.peek() == ' ' || reader.peek() == '\r') {
                if (reader.peek() != ' ') {
                    b.append(scanLineBreak());
                    end = reader.mark();
                } else {
                    reader.forward();
                    if (reader.column() > maxIndent) {
                        maxIndent = reader.column();
                    }
                }
            }
            breaks = b.toString();
            blockIndent = Math.max(minIndent, maxIndent);
        } else {
            blockIndent = minIndent + increment - 1;
            Object[] be = scanBlockScalarBreaks(blockIndent);
            breaks = (String) be[0];
            end = (Mark) be[1];
        }
        String lineBreak = "";
        while (reader.column() == blockIndent && reader.peek() != 0) {
            chunks.append(breaks);
            boolean leadingNonSpace = " \t".indexOf(reader.peek()) == -1;
            int length = 0;
            while (!isNullOrLinebr(reader.peek(length))) {
                length++;
            }
            chunks.append(reader.prefixForward(length));
            lineBreak = scanLineBreak();
            Object[] be = scanBlockScalarBreaks(blockIndent);
            breaks = (String) be[0];
            end = (Mark) be[1];
            if (reader.column() == blockIndent && reader.peek() != 0) {
                if (folded && "\n".equals(lineBreak) && leadingNonSpace && " \t".indexOf(reader.peek()) == -1) {
                    if (breaks.isEmpty()) {
                        chunks.append(' ');
                    }
                } else {
                    chunks.append(lineBreak);
                }
            } else {
                break;
            }
        }
        if (chomping == null || chomping) {
            chunks.append(lineBreak);
        }
        if (chomping != null && chomping) {
            chunks.append(breaks);
        }
        return new Token(Kind.SCALAR, start, end, chunks.toString(), style, null, null, null);
    }

    private Object[] scanBlockScalarBreaks(int blockIndent) {
        StringBuilder chunks = new StringBuilder();
        Mark end = reader.mark();
        int col = reader.column();
        while (col < blockIndent && reader.peek() == ' ') {
            reader.forward();
            col++;
        }
        String lineBreak;
        while (!(lineBreak = scanLineBreak()).isEmpty()) {
            chunks.append(lineBreak);
            end = reader.mark();
            col = reader.column();
            while (col < blockIndent && reader.peek() == ' ') {
                reader.forward();
                col++;
            }
        }
        return new Object[] {chunks.toString(), end};
    }

    private Token scanFlowScalar(char style) {
        boolean doubleQuoted = style == '"';
        StringBuilder chunks = new StringBuilder();
        Mark start = reader.mark();
        int quote = reader.peek();
        reader.forward();
        scanFlowScalarNonSpaces(doubleQuoted, start, chunks);
        while (reader.peek() != quote) {
            scanFlowScalarSpaces(start, chunks);
            scanFlowScalarNonSpaces(doubleQuoted, start, chunks);
        }
        reader.forward();
        return new Token(Kind.SCALAR, start, reader.mark(), chunks.toString(), style, null, null, null);
    }

    private void scanFlowScalarNonSpaces(boolean doubleQuoted, Mark start, StringBuilder chunks) {
        while (true) {
            int length = 0;
            while (!isNullBlTLinebr(reader.peek(length)) && !in(reader.peek(length), "'\"\\")) {
                length++;
            }
            if (length != 0) {
                chunks.append(reader.prefixForward(length));
            }
            int c = reader.peek();
            if (!doubleQuoted && c == '\'' && reader.peek(1) == '\'') {
                chunks.append('\'');
                reader.forward(2);
            } else if (doubleQuoted && c == '\'' || !doubleQuoted && (c == '"' || c == '\\')) {
                chunks.appendCodePoint(c);
                reader.forward();
            } else if (doubleQuoted && c == '\\') {
                reader.forward();
                c = reader.peek();
                int simple = Character.isSupplementaryCodePoint(c) ? -1 : ESCAPE_KEYS.indexOf(c);
                if (simple >= 0) {
                    chunks.append(ESCAPE_VALUES[simple]);
                    reader.forward();
                } else if (c == 'x' || c == 'u' || c == 'U') {
                    length = c == 'x' ? 2 : c == 'u' ? 4 : 8;
                    reader.forward();
                    String hex = reader.prefix(length);
                    for (int i = 0; i < hex.length(); i++) {
                        if (Character.digit(hex.charAt(i), 16) < 0 || hex.charAt(i) > 'f') {
                            throw new Problem("while scanning a double-quoted scalar", start,
                                    "expected escape sequence of " + length + " hexadecimal numbers, but found: " + hex, reader.mark());
                        }
                    }
                    int decimal = Integer.parseInt(hex, 16);
                    if (decimal < 0 || decimal > Character.MAX_CODE_POINT) {
                        throw new Problem("while scanning a double-quoted scalar", start, "found unknown escape character " + hex, reader.mark());
                    }
                    chunks.appendCodePoint(decimal);
                    reader.forward(length);
                } else if (!scanLineBreak().isEmpty()) {
                    chunks.append(scanFlowScalarBreaks(start));
                } else {
                    throw new Problem("while scanning a double-quoted scalar", start,
                            "found unknown escape character " + chars(c) + "(" + c + ")", reader.mark());
                }
            } else {
                return;
            }
        }
    }

    private void scanFlowScalarSpaces(Mark start, StringBuilder chunks) {
        int length = 0;
        while (" \t".indexOf(reader.peek(length)) != -1) {
            length++;
        }
        String whitespaces = reader.prefixForward(length);
        int c = reader.peek();
        if (c == 0) {
            throw new Problem("while scanning a quoted scalar", start, "found unexpected end of stream", reader.mark());
        }
        String lineBreak = scanLineBreak();
        if (!lineBreak.isEmpty()) {
            String breaks = scanFlowScalarBreaks(start);
            if (!"\n".equals(lineBreak)) {
                chunks.append(lineBreak);
            } else if (breaks.isEmpty()) {
                chunks.append(' ');
            }
            chunks.append(breaks);
        } else {
            chunks.append(whitespaces);
        }
    }

    private String scanFlowScalarBreaks(Mark start) {
        StringBuilder chunks = new StringBuilder();
        while (true) {
            String prefix = reader.prefix(3);
            if (("---".equals(prefix) || "...".equals(prefix)) && isNullBlTLinebr(reader.peek(3))) {
                throw new Problem("while scanning a quoted scalar", start, "found unexpected document separator", reader.mark());
            }
            while (" \t".indexOf(reader.peek()) != -1) {
                reader.forward();
            }
            String lineBreak = scanLineBreak();
            if (!lineBreak.isEmpty()) {
                chunks.append(lineBreak);
            } else {
                return chunks.toString();
            }
        }
    }

    private Token scanPlain() {
        StringBuilder chunks = new StringBuilder();
        Mark start = reader.mark();
        Mark end = start;
        int plainIndent = indent + 1;
        String spaces = "";
        while (true) {
            int c;
            int length = 0;
            if (reader.peek() == '#') {
                break;
            }
            while (true) {
                c = reader.peek(length);
                if (isNullBlTLinebr(c)
                        || c == ':' && (isNullBlTLinebr(reader.peek(length + 1)) || flowLevel != 0 && in(reader.peek(length + 1), ",[]{}"))
                        || flowLevel != 0 && in(c, ",?[]{}")) {
                    break;
                }
                length++;
            }
            if (length == 0) {
                break;
            }
            allowSimpleKey = false;
            chunks.append(spaces);
            chunks.append(reader.prefixForward(length));
            end = reader.mark();
            spaces = scanPlainSpaces();
            if (spaces.isEmpty() || reader.peek() == '#' || flowLevel == 0 && reader.column() < plainIndent) {
                break;
            }
        }
        return new Token(Kind.SCALAR, start, end, chunks.toString(), (char) 0, null, null, null);
    }

    private String scanPlainSpaces() {
        int length = 0;
        while (reader.peek(length) == ' ' || reader.peek(length) == '\t') {
            length++;
        }
        String whitespaces = reader.prefixForward(length);
        String lineBreak = scanLineBreak();
        if (!lineBreak.isEmpty()) {
            allowSimpleKey = true;
            String prefix = reader.prefix(3);
            if ("---".equals(prefix) || "...".equals(prefix) && isNullBlTLinebr(reader.peek(3))) {
                return "";
            }
            StringBuilder breaks = new StringBuilder();
            while (true) {
                if (reader.peek() == ' ') {
                    reader.forward();
                } else {
                    String lb = scanLineBreak();
                    if (!lb.isEmpty()) {
                        breaks.append(lb);
                        prefix = reader.prefix(3);
                        if ("---".equals(prefix) || "...".equals(prefix) && isNullBlTLinebr(reader.peek(3))) {
                            return "";
                        }
                    } else {
                        break;
                    }
                }
            }
            if (!"\n".equals(lineBreak)) {
                return lineBreak + breaks;
            } else if (breaks.length() == 0) {
                return " ";
            }
            return breaks.toString();
        }
        return whitespaces;
    }

    private String scanTagHandle(String name, Mark start) {
        int c = reader.peek();
        if (c != '!') {
            throw new Problem("while scanning a " + name, start, "expected '!', but found " + chars(c) + "(" + c + ")", reader.mark());
        }
        int length = 1;
        c = reader.peek(length);
        if (c != ' ') {
            while (isAlpha(c)) {
                length++;
                c = reader.peek(length);
            }
            if (c != '!') {
                reader.forward(length);
                throw new Problem("while scanning a " + name, start, "expected '!', but found " + chars(c) + "(" + c + ")", reader.mark());
            }
            length++;
        }
        return reader.prefixForward(length);
    }

    private String scanTagUri(String name, Mark start) {
        StringBuilder chunks = new StringBuilder();
        int length = 0;
        int c = reader.peek(length);
        while (c < 128 && in(c, URI_CHARS) && c != 0) {
            if (c == '%') {
                chunks.append(reader.prefixForward(length));
                length = 0;
                chunks.append(scanUriEscapes(name, start));
            } else {
                length++;
            }
            c = reader.peek(length);
        }
        if (length != 0) {
            chunks.append(reader.prefixForward(length));
        }
        if (chunks.length() == 0) {
            throw new Problem("while scanning a " + name, start, "expected URI, but found " + chars(c) + "(" + c + ")", reader.mark());
        }
        return chunks.toString();
    }

    private String scanUriEscapes(String name, Mark start) {
        Mark beginning = reader.mark();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (reader.peek() == '%') {
            reader.forward();
            String two = reader.prefix(2);
            int code = two.length() == 2 ? hexByte(two) : -1;
            if (code < 0) {
                int c1 = reader.peek();
                int c2 = reader.peek(1);
                throw new Problem("while scanning a " + name, start, "expected URI escape sequence of 2 hexadecimal numbers, but found "
                        + chars(c1) + "(" + c1 + ") and " + chars(c2) + "(" + c2 + ")", reader.mark());
            }
            bytes.write(code);
            reader.forward(2);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException e) {
            throw new Problem("while scanning a " + name, start, "expected URI in UTF-8: " + e.getMessage(), beginning);
        }
    }

    private static int hexByte(String two) {
        int hi = Character.digit(two.charAt(0), 16);
        int lo = Character.digit(two.charAt(1), 16);
        return hi < 0 || lo < 0 ? -1 : hi * 16 + lo;
    }

    private String scanLineBreak() {
        int c = reader.peek();
        if (c == '\r' || c == '\n' || c == 0x85) {
            if (c == '\r' && reader.peek(1) == '\n') {
                reader.forward(2);
            } else {
                reader.forward();
            }
            return "\n";
        } else if (c == 0x2028 || c == 0x2029) {
            reader.forward();
            return chars(c);
        }
        return "";
    }
}
