/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.api.Node;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Streams JSON straight into {@link Node} trees and back, without an intermediate Jackson tree.
 * Thread-safe: the factory is shared, parsers are per call.
 */
public final class JsonCodec {

    private final JsonFactory factory =
            JsonFactory.builder().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();

    public Node read(InputStream in) throws IOException {
        try (JsonParser p = factory.createParser(in)) {
            return readRoot(p);
        }
    }

    public Node read(String json) {
        try (JsonParser p = factory.createParser(json)) {
            return readRoot(p);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private Node readRoot(JsonParser p) throws IOException {
        JsonToken t = p.nextToken();
        if (t == null) {
            return Node.missing();
        }
        return readValue(p, t);
    }

    private Node readValue(JsonParser p, JsonToken t) throws IOException {
        switch (t) {
            case START_OBJECT: {
                Map<String, Node> fields = new LinkedHashMap<>();
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    String name = p.currentName();
                    fields.put(name, readValue(p, p.nextToken()));
                }
                return Node.of(fields);
            }
            case START_ARRAY: {
                ArrayList<Node> items = new ArrayList<>();
                JsonToken n;
                while ((n = p.nextToken()) != JsonToken.END_ARRAY) {
                    items.add(readValue(p, n));
                }
                return Node.list(items);
            }
            case VALUE_STRING:
                return Node.of(p.getText());
            case VALUE_NUMBER_INT:
                return Node.of(p.getNumberType() == JsonParser.NumberType.BIG_INTEGER
                        ? p.getBigIntegerValue() : (Object) p.getLongValue());
            case VALUE_NUMBER_FLOAT:
                return Node.of(p.getDoubleValue());
            case VALUE_TRUE:
                return Node.of(Boolean.TRUE);
            case VALUE_FALSE:
                return Node.of(Boolean.FALSE);
            case VALUE_NULL:
                return Node.nullValue();
            default:
                throw new IllegalArgumentException("unexpected token " + t + " at " + p.currentLocation());
        }
    }



    public JsonFactory factory() {
        return factory;
    }
}
