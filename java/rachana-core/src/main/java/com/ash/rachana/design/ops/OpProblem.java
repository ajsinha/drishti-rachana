/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

/**
 * One thing wrong with one operation of a list. The operation was not applied; the Sutra text is as it was before it.
 *
 * @param op index of the operation in the list (0-based)
 * @param name the operation's name ({@code addPanel}, {@code setOption}, ...)
 * @param code stable {@code DRS-5nnn} (the operation) or {@code DRS-2nnn} (the Sutra the operation would have made)
 * @param message what is wrong and how to fix it
 * @param line 1-based line in the Sutra text the operation worked on, or 0
 */
public record OpProblem(int op, String name, String code, String message, int line) {}
