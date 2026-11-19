/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import java.util.List;

/**
 * What a list of operations made.
 *
 * @param yaml the Sutra text after every operation that could be applied (the input text when none could)
 * @param problems one entry per operation that could not be applied
 * @param applied how many operations were applied
 */
public record OpResult(String yaml, List<OpProblem> problems, int applied) {}
