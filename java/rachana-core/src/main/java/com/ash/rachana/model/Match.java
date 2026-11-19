/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

/**
 * Which entities a Sutra applies to.
 *
 * @param kind the entity kind
 * @param where an optional Rachana-EL predicate over the document; null matches every entity of the kind
 * @param priority higher wins when several Sutras match
 */
public record Match(String kind, String where, int priority) {}
