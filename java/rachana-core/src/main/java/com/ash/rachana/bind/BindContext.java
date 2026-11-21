/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.el.EvalContext;
import java.util.Map;
import java.util.Set;

/**
 * What one view's panels bind against: the entity viewed ({@code doc}, may be null for a view of no entity), its layout, the
 * evaluation context, the linked entities fetched ({@code linked}), those still being fetched ({@code pending}) and those the viewer may
 * not open ({@code denied}), the map panels' lookups, and {@code host}: what the host's own panel binders read (its links, provenance,
 * fingerprint; Core never reads it).
 */
public record BindContext(
        Entity doc,
        Layout layout,
        EvalContext eval,
        Map<EntityRef, Entity> linked,
        Set<EntityRef> pending,
        Set<EntityRef> denied,
        Map<String, MapSupport.Lookup> maps,
        Object host) {

    public BindContext {
        layout = layout == null ? Layout.written(null) : layout;
        linked = linked == null ? Map.of() : linked;
        pending = pending == null ? Set.of() : pending;
        denied = denied == null ? Set.of() : denied;
        maps = maps == null ? Map.of() : maps;
    }

    /** A context of one entity with nothing linked. */
    public static BindContext of(Entity doc, Layout layout, EvalContext eval) {
        return new BindContext(doc, layout, eval, null, null, null, null, null);
    }

    /** The same binding read in another evaluation context (a tab's element is {@code @}). */
    public BindContext withEval(EvalContext next) {
        return new BindContext(doc, layout, next, linked, pending, denied, maps, host);
    }
}
