/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.internal.CoreText;
import com.ash.rachana.el.Token.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a Rachana-EL expression into tokens, at the one language level (1.2). The syntax batch (EL_EXPANSION X6, X7, X11 to X14,
 * DECISIONS PY1 to PY8) is part of it: comments {@code /* ... *}{@code /}, digit separators ({@code 1_000}), the tokens
 * {@code |>}, {@code ??}, {@code **}, {@code ...}, <code>{</code> and <code>}</code>, and keeps {@code =>} and {@code ..} reserved with
 * the Python spelling as the fix.
 */
final class Lexer {

    private Lexer() {}

    /** The tokens of {@code src}, at the one language level ({@code Grammar.LEVEL}). */
    static List<Token> tokens(String src) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (CoreText.isWhitespace(c)) {
                i++;
            } else if (src.startsWith("/*", i)) {
                int end = src.indexOf("*/", i + 2);                // X14: a comment stands where white space may
                if (end < 0) {
                    throw new ElException("a comment '/*' is not closed", i);
                }
                i = end + 2;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                i = number11(src, i, out);
            } else if (c == '\'' || c == '"') {
                StringBuilder sb = new StringBuilder();
                int j = i + 1;
                while (j < n && src.charAt(j) != c) {
                    if (src.charAt(j) == '\\' && j + 1 < n) {
                        j++;
                    }
                    sb.append(src.charAt(j++));
                }
                if (j >= n) {
                    throw new ElException("unterminated string", i);
                }
                out.add(new Token(Type.STRING, sb.toString(), i));
                i = j + 1;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_')) {
                    j++;
                }
                out.add(new Token(Type.IDENT, src.substring(i, j), i));
                i = j;
            } else {
                String two = i + 1 < n ? src.substring(i, i + 2) : "";
                if (src.startsWith("...", i)) {
                    out.add(new Token(Type.SPREAD, "...", i));    // X6: spread in a record or a list
                    i += 3;
                    continue;
                }
                switch (two) {
                    case "|>", "??", "**" -> {                       // X11 the pipe, X12 the first non-empty, PY6 the power
                        out.add(new Token(Type.OP, two, i));
                        i += 2;
                        continue;
                    }
                    case "=>" -> throw new ElException("'=>' is reserved for a later version of Rachana: write lambda x: ...", i, i + 2);
                    case ".." -> throw new ElException("'..' is reserved for a later version of Rachana: write range(start, stop)", i, i + 2);
                    case "*/" -> throw new ElException("'*/' closes no comment", i, i + 2);
                    default -> { }
                }
                if (c == '{' || c == '}') {
                    out.add(new Token(c == '{' ? Type.LBRACE : Type.RBRACE, String.valueOf(c), i));   // X6: a record
                    i++;
                    continue;
                }
                switch (two) {
                    case "//" -> throw new ElException("'//' is not Rachana: write floor(x / y)", i);
                    case "&&", "||", "==", "!=", "<=", ">=" -> {
                        out.add(new Token(Type.OP, two, i));
                        i += 2;
                        continue;
                    }
                    case "[?" -> {
                        out.add(new Token(Type.FILTER, two, i));
                        i += 2;
                        continue;
                    }
                    default -> { }
                }
                if (c == '@' && i + 1 < n && Character.isLetter(src.charAt(i + 1))) {
                    int j = i + 1;                                   // @<panel>: the events of a panel of the screen (RACHANA_REFERENCE.md 7)
                    while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_'
                            || src.charAt(j) == '-' && j + 1 < n && Character.isLetterOrDigit(src.charAt(j + 1)))) {
                        j++;
                    }
                    out.add(new Token(Type.EVENT, src.substring(i + 1, j), i));
                    i = j;
                    continue;
                }
                if (c == '$' && src.startsWith("$src", i) && (i + 4 >= n || !Character.isLetterOrDigit(src.charAt(i + 4)) && src.charAt(i + 4) != '_')) {
                    out.add(new Token(Type.SRC, "$src", i));       // $src: the record the panel's source: names (RACHANA_REFERENCE.md 6)
                    i += 4;
                    continue;
                }
                if (c == '#' && src.startsWith("#index", i)) {
                    out.add(new Token(Type.HASH_INDEX, "#index", i));
                    i += 6;
                    continue;
                }
                Type t = switch (c) {
                    case '$' -> Type.DOLLAR;
                    case '@' -> Type.AT;
                    case '^' -> Type.CARET;                          // ^: the element's parent (^^ its parent), docs/architecture/ARCHITECTURE.md §28 Q8
                    case '(' -> Type.LPAREN;
                    case ')' -> Type.RPAREN;
                    case '[' -> Type.LBRACKET;
                    case ']' -> Type.RBRACKET;
                    case '.' -> Type.DOT;
                    case ',' -> Type.COMMA;
                    case '?' -> Type.QUESTION;
                    case ':' -> Type.COLON;
                    case '+', '-', '*', '/', '%', '<', '>', '!', '=' -> Type.OP;   // a lone '=' only names a value in let(...)
                    default -> throw new ElException("unexpected character '" + c + "'", i);
                };
                out.add(new Token(t, String.valueOf(c), i));
                i++;
            }
        }
        out.add(new Token(Type.EOF, "", n));
        return out;
    }

    /**
     * A number at level 1.1: digits, one point, an exponent, and {@code _} only between two digits (X14); the run ends before {@code ..}.
     * The token's text is the number without its separators.
     */
    private static int number11(String src, int i, List<Token> out) {
        int n = src.length();
        int j = i;
        while (j < n) {
            char ch = src.charAt(j);
            boolean sign = (ch == '-' || ch == '+') && j > i && (src.charAt(j - 1) == 'e' || src.charAt(j - 1) == 'E');
            if (ch == '.' && j + 1 < n && src.charAt(j + 1) == '.') {
                break;
            }
            if (!(Character.isDigit(ch) || ch == '.' || ch == '_' || ch == 'e' || ch == 'E' || sign)) {
                break;
            }
            j++;
        }
        String run = src.substring(i, j);
        for (int k = 0; k < run.length(); k++) {
            if (run.charAt(k) == '_' && (k == 0 || k + 1 == run.length() || !ascii(run.charAt(k - 1)) || !ascii(run.charAt(k + 1)))) {
                throw new ElException("'_' in a number stands only between two digits: '" + run + "'", i, j);
            }
        }
        out.add(new Token(Type.NUMBER, run.replace("_", ""), i, j - i));
        return j;
    }

    private static boolean ascii(char c) {
        return c >= '0' && c <= '9';
    }
}
