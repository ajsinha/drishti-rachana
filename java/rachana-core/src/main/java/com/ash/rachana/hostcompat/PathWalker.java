/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.hostcompat;

/** Walks simple document paths: {@code $.a.b[2].c}, {@code legs[-1]}. No allocation beyond substrings. */
final class PathWalker {

    private PathWalker() {}

    static DataNode walk(DataNode root, String path) {
        if (path == null || path.isEmpty() || "$".equals(path)) {
            return root;
        }
        int i = path.startsWith("$.") ? 2 : path.startsWith("$") ? 1 : 0;
        DataNode node = root;
        int n = path.length();
        while (i < n && !node.isMissing()) {
            char c = path.charAt(i);
            if (c == '.') {
                i++;
            } else if (c == '[') {
                int end = path.indexOf(']', i);
                if (end < 0) {
                    return DataNode.missing();
                }
                String inner = path.substring(i + 1, end).trim();
                if (!inner.isEmpty() && (inner.charAt(0) == '\'' || inner.charAt(0) == '"')) {
                    node = node.get(inner.substring(1, inner.length() - 1));
                } else {
                    try {
                        node = node.get(Integer.parseInt(inner));
                    } catch (NumberFormatException e) {
                        return DataNode.missing();
                    }
                }
                i = end + 1;
            } else {
                int j = i;
                while (j < n && path.charAt(j) != '.' && path.charAt(j) != '[') {
                    j++;
                }
                node = node.get(path.substring(i, j));
                i = j;
            }
        }
        return node;
    }
}
