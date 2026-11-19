/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import java.util.List;

/** A Sutra failed to parse or validate. Carries every problem found, not only the first. */
public class SutraException extends RachanaException {

    private static final long serialVersionUID = 1L;
    private final transient List<SutraProblem> problems;

    public SutraException(List<SutraProblem> problems) {
        super(RachanaCode.SUTRA_INVALID, problems.size() + " problem(s): " + problems);
        this.problems = List.copyOf(problems);
    }

    public List<SutraProblem> problems() {
        return problems;
    }
}
