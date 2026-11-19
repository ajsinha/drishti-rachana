/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.drishti.api.error.DrishtiException;
import com.ash.drishti.api.error.ErrorCode;
import java.util.List;

/** A Sutra failed to parse or validate. Carries every problem found, not only the first. */
public class SutraException extends DrishtiException {

    private static final long serialVersionUID = 1L;
    private final transient List<SutraProblem> problems;

    public SutraException(List<SutraProblem> problems) {
        super(ErrorCode.SUTRA_INVALID, problems.size() + " problem(s): " + problems);
        this.problems = List.copyOf(problems);
    }

    public List<SutraProblem> problems() {
        return problems;
    }
}
