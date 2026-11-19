/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Proof-only stand-in for {@code new ObjectMapper(new YAMLFactory()).readTree(in)} (cut C3): the stream holds the JSON the prep
 * step wrote from the reference's own Jackson read of the YAML resource, so the tree is the one the reference saw.
 */
public final class ObjectMapper {

    public ObjectMapper(YAMLFactory f) {
    }

    public JsonNode readTree(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
        }
        return JsonNode.of(Json.parse(new String(b.toByteArray(), StandardCharsets.UTF_8)));
    }
}
