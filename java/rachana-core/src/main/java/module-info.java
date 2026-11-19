/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * rachana-core: the Rachana compiler (Sutra grammar, Rachana-EL, formats, checker, editing operations).
 *
 * <p>Every package is exported as it was in the host it moved from; the public surface is cut down at the API review before
 * {@code v1.0.0}. The {@code requires} of Caffeine and the {@code hostcompat} package are split exemptions
 * (java/SPLIT_EXEMPTIONS.md), each removed by its step.
 */
module com.ash.rachana.core {
    requires transitive com.ash.rachana.api;
    requires com.github.benmanes.caffeine;

    uses com.ash.rachana.el.FunctionProvider;

    exports com.ash.rachana;
    exports com.ash.rachana.design.ops;
    exports com.ash.rachana.el;
    exports com.ash.rachana.el.check;
    exports com.ash.rachana.format;
    exports com.ash.rachana.hostcompat;
    exports com.ash.rachana.model;
    exports com.ash.rachana.parse;
    exports com.ash.rachana.profile;

}
