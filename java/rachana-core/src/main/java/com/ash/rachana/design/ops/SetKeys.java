/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import java.util.Map;

/**
 * Sets the function keys ({@code F2: panel-id} or an action); an empty mapping removes them.
 *
 * @param keys function key to action
 */
public record SetKeys(Map<String, Object> keys) implements Op {

    @Override
    public String name() {
        return "setKeys";
    }

    @Override
    public void applyTo(SutraDoc doc) {
        if (keys == null) {
            throw new OpException(OpException.MALFORMED, "setKeys needs 'keys': a mapping such as { F2: legs } (empty to remove them)");
        }
        doc.setTop("keys", OpChecks.plain(keys));
    }
}
