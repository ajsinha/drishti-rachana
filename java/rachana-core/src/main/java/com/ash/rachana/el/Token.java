/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

/**
 * A lexical token.
 *
 * @param type the token type
 * @param text the source text (unquoted for strings)
 * @param pos 0-based offset in the expression
 */
record Token(Type type, String text, int pos, int length) {

    Token(Type type, String text, int pos) {
        this(type, text, pos, text.length());
    }

    enum Type { NUMBER, STRING, IDENT, DOLLAR, AT, CARET, EVENT, SRC, HASH_INDEX, OP, LPAREN, RPAREN, LBRACKET, FILTER, RBRACKET, DOT, COMMA, QUESTION, COLON, SPREAD, LBRACE, RBRACE, EOF }

    boolean is(Type t, String s) {
        return type == t && text.equals(s);
    }
}
