/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.js;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

/**
 * The class-path resources Core reads, embedded in the bundles at build time (TeaVM's resource SPI; JS_PROOF J22): the bundled
 * format table, the world regions and the function catalogue. Used by the build only, never part of a bundle.
 */
public final class BundledResources implements ResourceSupplier {

    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        return new String[] { "formats.yaml", "world-regions.yaml", "rachana/el/signatures.yaml", "rachana/el/functions.yaml" };
    }
}
