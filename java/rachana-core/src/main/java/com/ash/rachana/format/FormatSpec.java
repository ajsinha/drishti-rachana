/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.format;

import com.ash.rachana.internal.NumberText;
import com.ash.rachana.api.Node;
import com.ash.rachana.el.Link;
import com.ash.rachana.el.Values;
import com.ash.rachana.internal.DateText;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * One named format. Immutable and thread-safe (no shared {@code DecimalFormat}).
 *
 * @param type {@code number}, {@code percent}, {@code compact}, {@code date} or {@code text}
 * @param decimals fraction digits
 * @param grouping insert thousands separators
 * @param sign {@code auto} (minus only) or {@code always} (plus for positives)
 * @param scale multiplier applied first (100 for percent of a fraction)
 * @param suffix appended text (for example {@code %})
 * @param pattern date pattern for {@code date}
 */
public record FormatSpec(String type, int decimals, boolean grouping, String sign, double scale, String suffix, String pattern) {

    static final char MINUS = '−';

    public String format(Object value) {
        Object v = Values.simplify(value);
        if (v == null) {
            return "";
        }
        if (v instanceof Link l) {
            return l.display();
        }
        if (Values.masked(v)) {
            return Node.MASK;                 // a masked value reads as the mask in every format
        }
        return switch (type) {
            case "date" -> date(Values.text(v));
            case "text" -> Values.text(v);
            case "compact" -> compact(Values.number(v));
            default -> {
                double d = Values.number(v);
                yield Double.isNaN(d) ? Values.text(v) : number(d * scale) + (suffix == null ? "" : suffix);
            }
        };
    }

    private String number(double d) {
        BigDecimal b = NumberText.decimal(Math.abs(d)).setScale(decimals, RoundingMode.HALF_UP);
        String digits = b.toPlainString();
        if (grouping) {
            digits = group(digits);
        }
        boolean zero = b.signum() == 0;
        if (d < 0 && !zero) {
            return MINUS + digits;
        }
        return "always".equals(sign) && !zero ? "+" + digits : digits;
    }

    static String group(String plain) {
        return NumberText.group(plain);
    }

    private String compact(double d) {
        if (Double.isNaN(d)) {
            return "";
        }
        double a = Math.abs(d);
        String unit;
        double v;
        if (a >= 1e9) {
            unit = "bn";
            v = d / 1e9;
        } else if (a >= 1e6) {
            unit = "m";
            v = d / 1e6;
        } else if (a >= 1e3) {
            unit = "k";
            v = d / 1e3;
        } else {
            unit = "";
            v = d;
        }
        return number(v) + unit;
    }

    private String date(String iso) {
        if (pattern == null || pattern.equals("yyyy-MM-dd") || iso.length() < 10) {
            return iso;
        }
        try {
            String text = DateText.format(LocalDate.parse(iso.substring(0, 10)), pattern);
            return text == null ? iso : text;
        } catch (RuntimeException e) {
            return iso;
        }
    }
}
