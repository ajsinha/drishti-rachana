/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.format;

import com.ash.rachana.internal.NumberText;
import com.ash.rachana.internal.CoreMath;
import java.util.regex.Pattern;

/**
 * Ids are text. An id that arrives as a number (an Endur deal number written as {@code 1000008}, or a column a table keeps
 * as a number) is read as its exact digits: {@code "1000008"}, never {@code "1000008.0"}. No id shape is assumed: digits,
 * letters, dashes and underscores are all ids.
 */
public final class IdText {

    /** A field named like an id or key: ID, TRADE_ID, TRADE_IDS, DEAL_REF, NETTING_SET_KEY (last path segment, any case). */
    private static final Pattern ID_FIELD = Pattern.compile("(?i)(^|_)(id|ids|ref|refs|key)$");

    private IdText() {
    }

    /** Whether a field (a dotted path or a physical column such as counterparty__id) names an id. */
    public static boolean isIdField(String path) {
        if (path == null) {
            return false;
        }
        String last = path.replace("__", ".");
        int dot = last.lastIndexOf('.');
        return ID_FIELD.matcher(dot < 0 ? last : last.substring(dot + 1)).find();
    }

    /** A number as id text: a whole number without a fraction or exponent. */
    public static String of(double d) {
        if (d == CoreMath.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e18) {
            return Long.toString(CoreMath.toLong(d));
        }
        return NumberText.decimal(d).stripTrailingZeros().toPlainString();
    }
}
