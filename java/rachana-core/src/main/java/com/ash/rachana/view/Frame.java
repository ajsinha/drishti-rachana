/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import java.util.List;

/**
 * What one SSE event carries: the minimal patches that turn the client's view into the current one.
 *
 * @param seq monotonic per stream; the SSE event id
 * @param generation source generation of the entity after this frame
 * @param patches the changes
 * @param latencyMs source tick to frame build, for this frame
 * @param p99Ms rolling p99 of tick-to-send latency across the server
 */
public record Frame(long seq, long generation, List<Patch> patches, double latencyMs, double p99Ms) {}
