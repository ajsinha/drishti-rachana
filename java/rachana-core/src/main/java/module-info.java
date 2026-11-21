/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * rachana-core: the Rachana compiler (Sutra grammar, Rachana-EL, formats, checker, editing operations).
 *
 * <p>Every package is exported as it was in the host it moved from; the public surface is cut down at the API review before
 * {@code v1.0.0}.
 */
module com.ash.rachana.core {
    requires transitive com.ash.rachana.api;

    exports com.ash.rachana;
    exports com.ash.rachana.agg;
    exports com.ash.rachana.bind;
    exports com.ash.rachana.control;
    exports com.ash.rachana.design.ops;
    exports com.ash.rachana.el;
    exports com.ash.rachana.el.check;
    exports com.ash.rachana.format;
    exports com.ash.rachana.model;
    exports com.ash.rachana.parse;
    exports com.ash.rachana.profile;
    exports com.ash.rachana.table;
    exports com.ash.rachana.view;
    exports com.ash.rachana.wire;

}
