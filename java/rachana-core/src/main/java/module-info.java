/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
/**
 * rachana-core: the Rachana compiler (Sutra grammar, Rachana-EL, formats, checker, editing operations).
 *
 * <p>Every package is exported as it was in the host it moved from; the public surface is cut down at the API review before
 * {@code v1.0.0}. The {@code requires} of Jackson is a split exemption (java/SPLIT_EXEMPTIONS.md), removed by A8b.
 */
module com.ash.rachana.core {
    requires transitive com.ash.rachana.api;
    requires transitive com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;

    uses com.ash.rachana.el.FunctionProvider;

    exports com.ash.rachana;
    exports com.ash.rachana.design.ops;
    exports com.ash.rachana.el;
    exports com.ash.rachana.el.check;
    exports com.ash.rachana.format;
    exports com.ash.rachana.model;
    exports com.ash.rachana.parse;
    exports com.ash.rachana.profile;

    opens com.ash.rachana to com.fasterxml.jackson.databind;
    opens com.ash.rachana.design.ops to com.fasterxml.jackson.databind;
    opens com.ash.rachana.model to com.fasterxml.jackson.databind;
}
