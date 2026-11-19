/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.ElLimits;
import com.ash.rachana.el.SafeRegex;
import com.ash.rachana.format.Formats;
import java.util.List;

/**
 * Every setting of Rachana, in one value: no framework, no system property, no environment variable is read by Core. A host builds
 * it from its own configuration (a host keeps its own setting names) and hands it over; the defaults are what Core uses alone.
 *
 * @param expressionCacheSize how many compiled expressions (and as many templates) the compiler keeps, least recently used out
 * @param expressionLimits    the deepest nesting and the longest text an expression may have
 * @param formatsFile         a site file whose format names override every other, or null
 * @param formatFiles         files of format names a profile adds, read in order after the bundled table
 * @param regexLimits         the caps of the linear-time regular expressions
 */
public record RachanaOptions(long expressionCacheSize, ElLimits expressionLimits, String formatsFile, List<String> formatFiles,
        SafeRegex.Limits regexLimits) {

    /** What Core uses alone. */
    public static final RachanaOptions DEFAULTS = new RachanaOptions(10_000, ElLimits.DEFAULTS, null, List.of(), SafeRegex.Limits.DEFAULTS);

    public RachanaOptions {
        if (expressionCacheSize < 1) {
            throw new IllegalArgumentException("the expression cache holds at least one entry, not " + expressionCacheSize);
        }
        expressionLimits = expressionLimits == null ? ElLimits.DEFAULTS : expressionLimits;
        formatFiles = formatFiles == null ? List.of() : List.copyOf(formatFiles);
        regexLimits = regexLimits == null ? SafeRegex.Limits.DEFAULTS : regexLimits;
    }

    /** These options with another cache size. */
    public RachanaOptions withExpressionCacheSize(long size) {
        return new RachanaOptions(size, expressionLimits, formatsFile, formatFiles, regexLimits);
    }

    /** These options with other expression limits. */
    public RachanaOptions withExpressionLimits(ElLimits limits) {
        return new RachanaOptions(expressionCacheSize, limits, formatsFile, formatFiles, regexLimits);
    }

    /** These options with another site formats file and profile format files. */
    public RachanaOptions withFormats(String siteFile, List<String> profileFiles) {
        return new RachanaOptions(expressionCacheSize, expressionLimits, siteFile, profileFiles, regexLimits);
    }

    /** These options with other regular-expression caps. */
    public RachanaOptions withRegexLimits(SafeRegex.Limits limits) {
        return new RachanaOptions(expressionCacheSize, expressionLimits, formatsFile, formatFiles, limits);
    }

    /** A compiler with these options' cache size and limits. */
    public ElCompiler compiler() {
        return new ElCompiler(expressionCacheSize, expressionLimits);
    }

    /** The format table: the bundled names, then the profile files, then the site file. */
    public Formats formats() {
        return Formats.load(formatsFile, formatFiles);
    }
}
