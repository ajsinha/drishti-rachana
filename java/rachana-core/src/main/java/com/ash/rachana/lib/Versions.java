/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.lib;

/** Semantic versions of libraries: constraints and ordering. */
final class Versions {

    private Versions() {
    }

    /** Whether a version ({@code 1.4.2}) satisfies a constraint: empty (any), a major ({@code 1}) or an exact version. */
    static boolean matches(String version, String constraint) {
        if (version == null) {
            return false;
        }
        if (constraint == null || constraint.isEmpty()) {
            return true;
        }
        return constraint.contains(".") ? constraint.equals(version) : version.startsWith(constraint + ".");
    }

    /** A version as a sortable key ({@code 1.10.0} after {@code 1.9.3}). */
    static String semver(String v) {
        StringBuilder sb = new StringBuilder();
        for (String p : v.split("\\.")) {
            String n = Long.toString(Long.parseLong(p));
            sb.append("0000000000", 0, Math.max(0, 10 - n.length())).append(n);
        }
        return sb.toString();
    }
}
